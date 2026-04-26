package tn.esprit.fahamni.services;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tn.esprit.fahamni.Models.AdminUser;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.PasswordSecurity;
import tn.esprit.fahamni.utils.UserInputValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AdminUserService implements IServices<AdminUser> {

    public record AdminUserInsights(
        int pendingCount,
        int approvedCount,
        int declinedCount,
        int linkedStudentProfiles,
        int profileOffCount,
        int mediumRiskCount,
        int highRiskCount,
        String summary
    ) {
    }

    private static final String REVIEW_PENDING_DB = "PENDING_REVIEW";
    private static final String REVIEW_APPROVED_DB = "APPROVED";
    private static final String REVIEW_DECLINED_DB = "DECLINED";

    private static final String REVIEW_PENDING = "Pending Review";
    private static final String REVIEW_APPROVED = "Approved";
    private static final String REVIEW_DECLINED = "Declined";

    private static final String PROFILE_ON = "Profile On";
    private static final String PROFILE_OFF = "Profile Off";

    private static final String LINKED_PROFILE = "Linked";
    private static final String LINK_PENDING = "Will link on approval";
    private static final String LINK_NOT_NEEDED = "Not needed";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

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

    public List<String> getAvailableReviewStatuses() {
        return List.of(REVIEW_PENDING, REVIEW_APPROVED, REVIEW_DECLINED);
    }

    public List<String> getAvailableProfileStatuses() {
        return List.of(PROFILE_ON, PROFILE_OFF);
    }

    public OperationResult refreshUsers() {
        users.clear();
        lastLoadError = null;

        if (connection == null) {
            lastLoadError = "Connexion a la base indisponible. Impossible de charger les utilisateurs.";
            return OperationResult.failure(lastLoadError);
        }

        try {
            ensureAdminUserSchema();
            syncLinkedStudentProfiles();

            String query = """
                SELECT
                    u.id,
                    u.full_name,
                    u.email,
                    u.roles,
                    u.status,
                    u.created_at,
                    COALESCE(u.registration_status, 'APPROVED') AS registration_status,
                    u.reviewed_at,
                    u.review_note,
                    COALESCE(u.profile_active, 1) AS profile_active,
                    EXISTS(SELECT 1 FROM student_profile sp WHERE sp.user_id = u.id) AS linked_student_profile
                FROM `user` u
                ORDER BY
                    CASE COALESCE(u.registration_status, 'APPROVED')
                        WHEN 'PENDING_REVIEW' THEN 0
                        WHEN 'DECLINED' THEN 2
                        ELSE 1
                    END,
                    u.id DESC
                """;

            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String fullName = resultSet.getString("full_name");
                    String email = resultSet.getString("email");
                    String role = inferRole(resultSet.getString("roles"));
                    String reviewStatusDb = normalizeReviewStatus(resultSet.getString("registration_status"));
                    boolean accountActive = resultSet.getBoolean("status");
                    boolean profileActive = resultSet.getBoolean("profile_active");
                    boolean linkedStudentProfile = resultSet.getBoolean("linked_student_profile");
                    String fraudRisk = inferFraudRisk(fullName, email);
                    String fraudReason = inferFraudReason(fullName, email, fraudRisk);
                    String reviewStatus = mapReviewStatus(reviewStatusDb);
                    String accountStatus = mapAccountStatus(accountActive);
                    String profileStatus = profileActive ? PROFILE_ON : PROFILE_OFF;
                    String linkedProfileStatus = mapLinkedProfileStatus(role, reviewStatusDb, linkedStudentProfile);
                    String createdAt = formatDate(resultSet.getTimestamp("created_at"));
                    String reviewedAt = formatDate(resultSet.getTimestamp("reviewed_at"));
                    String reviewNote = safeValue(resultSet.getString("review_note"));
                    String insight = buildAdminInsight(
                        role,
                        reviewStatus,
                        accountStatus,
                        profileStatus,
                        linkedProfileStatus,
                        fraudRisk
                    );

                    users.add(new AdminUser(
                        resultSet.getInt("id"),
                        fullName,
                        email,
                        role,
                        accountStatus,
                        reviewStatus,
                        profileStatus,
                        linkedProfileStatus,
                        createdAt,
                        reviewedAt,
                        fraudRisk,
                        fraudReason,
                        reviewNote,
                        insight
                    ));
                }
            }

            return OperationResult.success("Utilisateurs charges.");
        } catch (SQLException e) {
            lastLoadError = "Erreur lors du chargement des utilisateurs : " + e.getMessage();
            System.out.println("Error loading users: " + e.getMessage());
            return OperationResult.failure(lastLoadError);
        }
    }

    public OperationResult createUser(String fullName, String email, String password, String confirmPassword, String role,
                                      String status, String reviewStatus, String profileStatus) {
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

        String reviewStatusError = UserInputValidator.validateReviewStatus(reviewStatus);
        if (reviewStatusError != null) {
            return OperationResult.failure(reviewStatusError);
        }

        String profileStatusError = UserInputValidator.validateProfileStatus(profileStatus);
        if (profileStatusError != null) {
            return OperationResult.failure(profileStatusError);
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Creation impossible.");
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        String normalizedFullName = UserInputValidator.normalizeFullName(fullName);

        if (emailAlreadyExists(normalizedEmail)) {
            return OperationResult.failure("Un compte existe deja avec cette adresse email.");
        }

        try {
            ensureAdminUserSchema();
            String normalizedRole = role.trim();
            String normalizedReviewStatus = normalizeReviewStatus(reviewStatus);
            String normalizedAccountStatus = normalizeAccountStatus(status, normalizedReviewStatus);
            String normalizedProfileStatus = normalizeProfileStatus(profileStatus);
            String approvalGuard = validateApprovalGate(
                inferFraudRisk(normalizedFullName, normalizedEmail),
                mapReviewStatus(normalizedReviewStatus)
            );
            if (approvalGuard != null && REVIEW_APPROVED_DB.equals(normalizedReviewStatus)) {
                return OperationResult.failure(approvalGuard);
            }

            int insertedUserId;
            String query = """
                INSERT INTO `user` (
                    `email`, `password`, `full_name`, `roles`, `status`, `created_at`,
                    `registration_status`, `profile_active`, `review_note`, `reviewed_at`
                ) VALUES (?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?)
                """;
            try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, normalizedEmail);
                statement.setString(2, PasswordSecurity.hashPassword(password));
                statement.setString(3, normalizedFullName);
                statement.setString(4, mapRoleToDatabaseValue(normalizedRole));
                statement.setBoolean(5, toDatabaseAccountStatus(normalizedAccountStatus));
                statement.setString(6, normalizedReviewStatus);
                statement.setBoolean(7, toDatabaseProfileStatus(normalizedProfileStatus));
                statement.setString(8, buildSystemReviewNote(reviewStatus, "Created from the backoffice user editor."));
                if (REVIEW_PENDING_DB.equals(normalizedReviewStatus)) {
                    statement.setTimestamp(9, null);
                } else {
                    statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
                }
                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        return OperationResult.failure("Le compte a ete cree, mais son identifiant est introuvable.");
                    }
                    insertedUserId = generatedKeys.getInt(1);
                }
            }

            if ("Student".equalsIgnoreCase(normalizedRole) && REVIEW_APPROVED_DB.equals(normalizedReviewStatus)) {
                ensureLinkedStudentProfile(insertedUserId);
            }

            refreshUsers();
            return OperationResult.success("Utilisateur ajoute avec succes.");
        } catch (SQLException e) {
            System.out.println("Error creating user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la creation : " + e.getMessage());
        }
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
            entity.getStatus(),
            entity.getReviewStatus(),
            entity.getProfileStatus()
        );

        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
    }

    public OperationResult updateUser(AdminUser user, String fullName, String email, String role, String status,
                                      String reviewStatus, String profileStatus) {
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

        String reviewStatusError = UserInputValidator.validateReviewStatus(reviewStatus);
        if (reviewStatusError != null) {
            return OperationResult.failure(reviewStatusError);
        }

        String profileStatusError = UserInputValidator.validateProfileStatus(profileStatus);
        if (profileStatusError != null) {
            return OperationResult.failure(profileStatusError);
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        String normalizedFullName = UserInputValidator.normalizeFullName(fullName);
        if (emailAlreadyExistsForAnotherUser(normalizedEmail, user.getId())) {
            return OperationResult.failure("Un autre compte utilise deja cette adresse email.");
        }

        try {
            ensureAdminUserSchema();
            String normalizedRole = role.trim();
            String normalizedReviewStatus = normalizeReviewStatus(reviewStatus);
            String normalizedAccountStatus = normalizeAccountStatus(status, normalizedReviewStatus);
            String normalizedProfileStatus = normalizeProfileStatus(profileStatus);

            String approvalGuard = validateApprovalGate(
                inferFraudRisk(normalizedFullName, normalizedEmail),
                mapReviewStatus(normalizedReviewStatus)
            );
            if (approvalGuard != null && REVIEW_APPROVED_DB.equals(normalizedReviewStatus)) {
                return OperationResult.failure(approvalGuard);
            }

            String query = """
                UPDATE `user`
                SET `email` = ?, `full_name` = ?, `roles` = ?, `status` = ?,
                    `registration_status` = ?, `profile_active` = ?, `review_note` = ?, `reviewed_at` = ?
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, normalizedEmail);
                statement.setString(2, normalizedFullName);
                statement.setString(3, mapRoleToDatabaseValue(normalizedRole));
                statement.setBoolean(4, toDatabaseAccountStatus(normalizedAccountStatus));
                statement.setString(5, normalizedReviewStatus);
                statement.setBoolean(6, toDatabaseProfileStatus(normalizedProfileStatus));
                statement.setString(7, buildSystemReviewNote(reviewStatus, "Updated from the backoffice editor."));
                if (REVIEW_PENDING_DB.equals(normalizedReviewStatus)) {
                    statement.setTimestamp(8, null);
                } else {
                    statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                }
                statement.setInt(9, user.getId());
                statement.executeUpdate();
            }

            if ("Student".equalsIgnoreCase(normalizedRole) && REVIEW_APPROVED_DB.equals(normalizedReviewStatus)) {
                ensureLinkedStudentProfile(user.getId());
            }

            refreshUsers();
            return OperationResult.success("Utilisateur mis a jour avec succes.");
        } catch (SQLException e) {
            System.out.println("Error updating user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour : " + e.getMessage());
        }
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
            entity.getStatus(),
            entity.getReviewStatus(),
            entity.getProfileStatus()
        );

        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
    }

    public OperationResult deleteUser(AdminUser user) {
        return updateAccountStatus(user, "Suspended", false);
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

        if (!REVIEW_APPROVED.equalsIgnoreCase(user.getReviewStatus())) {
            return OperationResult.failure("Seuls les comptes approuves peuvent etre actives.");
        }

        String approvalGuard = validateApprovalGate(user.getFraudRisk(), user.getReviewStatus());
        if (approvalGuard != null) {
            return OperationResult.failure(approvalGuard);
        }

        return updateAccountStatus(user, "Active", false);
    }

    public OperationResult approveUser(AdminUser user) {
        return updateReviewStatus(user, REVIEW_APPROVED, true, "Approved from the pending review queue.", false);
    }

    public OperationResult declineUser(AdminUser user) {
        return updateReviewStatus(user, REVIEW_DECLINED, false, "Declined from the backoffice review queue.", false);
    }

    public OperationResult moveUserBackToPending(AdminUser user) {
        return updateReviewStatus(user, REVIEW_PENDING, false, "Moved back to pending review.", true);
    }

    public OperationResult toggleProfileActivity(AdminUser user) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        try {
            ensureAdminUserSchema();
            boolean enableProfile = !PROFILE_ON.equalsIgnoreCase(user.getProfileStatus());
            String query = "UPDATE `user` SET `profile_active` = ? WHERE `id` = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, enableProfile);
                statement.setInt(2, user.getId());
                statement.executeUpdate();
            }

            refreshUsers();
            return OperationResult.success(enableProfile
                ? "Profil active pour cet utilisateur."
                : "Profil desactive pour cet utilisateur.");
        } catch (SQLException e) {
            System.out.println("Error toggling profile activity: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour du profil : " + e.getMessage());
        }
    }

    public OperationResult applyBulkAction(List<AdminUser> selectedUsers, String actionId) {
        if (selectedUsers == null || selectedUsers.isEmpty()) {
            return OperationResult.failure("Selectionnez au moins un utilisateur.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Action impossible.");
        }

        int successCount = 0;
        int skippedCount = 0;
        List<String> skippedUsers = new ArrayList<>();

        for (AdminUser user : selectedUsers) {
            OperationResult result = switch (actionId) {
                case "APPROVE" -> updateReviewStatus(user, REVIEW_APPROVED, true, "Approved from a bulk action.", true);
                case "DECLINE" -> updateReviewStatus(user, REVIEW_DECLINED, false, "Declined from a bulk action.", true);
                case "MOVE_TO_PENDING" -> updateReviewStatus(user, REVIEW_PENDING, false, "Returned to pending review from a bulk action.", true);
                case "ACTIVATE" -> activateUserWithoutRefresh(user);
                case "SUSPEND" -> updateAccountStatus(user, "Suspended", true);
                case "TOGGLE_PROFILE" -> toggleProfileActivityWithoutRefresh(user);
                default -> OperationResult.failure("Action groupée inconnue.");
            };

            if (result.isSuccess()) {
                successCount++;
            } else {
                skippedCount++;
                skippedUsers.add(user.getEmail());
            }
        }

        if (successCount > 0) {
            refreshUsers();
        }

        if (successCount == 0) {
            return OperationResult.failure("Aucune action appliquee. Verifiez la selection.");
        }

        StringBuilder message = new StringBuilder(successCount + " utilisateur(s) mis a jour.");
        if (skippedCount > 0) {
            message.append(" ").append(skippedCount).append(" ignore(s)");
            if (!skippedUsers.isEmpty()) {
                message.append(" : ").append(String.join(", ", skippedUsers));
            }
            message.append(".");
        }
        return OperationResult.success(message.toString());
    }

    public List<AdminUser> getPendingUsersPreview() {
        List<AdminUser> pendingUsers = new ArrayList<>();
        for (AdminUser user : users) {
            if (REVIEW_PENDING.equalsIgnoreCase(user.getReviewStatus())) {
                pendingUsers.add(user);
            }
        }
        return pendingUsers;
    }

    public AdminUserInsights getAdminInsights() {
        int pendingCount = 0;
        int approvedCount = 0;
        int declinedCount = 0;
        int linkedStudentProfiles = 0;
        int profileOffCount = 0;
        int mediumRiskCount = 0;
        int highRiskCount = 0;

        for (AdminUser user : users) {
            if (REVIEW_PENDING.equalsIgnoreCase(user.getReviewStatus())) {
                pendingCount++;
            } else if (REVIEW_APPROVED.equalsIgnoreCase(user.getReviewStatus())) {
                approvedCount++;
            } else if (REVIEW_DECLINED.equalsIgnoreCase(user.getReviewStatus())) {
                declinedCount++;
            }

            if (LINKED_PROFILE.equalsIgnoreCase(user.getLinkedProfileStatus())) {
                linkedStudentProfiles++;
            }

            if (PROFILE_OFF.equalsIgnoreCase(user.getProfileStatus())) {
                profileOffCount++;
            }

            if ("HIGH".equalsIgnoreCase(user.getFraudRisk())) {
                highRiskCount++;
            } else if ("MEDIUM".equalsIgnoreCase(user.getFraudRisk())) {
                mediumRiskCount++;
            }
        }

        String summary = buildInsightsSummary(
            pendingCount,
            approvedCount,
            declinedCount,
            linkedStudentProfiles,
            profileOffCount,
            mediumRiskCount,
            highRiskCount
        );

        return new AdminUserInsights(
            pendingCount,
            approvedCount,
            declinedCount,
            linkedStudentProfiles,
            profileOffCount,
            mediumRiskCount,
            highRiskCount,
            summary
        );
    }

    @Override
    public List<AdminUser> getAll() throws SQLException {
        OperationResult result = refreshUsers();
        if (!result.isSuccess()) {
            throw new SQLException(result.getMessage());
        }
        return new ArrayList<>(users);
    }

    private OperationResult updateAccountStatus(AdminUser user, String targetStatus, boolean skipRefresh) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        if ("Active".equalsIgnoreCase(targetStatus) && !REVIEW_APPROVED.equalsIgnoreCase(user.getReviewStatus())) {
            return OperationResult.failure("Le compte doit etre approuve avant activation.");
        }

        if (targetStatus.equalsIgnoreCase(user.getStatus())) {
            return OperationResult.failure("Le compte est deja dans cet etat.");
        }

        try {
            ensureAdminUserSchema();
            String query = "UPDATE `user` SET `status` = ? WHERE `id` = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, toDatabaseAccountStatus(targetStatus));
                statement.setInt(2, user.getId());
                statement.executeUpdate();
            }

            if (!skipRefresh) {
                refreshUsers();
            }

            return OperationResult.success("Active".equalsIgnoreCase(targetStatus)
                ? "Utilisateur active."
                : "Utilisateur suspendu.");
        } catch (SQLException e) {
            System.out.println("Error updating account status: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour du compte : " + e.getMessage());
        }
    }

    private OperationResult activateUserWithoutRefresh(AdminUser user) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a activer.");
        }

        if (!REVIEW_APPROVED.equalsIgnoreCase(user.getReviewStatus())) {
            return OperationResult.failure("Seuls les comptes approuves peuvent etre actives.");
        }

        String approvalGuard = validateApprovalGate(user.getFraudRisk(), user.getReviewStatus());
        if (approvalGuard != null) {
            return OperationResult.failure(approvalGuard);
        }

        return updateAccountStatus(user, "Active", true);
    }

    private OperationResult toggleProfileActivityWithoutRefresh(AdminUser user) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        try {
            ensureAdminUserSchema();
            boolean enableProfile = !PROFILE_ON.equalsIgnoreCase(user.getProfileStatus());
            String query = "UPDATE `user` SET `profile_active` = ? WHERE `id` = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, enableProfile);
                statement.setInt(2, user.getId());
                statement.executeUpdate();
            }
            return OperationResult.success(enableProfile ? "Profil active." : "Profil desactive.");
        } catch (SQLException e) {
            System.out.println("Error toggling profile activity: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour du profil : " + e.getMessage());
        }
    }

    private OperationResult updateReviewStatus(AdminUser user, String targetReviewStatus, boolean activateAccount,
                                               String note, boolean skipRefresh) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        String normalizedReviewStatus = normalizeReviewStatus(targetReviewStatus);
        String displayReviewStatus = mapReviewStatus(normalizedReviewStatus);
        if (displayReviewStatus.equalsIgnoreCase(user.getReviewStatus())) {
            return OperationResult.failure("Le compte est deja dans cet etat de revision.");
        }

        if (REVIEW_APPROVED.equalsIgnoreCase(displayReviewStatus)) {
            String approvalGuard = validateApprovalGate(user.getFraudRisk(), displayReviewStatus);
            if (approvalGuard != null) {
                return OperationResult.failure(approvalGuard);
            }
        }

        try {
            ensureAdminUserSchema();
            String query = """
                UPDATE `user`
                SET `registration_status` = ?, `status` = ?, `review_note` = ?, `reviewed_at` = ?, `profile_active` = COALESCE(`profile_active`, 1)
                WHERE `id` = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, normalizedReviewStatus);
                statement.setBoolean(2, activateAccount && REVIEW_APPROVED_DB.equals(normalizedReviewStatus));
                statement.setString(3, note);
                if (REVIEW_PENDING_DB.equals(normalizedReviewStatus)) {
                    statement.setTimestamp(4, null);
                } else {
                    statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                }
                statement.setInt(5, user.getId());
                statement.executeUpdate();
            }

            if ("Student".equalsIgnoreCase(user.getRole()) && REVIEW_APPROVED_DB.equals(normalizedReviewStatus)) {
                ensureLinkedStudentProfile(user.getId());
            }

            if (!skipRefresh) {
                refreshUsers();
            }

            return OperationResult.success("Statut de revision mis a jour vers " + displayReviewStatus + ".");
        } catch (SQLException e) {
            System.out.println("Error updating review status: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour de la revision : " + e.getMessage());
        }
    }

    private void ensureAdminUserSchema() throws SQLException {
        executeSchemaStatement("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `registration_status` VARCHAR(32) NULL");
        executeSchemaStatement("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `review_note` TEXT NULL");
        executeSchemaStatement("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `reviewed_at` DATETIME NULL");
        executeSchemaStatement("ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `profile_active` TINYINT(1) NULL");
        executeSchemaStatement("UPDATE `user` SET `registration_status` = 'APPROVED' WHERE `registration_status` IS NULL OR TRIM(`registration_status`) = ''");
        executeSchemaStatement("UPDATE `user` SET `profile_active` = 1 WHERE `profile_active` IS NULL");
        executeSchemaStatement("""
            CREATE TABLE IF NOT EXISTS `student_profile` (
                `id` INT NOT NULL AUTO_INCREMENT,
                `user_id` INT NOT NULL,
                `learning_goal` VARCHAR(255) NULL,
                `study_level` VARCHAR(120) NULL,
                `notes` TEXT NULL,
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (`id`),
                UNIQUE KEY `uk_student_profile_user` (`user_id`)
            )
            """);
    }

    private void syncLinkedStudentProfiles() throws SQLException {
        if (connection == null) {
            return;
        }

        String query = """
            SELECT id
            FROM `user`
            WHERE COALESCE(`registration_status`, 'APPROVED') = 'APPROVED'
              AND LOWER(COALESCE(`roles`, '')) LIKE '%role_user%'
            """;
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ensureLinkedStudentProfile(resultSet.getInt("id"));
            }
        }
    }

    private void ensureLinkedStudentProfile(int userId) throws SQLException {
        if (userId <= 0 || connection == null) {
            return;
        }

        String query = """
            INSERT INTO `student_profile` (`user_id`, `learning_goal`, `study_level`, `notes`, `created_at`, `updated_at`)
            SELECT ?, NULL, NULL, 'Auto-created from the user approval workflow.', NOW(), NOW()
            WHERE NOT EXISTS (SELECT 1 FROM `student_profile` WHERE `user_id` = ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    private void executeSchemaStatement(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
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

    private String mapAccountStatus(boolean active) {
        return active ? "Active" : "Suspended";
    }

    private String mapReviewStatus(String reviewStatusDb) {
        return switch (normalizeReviewStatus(reviewStatusDb)) {
            case REVIEW_PENDING_DB -> REVIEW_PENDING;
            case REVIEW_DECLINED_DB -> REVIEW_DECLINED;
            default -> REVIEW_APPROVED;
        };
    }

    private String mapLinkedProfileStatus(String role, String reviewStatusDb, boolean linkedStudentProfile) {
        if (!"Student".equalsIgnoreCase(role)) {
            return LINK_NOT_NEEDED;
        }
        if (linkedStudentProfile) {
            return LINKED_PROFILE;
        }
        if (REVIEW_APPROVED_DB.equals(normalizeReviewStatus(reviewStatusDb))) {
            return "Missing";
        }
        return LINK_PENDING;
    }

    private String normalizeReviewStatus(String reviewStatus) {
        if (reviewStatus == null || reviewStatus.trim().isEmpty()) {
            return REVIEW_APPROVED_DB;
        }

        String normalized = reviewStatus.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case REVIEW_PENDING_DB, "PENDING" -> REVIEW_PENDING_DB;
            case REVIEW_DECLINED_DB, "REJECTED" -> REVIEW_DECLINED_DB;
            default -> REVIEW_APPROVED_DB;
        };
    }

    private String normalizeAccountStatus(String status, String normalizedReviewStatus) {
        if (REVIEW_PENDING_DB.equals(normalizedReviewStatus) || REVIEW_DECLINED_DB.equals(normalizedReviewStatus)) {
            return "Suspended";
        }
        return status == null || status.trim().isEmpty() ? "Active" : status.trim();
    }

    private String normalizeProfileStatus(String profileStatus) {
        return profileStatus == null || profileStatus.trim().isEmpty() ? PROFILE_ON : profileStatus.trim();
    }

    private boolean toDatabaseAccountStatus(String status) {
        return "Active".equalsIgnoreCase(status);
    }

    private boolean toDatabaseProfileStatus(String profileStatus) {
        return PROFILE_ON.equalsIgnoreCase(profileStatus);
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

    private String formatDate(Timestamp value) {
        if (value == null) {
            return "Non disponible";
        }
        return value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String inferFraudRisk(String fullName, String email) {
        int riskScore = 10;
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String localPart = normalizedEmail.contains("@")
            ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
            : normalizedEmail;

        if (!normalizedName.contains(" ")) {
            riskScore += 20;
        }

        if (normalizedName.length() < 8) {
            riskScore += 25;
        }

        if (normalizedEmail.matches(".*\\d{4,}.*")) {
            riskScore += 20;
        }

        if (localPart.length() < 5) {
            riskScore += 10;
        }

        if (normalizedEmail.endsWith("@gmail.com")
            || normalizedEmail.endsWith("@yahoo.com")
            || normalizedEmail.endsWith("@outlook.com")) {
            riskScore += 5;
        }

        if (riskScore >= 70) {
            return "HIGH";
        }
        if (riskScore >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String inferFraudReason(String fullName, String email, String risk) {
        List<String> reasons = new ArrayList<>();
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        String localPart = normalizedEmail.contains("@")
            ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
            : normalizedEmail;

        if (!normalizedName.contains(" ")) {
            reasons.add("Full name is a single token.");
        }

        if (normalizedName.length() < 8) {
            reasons.add("Full name is very short.");
        }

        if (normalizedEmail.matches(".*\\d{4,}.*")) {
            reasons.add("Email contains a long numeric suffix.");
        }

        if (localPart.length() < 5) {
            reasons.add("Email prefix is unusually short.");
        }

        if (reasons.isEmpty()) {
            reasons.add("No blocking signals detected.");
        }

        return risk + " review: " + String.join(" ", reasons);
    }

    private String buildAdminInsight(String role, String reviewStatus, String accountStatus, String profileStatus,
                                     String linkedProfileStatus, String fraudRisk) {
        List<String> fragments = new ArrayList<>();

        if (REVIEW_PENDING.equalsIgnoreCase(reviewStatus)) {
            fragments.add("Awaiting approval from the review queue.");
        } else if (REVIEW_DECLINED.equalsIgnoreCase(reviewStatus)) {
            fragments.add("Currently declined and blocked from sign-in.");
        } else if ("Suspended".equalsIgnoreCase(accountStatus)) {
            fragments.add("Approved account is currently deactivated.");
        } else {
            fragments.add("Account is approved and ready for use.");
        }

        if ("HIGH".equalsIgnoreCase(fraudRisk)) {
            fragments.add("High-risk signals are present, so approval and activation stay blocked.");
        } else if ("MEDIUM".equalsIgnoreCase(fraudRisk)) {
            fragments.add("Medium-risk signals suggest a quick manual identity check.");
        }

        if ("Student".equalsIgnoreCase(role)) {
            if (LINKED_PROFILE.equalsIgnoreCase(linkedProfileStatus)) {
                fragments.add("Linked student profile is already available.");
            } else {
                fragments.add("Student profile will be auto-created once the account is approved.");
            }
        }

        if (PROFILE_OFF.equalsIgnoreCase(profileStatus)) {
            fragments.add("Profile visibility is turned off.");
        }

        return String.join(" ", fragments);
    }

    private String buildInsightsSummary(int pendingCount, int approvedCount, int declinedCount, int linkedStudentProfiles,
                                        int profileOffCount, int mediumRiskCount, int highRiskCount) {
        StringBuilder summary = new StringBuilder();
        if (pendingCount > 0) {
            summary.append(pendingCount).append(" account(s) are waiting for review");
            if (highRiskCount > 0 || mediumRiskCount > 0) {
                summary.append(", including ").append(highRiskCount).append(" high-risk and ")
                    .append(mediumRiskCount).append(" medium-risk profile(s)");
            }
            summary.append(". ");
        } else {
            summary.append("No accounts are waiting in the review queue. ");
        }

        summary.append(approvedCount).append(" approved account(s) are already live, ")
            .append(declinedCount).append(" declined account(s) remain blocked, and ")
            .append(linkedStudentProfiles).append(" linked student profile(s) are synced. ");

        if (profileOffCount > 0) {
            summary.append(profileOffCount).append(" profile(s) are hidden from activity visibility.");
        } else {
            summary.append("All visible profiles are currently active.");
        }

        return summary.toString().trim();
    }

    private String validateApprovalGate(String fraudRisk, String reviewStatus) {
        if ("HIGH".equalsIgnoreCase(fraudRisk)) {
            return "Ce compte est bloque par le controle fraude (risque HIGH). Corrigez les signaux suspects avant approbation ou activation.";
        }
        if (!REVIEW_APPROVED.equalsIgnoreCase(reviewStatus) && "HIGH".equalsIgnoreCase(fraudRisk)) {
            return "Ce compte doit passer un controle manuel avant activation.";
        }
        return null;
    }

    private String buildSystemReviewNote(String reviewStatus, String context) {
        if (REVIEW_PENDING.equalsIgnoreCase(reviewStatus)) {
            return "Queued for manual review. " + context;
        }
        if (REVIEW_DECLINED.equalsIgnoreCase(reviewStatus)) {
            return "Declined from backoffice. " + context;
        }
        return "Approved from backoffice. " + context;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
