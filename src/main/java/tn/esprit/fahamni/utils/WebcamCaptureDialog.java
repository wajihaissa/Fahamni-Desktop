package tn.esprit.fahamni.utils;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
//////TESST
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class WebcamCaptureDialog {

    public record CaptureResult(byte[] imageBytes, String message) {
        public boolean hasImage() {
            return imageBytes != null && imageBytes.length > 0;
        }
    }

    private WebcamCaptureDialog() {
    }

    public static CaptureResult captureJpeg(Window owner, String title, String subtitle) {
        Webcam webcam = null;
        ScheduledExecutorService executor = null;

        try {
            webcam = Webcam.getDefault(3_000);
            if (webcam == null) {
                return new CaptureResult(null, "Aucune webcam detectee sur cette machine.");
            }

            Dimension preferredSize = WebcamResolution.VGA.getSize();
            webcam.setViewSize(preferredSize);
            webcam.open(true);

            ImageView preview = new ImageView();
            preview.setFitWidth(460);
            preview.setFitHeight(320);
            preview.setPreserveRatio(true);
            preview.setSmooth(true);
            preview.getStyleClass().add("webcam-preview-image");

            Label eyebrowLabel = new Label("FACE ID LIVE CAPTURE");
            eyebrowLabel.getStyleClass().add("webcam-dialog-eyebrow");

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("webcam-dialog-title");
            titleLabel.setWrapText(true);

            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.setWrapText(true);
            subtitleLabel.getStyleClass().add("webcam-dialog-copy");

            Label statusLabel = new Label("Positionnez votre visage au centre du cadre, puis capturez.");
            statusLabel.getStyleClass().add("webcam-dialog-status");

            Label liveChip = new Label("LIVE");
            liveChip.getStyleClass().add("webcam-dialog-chip");

            Label oneFaceChip = new Label("ONE FACE ONLY");
            oneFaceChip.getStyleClass().addAll("webcam-dialog-chip", "secondary");

            Button captureButton = new Button("Capturer");
            captureButton.getStyleClass().add("profile-accent-button");
            captureButton.setDefaultButton(true);
            captureButton.setMaxWidth(Double.MAX_VALUE);

            Button cancelButton = new Button("Annuler");
            cancelButton.getStyleClass().add("profile-soft-button");
            cancelButton.setCancelButton(true);
            cancelButton.setMaxWidth(Double.MAX_VALUE);

            HBox chipRow = new HBox(8.0, liveChip, oneFaceChip);
            chipRow.setAlignment(Pos.CENTER_LEFT);

            VBox headerBox = new VBox(8.0, eyebrowLabel, titleLabel, subtitleLabel, chipRow);
            headerBox.getStyleClass().add("webcam-dialog-header");

            StackPane previewFrame = new StackPane(preview);
            previewFrame.getStyleClass().add("webcam-preview-frame");
            previewFrame.setPrefHeight(340.0);
            previewFrame.setMinHeight(340.0);
            VBox.setVgrow(previewFrame, Priority.ALWAYS);

            Region actionSpacer = new Region();
            HBox.setHgrow(actionSpacer, Priority.ALWAYS);

            HBox actionRow = new HBox(12.0, cancelButton, actionSpacer, captureButton);
            actionRow.setAlignment(Pos.CENTER_LEFT);
            actionRow.getStyleClass().add("webcam-dialog-actions");

            VBox root = new VBox(18.0, headerBox, previewFrame, statusLabel, actionRow);
            root.setPadding(new Insets(22.0));
            root.getStyleClass().add("webcam-dialog-root");

            Scene scene = new Scene(root);
            String frontTheme = WebcamCaptureDialog.class.getResource("/com/fahamni/styles/frontoffice-theme.css") != null
                ? WebcamCaptureDialog.class.getResource("/com/fahamni/styles/frontoffice-theme.css").toExternalForm()
                : null;
            if (frontTheme != null) {
                scene.getStylesheets().add(frontTheme);
            }

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setTitle(title);
            stage.setScene(scene);
            stage.setMinWidth(520);
            stage.setMinHeight(520);
            stage.setResizable(false);

            BufferedImage[] latestFrame = new BufferedImage[1];
            byte[][] resultHolder = new byte[1][];

            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "fahamni-webcam-preview");
                thread.setDaemon(true);
                return thread;
            });

            Webcam activeWebcam = webcam;
            executor.scheduleAtFixedRate(() -> {
                if (!activeWebcam.isOpen()) {
                    return;
                }

                BufferedImage image = activeWebcam.getImage();
                if (image == null) {
                    return;
                }

                latestFrame[0] = image;
                Platform.runLater(() -> preview.setImage(SwingFXUtils.toFXImage(image, null)));
            }, 0L, 120L, TimeUnit.MILLISECONDS);

            captureButton.setOnAction(event -> {
                BufferedImage image = latestFrame[0];
                if (image == null) {
                    statusLabel.setText("Aucune image detectee. Verifiez votre camera et reessayez.");
                    return;
                }

                try {
                    resultHolder[0] = toJpegBytes(image);
                    stage.close();
                } catch (IOException exception) {
                    statusLabel.setText("Impossible de convertir la capture en JPEG.");
                }
            });

            cancelButton.setOnAction(event -> stage.close());
            final ScheduledExecutorService[] executorRef = {executor};
            final Webcam[] webcamRef = {webcam};
            stage.setOnHidden(event -> {
                if (executorRef[0] != null) {
                    executorRef[0].shutdownNow();
                }
                if (webcamRef[0] != null && webcamRef[0].isOpen()) {
                    webcamRef[0].close();
                }
            });

            stage.showAndWait();
            if (resultHolder[0] == null) {
                return new CaptureResult(null, "Capture annulee.");
            }
            return new CaptureResult(resultHolder[0], null);
        } catch (Exception ignored) {
            return new CaptureResult(null, "Impossible d'acceder a la webcam pour le moment.");
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
            }
        }
    }

    private static byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }
}
