package tn.esprit.fahamni.Models;

public class Chapter {

    private int id;
    private String titre;
    private int matiereId;

    public Chapter() {
    }

    public Chapter(String titre, int matiereId) {
        this.titre = titre;
        this.matiereId = matiereId;
    }

    public Chapter(int id, String titre, int matiereId) {
        this.id = id;
        this.titre = titre;
        this.matiereId = matiereId;
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

    public int getMatiereId() {
        return matiereId;
    }

    public void setMatiereId(int matiereId) {
        this.matiereId = matiereId;
    }
}
