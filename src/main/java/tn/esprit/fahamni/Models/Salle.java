package tn.esprit.fahamni.Models;

public class Salle {

    private int idSalle;
    private String nom;
    private int capacite;
    private String localisation;
    private String typeSalle;
    private String etat;
    private String description;

    public Salle(int idSalle, String nom, int capacite, String localisation,
                 String typeSalle, String etat, String description) {
        this.idSalle = idSalle;
        this.nom = nom;
        this.capacite = capacite;
        this.localisation = localisation;
        this.typeSalle = typeSalle;
        this.etat = etat;
        this.description = description;
    }

    public int getIdSalle() {
        return idSalle;
    }

    public void setIdSalle(int idSalle) {
        this.idSalle = idSalle;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public int getCapacite() {
        return capacite;
    }

    public void setCapacite(int capacite) {
        this.capacite = capacite;
    }

    public String getLocalisation() {
        return localisation;
    }

    public void setLocalisation(String localisation) {
        this.localisation = localisation;
    }

    public String getTypeSalle() {
        return typeSalle;
    }

    public void setTypeSalle(String typeSalle) {
        this.typeSalle = typeSalle;
    }

    public String getEtat() {
        return etat;
    }

    public void setEtat(String etat) {
        this.etat = etat;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
