package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminUser;
import tn.esprit.fahamni.services.AdminUserService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class BackofficeUsersController {

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
    private Label feedbackLabel;

    private final AdminUserService userService = new AdminUserService();

    @FXML
    private void initialize() {
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        roleComboBox.getItems().setAll(userService.getAvailableRoles());
        statusComboBox.getItems().setAll(userService.getAvailableStatuses());
        roleComboBox.setValue("Student");
        statusComboBox.setValue("Active");
        roleComboBox.setDisable(true);

        usersTable.setItems(userService.getUsers());
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));

        hideFeedback();

        if (userService.getLastLoadError() != null) {
            showFeedback(userService.getLastLoadError(), false);
        }
    }

    @FXML
    private void handleCreateUser() {
        hideFeedback();

        OperationResult result = userService.createUser(
            fullNameField.getText(),
            emailField.getText(),
            roleComboBox.getValue(),
            statusComboBox.getValue()
        );
        if (result.isSuccess()) {
            clearForm();
        }
        showFeedback(result.getMessage(), result.isSuccess());
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
        if (result.isSuccess()) {
            usersTable.refresh();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleResetForm() {
        usersTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
    }

    @FXML
    private void handleDeleteUser() {
        hideFeedback();

        AdminUser selectedUser = usersTable.getSelectionModel().getSelectedItem();
        OperationResult result = userService.deleteUser(selectedUser);
        if (result.isSuccess()) {
            usersTable.refresh();
            statusComboBox.setValue("Suspended");
        }

        showFeedback(result.getMessage(), result.isSuccess());
    }

    private void populateForm(AdminUser user) {
        if (user == null) {
            return;
        }

        fullNameField.setText(user.getFullName());
        emailField.setText(user.getEmail());
        roleComboBox.setValue(user.getRole());
        statusComboBox.setValue(user.getStatus());
    }

    private void clearForm() {
        fullNameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        roleComboBox.setValue("Student");
        statusComboBox.setValue("Active");
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

