package tn.esprit.fahamni.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.services.AuthService;
import tn.esprit.fahamni.services.PasswordResetService;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserSession;

import java.net.URL;
import java.util.Optional;

public class LoginController {

    private final AuthService authService = new AuthService();
    private final PasswordResetService passwordResetService = new PasswordResetService();

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
    private ComboBox<String> roleComboBox;

    @FXML
    private Button createAccountButton;

    @FXML
    private Label loginMessageLabel;

    @FXML
    private Label registerMessageLabel;

    @FXML
    private AnchorPane loginRoot;

    @FXML
    private VBox brandBlock;

    @FXML
    private VBox loginFormCard;

    @FXML
    private ImageView loginArtImage;

    @FXML
    public void initialize() {
        if (roleComboBox != null) {
            roleComboBox.getItems().setAll("Student", "Tutor");
            roleComboBox.setValue("Student");
        }
        hideMessage(loginMessageLabel);
        hideMessage(registerMessageLabel);
        switchMode(true);
        playIntroAnimation();
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
            String authError = authService.getLastAuthenticationError();
            showMessage(loginMessageLabel, authError != null ? authError : "Email ou mot de passe invalide.", false);
            return;
        }

        String jwtToken = authService.issueJwt(authenticatedUser);
        if (jwtToken == null || !authService.isJwtValidForUser(jwtToken, authenticatedUser)) {
            showMessage(loginMessageLabel, "Erreur lors de la creation du token de session.", false);
            return;
        }

        UserSession.start(authenticatedUser, jwtToken);

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
        String selectedRole = roleComboBox != null ? roleComboBox.getValue() : null;

        OperationResult result = authService.register(fullName, email, password, confirmPassword, selectedRole);
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

    @FXML
    private void handleForgotPassword() {
        String initialEmail = emailField != null ? emailField.getText().trim() : "";

        Dialog<String> dialog = createForgotPasswordDialog(initialEmail);
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String requestedEmail = result.get() == null ? "" : result.get().trim();
        if (requestedEmail.isEmpty()) {
            showForgotPasswordFeedback(OperationResult.failure("Veuillez saisir une adresse email."));
            return;
        }

        OperationResult requestResult = passwordResetService.requestReset(requestedEmail);
        showForgotPasswordFeedback(requestResult);
    }

