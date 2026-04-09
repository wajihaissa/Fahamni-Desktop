package tn.esprit.fahamni.Models;

import java.util.ArrayList;
import java.util.List;

public class Conversation {

    private final String partnerName;
    private final String status;
    private final List<ChatMessage> messages;

    public Conversation(String partnerName, String status, List<ChatMessage> messages) {
        this.partnerName = partnerName;
        this.status = status;
        this.messages = new ArrayList<>(messages);
    }

    public String getPartnerName() {
        return partnerName;
    }

    public String getStatus() {
        return status;
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }
}

