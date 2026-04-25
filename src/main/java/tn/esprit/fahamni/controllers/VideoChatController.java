package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.stage.Window;
import tn.esprit.fahamni.services.conference.LocalCameraService;
import tn.esprit.fahamni.services.conference.UdpAudioReceiver;
import tn.esprit.fahamni.services.conference.UdpAudioSender;
import tn.esprit.fahamni.services.conference.UdpVideoReceiver;
import tn.esprit.fahamni.services.conference.UdpVideoSender;

public class VideoChatController {

    @FXML
    private ImageView localCameraView;

    @FXML
    private ImageView remoteCameraView;

    @FXML
    private TextField myPortField;

    @FXML
    private TextField targetIpField;

    @FXML
    private TextField targetPortField;

    @FXML
    private ToggleButton muteMicButton;

    @FXML
    private ToggleButton cameraOffButton;

    private final LocalCameraService localCameraService = new LocalCameraService();
    private volatile UdpVideoSender udpVideoSender;
    private volatile UdpVideoReceiver udpVideoReceiver;
    private volatile UdpAudioSender udpAudioSender;
    private volatile UdpAudioReceiver udpAudioReceiver;
    private volatile boolean callRunning;

    @FXML
    private void initialize() {
        // Start local loopback preview and optionally stream frames through UDP when call starts.
        localCameraService.startPreview(
            localCameraView::setImage,
            bufferedImage -> {
                if (callRunning && udpVideoSender != null) {
                    udpVideoSender.sendFrame(bufferedImage);
                }
            }
        );

        // Ensure camera is released when this window closes.
        localCameraView.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            Window window = newScene.getWindow();
            if (window != null) {
                window.setOnHidden(event -> shutdownAll());
            }
            newScene.windowProperty().addListener((obsWindow, oldWindow, currentWindow) -> {
                if (currentWindow != null) {
                    currentWindow.setOnHidden(event -> shutdownAll());
                }
            });
        });
    }

    @FXML
    private void startCall() {
        if (callRunning) {
            return;
        }

        try {
            int myPort = Integer.parseInt(myPortField.getText().trim());
            String targetIp = targetIpField.getText().trim();
            int targetPort = Integer.parseInt(targetPortField.getText().trim());
            int myAudioPort = myPort + 1;
            int targetAudioPort = targetPort + 1;

            if (targetIp.isEmpty()) {
                showError("Target IP is required.");
                return;
            }

            udpVideoReceiver = new UdpVideoReceiver(myPort);
            udpVideoReceiver.start(remoteCameraView::setImage);

            udpVideoSender = new UdpVideoSender(targetIp, targetPort);

            udpAudioReceiver = new UdpAudioReceiver(myAudioPort);
            udpAudioReceiver.start();

            udpAudioSender = new UdpAudioSender(targetIp, targetAudioPort);
            udpAudioSender.start();

            callRunning = true;
        } catch (NumberFormatException e) {
            showError("Ports must be valid numbers.");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not start UDP call: " + e.getMessage());
            endCall();
        }
    }

    @FXML
    private void endCall() {
        callRunning = false;

        if (udpVideoSender != null) {
            udpVideoSender.close();
            udpVideoSender = null;
        }

        if (udpVideoReceiver != null) {
            udpVideoReceiver.stop();
            udpVideoReceiver = null;
        }

        if (udpAudioSender != null) {
            udpAudioSender.stop();
            udpAudioSender = null;
        }

        if (udpAudioReceiver != null) {
            udpAudioReceiver.stop();
            udpAudioReceiver = null;
        }

        remoteCameraView.setImage(null);
    }

    @FXML
    private void toggleMuteMic() {
        // Placeholder UX only for now (logic will be wired later).
        muteMicButton.setText(muteMicButton.isSelected() ? "Mic Muted" : "Mute Mic");
    }

    @FXML
    private void toggleCamera() {
        // Placeholder UX only for now (logic will be wired later).
        cameraOffButton.setText(cameraOffButton.isSelected() ? "Camera Off" : "Camera On");
    }

    private void shutdownAll() {
        endCall();
        localCameraService.stopPreview();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Call Lab");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
