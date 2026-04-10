package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Reservation;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReservationService {

    private final Connection cnx = MyDataBase.getInstance().getCnx();

    private final List<Reservation> reservations = new ArrayList<>(List.of(
        new Reservation("Mathematics - Algebra Fundamentals", "Ahmed Ben Ali",
            "15 Dec 2024 - 10:00 AM", "Online (Zoom)", 50.0, "Confirmed", true, null),
        new Reservation("Physics - Mechanics", "Sarah Mansour",
            "16 Dec 2024 - 2:00 PM", "Online (Zoom)", 75.0, "Pending", true, null),
        new Reservation("Chemistry - Organic Chemistry", "Mohamed Trabelsi",
            "20 Nov 2024 - 4:00 PM", "Online (Zoom)", 60.0, "Completed", false, 5),
        new Reservation("English - Grammar", "Leila Khemiri",
            "15 Nov 2024 - 11:00 AM", "Online (Zoom)", 45.0, "Completed", false, 4)
    ));

    public List<Reservation> getAllReservations() {
        return new ArrayList<>(reservations);
    }

    public List<Reservation> getUpcomingReservations() {
        return reservations.stream()
            .filter(Reservation::isUpcoming)
            .collect(Collectors.toList());
    }

    public List<Reservation> getReservationHistory() {
        return reservations.stream()
            .filter(reservation -> !reservation.isUpcoming())
            .collect(Collectors.toList());
    }

    public Reservation createReservation(Reservation reservation) {
        reservations.add(reservation);
        return reservation;
    }

    public int countBySeanceId(int seanceId) {
        return getStatsBySeanceId(seanceId).total();
    }

    public ReservationStats getStatsBySeanceId(int seanceId) {
        if (seanceId <= 0 || cnx == null) {
            return ReservationStats.empty();
        }

        String sql = """
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS pending,
                SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS accepted,
                SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS paid
            FROM reservation
            WHERE seance_id = ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return ReservationStats.empty();
                }
                return new ReservationStats(
                    rs.getInt("total"),
                    rs.getInt("pending"),
                    rs.getInt("accepted"),
                    rs.getInt("paid")
                );
            }
        } catch (SQLException e) {
            System.out.println("Erreur comptage reservations: " + e.getMessage());
            return ReservationStats.empty();
        }
    }

    public record ReservationStats(int total, int pending, int accepted, int paid) {
        public static ReservationStats empty() {
            return new ReservationStats(0, 0, 0, 0);
        }
    }
}

