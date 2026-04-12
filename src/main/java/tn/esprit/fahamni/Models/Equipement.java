package tn.esprit.fahamni.Models;

public class Equipement {

    private int idEquipement;
    private String nom;
    private String typeEquipement;
    private int quantiteDisponible;
    private String etat;
    private String description;

    public Equipement(int idEquipement, String nom, String typeEquipement,
                      int quantiteDisponible, String etat, String description) {
        this.idEquipement = idEquipement;
        this.nom = nom;
        this.typeEquipement = typeEquipement;
        this.quantiteDisponible = quantiteDisponible;
        this.etat = etat;
        this.description = description;
    }

    public int getIdEquipement() {
        return idEquipement;
    }

    public void setIdEquipement(int idEquipement) {
        this.idEquipement = idEquipement;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getTypeEquipement() {
        return typeEquipement;
    }

    public void setTypeEquipement(String typeEquipement) {
        this.typeEquipement = typeEquipement;
    }

    public int getQuantiteDisponible() {
        return quantiteDisponible;
    }

    public void setQuantiteDisponible(int quantiteDisponible) {
        this.quantiteDisponible = quantiteDisponible;
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
