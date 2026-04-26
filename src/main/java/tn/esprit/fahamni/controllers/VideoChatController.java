package tn.esprit.fahamni.controllers;

import java.net.InetAddress;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.stage.Window;
import javafx.util.Duration;
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
    private Label toastLabel;

    @FXML
    private Button createRoomButton;

    @FXML
    private Button joinRoomButton;

    @FXML
    private Button endCallButton;

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
    private Timeline waitingTimeline;

    private static final String CREATE_BASE_STYLE =
        "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 16;";
    private static final String CREATE_HOVER_STYLE =
        "-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 16;";
    private static final String JOIN_BASE_STYLE =
        "-fx-background-color: #0ea5e9; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 16;";
    private static final String JOIN_HOVER_STYLE =
        "-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 16;";
    private static final String END_BASE_STYLE =
        "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 9 16;";
    private static final String END_HOVER_STYLE =
        "-fx-background-color: #b91c1c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 9 16;";

    @FXML
    private void initialize() {
        localIpLabel.setText("Local IP: " + resolveLocalIpAddress());
        roomCodeValueLabel.setText("-");
        callStatusLabel.setText("Idle");
        toastLabel.setVisible(false);
        toastLabel.setManaged(false);
        toastLabel.setOpacity(0);

        applyHoverEffects();

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
            callStatusLabel.setStyle("-fx-text-fill: #fde68a; -fx-font-size: 12px; -fx-background-color: rgba(120,53,15,0.75); -fx-background-radius: 10; -fx-padding: 7 10;");
            startWaitingAnimation();

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
            showJoinToast("Joined room " + roomCode + " successfully");
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
        stopWaitingAnimation();
        callRunning = false;

        if (currentRoomCode != null && !currentRoomCode.isBlank()) {
            try {
                callRoomService.closeRoom(currentRoomCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

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
        currentRoomCode = null;
        roomCodeValueLabel.setText("-");
        if (callStatusLabel != null) {
            callStatusLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 12px; -fx-background-color: rgba(30,41,59,0.9); -fx-background-radius: 10; -fx-padding: 7 10;");
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
                                showJoinToast("Guest joined room " + roomCode);
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

        stopWaitingAnimation();

        udpVideoReceiver = new UdpVideoReceiver(myVideoPort);
        udpVideoReceiver.start(remoteCameraView::setImage);

        udpVideoSender = new UdpVideoSender(targetIp, targetVideoPort);

        udpAudioReceiver = new UdpAudioReceiver(myAudioPort);
        udpAudioReceiver.start();

        udpAudioSender = new UdpAudioSender(targetIp, targetAudioPort);
        udpAudioSender.start();

        callRunning = true;
        stopPollingHostRoom();
        callStatusLabel.setStyle("-fx-text-fill: #d1fae5; -fx-font-size: 12px; -fx-background-color: rgba(6,95,70,0.85); -fx-background-radius: 10; -fx-padding: 7 10;");
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

    private void startWaitingAnimation() {
        stopWaitingAnimation();
        String[] states = {"Waiting for guest.", "Waiting for guest..", "Waiting for guest..."};
        final int[] index = {0};

        callStatusLabel.setText(states[0]);
        waitingTimeline = new Timeline(new KeyFrame(Duration.millis(550), event -> {
            if (!callRunning) {
                callStatusLabel.setText(states[index[0] % states.length]);
                index[0]++;
            }
        }));
        waitingTimeline.setCycleCount(Timeline.INDEFINITE);
        waitingTimeline.play();
    }

    private void stopWaitingAnimation() {
        if (waitingTimeline != null) {
            waitingTimeline.stop();
            waitingTimeline = null;
        }
    }

    private void showJoinToast(String message) {
        toastLabel.setText(message);
        toastLabel.setManaged(true);
        toastLabel.setVisible(true);
        toastLabel.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), toastLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition hold = new PauseTransition(Duration.seconds(2.2));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(420), toastLabel);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition sequence = new SequentialTransition(fadeIn, hold, fadeOut);
        sequence.setOnFinished(event -> {
            toastLabel.setVisible(false);
            toastLabel.setManaged(false);
        });
        sequence.play();
    }

    private void applyHoverEffects() {
        bindButtonHover(createRoomButton, CREATE_BASE_STYLE, CREATE_HOVER_STYLE);
        bindButtonHover(joinRoomButton, JOIN_BASE_STYLE, JOIN_HOVER_STYLE);
        bindButtonHover(endCallButton, END_BASE_STYLE, END_HOVER_STYLE);
    }

    private void bindButtonHover(Button button, String baseStyle, String hoverStyle) {
        if (button == null) {
            return;
        }
        button.setStyle(baseStyle);
        button.hoverProperty().addListener((obs, wasHovered, isHovered) -> button.setStyle(isHovered ? hoverStyle : baseStyle));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Call Lab");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
