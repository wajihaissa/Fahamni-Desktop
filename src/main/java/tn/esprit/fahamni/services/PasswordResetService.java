package tn.esprit.fahamni.services;

import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserInputValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class PasswordResetService {

    private static final String RESET_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RESET_CODE_LENGTH = 10;
    private static final int RESET_EXPIRY_MINUTES = 30;

    private final Connection connection;
    private final MailService mailService;
    private final SecureRandom secureRandom;

    public PasswordResetService() {
        this.connection = MyDataBase.getInstance().getCnx();
        this.mailService = new MailService();
        this.secureRandom = new SecureRandom();
    }

    public OperationResult requestReset(String email) {
        String emailError = UserInputValidator.validateEmail(email);
        if (emailError != null) {
            return OperationResult.failure(emailError);
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }

        if (!mailService.isConfigured()) {
            return OperationResult.failure("Le service email n'est pas configure. Ajoutez les variables MAILER_HOST, MAILER_PORT, MAILER_USERNAME, MAILER_PASSWORD et MAILER_FROM.");
        }

        try {
            ensureResetTable();
            UserResetTarget target = findUser(UserInputValidator.normalizeEmail(email));
            if (target == null) {
                return OperationResult.success("Si cette adresse existe, un email de reinitialisation a ete envoye.");
            }

            String resetCode = generateResetCode();
            invalidateActiveTokens(target.userId());
            storeResetCode(target.userId(), resetCode);

            OperationResult mailResult = mailService.sendPasswordResetCode(target.email(), target.fullName(), resetCode);
            if (!mailResult.isSuccess()) {
                return mailResult;
            }

            return OperationResult.success("Si cette adresse existe, un email de reinitialisation a ete envoye.");
        } catch (SQLException e) {
            System.out.println("PasswordResetService SQL error: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la preparation de la reinitialisation : " + e.getMessage());
        }
    }

    private void ensureResetTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS password_reset_token (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                token_hash VARCHAR(64) NOT NULL,
                expires_at DATETIME NOT NULL,
                used_at DATETIME NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_password_reset_user (user_id),
                INDEX idx_password_reset_hash (token_hash),
                CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
            )
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate foreign key")) {
                return;
            }
            throw e;
        }
    }

    private UserResetTarget findUser(String normalizedEmail) throws SQLException {
        String query = "SELECT id, email, full_name FROM user WHERE LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserResetTarget(
                    resultSet.getInt("id"),
                    resultSet.getString("email"),
                    resultSet.getString("full_name")
                );
            }
        }
    }

    private void invalidateActiveTokens(int userId) throws SQLException {
        String query = "UPDATE password_reset_token SET used_at = NOW() WHERE user_id = ? AND used_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }

    private void storeResetCode(int userId, String resetCode) throws SQLException {
        String query = "INSERT INTO password_reset_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            statement.setString(2, hashToken(resetCode));
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusMinutes(RESET_EXPIRY_MINUTES)));
            statement.executeUpdate();
        }
    }

    private String generateResetCode() {
        StringBuilder builder = new StringBuilder(RESET_CODE_LENGTH);
        for (int i = 0; i < RESET_CODE_LENGTH; i++) {
            builder.append(RESET_ALPHABET.charAt(secureRandom.nextInt(RESET_ALPHABET.length())));
        }
        return builder.toString();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private record UserResetTarget(int userId, String email, String fullName) {
    }
}
