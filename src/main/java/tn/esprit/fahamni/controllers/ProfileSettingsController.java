package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.services.FaceRecognitionService;
import tn.esprit.fahamni.services.TwoFactorAuthService;
import tn.esprit.fahamni.services.UserAccountService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserSession;
import tn.esprit.fahamni.utils.WebcamCaptureDialog;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class ProfileSettingsController {

    private enum Section {
        PERSONAL_INFO,
        ACCOUNT_SETTINGS,
        CERTIFICATIONS,
        SECURITY
    }

    @FXML
    private Label profileAvatarLabel;

    @FXML
    private Label sidebarProfileNameLabel;

    @FXML
    private Label sidebarProfileEmailLabel;

    @FXML
    private Label profilePictureStatusLabel;

    @FXML
    private Label contentTitleLabel;

    @FXML
    private Label contentSubtitleLabel;

    @FXML
    private Button personalInfoNavButton;

    @FXML
    private Button accountSettingsNavButton;

    @FXML
    private Button certificationsNavButton;

    @FXML
    private Button securityNavButton;

    @FXML
    private VBox personalInfoSection;

    @FXML
    private VBox accountSettingsSection;

    @FXML
    private VBox certificationsSection;

    @FXML
    private VBox securitySection;

    @FXML
    private TextField firstNameField;

    @FXML
    private TextField lastNameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextArea bioArea;

    @FXML
    private TextField roleField;

    @FXML
    private TextField accountStatusField;

    @FXML
    private TextField accountRoleField;

    @FXML
    private TextField validationStatusField;

    @FXML
    private TextField createdAtField;

    @FXML
    private TextArea certificationsArea;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Label faceEngineStatusLabel;

    @FXML
    private Label faceEnrollmentStatusLabel;

    @FXML
    private Label faceEnrolledAtLabel;

    @FXML
    private Label twoFactorStatusLabel;

    @FXML
    private Label twoFactorConfirmedAtLabel;

    @FXML
    private ImageView twoFactorQrImageView;

    @FXML
    private VBox twoFactorSetupBox;

    @FXML
    private Label twoFactorSecretLabel;

    @FXML
    private TextField twoFactorCodeField;

    @FXML
    private TextArea recoveryCodesArea;

    @FXML
    private VBox recoveryCodesBox;

    @FXML
    private TextField disableTwoFactorCodeField;

    private final UserAccountService accountService = new UserAccountService();
    private final FaceRecognitionService faceRecognitionService = new FaceRecognitionService();
    private final TwoFactorAuthService twoFactorAuthService = new TwoFactorAuthService();
    private Consumer<User> onProfileUpdated;
    private Runnable onAccountDeleted;

    @FXML
    private void initialize() {
        hideFeedback();
        refreshViewFromSession();
        showSection(Section.PERSONAL_INFO);
    }

    public void configure(boolean settingsMode) {
        showSection(settingsMode ? Section.ACCOUNT_SETTINGS : Section.PERSONAL_INFO);
    }

    public void setOnProfileUpdated(Consumer<User> onProfileUpdated) {
        this.onProfileUpdated = onProfileUpdated;
    }

    public void setOnAccountDeleted(Runnable onAccountDeleted) {
        this.onAccountDeleted = onAccountDeleted;
    }

    @FXML
    private void showPersonalInfoSection() {
        showSection(Section.PERSONAL_INFO);
    }

    @FXML
    private void showAccountSettingsSection() {
        showSection(Section.ACCOUNT_SETTINGS);
    }

    @FXML
    private void showCertificationsSection() {
        showSection(Section.CERTIFICATIONS);
    }

    @FXML
    private void showSecuritySection() {
        showSection(Section.SECURITY);
    }

    @FXML
    private void handleChooseProfilePicture() {
        hideFeedback();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une photo de profil");
        fileChooser.getExtensionFilters().setAll(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.webp")
        );

        File selectedFile = fileChooser.showOpenDialog(profileAvatarLabel.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        OperationResult result = accountService.updateCurrentAvatar(selectedFile);
        if (!result.isSuccess()) {
            showFeedback(result.getMessage(), false);
            return;
        }

        refreshViewFromSession();
        showFeedback(result.getMessage(), true);
    }

    @FXML
    private void handleSaveProfile() {
        hideFeedback();

        String fullName = buildFullName();
        OperationResult result = accountService.updateCurrentUser(
            fullName,
            emailField.getText(),
            phoneField.getText(),
            bioArea.getText(),
            "",
            ""
        );

        if (!result.isSuccess()) {
            showFeedback(result.getMessage(), false);
            return;
        }

        refreshViewFromSession();
        showFeedback(result.getMessage(), true);

        if (onProfileUpdated != null) {
            onProfileUpdated.accept(UserSession.getCurrentUser());
        }
    }

    @FXML
    private void handleResetForm() {
        hideFeedback();
        refreshViewFromSession();
    }

    @FXML
    private void handleSaveCertifications() {
        hideFeedback();
        OperationResult result = accountService.updateCurrentCertifications(certificationsArea.getText());
        if (result.isSuccess()) {
            refreshViewFromSession();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleEnrollFaceId() {
        hideFeedback();

        WebcamCaptureDialog.CaptureResult captureResult = WebcamCaptureDialog.captureJpeg(
            profileAvatarLabel.getScene().getWindow(),
            "Enroler Face ID",
            "Prenez un selfie net avec une seule personne visible. Nous enregistrerons uniquement le face_token retourne par Face++."
        );
        if (!captureResult.hasImage()) {
            if (captureResult.message() != null) {
                showFeedback(captureResult.message(), false);
            }
            return;
        }

        OperationResult result = faceRecognitionService.enrollCurrentUserFace(captureResult.imageBytes());
        refreshFaceStatus();
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleRemoveFaceId() {
        hideFeedback();
        OperationResult result = faceRecognitionService.removeCurrentUserFace();
        refreshFaceStatus();
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleChangePassword() {
        hideFeedback();

        Dialog<PasswordChangePayload> dialog = createChangePasswordDialog();
        Optional<PasswordChangePayload> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        PasswordChangePayload payload = result.get();
        OperationResult updateResult = accountService.updateCurrentPassword(payload.newPassword(), payload.confirmPassword());
        showFeedback(updateResult.getMessage(), updateResult.isSuccess());
    }

    @FXML
    private void handleStartTwoFactorSetup() {
        hideFeedback();
        TwoFactorAuthService.SetupStartResult result = twoFactorAuthService.startSetupForCurrentUser();
        if (!result.success()) {
            showFeedback(result.message(), false);
            refreshTwoFactorStatus();
            return;
        }

        applyTwoFactorPendingPayload(result.payload());
        showFeedback(result.message(), true);
        refreshTwoFactorStatus();
    }

    @FXML
    private void handleConfirmTwoFactor() {
        hideFeedback();
        TwoFactorAuthService.SetupConfirmResult result = twoFactorAuthService.confirmSetupForCurrentUser(twoFactorCodeField.getText());
        if (!result.success()) {
            showTwoFactorAlert("Activation 2FA impossible", result.message(), false);
            showFeedback(result.message(), false);
            refreshTwoFactorStatus();
            return;
        }

        recoveryCodesArea.setText(String.join(System.lineSeparator(), result.recoveryCodes()));
        recoveryCodesBox.setManaged(true);
        recoveryCodesBox.setVisible(true);
        twoFactorCodeField.clear();
        clearTwoFactorPendingUi();
        refreshTwoFactorStatus();
        showTwoFactorAlert("2FA activee", result.message(), true);
        showFeedback(result.message(), true);
    }

    @FXML
    private void handleDisableTwoFactor() {
        hideFeedback();
        OperationResult result = twoFactorAuthService.disableCurrentUser(disableTwoFactorCodeField.getText());
        if (result.isSuccess()) {
            disableTwoFactorCodeField.clear();
            clearTwoFactorPendingUi();
            recoveryCodesArea.clear();
            recoveryCodesBox.setManaged(false);
            recoveryCodesBox.setVisible(false);
        }
        refreshTwoFactorStatus();
        showTwoFactorAlert(result.isSuccess() ? "2FA desactivee" : "Desactivation 2FA impossible", result.getMessage(), result.isSuccess());
        showFeedback(result.getMessage(), result.isSuccess());
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
            applyAvatar(profileAvatarLabel, null, 56);
            sidebarProfileNameLabel.setText("Etudiant Fahamni");
            sidebarProfileEmailLabel.setText("Aucun compte connecte");
            profilePictureStatusLabel.setText("Aucun fichier choisi");
            firstNameField.clear();
            lastNameField.clear();
            emailField.clear();
            phoneField.clear();
            bioArea.clear();
            certificationsArea.clear();
            roleField.clear();
            applyAccountOverview(new UserAccountService.AccountOverview("Inconnu", "Aucun role", "Non disponible", "Non disponible"));
            refreshFaceStatus();
            refreshTwoFactorStatus();
            return;
        }

        applyAvatar(profileAvatarLabel, accountService.getCurrentAvatarPath(), 56);
        sidebarProfileNameLabel.setText(currentUser.getFullName());
        sidebarProfileEmailLabel.setText(currentUser.getEmail());

        String[] nameParts = splitName(currentUser.getFullName());
        firstNameField.setText(nameParts[0]);
        lastNameField.setText(nameParts[1]);
        emailField.setText(currentUser.getEmail());
        roleField.setText(UserSession.getRoleLabel());
        profilePictureStatusLabel.setText(accountService.getCurrentAvatarStatus());

        UserAccountService.ProfileDetails profileDetails = accountService.getCurrentProfileDetails();
        phoneField.setText(profileDetails.phoneNumber());
        bioArea.setText(profileDetails.bio());
        certificationsArea.setText(profileDetails.certifications());

        applyAccountOverview(accountService.getCurrentAccountOverview());
        refreshFaceStatus();
        refreshTwoFactorStatus();
    }

    private void applyAccountOverview(UserAccountService.AccountOverview overview) {
        accountStatusField.setText(overview.accountStatus());
        accountRoleField.setText(overview.roleLabel());
        validationStatusField.setText(overview.validationStatus());
        createdAtField.setText(overview.createdAt());
    }

    private void showSection(Section section) {
        setSectionVisible(personalInfoSection, section == Section.PERSONAL_INFO);
        setSectionVisible(accountSettingsSection, section == Section.ACCOUNT_SETTINGS);
        setSectionVisible(certificationsSection, section == Section.CERTIFICATIONS);
        setSectionVisible(securitySection, section == Section.SECURITY);

        updateNavState(personalInfoNavButton, section == Section.PERSONAL_INFO);
        updateNavState(accountSettingsNavButton, section == Section.ACCOUNT_SETTINGS);
        updateNavState(certificationsNavButton, section == Section.CERTIFICATIONS);
        updateNavState(securityNavButton, section == Section.SECURITY);

        switch (section) {
            case PERSONAL_INFO -> {
                contentTitleLabel.setText("Informations personnelles");
                contentSubtitleLabel.setText("Mets a jour tes informations personnelles et ton profil.");
            }
            case ACCOUNT_SETTINGS -> {
                contentTitleLabel.setText("Parametres du compte");
                contentSubtitleLabel.setText("Ces valeurs sont gerees uniquement depuis le backoffice par les administrateurs.");
            }
            case CERTIFICATIONS -> {
                contentTitleLabel.setText("Certifications et justificatifs");
                contentSubtitleLabel.setText("Enregistre tes certifications et pieces utiles directement sur ton profil.");
            }
            case SECURITY -> {
                contentTitleLabel.setText("Parametres de securite");
                contentSubtitleLabel.setText("Gere Face ID, la 2FA et le mot de passe depuis un seul espace.");
            }
        }
    }

    private void setSectionVisible(VBox section, boolean visible) {
        section.setManaged(visible);
        section.setVisible(visible);
    }

    private void updateNavState(Button button, boolean active) {
        if (active) {
            if (!button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }
        } else {
            button.getStyleClass().remove("active");
        }
    }

    private String buildFullName() {
        String firstName = safeTrim(firstNameField.getText());
        String lastName = safeTrim(lastNameField.getText());
        return (firstName + " " + lastName).trim();
    }

    private String[] splitName(String fullName) {
        String normalizedName = safeTrim(fullName);
        if (normalizedName.isEmpty()) {
            return new String[]{"", ""};
        }

        String[] tokens = normalizedName.split("\\s+", 2);
        if (tokens.length == 1) {
            return new String[]{tokens[0], ""};
        }

        return new String[]{tokens[0], tokens[1]};
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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

    private void showTwoFactorAlert(String header, String message, boolean success) {
        Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setTitle("Authenticator 2FA");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Dialog<PasswordChangePayload> createChangePasswordDialog() {
        Dialog<PasswordChangePayload> dialog = new Dialog<>();
        dialog.setTitle("Changer le mot de passe");
        dialog.setHeaderText("Choisissez un nouveau mot de passe");

        DialogPane dialogPane = dialog.getDialogPane();
        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirmButtonType = new ButtonType("Mettre a jour", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().setAll(cancelButtonType, confirmButtonType);

        Label firstLabel = new Label("Nouveau mot de passe");
        PasswordField firstField = new PasswordField();
        firstField.setPromptText("Nouveau mot de passe");

        Label secondLabel = new Label("Confirmer le mot de passe");
        PasswordField secondField = new PasswordField();
        secondField.setPromptText("Confirmer le mot de passe");

        VBox content = new VBox(10.0, firstLabel, firstField, secondLabel, secondField);
        dialogPane.setContent(content);
        dialog.setResultConverter(buttonType ->
            buttonType == confirmButtonType ? new PasswordChangePayload(firstField.getText(), secondField.getText()) : null
        );

        Window owner = profileAvatarLabel != null && profileAvatarLabel.getScene() != null ? profileAvatarLabel.getScene().getWindow() : null;
        if (owner != null) {
            dialog.initOwner(owner);
        }

        return dialog;
    }

    private void refreshFaceStatus() {
        FaceRecognitionService.FaceStatus faceStatus = faceRecognitionService.getCurrentFaceStatus();
        if (faceEngineStatusLabel != null) {
            faceEngineStatusLabel.setText(faceStatus.engineStatus());
        }
        if (faceEnrollmentStatusLabel != null) {
            faceEnrollmentStatusLabel.setText(faceStatus.faceStatus());
        }
        if (faceEnrolledAtLabel != null) {
            faceEnrolledAtLabel.setText("Enregistre le : " + faceStatus.enrolledAt());
        }
    }

    private void refreshTwoFactorStatus() {
        TwoFactorAuthService.TwoFactorStatus status = twoFactorAuthService.getCurrentStatus();
        if (twoFactorStatusLabel != null) {
            twoFactorStatusLabel.setText(status.enabled() ? "Statut : Active" : "Statut : Desactivee");
        }
        if (twoFactorConfirmedAtLabel != null) {
            twoFactorConfirmedAtLabel.setText("Confirmee le : " + status.confirmedAt());
        }

        if (status.setupPending() && status.pendingPayload() != null) {
            applyTwoFactorPendingPayload(status.pendingPayload());
        } else {
            clearTwoFactorPendingUi();
        }
    }

    private void applyTwoFactorPendingPayload(TwoFactorAuthService.SetupPayload payload) {
        if (payload == null) {
            clearTwoFactorPendingUi();
            return;
        }

        if (twoFactorQrImageView != null) {
            twoFactorQrImageView.setImage(payload.qrCodeImage());
        }
        if (twoFactorSecretLabel != null) {
            twoFactorSecretLabel.setText(payload.secret());
        }
        if (twoFactorSetupBox != null) {
            twoFactorSetupBox.setManaged(true);
            twoFactorSetupBox.setVisible(true);
        }
    }

    private void clearTwoFactorPendingUi() {
        if (twoFactorQrImageView != null) {
            twoFactorQrImageView.setImage(null);
        }
        if (twoFactorSecretLabel != null) {
            twoFactorSecretLabel.setText("Aucun secret genere pour le moment.");
        }
        if (twoFactorSetupBox != null) {
            twoFactorSetupBox.setManaged(false);
            twoFactorSetupBox.setVisible(false);
        }
    }

    private void applyAvatar(Label label, Path avatarPath, double size) {
        if (label == null) {
            return;
        }

        if (avatarPath == null) {
            label.setGraphic(null);
            label.setText(UserSession.getInitials());
            return;
        }

        Image image = new Image(avatarPath.toUri().toString(), size, size, false, true);
        if (image.isError()) {
            label.setGraphic(null);
            label.setText(UserSession.getInitials());
            return;
        }

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        Circle clip = new Circle(size / 2);
        clip.setCenterX(size / 2);
        clip.setCenterY(size / 2);
        imageView.setClip(clip);

        label.setText("");
        label.setGraphic(imageView);
    }

    private record PasswordChangePayload(String newPassword, String confirmPassword) {
    }
}
