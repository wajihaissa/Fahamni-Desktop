package tn.esprit.fahamni.utils;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

public final class VoiceCaptureDialog {

    private static final int SAMPLE_RATE = 16_000;
    private static final int SAMPLE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final int CAPTURE_SECONDS = 4;
    private static final AudioFormat FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        SAMPLE_RATE,
        SAMPLE_BITS,
        CHANNELS,
        CHANNELS * SAMPLE_BITS / 8,
        SAMPLE_RATE,
        false
    );

    private VoiceCaptureDialog() {
    }

    public static CaptureResult capture(Window owner, String title, String instruction) {
        Dialog<CaptureResult> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Enregistrement vocal");

        DialogPane dialogPane = dialog.getDialogPane();
        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType useButtonType = new ButtonType("Utiliser cet enregistrement", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().setAll(cancelButtonType, useButtonType);

        Label instructionLabel = new Label(instruction);
        instructionLabel.setWrapText(true);

        Label statusLabel = new Label("Pret. Cliquez sur enregistrer puis parlez clairement pendant 4 secondes.");
        statusLabel.setWrapText(true);

        Button recordButton = new Button("Enregistrer ma voix");
        VBox content = new VBox(12.0, instructionLabel, statusLabel, recordButton);
        dialogPane.setContent(content);

        final byte[][] capturedAudio = new byte[1][];
        Button useButton = (Button) dialogPane.lookupButton(useButtonType);
        useButton.setDisable(true);

        recordButton.setOnAction(event -> {
            recordButton.setDisable(true);
            useButton.setDisable(true);
            statusLabel.setText("Enregistrement en cours... parlez maintenant.");

            Task<byte[]> recordTask = new Task<>() {
                @Override
                protected byte[] call() throws Exception {
                    return recordPcm();
                }
            };

            recordTask.setOnSucceeded(done -> {
                capturedAudio[0] = recordTask.getValue();
                if (capturedAudio[0] == null || capturedAudio[0].length == 0) {
                    statusLabel.setText("Aucun son capture. Verifiez le micro et recommencez.");
                    recordButton.setDisable(false);
                    return;
                }

                statusLabel.setText("Voix capturee. Vous pouvez utiliser cet enregistrement ou recommencer.");
                recordButton.setDisable(false);
                useButton.setDisable(false);
            });

            recordTask.setOnFailed(done -> {
                Throwable error = recordTask.getException();
                statusLabel.setText(error == null ? "Capture impossible." : error.getMessage());
                recordButton.setDisable(false);
            });

            Thread thread = new Thread(recordTask, "voice-capture");
            thread.setDaemon(true);
            thread.start();
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType == useButtonType) {
                return new CaptureResult(capturedAudio[0], capturedAudio[0] != null, null);
            }
            return new CaptureResult(null, false, "Capture annulee.");
        });

        if (owner != null) {
            dialog.initOwner(owner);
        }

        Optional<CaptureResult> result = dialog.showAndWait();
        return result.orElseGet(() -> new CaptureResult(null, false, "Capture annulee."));
    }

    public static boolean isMicrophoneAvailable() {
        try {
            return AudioSystem.isLineSupported(AudioSystem.getTargetDataLine(FORMAT).getLineInfo());
        } catch (LineUnavailableException exception) {
            return false;
        }
    }

    public static void showMicrophoneError(Window owner) {
        Alert alert = new Alert(Alert.AlertType.ERROR, "Aucun microphone compatible n'a ete trouve.");
        alert.setTitle("Microphone indisponible");
        alert.setHeaderText("Impossible d'enregistrer la voix");
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    private static byte[] recordPcm() throws LineUnavailableException {
        TargetDataLine line = AudioSystem.getTargetDataLine(FORMAT);
        int bytesToCapture = SAMPLE_RATE * CAPTURE_SECONDS * FORMAT.getFrameSize();
        byte[] buffer = new byte[2048];
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytesToCapture);

        try {
            line.open(FORMAT);
            line.start();
            long deadline = System.currentTimeMillis() + CAPTURE_SECONDS * 1000L;
            while (System.currentTimeMillis() < deadline && out.size() < bytesToCapture) {
                int read = line.read(buffer, 0, Math.min(buffer.length, bytesToCapture - out.size()));
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            line.stop();
            line.close();
        }

        return out.toByteArray();
    }

    public record CaptureResult(byte[] audioBytes, boolean hasAudio, String message) {
    }
}
