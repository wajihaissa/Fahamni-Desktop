package tn.esprit.fahamni.controllers;

import java.net.InetAddress;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.stage.Window;
import tn.esprit.fahamni.services.conference.CallRoomService;
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
    private TextField joinRoomCodeField;

    @FXML
    private Label localIpLabel;

    @FXML
    private Label roomCodeValueLabel;

    @FXML
    private Label callStatusLabel;

    @FXML
    private ToggleButton muteMicButton;

    @FXML
    private ToggleButton cameraOffButton;

    private final LocalCameraService localCameraService = new LocalCameraService();
    private final CallRoomService callRoomService = new CallRoomService();

    private volatile UdpVideoSender udpVideoSender;
    private volatile UdpVideoReceiver udpVideoReceiver;
    private volatile UdpAudioSender udpAudioSender;
    private volatile UdpAudioReceiver udpAudioReceiver;
    private volatile boolean callRunning;

    private volatile boolean pollingHostRoom;
    private Thread pollThread;
    private String currentRoomCode;

    @FXML
    private void initialize() {
        localIpLabel.setText("Local IP: " + resolveLocalIpAddress());
        roomCodeValueLabel.setText("-");
        callStatusLabel.setText("Idle");

        // Always keep local preview on.
        localCameraService.startPreview(
            localCameraView::setImage,
            bufferedImage -> {
                if (callRunning && udpVideoSender != null) {
                    udpVideoSender.sendFrame(bufferedImage);
                }
            }
        );

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
    private void createRoom() {
        stopPollingHostRoom();
        endCall();

        try {
            int myVideoPort = Integer.parseInt(myPortField.getText().trim());
            String localIp = resolveLocalIpAddress();

            String roomCode = callRoomService.createRoom(localIp, myVideoPort);
            currentRoomCode = roomCode;
            roomCodeValueLabel.setText(roomCode);
            callStatusLabel.setText("Room created. Waiting for guest...");

            startPollingForGuest(roomCode, myVideoPort);
        } catch (NumberFormatException e) {
            showError("My Video Port must be a valid number.");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not create room: " + e.getMessage());
        }
    }

    @FXML
    private void joinRoom() {
        stopPollingHostRoom();
        endCall();

        String roomCode = joinRoomCodeField.getText() == null ? "" : joinRoomCodeField.getText().trim();
        if (roomCode.isEmpty()) {
            showError("Please enter a room code.");
            return;
        }

        try {
            int myVideoPort = Integer.parseInt(myPortField.getText().trim());
            String localIp = resolveLocalIpAddress();

            CallRoomService.PeerEndpoint hostEndpoint = callRoomService.joinRoom(roomCode, localIp, myVideoPort);
            if (hostEndpoint == null) {
                showError("Room not found or already active.");
                return;
            }

            currentRoomCode = roomCode;
            roomCodeValueLabel.setText(roomCode);
            startMediaSession(hostEndpoint.getIp(), hostEndpoint.getVideoPort(), myVideoPort);
            callStatusLabel.setText("Connected as guest.");
        } catch (NumberFormatException e) {
            showError("My Video Port must be a valid number.");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not join room: " + e.getMessage());
        }
    }

    @FXML
    private void endCall() {
        stopPollingHostRoom();
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
        if (callStatusLabel != null) {
            callStatusLabel.setText("Idle");
        }
    }

    @FXML
    private void toggleMuteMic() {
        muteMicButton.setText(muteMicButton.isSelected() ? "Mic Muted" : "Mute Mic");
    }

    @FXML
    private void toggleCamera() {
        cameraOffButton.setText(cameraOffButton.isSelected() ? "Camera Off" : "Camera On");
    }

    private void startPollingForGuest(String roomCode, int myVideoPort) {
        pollingHostRoom = true;

        pollThread = new Thread(() -> {
            while (pollingHostRoom && !callRunning) {
                try {
                    CallRoomService.PeerEndpoint guestEndpoint = callRoomService.pollForGuest(roomCode);
                    if (guestEndpoint != null) {
                        Platform.runLater(() -> {
                            try {
                                startMediaSession(guestEndpoint.getIp(), guestEndpoint.getVideoPort(), myVideoPort);
                                callStatusLabel.setText("Guest joined. Call connected.");
                            } catch (Exception e) {
                                showError("Could not start media session: " + e.getMessage());
                            }
                        });
                        break;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }, "call-room-host-poll-thread");

        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void startMediaSession(String targetIp, int targetVideoPort, int myVideoPort) throws Exception {
        if (callRunning) {
            return;
        }

        int myAudioPort = myVideoPort + 1;
        int targetAudioPort = targetVideoPort + 1;

        udpVideoReceiver = new UdpVideoReceiver(myVideoPort);
        udpVideoReceiver.start(remoteCameraView::setImage);

        udpVideoSender = new UdpVideoSender(targetIp, targetVideoPort);

        udpAudioReceiver = new UdpAudioReceiver(myAudioPort);
        udpAudioReceiver.start();

        udpAudioSender = new UdpAudioSender(targetIp, targetAudioPort);
        udpAudioSender.start();

        callRunning = true;
        stopPollingHostRoom();
    }

    private void stopPollingHostRoom() {
        pollingHostRoom = false;
        if (pollThread != null && pollThread.isAlive()) {
            pollThread.interrupt();
        }
        pollThread = null;
    }

    private String resolveLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
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
