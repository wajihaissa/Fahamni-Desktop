package tn.esprit.fahamni.controllers;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
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
import javafx.scene.layout.BorderPane;
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
    private BorderPane rootPane;

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
    private volatile boolean cameraEnabled = true;
    private volatile boolean previewStarted;
    private volatile String currentPeerIp;
    private volatile int currentPeerAudioPort = -1;

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
        callStatusLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 12px; -fx-background-color: rgba(30,41,59,0.9); -fx-background-radius: 10; -fx-padding: 7 10;");
        toastLabel.setVisible(false);
        toastLabel.setManaged(false);
        toastLabel.setOpacity(0);
        muteMicButton.setSelected(false);
        cameraOffButton.setSelected(false);
        muteMicButton.setText("Mute Mic");
        cameraOffButton.setText("Camera On");

        applyHoverEffects();
        rootPane.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null) {
                shutdownAll();
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
            int myVideoPort = parseVideoPort();
            String localIp = resolveLocalIpAddress();

            String roomCode = callRoomService.createRoom(localIp, myVideoPort);
            ensureLocalPreviewStarted();
            currentRoomCode = roomCode;
            roomCodeValueLabel.setText(roomCode);
            callStatusLabel.setStyle("-fx-text-fill: #fde68a; -fx-font-size: 12px; -fx-background-color: rgba(120,53,15,0.75); -fx-background-radius: 10; -fx-padding: 7 10;");
            startWaitingAnimation();

            startPollingForGuest(roomCode, myVideoPort);
        } catch (NumberFormatException e) {
            showError(e.getMessage());
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
        if (!roomCode.matches("\\d{6}")) {
            showError("Room code must be exactly 6 digits.");
            return;
        }

        try {
            int myVideoPort = parseVideoPort();
            String localIp = resolveLocalIpAddress();

            CallRoomService.PeerEndpoint hostEndpoint = callRoomService.joinRoom(roomCode, localIp, myVideoPort);
            if (hostEndpoint == null) {
                showError("Room not found or already active.");
                return;
            }

            ensureLocalPreviewStarted();
            currentRoomCode = roomCode;
            roomCodeValueLabel.setText(roomCode);
            startMediaSession(hostEndpoint.getIp(), hostEndpoint.getVideoPort(), myVideoPort);
            callStatusLabel.setText("Connected as guest.");
            showJoinToast("Joined room " + roomCode + " successfully");
        } catch (NumberFormatException e) {
            showError(e.getMessage());
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
        localCameraView.setImage(null);
        localCameraService.stopPreview();
        previewStarted = false;
        currentPeerIp = null;
        currentPeerAudioPort = -1;
        currentRoomCode = null;
        roomCodeValueLabel.setText("-");
        muteMicButton.setSelected(false);
        muteMicButton.setText("Mute Mic");
        cameraEnabled = true;
        cameraOffButton.setSelected(false);
        cameraOffButton.setText("Camera On");
        if (callStatusLabel != null) {
            updateCallStatus("Idle", "#f8fafc", "rgba(30,41,59,0.9)");
        }
    }

    @FXML
    private void toggleMuteMic() {
        if (muteMicButton.isSelected()) {
            muteMicButton.setText("Mic Off");
            stopAudioSender();
            return;
        }

        muteMicButton.setText("Mute Mic");
        if (callRunning) {
            try {
                startAudioSender();
            } catch (Exception e) {
                muteMicButton.setSelected(true);
                muteMicButton.setText("Mic Off");
                showError("Could not turn microphone back on: " + e.getMessage());
            }
        }
    }

    @FXML
    private void toggleCamera() {
        if (cameraOffButton.isSelected()) {
            cameraEnabled = false;
            cameraOffButton.setText("Camera Off");
            localCameraService.stopPreview();
            previewStarted = false;
            localCameraView.setImage(null);
            return;
        }

        cameraEnabled = true;
        cameraOffButton.setText("Camera On");
        if (currentRoomCode != null || callRunning) {
            ensureLocalPreviewStarted();
        }
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
                    Platform.runLater(() -> {
                        if (!callRunning && roomCode.equals(currentRoomCode)) {
                            stopWaitingAnimation();
                            updateCallStatus("Room polling failed.", "#fecaca", "rgba(127,29,29,0.85)");
                            showError("Could not keep waiting for a guest: " + e.getMessage());
                        }
                    });
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

        UdpVideoReceiver nextVideoReceiver = null;
        UdpVideoSender nextVideoSender = null;
        UdpAudioReceiver nextAudioReceiver = null;
        UdpAudioSender nextAudioSender = null;

        try {
            currentPeerIp = targetIp;
            currentPeerAudioPort = targetAudioPort;
            nextVideoReceiver = new UdpVideoReceiver(myVideoPort);
            nextVideoReceiver.start(remoteCameraView::setImage);

            nextVideoSender = new UdpVideoSender(targetIp, targetVideoPort);

            nextAudioReceiver = new UdpAudioReceiver(myAudioPort);
            nextAudioReceiver.start();

            if (!muteMicButton.isSelected()) {
                nextAudioSender = new UdpAudioSender(targetIp, targetAudioPort);
                nextAudioSender.start();
            }

            udpVideoReceiver = nextVideoReceiver;
            udpVideoSender = nextVideoSender;
            udpAudioReceiver = nextAudioReceiver;
            udpAudioSender = nextAudioSender;

            callRunning = true;
            stopPollingHostRoom();
            updateCallStatus("Call connected.", "#d1fae5", "rgba(6,95,70,0.85)");
        } catch (Exception e) {
            if (nextVideoSender != null) {
                nextVideoSender.close();
            }
            if (nextVideoReceiver != null) {
                nextVideoReceiver.stop();
            }
            if (nextAudioSender != null) {
                nextAudioSender.stop();
            }
            if (nextAudioReceiver != null) {
                nextAudioReceiver.stop();
            }
            currentPeerIp = null;
            currentPeerAudioPort = -1;
            throw e;
        }
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
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String ip = address.getHostAddress();

                    // Keep only valid IPv4 LAN addresses. Ignore IPv6 and APIPA fallback ranges.
                    if (!ip.contains(":") && !ip.startsWith("169.254")) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }

    private void shutdownAll() {
        endCall();
        localCameraService.stopPreview();
        previewStarted = false;
        if (localCameraView != null) {
            localCameraView.setImage(null);
        }
        if (remoteCameraView != null) {
            remoteCameraView.setImage(null);
        }
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

    private void updateCallStatus(String text, String textColor, String backgroundColor) {
        callStatusLabel.setText(text);
        callStatusLabel.setStyle(
            "-fx-text-fill: " + textColor + "; "
                + "-fx-font-size: 12px; "
                + "-fx-background-color: " + backgroundColor + "; "
                + "-fx-background-radius: 10; "
                + "-fx-padding: 7 10;"
        );
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

    private void ensureLocalPreviewStarted() {
        if (previewStarted || !cameraEnabled) {
            return;
        }

        localCameraService.startPreview(
            localCameraView::setImage,
            bufferedImage -> {
                if (callRunning && udpVideoSender != null && cameraEnabled) {
                    udpVideoSender.sendFrame(bufferedImage);
                }
            }
        );
        previewStarted = true;
    }

    private void startAudioSender() throws Exception {
        if (!callRunning || currentPeerIp == null || currentPeerAudioPort <= 0 || udpAudioSender != null) {
            return;
        }
        UdpAudioSender sender = new UdpAudioSender(currentPeerIp, currentPeerAudioPort);
        sender.start();
        udpAudioSender = sender;
    }

    private void stopAudioSender() {
        if (udpAudioSender != null) {
            udpAudioSender.stop();
            udpAudioSender = null;
        }
    }

    private int parseVideoPort() {
        int port;
        try {
            port = Integer.parseInt(myPortField.getText().trim());
        } catch (Exception e) {
            throw new NumberFormatException("My Video Port must be a valid number.");
        }

        if (port < 1024 || port > 65534) {
            throw new NumberFormatException("My Video Port must be between 1024 and 65534.");
        }

        return port;
    }
}
