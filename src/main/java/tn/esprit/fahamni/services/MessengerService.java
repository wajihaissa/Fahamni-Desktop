package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.ChatMessage;
import tn.esprit.fahamni.Models.Conversation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessengerService {

    private final Map<String, Conversation> conversations = createMockConversations();

    public List<String> getConversationPartners() {
        return List.copyOf(conversations.keySet());
    }

    public Conversation getConversation(String partnerName) {
        return conversations.get(partnerName);
    }

    public ChatMessage sendMessage(String partnerName, String content) {
        if (partnerName == null || content == null || content.trim().isEmpty()) {
            return null;
        }

        Conversation conversation = conversations.get(partnerName);
        if (conversation == null) {
            return null;
        }

        ChatMessage message = new ChatMessage("You", content.trim(), true);
        conversation.addMessage(message);
        return message;
    }

    private Map<String, Conversation> createMockConversations() {
        Map<String, Conversation> data = new LinkedHashMap<>();
        data.put("Ahmed Ben Ali", new Conversation(
            "Ahmed Ben Ali",
            "Active now",
            List.of(
                new ChatMessage("Ahmed Ben Ali", "Hello! How can I help you with your studies?", false),
                new ChatMessage("You", "Hi! I need help with algebra problems.", true),
                new ChatMessage("Ahmed Ben Ali", "Sure! I'd be happy to help. What specific problems are you working on?", false),
                new ChatMessage("You", "I'm having trouble with quadratic equations.", true),
                new ChatMessage("Ahmed Ben Ali", "Quadratic equations are a great topic! Let me explain the formula: axÂ² + bx + c = 0", false)
            )
        ));
        data.put("Sarah Mansour", new Conversation(
            "Sarah Mansour",
            "Active now",
            List.of(
                new ChatMessage("Sarah Mansour", "Do you need help with mechanics revision?", false),
                new ChatMessage("You", "Yes, especially Newton's laws.", true)
            )
        ));
        data.put("Mohamed Trabelsi", new Conversation(
            "Mohamed Trabelsi",
            "Away",
            List.of(
                new ChatMessage("Mohamed Trabelsi", "I shared new chemistry notes for this week.", false)
            )
        ));
        data.put("Leila Khemiri", new Conversation(
            "Leila Khemiri",
            "Online",
            List.of(
                new ChatMessage("Leila Khemiri", "We can review your writing exercises tomorrow.", false)
            )
        ));
        return data;
    }
}

