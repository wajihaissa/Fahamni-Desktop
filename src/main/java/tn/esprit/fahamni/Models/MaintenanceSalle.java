package tn.esprit.fahamni.Models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MaintenanceSalle {

    private int idMaintenance;
    private int idSalle;
    private Integer idReclamation;
    private String nomSalle;
    private String batimentSalle;
    private String localisationSalle;
    private String typeSalle;
    private String etatSalle;
    private String descriptionSalle;
    private String typeMaintenance;
    private String statut;
    private String responsable;
    private String detailsIntervention;
    private LocalDate datePlanifiee;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private LocalDateTime dateCreation;

    public MaintenanceSalle(int idMaintenance, int idSalle, Integer idReclamation, String nomSalle,
                            String batimentSalle, String localisationSalle, String typeSalle,
                            String etatSalle, String descriptionSalle, String typeMaintenance,
                            String statut, String responsable, String detailsIntervention,
                            LocalDate datePlanifiee, LocalDate dateDebut, LocalDate dateFin,
                            LocalDateTime dateCreation) {
        this.idMaintenance = idMaintenance;
        this.idSalle = idSalle;
        this.idReclamation = idReclamation;
        this.nomSalle = nomSalle;
        this.batimentSalle = batimentSalle;
        this.localisationSalle = localisationSalle;
        this.typeSalle = typeSalle;
        this.etatSalle = etatSalle;
        this.descriptionSalle = descriptionSalle;
        this.typeMaintenance = typeMaintenance;
        this.statut = statut;
        this.responsable = responsable;
        this.detailsIntervention = detailsIntervention;
        this.datePlanifiee = datePlanifiee;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.dateCreation = dateCreation;
    }

    public int getIdMaintenance() {
        return idMaintenance;
    }

    public void setIdMaintenance(int idMaintenance) {
        this.idMaintenance = idMaintenance;
    }

    public int getIdSalle() {
        return idSalle;
    }

    public void setIdSalle(int idSalle) {
        this.idSalle = idSalle;
    }

    public Integer getIdReclamation() {
        return idReclamation;
    }

    public void setIdReclamation(Integer idReclamation) {
        this.idReclamation = idReclamation;
    }

    public String getNomSalle() {
        return nomSalle;
    }

    public void setNomSalle(String nomSalle) {
        this.nomSalle = nomSalle;
    }

    public String getBatimentSalle() {
        return batimentSalle;
    }

    public void setBatimentSalle(String batimentSalle) {
        this.batimentSalle = batimentSalle;
    }

    public String getLocalisationSalle() {
        return localisationSalle;
    }

    public void setLocalisationSalle(String localisationSalle) {
        this.localisationSalle = localisationSalle;
    }

    public String getTypeSalle() {
        return typeSalle;
    }

    public void setTypeSalle(String typeSalle) {
        this.typeSalle = typeSalle;
    }

    public String getEtatSalle() {
        return etatSalle;
    }

    public void setEtatSalle(String etatSalle) {
        this.etatSalle = etatSalle;
    }

    public String getDescriptionSalle() {
        return descriptionSalle;
    }

    public void setDescriptionSalle(String descriptionSalle) {
        this.descriptionSalle = descriptionSalle;
    }

    public String getTypeMaintenance() {
        return typeMaintenance;
    }

    public void setTypeMaintenance(String typeMaintenance) {
        this.typeMaintenance = typeMaintenance;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getResponsable() {
        return responsable;
    }

    public void setResponsable(String responsable) {
        this.responsable = responsable;
    }

    public String getDetailsIntervention() {
        return detailsIntervention;
    }

    public void setDetailsIntervention(String detailsIntervention) {
        this.detailsIntervention = detailsIntervention;
    }

    public LocalDate getDatePlanifiee() {
        return datePlanifiee;
    }

    public void setDatePlanifiee(LocalDate datePlanifiee) {
        this.datePlanifiee = datePlanifiee;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
}
