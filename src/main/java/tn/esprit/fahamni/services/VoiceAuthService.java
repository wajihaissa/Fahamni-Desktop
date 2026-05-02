package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserInputValidator;
import tn.esprit.fahamni.utils.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class VoiceAuthService {

    private static final int SAMPLE_RATE = 16_000;
    private static final int FRAME_COUNT = 20;
    private static final int BAND_COUNT = 6;
    private static final int FEATURES_PER_FRAME = 2 + BAND_COUNT;
    private static final int EXPECTED_FEATURE_COUNT = FRAME_COUNT * FEATURES_PER_FRAME + 4;
    private static final double MIN_RMS = 0.014;
    private static final double MATCH_THRESHOLD = 0.90;

    public record VoiceStatus(
        boolean enabled,
        String status,
        String enrolledAt
    ) {
    }

    public record VoiceLoginResult(
        boolean success,
        User user,
        String message
    ) {
    }

    private final Connection connection;

    public VoiceAuthService() {
        this.connection = MyDataBase.getInstance().getCnx();
    }

    public OperationResult enrollCurrentUserVoice(byte[] audioBytes) {
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

        VoicePrint voicePrint = buildVoicePrint(audioBytes);
        if (!voicePrint.valid()) {
            return OperationResult.failure("Voix trop faible ou capture invalide. Rapprochez-vous du micro et recommencez.");
        }

        try {
            ensureVoiceColumns();
            String query = """
                UPDATE `user`
                SET `voice_login_enabled` = ?,
                    `voice_print` = ?,
                    `voice_enrolled_at` = NOW()
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, true);
                statement.setString(2, serialize(voicePrint.features()));
                statement.setInt(3, currentUser.getId());
                statement.executeUpdate();
            }
            return OperationResult.success("Voice Pass active. Vous pouvez maintenant vous connecter avec votre voix.");
        } catch (SQLException exception) {
            System.out.println("VoiceAuthService enroll SQL error: " + exception.getMessage());
            return OperationResult.failure("Erreur lors de l'activation Voice Pass : " + exception.getMessage());
        }
    }

    public OperationResult removeCurrentUserVoice() {
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
            ensureVoiceColumns();
            String query = """
                UPDATE `user`
                SET `voice_login_enabled` = ?,
                    `voice_print` = NULL,
                    `voice_enrolled_at` = NULL
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, false);
                statement.setInt(2, currentUser.getId());
                statement.executeUpdate();
            }
            return OperationResult.success("Voice Pass desactive.");
        } catch (SQLException exception) {
            System.out.println("VoiceAuthService remove SQL error: " + exception.getMessage());
            return OperationResult.failure("Erreur lors de la desactivation Voice Pass : " + exception.getMessage());
        }
    }

    public VoiceLoginResult authenticateWithVoice(String identity, byte[] audioBytes) {
        if (connection == null) {
            return new VoiceLoginResult(false, null, "Connexion a la base indisponible.");
        }

        String normalizedIdentity = UserInputValidator.normalizeEmail(identity);
        if (normalizedIdentity == null || normalizedIdentity.isBlank()) {
            return new VoiceLoginResult(false, null, "Saisissez votre email avant la verification vocale.");
        }

        VoicePrint candidatePrint = buildVoicePrint(audioBytes);
        if (!candidatePrint.valid()) {
            return new VoiceLoginResult(false, null, "Voix trop faible ou capture invalide. Reessayez dans un endroit calme.");
        }

        try {
            ensureVoiceColumns();
            String query = """
                SELECT id, full_name, email, password, roles, status,
                       COALESCE(registration_status, 'APPROVED') AS registration_status,
                       voice_login_enabled, voice_print
                FROM `user`
                WHERE LOWER(email) = LOWER(?)
                LIMIT 1
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, normalizedIdentity);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return new VoiceLoginResult(false, null, "Aucun compte Voice Pass trouve pour cette adresse.");
                    }

                    String reviewStatus = normalizeReviewStatus(resultSet.getString("registration_status"));
                    if ("PENDING_REVIEW".equals(reviewStatus)) {
                        return new VoiceLoginResult(false, null, "Votre compte est en attente de validation administrative.");
                    }
                    if ("DECLINED".equals(reviewStatus)) {
                        return new VoiceLoginResult(false, null, "Votre inscription a ete refusee. Contactez l'administration.");
                    }
                    if (!resultSet.getBoolean("status")) {
                        return new VoiceLoginResult(false, null, "Votre compte est suspendu. Contactez l'administration.");
                    }
                    if (!resultSet.getBoolean("voice_login_enabled")) {
                        return new VoiceLoginResult(false, null, "Voice Pass n'est pas active sur ce compte.");
                    }

                    double[] storedFeatures = parse(resultSet.getString("voice_print"));
                    if (storedFeatures.length != EXPECTED_FEATURE_COUNT) {
                        return new VoiceLoginResult(false, null, "Voice Pass doit etre reconfigure sur ce compte.");
                    }

                    double score = similarity(storedFeatures, candidatePrint.features());
                    if (score < MATCH_THRESHOLD) {
                        return new VoiceLoginResult(false, null, "La voix ne correspond pas assez au Voice Pass enregistre.");
                    }

                    User user = new User(
                        resultSet.getInt("id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        resultSet.getString("password"),
                        mapRole(resultSet.getString("roles"))
                    );
                    return new VoiceLoginResult(true, user, String.format(Locale.US, "Voice Pass valide (score %.2f).", score));
                }
            }
        } catch (SQLException exception) {
            System.out.println("VoiceAuthService login SQL error: " + exception.getMessage());
            return new VoiceLoginResult(false, null, "Erreur SQL lors de la verification vocale.");
        }
    }

    public VoiceStatus getCurrentVoiceStatus() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0 || connection == null) {
            return new VoiceStatus(false, "Statut : Non enrole", "Jamais");
        }

        try {
            ensureVoiceColumns();
            String query = "SELECT `voice_login_enabled`, `voice_enrolled_at` FROM `user` WHERE `id` = ? LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUser.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return new VoiceStatus(false, "Statut : Non enrole", "Jamais");
                    }
                    boolean enabled = resultSet.getBoolean("voice_login_enabled");
                    String enrolledAt = resultSet.getString("voice_enrolled_at");
                    return new VoiceStatus(
                        enabled,
                        enabled ? "Statut : Voice Pass actif" : "Statut : Non enrole",
                        enrolledAt == null ? "Jamais" : enrolledAt
                    );
                }
            }
        } catch (SQLException exception) {
            System.out.println("VoiceAuthService status SQL error: " + exception.getMessage());
            return new VoiceStatus(false, "Statut : Lecture Voice Pass impossible", "Inconnu");
        }
    }

    private void ensureVoiceColumns() throws SQLException {
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `voice_login_enabled` BOOLEAN NOT NULL DEFAULT FALSE");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `voice_print` TEXT NULL");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `voice_enrolled_at` DATETIME NULL");
    }

    private void executeAlterIfNeeded(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private VoicePrint buildVoicePrint(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < 4096) {
            return new VoicePrint(false, new double[0]);
        }

        int sampleCount = audioBytes.length / 2;
        double[] samples = new double[sampleCount];
        double squareSum = 0.0;
        int zeroCrossings = 0;
        double peak = 0.0;

        for (int i = 0; i < sampleCount; i++) {
            int low = audioBytes[i * 2] & 0xFF;
            int high = audioBytes[i * 2 + 1];
            short value = (short) ((high << 8) | low);
            double normalized = value / 32768.0;
            samples[i] = normalized;
            squareSum += normalized * normalized;
            peak = Math.max(peak, Math.abs(normalized));
            if (i > 0 && Math.signum(samples[i - 1]) != Math.signum(normalized)) {
                zeroCrossings++;
            }
        }

        double rms = Math.sqrt(squareSum / sampleCount);
        if (rms < MIN_RMS || peak < 0.04) {
            return new VoicePrint(false, new double[0]);
        }

        double[] features = new double[EXPECTED_FEATURE_COUNT];
        int frameSize = Math.max(1, sampleCount / FRAME_COUNT);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int start = frame * frameSize;
            int end = frame == FRAME_COUNT - 1 ? sampleCount : Math.min(sampleCount, start + frameSize);
            fillFrameFeatures(samples, start, end, features, frame * FEATURES_PER_FRAME);
        }

        int globalOffset = FRAME_COUNT * FEATURES_PER_FRAME;
        features[globalOffset] = rms;
        features[globalOffset + 1] = zeroCrossings / (double) sampleCount;
        features[globalOffset + 2] = peak;
        features[globalOffset + 3] = averageSlope(samples);
        return new VoicePrint(true, features);
    }

    private void fillFrameFeatures(double[] samples, int start, int end, double[] features, int offset) {
        int length = Math.max(1, end - start);
        double energy = 0.0;
        int zeroCrossings = 0;

        for (int i = start; i < end; i++) {
            energy += Math.abs(samples[i]);
            if (i > start && Math.signum(samples[i - 1]) != Math.signum(samples[i])) {
                zeroCrossings++;
            }
        }

        features[offset] = energy / length;
        features[offset + 1] = zeroCrossings / (double) length;

        double[] bandEnergies = new double[BAND_COUNT];
        int[] frequencies = {250, 500, 900, 1400, 2200, 3400};
        double totalBandEnergy = 0.000001;
        for (int band = 0; band < BAND_COUNT; band++) {
            bandEnergies[band] = goertzelPower(samples, start, end, frequencies[band]);
            totalBandEnergy += bandEnergies[band];
        }

        for (int band = 0; band < BAND_COUNT; band++) {
            features[offset + 2 + band] = bandEnergies[band] / totalBandEnergy;
        }
    }

    private double goertzelPower(double[] samples, int start, int end, int targetFrequency) {
        int length = Math.max(1, end - start);
        double normalizedFrequency = targetFrequency / (double) SAMPLE_RATE;
        double coefficient = 2.0 * Math.cos(2.0 * Math.PI * normalizedFrequency);
        double previous = 0.0;
        double previous2 = 0.0;

        for (int i = start; i < end; i++) {
            double current = samples[i] + coefficient * previous - previous2;
            previous2 = previous;
            previous = current;
        }

        return previous2 * previous2 + previous * previous - coefficient * previous * previous2;
    }

    private double averageSlope(double[] samples) {
        if (samples.length < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < samples.length; i++) {
            total += Math.abs(samples[i] - samples[i - 1]);
        }
        return total / (samples.length - 1);
    }

    private double similarity(double[] first, double[] second) {
        if (first.length != EXPECTED_FEATURE_COUNT || second.length != EXPECTED_FEATURE_COUNT) {
            return 0.0;
        }

        double frameDistance = 0.0;
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int offset = frame * FEATURES_PER_FRAME;
            double energyDistance = Math.abs(first[offset] - second[offset]) / 0.20;
            double zcrDistance = Math.abs(first[offset + 1] - second[offset + 1]) / 0.28;
            double bandDistance = 0.0;

            for (int band = 0; band < BAND_COUNT; band++) {
                bandDistance += Math.abs(first[offset + 2 + band] - second[offset + 2 + band]);
            }

            frameDistance += Math.min(1.0, energyDistance * 0.25 + zcrDistance * 0.20 + bandDistance * 0.55);
        }

        double averageFrameDistance = frameDistance / FRAME_COUNT;
        int globalOffset = FRAME_COUNT * FEATURES_PER_FRAME;
        double globalDistance =
            Math.abs(first[globalOffset] - second[globalOffset]) / 0.20 * 0.35
                + Math.abs(first[globalOffset + 1] - second[globalOffset + 1]) / 0.28 * 0.25
                + Math.abs(first[globalOffset + 2] - second[globalOffset + 2]) / 0.30 * 0.20
                + Math.abs(first[globalOffset + 3] - second[globalOffset + 3]) / 0.10 * 0.20;

        double combinedDistance = averageFrameDistance * 0.76 + Math.min(1.0, globalDistance) * 0.24;
        return Math.max(0.0, Math.min(1.0, 1.0 - combinedDistance));
    }

    private String serialize(double[] features) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < features.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US, "%.6f", features[i]));
        }
        return builder.toString();
    }

    private double[] parse(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return new double[0];
        }
        String[] parts = serialized.split(",");
        double[] values = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Double.parseDouble(parts[i]);
            }
            return values;
        } catch (NumberFormatException exception) {
            return new double[0];
        }
    }

    private String normalizeReviewStatus(String reviewStatus) {
        if (reviewStatus == null || reviewStatus.trim().isEmpty()) {
            return "APPROVED";
        }
        return reviewStatus.trim().toUpperCase().replace('-', '_').replace(' ', '_');
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

    private record VoicePrint(boolean valid, double[] features) {
    }
}
