package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

public class MainController {

    private final NotificationService notifService = new NotificationService();

    @FXML private AnchorPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Button dashboardButton;
    @FXML private Button seancesButton;
    @FXML private Button reservationsButton;
    @FXML private Button plannerButton;
    @FXML private Button messengerButton;
    @FXML private Button quizButton;
    @FXML private Button blogButton;
    @FXML private Label  blogNotifBadge;

    @FXML
    private void initialize() {
        showDashboard();
        refreshBlogBadge();
    }

    public void refreshBlogBadge() {
        try {
            tn.esprit.fahamni.Models.User u = SessionManager.getCurrentUser();
            if (u == null || u.getId() <= 0) return;
            int count = notifService.countUnreadForUser(u.getId());
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

    @FXML private void showDashboard() {
        loadView("DashboardView.fxml", "Dashboard");
        setActiveButton(dashboardButton);
    }

    @FXML private void showSeances() {
        loadView("SeanceListView.fxml", "Séances");
        setActiveButton(seancesButton);
    }

    @FXML private void showReservations() {
        loadView("ReservationView.fxml", "Réservations");
        setActiveButton(reservationsButton);
    }

    @FXML private void showPlanner() {
        loadView("PlannerView.fxml", "Planner de Révision");
        setActiveButton(plannerButton);
    }

    @FXML private void showMessenger() {
        loadView("MessengerView.fxml", "Messagerie");
        setActiveButton(messengerButton);
    }

    @FXML private void showQuiz() {
        loadView("QuizView.fxml", "Quiz");
        setActiveButton(quizButton);
    }

    @FXML private void showBlog() {
        loadView("BlogView.fxml", "Blog & Ressources");
        setActiveButton(blogButton);
        blogNotifBadge.setVisible(false);
        blogNotifBadge.setManaged(false);
    }

    @FXML private void handleLogout() {
        try { Main.showLogin(); } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadView(String fxmlFile, String title) {
        try {
            Node view = SceneManager.loadView(Main.class, SceneManager.frontofficeView(fxmlFile));
            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);
            contentPane.getChildren().add(view);
            pageTitle.setText(title);
        } catch (Exception e) {
            e.printStackTrace();
            Label placeholder = new Label("View not implemented yet: " + fxmlFile);
            placeholder.getStyleClass().add("content-placeholder");
            contentPane.getChildren().clear();
            contentPane.getChildren().add(placeholder);
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
        if (!activeButton.getStyleClass().contains("active"))
            activeButton.getStyleClass().add("active");
    }
}
