package tn.esprit.fahamni.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.services.SessionCreationContext;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.FrontOfficeMotion;
import tn.esprit.fahamni.utils.FrontOfficeNavigation;
import tn.esprit.fahamni.utils.FrontOfficeThemePreference;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.UserSession;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.util.Duration;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.UserAccountService;

public class MainController {

    private final NotificationService notifService = new NotificationService();
    private final UserAccountService userAccountService = new UserAccountService();

    @FXML private BorderPane rootPane;
    @FXML private AnchorPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Button alertButton;
    @FXML private Label alertNotifBadge;
    @FXML private Button dashboardButton;
    @FXML private Button reservationsButton;
    @FXML private Button seancesButton;
    @FXML private Button sallesEquipementsButton;
    @FXML private Button plannerButton;
    @FXML private Button messengerButton;
    @FXML private Button quizButton;
    @FXML private Button blogButton;
    @FXML private Button aboutButton;
    @FXML private Button themeToggleButton;
    @FXML private Button accountButton;
    @FXML private Label profileAvatarLabel;
    @FXML private Label profileNameLabel;
    @FXML private Label profileRoleLabel;

    private ContextMenu accountMenu;
    private Popup alertsPopup;
    private static final DateTimeFormatter NOTIF_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private void initialize() {
        if (!UserSession.hasCurrentUser() || !UserSession.hasValidJwtToken()) {
            handleLogout();
            return;
        }
        System.out.println("MainController initialized");
        SessionCreationContext.registerNavigator(this::showReservations);
        FrontOfficeNavigation.registerNavigator(this::openDestination);
        refreshCurrentUserSummary();
        applyThemeMode();
        initializeAccountMenu();
        Platform.runLater(() -> FrontOfficeMotion.installInteractiveMotion(rootPane));
        showDashboard();
        refreshBlogBadge();
    }

    public void refreshBlogBadge() {
        if (alertNotifBadge == null) {
            return;
        }
        try {
            if (!UserSession.hasCurrentUser() || UserSession.getCurrentUserId() <= 0) {
                alertNotifBadge.setVisible(false);
                alertNotifBadge.setManaged(false);
                return;
            }

            int count = notifService.countUnreadForUser(UserSession.getCurrentUserId());
            if (count > 0) {
                alertNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                alertNotifBadge.setVisible(true);
                alertNotifBadge.setManaged(true);
            } else {
                alertNotifBadge.setVisible(false);
                alertNotifBadge.setManaged(false);
            }
        } catch (Exception e) {
            System.err.println("refreshBlogBadge: " + e.getMessage());
        }
    }

    @FXML
    private void showDashboard() {
        loadView("DashboardView.fxml", "Accueil");
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
    }

    @FXML
    private void showAbout() {
        loadView("AboutView.fxml", "A propos de Fahamni");
        setActiveButton(aboutButton);
    }

    @FXML
    private void toggleTheme() {
        FrontOfficeThemePreference.toggle();
        applyThemeMode();
        playThemeSwitchAnimation();
    }

