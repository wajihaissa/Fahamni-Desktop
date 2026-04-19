package tn.esprit.fahamni.Models;

public class SalleEquipement {

    private final int idSalle;
    private final int idEquipement;
    private final String nomEquipement;
    private final String typeEquipement;
    private final int quantite;
    private final int quantiteDisponible;
    private final String etatEquipement;
    private final String descriptionEquipement;

    public SalleEquipement(int idSalle, int idEquipement, String nomEquipement, String typeEquipement,
                           int quantite, int quantiteDisponible, String etatEquipement, String descriptionEquipement) {
        this.idSalle = idSalle;
        this.idEquipement = idEquipement;
        this.nomEquipement = nomEquipement;
        this.typeEquipement = typeEquipement;
        this.quantite = quantite;
        this.quantiteDisponible = quantiteDisponible;
        this.etatEquipement = etatEquipement;
        this.descriptionEquipement = descriptionEquipement;
    }

    public int getIdSalle() {
        return idSalle;
    }

    public int getIdEquipement() {
        return idEquipement;
    }

    public String getNomEquipement() {
        return nomEquipement;
    }

    public String getTypeEquipement() {
        return typeEquipement;
    }

    public int getQuantite() {
        return quantite;
    }

    public int getQuantiteDisponible() {
        return quantiteDisponible;
    }

    public String getEtatEquipement() {
        return etatEquipement;
    }

    public String getDescriptionEquipement() {
        return descriptionEquipement;
    }
}
