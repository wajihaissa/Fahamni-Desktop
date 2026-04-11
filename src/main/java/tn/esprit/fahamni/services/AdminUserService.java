package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminUser;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.MyDataBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AdminUserService {

    private final ObservableList<AdminUser> users = FXCollections.observableArrayList();
    private final Connection connection;
    private String lastLoadError;

    public AdminUserService() {
        connection = MyDataBase.getInstance().getCnx();
        refreshUsers();
    }

    public ObservableList<AdminUser> getUsers() {
        return users;
    }

    public String getLastLoadError() {
        return lastLoadError;
    }

    public List<String> getAvailableRoles() {
        return List.of("Student", "Tutor", "Administrator");
    }

    public List<String> getAvailableStatuses() {
        return List.of("Active", "Suspended");
    }

    public OperationResult refreshUsers() {
        users.clear();
        lastLoadError = null;

        if (connection == null) {
            lastLoadError = "Connexion a la base indisponible. Impossible de charger les utilisateurs.";
            return OperationResult.failure(lastLoadError);
        }

        String query = "SELECT id, full_name, email, roles, status FROM `user` ORDER BY id DESC";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(new AdminUser(
                    resultSet.getInt("id"),
                    resultSet.getString("full_name"),
                    resultSet.getString("email"),
                    inferRole(resultSet.getString("roles")),
                    mapStatus(resultSet.getBoolean("status"))
                ));
            }

            return OperationResult.success("Utilisateurs charges.");
        } catch (SQLException e) {
            lastLoadError = "Erreur lors du chargement des utilisateurs : " + e.getMessage();
            System.out.println("Error loading users: " + e.getMessage());
            return OperationResult.failure(lastLoadError);
        }
    }

    public OperationResult createUser(String fullName, String email, String role, String status) {
        if (isBlank(fullName) || isBlank(email)) {
            return OperationResult.failure("Veuillez renseigner le nom et l'email.");
        }

        return OperationResult.failure("Ajout backoffice non disponible pour le moment : utilisez l'inscription pour creer un compte avec mot de passe.");
    }

    public OperationResult updateUser(AdminUser user, String fullName, String email, String role, String status) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a mettre a jour.");
        }

        if (isBlank(fullName) || isBlank(email)) {
            return OperationResult.failure("Veuillez renseigner le nom et l'email.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        String normalizedStatus = normalizeStatus(status);
        String query = "UPDATE `user` SET `email` = ?, `full_name` = ?, `status` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email.trim());
            statement.setString(2, fullName.trim());
            statement.setBoolean(3, toDatabaseStatus(normalizedStatus));
            statement.setInt(4, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour : " + e.getMessage());
        }

        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setStatus(normalizedStatus);
        return OperationResult.success("Utilisateur mis a jour (nom, email et statut).");
    }

    public OperationResult deleteUser(AdminUser user) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a supprimer.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Suppression impossible.");
        }

        String query = "DELETE FROM `user` WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la suppression : " + e.getMessage());
        }

        users.remove(user);
        return OperationResult.success("Utilisateur supprime.");
    }

    private String inferRole(String roles) {
        if (roles != null && roles.toUpperCase().contains("ROLE_ADMIN")) {
            return "Administrator";
        }

        return "Student";
    }

    private String mapStatus(boolean active) {
        return active ? "Active" : "Suspended";
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            return "Active";
        }

        return status.trim();
    }

    private boolean toDatabaseStatus(String status) {
        return !"Suspended".equalsIgnoreCase(status);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

