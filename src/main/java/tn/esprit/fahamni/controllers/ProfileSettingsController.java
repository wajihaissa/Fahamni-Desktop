package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.services.UserAccountService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Optional;
import java.util.function.Consumer;

public class ProfileSettingsController {

    @FXML
    private Label sectionTitleLabel;

    @FXML
    private Label sectionCopyLabel;

    @FXML
    private Label profileAvatarLabel;

    @FXML
    private Label profileNameLabel;

    @FXML
    private Label profileEmailLabel;

    @FXML
    private Label profileRoleLabel;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label feedbackLabel;

    private final UserAccountService accountService = new UserAccountService();
    private Consumer<User> onProfileUpdated;
    private Runnable onAccountDeleted;

    @FXML
    private void initialize() {
        hideFeedback();
        refreshViewFromSession();
    }

    public void configure(boolean settingsMode) {
        if (settingsMode) {
            sectionTitleLabel.setText("Parametres du compte");
            sectionCopyLabel.setText("Mettez a jour votre nom, votre email ou votre mot de passe depuis le meme espace.");
        } else {
            sectionTitleLabel.setText("Mon profil");
            sectionCopyLabel.setText("Retrouvez vos informations personnelles et ajustez votre compte quand vous en avez besoin.");
        }
    }

    public void setOnProfileUpdated(Consumer<User> onProfileUpdated) {
        this.onProfileUpdated = onProfileUpdated;
    }

    public void setOnAccountDeleted(Runnable onAccountDeleted) {
        this.onAccountDeleted = onAccountDeleted;
    }

    @FXML
    private void handleSaveProfile() {
        hideFeedback();

        OperationResult result = accountService.updateCurrentUser(
            fullNameField.getText(),
            emailField.getText(),
            passwordField.getText(),
            confirmPasswordField.getText()
        );

        if (!result.isSuccess()) {
            showFeedback(result.getMessage(), false);
            return;
        }

        passwordField.clear();
        confirmPasswordField.clear();
        refreshViewFromSession();
        showFeedback(result.getMessage(), true);

        if (onProfileUpdated != null) {
            onProfileUpdated.accept(UserSession.getCurrentUser());
        }
    }

    @FXML
    private void handleResetForm() {
        hideFeedback();
        passwordField.clear();
        confirmPasswordField.clear();
        refreshViewFromSession();
    }

    @FXML
    private void handleDeleteAccount() {
        hideFeedback();

        if (!confirmAccountDeletion()) {
            return;
        }

        OperationResult result = accountService.deleteCurrentUser();
        if (!result.isSuccess()) {
            showFeedback(result.getMessage(), false);
            return;
        }

        try {
            if (onAccountDeleted != null) {
                onAccountDeleted.run();
            } else {
                Main.showLogin();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshViewFromSession() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            profileAvatarLabel.setText("FS");
            profileNameLabel.setText("Etudiant Fahamni");
            profileEmailLabel.setText("Aucun compte connecte");
            profileRoleLabel.setText("Espace Etudiant");
            fullNameField.clear();
            emailField.clear();
            return;
        }

        profileAvatarLabel.setText(UserSession.getInitials());
        profileNameLabel.setText(currentUser.getFullName());
        profileEmailLabel.setText(currentUser.getEmail());
        profileRoleLabel.setText(UserSession.getRoleLabel());
        fullNameField.setText(currentUser.getFullName());
        emailField.setText(currentUser.getEmail());
    }

    private boolean confirmAccountDeletion() {
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmer la suppression");
        alert.setHeaderText("Supprimer votre compte ?");
        alert.setContentText("Cette action supprimera votre compte utilisateur. Voulez-vous continuer ?");
        alert.getButtonTypes().setAll(cancelButton, deleteButton);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == deleteButton;
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