    @FXML
    private void toggleAlertsPopup() {
        if (alertButton == null) {
            return;
        }
        if (alertsPopup != null && alertsPopup.isShowing()) {
            alertsPopup.hide();
            return;
        }

        int userId = UserSession.hasCurrentUser() ? UserSession.getCurrentUserId() : 0;
        List<Notification> notifications = userId > 0
                ? notifService.getAllForUser(userId)
                : new ArrayList<>();
        long unreadCount = notifications.stream().filter(notification -> !notification.isRead()).count();
        boolean lightTheme = FrontOfficeThemePreference.isLightMode();

        VBox container = new VBox(0);
        container.setStyle(
            lightTheme
                ? "-fx-background-color: rgba(253,255,255,0.98); -fx-background-radius: 18;" +
                  "-fx-effect: dropshadow(gaussian,rgba(32,56,84,0.16),28,0.18,0,10);" +
                  "-fx-border-color: rgba(84,122,165,0.14); -fx-border-radius: 18; -fx-border-width: 1;"
                : "-fx-background-color: rgba(8,14,23,0.98); -fx-background-radius: 18;" +
                  "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.42),30,0.22,0,10);" +
                  "-fx-border-color: rgba(142,192,255,0.15); -fx-border-radius: 18; -fx-border-width: 1;");
        container.setPrefWidth(340);

        HBox header = new HBox(8);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 16));
        header.setStyle(lightTheme
                ? "-fx-background-color: linear-gradient(to right,#edf5ff,#f8fbff);" +
                  "-fx-background-radius: 18 18 0 0;"
                : "-fx-background-color: linear-gradient(to right,#10304a,#0c1f33);" +
                  "-fx-background-radius: 18 18 0 0;");
        Label titleLabel = new Label("Notifications");
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: "
                + (lightTheme ? "#15263a;" : "#f3f8ff;"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String countText = unreadCount > 0
                ? unreadCount + " unread"
                : (notifications.isEmpty() ? "Empty" : notifications.size() + " total");
        Label countLabel = new Label(countText);
        countLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + (lightTheme ? "#6d8198;" : "#92a7bf;"));
        header.getChildren().addAll(titleLabel, spacer, countLabel);
        container.getChildren().add(header);

        if (notifications.isEmpty()) {
            Label emptyLabel = new Label("No notifications for now.");
            emptyLabel.setPadding(new Insets(24, 16, 24, 16));
            emptyLabel.setStyle("-fx-text-fill: " + (lightTheme ? "#7288a0;" : "#8fa6bf;") + "-fx-font-size: 12;");
            container.getChildren().add(emptyLabel);
        } else {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(Math.min(notifications.size() * 76.0, 300));
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

            VBox list = new VBox(0);
            for (int i = 0; i < notifications.size(); i++) {
                Notification notification = notifications.get(i);
                boolean approved = notification.getMessage() != null
                        && (notification.getMessage().contains("approuve") || notification.getMessage().contains("publie"));
                boolean refused = notification.getMessage() != null && notification.getMessage().contains("refuse");
                boolean isUnread = !notification.isRead();

                VBox item = new VBox(4);
                item.setPadding(new Insets(10, 16, 10, 16));
                String background = isUnread
                        ? (approved
                            ? (lightTheme ? "rgba(90,205,151,0.14)" : "rgba(50,120,94,0.20)")
                            : (refused
                                ? (lightTheme ? "rgba(255,166,184,0.18)" : "rgba(140,54,73,0.20)")
                                : (lightTheme ? "rgba(99,178,255,0.14)" : "rgba(41,74,109,0.26)")))
                        : (lightTheme ? "rgba(15,34,52,0.03)" : "rgba(255,255,255,0.03)");
                item.setStyle("-fx-background-color: " + background + ";");

                HBox row = new HBox(8);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label icon = new Label(approved ? "✓" : (refused ? "✕" : "•"));
                icon.setStyle("-fx-font-size: 15; -fx-text-fill: " +
                        (approved ? "#198754" : (refused ? "#d1435b" : "#5f49bf")) + ";");

                Label message = new Label(notification.getMessage() != null ? notification.getMessage() : "");
                message.setWrapText(true);
                String messageColor = isUnread
                        ? (approved ? "#c5ffe0" : (refused ? "#ffd4db" : "#e8f5ff"))
                        : "#92a7bf";
                message.setStyle("-fx-font-size: 12; -fx-text-fill: " + messageColor + ";");
                message.setMaxWidth(230);
                HBox.setHgrow(message, Priority.ALWAYS);

                HBox rightBox = new HBox(4);
                rightBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
                if (isUnread) {
                    Label badge = new Label("New");
                    badge.setStyle("-fx-background-color: rgba(79,216,255,0.18); -fx-text-fill: #c6f7ff;" +
                            "-fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6;" +
                            "-fx-padding: 1 5;");
                    rightBox.getChildren().add(badge);
                }
                row.getChildren().addAll(icon, message, rightBox);

                Label date = new Label(notification.getCreatedAt() != null ? notification.getCreatedAt().format(NOTIF_FMT) : "");
                date.setStyle("-fx-font-size: 10; -fx-text-fill: #7f95ad;");

                item.getChildren().addAll(row, date);
                list.getChildren().add(item);

                if (i < notifications.size() - 1) {
                    Separator separator = new Separator();
                    separator.setStyle("-fx-opacity: 0.3;");
                    list.getChildren().add(separator);
                }
            }

            scroll.setContent(list);
            container.getChildren().add(scroll);

            if (unreadCount > 0 && userId > 0) {
                Button markReadButton = new Button("Mark all as read");
                markReadButton.setMaxWidth(Double.MAX_VALUE);
                markReadButton.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.04); -fx-text-fill: #d6e7f8;" +
                    "-fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 10;" +
                    "-fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-width: 1 0 0 0;");
                markReadButton.setOnAction(event -> {
                    notifService.markAllReadForUser(userId);
                    if (alertsPopup != null) {
                        alertsPopup.hide();
                    }
                    refreshBlogBadge();
                });
                container.getChildren().add(markReadButton);
            }
        }

        alertsPopup = new Popup();
        alertsPopup.getContent().add(container);
        alertsPopup.setAutoHide(true);

        double x = alertButton.localToScreen(alertButton.getBoundsInLocal()).getMaxX() - 340;
        double y = alertButton.localToScreen(alertButton.getBoundsInLocal()).getMaxY() + 8;
        alertsPopup.show(alertButton.getScene().getWindow(), x, y);

        if (unreadCount > 0 && userId > 0) {
            notifService.markAllReadForUser(userId);
            refreshBlogBadge();
        }
    }

    @FXML
    private void toggleAccountMenu() {
        if (accountMenu == null || accountButton == null) {
            return;
        }
        if (accountMenu.isShowing()) {
            accountMenu.hide();
        } else {
            accountMenu.show(accountButton, Side.BOTTOM, 0.0, 8.0);
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
        Platform.runLater(() -> FrontOfficeMotion.installInteractiveMotion(rootPane));
        playViewTransition(view);
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    private void openDestination(FrontOfficeNavigation.Destination destination) {
        if (destination == null) {
            return;
        }

        switch (destination) {
            case DASHBOARD -> showDashboard();
            case RESERVATIONS -> showReservations();
            case CALENDAR -> showSeances();
            case INFRASTRUCTURE -> showSallesEquipements();
            case PLANNER -> showPlanner();
            case QUIZ -> showQuiz();
            case BLOG -> showBlog();
            case ABOUT -> showAbout();
            case PROFILE -> showProfile();
            case SETTINGS -> showSettings();
        }
    }

    private void applyThemeMode() {
        FrontOfficeThemePreference.apply(rootPane);
        if (themeToggleButton != null) {
            themeToggleButton.setText(FrontOfficeThemePreference.isLightMode() ? "Dark Mode" : "Light Mode");
        }
        refreshAccountMenuTheme();
        if (alertsPopup != null && alertsPopup.isShowing()) {
            alertsPopup.hide();
        }
    }

    private void refreshAccountMenuTheme() {
        if (accountMenu == null) {
            return;
        }

        accountMenu.getStyleClass().remove("light-context-menu");
        if (FrontOfficeThemePreference.isLightMode()) {
            accountMenu.getStyleClass().add("light-context-menu");
        }
    }

    private void playThemeSwitchAnimation() {
        if (rootPane == null) {
            return;
        }

        rootPane.setOpacity(0.94);
        rootPane.setScaleX(0.994);
        rootPane.setScaleY(0.994);

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(220), rootPane);
        fadeTransition.setFromValue(0.94);
        fadeTransition.setToValue(1.0);

        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(220), rootPane);
        scaleTransition.setFromX(0.994);
        scaleTransition.setFromY(0.994);
        scaleTransition.setToX(1.0);
        scaleTransition.setToY(1.0);

        new ParallelTransition(fadeTransition, scaleTransition).play();
    }

    private void playViewTransition(Node view) {
        if (view == null) {
            return;
        }

        view.setOpacity(0.0);
        view.setTranslateY(18.0);
        view.setScaleX(0.992);
        view.setScaleY(0.992);

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(320), view);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);

        TranslateTransition slideTransition = new TranslateTransition(Duration.millis(320), view);
        slideTransition.setFromY(18.0);
        slideTransition.setToY(0.0);

        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(320), view);
        scaleTransition.setFromX(0.992);
        scaleTransition.setFromY(0.992);
        scaleTransition.setToX(1.0);
        scaleTransition.setToY(1.0);

        new ParallelTransition(fadeTransition, slideTransition, scaleTransition).play();
    }

    private void refreshCurrentUserSummary() {
        try {
            applyAvatar(profileAvatarLabel, userAccountService.getCurrentAvatarPath(), 34);
            profileNameLabel.setText(UserSession.getDisplayName());
            profileRoleLabel.setText(UserSession.getRoleLabel());
        } catch (Exception e) {
            System.err.println("refreshCurrentUserSummary: " + e.getMessage());
        }
    }

    private void initializeAccountMenu() {
        if (accountButton == null) {
            return;
        }

        MenuItem profileItem = new MenuItem("Mon profil");
        profileItem.getStyleClass().add("front-account-menu-item");
        profileItem.setOnAction(event -> showProfile());

        MenuItem settingsItem = new MenuItem("Parametres");
        settingsItem.getStyleClass().add("front-account-menu-item");
        settingsItem.setOnAction(event -> showSettings());

        MenuItem logoutItem = new MenuItem("Deconnexion");
        logoutItem.getStyleClass().add("account-menu-danger");
        logoutItem.setOnAction(event -> handleLogout());

        accountMenu = new ContextMenu(profileItem, settingsItem, new SeparatorMenuItem(), logoutItem);
        accountMenu.getStyleClass().add("front-navbar-context-menu");
        accountMenu.setAutoHide(true);
        accountMenu.setAutoFix(true);
        refreshAccountMenuTheme();
    }

    private void hideAccountMenuInstant() {
        if (accountMenu != null) {
            accountMenu.hide();
        }
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
        removeActiveClass(aboutButton);

        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    private void removeActiveClass(Button button) {
        if (button != null) {
            button.getStyleClass().remove("active");
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
}
