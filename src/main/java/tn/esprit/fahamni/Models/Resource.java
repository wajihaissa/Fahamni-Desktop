package tn.esprit.fahamni.Models;

public class Resource {

    private int id;
    private String titre;
    private String type;
    private String filepath;
    private String link;
    private int sectionId;

    public Resource() {
    }

    public Resource(String titre, String type, String filepath, String link, int sectionId) {
        this.titre = titre;
        this.type = type;
        this.filepath = filepath;
        this.link = link;
        this.sectionId = sectionId;
    }

    public Resource(int id, String titre, String type, String filepath, String link, int sectionId) {
        this.id = id;
        this.titre = titre;
        this.type = type;
        this.filepath = filepath;
        this.link = link;
        this.sectionId = sectionId;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public int getSectionId() {
        return sectionId;
    }

    public void setSectionId(int sectionId) {
        this.sectionId = sectionId;
    }
}
