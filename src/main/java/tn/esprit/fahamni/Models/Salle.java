package tn.esprit.fahamni.Models;

import java.time.LocalDate;

public class Salle {

    private int idSalle;
    private String nom;
    private int capacite;
    private String localisation;
    private String typeSalle;
    private String etat;
    private String description;
    private String batiment;
    private Integer etage;
    private String typeDisposition;
    private boolean accesHandicape;
    private String statutDetaille;
    private LocalDate dateDerniereMaintenance;

    public Salle(int idSalle, String nom, int capacite, String localisation,
                 String typeSalle, String etat, String description) {
        this(idSalle, nom, capacite, localisation, typeSalle, etat, description,
            null, null, null, false, null, null);
    }

    public Salle(int idSalle, String nom, int capacite, String localisation,
                 String typeSalle, String etat, String description,
                 String batiment, Integer etage, String typeDisposition,
                 boolean accesHandicape, String statutDetaille, LocalDate dateDerniereMaintenance) {
        this.idSalle = idSalle;
        this.nom = nom;
        this.capacite = capacite;
        this.localisation = localisation;
        this.typeSalle = typeSalle;
        this.etat = etat;
        this.description = description;
        this.batiment = batiment;
        this.etage = etage;
        this.typeDisposition = typeDisposition;
        this.accesHandicape = accesHandicape;
        this.statutDetaille = statutDetaille;
        this.dateDerniereMaintenance = dateDerniereMaintenance;
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

    public String getBatiment() {
        return batiment;
    }

    public void setBatiment(String batiment) {
        this.batiment = batiment;
    }

    public Integer getEtage() {
        return etage;
    }

    public void setEtage(Integer etage) {
        this.etage = etage;
    }

    public String getTypeDisposition() {
        return typeDisposition;
    }

    public void setTypeDisposition(String typeDisposition) {
        this.typeDisposition = typeDisposition;
    }

    public boolean isAccesHandicape() {
        return accesHandicape;
    }

    public void setAccesHandicape(boolean accesHandicape) {
        this.accesHandicape = accesHandicape;
    }

    public String getStatutDetaille() {
        return statutDetaille;
    }

    public void setStatutDetaille(String statutDetaille) {
        this.statutDetaille = statutDetaille;
    }

    public LocalDate getDateDerniereMaintenance() {
        return dateDerniereMaintenance;
    }

    public void setDateDerniereMaintenance(LocalDate dateDerniereMaintenance) {
        this.dateDerniereMaintenance = dateDerniereMaintenance;
    }
}
