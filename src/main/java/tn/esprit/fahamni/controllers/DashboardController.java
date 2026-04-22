package tn.esprit.fahamni.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tn.esprit.fahamni.utils.UserSession;

public class DashboardController {

    @FXML
    private VBox dashboardHomeContent;

    @FXML
    private FlowPane heroSection;

    @FXML
    private VBox floatingStack;

    @FXML
    private FlowPane statStrip;

    @FXML
    private FlowPane mainGrid;

    @FXML
    private FlowPane bottomGrid;

    @FXML
    private Label welcomeTitleLabel;

    @FXML
    private Label welcomeSubtitleLabel;

    @FXML
    private Label accountNameLabel;

    @FXML
    private Label accountEmailLabel;

    @FXML
    private Label accountRoleLabel;

    @FXML
    private Label focusTitleLabel;

    @FXML
    private Label focusCopyLabel;

    @FXML
    private void initialize() {
        welcomeTitleLabel.setText("Learn better with the right tutor, tools, and study flow");
        welcomeSubtitleLabel.setText(
            "Welcome back, " + UserSession.getDisplayName()
                + ". Book tutoring sessions, manage your calendar, and stay focused from one clean workspace.");
        accountNameLabel.setText(UserSession.getDisplayName());
        accountEmailLabel.setText(UserSession.hasCurrentUser() ? UserSession.getCurrentUser().getEmail() : "No email available");
        accountRoleLabel.setText(UserSession.getRoleLabel());
        focusTitleLabel.setText("Priority for today");
        focusCopyLabel.setText(
            "Start with your next session, then keep the week steady with planner blocks and resource check-ins.");
        playIntroAnimation();
    }

    private void playIntroAnimation() {
        animateSection(heroSection, 0);
        animateSection(statStrip, 90);
        animateSection(mainGrid, 180);
        animateSection(bottomGrid, 270);
        playFloatingMotion();
    }

    private void animateSection(Node node, int delayMs) {
        if (node == null) {
            return;
        }

        node.setOpacity(0.0);
        node.setTranslateY(18.0);

        PauseTransition delay = new PauseTransition(Duration.millis(delayMs));
        delay.setOnFinished(event -> {
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(420), node);
            fadeTransition.setFromValue(0.0);
            fadeTransition.setToValue(1.0);

            TranslateTransition slideTransition = new TranslateTransition(Duration.millis(420), node);
            slideTransition.setFromY(18.0);
            slideTransition.setToY(0.0);

            new ParallelTransition(fadeTransition, slideTransition).play();
        });
        delay.play();
    }

    private void playFloatingMotion() {
        if (floatingStack == null) {
            return;
        }

        TranslateTransition floatTransition = new TranslateTransition(Duration.seconds(3.6), floatingStack);
        floatTransition.setFromY(-4.0);
        floatTransition.setToY(6.0);
        floatTransition.setCycleCount(TranslateTransition.INDEFINITE);
        floatTransition.setAutoReverse(true);
        floatTransition.play();
    }
}
