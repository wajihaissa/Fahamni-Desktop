package tn.esprit.fahamni.Models;

public class ChatMessage {

    private final String sender;
    private final String content;
    private final boolean sentByCurrentUser;

    public ChatMessage(String sender, String content, boolean sentByCurrentUser) {
        this.sender = sender;
        this.content = content;
        this.sentByCurrentUser = sentByCurrentUser;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public boolean isSentByCurrentUser() {
        return sentByCurrentUser;
    }
}

