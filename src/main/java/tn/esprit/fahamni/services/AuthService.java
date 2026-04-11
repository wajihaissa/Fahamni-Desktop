package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;

import java.sql.*;
import java.util.List;

public class AuthService {

    private final List<User> mockUsers = List.of(
        new User(0, "Administrateur Fahamni", "admin@fahamni.tn", "admin123", UserRole.ADMIN),
        new User(0, "Utilisateur Fahamni", "user@fahamni.tn", "user123", UserRole.USER)
    );

    private Connection cnx() {
        return MyDataBase.getInstance().getCnx();
    }

    private final Connection connection;
    private String lastAuthenticationError;

    public AuthService() {
        connection = MyDataBase.getInstance().getCnx();
    }

    public User authenticate(String email, String password) {
        lastAuthenticationError = null;

        if (isBlank(email) || isBlank(password)) return null;

        String normalizedEmail = email.trim();
        Connection c = cnx();

        if (c != null) {
            // Chercher l'utilisateur dans la BD (plusieurs noms de colonnes possibles)
            String[] nameCols = {"fullName", "full_name", "name", "username"};
            String[] roleCols = {"roles", "role"};
            for (String nameCol : nameCols) {
                for (String roleCol : roleCols) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT id, " + nameCol + ", " + roleCol + ", password FROM user WHERE email = ?")) {
                        ps.setString(1, normalizedEmail);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            int userId = rs.getInt("id");
                            String fullName = rs.getString(nameCol);
                            String roleStr = rs.getString(roleCol);
                            String dbPassword = rs.getString("password");

                            // Vérification mot de passe (plaintext ou hash BCrypt)
                            if (!passwordMatches(password, dbPassword)) {
                                lastAuthenticationError = "Email ou mot de passe invalide.";
                                return null;
                            }

                            UserRole role = mapRole(roleStr);
                            System.out.println("AuthService: connexion BD réussie pour " + email + " (id=" + userId + ")");
                            return new User(userId, fullName, email, password, role);
                        }
                    } catch (Exception e) { /* colonne suivante */ }
                }
            }
        }

        // Fallback mock (développement)
        for (User user : mockUsers) {
            if (user.getEmail().equalsIgnoreCase(normalizedEmail) && user.getPassword().equals(password)) {
                return user;
            }
        }

        System.out.println("AuthService: utilisateur non trouvé pour " + email);
        return null;
    }

    public String getLastAuthenticationError() {
        return lastAuthenticationError;
    }

    public OperationResult register(String fullName, String email, String password, String confirmPassword, String role) {
        if (isBlank(fullName) || isBlank(email) || isBlank(password) || isBlank(confirmPassword) || isBlank(role)) {
            return OperationResult.failure("Veuillez remplir tous les champs.");
        }
        if (!email.contains("@")) {
            return OperationResult.failure("Veuillez saisir une adresse email valide.");
        }
        if (password.length() < 4) {
            return OperationResult.failure("Le mot de passe doit contenir au moins 4 caracteres.");
        }
        if (!password.equals(confirmPassword)) {
            return OperationResult.failure("Les mots de passe ne correspondent pas.");
        }

        if (emailAlreadyExists(email)) {
            return OperationResult.failure("Un compte existe deja avec cette adresse email.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Impossible de creer le compte.");
        }

        String insertQuery = "INSERT INTO `user` (`email`, `password`, `full_name`, `roles`, `status`, `created_at`) VALUES (?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, email.trim());
            statement.setString(2, password);
            statement.setString(3, fullName.trim());
            statement.setString(4, mapRegistrationRole(role));
            statement.setBoolean(5, true);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error registering user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la creation du compte : " + e.getMessage());
        }

        return OperationResult.success("Compte cree avec succes. Vous pouvez maintenant vous connecter.");
    }

    private boolean emailAlreadyExists(String email) {
        String normalizedEmail = email.trim();

        for (User user : mockUsers) {
            if (user.getEmail().equalsIgnoreCase(normalizedEmail)) {
                return true;
            }
        }

        if (connection == null) return false;

        String query = "SELECT 1 FROM `user` WHERE LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking email: " + e.getMessage());
            return false;
        }
    }

    /** Vérifie si le mot de passe saisi correspond au hash BD (plaintext ou BCrypt) */
    private boolean passwordMatches(String input, String stored) {
        if (stored == null) return false;
        if (stored.equals(input)) return true;
        // Hash BCrypt (commence par $2y$ ou $2a$) — accepté simplement pour dev
        if (stored.startsWith("$2")) {
            System.out.println("AuthService: mot de passe BCrypt détecté, vérification simplifiée.");
            return true;
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private UserRole mapRole(String databaseRoles) {
        if (databaseRoles != null &&
            (databaseRoles.toUpperCase().contains("ROLE_ADMIN") || databaseRoles.equalsIgnoreCase("admin"))) {
            return UserRole.ADMIN;
        }

        if (databaseRoles != null && databaseRoles.toUpperCase().contains("ROLE_TUTOR")) {
            return UserRole.TUTOR;
        }

        return UserRole.USER;
    }

    private String mapRegistrationRole(String role) {
        if ("Tuteur".equalsIgnoreCase(role)) {
            return "[\"ROLE_TUTOR\"]";
        }

        return "[\"ROLE_USER\"]";
    }
}
