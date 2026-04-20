package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.services.SessionCreationContext;
import tn.esprit.fahamni.test.Main;
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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Popup;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tn.esprit.fahamni.Models.Notification;

public class MainController {

    private final NotificationService notifService = new NotificationService();

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
    @FXML private Button quizButton;
    @FXML private Button blogButton;
    @FXML private Button accountButton;
    @FXML private Label profileAvatarLabel;
    @FXML private Label profileNameLabel;
    @FXML private Label profileRoleLabel;

    private ContextMenu accountMenu;
    private Popup alertsPopup;
    private static final DateTimeFormatter NOTIF_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private void initialize() {
        System.out.println("MainController initialized");
        SessionCreationContext.registerNavigator(this::showReservations);
        refreshCurrentUserSummary();
        initializeAccountMenu();
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

        VBox container = new VBox(0);
        container.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.18),18,0,0,4);" +
            "-fx-border-color: #e3e6ef; -fx-border-radius: 14; -fx-border-width: 1;");
        container.setPrefWidth(340);

        HBox header = new HBox(8);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 16));
        header.setStyle("-fx-background-color: linear-gradient(to right,#6b5dd3,#5068d1);" +
                        "-fx-background-radius: 14 14 0 0;");
        Label titleLabel = new Label("Notifications");
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String countText = unreadCount > 0
                ? unreadCount + " non lu(s)"
                : (notifications.isEmpty() ? "Aucune" : notifications.size() + " au total");
        Label countLabel = new Label(countText);
        countLabel.setStyle("-fx-font-size: 11; -fx-text-fill: rgba(255,255,255,0.85);");
        header.getChildren().addAll(titleLabel, spacer, countLabel);
        container.getChildren().add(header);

        if (notifications.isEmpty()) {
            Label emptyLabel = new Label("Aucune notification pour le moment.");
            emptyLabel.setPadding(new Insets(24, 16, 24, 16));
            emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");
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
                        ? (approved ? "#f0fdf4" : (refused ? "#fff1f2" : "#f3f1ff"))
                        : "#f8fafc";
                item.setStyle("-fx-background-color: " + background + ";");

                HBox row = new HBox(8);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label icon = new Label(approved ? "✓" : (refused ? "✕" : "•"));
                icon.setStyle("-fx-font-size: 15; -fx-text-fill: " +
                        (approved ? "#198754" : (refused ? "#d1435b" : "#5f49bf")) + ";");

                Label message = new Label(notification.getMessage() != null ? notification.getMessage() : "");
                message.setWrapText(true);
                String messageColor = isUnread
                        ? (approved ? "#166534" : (refused ? "#9f1239" : "#4c1d95"))
                        : "#64748b";
                message.setStyle("-fx-font-size: 12; -fx-text-fill: " + messageColor + ";");
                message.setMaxWidth(230);
                HBox.setHgrow(message, Priority.ALWAYS);

                HBox rightBox = new HBox(4);
                rightBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
                if (isUnread) {
                    Label badge = new Label("Nouveau");
                    badge.setStyle("-fx-background-color: #d1435b; -fx-text-fill: white;" +
                            "-fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6;" +
                            "-fx-padding: 1 5;");
                    rightBox.getChildren().add(badge);
                }
                row.getChildren().addAll(icon, message, rightBox);

                Label date = new Label(notification.getCreatedAt() != null ? notification.getCreatedAt().format(NOTIF_FMT) : "");
                date.setStyle("-fx-font-size: 10; -fx-text-fill: #94a3b8;");

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
                Button markReadButton = new Button("Tout marquer comme lu");
                markReadButton.setMaxWidth(Double.MAX_VALUE);
                markReadButton.setStyle(
                    "-fx-background-color: #f8f7ff; -fx-text-fill: #5f49bf;" +
                    "-fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 10;" +
                    "-fx-cursor: hand; -fx-border-color: #e2e8f0;" +
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
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
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
