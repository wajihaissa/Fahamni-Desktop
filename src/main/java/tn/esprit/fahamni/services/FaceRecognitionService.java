package tn.esprit.fahamni.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.utils.AppConfig;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserInputValidator;
import tn.esprit.fahamni.utils.UserSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class FaceRecognitionService {

    private static final long MAX_FACE_IMAGE_SIZE_BYTES = 4L * 1024 * 1024;
    private static final long[] RETRY_DELAYS_MS = {0L, 200L, 600L};
    private static final double SAFE_DEFAULT_THRESHOLD = 80.0;

    public record FaceStatus(
        String engineStatus,
        String faceStatus,
        boolean enabled,
        String enrolledAt
    ) {
    }

    public record DetectResult(
        boolean success,
        String faceToken,
        String message,
        int faceCount
    ) {
    }

    public record CompareResult(
        boolean success,
        boolean match,
        double confidence,
        double threshold,
        String message
    ) {
    }

    public record FaceLoginResult(
        boolean success,
        User user,
        String message
    ) {
    }

    private final Connection connection;
    private final HttpClient httpClient;

    public FaceRecognitionService() {
        this.connection = MyDataBase.getInstance().getCnx();
        this.httpClient = HttpClient.newBuilder().build();
    }

    public boolean isConfigured() {
        return AppConfig.isFaceConfigured();
    }

    public DetectResult detectFaceToken(byte[] imageBytes) {
        String validationError = validateJpegCapture(imageBytes);
        if (validationError != null) {
            return new DetectResult(false, null, validationError, 0);
        }

        if (!isConfigured()) {
            return new DetectResult(false, null, "Face++ n'est pas configure. Ajoutez FACEPP_API_KEY, FACEPP_API_SECRET et FACEPP_API_BASE_URL.", 0);
        }

        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("api_key", AppConfig.getFaceApiKey());
        form.put("api_secret", AppConfig.getFaceApiSecret());
        form.put("image_base64", imageBase64);

        try {
            JsonObject json = postWithRetries("/facepp/v3/detect", form);
            JsonArray faces = json.has("faces") && json.get("faces").isJsonArray()
                ? json.getAsJsonArray("faces")
                : new JsonArray();

            int faceCount = faces.size();
            if (faceCount == 0) {
                return new DetectResult(false, null, "No face detected. Use a clear selfie.", 0);
            }
            if (faceCount > 1) {
                return new DetectResult(false, null, "Multiple faces detected. Use only one face.", faceCount);
            }

            JsonObject firstFace = faces.get(0).getAsJsonObject();
            if (!firstFace.has("face_token") || firstFace.get("face_token").getAsString().isBlank()) {
                return new DetectResult(false, null, "Face verification failed.", faceCount);
            }

            return new DetectResult(true, firstFace.get("face_token").getAsString(), null, faceCount);
        } catch (BusyFaceServiceException exception) {
            return new DetectResult(false, null, "Face service is busy right now. Please wait 3-5 seconds and retry.", 0);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.out.println("FaceRecognitionService detect error: " + exception.getMessage());
            return new DetectResult(false, null, "Face service is busy right now. Please wait 3-5 seconds and retry.", 0);
        }
    }

    public CompareResult compareFaces(String enrolledToken, String freshToken) {
        if (!isConfigured()) {
            return new CompareResult(false, false, 0.0, SAFE_DEFAULT_THRESHOLD, "Face++ n'est pas configure.");
        }

        if (isBlank(enrolledToken) || isBlank(freshToken)) {
            return new CompareResult(false, false, 0.0, SAFE_DEFAULT_THRESHOLD, "Face verification failed.");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("api_key", AppConfig.getFaceApiKey());
        form.put("api_secret", AppConfig.getFaceApiSecret());
        form.put("face_token1", enrolledToken);
        form.put("face_token2", freshToken);

        try {
            JsonObject json = postWithRetries("/facepp/v3/compare", form);
            double confidence = getDouble(json, "confidence", 0.0);
            double threshold = extractThreshold(json);
            boolean match = confidence >= threshold;
            return new CompareResult(true, match, confidence, threshold, match ? null : "Face verification failed.");
        } catch (BusyFaceServiceException exception) {
            return new CompareResult(false, false, 0.0, SAFE_DEFAULT_THRESHOLD, "Face service is busy right now. Please wait 3-5 seconds and retry.");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.out.println("FaceRecognitionService compare error: " + exception.getMessage());
            return new CompareResult(false, false, 0.0, SAFE_DEFAULT_THRESHOLD, "Face service is busy right now. Please wait 3-5 seconds and retry.");
        }
    }

    public OperationResult enrollCurrentUserFace(byte[] imageBytes) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }
        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }
        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }

        DetectResult detectResult = detectFaceToken(imageBytes);
        if (!detectResult.success()) {
            return OperationResult.failure(detectResult.message());
        }

        try {
            ensureFaceColumns();
            String query = """
                UPDATE `user`
                SET `face_id_enabled` = ?,
                    `face_id_token` = ?,
                    `face_id_enrolled_at` = NOW()
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, true);
                statement.setString(2, detectResult.faceToken());
                statement.setInt(3, currentUser.getId());
                statement.executeUpdate();
            }
            return OperationResult.success("Face ID enregistre avec succes.");
        } catch (SQLException exception) {
            System.out.println("FaceRecognitionService enroll SQL error: " + exception.getMessage());
            return OperationResult.failure("Erreur lors de l'enregistrement Face ID : " + exception.getMessage());
        }
    }

    public FaceLoginResult authenticateWithFace(String identity, byte[] imageBytes) {
        if (connection == null) {
            return new FaceLoginResult(false, null, "Connexion a la base indisponible.");
        }

        String normalizedIdentity = UserInputValidator.normalizeEmail(identity);
        if (normalizedIdentity.isBlank()) {
            return new FaceLoginResult(false, null, "Veuillez saisir votre email avant la verification Face ID.");
        }

        try {
            ensureFaceColumns();
            String query = "SELECT id, full_name, email, password, roles, status, face_id_enabled, face_id_token FROM `user` WHERE LOWER(email) = LOWER(?) LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, normalizedIdentity);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return new FaceLoginResult(false, null, "Aucun compte Face ID trouve pour cette adresse.");
                    }

                    boolean active = resultSet.getBoolean("status");
                    if (!active) {
                        return new FaceLoginResult(false, null, "Votre compte est suspendu. Contactez l'administration.");
                    }

                    boolean faceEnabled = resultSet.getBoolean("face_id_enabled");
                    String enrolledToken = resultSet.getString("face_id_token");
                    if (!faceEnabled || isBlank(enrolledToken)) {
                        return new FaceLoginResult(false, null, "Face ID n'est pas active sur ce compte.");
                    }

                    DetectResult detectResult = detectFaceToken(imageBytes);
                    if (!detectResult.success()) {
                        return new FaceLoginResult(false, null, detectResult.message());
                    }

                    CompareResult compareResult = compareFaces(enrolledToken, detectResult.faceToken());
                    if (!compareResult.success()) {
                        return new FaceLoginResult(false, null, compareResult.message());
                    }
                    if (!compareResult.match()) {
                        return new FaceLoginResult(false, null, "Face verification failed.");
                    }

                    User user = new User(
                        resultSet.getInt("id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        resultSet.getString("password"),
                        mapRole(resultSet.getString("roles"))
                    );

                    return new FaceLoginResult(true, user,
                        String.format("Face ID validee (confidence %.2f / threshold %.2f).", compareResult.confidence(), compareResult.threshold()));
                }
            }
        } catch (SQLException exception) {
            System.out.println("FaceRecognitionService face login SQL error: " + exception.getMessage());
            return new FaceLoginResult(false, null, "Erreur SQL lors de la verification Face ID.");
        }
    }

    public OperationResult removeCurrentUserFace() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }
        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }
        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }

        try {
            ensureFaceColumns();
            String query = """
                UPDATE `user`
                SET `face_id_enabled` = ?,
                    `face_id_token` = NULL,
                    `face_id_enrolled_at` = NULL
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, false);
                statement.setInt(2, currentUser.getId());
                statement.executeUpdate();
            }
            return OperationResult.success("Face ID supprime avec succes.");
        } catch (SQLException exception) {
            System.out.println("FaceRecognitionService remove SQL error: " + exception.getMessage());
            return OperationResult.failure("Erreur lors de la suppression Face ID : " + exception.getMessage());
        }
    }

    public FaceStatus getCurrentFaceStatus() {
        String engineStatus = isConfigured() ? "Moteur Face ID : Connecte" : "Moteur Face ID : Configuration manquante";
        User currentUser = UserSession.getCurrentUser();

        if (currentUser == null || currentUser.getId() <= 0 || connection == null) {
            return new FaceStatus(engineStatus, "Statut : Non enrole", false, "Jamais");
        }

        try {
            ensureFaceColumns();
            String query = "SELECT `face_id_enabled`, `face_id_enrolled_at` FROM `user` WHERE `id` = ? LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUser.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return new FaceStatus(engineStatus, "Statut : Non enrole", false, "Jamais");
                    }

                    boolean enabled = resultSet.getBoolean("face_id_enabled");
                    String enrolledAt = resultSet.getString("face_id_enrolled_at");
                    return new FaceStatus(
                        engineStatus,
                        enabled ? "Statut : Face ID active" : "Statut : Non enrole",
                        enabled,
                        enrolledAt == null ? "Jamais" : enrolledAt
                    );
                }
            }
        } catch (SQLException exception) {
            System.out.println("FaceRecognitionService status SQL error: " + exception.getMessage());
            return new FaceStatus(engineStatus, "Statut : Lecture Face ID impossible", false, "Inconnu");
        }
    }

    private void ensureFaceColumns() throws SQLException {
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `face_id_enabled` BOOLEAN NOT NULL DEFAULT FALSE");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `face_id_token` VARCHAR(255) NULL");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `face_id_enrolled_at` DATETIME NULL");
    }

    private void executeAlterIfNeeded(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private JsonObject postWithRetries(String endpoint, Map<String, String> formData) throws IOException, InterruptedException, BusyFaceServiceException {
        IOException lastIoException = null;
        InterruptedException lastInterruptedException = null;
        BusyFaceServiceException lastBusyException = null;

        for (int attempt = 0; attempt < RETRY_DELAYS_MS.length; attempt++) {
            long delay = RETRY_DELAYS_MS[attempt];
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw interruptedException;
                }
            }

            try {
                return postForm(endpoint, formData);
            } catch (BusyFaceServiceException busyException) {
                lastBusyException = busyException;
            } catch (IOException ioException) {
                lastIoException = ioException;
            } catch (InterruptedException interruptedException) {
                lastInterruptedException = interruptedException;
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (lastInterruptedException != null) {
            throw lastInterruptedException;
        }
        if (lastIoException != null) {
            throw lastIoException;
        }
        if (lastBusyException != null) {
            throw lastBusyException;
        }

        throw new IOException("Face service request failed.");
    }

    private JsonObject postForm(String endpoint, Map<String, String> formData) throws IOException, InterruptedException, BusyFaceServiceException {
        String formBody = buildFormBody(formData);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(AppConfig.getFaceApiBaseUrl().replaceAll("/+$", "") + endpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body();

        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception parseException) {
            throw new IOException("Face++ returned an unreadable response.");
        }

        String errorMessage = extractProviderError(json);
        if ("CONCURRENCY_LIMIT_EXCEEDED".equalsIgnoreCase(errorMessage)) {
            throw new BusyFaceServiceException();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (errorMessage != null && !errorMessage.isBlank()) {
                throw new IOException(errorMessage);
            }
            throw new IOException("Face++ returned HTTP " + response.statusCode());
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            throw new IOException(errorMessage);
        }

        return json;
    }

    private String buildFormBody(Map<String, String> formData) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String extractProviderError(JsonObject json) {
        if (json == null) {
            return null;
        }
        if (json.has("error_message")) {
            JsonElement element = json.get("error_message");
            if (element != null && !element.isJsonNull()) {
                return element.getAsString();
            }
        }
        return null;
    }

    private double extractThreshold(JsonObject json) {
        if (json == null || !json.has("thresholds") || !json.get("thresholds").isJsonObject()) {
            return SAFE_DEFAULT_THRESHOLD;
        }

        JsonObject thresholds = json.getAsJsonObject("thresholds");
        if (thresholds.has("1e-4")) {
            return getDouble(thresholds, "1e-4", SAFE_DEFAULT_THRESHOLD);
        }
        if (thresholds.has("1e-3")) {
            return getDouble(thresholds, "1e-3", SAFE_DEFAULT_THRESHOLD);
        }
        return SAFE_DEFAULT_THRESHOLD;
    }

    private double getDouble(JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String validateJpegCapture(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "Impossible de lire la capture camera.";
        }
        if (imageBytes.length > MAX_FACE_IMAGE_SIZE_BYTES) {
            return "La capture Face ID ne doit pas depasser 4 MB.";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private UserRole mapRole(String databaseRoles) {
        if (databaseRoles == null) {
            return UserRole.USER;
        }

        String normalized = databaseRoles.trim().toUpperCase();
        if (normalized.contains("ROLE_ADMIN") || normalized.equals("ADMIN")) {
            return UserRole.ADMIN;
        }
        if (normalized.contains("ROLE_TUTOR") || normalized.equals("TUTOR") || normalized.equals("TUTEUR")) {
            return UserRole.TUTOR;
        }
        return UserRole.USER;
    }

    private static final class BusyFaceServiceException extends Exception {
    }
}
