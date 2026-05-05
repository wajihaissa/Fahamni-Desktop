package tn.esprit.fahamni.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.util.Duration;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.services.SessionCreationContext;
import tn.esprit.fahamni.services.UserAccountService;
import tn.esprit.fahamni.services.voice.LocalSpeechToTextService;
import tn.esprit.fahamni.services.voice.VoiceAssistantIntent;
import tn.esprit.fahamni.services.voice.VoiceAssistantInterpreter;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.ApplicationState;
import tn.esprit.fahamni.utils.FrontOfficeMotion;
import tn.esprit.fahamni.utils.FrontOfficeNavigation;
import tn.esprit.fahamni.utils.FrontOfficeThemePreference;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.UserSession;
import tn.esprit.fahamni.utils.ViewNavigator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    private static final double GLOBAL_AI_PANEL_WIDTH = 400.0;
    private static final List<String> VOICE_WAKE_GRAMMAR = List.of(
        "hey funny",
        "hi funny",
        "hello funny",
        "hey family",
        "hi family",
        "hello family",
        "hey for me",
        "hi for me",
        "hello for me",
        "hey faham me",
        "assistant"
    );
    private static final List<String> VOICE_COMMAND_GRAMMAR = List.of(
        "open dashboard",
        "open home",
        "open profile",
        "open settings",
        "open security",
        "save my voice",
        "record my voice",
        "activate voice pass",
        "open reservations",
        "find tutor",
        "open calendar",
        "open sessions",
        "open planner",
        "open messages",
        "open messenger",
        "open quiz",
        "open blog",
        "open about",
        "logout",
        "log out",
        "cancel",
        "stop",
        "help"
    );

    private final NotificationService notifService = new NotificationService();
    private final UserAccountService userAccountService = new UserAccountService();
    private final VoiceAssistantInterpreter voiceAssistantInterpreter = new VoiceAssistantInterpreter();
    private final LocalSpeechToTextService localSpeechToTextService = new LocalSpeechToTextService();

    @FXML private BorderPane rootPane;
    @FXML private AnchorPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Button alertButton;
    @FXML private Label alertNotifBadge;
    @FXML private Button dashboardButton;
    @FXML private Button reservationsButton;
    @FXML private Button seancesButton;
    @FXML private Button sallesEquipementsButton;
    @FXML private Button plannerButton;
    @FXML private Button coursButton;
    @FXML private Button callLabButton;
    @FXML private Button quizButton;
    @FXML private Button blogButton;
    @FXML private Button aboutButton;
    @FXML private Button aiButton;
    @FXML private Button accountButton;
    @FXML private Label profileAvatarLabel;
    @FXML private Label profileNameLabel;
    @FXML private Label profileRoleLabel;
    @FXML private VBox globalAiPanel;
    @FXML private AnchorPane globalAiContent;
    @FXML private Button globalAiFab;
    @FXML private Region globalAiScrim;
    @FXML private VBox voiceAssistantPanel;
    @FXML private Button voiceAssistantBubbleButton;
    @FXML private Label voiceAssistantStatusLabel;
    @FXML private Label voiceAssistantReplyLabel;
    @FXML private TextField voiceAssistantCommandField;

    private ContextMenu accountMenu;
    private Popup alertsPopup;
    private GlobalChatbotController globalChatbotController;
    private boolean globalAiLoaded;
    private boolean globalAiOpen;
    private boolean voiceAssistantWaitingForCommand;
    private volatile boolean voiceAssistantWakeLoopRunning;
    private volatile boolean voiceAssistantListenBusy;
    private static final DateTimeFormatter NOTIF_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private void initialize() {
        if (!UserSession.hasCurrentUser() || !UserSession.hasValidJwtToken()) {
            handleLogout();
            return;
        }

        System.out.println("MainController initialized");
        SessionCreationContext.registerNavigator(this::showReservations);
        FrontOfficeNavigation.registerNavigator(this::openDestination);
        ViewNavigator.getInstance().initialize(contentPane, pageTitle);
        ApplicationState.getInstance().setCurrentView("Accueil");
        refreshCurrentUserSummary();
        applyThemeMode();
        initializeAccountMenu();
        installContentClip();
        Platform.runLater(() -> {
            FrontOfficeMotion.installInteractiveMotion(rootPane);
            startVoiceAssistantWakeLoop();
        });

        if (globalAiPanel != null) {
            globalAiPanel.setManaged(false);
            globalAiPanel.setVisible(false);
            globalAiPanel.setTranslateX(GLOBAL_AI_PANEL_WIDTH);
        }
        if (globalAiScrim != null) {
            globalAiScrim.setManaged(false);
            globalAiScrim.setVisible(false);
            globalAiScrim.setMouseTransparent(true);
            globalAiScrim.setOpacity(0);
        }

        showDashboard();
        refreshBlogBadge();
    }

    public void refreshBlogBadge() {
        if (alertNotifBadge == null) {
            return;
        }
        try {
            if (!UserSession.hasCurrentUser() || UserSession.getCurrentUserId() <= 0) {
                alertNotifBadge.setVisible(false);
                alertNotifBadge.setManaged(false);
                return;
            }

            int count = notifService.countUnreadForUser(UserSession.getCurrentUserId());
            if (count > 0) {
                alertNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                alertNotifBadge.setVisible(true);
                alertNotifBadge.setManaged(true);
            } else {
                alertNotifBadge.setVisible(false);
                alertNotifBadge.setManaged(false);
            }
        } catch (Exception e) {
            System.err.println("refreshBlogBadge: " + e.getMessage());
        }
    }

    @FXML
    private void showDashboard() {
        loadView("DashboardView.fxml", "Accueil");
        setActiveButton(dashboardButton);
    }

    @FXML
    private void showReservations() {
        loadView("ReservationView.fxml", "Trouver un tuteur");
        setActiveButton(reservationsButton);
    }

    @FXML
    private void showSeances() {
        loadView("SeanceListView.fxml", "Calendrier");
        setActiveButton(seancesButton);
    }

    @FXML
    private void showSallesEquipements() {
        if (!canAccessSallesEquipements()) {
            showDashboard();
            return;
        }
        loadView("SallesEquipementsView.fxml", "Salles & Materiel");
        setActiveButton(sallesEquipementsButton);
    }

    @FXML
    private void showPlanner() {
        loadView("PlannerView.fxml", "Planner de revision");
        setActiveButton(plannerButton);
    }

    @FXML
    private void showCours() {
        loadView("FrontMatiereView.fxml", "Cours");
        setActiveButton(coursButton);
    }

    @FXML
    private void showCallLab() {
        loadView("VideoChatView.fxml", "Call Lab");
        setActiveButton(callLabButton);
    }

    @FXML
    private void showQuiz() {
        loadView("QuizView.fxml", "Quiz");
        setActiveButton(quizButton);
    }

    @FXML
    private void showBlog() {
        loadView("BlogView.fxml", "Blog & Ressources");
        setActiveButton(blogButton);
    }

    @FXML
    private void showAbout() {
        loadView("AboutView.fxml", "A propos de Fahamni");
        setActiveButton(aboutButton);
    }

    @FXML
    private void showFahamniAi() {
        toggleGlobalAi();
    }

    @FXML
    private void toggleAlertsPopup() {
        if (alertButton == null) {
            return;
        }
        if (alertsPopup != null && alertsPopup.isShowing()) {
            alertsPopup.hide();
            return;
        }

        int userId = UserSession.hasCurrentUser() ? UserSession.getCurrentUserId() : 0;
        List<Notification> notifications = userId > 0
                ? notifService.getAllForUser(userId)
                : new ArrayList<>();
        long unreadCount = notifications.stream().filter(notification -> !notification.isRead()).count();

        VBox container = new VBox(0);
        container.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.18),18,0,0,4);" +
            "-fx-border-color: #e3e6ef; -fx-border-radius: 14; -fx-border-width: 1;");
        container.setPrefWidth(340);

        HBox header = new HBox(8);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 16));
        header.setStyle("-fx-background-color: linear-gradient(to right,#6b5dd3,#5068d1);" +
                        "-fx-background-radius: 14 14 0 0;");
        Label titleLabel = new Label("Notifications");
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String countText = unreadCount > 0
                ? unreadCount + " non lu(s)"
                : (notifications.isEmpty() ? "Aucune" : notifications.size() + " au total");
        Label countLabel = new Label(countText);
        countLabel.setStyle("-fx-font-size: 11; -fx-text-fill: rgba(255,255,255,0.85);");
        header.getChildren().addAll(titleLabel, spacer, countLabel);
        container.getChildren().add(header);

        if (notifications.isEmpty()) {
            Label emptyLabel = new Label("Aucune notification pour le moment.");
            emptyLabel.setPadding(new Insets(24, 16, 24, 16));
            emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");
            container.getChildren().add(emptyLabel);
        } else {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(Math.min(notifications.size() * 76.0, 300));
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

            VBox list = new VBox(0);
            for (int i = 0; i < notifications.size(); i++) {
                Notification notification = notifications.get(i);
                boolean approved = notification.getMessage() != null
                        && (notification.getMessage().contains("approuve") || notification.getMessage().contains("publie"));
                boolean refused = notification.getMessage() != null && notification.getMessage().contains("refuse");
                boolean isUnread = !notification.isRead();

                VBox item = new VBox(4);
                item.setPadding(new Insets(10, 16, 10, 16));
                String background = isUnread
                        ? (approved ? "#f0fdf4" : (refused ? "#fff1f2" : "#f3f1ff"))
                        : "#f8fafc";
                item.setStyle("-fx-background-color: " + background + ";");

                HBox row = new HBox(8);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label icon = new Label(approved ? "✓" : (refused ? "✕" : "•"));
                icon.setStyle("-fx-font-size: 15; -fx-text-fill: " +
                        (approved ? "#198754" : (refused ? "#d1435b" : "#5f49bf")) + ";");

                Label message = new Label(notification.getMessage() != null ? notification.getMessage() : "");
                message.setWrapText(true);
                String messageColor = isUnread
                        ? (approved ? "#166534" : (refused ? "#9f1239" : "#4c1d95"))
                        : "#64748b";
                message.setStyle("-fx-font-size: 12; -fx-text-fill: " + messageColor + ";");
                message.setMaxWidth(230);
                HBox.setHgrow(message, Priority.ALWAYS);

                HBox rightBox = new HBox(4);
                rightBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
                if (isUnread) {
                    Label badge = new Label("Nouveau");
                    badge.setStyle("-fx-background-color: #d1435b; -fx-text-fill: white;" +
                            "-fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6;" +
                            "-fx-padding: 1 5;");
                    rightBox.getChildren().add(badge);
                }
                row.getChildren().addAll(icon, message, rightBox);

                Label date = new Label(notification.getCreatedAt() != null ? notification.getCreatedAt().format(NOTIF_FMT) : "");
                date.setStyle("-fx-font-size: 10; -fx-text-fill: #94a3b8;");

                item.getChildren().addAll(row, date);
                list.getChildren().add(item);

                if (i < notifications.size() - 1) {
                    Separator separator = new Separator();
                    separator.setStyle("-fx-opacity: 0.3;");
                    list.getChildren().add(separator);
                }
            }

            scroll.setContent(list);
            container.getChildren().add(scroll);

            if (unreadCount > 0 && userId > 0) {
                Button markReadButton = new Button("Tout marquer comme lu");
                markReadButton.setMaxWidth(Double.MAX_VALUE);
                markReadButton.setStyle(
                    "-fx-background-color: #f8f7ff; -fx-text-fill: #5f49bf;" +
                    "-fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 10;" +
                    "-fx-cursor: hand; -fx-border-color: #e2e8f0;" +
                    "-fx-border-width: 1 0 0 0;");
                markReadButton.setOnAction(event -> {
                    notifService.markAllReadForUser(userId);
                    if (alertsPopup != null) {
                        alertsPopup.hide();
                    }
                    refreshBlogBadge();
                });
                container.getChildren().add(markReadButton);
            }
        }

        alertsPopup = new Popup();
        alertsPopup.getContent().add(container);
        alertsPopup.setAutoHide(true);

        double x = alertButton.localToScreen(alertButton.getBoundsInLocal()).getMaxX() - 340;
        double y = alertButton.localToScreen(alertButton.getBoundsInLocal()).getMaxY() + 8;
        alertsPopup.show(alertButton.getScene().getWindow(), x, y);

        if (unreadCount > 0 && userId > 0) {
            notifService.markAllReadForUser(userId);
            refreshBlogBadge();
        }
    }

    @FXML
    private void toggleAccountMenu() {
        if (accountMenu == null || accountButton == null) {
            return;
        }
        if (accountMenu.isShowing()) {
            accountMenu.hide();
        } else {
            accountMenu.show(accountButton, Side.BOTTOM, 0.0, 8.0);
        }
    }

    @FXML
    private void showProfile() {
        hideAccountMenuInstant();
        loadProfileSettingsView("Mon profil", false);
    }

    @FXML
    private void showSettings() {
        hideAccountMenuInstant();
        loadProfileSettingsView("Parametres du compte", true);
    }

    @FXML
    private void toggleGlobalAi() {
        runOnFxThread(() -> {
            try {
                ensureGlobalAiLoaded();
                if (globalAiOpen) {
                    closeGlobalAi();
                } else {
                    openGlobalAi();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Label placeholder = new Label("Failed to load Fahamni AI: " + e.getMessage());
                placeholder.getStyleClass().add("content-placeholder");
                contentPane.getChildren().clear();
                contentPane.getChildren().add(placeholder);
            }
        });
    }

    @FXML
    private void handleLogout() {
        stopVoiceAssistantWakeLoop();
        hideAccountMenuInstant();
        SessionCreationContext.clearPendingSelection();
        UserSession.clear();
        try {
            if (globalChatbotController != null) {
                globalChatbotController.shutdown();
            }
            Main.showLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void toggleVoiceAssistant() {
        boolean show = voiceAssistantPanel != null && !voiceAssistantPanel.isVisible();
        setVoiceAssistantVisible(show);
        if (show) {
            setVoiceAssistantStatus("Waiting for hey fahamni");
            setVoiceAssistantReply("Speak naturally. I can open your pages, start Voice Pass setup, or log you out.");
        }
    }

    @FXML
    private void handleVoiceAssistantTextCommand() {
        if (voiceAssistantCommandField == null) {
            return;
        }
        String command = voiceAssistantCommandField.getText();
        voiceAssistantCommandField.clear();
        processVoiceAssistantTranscript(command);
    }

    @FXML
    private void handleVoiceAssistantListen() {
        listenForManualVoiceCommand();
    }

    private void startVoiceAssistantWakeLoop() {
        if (voiceAssistantWakeLoopRunning) {
            return;
        }
        voiceAssistantWakeLoopRunning = true;
        Thread thread = new Thread(this::runVoiceAssistantWakeLoop, "fahamni-voice-assistant-wake-loop");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopVoiceAssistantWakeLoop() {
        voiceAssistantWakeLoopRunning = false;
    }

    private void runVoiceAssistantWakeLoop() {
        boolean setupErrorShown = false;
        while (voiceAssistantWakeLoopRunning && UserSession.hasCurrentUser()) {
            if (voiceAssistantListenBusy) {
                sleepVoiceAssistantLoop(250);
                continue;
            }

            voiceAssistantListenBusy = true;
            LocalSpeechToTextService.SpeechResult wakeResult;
            try {
                wakeResult = localSpeechToTextService.listenOnce(java.time.Duration.ofSeconds(2), VOICE_WAKE_GRAMMAR);
            } finally {
                voiceAssistantListenBusy = false;
            }

            if (!voiceAssistantWakeLoopRunning || !UserSession.hasCurrentUser()) {
                break;
            }
            if (!wakeResult.success()) {
                if (!setupErrorShown && shouldShowVoiceAssistantSetupError(wakeResult.message())) {
                    setupErrorShown = true;
                    Platform.runLater(() -> {
                        setVoiceAssistantVisible(true);
                        setVoiceAssistantStatus("Voice setup needed");
                        setVoiceAssistantReply(wakeResult.message());
                    });
                }
                sleepVoiceAssistantLoop(300);
                continue;
            }

            VoiceAssistantInterpreter.Interpretation wakeInterpretation =
                voiceAssistantInterpreter.interpret(wakeResult.transcript(), false);
            if (wakeInterpretation.intent() != VoiceAssistantIntent.WAKE) {
                continue;
            }

            Platform.runLater(() -> {
                setVoiceAssistantVisible(true);
                setVoiceAssistantStatus("Listening");
                setVoiceAssistantReply("Go ahead. You have 4 seconds.");
            });

            voiceAssistantListenBusy = true;
            LocalSpeechToTextService.SpeechResult commandResult;
            try {
                commandResult = localSpeechToTextService.listenOnce(java.time.Duration.ofSeconds(4), VOICE_COMMAND_GRAMMAR);
            } finally {
                voiceAssistantListenBusy = false;
            }

            if (!voiceAssistantWakeLoopRunning || !UserSession.hasCurrentUser()) {
                break;
            }
            if (!commandResult.success()) {
                Platform.runLater(() -> {
                    setVoiceAssistantStatus("No response");
                    setVoiceAssistantReply("No response. I am back in standby.");
                });
                sleepVoiceAssistantLoop(700);
                continue;
            }

            Platform.runLater(() -> {
                voiceAssistantWaitingForCommand = true;
                setVoiceAssistantStatus("Heard");
                setVoiceAssistantReply(commandResult.message());
                processVoiceAssistantTranscript(commandResult.transcript());
            });
            sleepVoiceAssistantLoop(700);
        }
    }

    private void listenForManualVoiceCommand() {
        if (voiceAssistantListenBusy) {
            setVoiceAssistantVisible(true);
            setVoiceAssistantStatus("Busy");
            setVoiceAssistantReply("I am already listening.");
            return;
        }

        setVoiceAssistantVisible(true);
        setVoiceAssistantStatus("Listening");
        setVoiceAssistantReply("Go ahead. You have 4 seconds.");

        Task<LocalSpeechToTextService.SpeechResult> listenTask = new Task<>() {
            @Override
            protected LocalSpeechToTextService.SpeechResult call() {
                voiceAssistantListenBusy = true;
                try {
                    return localSpeechToTextService.listenOnce(java.time.Duration.ofSeconds(4), VOICE_COMMAND_GRAMMAR);
                } finally {
                    voiceAssistantListenBusy = false;
                }
            }
        };
        listenTask.setOnSucceeded(event -> {
            LocalSpeechToTextService.SpeechResult result = listenTask.getValue();
            if (!result.success()) {
                setVoiceAssistantStatus("No response");
                setVoiceAssistantReply("No response. I am back in standby.");
                return;
            }
            voiceAssistantWaitingForCommand = true;
            setVoiceAssistantStatus("Heard");
            setVoiceAssistantReply(result.message());
            processVoiceAssistantTranscript(result.transcript());
        });
        listenTask.setOnFailed(event -> {
            voiceAssistantListenBusy = false;
            setVoiceAssistantStatus("Error");
            setVoiceAssistantReply("Offline listening failed. I am back in standby.");
        });

        Thread thread = new Thread(listenTask, "fahamni-voice-assistant-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean shouldShowVoiceAssistantSetupError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("Vosk")
            || message.contains("Missing offline speech model")
            || message.contains("No compatible microphone");
    }

    private void sleepVoiceAssistantLoop(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadView(String fxmlFile, String title) {
        hideAccountMenuInstant();
        try {
            closeGlobalAiIfOpen();
            ApplicationState.getInstance().setCurrentView(title);
            if ("FrontMatiereView.fxml".equals(fxmlFile) || !title.startsWith("Cours")) {
                ApplicationState.getInstance().clearCurrentMatiere();
            }
            Node view = SceneManager.loadView(Main.class, SceneManager.frontofficeView(fxmlFile));
            displayView(view, title);
        } catch (Exception e) {
            e.printStackTrace();
            Label placeholder = new Label("View not implemented yet: " + fxmlFile);
            placeholder.getStyleClass().add("content-placeholder");
            displayView(placeholder, title);
        }
    }

    private void loadProfileSettingsView(String title, boolean settingsMode) {
        closeGlobalAiIfOpen();
        ApplicationState.getInstance().setCurrentView(title);
        ApplicationState.getInstance().clearCurrentMatiere();
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(SceneManager.frontofficeView("ProfileSettingsView.fxml")));
            Node view = loader.load();
            ProfileSettingsController controller = loader.getController();
            controller.configure(settingsMode);
            controller.setOnProfileUpdated(updatedUser -> refreshCurrentUserSummary());
            controller.setOnAccountDeleted(this::handleLogout);
            displayView(view, title);
        } catch (IOException e) {
            e.printStackTrace();
            Label placeholder = new Label("Impossible de charger le panneau de compte.");
            placeholder.getStyleClass().add("content-placeholder");
            displayView(placeholder, title);
        }
    }

    private void displayView(Node view, String title) {
        contentPane.getChildren().clear();
        if (view instanceof Region region) {
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        AnchorPane.setTopAnchor(view, 0.0);
        AnchorPane.setBottomAnchor(view, 0.0);
        AnchorPane.setLeftAnchor(view, 0.0);
        AnchorPane.setRightAnchor(view, 0.0);
        contentPane.getChildren().add(view);
        Platform.runLater(() -> FrontOfficeMotion.installInteractiveMotion(rootPane));
        playViewTransition(view);
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    private void openDestination(FrontOfficeNavigation.Destination destination) {
        if (destination == null) {
            return;
        }

        switch (destination) {
            case DASHBOARD -> showDashboard();
            case RESERVATIONS -> showReservations();
            case CALENDAR -> showSeances();
            case INFRASTRUCTURE -> showSallesEquipements();
            case PLANNER -> showPlanner();
            case QUIZ -> showQuiz();
            case BLOG -> showBlog();
            case ABOUT -> showAbout();
            case PROFILE -> showProfile();
            case SETTINGS -> showSettings();
        }
    }

    private void processVoiceAssistantTranscript(String transcript) {
        setVoiceAssistantVisible(true);
        VoiceAssistantInterpreter.Interpretation interpretation =
            voiceAssistantInterpreter.interpret(transcript, voiceAssistantWaitingForCommand);
        setVoiceAssistantReply(interpretation.reply());

        if (interpretation.intent() == VoiceAssistantIntent.WAKE) {
            voiceAssistantWaitingForCommand = true;
            setVoiceAssistantStatus("Listening");
            return;
        }

        if (interpretation.intent() == VoiceAssistantIntent.UNKNOWN) {
            setVoiceAssistantStatus("Try again");
            return;
        }

        voiceAssistantWaitingForCommand = false;
        setVoiceAssistantStatus("Command");
        executeVoiceAssistantIntent(interpretation.intent());
    }

    private void executeVoiceAssistantIntent(VoiceAssistantIntent intent) {
        switch (intent) {
            case HELP -> setVoiceAssistantReply("Try: open profile, open security, save my voice, open planner, open quiz, open blog, open calendar, find tutor, or logout.");
            case CANCEL -> setVoiceAssistantVisible(false);
            case GO_BACK, OPEN_DASHBOARD -> showDashboard();
            case OPEN_PROFILE -> showProfile();
            case OPEN_SETTINGS, OPEN_SECURITY, ENROLL_VOICE_PASS, REMOVE_VOICE_PASS -> showSettings();
            case OPEN_RESERVATIONS -> showReservations();
            case OPEN_CALENDAR -> showSeances();
            case OPEN_PLANNER -> showPlanner();
            case OPEN_MESSENGER -> showCallLab();
            case OPEN_QUIZ -> showQuiz();
            case OPEN_BLOG -> showBlog();
            case OPEN_ABOUT -> showAbout();
            case START_VOICE_LOGIN -> setVoiceAssistantReply("Voice login is available on the login screen. Log out, enter your email, then use Voice Pass.");
            case LOGOUT -> handleLogout();
            case WAKE, UNKNOWN -> {
            }
        }
    }

    private void setVoiceAssistantVisible(boolean visible) {
        if (voiceAssistantPanel != null) {
            voiceAssistantPanel.setVisible(visible);
            voiceAssistantPanel.setManaged(visible);
        }
        if (voiceAssistantBubbleButton != null) {
            voiceAssistantBubbleButton.setVisible(!visible);
            voiceAssistantBubbleButton.setManaged(!visible);
        }
        if (!visible) {
            voiceAssistantWaitingForCommand = false;
        }
    }

    private void setVoiceAssistantStatus(String status) {
        if (voiceAssistantStatusLabel != null) {
            voiceAssistantStatusLabel.setText(status);
        }
    }

    private void setVoiceAssistantReply(String reply) {
        if (voiceAssistantReplyLabel != null) {
            voiceAssistantReplyLabel.setText(reply);
        }
    }

    private void applyThemeMode() {
        FrontOfficeThemePreference.apply(rootPane);
        refreshAccountMenuTheme();
        if (alertsPopup != null && alertsPopup.isShowing()) {
            alertsPopup.hide();
        }
    }

    private void refreshAccountMenuTheme() {
        if (accountMenu == null) {
            return;
        }

        accountMenu.getStyleClass().remove("light-context-menu");
        if (FrontOfficeThemePreference.isLightMode()) {
            accountMenu.getStyleClass().add("light-context-menu");
        }
    }

    private void playViewTransition(Node view) {
        if (view == null) {
            return;
        }

        view.setOpacity(0.0);

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(320), view);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        fadeTransition.play();
    }

    private void installContentClip() {
        if (contentPane == null) {
            return;
        }

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contentPane.widthProperty());
        clip.heightProperty().bind(contentPane.heightProperty());
        contentPane.setClip(clip);
    }

    private void refreshCurrentUserSummary() {
        try {
            applyAvatar(profileAvatarLabel, userAccountService.getCurrentAvatarPath(), 34);
            profileNameLabel.setText(UserSession.getDisplayName());
            profileRoleLabel.setText(UserSession.getRoleLabel());
            refreshFrontOfficeAccess();
        } catch (Exception e) {
            System.err.println("refreshCurrentUserSummary: " + e.getMessage());
        }
    }

    private void refreshFrontOfficeAccess() {
        boolean showInfrastructure = canAccessSallesEquipements();
        if (sallesEquipementsButton != null) {
            sallesEquipementsButton.setManaged(showInfrastructure);
            sallesEquipementsButton.setVisible(showInfrastructure);
            if (!showInfrastructure) {
                sallesEquipementsButton.getStyleClass().remove("active");
            }
        }
    }

    private boolean canAccessSallesEquipements() {
        return !UserSession.isCurrentStudent();
    }

    private void initializeAccountMenu() {
        if (accountButton == null) {
            return;
        }

        MenuItem profileItem = new MenuItem("Mon profil");
        profileItem.getStyleClass().add("front-account-menu-item");
        profileItem.setOnAction(event -> showProfile());

        MenuItem settingsItem = new MenuItem("Parametres");
        settingsItem.getStyleClass().add("front-account-menu-item");
        settingsItem.setOnAction(event -> showSettings());

        MenuItem logoutItem = new MenuItem("Deconnexion");
        logoutItem.getStyleClass().add("account-menu-danger");
        logoutItem.setOnAction(event -> handleLogout());

        accountMenu = new ContextMenu(profileItem, settingsItem, new SeparatorMenuItem(), logoutItem);
        accountMenu.getStyleClass().add("front-navbar-context-menu");
        accountMenu.setAutoHide(true);
        accountMenu.setAutoFix(true);
        refreshAccountMenuTheme();
    }

    private void hideAccountMenuInstant() {
        if (accountMenu != null) {
            accountMenu.hide();
        }
    }

    private void ensureGlobalAiLoaded() throws Exception {
        if (globalAiLoaded) {
            if (globalChatbotController != null) {
                globalChatbotController.refreshContextBadge();
            }
            return;
        }
        if (globalAiContent == null) {
            return;
        }

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/tn/esprit/fahamni/views/GlobalChatbotView.fxml"));
        Node chatbotView = loader.load();
        globalChatbotController = loader.getController();

        globalAiContent.getChildren().setAll(chatbotView);
        AnchorPane.setTopAnchor(chatbotView, 0.0);
        AnchorPane.setBottomAnchor(chatbotView, 0.0);
        AnchorPane.setLeftAnchor(chatbotView, 0.0);
        AnchorPane.setRightAnchor(chatbotView, 0.0);

        globalAiLoaded = true;
    }

    private void openGlobalAi() {
        if (globalAiPanel == null || globalAiScrim == null || globalAiFab == null) {
            return;
        }

        globalAiOpen = true;
        if (globalChatbotController != null) {
            globalChatbotController.refreshContextBadge();
        }

        globalAiPanel.setManaged(true);
        globalAiPanel.setVisible(true);
        globalAiPanel.setTranslateX(GLOBAL_AI_PANEL_WIDTH);

        globalAiScrim.setManaged(true);
        globalAiScrim.setVisible(true);
        globalAiScrim.setMouseTransparent(false);
        globalAiScrim.setOnMouseClicked(event -> closeGlobalAi());
        globalAiScrim.setOpacity(0);

        globalAiFab.setText("Close Guide");
        setAiButtonActive(true);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(240), globalAiPanel);
        slideIn.setFromX(GLOBAL_AI_PANEL_WIDTH);
        slideIn.setToX(0);
        slideIn.play();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), globalAiScrim);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void closeGlobalAi() {
        if (globalAiPanel == null || globalAiScrim == null || globalAiFab == null) {
            globalAiOpen = false;
            return;
        }

        globalAiOpen = false;
        globalAiFab.setText("Ask Fahamni");
        setAiButtonActive(false);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(220), globalAiPanel);
        slideOut.setFromX(globalAiPanel.getTranslateX());
        slideOut.setToX(GLOBAL_AI_PANEL_WIDTH);
        slideOut.setOnFinished(event -> {
            globalAiPanel.setVisible(false);
            globalAiPanel.setManaged(false);
        });
        slideOut.play();

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), globalAiScrim);
        fadeOut.setFromValue(globalAiScrim.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            globalAiScrim.setVisible(false);
            globalAiScrim.setManaged(false);
            globalAiScrim.setMouseTransparent(true);
        });
        fadeOut.play();
    }

    private void closeGlobalAiIfOpen() {
        if (globalAiOpen) {
            closeGlobalAi();
        }
    }

    private void setAiButtonActive(boolean active) {
        if (aiButton == null) {
            return;
        }
        aiButton.getStyleClass().remove("active");
        if (active) {
            aiButton.getStyleClass().add("active");
        }
    }

    private void setActiveButton(Button activeButton) {
        removeActiveClass(dashboardButton);
        removeActiveClass(reservationsButton);
        removeActiveClass(seancesButton);
        removeActiveClass(sallesEquipementsButton);
        removeActiveClass(plannerButton);
        removeActiveClass(coursButton);
        removeActiveClass(callLabButton);
        removeActiveClass(quizButton);
        removeActiveClass(blogButton);
        removeActiveClass(aboutButton);
        removeActiveClass(aiButton);

        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    private void removeActiveClass(Button button) {
        if (button != null) {
            button.getStyleClass().remove("active");
        }
    }

    private void applyAvatar(Label label, Path avatarPath, double size) {
        if (label == null) {
            return;
        }

        if (avatarPath == null) {
            label.setGraphic(null);
            label.setText(UserSession.getInitials());
            return;
        }

        Image image = new Image(avatarPath.toUri().toString(), size, size, false, true);
        if (image.isError()) {
            label.setGraphic(null);
            label.setText(UserSession.getInitials());
            return;
        }

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        Circle clip = new Circle(size / 2);
        clip.setCenterX(size / 2);
        clip.setCenterY(size / 2);
        imageView.setClip(clip);

        label.setText("");
        label.setGraphic(imageView);
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
