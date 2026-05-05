package tn.esprit.fahamni.Models;

import java.sql.Timestamp;

public class Matiere {

    private int id;
    private String titre;
    private String description;
    private String structure;
    private Timestamp createdAt;
    private String coverImage;

    public Matiere() {
    }

    public Matiere(String titre, String description, String structure, Timestamp createdAt, String coverImage) {
        this.titre = titre;
        this.description = description;
        this.structure = structure;
        this.createdAt = createdAt;
        this.coverImage = coverImage;
    }

    public Matiere(int id, String titre, String description, String structure, Timestamp createdAt, String coverImage) {
        this.id = id;
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

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }
}
