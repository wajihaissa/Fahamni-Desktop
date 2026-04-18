package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class Interaction {

    private int id;
    private String type;
    private String commentaire;
    private LocalDateTime createdAt;
    private String createdBy;

    public Interaction(int id, String type, String commentaire,
                       LocalDateTime createdAt, String createdBy) {
        this.id = id;
        this.type = type;
        this.commentaire = commentaire;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
