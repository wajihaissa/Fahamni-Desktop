package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserAccountService {

    public record AccountOverview(
        String accountStatus,
        String roleLabel,
        String validationStatus,
        String createdAt
    ) {
    }

    private final Connection connection;

    public UserAccountService() {
        connection = MyDataBase.getInstance().getCnx();
    }

    public OperationResult updateCurrentUser(String fullName, String email, String password, String confirmPassword) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }

        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }

        if (isBlank(fullName) || isBlank(email)) {
            return OperationResult.failure("Le nom complet et l'email sont obligatoires.");
        }

        if (!email.contains("@")) {
            return OperationResult.failure("Veuillez saisir une adresse email valide.");
        }

        if (!isBlank(password) || !isBlank(confirmPassword)) {
            if (password == null || password.length() < 4) {
                return OperationResult.failure("Le mot de passe doit contenir au moins 4 caracteres.");
            }

            if (!password.equals(confirmPassword)) {
                return OperationResult.failure("Les mots de passe ne correspondent pas.");
            }
        }

        if (emailAlreadyUsedByAnotherUser(email, currentUser.getId())) {
            return OperationResult.failure("Un autre compte utilise deja cette adresse email.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        String updatedPassword = isBlank(password) ? currentUser.getPassword() : password;
        String query = "UPDATE `user` SET `full_name` = ?, `email` = ?, `password` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, fullName.trim());
            statement.setString(2, email.trim());
            statement.setString(3, updatedPassword);
            statement.setInt(4, currentUser.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating current user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour du compte : " + e.getMessage());
        }

        UserSession.setCurrentUser(new User(
            currentUser.getId(),
            fullName.trim(),
            email.trim(),
            updatedPassword,
            currentUser.getRole()
        ));

        return OperationResult.success("Profil mis a jour avec succes.");
    }

    public OperationResult deleteCurrentUser() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }

        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Suppression impossible.");
        }

        String query = "DELETE FROM `user` WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, currentUser.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting current user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la suppression du compte : " + e.getMessage());
        }

        UserSession.clear();
        return OperationResult.success("Compte supprime avec succes.");
    }

    public AccountOverview getCurrentAccountOverview() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return new AccountOverview("Inconnu", "Aucun role", "Non disponible", "Non disponible");
        }

        String accountStatus = "Active";
        String validationStatus = "Approved";
        String createdAt = "Non disponible";

        if (connection != null && currentUser.getId() > 0) {
            String query = "SELECT `status`, `created_at` FROM `user` WHERE `id` = ? LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUser.getId());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        boolean active = resultSet.getBoolean("status");
                        accountStatus = active ? "Active" : "Suspended";
                        validationStatus = active ? "Approved" : "Restricted";
                        String createdValue = resultSet.getString("created_at");
                        if (!isBlank(createdValue)) {
                            createdAt = createdValue;
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println("Error loading current account overview: " + e.getMessage());
            }
        }

        return new AccountOverview(accountStatus, UserSession.getRoleLabel(), validationStatus, createdAt);
    }

    private boolean emailAlreadyUsedByAnotherUser(String email, int currentUserId) {
        if (connection == null) {
            return false;
        }

        String query = "SELECT 1 FROM `user` WHERE LOWER(email) = LOWER(?) AND id <> ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email.trim());
            statement.setInt(2, currentUserId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking duplicate account email: " + e.getMessage());
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
