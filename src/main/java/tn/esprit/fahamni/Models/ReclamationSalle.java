package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class ReclamationSalle {

    private int idReclamation;
    private int idSalle;
    private String nomSalle;
    private String nomDeclarant;
    private String referenceSeance;
    private String typeProbleme;
    private String priorite;
    private String statut;
    private String description;
    private String commentaireAdmin;
    private LocalDateTime dateReclamation;
    private LocalDateTime dateTraitement;

    public ReclamationSalle(int idReclamation, int idSalle, String nomSalle, String nomDeclarant,
                            String referenceSeance, String typeProbleme, String priorite, String statut,
                            String description, String commentaireAdmin, LocalDateTime dateReclamation,
                            LocalDateTime dateTraitement) {
        this.idReclamation = idReclamation;
        this.idSalle = idSalle;
        this.nomSalle = nomSalle;
        this.nomDeclarant = nomDeclarant;
        this.referenceSeance = referenceSeance;
        this.typeProbleme = typeProbleme;
        this.priorite = priorite;
        this.statut = statut;
        this.description = description;
        this.commentaireAdmin = commentaireAdmin;
        this.dateReclamation = dateReclamation;
        this.dateTraitement = dateTraitement;
    }

    public int getIdReclamation() {
        return idReclamation;
    }

    public void setIdReclamation(int idReclamation) {
        this.idReclamation = idReclamation;
    }

    public int getIdSalle() {
        return idSalle;
    }

    public void setIdSalle(int idSalle) {
        this.idSalle = idSalle;
    }

    public String getNomSalle() {
        return nomSalle;
    }

    public void setNomSalle(String nomSalle) {
        this.nomSalle = nomSalle;
    }

    public String getNomDeclarant() {
        return nomDeclarant;
    }

    public void setNomDeclarant(String nomDeclarant) {
        this.nomDeclarant = nomDeclarant;
    }

    public String getReferenceSeance() {
        return referenceSeance;
    }

    public void setReferenceSeance(String referenceSeance) {
        this.referenceSeance = referenceSeance;
    }

    public String getTypeProbleme() {
        return typeProbleme;
    }

    public void setTypeProbleme(String typeProbleme) {
        this.typeProbleme = typeProbleme;
    }

    public String getPriorite() {
        return priorite;
    }

    public void setPriorite(String priorite) {
        this.priorite = priorite;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCommentaireAdmin() {
        return commentaireAdmin;
    }

    public void setCommentaireAdmin(String commentaireAdmin) {
        this.commentaireAdmin = commentaireAdmin;
    }

    public LocalDateTime getDateReclamation() {
        return dateReclamation;
    }

    public void setDateReclamation(LocalDateTime dateReclamation) {
        this.dateReclamation = dateReclamation;
    }

    public LocalDateTime getDateTraitement() {
        return dateTraitement;
    }

    public void setDateTraitement(LocalDateTime dateTraitement) {
        this.dateTraitement = dateTraitement;
    }
}
