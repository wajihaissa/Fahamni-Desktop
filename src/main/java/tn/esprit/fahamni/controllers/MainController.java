package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.services.SessionCreationContext;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;

public class MainController {

    private static final double ACCOUNT_MENU_HIDDEN_OFFSET = -8.0;
    private static final double ACCOUNT_MENU_VISIBLE_OFFSET = 10.0;

    private final NotificationService notifService = new NotificationService();

    @FXML private BorderPane rootPane;
    @FXML private AnchorPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Button dashboardButton;
    @FXML private Button reservationsButton;
    @FXML private Button seancesButton;
    @FXML private Button sallesEquipementsButton;
    @FXML private Button plannerButton;
    @FXML private Button messengerButton;
    @FXML private Button quizButton;
    @FXML private Button blogButton;
    @FXML private Label blogNotifBadge;
    @FXML private StackPane accountMenuWrapper;
    @FXML private VBox accountMenuPane;
    @FXML private Label profileAvatarLabel;
    @FXML private Label profileNameLabel;
    @FXML private Label profileRoleLabel;

    private FadeTransition accountMenuFadeTransition;
    private TranslateTransition accountMenuTranslateTransition;

    @FXML
    private void initialize() {
        System.out.println("MainController initialized");
        SessionCreationContext.registerNavigator(this::showReservations);
        refreshCurrentUserSummary();
        hideAccountMenuInstant();
        if (rootPane != null) {
            rootPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (accountMenuPane == null || !accountMenuPane.isVisible()) return;
                if (event.getTarget() instanceof Node node && !isInsideAccountMenu(node)) {
                    hideAccountMenuAnimated();
                }
            });
        }
        showDashboard();
        refreshBlogBadge();
    }

    public void refreshBlogBadge() {
        if (blogNotifBadge == null) {
            return;
        }
        try {
            if (!UserSession.hasCurrentUser() || UserSession.getCurrentUserId() <= 0) {
                blogNotifBadge.setVisible(false);
                blogNotifBadge.setManaged(false);
                return;
            }

            int count = notifService.countUnreadForUser(UserSession.getCurrentUserId());
            if (count > 0) {
                blogNotifBadge.setText(String.valueOf(count));
                blogNotifBadge.setVisible(true);
                blogNotifBadge.setManaged(true);
            } else {
                blogNotifBadge.setVisible(false);
                blogNotifBadge.setManaged(false);
            }
        } catch (Exception e) {
            System.err.println("refreshBlogBadge: " + e.getMessage());
        }
    }

    @FXML
    private void showDashboard() {
        loadView("DashboardView.fxml", "Dashboard");
        setActiveButton(dashboardButton);
    }

    @FXML
    private void showReservations() {
        loadView("ReservationView.fxml", "Trouver un tuteur");
        setActiveButton(reservationsButton);
    }

    @FXML
    private void showSeances() {
        loadView("SeanceListView.fxml", "Calendrier");
        setActiveButton(seancesButton);
    }

    @FXML
    private void showSallesEquipements() {
        loadView("SallesEquipementsView.fxml", "Salles & Materiel");
        setActiveButton(sallesEquipementsButton);
    }

    @FXML
    private void showPlanner() {
        loadView("PlannerView.fxml", "Planner de revision");
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
        if (blogNotifBadge != null) {
            blogNotifBadge.setVisible(false);
            blogNotifBadge.setManaged(false);
        }
    }

    @FXML
    private void toggleAccountMenu() {
        if (accountMenuPane != null && accountMenuPane.isVisible()) {
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
        SessionCreationContext.clearPendingSelection();
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
        try {
            profileAvatarLabel.setText(UserSession.getInitials());
            profileNameLabel.setText(UserSession.getDisplayName());
            profileRoleLabel.setText(UserSession.getRoleLabel());
        } catch (Exception e) {
            System.err.println("refreshCurrentUserSummary: " + e.getMessage());
        }
    }

    private void showAccountMenuAnimated() {
        if (accountMenuPane == null) {
            return;
        }
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
        if (accountMenuPane == null) {
            return;
        }
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
        if (accountMenuPane == null) return;
        accountMenuPane.setVisible(false);
        accountMenuPane.setManaged(false);
        accountMenuPane.setOpacity(0.0);
        accountMenuPane.setTranslateY(ACCOUNT_MENU_HIDDEN_OFFSET);
    }

    private boolean isInsideAccountMenu(Node node) {
        Node current = node;
        while (current != null) {
            if (current == accountMenuWrapper) return true;
            current = current.getParent();
        }
        return false;
    }

    private void stopAccountMenuAnimations() {
        if (accountMenuFadeTransition != null) accountMenuFadeTransition.stop();
        if (accountMenuTranslateTransition != null) accountMenuTranslateTransition.stop();
    }

    private void setActiveButton(Button activeButton) {
        removeActiveClass(dashboardButton);
        removeActiveClass(reservationsButton);
        removeActiveClass(seancesButton);
        removeActiveClass(sallesEquipementsButton);
        removeActiveClass(plannerButton);
        removeActiveClass(messengerButton);
        removeActiveClass(quizButton);
        removeActiveClass(blogButton);

        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    private void removeActiveClass(Button button) {
        if (button != null) {
            button.getStyleClass().remove("active");
        }
    }
}
