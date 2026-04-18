package tn.esprit.fahamni.Models;

public class Place {

    private int idPlace;
    private int numero;
    private int rang;
    private int colonne;
    private String etat;
    private int idSalle;

    public Place(int idPlace, int numero, int rang, int colonne, String etat, int idSalle) {
        this.idPlace = idPlace;
        this.numero = numero;
        this.rang = rang;
        this.colonne = colonne;
        this.etat = etat;
        this.idSalle = idSalle;
    }

    public int getIdPlace() {
        return idPlace;
    }

    public void setIdPlace(int idPlace) {
        this.idPlace = idPlace;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public int getRang() {
        return rang;
    }

    public void setRang(int rang) {
        this.rang = rang;
    }

    public int getColonne() {
        return colonne;
    }

    public void setColonne(int colonne) {
        this.colonne = colonne;
    }

    public String getEtat() {
        return etat;
    }

    public void setEtat(String etat) {
        this.etat = etat;
    }

    public int getIdSalle() {
        return idSalle;
    }

    public void setIdSalle(int idSalle) {
        this.idSalle = idSalle;
    }
}
