package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class ReservationPlace {

    private int idReservationPlace;
    private int idReservation;
    private int idSeance;
    private int idPlace;
    private LocalDateTime createdAt;

    public ReservationPlace() {
    }

    public ReservationPlace(int idReservationPlace, int idReservation, int idSeance, int idPlace, LocalDateTime createdAt) {
        this.idReservationPlace = idReservationPlace;
        this.idReservation = idReservation;
        this.idSeance = idSeance;
        this.idPlace = idPlace;
        this.createdAt = createdAt;
    }

    public ReservationPlace(int idReservation, int idSeance, int idPlace) {
        this(0, idReservation, idSeance, idPlace, LocalDateTime.now());
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

    public int getIdSeance() {
        return idSeance;
    }

    public void setIdSeance(int idSeance) {
        this.idSeance = idSeance;
    }

    public int getIdPlace() {
        return idPlace;
    }

    public void setIdPlace(int idPlace) {
        this.idPlace = idPlace;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
