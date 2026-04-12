package tn.esprit.fahamni.Models;

public class Section {

    private int id;
    private String titre;
    private int chapterId;

    public Section() {
    }

    public Section(String titre, int chapterId) {
        this.titre = titre;
        this.chapterId = chapterId;
    }

    public Section(int id, String titre, int chapterId) {
        this.id = id;
        this.titre = titre;
        this.chapterId = chapterId;
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

    public int getChapterId() {
        return chapterId;
    }

    public void setChapterId(int chapterId) {
        this.chapterId = chapterId;
    }
}
