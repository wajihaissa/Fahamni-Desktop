package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.ChatMessage;
import tn.esprit.fahamni.Models.Conversation;
import tn.esprit.fahamni.services.MessengerService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class MessengerController {

    private final MessengerService messengerService = new MessengerService();

    @FXML
    private ListView<String> conversationsListView;

    @FXML
    private VBox chatHeader;

    @FXML
    private Label chatPartnerLabel;

    @FXML
    private Label chatStatusLabel;

    @FXML
    private ScrollPane messagesScrollPane;

    @FXML
    private VBox messagesContainer;

    @FXML
    private TextField messageTextField;

    @FXML
    private Button sendButton;

    @FXML
    private void initialize() {
        conversationsListView.getItems().setAll(messengerService.getConversationPartners());

        conversationsListView.setCellFactory(param -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item);
                    }
                }
            };
            cell.getStyleClass().add("conversation-cell");
            return cell;
        });

        conversationsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                displayConversation(messengerService.getConversation(newSelection));
            }
        });

        if (!conversationsListView.getItems().isEmpty()) {
            conversationsListView.getSelectionModel().select(0);
        }
    }

    private void displayConversation(Conversation conversation) {
        if (conversation == null) {
            return;
        }

        chatPartnerLabel.setText(conversation.getPartnerName());
        chatStatusLabel.setText(conversation.getStatus());
        messagesContainer.getChildren().clear();

        for (ChatMessage message : conversation.getMessages()) {
            renderMessage(message);
        }

        messagesScrollPane.setVvalue(1.0);
    }

    private void renderMessage(ChatMessage message) {
        HBox messageBox = new HBox();
        messageBox.setSpacing(10);
        messageBox.setPadding(new Insets(5, 0, 5, 0));
        messageBox.setAlignment(message.isSentByCurrentUser() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBox.getStyleClass().add("message-row");

        VBox messageBubble = new VBox();
        messageBubble.setSpacing(2);

        if (!message.isSentByCurrentUser()) {
            Label senderLabel = new Label(message.getSender());
            senderLabel.getStyleClass().add("sender-label");
            messageBubble.getChildren().add(senderLabel);
        }

        Text messageText = new Text(message.getContent());
        messageText.setWrappingWidth(300);
        messageText.getStyleClass().addAll(
            "message-text",
            message.isSentByCurrentUser() ? "message-text-sent" : "message-text-received"
        );

        TextFlow textFlow = new TextFlow(messageText);
        textFlow.getStyleClass().addAll(
            "message-bubble",
            message.isSentByCurrentUser() ? "message-sent" : "message-received"
        );

        messageBubble.getChildren().add(textFlow);
        messageBox.getChildren().add(messageBubble);
        messagesContainer.getChildren().add(messageBox);
    }

    @FXML
    private void sendMessage() {
        String message = messageTextField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        String selectedPartner = conversationsListView.getSelectionModel().getSelectedItem();
        ChatMessage sentMessage = messengerService.sendMessage(selectedPartner, message);
        if (sentMessage == null) {
            return;
        }

        renderMessage(sentMessage);
        messageTextField.clear();
        messagesScrollPane.setVvalue(1.0);
    }
}

