package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminReservation;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

public class AdminReservationService {

    private final ObservableList<AdminReservation> reservations = FXCollections.observableArrayList(
        new AdminReservation("Nour Ben Salem", "Mathematics - Algebra", "31 Mar 2026", "Confirmed"),
        new AdminReservation("Amira Trabelsi", "Physics - Mechanics", "31 Mar 2026", "Pending"),
        new AdminReservation("Walid Jaziri", "Chemistry - Organic", "30 Mar 2026", "Cancelled")
    );

    public ObservableList<AdminReservation> getReservations() {
        return reservations;
    }

    public List<String> getAvailableStatuses() {
        return List.of("Pending", "Confirmed", "Cancelled");
    }

    public OperationResult confirmReservation(AdminReservation reservation) {
        return updateReservationStatus(reservation, "Confirmed", "Reservation confirmee.");
    }

    public OperationResult cancelReservation(AdminReservation reservation) {
        return updateReservationStatus(reservation, "Cancelled", "Reservation annulee.");
    }

    public OperationResult saveReservation(AdminReservation reservation, String studentName, String sessionTitle, String requestDate, String status) {
        if (reservation == null) {
            return OperationResult.failure("Selectionnez une reservation a mettre a jour.");
        }

        reservation.setStudentName(studentName.trim());
        reservation.setSessionTitle(sessionTitle.trim());
        reservation.setRequestDate(requestDate.trim());
        reservation.setStatus(status);
        return OperationResult.success("Reservation mise a jour.");
    }

    private OperationResult updateReservationStatus(AdminReservation reservation, String status, String message) {
        if (reservation == null) {
            return OperationResult.failure("Selectionnez une reservation.");
        }

        reservation.setStatus(status);
        return OperationResult.success(message);
    }
}

