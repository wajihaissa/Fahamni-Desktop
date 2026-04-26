package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.utils.JwtService;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.PasswordSecurity;
import tn.esprit.fahamni.utils.UserInputValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {

    private static final String REVIEW_PENDING_DB = "PENDING_REVIEW";
    private static final String REVIEW_DECLINED_DB = "DECLINED";

    private final Connection connection;
    private String lastAuthenticationError;

    public AuthService() {
        connection = MyDataBase.getInstance().getCnx();
    }

    public User authenticate(String email, String password) {
        lastAuthenticationError = null;

        if (isBlank(email) || isBlank(password)) {
            return null;
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        Connection c = MyDataBase.getInstance().getCnx();
        if (c == null) {
            lastAuthenticationError = "Connexion a la base indisponible.";
            return null;
        }

        try {
            ensureAuthUserSchema(c);
        } catch (SQLException exception) {
            lastAuthenticationError = "Impossible de preparer la verification du compte : " + exception.getMessage();
            return null;
        }

        String[] nameCols = {"full_name", "fullName", "name", "username"};
        String[] roleCols = {"roles", "role"};
        for (String nameCol : nameCols) {
            for (String roleCol : roleCols) {
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, " + nameCol + ", " + roleCol + ", password, status, " +
                        "COALESCE(registration_status, 'APPROVED') AS registration_status " +
                        "FROM user WHERE email = ?")) {
                    ps.setString(1, normalizedEmail);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int userId = rs.getInt("id");
                            String fullName = rs.getString(nameCol);
                            String roleStr = rs.getString(roleCol);
                            String dbPassword = rs.getString("password");
                            String reviewStatus = normalizeReviewStatus(rs.getString("registration_status"));

                            if (!PasswordSecurity.matches(password, dbPassword)) {
                                lastAuthenticationError = "Email ou mot de passe invalide.";
                                return null;
                            }

                            if (REVIEW_PENDING_DB.equals(reviewStatus)) {
                                lastAuthenticationError = "Votre compte est en attente de validation administrative.";
                                return null;
                            }

                            if (REVIEW_DECLINED_DB.equals(reviewStatus)) {
                                lastAuthenticationError = "Votre inscription a ete refusee. Contactez l'administration.";
                                return null;
                            }

                            boolean active = true;
                            try {
                                active = rs.getBoolean("status");
                            } catch (SQLException ignored) {
                            }
                            if (!active) {
                                lastAuthenticationError = "Votre compte est suspendu. Contactez l'administration.";
                                return null;
                            }

                            UserRole role = mapRole(roleStr);
                            System.out.println("AuthService: connexion BD reussie pour " + normalizedEmail + " (id=" + userId + ")");
                            return new User(userId, fullName, normalizedEmail, dbPassword, role);
                        }
                    }
                } catch (SQLException ignored) {
                    // Try the next schema variant.
                }
            }
        }

        System.out.println("AuthService: utilisateur non trouve pour " + normalizedEmail);
        return null;
    }

    public String getLastAuthenticationError() {
        return lastAuthenticationError;
    }

    public String issueJwt(User user) {
        if (user == null) {
            return null;
        }
        return JwtService.generateToken(user);
    }

    public boolean isJwtValidForUser(String token, User user) {
        return JwtService.isTokenValidForUser(token, user);
    }

    public OperationResult register(String fullName, String email, String password, String confirmPassword, String role) {
        String fullNameError = UserInputValidator.validateFullName(fullName);
        if (fullNameError != null) {
            return OperationResult.failure(fullNameError);
        }

        String emailError = UserInputValidator.validateEmail(email);
        if (emailError != null) {
            return OperationResult.failure(emailError);
        }

        String passwordError = UserInputValidator.validatePassword(password, confirmPassword, true);
        if (passwordError != null) {
            return OperationResult.failure(passwordError);
        }

        String roleError = UserInputValidator.validateFrontRole(role);
        if (roleError != null) {
            return OperationResult.failure(roleError);
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        String normalizedFullName = UserInputValidator.normalizeFullName(fullName);

        if (emailAlreadyExists(normalizedEmail)) {
            return OperationResult.failure("Un compte existe deja avec cette adresse email.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Impossible de creer le compte.");
        }

        try {
            ensureAuthUserSchema(connection);
        } catch (SQLException exception) {
            return OperationResult.failure("Impossible de preparer l'inscription : " + exception.getMessage());
        }

        String insertQuery = """
            INSERT INTO `user` (
                `email`, `password`, `full_name`, `roles`, `status`, `created_at`,
                `registration_status`, `profile_active`, `review_note`, `reviewed_at`
            ) VALUES (?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, PasswordSecurity.hashPassword(password));
            statement.setString(3, normalizedFullName);
            statement.setString(4, mapRegistrationRole(role));
            statement.setBoolean(5, false);
            statement.setString(6, REVIEW_PENDING_DB);
            statement.setBoolean(7, true);
            statement.setString(8, "Self-registration submitted and waiting for review.");
            statement.setTimestamp(9, null);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error registering user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la creation du compte : " + e.getMessage());
        }

        return OperationResult.success("Compte cree avec succes. Il sera disponible apres validation par l'administration.");
    }

    private boolean emailAlreadyExists(String email) {
        if (connection == null) {
            return false;
        }

        String query = "SELECT 1 FROM `user` WHERE LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking email: " + e.getMessage());
            return false;
        }
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

    private String mapRegistrationRole(String role) {
        if ("Tutor".equalsIgnoreCase(role) || "Tuteur".equalsIgnoreCase(role)) {
            return "[\"ROLE_TUTOR\"]";
        }
        return "[\"ROLE_USER\"]";
    }

    private void ensureAuthUserSchema(Connection c) throws SQLException {
        executeStatement(c, "ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `registration_status` VARCHAR(32) NULL");
        executeStatement(c, "ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `review_note` TEXT NULL");
        executeStatement(c, "ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `reviewed_at` DATETIME NULL");
        executeStatement(c, "ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `profile_active` TINYINT(1) NULL");
        executeStatement(c, "UPDATE `user` SET `registration_status` = 'APPROVED' WHERE `registration_status` IS NULL OR TRIM(`registration_status`) = ''");
        executeStatement(c, "UPDATE `user` SET `profile_active` = 1 WHERE `profile_active` IS NULL");
    }

    private void executeStatement(Connection c, String sql) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private String normalizeReviewStatus(String reviewStatus) {
        if (reviewStatus == null || reviewStatus.trim().isEmpty()) {
            return "APPROVED";
        }
        return reviewStatus.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }
}
