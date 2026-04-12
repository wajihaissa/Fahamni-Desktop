package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class Notification {

    private int id;
    private int recipientId;   // 0 = admin, >0 = user_id du tuteur
    private int blogId;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;

    public Notification() {}

    public Notification(int recipientId, int blogId, String message) {
        this.recipientId = recipientId;
        this.blogId = blogId;
        this.message = message;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRecipientId() { return recipientId; }
    public void setRecipientId(int recipientId) { this.recipientId = recipientId; }

    public int getBlogId() { return blogId; }
    public void setBlogId(int blogId) { this.blogId = blogId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
