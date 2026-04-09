package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Reservation;
import tn.esprit.fahamni.services.ReservationService;
import javafx.fxml.FXML;
import java.util.List;

public class ReservationController {

    private final ReservationService reservationService = new ReservationService();
    private List<Reservation> reservations;

    @FXML
    private void initialize() {
        reservations = reservationService.getAllReservations();
    }

    public List<Reservation> getReservations() {
        return reservations;
    }
}