    private void switchMode(boolean signInMode) {
        VBox activePane = signInMode ? signInPane : signUpPane;
        VBox inactivePane = signInMode ? signUpPane : signInPane;

        inactivePane.setOpacity(0.0);
        inactivePane.setTranslateX(signInMode ? 16.0 : -16.0);
        inactivePane.setManaged(false);
        inactivePane.setVisible(false);

        activePane.setManaged(true);
        activePane.setVisible(true);
        activePane.setOpacity(0.0);
        activePane.setTranslateX(signInMode ? -16.0 : 16.0);

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(260), activePane);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);

        TranslateTransition slideTransition = new TranslateTransition(Duration.millis(260), activePane);
        slideTransition.setFromX(signInMode ? -16.0 : 16.0);
        slideTransition.setToX(0.0);

        new ParallelTransition(fadeTransition, slideTransition).play();

        hideMessage(loginMessageLabel);
        hideMessage(registerMessageLabel);
    }

    private void clearRegistrationFields() {
        fullNameField.clear();
        registerEmailField.clear();
        registerPasswordField.clear();
        confirmPasswordField.clear();
        if (roleComboBox != null) {
            roleComboBox.setValue("Student");
        }
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

    private void playIntroAnimation() {
        if (brandBlock != null) {
            brandBlock.setOpacity(0.0);
            brandBlock.setTranslateX(-28.0);

            FadeTransition fadeTransition = new FadeTransition(Duration.millis(520), brandBlock);
            fadeTransition.setFromValue(0.0);
            fadeTransition.setToValue(1.0);

            TranslateTransition slideTransition = new TranslateTransition(Duration.millis(520), brandBlock);
            slideTransition.setFromX(-28.0);
            slideTransition.setToX(0.0);

            new ParallelTransition(fadeTransition, slideTransition).play();
        }

        if (loginFormCard != null) {
            loginFormCard.setOpacity(0.0);
            loginFormCard.setTranslateX(34.0);
            loginFormCard.setScaleX(0.98);
            loginFormCard.setScaleY(0.98);

            FadeTransition fadeTransition = new FadeTransition(Duration.millis(560), loginFormCard);
            fadeTransition.setFromValue(0.0);
            fadeTransition.setToValue(1.0);

            TranslateTransition slideTransition = new TranslateTransition(Duration.millis(560), loginFormCard);
            slideTransition.setFromX(34.0);
            slideTransition.setToX(0.0);

            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(560), loginFormCard);
            scaleTransition.setFromX(0.98);
            scaleTransition.setFromY(0.98);
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);

            new ParallelTransition(fadeTransition, slideTransition, scaleTransition).play();
        }

        if (loginArtImage != null) {
            TranslateTransition floatTransition = new TranslateTransition(Duration.seconds(3.8), loginArtImage);
            floatTransition.setFromY(-7.0);
            floatTransition.setToY(7.0);
            floatTransition.setCycleCount(TranslateTransition.INDEFINITE);
            floatTransition.setAutoReverse(true);
            floatTransition.play();
        }
    }

    private void showForgotPasswordFeedback(OperationResult result) {
        Alert.AlertType alertType = result.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
        Alert alert = new Alert(alertType, result.getMessage(), ButtonType.OK);
        alert.setTitle("Reinitialisation du mot de passe");
        alert.setHeaderText(result.isSuccess() ? "Demande envoyee" : "Demande impossible");
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private Dialog<String> createForgotPasswordDialog(String initialEmail) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Mot de passe oublie");
        dialog.setHeaderText("Recevoir un code de reinitialisation");

        DialogPane dialogPane = dialog.getDialogPane();
        styleDialog(dialogPane);

        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType sendButtonType = new ButtonType("Envoyer le code", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().setAll(cancelButtonType, sendButtonType);

        Label leadCopy = new Label("Saisissez votre adresse email pour recevoir un code de reinitialisation temporaire.");
        leadCopy.getStyleClass().add("login-dialog-copy");
        leadCopy.setWrapText(true);

        Label emailLabel = new Label("Adresse email");
        emailLabel.getStyleClass().add("login-dialog-label");

        TextField emailInput = new TextField(initialEmail);
        emailInput.setPromptText("Entrer votre email");
        emailInput.getStyleClass().add("login-input");

        VBox content = new VBox(12.0, leadCopy, emailLabel, emailInput);
        content.getStyleClass().add("login-dialog-content");
        VBox.setVgrow(emailInput, Priority.NEVER);

        dialogPane.setGraphic(null);
        dialogPane.setContent(content);
        dialog.setResultConverter(buttonType -> buttonType == sendButtonType ? emailInput.getText() : null);

        Window owner = loginRoot != null && loginRoot.getScene() != null ? loginRoot.getScene().getWindow() : null;
        if (owner != null) {
            dialog.initOwner(owner);
        }

        return dialog;
    }

    private void styleDialog(DialogPane dialogPane) {
        if (dialogPane == null) {
            return;
        }

        dialogPane.getStyleClass().add("login-dialog-pane");
        dialogPane.setMinHeight(Region.USE_PREF_SIZE);
        dialogPane.setGraphic(null);

        URL stylesheet = getClass().getResource("/com/fahamni/styles/frontoffice-login.css");
        if (stylesheet != null) {
            String stylesheetUri = stylesheet.toExternalForm();
            if (!dialogPane.getStylesheets().contains(stylesheetUri)) {
                dialogPane.getStylesheets().add(stylesheetUri);
            }
        }
    }
}
