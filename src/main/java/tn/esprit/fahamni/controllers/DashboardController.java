package tn.esprit.fahamni.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tn.esprit.fahamni.services.SessionCreationContext;
import tn.esprit.fahamni.utils.FrontOfficeMotion;
import tn.esprit.fahamni.utils.FrontOfficeNavigation;
import tn.esprit.fahamni.utils.UserSession;

public class DashboardController {

    @FXML
    private VBox dashboardHomeContent;

    @FXML
    private FlowPane heroSection;

    @FXML
    private VBox floatingStack;

    @FXML
    private StackPane heroVisualPane;

    @FXML
    private Region heroGlowOrbOne;

    @FXML
    private Region heroGlowOrbTwo;

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
    private ImageView heroStudyImageView;

    @FXML
    private ImageView tutorPortraitImageView;

    @FXML
    private ImageView campusRoomImageView;

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
        Platform.runLater(() -> {
            FrontOfficeMotion.installInteractiveMotion(dashboardHomeContent);
            FrontOfficeMotion.applyRoundedClip(heroStudyImageView, 38);
            FrontOfficeMotion.applyRoundedClip(tutorPortraitImageView, 28);
            FrontOfficeMotion.applyRoundedClip(campusRoomImageView, 34);
        });
        playIntroAnimation();
    }

    @FXML
    private void handleNavigationAction(ActionEvent event) {
        if (!(event.getSource() instanceof Button button) || button.getUserData() == null) {
            return;
        }

        String target = button.getUserData().toString();
        if ("CREATE_SESSION".equals(target)) {
            boolean opened = SessionCreationContext.requestSessionCreationOpen();
            if (!opened) {
                FrontOfficeNavigation.open(FrontOfficeNavigation.Destination.RESERVATIONS);
            }
            return;
        }

        try {
            FrontOfficeNavigation.open(FrontOfficeNavigation.Destination.valueOf(target));
        } catch (IllegalArgumentException ignored) {
        }
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
        FrontOfficeMotion.playFloat(floatingStack, 0.0, 0.0, -4.0, 6.0, 3.8);
        FrontOfficeMotion.playFloat(heroVisualPane, -2.0, 2.0, -3.0, 5.0, 4.6);
        FrontOfficeMotion.playFloat(heroGlowOrbOne, -10.0, 12.0, -8.0, 10.0, 7.6);
        FrontOfficeMotion.playFloat(heroGlowOrbTwo, 8.0, -10.0, 10.0, -8.0, 8.4);
    }
}
