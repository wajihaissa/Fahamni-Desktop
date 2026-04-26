package tn.esprit.fahamni.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.AdminUser;
import tn.esprit.fahamni.services.AdminUserService;
import tn.esprit.fahamni.utils.OperationResult;

import java.util.ArrayList;
import java.util.List;

public class BackofficeUsersController {

    @FXML
    private VBox pendingRegistrationsContainer;

    @FXML
    private TableView<AdminUser> usersTable;

    @FXML
    private TableColumn<AdminUser, String> fullNameColumn;

    @FXML
    private TableColumn<AdminUser, String> emailColumn;

    @FXML
    private TableColumn<AdminUser, String> roleColumn;

    @FXML
    private TableColumn<AdminUser, String> reviewStatusColumn;

    @FXML
    private TableColumn<AdminUser, String> statusColumn;

    @FXML
    private TableColumn<AdminUser, String> profileStatusColumn;

    @FXML
    private TableColumn<AdminUser, String> fraudRiskColumn;

    @FXML
    private TableColumn<AdminUser, String> linkedProfileColumn;

    @FXML
    private TableColumn<AdminUser, String> createdAtColumn;

    @FXML
    private TableColumn<AdminUser, Void> actionsColumn;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private ComboBox<String> roleFilterComboBox;

    @FXML
    private Button allReviewsFilterButton;

    @FXML
    private Button pendingReviewsFilterButton;

    @FXML
    private Button approvedReviewsFilterButton;

    @FXML
    private Button declinedReviewsFilterButton;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private ComboBox<String> roleComboBox;

    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private ComboBox<String> reviewStatusComboBox;

    @FXML
    private ComboBox<String> profileStatusComboBox;

    @FXML
    private Label totalUsersLabel;

    @FXML
    private Label directorySummaryLabel;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Label selectionSummaryLabel;

    @FXML
    private Label pendingCountValueLabel;

    @FXML
    private Label approvedCountValueLabel;

    @FXML
    private Label declinedCountValueLabel;

    @FXML
    private Label linkedProfilesValueLabel;

    @FXML
    private Label insightsSummaryLabel;

    @FXML
    private Label selectedUserInsightLabel;

    private final AdminUserService userService = new AdminUserService();
    private final ObservableList<AdminUser> pendingUsers = FXCollections.observableArrayList();
    private FilteredList<AdminUser> filteredUsers;
    private String currentReviewFilter = "ALL";

    @FXML
    private void initialize() {
        configureTable();
        configureFilters();
        configureEditorDefaults();

        filteredUsers = new FilteredList<>(userService.getUsers(), user -> true);
        usersTable.setItems(filteredUsers);
        usersTable.getSelectionModel().getSelectedItems().addListener(
            (ListChangeListener<AdminUser>) change -> handleSelectionChanged());

        refreshDerivedState();
        hideFeedback();

        if (userService.getLastLoadError() != null) {
            showFeedback(userService.getLastLoadError(), false);
        }
    }

    @FXML
    private void handleAddNewUser() {
        usersTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
        fullNameField.requestFocus();
    }

    @FXML
    private void handleCreateUser() {
        hideFeedback();
        OperationResult result = userService.createUser(
            fullNameField.getText(),
            emailField.getText(),
            passwordField.getText(),
            confirmPasswordField.getText(),
            roleComboBox.getValue(),
            statusComboBox.getValue(),
            reviewStatusComboBox.getValue(),
            profileStatusComboBox.getValue()
        );
        afterMutation(result, true);
    }

    @FXML
    private void handleUpdateUser() {
        hideFeedback();
        AdminUser selectedUser = getSingleSelectedUser();
        if (selectedUser == null) {
            showFeedback("Selectionnez un seul utilisateur pour la mise a jour.", false);
            return;
        }

        OperationResult result = userService.updateUser(
            selectedUser,
            fullNameField.getText(),
            emailField.getText(),
            roleComboBox.getValue(),
            statusComboBox.getValue(),
            reviewStatusComboBox.getValue(),
            profileStatusComboBox.getValue()
        );
        afterMutation(result, false);
    }

