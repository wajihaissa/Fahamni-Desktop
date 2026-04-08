package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.services.AuthService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginController {

    private final AuthService authService = new AuthService();

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private VBox signInPane;

    @FXML
    private VBox signUpPane;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button createAccountButton;

    @FXML
    private Label loginMessageLabel;

    @FXML
    private Label registerMessageLabel;

    @FXML
    public void initialize() {
        hideMessage(loginMessageLabel);
        hideMessage(registerMessageLabel);
        switchMode(true);
    }

    @FXML
    private void handleLogin() {
        hideMessage(loginMessageLabel);

        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showMessage(loginMessageLabel, "Veuillez saisir votre email et votre mot de passe.", false);
            return;
        }

        User authenticatedUser = authService.authenticate(email, password);
        if (authenticatedUser == null) {
            showMessage(loginMessageLabel, "Email ou mot de passe invalide.", false);
            return;
        }

        try {
            if (authenticatedUser.getRole() == UserRole.ADMIN) {
                Main.showBackoffice();
            } else {
                Main.showMain();
            }
        } catch (Exception e) {
            e.printStackTrace();
            showMessage(loginMessageLabel, "Erreur lors du chargement de l'application.", false);
        }
    }

    @FXML
    private void handleCreateAccount() {
        hideMessage(registerMessageLabel);

        String fullName = fullNameField.getText().trim();
        String email = registerEmailField.getText().trim();
        String password = registerPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        OperationResult result = authService.register(fullName, email, password, confirmPassword);
        if (!result.isSuccess()) {
            showMessage(registerMessageLabel, result.getMessage(), false);
            return;
        }

        clearRegistrationFields();
        emailField.setText(email);
        passwordField.clear();
        switchMode(true);
        showMessage(loginMessageLabel, result.getMessage(), true);
    }

    @FXML
    private void showSignInMode() {
        switchMode(true);
    }

    @FXML
    private void showSignUpMode() {
        switchMode(false);
    }

    private void switchMode(boolean signInMode) {
        signInPane.setManaged(signInMode);
        signInPane.setVisible(signInMode);
        signUpPane.setManaged(!signInMode);
        signUpPane.setVisible(!signInMode);

        hideMessage(loginMessageLabel);
        hideMessage(registerMessageLabel);
    }

    private void clearRegistrationFields() {
        fullNameField.clear();
        registerEmailField.clear();
        registerPasswordField.clear();
        confirmPasswordField.clear();
    }

    private void showMessage(Label label, String message, boolean success) {
        label.setText(message);
        label.getStyleClass().setAll("login-message-label", success ? "success" : "error");
        label.setManaged(true);
        label.setVisible(true);
    }

    private void hideMessage(Label label) {
        label.setText("");
        label.getStyleClass().setAll("login-message-label");
        label.setManaged(false);
        label.setVisible(false);
    }
}

