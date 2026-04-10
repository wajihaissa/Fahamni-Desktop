package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.services.AdminArticlesService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

public class BackofficeMainController {

    private final AdminArticlesService articlesService = new AdminArticlesService();

    @FXML private AnchorPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Button dashboardButton;
    @FXML private Button usersButton;
    @FXML private Button sessionsButton;
    @FXML private Button reservationsButton;
    @FXML private Button contentButton;
    @FXML private Button articlesButton;
    @FXML private Label  articlesBadge;

    @FXML
    private void initialize() {
        showDashboard();
        refreshArticlesBadge();
    }

    /** Met à jour le badge rouge avec le nombre d'articles en attente */
    public void refreshArticlesBadge() {
        int pending = articlesService.countByStatus("pending");
        if (pending > 0) {
            articlesBadge.setText(String.valueOf(pending));
            articlesBadge.setVisible(true);
            articlesBadge.setManaged(true);
        } else {
            articlesBadge.setVisible(false);
            articlesBadge.setManaged(false);
        }
    }

    @FXML
    private void showDashboard() {
        loadView("BackofficeDashboardView.fxml", "Dashboard Admin");
        setActiveButton(dashboardButton);
    }

    @FXML
    private void showUsers() {
        loadView("BackofficeUsersView.fxml", "Gestion des utilisateurs");
        setActiveButton(usersButton);
    }

    @FXML
    private void showSessions() {
        loadView("BackofficeSessionsView.fxml", "Gestion des seances");
        setActiveButton(sessionsButton);
    }

    @FXML
    private void showReservations() {
        loadView("BackofficeReservationsView.fxml", "Gestion des reservations");
        setActiveButton(reservationsButton);
    }

    @FXML
    private void showContent() {
        loadView("BackofficeContentView.fxml", "Gestion du contenu");
        setActiveButton(contentButton);
    }

    @FXML
    private void showArticles() {
        loadView("BackofficeArticlesView.fxml", "Gestion des Articles");
        setActiveButton(articlesButton);
        refreshArticlesBadge();
    }

    @FXML
    private void handleLogout() {
        try {
            Main.showLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxmlFile, String title) {
        try {
            Node view = SceneManager.loadView(Main.class, SceneManager.backofficeView(fxmlFile));
            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);
            contentPane.getChildren().add(view);
            pageTitle.setText(title);
        } catch (Exception e) {
            e.printStackTrace();
            Label placeholder = new Label("Vue indisponible : " + fxmlFile);
            placeholder.getStyleClass().add("backoffice-empty-state");
            contentPane.getChildren().setAll(placeholder);
        }
    }

    private void setActiveButton(Button activeButton) {
        dashboardButton.getStyleClass().remove("active");
        usersButton.getStyleClass().remove("active");
        sessionsButton.getStyleClass().remove("active");
        reservationsButton.getStyleClass().remove("active");
        contentButton.getStyleClass().remove("active");
        articlesButton.getStyleClass().remove("active");

        if (!activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }
}

