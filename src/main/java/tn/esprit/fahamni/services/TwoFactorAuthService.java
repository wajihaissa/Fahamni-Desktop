package tn.esprit.fahamni.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.commons.codec.binary.Base32;
import org.mindrot.jbcrypt.BCrypt;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.utils.AppConfig;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserSession;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TwoFactorAuthService {

    private static final String ISSUER = "FAHIMNI";
    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int WINDOW_STEPS = 1;
    private static final int LOGIN_TTL_SECONDS = 300;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private static final Map<Integer, PendingTwoFactorSetup> PENDING_SETUPS = new ConcurrentHashMap<>();
    private static final Map<Integer, LoginChallenge> LOGIN_CHALLENGES = new ConcurrentHashMap<>();

    public record SetupPayload(String secret, String otpauthUri, Image qrCodeImage) {
    }

    public record SetupStartResult(boolean success, String message, SetupPayload payload) {
    }

    public record SetupConfirmResult(boolean success, String message, List<String> recoveryCodes) {
    }

    public record TwoFactorStatus(boolean enabled, String confirmedAt, boolean setupPending, SetupPayload pendingPayload) {
    }

    public record ChallengeStartResult(boolean success, boolean requiresTwoFactor, String message) {
    }

    public record ChallengeVerifyResult(boolean success, boolean terminal, String message) {
    }

    private record PendingTwoFactorSetup(String secret, String otpauthUri, Image qrCodeImage, LocalDateTime createdAt) {
    }

    private record LoginChallenge(int userId, LocalDateTime expiresAt, int attempts) {
    }

    private final Connection connection;
    private final Gson gson;
    private final SecureRandom secureRandom;
    private final Base32 base32;

    public TwoFactorAuthService() {
        this.connection = MyDataBase.getInstance().getCnx();
        this.gson = new Gson();
        this.secureRandom = new SecureRandom();
        this.base32 = new Base32();
    }

    public SetupStartResult startSetupForCurrentUser() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return new SetupStartResult(false, "Aucun utilisateur connecte.", null);
        }
        if (currentUser.getId() <= 0) {
            return new SetupStartResult(false, "Ce compte n'est pas synchronise avec la base de donnees.", null);
        }
        if (connection == null) {
            return new SetupStartResult(false, "Connexion a la base indisponible.", null);
        }
        if (!AppConfig.isAppSecretConfigured()) {
            return new SetupStartResult(false, "APP_SECRET est requis pour securiser la 2FA.", null);
        }

        try {
            ensureTwoFactorColumns();
            byte[] rawSecret = new byte[SECRET_BYTES];
            secureRandom.nextBytes(rawSecret);
            String secret = base32.encodeToString(rawSecret).replace("=", "");
            String email = currentUser.getEmail();
            String otpauthUri = buildOtpAuthUri(email, secret);
            Image qrCode = buildQrCodeImage(otpauthUri);

            SetupPayload payload = new SetupPayload(secret, otpauthUri, qrCode);
            PENDING_SETUPS.put(currentUser.getId(), new PendingTwoFactorSetup(secret, otpauthUri, qrCode, LocalDateTime.now()));
            return new SetupStartResult(true, "Configuration 2FA prete. Scannez le QR code et confirmez avec un code 6 chiffres.", payload);
        } catch (SQLException exception) {
            System.out.println("TwoFactorAuthService start setup SQL error: " + exception.getMessage());
            return new SetupStartResult(false, "Impossible de preparer la configuration 2FA : " + exception.getMessage(), null);
        } catch (WriterException exception) {
            System.out.println("TwoFactorAuthService QR error: " + exception.getMessage());
            return new SetupStartResult(false, "Impossible de generer le QR code 2FA.", null);
        }
    }

    public SetupConfirmResult confirmSetupForCurrentUser(String code) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return new SetupConfirmResult(false, "Aucun utilisateur connecte.", Collections.emptyList());
        }

        PendingTwoFactorSetup pendingSetup = PENDING_SETUPS.get(currentUser.getId());
        if (pendingSetup == null) {
            return new SetupConfirmResult(false, "Aucune configuration 2FA en attente. Cliquez d'abord sur Start 2FA Setup.", Collections.emptyList());
        }

        if (!isValidTotpCode(pendingSetup.secret(), code)) {
            return new SetupConfirmResult(false, "Code 2FA invalide. Verifiez votre application d'authentification.", Collections.emptyList());
        }

        try {
            ensureTwoFactorColumns();
            String encryptedSecret = encryptSymfonyCompatible(pendingSetup.secret());
            List<String> recoveryCodes = generateRecoveryCodes();
            List<String> hashedRecoveryCodes = hashRecoveryCodes(recoveryCodes);

            String query = """
                UPDATE `user`
                SET `two_factor_enabled` = ?,
                    `two_factor_secret` = ?,
                    `two_factor_recovery_codes` = ?,
                    `two_factor_confirmed_at` = NOW()
                WHERE `id` = ?
                """;

            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, true);
                statement.setString(2, encryptedSecret);
                statement.setString(3, gson.toJson(hashedRecoveryCodes));
                statement.setInt(4, currentUser.getId());
                statement.executeUpdate();
            }

            PENDING_SETUPS.remove(currentUser.getId());
            return new SetupConfirmResult(true, "2FA activee avec succes. Conservez vos codes de recuperation dans un endroit sur.", recoveryCodes);
        } catch (SQLException exception) {
            System.out.println("TwoFactorAuthService confirm setup SQL error: " + exception.getMessage());
            return new SetupConfirmResult(false, "Erreur lors de l'activation 2FA : " + exception.getMessage(), Collections.emptyList());
        } catch (GeneralSecurityException exception) {
            System.out.println("TwoFactorAuthService secret encryption error: " + exception.getMessage());
            return new SetupConfirmResult(false, "Impossible de chiffrer la cle 2FA. Verifiez APP_SECRET.", Collections.emptyList());
        }
    }

    public TwoFactorStatus getCurrentStatus() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0 || connection == null) {
            return new TwoFactorStatus(false, "Never", false, null);
        }

        PendingTwoFactorSetup pendingSetup = PENDING_SETUPS.get(currentUser.getId());
        SetupPayload payload = pendingSetup == null ? null : new SetupPayload(pendingSetup.secret(), pendingSetup.otpauthUri(), pendingSetup.qrCodeImage());

        try {
            ensureTwoFactorColumns();
            String query = "SELECT `two_factor_enabled`, `two_factor_confirmed_at` FROM `user` WHERE `id` = ? LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUser.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return new TwoFactorStatus(false, "Never", pendingSetup != null, payload);
                    }
                    return new TwoFactorStatus(
                        resultSet.getBoolean("two_factor_enabled"),
                        valueOrDefault(resultSet.getString("two_factor_confirmed_at"), "Never"),
                        pendingSetup != null,
                        payload
                    );
                }
            }
        } catch (SQLException exception) {
            System.out.println("TwoFactorAuthService status SQL error: " + exception.getMessage());
            return new TwoFactorStatus(false, "Unknown", pendingSetup != null, payload);
        }
    }

    public OperationResult disableCurrentUser(String codeOrRecovery) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }
        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }

        String normalized = normalizeVerificationCode(codeOrRecovery);
        if (normalized.isBlank()) {
            return OperationResult.failure("Veuillez saisir un code TOTP ou un recovery code.");
        }

        try {
            ensureTwoFactorColumns();
            StoredTwoFactorData storedData = loadStoredTwoFactorData(currentUser.getId());
            if (!storedData.enabled()) {
                return OperationResult.failure("La 2FA n'est pas activee sur ce compte.");
            }

            boolean verified = false;
            List<String> updatedHashes = new ArrayList<>(storedData.recoveryCodeHashes());
            String decryptedSecret = null;
            if (!valueOrDefault(storedData.encryptedSecret(), "").isBlank()) {
                decryptedSecret = decryptSymfonyCompatible(storedData.encryptedSecret());
            }

            if (decryptedSecret != null && isValidTotpCode(decryptedSecret, normalized)) {
                verified = true;
            } else {
                int index = findMatchingRecoveryCodeIndex(updatedHashes, normalized);
                if (index >= 0) {
                    updatedHashes.remove(index);
                    verified = true;
                }
            }

            if (!verified) {
                return OperationResult.failure("Code de desactivation invalide.");
            }

            String query = """
                UPDATE `user`
                SET `two_factor_enabled` = ?,
                    `two_factor_secret` = NULL,
                    `two_factor_recovery_codes` = NULL,
                    `two_factor_confirmed_at` = NULL
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, false);
                statement.setInt(2, currentUser.getId());
                statement.executeUpdate();
            }

            PENDING_SETUPS.remove(currentUser.getId());
            LOGIN_CHALLENGES.remove(currentUser.getId());
            return OperationResult.success("2FA desactivee avec succes.");
        } catch (SQLException exception) {
            System.out.println("TwoFactorAuthService disable SQL error: " + exception.getMessage());
            return OperationResult.failure("Erreur lors de la desactivation 2FA : " + exception.getMessage());
        } catch (GeneralSecurityException exception) {
            System.out.println("TwoFactorAuthService disable crypto error: " + exception.getMessage());
            return OperationResult.failure("Impossible de verifier la cle 2FA. Verifiez APP_SECRET.");
        }
    }

    public boolean requiresTwoFactor(User user) {
        if (user == null || user.getId() <= 0 || connection == null) {
            return false;
        }

        try {
            ensureTwoFactorColumns();
            String query = "SELECT `two_factor_enabled`, `two_factor_secret` FROM `user` WHERE `id` = ? LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, user.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next()
                        && resultSet.getBoolean("two_factor_enabled")
                        && !valueOrDefault(resultSet.getString("two_factor_secret"), "").isBlank();
                }
            }
        } catch (SQLException exception) {
            System.out.println("TwoFactorAuthService requiresTwoFactor SQL error: " + exception.getMessage());
            return false;
        }
    }

    public ChallengeStartResult startLoginChallenge(User user) {
        if (user == null || user.getId() <= 0) {
            return new ChallengeStartResult(false, false, "Compte invalide pour le challenge 2FA.");
        }
        if (!requiresTwoFactor(user)) {
            LOGIN_CHALLENGES.remove(user.getId());
            return new ChallengeStartResult(true, false, "2FA non requise.");
        }

        LOGIN_CHALLENGES.put(user.getId(), new LoginChallenge(user.getId(), LocalDateTime.now().plusSeconds(LOGIN_TTL_SECONDS), 0));
        return new ChallengeStartResult(true, true, "2FA requise. Entrez votre code d'authentification.");
    }

    public ChallengeVerifyResult verifyLoginChallenge(User user, String codeOrRecovery) {
        if (user == null || user.getId() <= 0) {
            return new ChallengeVerifyResult(false, true, "Challenge 2FA invalide.");
        }

        LoginChallenge challenge = LOGIN_CHALLENGES.get(user.getId());
        if (challenge == null) {
            return new ChallengeVerifyResult(false, true, "Challenge 2FA introuvable. Recommencez la connexion.");
        }

        if (LocalDateTime.now().isAfter(challenge.expiresAt())) {
            LOGIN_CHALLENGES.remove(user.getId());
            return new ChallengeVerifyResult(false, true, "Le challenge 2FA a expire. Reconnectez-vous.");
        }

        if (challenge.attempts() >= MAX_LOGIN_ATTEMPTS) {
            LOGIN_CHALLENGES.remove(user.getId());
            return new ChallengeVerifyResult(false, true, "Nombre maximal d'essais 2FA atteint. Reconnectez-vous.");
        }

        String normalized = normalizeVerificationCode(codeOrRecovery);
        if (normalized.isBlank()) {
            incrementAttempt(user.getId(), challenge);
            return new ChallengeVerifyResult(false, false, "Veuillez saisir un code TOTP ou un recovery code.");
        }

        try {
            StoredTwoFactorData storedData = loadStoredTwoFactorData(user.getId());
            if (!storedData.enabled()) {
                LOGIN_CHALLENGES.remove(user.getId());
                return new ChallengeVerifyResult(true, true, "2FA non requise.");
            }

            String decryptedSecret = decryptSymfonyCompatible(storedData.encryptedSecret());
            if (isValidTotpCode(decryptedSecret, normalized)) {
                LOGIN_CHALLENGES.remove(user.getId());
                return new ChallengeVerifyResult(true, true, "Code 2FA valide.");
            }

            List<String> updatedHashes = new ArrayList<>(storedData.recoveryCodeHashes());
            int recoveryIndex = findMatchingRecoveryCodeIndex(updatedHashes, normalized);
            if (recoveryIndex >= 0) {
                updatedHashes.remove(recoveryIndex);
                saveRecoveryHashes(user.getId(), updatedHashes);
                LOGIN_CHALLENGES.remove(user.getId());
                return new ChallengeVerifyResult(true, true, "Recovery code valide.");
            }

            LoginChallenge updatedChallenge = incrementAttempt(user.getId(), challenge);
            if (updatedChallenge.attempts() >= MAX_LOGIN_ATTEMPTS) {
                LOGIN_CHALLENGES.remove(user.getId());
                return new ChallengeVerifyResult(false, true, "Nombre maximal d'essais 2FA atteint. Reconnectez-vous.");
            }

            return new ChallengeVerifyResult(false, false,
                "Code 2FA invalide. Il vous reste " + (MAX_LOGIN_ATTEMPTS - updatedChallenge.attempts()) + " essai(s).");
        } catch (SQLException exception) {
            System.out.println("TwoFactorAuthService verify challenge SQL error: " + exception.getMessage());
            LOGIN_CHALLENGES.remove(user.getId());
            return new ChallengeVerifyResult(false, true, "Erreur SQL pendant la verification 2FA.");
        } catch (GeneralSecurityException exception) {
            System.out.println("TwoFactorAuthService verify challenge crypto error: " + exception.getMessage());
            LOGIN_CHALLENGES.remove(user.getId());
            return new ChallengeVerifyResult(false, true, "Impossible de dechiffrer la cle 2FA. Verifiez APP_SECRET.");
        }
    }

    private LoginChallenge incrementAttempt(int userId, LoginChallenge challenge) {
        LoginChallenge updated = new LoginChallenge(userId, challenge.expiresAt(), challenge.attempts() + 1);
        LOGIN_CHALLENGES.put(userId, updated);
        return updated;
    }

    private StoredTwoFactorData loadStoredTwoFactorData(int userId) throws SQLException {
        String query = "SELECT `two_factor_enabled`, `two_factor_secret`, `two_factor_recovery_codes` FROM `user` WHERE `id` = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new StoredTwoFactorData(false, null, Collections.emptyList());
                }

                boolean enabled = resultSet.getBoolean("two_factor_enabled");
                String encryptedSecret = resultSet.getString("two_factor_secret");
                String rawJson = resultSet.getString("two_factor_recovery_codes");
                List<String> hashes = rawJson == null || rawJson.isBlank()
                    ? new ArrayList<>()
                    : new ArrayList<>(gson.fromJson(rawJson, STRING_LIST_TYPE));
                return new StoredTwoFactorData(enabled, encryptedSecret, hashes);
            }
        }
    }

    private void saveRecoveryHashes(int userId, List<String> hashes) throws SQLException {
        String query = "UPDATE `user` SET `two_factor_recovery_codes` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, gson.toJson(hashes));
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    private void ensureTwoFactorColumns() throws SQLException {
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `two_factor_enabled` BOOLEAN NOT NULL DEFAULT FALSE");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `two_factor_secret` TEXT NULL");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `two_factor_recovery_codes` JSON NULL");
        executeAlterIfNeeded("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `two_factor_confirmed_at` DATETIME NULL");
    }

    private void executeAlterIfNeeded(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private String buildOtpAuthUri(String email, String secret) {
        String account = URLEncoder.encode(ISSUER + ":" + email, StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + account
            + "?secret=" + secret
            + "&issuer=" + issuer
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private Image buildQrCodeImage(String otpauthUri) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new MultiFormatWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, 220, 220, hints);
        MatrixToImageConfig config = new MatrixToImageConfig(0xFF102244, 0xFFF5FAFF);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix, config);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    private boolean isValidTotpCode(String secret, String rawCode) {
        String normalizedCode = normalizeDigits(rawCode);
        if (normalizedCode.length() != CODE_DIGITS) {
            return false;
        }

        byte[] secretBytes = base32.decode(secret);
        long currentStep = System.currentTimeMillis() / 1000L / PERIOD_SECONDS;
        for (long offset = -WINDOW_STEPS; offset <= WINDOW_STEPS; offset++) {
            String generated = generateTotp(secretBytes, currentStep + offset);
            if (normalizedCode.equals(generated)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotp(byte[] secretBytes, long counter) {
        try {
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to compute TOTP", exception);
        }
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            codes.add(randomSegment(4) + "-" + randomSegment(4) + "-" + randomSegment(4));
        }
        return codes;
    }

    private String randomSegment(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private List<String> hashRecoveryCodes(List<String> recoveryCodes) {
        List<String> hashes = new ArrayList<>(recoveryCodes.size());
        for (String code : recoveryCodes) {
            hashes.add(BCrypt.hashpw(code, BCrypt.gensalt()));
        }
        return hashes;
    }

    private int findMatchingRecoveryCodeIndex(List<String> hashes, String candidate) {
        for (int index = 0; index < hashes.size(); index++) {
            if (BCrypt.checkpw(candidate, hashes.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private String encryptSymfonyCompatible(String plaintextSecret) throws GeneralSecurityException {
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, buildAesKey(), new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(payload);
    }

    private String decryptSymfonyCompatible(String storedCiphertext) throws GeneralSecurityException {
        if (storedCiphertext == null || storedCiphertext.isBlank()) {
            return null;
        }

        byte[] payload = Base64.getDecoder().decode(storedCiphertext);
        if (payload.length < 17) {
            throw new GeneralSecurityException("Cipher payload too short.");
        }

        byte[] iv = new byte[16];
        byte[] ciphertext = new byte[payload.length - 16];
        System.arraycopy(payload, 0, iv, 0, 16);
        System.arraycopy(payload, 16, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, buildAesKey(), new IvParameterSpec(iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private SecretKeySpec buildAesKey() throws GeneralSecurityException {
        String appSecret = AppConfig.getAppSecret();
        if (appSecret.isBlank()) {
            throw new GeneralSecurityException("APP_SECRET is empty.");
        }
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(appSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String normalizeDigits(String code) {
        return code == null ? "" : code.replaceAll("\\D", "");
    }

    private String normalizeVerificationCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record StoredTwoFactorData(boolean enabled, String encryptedSecret, List<String> recoveryCodeHashes) {
    }
}