    @FXML
    private void handleDeleteUser() {
        hideFeedback();
        AdminUser selectedUser = getSingleSelectedUser();
        if (selectedUser == null) {
            showFeedback("Selectionnez un seul utilisateur a suspendre.", false);
            return;
        }

        OperationResult result = userService.deleteUser(selectedUser);
        afterMutation(result, false);
    }

    @FXML
    private void handleRefreshUsers() {
        hideFeedback();
        OperationResult result = userService.refreshUsers();
        usersTable.getSelectionModel().clearSelection();
        clearForm();
        refreshDerivedState();
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleResetForm() {
        usersTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
    }

    @FXML
    private void handleReviewFilterAction(ActionEvent event) {
        if (!(event.getSource() instanceof Button button) || button.getUserData() == null) {
            return;
        }

        currentReviewFilter = button.getUserData().toString();
        refreshReviewFilterButtons();
        applyFilters();
    }

    @FXML
    private void handleBulkApprove() {
        performBulkAction("APPROVE");
    }

    @FXML
    private void handleBulkDecline() {
        performBulkAction("DECLINE");
    }

    @FXML
    private void handleBulkMoveToPending() {
        performBulkAction("MOVE_TO_PENDING");
    }

    @FXML
    private void handleBulkActivate() {
        performBulkAction("ACTIVATE");
    }

    @FXML
    private void handleBulkSuspend() {
        performBulkAction("SUSPEND");
    }

    @FXML
    private void handleBulkToggleProfile() {
        performBulkAction("TOGGLE_PROFILE");
    }

    private void configureTable() {
        usersTable.setFixedCellSize(78.0);
        usersTable.setPlaceholder(new Label("No users found for the current filters."));
        usersTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        reviewStatusColumn.setCellValueFactory(new PropertyValueFactory<>("reviewStatus"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        profileStatusColumn.setCellValueFactory(new PropertyValueFactory<>("profileStatus"));
        fraudRiskColumn.setCellValueFactory(new PropertyValueFactory<>("fraudReason"));
        linkedProfileColumn.setCellValueFactory(new PropertyValueFactory<>("linkedProfileStatus"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        roleColumn.setCellFactory(column -> createBadgeCell("backoffice-role-chip", value -> null));
        reviewStatusColumn.setCellFactory(column -> createBadgeCell("backoffice-status-chip", this::resolveReviewStatusStyle));
        statusColumn.setCellFactory(column -> createBadgeCell("backoffice-status-chip", this::resolveAccountStatusStyle));
        profileStatusColumn.setCellFactory(column -> createBadgeCell("backoffice-status-chip", this::resolveProfileStatusStyle));
        linkedProfileColumn.setCellFactory(column -> createBadgeCell("backoffice-status-chip", this::resolveLinkedProfileStyle));

        fraudRiskColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                AdminUser user = getTableView().getItems().get(getIndex());
                Label riskBadge = createRiskBadge(user);
                Label riskReason = new Label(compactText(item, 72));
                riskReason.setWrapText(false);
                riskReason.setTextOverrun(OverrunStyle.ELLIPSIS);
                riskReason.setMaxWidth(200.0);
                riskReason.getStyleClass().add("backoffice-risk-copy");
                Tooltip.install(riskReason, new Tooltip(item));
                VBox box = new VBox(6.0, riskBadge, riskReason);
                setGraphic(box);
            }
        });

        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button approveButton = new Button("Approve");
            private final Button queueButton = new Button("Queue");
            private final Button accessButton = new Button("Off");
            private final HBox actionBox = new HBox(6.0, editButton, approveButton, queueButton, accessButton);

            {
                actionBox.setAlignment(Pos.CENTER_LEFT);
                editButton.getStyleClass().add("backoffice-table-action-button");
                approveButton.getStyleClass().add("backoffice-table-action-button");
                queueButton.getStyleClass().add("backoffice-table-action-button");
                accessButton.getStyleClass().addAll("backoffice-table-action-button", "danger");

                editButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        usersTable.getSelectionModel().clearSelection();
                        usersTable.getSelectionModel().select(user);
                        populateForm(user);
                        showFeedback("Utilisateur charge dans l'editeur.", true);
                    }
                });

                approveButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        afterMutation(userService.approveUser(user), false);
                    }
                });

                queueButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        afterMutation(userService.moveUserBackToPending(user), false);
                    }
                });

                accessButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        OperationResult result = "Active".equalsIgnoreCase(user.getStatus())
                            ? userService.deleteUser(user)
                            : userService.activateUser(user);
                        afterMutation(result, false);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                AdminUser user = getCurrentUser();
                if (user == null) {
                    setGraphic(null);
                    return;
                }

                approveButton.setDisable("Approved".equalsIgnoreCase(user.getReviewStatus()));
                queueButton.setDisable("Pending Review".equalsIgnoreCase(user.getReviewStatus()));
                accessButton.setText("Active".equalsIgnoreCase(user.getStatus()) ? "Off" : "On");
                setGraphic(actionBox);
            }

            private AdminUser getCurrentUser() {
                if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    return null;
                }
                return getTableView().getItems().get(getIndex());
            }
        });
    }

    private void configureFilters() {
        statusFilterComboBox.getItems().setAll("All Account States", "Active", "Suspended");
        roleFilterComboBox.getItems().setAll("All Roles", "Student", "Tutor", "Administrator");
        statusFilterComboBox.setValue("All Account States");
        roleFilterComboBox.setValue("All Roles");

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        roleFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        refreshReviewFilterButtons();
    }

    private void configureEditorDefaults() {
        roleComboBox.getItems().setAll(userService.getAvailableRoles());
        statusComboBox.getItems().setAll(userService.getAvailableStatuses());
        reviewStatusComboBox.getItems().setAll(userService.getAvailableReviewStatuses());
        profileStatusComboBox.getItems().setAll(userService.getAvailableProfileStatuses());
        clearForm();
    }

    private TableCell<AdminUser, String> createBadgeCell(String baseStyleClass, StyleResolver styleResolver) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Label badge = new Label(item);
                badge.getStyleClass().add(baseStyleClass);
                String extraStyle = styleResolver.resolve(item);
                if (extraStyle != null && !extraStyle.isBlank()) {
                    badge.getStyleClass().add(extraStyle);
                }
                setGraphic(badge);
            }
        };
    }

    private Label createRiskBadge(AdminUser user) {
        Label badge = new Label(user.getFraudRisk());
        badge.getStyleClass().add("backoffice-risk-chip");
        if ("MEDIUM".equalsIgnoreCase(user.getFraudRisk())) {
            badge.getStyleClass().add("medium");
        } else if ("HIGH".equalsIgnoreCase(user.getFraudRisk())) {
            badge.getStyleClass().add("high");
        }
        return badge;
    }

    private String resolveReviewStatusStyle(String reviewStatus) {
        if ("Pending Review".equalsIgnoreCase(reviewStatus)) {
            return "pending-review";
        }
        if ("Declined".equalsIgnoreCase(reviewStatus)) {
            return "declined-review";
        }
        return "approved-review";
    }

    private String resolveAccountStatusStyle(String accountStatus) {
        return "Suspended".equalsIgnoreCase(accountStatus) ? "suspended" : "active-status";
    }

    private String resolveProfileStatusStyle(String profileStatus) {
        return "Profile Off".equalsIgnoreCase(profileStatus) ? "profile-off" : "profile-on";
    }

    private String resolveLinkedProfileStyle(String linkedProfileStatus) {
        if ("Linked".equalsIgnoreCase(linkedProfileStatus)) {
            return "linked";
        }
        if ("Missing".equalsIgnoreCase(linkedProfileStatus)) {
            return "linked-missing";
        }
        return "linked-pending";
    }

    private void refreshDerivedState() {
        applyFilters();
        refreshPendingRegistrations();
        updateInsights();
        updateSelectionSummary();
        selectedUserInsightLabel.setText("Select one user to inspect tailored admin guidance.");
    }

    private void applyFilters() {
        if (filteredUsers == null) {
            return;
        }

        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStatus = statusFilterComboBox.getValue();
        String selectedRole = roleFilterComboBox.getValue();

        filteredUsers.setPredicate(user -> {
            boolean matchesSearch = search.isEmpty()
                || user.getFullName().toLowerCase().contains(search)
                || user.getEmail().toLowerCase().contains(search);

            boolean matchesAccountStatus = selectedStatus == null
                || "All Account States".equalsIgnoreCase(selectedStatus)
                || user.getStatus().equalsIgnoreCase(selectedStatus);

            boolean matchesRole = selectedRole == null
                || "All Roles".equalsIgnoreCase(selectedRole)
                || user.getRole().equalsIgnoreCase(selectedRole);

            boolean matchesReview = switch (currentReviewFilter) {
                case "PENDING" -> "Pending Review".equalsIgnoreCase(user.getReviewStatus());
                case "APPROVED" -> "Approved".equalsIgnoreCase(user.getReviewStatus());
                case "DECLINED" -> "Declined".equalsIgnoreCase(user.getReviewStatus());
                default -> true;
            };

            return matchesSearch && matchesAccountStatus && matchesRole && matchesReview;
        });

        updateDirectorySummary();
    }

    private void refreshReviewFilterButtons() {
        setFilterButtonState(allReviewsFilterButton, "ALL".equals(currentReviewFilter));
        setFilterButtonState(pendingReviewsFilterButton, "PENDING".equals(currentReviewFilter));
        setFilterButtonState(approvedReviewsFilterButton, "APPROVED".equals(currentReviewFilter));
        setFilterButtonState(declinedReviewsFilterButton, "DECLINED".equals(currentReviewFilter));
    }

    private void setFilterButtonState(Button button, boolean active) {
        if (button == null) {
            return;
        }

        if (active) {
            if (!button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }
        } else {
            button.getStyleClass().remove("active");
        }
    }

    private void populateForm(AdminUser user) {
        if (user == null) {
            return;
        }

        fullNameField.setText(user.getFullName());
        emailField.setText(user.getEmail());
        roleComboBox.setValue(user.getRole());
        statusComboBox.setValue(user.getStatus());
        reviewStatusComboBox.setValue(user.getReviewStatus());
        profileStatusComboBox.setValue(user.getProfileStatus());
        passwordField.clear();
        confirmPasswordField.clear();
        selectedUserInsightLabel.setText(user.getAdminInsight());
        Tooltip.install(selectedUserInsightLabel, new Tooltip(user.getFraudReason()));
    }

    private void clearForm() {
        fullNameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        roleComboBox.setValue("Student");
        statusComboBox.setValue("Active");
        reviewStatusComboBox.setValue("Approved");
        profileStatusComboBox.setValue("Profile On");
        selectedUserInsightLabel.setText("Select one user to inspect tailored admin guidance.");
    }

    private void refreshPendingRegistrations() {
        pendingUsers.setAll(userService.getPendingUsersPreview());
        pendingRegistrationsContainer.getChildren().clear();

        if (pendingUsers.isEmpty()) {
            Label emptyLabel = new Label("No accounts are waiting in the review queue right now.");
            emptyLabel.getStyleClass().add("backoffice-users-section-copy-light");
            pendingRegistrationsContainer.getChildren().add(emptyLabel);
        } else {
            for (AdminUser user : pendingUsers) {
                pendingRegistrationsContainer.getChildren().add(createPendingCard(user));
            }
        }

        totalUsersLabel.setText(userService.getUsers().size() + " users");
    }

    private VBox createPendingCard(AdminUser user) {
        VBox identityBox = new VBox(
            6.0,
            createCellLabel(user.getFullName(), "backoffice-pending-primary"),
            createCellLabel(user.getEmail(), "backoffice-pending-secondary")
        );

        VBox requestBox = new VBox(
            6.0,
            createCellLabel(user.getCreatedAt(), "backoffice-pending-primary"),
            createCellLabel(user.getRole() + " / " + user.getReviewStatus(), "backoffice-pending-secondary")
        );

        Label riskBadge = createRiskBadge(user);
        Label reasonLabel = createCellLabel(compactText(user.getFraudReason(), 120), "backoffice-pending-secondary");
        reasonLabel.setWrapText(true);
        Label insightLabel = createCellLabel(user.getAdminInsight(), "backoffice-pending-secondary");
        insightLabel.setWrapText(true);
        VBox riskBox = new VBox(6.0, riskBadge, reasonLabel, insightLabel);

        Button approveButton = new Button("Approve");
        approveButton.getStyleClass().add("backoffice-primary-button");
        approveButton.setOnAction(event -> afterMutation(userService.approveUser(user), false));

        Button declineButton = new Button("Decline");
        declineButton.getStyleClass().add("backoffice-secondary-button");
        declineButton.setOnAction(event -> afterMutation(userService.declineUser(user), false));

        Button editorButton = new Button("Open Editor");
        editorButton.getStyleClass().add("backoffice-secondary-button");
        editorButton.setOnAction(event -> {
            usersTable.getSelectionModel().clearSelection();
            usersTable.getSelectionModel().select(user);
            populateForm(user);
            showFeedback("Utilisateur charge dans l'editeur.", true);
        });

        HBox actionBox = new HBox(8.0, approveButton, declineButton, editorButton);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(18.0, wrapBox(identityBox), wrapBox(requestBox), wrapBox(riskBox), actionBox);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.getStyleClass().add("backoffice-pending-card");
        return card;
    }

    private Label createCellLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private VBox wrapBox(VBox box) {
        box.setPrefWidth(240.0);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void updateDirectorySummary() {
        if (filteredUsers == null) {
            directorySummaryLabel.setText("All users");
            return;
        }
        directorySummaryLabel.setText(filteredUsers.size() + " visible users");
    }

    private void updateInsights() {
        AdminUserService.AdminUserInsights insights = userService.getAdminInsights();
        pendingCountValueLabel.setText(String.valueOf(insights.pendingCount()));
        approvedCountValueLabel.setText(String.valueOf(insights.approvedCount()));
        declinedCountValueLabel.setText(String.valueOf(insights.declinedCount()));
        linkedProfilesValueLabel.setText(String.valueOf(insights.linkedStudentProfiles()));
        insightsSummaryLabel.setText(insights.summary());
    }

    private void updateSelectionSummary() {
        int selectedCount = usersTable.getSelectionModel().getSelectedItems().size();
        selectionSummaryLabel.setText(selectedCount + " selected");
    }

    private void handleSelectionChanged() {
        updateSelectionSummary();
        ObservableList<AdminUser> selectedItems = usersTable.getSelectionModel().getSelectedItems();
        if (selectedItems.size() == 1) {
            populateForm(selectedItems.get(0));
            return;
        }

        if (selectedItems.isEmpty()) {
            clearForm();
            return;
        }

        passwordField.clear();
        confirmPasswordField.clear();
        selectedUserInsightLabel.setText("Bulk selection active. Use the action bar above the table to update the selected users.");
    }

    private void performBulkAction(String actionId) {
        hideFeedback();
        List<AdminUser> selectedUsers = new ArrayList<>(usersTable.getSelectionModel().getSelectedItems());
        OperationResult result = userService.applyBulkAction(selectedUsers, actionId);
        afterMutation(result, true);
    }

    private void afterMutation(OperationResult result, boolean clearEditorOnSuccess) {
        if (result.isSuccess()) {
            usersTable.getSelectionModel().clearSelection();
            if (clearEditorOnSuccess) {
                clearForm();
            }
            refreshDerivedState();
            usersTable.refresh();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    private AdminUser getSingleSelectedUser() {
        ObservableList<AdminUser> selectedItems = usersTable.getSelectionModel().getSelectedItems();
        if (selectedItems.size() != 1) {
            return null;
        }
        return selectedItems.get(0);
    }

    private String compactText(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.getStyleClass().setAll("backoffice-feedback");
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
    }

    @FunctionalInterface
    private interface StyleResolver {
        String resolve(String value);
    }
}
