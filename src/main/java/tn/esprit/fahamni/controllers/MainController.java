package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.UserSession;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.io.IOException;

public class MainController {

    private static final double ACCOUNT_MENU_HIDDEN_OFFSET = -8.0;
    private static final double ACCOUNT_MENU_VISIBLE_OFFSET = 10.0;

    @FXML
    private BorderPane rootPane;

    @FXML
    private AnchorPane contentPane;

    @FXML
    private Label pageTitle;

    @FXML
    private Button dashboardButton;

    @FXML
    private Button seancesButton;

    @FXML
    private Button reservationsButton;

    @FXML
    private Button plannerButton;

    @FXML
    private Button messengerButton;

    @FXML
    private Button quizButton;

    @FXML
    private Button blogButton;

    @FXML
    private VBox accountMenuPane;

    @FXML
    private StackPane accountMenuWrapper;

    private FadeTransition accountMenuFadeTransition;
    private TranslateTransition accountMenuTranslateTransition;

    @FXML
    private Label profileAvatarLabel;

    @FXML
    private Label profileNameLabel;

    @FXML
    private Label profileRoleLabel;

    @FXML
    private void initialize() {
        if (!UserSession.hasCurrentUser() || !UserSession.hasValidJwtToken()) {
            handleLogout();
            return;
        }

        refreshCurrentUserSummary();
        hideAccountMenuInstant();
        rootPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!accountMenuPane.isVisible()) {
                return;
            }

