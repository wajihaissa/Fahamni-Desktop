package tn.esprit.fahamni.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
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
    private TableColumn<AdminUser, String> statusColumn;

    @FXML
    private TableColumn<AdminUser, String> fraudRiskColumn;

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
    private Label totalUsersLabel;

    @FXML
    private Label directorySummaryLabel;

    @FXML
    private Label feedbackLabel;

    private final AdminUserService userService = new AdminUserService();
    private FilteredList<AdminUser> filteredUsers;
    private final ObservableList<AdminUser> pendingUsers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        configureTable();
        configureFilters();
        configurePendingRegistrations();
        configureEditorDefaults();

        filteredUsers = new FilteredList<>(userService.getUsers(), user -> true);
        usersTable.setItems(filteredUsers);
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));

        refreshPendingRegistrations();
        updateDirectorySummary();
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
            statusComboBox.getValue()
        );

        afterDataChange(result, true);
    }

    @FXML
    private void handleUpdateUser() {
        hideFeedback();

        OperationResult result = userService.updateUser(
            usersTable.getSelectionModel().getSelectedItem(),
            fullNameField.getText(),
            emailField.getText(),
            roleComboBox.getValue(),
            statusComboBox.getValue()
        );

        afterDataChange(result, false);
    }

    @FXML
    private void handleDeleteUser() {
        hideFeedback();
        OperationResult result = userService.deleteUser(usersTable.getSelectionModel().getSelectedItem());
        afterDataChange(result, false);
    }

    @FXML
    private void handleRefreshUsers() {
        hideFeedback();
        OperationResult result = userService.refreshUsers();
        applyFilters();
        usersTable.getSelectionModel().clearSelection();
        clearForm();
        refreshPendingRegistrations();
        updateDirectorySummary();
        usersTable.refresh();
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleResetForm() {
        usersTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
    }

    private void configureTable() {
        usersTable.setFixedCellSize(74.0);
        usersTable.setPlaceholder(new Label("No users found for the current filters."));
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        fraudRiskColumn.setCellValueFactory(new PropertyValueFactory<>("fraudReason"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        roleColumn.setCellFactory(column -> createBadgeCell("backoffice-role-chip"));
        statusColumn.setCellFactory(column -> createStatusBadgeCell());
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
                Label riskReason = new Label(compactFraudReason(item));
                riskReason.setWrapText(false);
                riskReason.setTextOverrun(OverrunStyle.ELLIPSIS);
                riskReason.setMaxWidth(180.0);
                riskReason.getStyleClass().add("backoffice-risk-copy");
                Tooltip.install(riskReason, new Tooltip(item));
                VBox box = new VBox(6.0, riskBadge, riskReason);
                setGraphic(box);
            }
        });

        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button activateButton = new Button("On");
            private final Button suspendButton = new Button("Off");
            private final HBox actionsBox = new HBox(8.0, editButton, activateButton, suspendButton);

            {
                actionsBox.setAlignment(Pos.CENTER_LEFT);
                editButton.getStyleClass().add("backoffice-table-action-button");
                activateButton.getStyleClass().add("backoffice-table-action-button");
                suspendButton.getStyleClass().addAll("backoffice-table-action-button", "danger");

                editButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        usersTable.getSelectionModel().select(user);
                        populateForm(user);
                        showFeedback("Utilisateur charge dans l'editeur.", true);
                    }
                });

                activateButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        OperationResult result = userService.activateUser(user);
                        afterInlineAction(result);
                    }
                });

                suspendButton.setOnAction(event -> {
                    AdminUser user = getCurrentUser();
                    if (user != null) {
                        OperationResult result = userService.deleteUser(user);
                        afterInlineAction(result);
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

                activateButton.setDisable("Active".equalsIgnoreCase(user.getStatus()));
                suspendButton.setDisable("Suspended".equalsIgnoreCase(user.getStatus()));
                setGraphic(actionsBox);
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
        statusFilterComboBox.getItems().setAll("All Statuses", "Active", "Suspended");
        roleFilterComboBox.getItems().setAll("All Roles", "Student", "Tutor", "Administrator");
        statusFilterComboBox.setValue("All Statuses");
        roleFilterComboBox.setValue("All Roles");

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        roleFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
    }

    private void configurePendingRegistrations() {
        refreshPendingRegistrations();
    }

    private void configureEditorDefaults() {
        roleComboBox.getItems().setAll(userService.getAvailableRoles());
        statusComboBox.getItems().setAll(userService.getAvailableStatuses());
        roleComboBox.setValue("Student");
        statusComboBox.setValue("Active");
    }

    private TableCell<AdminUser, String> createBadgeCell(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Label badge = new Label(item);
                badge.getStyleClass().add(styleClass);
                setGraphic(badge);
            }
        };
    }

    private TableCell<AdminUser, String> createStatusBadgeCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Label badge = new Label(item);
                badge.getStyleClass().add("backoffice-status-chip");
                if ("Suspended".equalsIgnoreCase(item)) {
                    badge.getStyleClass().add("suspended");
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
        }
        return badge;
    }

    private String compactFraudReason(String reason) {
        if (reason == null) {
            return "";
        }

        String normalized = reason.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 52) {
            return normalized;
        }

        return normalized.substring(0, 49) + "...";
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

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStatus = statusFilterComboBox.getValue();
        String selectedRole = roleFilterComboBox.getValue();

        filteredUsers.setPredicate(user -> {
            boolean matchesSearch = search.isEmpty()
                || user.getFullName().toLowerCase().contains(search)
                || user.getEmail().toLowerCase().contains(search);

            boolean matchesStatus = selectedStatus == null
                || "All Statuses".equalsIgnoreCase(selectedStatus)
                || user.getStatus().equalsIgnoreCase(selectedStatus);

            boolean matchesRole = selectedRole == null
                || "All Roles".equalsIgnoreCase(selectedRole)
                || user.getRole().equalsIgnoreCase(selectedRole);

            return matchesSearch && matchesStatus && matchesRole;
        });

        updateDirectorySummary();
    }

    private void populateForm(AdminUser user) {
        if (user == null) {
            return;
        }

        fullNameField.setText(user.getFullName());
        emailField.setText(user.getEmail());
        roleComboBox.setValue(user.getRole());
        statusComboBox.setValue(user.getStatus());
        passwordField.clear();
        confirmPasswordField.clear();
    }

    private void clearForm() {
        fullNameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        roleComboBox.setValue("Student");
        statusComboBox.setValue("Active");
    }

    private void refreshPendingRegistrations() {
        pendingUsers.setAll(userService.getPendingUsersPreview());
        pendingRegistrationsContainer.getChildren().clear();

        if (pendingUsers.isEmpty()) {
            Label emptyLabel = new Label("No recent registrations to review right now.");
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
            createCellLabel(user.getRole(), "backoffice-pending-secondary")
        );

        Label riskBadge = createRiskBadge(user);
        Label reasonLabel = createCellLabel(user.getFraudReason(), "backoffice-pending-secondary");
        reasonLabel.setWrapText(true);
        Button reasonButton = new Button("View full reasons");
        reasonButton.getStyleClass().add("backoffice-pending-link-button");
        reasonButton.setOnAction(event -> showFeedback(user.getFraudReason(), true));
        VBox riskBox = new VBox(6.0, riskBadge, reasonLabel, reasonButton);

        Button acceptButton = new Button("Accept");
        acceptButton.getStyleClass().add("backoffice-primary-button");
        acceptButton.setOnAction(event -> afterInlineAction(userService.activateUser(user)));

        Button declineButton = new Button("Decline");
        declineButton.getStyleClass().add("backoffice-secondary-button");
        declineButton.setOnAction(event -> afterInlineAction(userService.deleteUser(user)));
        HBox actionBox = new HBox(8.0, acceptButton, declineButton);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(18.0, wrapBox(identityBox), wrapBox(requestBox), wrapBox(riskBox), actionBox);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.getStyleClass().add("backoffice-pending-card");
        return card;
    }

    private void updateDirectorySummary() {
        if (filteredUsers == null) {
            directorySummaryLabel.setText("All users");
            return;
        }
        directorySummaryLabel.setText(filteredUsers.size() + " visible users");
    }

    private void afterDataChange(OperationResult result, boolean clearEditorOnSuccess) {
        if (result.isSuccess()) {
            applyFilters();
            refreshPendingRegistrations();
            usersTable.refresh();
            if (clearEditorOnSuccess) {
                clearForm();
            }
        }

        showFeedback(result.getMessage(), result.isSuccess());
    }

    private void afterInlineAction(OperationResult result) {
        if (result.isSuccess()) {
            applyFilters();
            refreshPendingRegistrations();
            usersTable.refresh();
        }
        showFeedback(result.getMessage(), result.isSuccess());
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
}
