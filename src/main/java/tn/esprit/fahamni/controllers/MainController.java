package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.ViewNavigator;

public class MainController {

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
    private Button coursButton;

    @FXML
    private void initialize() {
        System.out.println("MainController initialized");
        // Initialize the ViewNavigator with references to contentPane and pageTitle
        ViewNavigator.getInstance().initialize(contentPane, pageTitle);
        // Load dashboard by default
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        System.out.println("Loading dashboard");
        loadView("DashboardView.fxml", "Dashboard");
        setActiveButton(dashboardButton);
    }

    @FXML
    private void showSeances() {
        loadView("SeanceListView.fxml", "SÃ©ances");
        setActiveButton(seancesButton);
    }

    @FXML
    private void showReservations() {
        loadView("ReservationView.fxml", "RÃ©servations");
        setActiveButton(reservationsButton);
    }

    @FXML
    private void showPlanner() {
        loadView("PlannerView.fxml", "Planner de RÃ©vision");
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
    private void showCours() {
        loadView("FrontMatiereView.fxml", "Cours");
        setActiveButton(coursButton);
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
            // For now, show a placeholder
            Label placeholder = new Label("View not implemented yet: " + fxmlFile);
            placeholder.getStyleClass().add("content-placeholder");
            contentPane.getChildren().clear();
            contentPane.getChildren().add(placeholder);
        }
    }

    private void setActiveButton(Button activeButton) {
        // Reset all buttons
        dashboardButton.getStyleClass().remove("active");
        seancesButton.getStyleClass().remove("active");
        reservationsButton.getStyleClass().remove("active");
        plannerButton.getStyleClass().remove("active");
        messengerButton.getStyleClass().remove("active");
        quizButton.getStyleClass().remove("active");
        blogButton.getStyleClass().remove("active");
        coursButton.getStyleClass().remove("active");

        // Set active
        if (!activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }
}