            if (event.getTarget() instanceof Node node && !isInsideAccountMenu(node)) {
                hideAccountMenuAnimated();
            }
        });
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        loadView("DashboardView.fxml", "Dashboard");
        setActiveButton(dashboardButton);
    }

    @FXML
    private void showSeances() {
        loadView("SeanceListView.fxml", "Seances");
        setActiveButton(seancesButton);
    }

    @FXML
    private void showReservations() {
        loadView("ReservationView.fxml", "Reservations");
        setActiveButton(reservationsButton);
    }

    @FXML
    private void showPlanner() {
        loadView("PlannerView.fxml", "Planner de Revision");
        setActiveButton(plannerButton);
    }

    @FXML
    private void showMessenger() {
        loadView("MessengerView.fxml", "Messagerie");
        setActiveButton(messengerButton);
    }

    @FXML
    private void showQuiz() {
        loadView("QuizView.fxml", "Quiz");
        setActiveButton(quizButton);
    }

    @FXML
    private void showBlog() {
        loadView("BlogView.fxml", "Blog & Ressources");
        setActiveButton(blogButton);
    }

    @FXML
    private void toggleAccountMenu() {
        if (accountMenuPane.isVisible()) {
            hideAccountMenuAnimated();
        } else {
            showAccountMenuAnimated();
        }
    }

    @FXML
    private void showProfile() {
        hideAccountMenuInstant();
        loadProfileSettingsView("Mon profil", false);
    }

    @FXML
    private void showSettings() {
        hideAccountMenuInstant();
        loadProfileSettingsView("Parametres du compte", true);
    }

    @FXML
    private void handleLogout() {
        hideAccountMenuInstant();
        UserSession.clear();
        try {
            Main.showLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxmlFile, String title) {
        hideAccountMenuInstant();
        try {
            Node view = SceneManager.loadView(Main.class, SceneManager.frontofficeView(fxmlFile));
            displayView(view, title);
        } catch (Exception e) {
            e.printStackTrace();
            Label placeholder = new Label("View not implemented yet: " + fxmlFile);
            placeholder.getStyleClass().add("content-placeholder");
            displayView(placeholder, title);
        }
    }

    private void loadProfileSettingsView(String title, boolean settingsMode) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(SceneManager.frontofficeView("ProfileSettingsView.fxml")));
            Node view = loader.load();
            ProfileSettingsController controller = loader.getController();
            controller.configure(settingsMode);
            controller.setOnProfileUpdated(updatedUser -> refreshCurrentUserSummary());
            controller.setOnAccountDeleted(this::handleLogout);
            displayView(view, title);
        } catch (IOException e) {
            e.printStackTrace();
            Label placeholder = new Label("Impossible de charger le panneau de compte.");
            placeholder.getStyleClass().add("content-placeholder");
            displayView(placeholder, title);
        }
    }

    private void displayView(Node view, String title) {
        contentPane.getChildren().clear();
        AnchorPane.setTopAnchor(view, 0.0);
        AnchorPane.setBottomAnchor(view, 0.0);
        AnchorPane.setLeftAnchor(view, 0.0);
        AnchorPane.setRightAnchor(view, 0.0);
        contentPane.getChildren().add(view);
        pageTitle.setText(title);
    }

    private void refreshCurrentUserSummary() {
        profileAvatarLabel.setText(UserSession.getInitials());
        profileNameLabel.setText(UserSession.getDisplayName());
        profileRoleLabel.setText(UserSession.getRoleLabel());
    }

    private void showAccountMenuAnimated() {
        stopAccountMenuAnimations();
        accountMenuPane.setManaged(true);
        accountMenuPane.setVisible(true);
        accountMenuPane.setOpacity(0.0);
        accountMenuPane.setTranslateY(ACCOUNT_MENU_HIDDEN_OFFSET);

        accountMenuFadeTransition = new FadeTransition(Duration.millis(160), accountMenuPane);
        accountMenuFadeTransition.setFromValue(0.0);
        accountMenuFadeTransition.setToValue(1.0);

        accountMenuTranslateTransition = new TranslateTransition(Duration.millis(160), accountMenuPane);
        accountMenuTranslateTransition.setFromY(ACCOUNT_MENU_HIDDEN_OFFSET);
        accountMenuTranslateTransition.setToY(ACCOUNT_MENU_VISIBLE_OFFSET);

        accountMenuFadeTransition.play();
        accountMenuTranslateTransition.play();
    }

    private void hideAccountMenuAnimated() {
        stopAccountMenuAnimations();

        accountMenuFadeTransition = new FadeTransition(Duration.millis(130), accountMenuPane);
        accountMenuFadeTransition.setFromValue(accountMenuPane.getOpacity());
        accountMenuFadeTransition.setToValue(0.0);

        accountMenuTranslateTransition = new TranslateTransition(Duration.millis(130), accountMenuPane);
        accountMenuTranslateTransition.setFromY(accountMenuPane.getTranslateY());
        accountMenuTranslateTransition.setToY(ACCOUNT_MENU_HIDDEN_OFFSET);

        accountMenuFadeTransition.setOnFinished(event -> hideAccountMenuInstant());

        accountMenuFadeTransition.play();
        accountMenuTranslateTransition.play();
    }

    private void hideAccountMenuInstant() {
        stopAccountMenuAnimations();
        accountMenuPane.setVisible(false);
        accountMenuPane.setManaged(false);
        accountMenuPane.setOpacity(0.0);
        accountMenuPane.setTranslateY(ACCOUNT_MENU_HIDDEN_OFFSET);
    }

    private boolean isInsideAccountMenu(Node node) {
        Node current = node;
        while (current != null) {
            if (current == accountMenuWrapper) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void stopAccountMenuAnimations() {
        if (accountMenuFadeTransition != null) {
            accountMenuFadeTransition.stop();
        }
        if (accountMenuTranslateTransition != null) {
            accountMenuTranslateTransition.stop();
        }
    }

    private void setActiveButton(Button activeButton) {
        dashboardButton.getStyleClass().remove("active");
        seancesButton.getStyleClass().remove("active");
        reservationsButton.getStyleClass().remove("active");
        plannerButton.getStyleClass().remove("active");
        messengerButton.getStyleClass().remove("active");
        quizButton.getStyleClass().remove("active");
        blogButton.getStyleClass().remove("active");

        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }
}
