package tn.esprit.fahamni.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.ApplicationState;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.ViewNavigator;

public class MainController {

    private static final double GLOBAL_AI_PANEL_WIDTH = 400.0;

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
    private Button callLabButton;

    @FXML
    private Button aiButton;

    @FXML
    private VBox globalAiPanel;

    @FXML
    private AnchorPane globalAiContent;

    @FXML
    private Button globalAiFab;

    @FXML
    private Region globalAiScrim;

    private GlobalChatbotController globalChatbotController;
    private boolean globalAiLoaded;
    private boolean globalAiOpen;

    @FXML
    private void initialize() {
        ViewNavigator.getInstance().initialize(contentPane, pageTitle);
        ApplicationState.getInstance().setCurrentView("Dashboard");
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
    private void showCours() {
        loadView("FrontMatiereView.fxml", "Cours");
        setActiveButton(coursButton);
    }

    @FXML
    private void showCallLab() {
        loadView("VideoChatView.fxml", "Call Lab");
        setActiveButton(callLabButton);
    }

    @FXML
    private void showFahamniAi() {
        toggleGlobalAi();
    }

    @FXML
    private void toggleGlobalAi() {
        runOnFxThread(() -> {
            try {
                ensureGlobalAiLoaded();
                if (globalAiOpen) {
                    closeGlobalAi();
                } else {
                    openGlobalAi();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Label placeholder = new Label("Failed to load Fahamni AI: " + e.getMessage());
                placeholder.getStyleClass().add("content-placeholder");
                contentPane.getChildren().clear();
                contentPane.getChildren().add(placeholder);
            }
        });
    }

    @FXML
    private void handleLogout() {
        try {
            if (globalChatbotController != null) {
                globalChatbotController.shutdown();
            }
            Main.showLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxmlFile, String title) {
        try {
            closeGlobalAiIfOpen();
            ApplicationState.getInstance().setCurrentView(title);
            if ("FrontMatiereView.fxml".equals(fxmlFile) || !title.startsWith("Cours")) {
                ApplicationState.getInstance().clearCurrentMatiere();
            }
            ViewNavigator.getInstance().loadView(SceneManager.frontofficeView(fxmlFile), title);
        } catch (Exception e) {
            e.printStackTrace();
            Label placeholder = new Label("View not implemented yet: " + fxmlFile);
            placeholder.getStyleClass().add("content-placeholder");
            contentPane.getChildren().clear();
            contentPane.getChildren().add(placeholder);
        }
    }

    private void ensureGlobalAiLoaded() throws Exception {
        if (globalAiLoaded) {
            globalChatbotController.refreshContextBadge();
            return;
        }

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/tn/esprit/fahamni/views/GlobalChatbotView.fxml"));
        Node chatbotView = loader.load();
        globalChatbotController = loader.getController();

        globalAiContent.getChildren().setAll(chatbotView);
        AnchorPane.setTopAnchor(chatbotView, 0.0);
        AnchorPane.setBottomAnchor(chatbotView, 0.0);
        AnchorPane.setLeftAnchor(chatbotView, 0.0);
        AnchorPane.setRightAnchor(chatbotView, 0.0);

        globalAiLoaded = true;
    }

    private void openGlobalAi() {
        globalAiOpen = true;
        if (globalChatbotController != null) {
            globalChatbotController.refreshContextBadge();
        }

        globalAiPanel.setManaged(true);
        globalAiPanel.setVisible(true);
        globalAiPanel.setTranslateX(GLOBAL_AI_PANEL_WIDTH);

        globalAiScrim.setManaged(true);
        globalAiScrim.setVisible(true);
        globalAiScrim.setMouseTransparent(false);
        globalAiScrim.setOnMouseClicked(event -> closeGlobalAi());
        globalAiScrim.setOpacity(0);

        globalAiFab.setText("Close Guide");
        setAiButtonActive(true);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(240), globalAiPanel);
        slideIn.setFromX(GLOBAL_AI_PANEL_WIDTH);
        slideIn.setToX(0);
        slideIn.play();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), globalAiScrim);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void closeGlobalAi() {
        globalAiOpen = false;
        globalAiFab.setText("Ask Fahamni");
        setAiButtonActive(false);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(220), globalAiPanel);
        slideOut.setFromX(globalAiPanel.getTranslateX());
        slideOut.setToX(GLOBAL_AI_PANEL_WIDTH);
        slideOut.setOnFinished(event -> {
            globalAiPanel.setVisible(false);
            globalAiPanel.setManaged(false);
        });
        slideOut.play();

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), globalAiScrim);
        fadeOut.setFromValue(globalAiScrim.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            globalAiScrim.setVisible(false);
            globalAiScrim.setManaged(false);
            globalAiScrim.setMouseTransparent(true);
        });
        fadeOut.play();
    }

    private void closeGlobalAiIfOpen() {
        if (globalAiOpen) {
            closeGlobalAi();
        }
    }

    private void setAiButtonActive(boolean active) {
        aiButton.getStyleClass().remove("active");
        if (active) {
            aiButton.getStyleClass().add("active");
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
        coursButton.getStyleClass().remove("active");
        callLabButton.getStyleClass().remove("active");
        aiButton.getStyleClass().remove("active");

        if (!activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
