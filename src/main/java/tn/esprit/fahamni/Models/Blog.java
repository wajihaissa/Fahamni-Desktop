package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class Blog {

    private int id;
    private String titre;
    private String content;
    private String image;
    private LocalDateTime createdAt;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private int publisherId; // id du user qui a créé l'article

    public Blog(int id, String titre, String content, String image, LocalDateTime createdAt,
                String publishedBy, LocalDateTime publishedAt) {
        this.id = id;
        this.titre = titre;
        this.content = content;
        this.image = image;
        this.createdAt = createdAt;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
        this.publisherId = 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public int getPublisherId() { return publisherId; }
    public void setPublisherId(int publisherId) { this.publisherId = publisherId; }
}
