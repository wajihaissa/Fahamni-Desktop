package tn.esprit.fahamni.controllers;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.ai.GeminiService;
import tn.esprit.fahamni.utils.ApplicationState;

public class GlobalChatbotController implements Initializable {

    private final GeminiService geminiService = new GeminiService();
    private final ExecutorService chatExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GlobalChatbotWorker");
        t.setDaemon(true);
        return t;
    });

    @FXML
    private VBox chatBox;

    @FXML
    private TextField messageInput;

    @FXML
    private Button sendButton;

    @FXML
    private ScrollPane chatScrollPane;

    @FXML
    private Label contextBadge;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addMessageToUI("Bonjour, je suis le guide Fahamni. Je peux vous aider a naviguer dans l'application.", false);
        refreshContextBadge();

        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> runOnFxThread(() -> {
            if (chatScrollPane != null) {
                chatScrollPane.setVvalue(1.0);
            }
        }));
    }

    @FXML
    private void handleSendAction() {
        String text = messageInput.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String cleanedText = text.trim();
        addMessageToUI(cleanedText, true);
        runOnFxThread(() -> messageInput.clear());
        refreshContextBadge();

        HBox typingRow = addMessageRow("Je regarde ou vous etes dans l'application...", false);
        String prompt = buildPrompt(cleanedText);

        chatExecutor.submit(() -> {
            try {
                String response = geminiService.askGemini(prompt);
                runOnFxThread(() -> {
                    chatBox.getChildren().remove(typingRow);
                    addMessageToUI(response, false);
                });
            } catch (Exception e) {
                runOnFxThread(() -> {
                    chatBox.getChildren().remove(typingRow);
                    addMessageToUI("Erreur lors de l'appel Gemini: " + e.getMessage(), false);
                });
            }
        });
    }

    public void refreshContextBadge() {
        ApplicationState state = ApplicationState.getInstance();
        String currentView = state.getCurrentView();
        Matiere currentMatiere = state.getCurrentMatiere();

        StringBuilder badge = new StringBuilder("Vue: ");
        badge.append(currentView == null || currentView.isBlank() ? "Inconnue" : currentView);
        if (currentMatiere != null && currentMatiere.getTitre() != null && !currentMatiere.getTitre().isBlank()) {
            badge.append(" | Cours: ").append(currentMatiere.getTitre());
        }

        runOnFxThread(() -> contextBadge.setText(badge.toString()));
    }

    public void shutdown() {
        if (!chatExecutor.isShutdown()) {
            chatExecutor.shutdown();
        }
    }

    private String buildPrompt(String userMessage) {
        ApplicationState state = ApplicationState.getInstance();
        String currentView = state.getCurrentView();
        Matiere currentMatiere = state.getCurrentMatiere();

        StringBuilder prompt = new StringBuilder();
        prompt.append("System: You are Fahamni's Global Assistant, a general in-app guide and guidance counselor. ");
        prompt.append("Help users understand features, navigation, and what they can do on the screen they are viewing. ");
        prompt.append("Do not behave like the course teaching assistant unless the user is explicitly asking about navigation within a course. ");
        prompt.append("If a user asks for course-content teaching or answers grounded in course materials, tell them the Course AI is better for that. ");
        prompt.append("Keep answers concise, practical, and oriented around using the Fahamni application.\n");
        prompt.append("System: The user is currently viewing: [")
            .append(currentView == null || currentView.isBlank() ? "Unknown View" : currentView)
            .append("].\n");
        if (currentMatiere != null && currentMatiere.getTitre() != null && !currentMatiere.getTitre().isBlank()) {
            prompt.append("System: The currently selected course is: [")
                .append(currentMatiere.getTitre())
                .append("].\n");
        }
        prompt.append("User: ").append(userMessage);
        return prompt.toString();
    }

    private void addMessageToUI(String text, boolean isUser) {
        runOnFxThread(() -> addMessageRow(text, isUser));
    }

    private HBox addMessageRow(String text, boolean isUser) {
        HBox row = new HBox();
        row.setFillHeight(true);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(320);
        bubble.getStyleClass().add(isUser ? "message-bubble-user" : "global-message-bubble-ai");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (isUser) {
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.getChildren().addAll(bubble, spacer);
        }

        chatBox.getChildren().add(row);
        return row;
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
