package tn.esprit.fahamni.controllers;

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
import tn.esprit.fahamni.services.ai.CourseContextBuilder;
import tn.esprit.fahamni.services.ai.GeminiService;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatbotController implements Initializable {

    private final GeminiService geminiService = new GeminiService();
    private final CourseContextBuilder courseContextBuilder = new CourseContextBuilder();
    private String courseContext = "";
    private boolean isContextLoaded = false;

    @FXML
    private VBox chatBox;

    @FXML
    private TextField messageInput;

    @FXML
    private Button sendButton;

    @FXML
    private ScrollPane chatScrollPane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addMessageToUI("Bonjour, je suis Fahamni AI. Comment puis-je vous aider aujourd'hui ?", false);

        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (chatScrollPane != null) {
                chatScrollPane.setVvalue(1.0);
            }
        });
    }

    @FXML
    private void handleSendAction() {
        String text = messageInput.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String cleanedText = text.trim();
        addMessageToUI(cleanedText, true);
        messageInput.clear();

        HBox typingRow = addMessageRow("Typing...", false);

        Thread worker = new Thread(() -> {
            try {
                String response;
                if (courseContext != null && !courseContext.isBlank()) {
                    response = geminiService.askGeminiWithContext(cleanedText, courseContext);
                } else {
                    response = geminiService.askGemini(cleanedText);
                }
                Platform.runLater(() -> {
                    chatBox.getChildren().remove(typingRow);
                    addMessageToUI(response, false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    chatBox.getChildren().remove(typingRow);
                    addMessageToUI("Erreur lors de l'appel Gemini: " + e.getMessage(), false);
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    public void setMatiere(Matiere matiere) {
        if (matiere == null) {
            return;
        }

        addMessageToUI(
            "Fahamni AI is reading the course materials (PDFs, Links, Videos). This might take a moment...",
            false
        );

        isContextLoaded = false;
        courseContextBuilder.buildCourseContext(matiere)
            .thenAccept(result -> {
                courseContext = result == null ? "" : result;
                isContextLoaded = true;
                Platform.runLater(() -> addMessageToUI("Materials loaded! What would you like to know?", false));
            })
            .exceptionally(ex -> {
                System.err.println("Course context ingestion failed: " + ex.getMessage());
                ex.printStackTrace();
                isContextLoaded = false;
                courseContext = "";
                Platform.runLater(() ->
                    addMessageToUI("Failed to load course materials. I can still answer general questions.", false)
                );
                return null;
            });
    }

    public void addMessageToUI(String text, boolean isUser) {
        addMessageRow(text, isUser);
    }

    private HBox addMessageRow(String text, boolean isUser) {
        HBox row = new HBox();
        row.setFillHeight(true);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(520);
        bubble.getStyleClass().add(isUser ? "message-bubble-user" : "message-bubble-ai");

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
}
