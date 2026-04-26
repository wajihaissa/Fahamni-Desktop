package tn.esprit.fahamni.utils;

import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class FrontOfficeMotion {

    private static final String HOVER_KEY = "frontoffice.motion.hover";
    private static final String PULSE_KEY = "frontoffice.motion.pulse";
    private static final String FLOAT_KEY = "frontoffice.motion.float";

    private static final String[] CARD_SELECTORS = {
        ".front-navbar-profile",
        ".dashboard-premium-stat-card",
        ".dashboard-premium-section-card",
        ".dashboard-premium-session-card",
        ".dashboard-premium-tutor-card",
        ".dashboard-premium-resource-row",
        ".dashboard-premium-activity-item",
        ".about-stat-card",
        ".about-section-card",
        ".about-audience-card",
        ".about-feature-card",
        ".about-side-card"
    };

    private FrontOfficeMotion() {
    }

    public static void installInteractiveMotion(Parent root) {
        if (root == null) {
            return;
        }

        Platform.runLater(() -> {
            installHoverForSelector(root, ".button", 1.03, -1.6, 170);
            for (String selector : CARD_SELECTORS) {
                installHoverForSelector(root, selector, 1.012, -4.0, 210);
            }
        });
    }

    public static void playFloat(Node node, double fromX, double toX, double fromY, double toY, double seconds) {
        if (node == null || node.getProperties().containsKey(FLOAT_KEY)) {
            return;
        }

        node.getProperties().put(FLOAT_KEY, Boolean.TRUE);

        TranslateTransition transition = new TranslateTransition(Duration.seconds(seconds), node);
        transition.setFromX(fromX);
        transition.setToX(toX);
        transition.setFromY(fromY);
        transition.setToY(toY);
        transition.setCycleCount(TranslateTransition.INDEFINITE);
        transition.setAutoReverse(true);
        transition.play();
    }

    public static void playPulse(Node node, double fromScale, double toScale, double seconds) {
        if (node == null || node.getProperties().containsKey(PULSE_KEY)) {
            return;
        }

        node.getProperties().put(PULSE_KEY, Boolean.TRUE);

        ScaleTransition transition = new ScaleTransition(Duration.seconds(seconds), node);
        transition.setFromX(fromScale);
        transition.setFromY(fromScale);
        transition.setToX(toScale);
        transition.setToY(toScale);
        transition.setCycleCount(ScaleTransition.INDEFINITE);
        transition.setAutoReverse(true);
        transition.play();
    }

    public static void applyRoundedClip(ImageView imageView, double arc) {
        if (imageView == null) {
            return;
        }

        Rectangle clip = new Rectangle();
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        clip.widthProperty().bind(imageView.fitWidthProperty());
        clip.heightProperty().bind(imageView.fitHeightProperty());
        imageView.setClip(clip);
    }

    public static void bindImageToRegion(ImageView imageView, Region region) {
        bindImageToRegion(imageView, region, 0.0, 0.0);
    }

    public static void bindImageToRegion(ImageView imageView, Region region, double horizontalInset, double verticalInset) {
        if (imageView == null || region == null) {
            return;
        }

        imageView.fitWidthProperty().bind(Bindings.createDoubleBinding(
            () -> Math.max(0.0, region.getWidth() - horizontalInset),
            region.widthProperty()));
        imageView.fitHeightProperty().bind(Bindings.createDoubleBinding(
            () -> Math.max(0.0, region.getHeight() - verticalInset),
            region.heightProperty()));
    }

    private static void installHoverForSelector(Parent root, String selector, double hoverScale, double hoverTranslateY, int durationMs) {
        for (Node node : root.lookupAll(selector)) {
            installHoverLift(node, hoverScale, hoverTranslateY, durationMs);
        }
    }

    private static void installHoverLift(Node node, double hoverScale, double hoverTranslateY, int durationMs) {
        if (node == null || node.getProperties().containsKey(HOVER_KEY)) {
            return;
        }

        node.getProperties().put(HOVER_KEY, Boolean.TRUE);

        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event ->
            animateNode(node, hoverScale, hoverTranslateY, durationMs));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event ->
            animateNode(node, 1.0, 0.0, durationMs));
    }

    private static void animateNode(Node node, double targetScale, double targetTranslateY, int durationMs) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(durationMs), node);
        scaleTransition.setToX(targetScale);
        scaleTransition.setToY(targetScale);

        TranslateTransition translateTransition = new TranslateTransition(Duration.millis(durationMs), node);
        translateTransition.setToY(targetTranslateY);

        new ParallelTransition(scaleTransition, translateTransition).play();
    }
}
