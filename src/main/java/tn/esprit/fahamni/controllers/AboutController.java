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
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tn.esprit.fahamni.utils.FrontOfficeMotion;
import tn.esprit.fahamni.utils.FrontOfficeNavigation;

public class AboutController {

    @FXML
    private VBox aboutContent;

    @FXML
    private HBox aboutHeroSection;

    @FXML
    private FlowPane aboutStatStrip;

    @FXML
    private VBox aboutAudienceSection;

    @FXML
    private VBox aboutFeatureSection;

    @FXML
    private HBox aboutClosingGrid;

    @FXML
    private VBox aboutPortraitCard;

    @FXML
    private VBox aboutSummaryBanner;

    @FXML
    private ImageView aboutPortraitImageView;

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            FrontOfficeMotion.installInteractiveMotion(aboutContent);
            FrontOfficeMotion.applyRoundedClip(aboutPortraitImageView, 38);
        });
        playIntroAnimation();
    }

    @FXML
    private void handleNavigationAction(ActionEvent event) {
        if (!(event.getSource() instanceof Button button) || button.getUserData() == null) {
            return;
        }

        try {
            FrontOfficeNavigation.open(FrontOfficeNavigation.Destination.valueOf(button.getUserData().toString()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playIntroAnimation() {
        animateSection(aboutHeroSection, 0);
        animateSection(aboutStatStrip, 90);
        animateSection(aboutAudienceSection, 180);
        animateSection(aboutFeatureSection, 270);
        animateSection(aboutClosingGrid, 360);
        animateSection(aboutSummaryBanner, 450);

        FrontOfficeMotion.playFloat(aboutPortraitCard, -2.0, 2.0, -4.0, 4.0, 4.8);
        FrontOfficeMotion.playFloat(aboutSummaryBanner, 0.0, 0.0, -2.0, 3.0, 5.8);
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
}
