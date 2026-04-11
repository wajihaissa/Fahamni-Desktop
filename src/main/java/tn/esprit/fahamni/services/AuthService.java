package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AuthService {

    private final List<User> mockUsers = List.of(
        new User(0, "Administrateur Fahamni", "admin@fahamni.tn", "admin123", UserRole.ADMIN),
        new User(0, "Utilisateur Fahamni", "user@fahamni.tn", "user123", UserRole.USER)
    );

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

        String normalizedEmail = email.trim();

        if (connection == null) {
            for (User user : mockUsers) {
                if (user.getEmail().equalsIgnoreCase(normalizedEmail) && user.getPassword().equals(password)) {
                    return user;
                }
            }
            return null;
        }

        String query = "SELECT id, full_name, email, password, roles, status FROM `user` WHERE LOWER(email) = LOWER(?) AND password = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, password);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    if (!resultSet.getBoolean("status")) {
                        lastAuthenticationError = "Votre compte est suspendu. Contactez l'administration.";
                        return null;
                    }

                    return new User(
                        resultSet.getInt("id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        resultSet.getString("password"),
                        mapRole(resultSet.getString("roles"))
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println("Error authenticating user: " + e.getMessage());
        }

        for (User user : mockUsers) {
            if (user.getEmail().equalsIgnoreCase(normalizedEmail) && user.getPassword().equals(password)) {
                return user;
            }
        }

        return null;
    }

    public String getLastAuthenticationError() {
        return lastAuthenticationError;
    }

    public OperationResult register(String fullName, String email, String password, String confirmPassword) {
        if (isBlank(fullName) || isBlank(email) || isBlank(password) || isBlank(confirmPassword)) {
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
            statement.setString(4, "[\"ROLE_USER\"]");
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

        if (connection == null) {
            return false;
        }

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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private UserRole mapRole(String databaseRoles) {
        if (databaseRoles != null && databaseRoles.toUpperCase().contains("ROLE_ADMIN")) {
            return UserRole.ADMIN;
        }

        return UserRole.USER;
    }
}

