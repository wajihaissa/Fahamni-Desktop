package tn.esprit.fahamni.services;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tn.esprit.fahamni.Models.AdminUser;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserInputValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AdminUserService implements IServices<AdminUser> {

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

        String query = "SELECT id, full_name, email, roles, status, created_at FROM `user` ORDER BY id DESC";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String fullName = resultSet.getString("full_name");
                String email = resultSet.getString("email");
                String risk = inferFraudRisk(fullName, email);
                users.add(new AdminUser(
                    resultSet.getInt("id"),
                    fullName,
                    email,
                    inferRole(resultSet.getString("roles")),
                    mapStatus(resultSet.getBoolean("status")),
                    formatCreatedAt(resultSet.getString("created_at")),
                    risk,
                    inferFraudReason(fullName, email, risk)
                ));
            }

            return OperationResult.success("Utilisateurs charges.");
        } catch (SQLException e) {
            lastLoadError = "Erreur lors du chargement des utilisateurs : " + e.getMessage();
            System.out.println("Error loading users: " + e.getMessage());
            return OperationResult.failure(lastLoadError);
        }
    }

    public OperationResult createUser(String fullName, String email, String password, String confirmPassword, String role, String status) {
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

        String roleError = UserInputValidator.validateBackofficeRole(role);
        if (roleError != null) {
            return OperationResult.failure(roleError);
        }

        String statusError = UserInputValidator.validateStatus(status);
        if (statusError != null) {
            return OperationResult.failure(statusError);
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Creation impossible.");
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        String normalizedFullName = UserInputValidator.normalizeFullName(fullName);

        if (emailAlreadyExists(normalizedEmail)) {
            return OperationResult.failure("Un compte existe deja avec cette adresse email.");
        }

        String normalizedRole = isBlank(role) ? "Student" : role.trim();
        String normalizedStatus = normalizeStatus(status);
        String query = "INSERT INTO `user` (`email`, `password`, `full_name`, `roles`, `status`, `created_at`) VALUES (?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, password);
            statement.setString(3, normalizedFullName);
            statement.setString(4, mapRoleToDatabaseValue(normalizedRole));
            statement.setBoolean(5, toDatabaseStatus(normalizedStatus));
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error creating user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la creation : " + e.getMessage());
        }

        refreshUsers();
        return OperationResult.success("Utilisateur ajoute avec succes.");
    }

    @Override
    public void add(AdminUser entity) throws SQLException {
        if (entity == null) {
            throw new SQLException("Utilisateur invalide.");
        }

        OperationResult result = createUser(
            entity.getFullName(),
            entity.getEmail(),
            "Temp1234",
            "Temp1234",
            entity.getRole(),
            entity.getStatus()
        );

        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
    }

    public OperationResult updateUser(AdminUser user, String fullName, String email, String role, String status) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a mettre a jour.");
        }

        String fullNameError = UserInputValidator.validateFullName(fullName);
        if (fullNameError != null) {
            return OperationResult.failure(fullNameError);
        }

        String emailError = UserInputValidator.validateEmail(email);
        if (emailError != null) {
            return OperationResult.failure(emailError);
        }

        String roleError = UserInputValidator.validateBackofficeRole(role);
        if (roleError != null) {
            return OperationResult.failure(roleError);
        }

        String statusError = UserInputValidator.validateStatus(status);
        if (statusError != null) {
            return OperationResult.failure(statusError);
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        String normalizedFullName = UserInputValidator.normalizeFullName(fullName);
        if (emailAlreadyExistsForAnotherUser(normalizedEmail, user.getId())) {
            return OperationResult.failure("Un autre compte utilise deja cette adresse email.");
        }

        String normalizedStatus = normalizeStatus(status);
        String normalizedRole = isBlank(role) ? user.getRole() : role.trim();
        String query = "UPDATE `user` SET `email` = ?, `full_name` = ?, `roles` = ?, `status` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, normalizedFullName);
            statement.setString(3, mapRoleToDatabaseValue(normalizedRole));
            statement.setBoolean(4, toDatabaseStatus(normalizedStatus));
            statement.setInt(5, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour : " + e.getMessage());
        }

        user.setFullName(normalizedFullName);
        user.setEmail(normalizedEmail);
        user.setRole(normalizedRole);
        user.setStatus(normalizedStatus);
        return OperationResult.success("Utilisateur mis a jour (nom, email, role et statut).");
    }

    @Override
    public void update(AdminUser entity) throws SQLException {
        if (entity == null) {
            throw new SQLException("Utilisateur invalide.");
        }

        OperationResult result = updateUser(
            entity,
            entity.getFullName(),
            entity.getEmail(),
            entity.getRole(),
            entity.getStatus()
        );

        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
    }

    public OperationResult deleteUser(AdminUser user) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a supprimer.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Suppression impossible.");
        }

        if ("Suspended".equalsIgnoreCase(user.getStatus())) {
            return OperationResult.failure("Cet utilisateur est deja suspendu.");
        }

        String query = "UPDATE `user` SET `status` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBoolean(1, false);
            statement.setInt(2, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la suppression : " + e.getMessage());
        }

        user.setStatus("Suspended");
        return OperationResult.success("Utilisateur suspendu.");
    }

    @Override
    public void delete(AdminUser entity) throws SQLException {
        if (entity == null) {
            throw new SQLException("Utilisateur invalide.");
        }

        OperationResult result = deleteUser(entity);
        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
    }

    public OperationResult activateUser(AdminUser user) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a activer.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Activation impossible.");
        }

        if ("Active".equalsIgnoreCase(user.getStatus())) {
            return OperationResult.failure("Cet utilisateur est deja actif.");
        }

        String query = "UPDATE `user` SET `status` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBoolean(1, true);
            statement.setInt(2, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error activating user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de l'activation : " + e.getMessage());
        }

        user.setStatus("Active");
        return OperationResult.success("Utilisateur active.");
    }

    public List<AdminUser> getPendingUsersPreview() {
        List<AdminUser> pendingUsers = new ArrayList<>();
        for (AdminUser user : users) {
            if ("Administrator".equalsIgnoreCase(user.getRole())) {
                continue;
            }

            pendingUsers.add(user);
            if (pendingUsers.size() == 4) {
                break;
            }
        }
        return pendingUsers;
    }

    @Override
    public List<AdminUser> getAll() throws SQLException {
        OperationResult result = refreshUsers();
        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
        return new ArrayList<>(users);
    }

    private String inferRole(String roles) {
        if (roles != null && roles.toUpperCase().contains("ROLE_TUTOR")) {
            return "Tutor";
        }

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

    private boolean emailAlreadyExists(String email) {
        String query = "SELECT 1 FROM `user` WHERE LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking duplicate admin email: " + e.getMessage());
            return false;
        }
    }

    private boolean emailAlreadyExistsForAnotherUser(String email, int userId) {
        String query = "SELECT 1 FROM `user` WHERE LOWER(email) = LOWER(?) AND id <> ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email.trim());
            statement.setInt(2, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking duplicate admin email on update: " + e.getMessage());
            return false;
        }
    }

    private String mapRoleToDatabaseValue(String role) {
        if ("Administrator".equalsIgnoreCase(role)) {
            return "[\"ROLE_ADMIN\"]";
        }

        if ("Tutor".equalsIgnoreCase(role)) {
            return "[\"ROLE_TUTOR\"]";
        }

        return "[\"ROLE_USER\"]";
    }

    private String formatCreatedAt(String createdAtValue) {
        if (isBlank(createdAtValue)) {
            return "Unknown";
        }

        try {
            LocalDateTime createdAt = LocalDateTime.parse(createdAtValue.replace(" ", "T"));
            return createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
        } catch (DateTimeParseException exception) {
            return createdAtValue;
        }
    }

    private String inferFraudRisk(String fullName, String email) {
        int riskScore = 10;
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        if (!normalizedName.contains(" ")) {
            riskScore += 15;
        }

        if (normalizedName.length() < 8) {
            riskScore += 20;
        }

        if (normalizedEmail.matches(".*\\d{4,}.*")) {
            riskScore += 15;
        }

        if (normalizedEmail.endsWith("@gmail.com") || normalizedEmail.endsWith("@yahoo.com")) {
            riskScore += 5;
        }

        if (riskScore >= 45) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private String inferFraudReason(String fullName, String email, String risk) {
        List<String> reasons = new ArrayList<>();
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim();

        if (!normalizedName.contains(" ")) {
            reasons.add("Full name is a single token.");
        }

        if (normalizedName.length() < 8) {
            reasons.add("Full name is very short.");
        }

        if (normalizedEmail.matches(".*\\d{4,}.*")) {
            reasons.add("Email contains a long numeric suffix.");
        }

        if (reasons.isEmpty()) {
            reasons.add("No blocking signals detected.");
        }

        return risk + " review: " + String.join(" ", reasons);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
