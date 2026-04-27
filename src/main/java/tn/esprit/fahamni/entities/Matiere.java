package tn.esprit.fahamni.entities;

import java.time.LocalDateTime;

public class Matiere {

    private int id;
    private String titre;
    private String description;
    private String structure;
    private LocalDateTime createdAt;
    private String coverImage;

    public Matiere() {
    }

    public Matiere(int id, String titre, String description, String structure, LocalDateTime createdAt, String coverImage) {
        this.id = id;
        this.titre = titre;
        this.description = description;
        this.structure = structure;
        this.createdAt = createdAt;
        this.coverImage = coverImage;
    }

    public Matiere(String titre, String description, String structure, LocalDateTime createdAt, String coverImage) {
        this.titre = titre;
        this.description = description;
        this.structure = structure;
        this.createdAt = createdAt;
        this.coverImage = coverImage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStructure() {
        return structure;
    }

    public void setStructure(String structure) {
        this.structure = structure;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    @Override
    public String toString() {
        return "Matiere{" +
            "id=" + id +
            ", titre='" + titre + '\'' +
            ", createdAt=" + createdAt +
            ", coverImage='" + coverImage + '\'' +
            '}';
    }
}
