package tn.esprit.fahamni.Models;

public class SalleEquipement {

    private int idSalle;
    private int idEquipement;
    private int quantite;

    public SalleEquipement(int idSalle, int idEquipement, int quantite) {
        this.idSalle = idSalle;
        this.idEquipement = idEquipement;
        this.quantite = quantite;
    }

    public int getIdSalle() {
        return idSalle;
    }

    public void setIdSalle(int idSalle) {
        this.idSalle = idSalle;
    }

    public int getIdEquipement() {
        return idEquipement;
    }

    public void setIdEquipement(int idEquipement) {
        this.idEquipement = idEquipement;
    }

    public int getQuantite() {
        return quantite;
    }

    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }
}
