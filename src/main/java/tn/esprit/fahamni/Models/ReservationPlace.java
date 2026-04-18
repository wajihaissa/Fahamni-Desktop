package tn.esprit.fahamni.Models;

public class ReservationPlace {

    private int idReservationPlace;
    private int idReservation;
    private int idPlace;

    public ReservationPlace(int idReservationPlace, int idReservation, int idPlace) {
        this.idReservationPlace = idReservationPlace;
        this.idReservation = idReservation;
        this.idPlace = idPlace;
    }

    public int getIdReservationPlace() {
        return idReservationPlace;
    }

    public void setIdReservationPlace(int idReservationPlace) {
        this.idReservationPlace = idReservationPlace;
    }

    public int getIdReservation() {
        return idReservation;
    }

    public void setIdReservation(int idReservation) {
        this.idReservation = idReservation;
    }

    public int getIdPlace() {
        return idPlace;
    }

    public void setIdPlace(int idPlace) {
        this.idPlace = idPlace;
    }
}
