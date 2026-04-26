package tn.esprit.fahamni.utils;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnimatedSpiderwebPane extends Pane {

    private static final int NODE_COUNT = 32;
    private static final double CLIP_RADIUS = 32.0;

    private final Canvas canvas = new Canvas();
    private final Rectangle clip = new Rectangle();
    private final List<WebNode> nodes = createNodes();
    private final AnimationTimer animationTimer;

    public AnimatedSpiderwebPane() {
        getChildren().add(canvas);
        setMouseTransparent(true);
        setManaged(true);

        clip.setArcWidth(CLIP_RADIUS * 2.0);
        clip.setArcHeight(CLIP_RADIUS * 2.0);
        setClip(clip);

        animationTimer = new AnimationTimer() {
            private long lastDrawNanos;

            @Override
            public void handle(long now) {
                if (now - lastDrawNanos < 33_000_000L) {
                    return;
                }

                lastDrawNanos = now;
                draw(now / 1_000_000_000.0);
            }
        };

        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                animationTimer.stop();
            } else {
                animationTimer.start();
                draw(System.nanoTime() / 1_000_000_000.0);
            }
        });
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();

        canvas.setWidth(width);
        canvas.setHeight(height);
        clip.setWidth(width);
        clip.setHeight(height);

        draw(System.nanoTime() / 1_000_000_000.0);
    }

    @Override
    protected double computePrefWidth(double height) {
        return 720.0;
    }

    @Override
    protected double computePrefHeight(double width) {
        return 320.0;
    }

    private void draw(double timeSeconds) {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0.0, 0.0, width, height);

        boolean lightMode = FrontOfficeThemePreference.isLightMode();
        double maxDistance = Math.max(138.0, Math.min(width, height) * 0.34);
        Color lineBase = lightMode ? Color.rgb(53, 115, 214) : Color.rgb(89, 225, 255);
        Color dotBase = lightMode ? Color.rgb(44, 121, 228) : Color.rgb(111, 236, 255);

        List<Point> points = new ArrayList<>(nodes.size());
        for (WebNode node : nodes) {
            points.add(node.resolve(width, height, timeSeconds));
        }

        for (int firstIndex = 0; firstIndex < points.size(); firstIndex++) {
            Point firstPoint = points.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < points.size(); secondIndex++) {
                Point secondPoint = points.get(secondIndex);
                double dx = secondPoint.x - firstPoint.x;
                double dy = secondPoint.y - firstPoint.y;
                double distance = Math.hypot(dx, dy);
                if (distance > maxDistance) {
                    continue;
                }

                double intensity = 1.0 - (distance / maxDistance);
                double alpha = (lightMode ? 0.20 : 0.18) * intensity;
                graphics.setStroke(Color.color(
                    lineBase.getRed(),
                    lineBase.getGreen(),
                    lineBase.getBlue(),
                    alpha
                ));
                graphics.setLineWidth(lightMode ? 1.05 : 0.95);
                graphics.strokeLine(firstPoint.x, firstPoint.y, secondPoint.x, secondPoint.y);
            }
        }

        for (Point point : points) {
            graphics.setFill(Color.color(
                dotBase.getRed(),
                dotBase.getGreen(),
                dotBase.getBlue(),
                lightMode ? 0.34 : 0.34
            ));
            graphics.fillOval(point.x - point.radius, point.y - point.radius, point.radius * 2.0, point.radius * 2.0);

            double glowRadius = point.radius * 3.6;
            graphics.setFill(Color.color(
                dotBase.getRed(),
                dotBase.getGreen(),
                dotBase.getBlue(),
                lightMode ? 0.10 : 0.09
            ));
            graphics.fillOval(point.x - glowRadius, point.y - glowRadius, glowRadius * 2.0, glowRadius * 2.0);
        }
    }

    private List<WebNode> createNodes() {
        Random random = new Random(42L);
        List<WebNode> items = new ArrayList<>(NODE_COUNT);

        addAnchorNode(items, 0.06, 0.16);
        addAnchorNode(items, 0.05, 0.42);
        addAnchorNode(items, 0.07, 0.72);
        addAnchorNode(items, 0.18, 0.88);
        addAnchorNode(items, 0.50, 0.90);
        addAnchorNode(items, 0.82, 0.86);
        addAnchorNode(items, 0.94, 0.30);
        addAnchorNode(items, 0.93, 0.66);

        while (items.size() < NODE_COUNT) {
            items.add(new WebNode(
                0.08 + (0.84 * random.nextDouble()),
                0.12 + (0.76 * random.nextDouble()),
                9.0 + (18.0 * random.nextDouble()),
                7.0 + (16.0 * random.nextDouble()),
                0.35 + (0.55 * random.nextDouble()),
                0.35 + (0.55 * random.nextDouble()),
                random.nextDouble() * Math.PI * 2.0,
                random.nextDouble() * Math.PI * 2.0,
                1.2 + (1.6 * random.nextDouble())
            ));
        }

        return items;
    }

    private void addAnchorNode(List<WebNode> items, double x, double y) {
        items.add(new WebNode(x, y, 6.0, 6.0, 0.26, 0.24, 0.0, 1.4, 1.8));
    }

    private record Point(double x, double y, double radius) {
    }

    private static final class WebNode {
        private final double x;
        private final double y;
        private final double swayX;
        private final double swayY;
        private final double speedX;
        private final double speedY;
        private final double phaseX;
        private final double phaseY;
        private final double radius;

        private WebNode(double x, double y, double swayX, double swayY, double speedX, double speedY,
                        double phaseX, double phaseY, double radius) {
            this.x = x;
            this.y = y;
            this.swayX = swayX;
            this.swayY = swayY;
            this.speedX = speedX;
            this.speedY = speedY;
            this.phaseX = phaseX;
            this.phaseY = phaseY;
            this.radius = radius;
        }

        private Point resolve(double width, double height, double timeSeconds) {
            double resolvedX = (x * width) + Math.sin((timeSeconds * speedX) + phaseX) * swayX;
            double resolvedY = (y * height) + Math.cos((timeSeconds * speedY) + phaseY) * swayY;
            return new Point(resolvedX, resolvedY, radius);
        }
    }
}
