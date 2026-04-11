package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.Models.Reservation;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReservationService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_PAID = 2;

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

    public OperationResult reserveSeance(Seance seance, int participantId) {
        if (cnx == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }
        if (seance == null || seance.getId() <= 0) {
            return OperationResult.failure("Selectionnez une seance valide.");
        }
        if (participantId <= 0) {
            return OperationResult.failure("Etudiant invalide pour la reservation.");
        }
        if (seance.getStatus() != 1) {
            return OperationResult.failure("Seules les seances publiees peuvent etre reservees.");
        }
        if (seance.getStartAt() != null && seance.getStartAt().isBefore(LocalDateTime.now())) {
            return OperationResult.failure("Cette seance est deja passee.");
        }

        try {
            if (!participantExists(participantId)) {
                return OperationResult.failure("Etudiant introuvable dans la base.");
            }
            if (hasActiveReservation(seance.getId(), participantId)) {
                return OperationResult.failure("Vous avez deja reserve cette seance.");
            }
            if (countActiveReservationsBySeanceId(seance.getId()) >= seance.getMaxParticipants()) {
                return OperationResult.failure("Cette seance est complete.");
            }

            insertReservation(seance.getId(), participantId);
            return OperationResult.success("Reservation envoyee avec succes. Statut: en attente.");
        } catch (SQLException e) {
            return OperationResult.failure("Reservation impossible: " + e.getMessage());
        }
    }

    public List<TutorReservationRequest> getTutorReservationRequests(int tutorId) {
        List<TutorReservationRequest> requests = new ArrayList<>();
        if (tutorId <= 0 || cnx == null) {
            return requests;
        }

        String sql = """
            SELECT
                r.id AS reservation_id,
                r.status AS reservation_status,
                r.reserved_at,
                r.notes,
                r.seance_id,
                r.participant_id,
                s.matiere,
                s.start_at,
                s.duration_min,
                s.max_participants,
                u.full_name AS participant_name,
                u.email AS participant_email
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            INNER JOIN user u ON u.id = r.participant_id
            WHERE s.tuteur_id = ? AND r.cancell_at IS NULL
            ORDER BY
                CASE WHEN r.status = 0 THEN 0 ELSE 1 END,
                r.reserved_at DESC
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, tutorId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    requests.add(new TutorReservationRequest(
                        rs.getInt("reservation_id"),
                        rs.getInt("reservation_status"),
                        readDateTime(rs, "reserved_at"),
                        rs.getString("notes"),
                        rs.getInt("seance_id"),
                        rs.getString("matiere"),
                        readDateTime(rs, "start_at"),
                        rs.getInt("duration_min"),
                        rs.getInt("max_participants"),
                        rs.getInt("participant_id"),
                        rs.getString("participant_name"),
                        rs.getString("participant_email")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement demandes reservation: " + e.getMessage());
        }
        return requests;
    }

    public OperationResult acceptReservation(int reservationId, int tutorId) {
        if (cnx == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }
        if (reservationId <= 0) {
            return OperationResult.failure("Selectionnez une reservation valide.");
        }
        if (tutorId <= 0) {
            return OperationResult.failure("Tuteur invalide pour cette action.");
        }

        String selectSql = """
            SELECT r.status, r.cancell_at, r.seance_id, s.max_participants
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            WHERE r.id = ? AND s.tuteur_id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(selectSql)) {
            pst.setInt(1, reservationId);
            pst.setInt(2, tutorId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return OperationResult.failure("Reservation introuvable pour les seances de ce tuteur.");
                }

                if (rs.getTimestamp("cancell_at") != null) {
                    return OperationResult.failure("Cette reservation est annulee.");
                }

                int currentStatus = rs.getInt("status");
                if (currentStatus == STATUS_ACCEPTED) {
                    return OperationResult.success("Cette reservation est deja acceptee.");
                }
                if (currentStatus == STATUS_PAID) {
                    return OperationResult.success("Cette reservation est deja payee.");
                }
                if (currentStatus != STATUS_PENDING) {
                    return OperationResult.failure("Cette reservation ne peut pas etre acceptee dans son etat actuel.");
                }

                int seanceId = rs.getInt("seance_id");
                int maxParticipants = rs.getInt("max_participants");
                if (countAcceptedOrPaidReservationsBySeanceId(seanceId) >= maxParticipants) {
                    return OperationResult.failure("La capacite de cette seance est deja atteinte.");
                }
            }
        } catch (SQLException e) {
            return OperationResult.failure("Verification impossible: " + e.getMessage());
        }

        String updateSql = """
            UPDATE reservation
            SET status = ?, acceptance_email_sent_at = ?
            WHERE id = ? AND status = ? AND cancell_at IS NULL
            """;

        try (PreparedStatement pst = cnx.prepareStatement(updateSql)) {
            pst.setInt(1, STATUS_ACCEPTED);
            pst.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            pst.setInt(3, reservationId);
            pst.setInt(4, STATUS_PENDING);

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure("Aucune reservation mise a jour.");
            }
            return OperationResult.success("Reservation acceptee avec succes.");
        } catch (SQLException e) {
            return OperationResult.failure("Acceptation impossible: " + e.getMessage());
        }
    }

    public boolean hasActiveReservation(int seanceId, int participantId) {
        if (seanceId <= 0 || participantId <= 0 || cnx == null) {
            return false;
        }

        String sql = """
            SELECT COUNT(*) AS total
            FROM reservation
            WHERE seance_id = ? AND participant_id = ? AND cancell_at IS NULL
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            pst.setInt(2, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() && rs.getInt("total") > 0;
            }
        } catch (SQLException e) {
            System.out.println("Erreur verification reservation: " + e.getMessage());
            return false;
        }
    }

    public int countActiveReservationsBySeanceId(int seanceId) {
        return getStatsBySeanceId(seanceId).total();
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
            WHERE seance_id = ? AND cancell_at IS NULL
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

    public record TutorReservationRequest(
        int id,
        int status,
        LocalDateTime reservedAt,
        String notes,
        int seanceId,
        String seanceTitle,
        LocalDateTime seanceStartAt,
        int durationMin,
        int maxParticipants,
        int participantId,
        String participantName,
        String participantEmail
    ) {
        public boolean isPending() {
            return status == STATUS_PENDING;
        }
    }

    public record ReservationStats(int total, int pending, int accepted, int paid) {
        public static ReservationStats empty() {
            return new ReservationStats(0, 0, 0, 0);
        }
    }

    private int countAcceptedOrPaidReservationsBySeanceId(int seanceId) throws SQLException {
        String sql = """
            SELECT COUNT(*) AS total
            FROM reservation
            WHERE seance_id = ?
              AND cancell_at IS NULL
              AND status IN (?, ?)
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            pst.setInt(2, STATUS_ACCEPTED);
            pst.setInt(3, STATUS_PAID);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    private boolean participantExists(int participantId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM user WHERE id = ?";
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() && rs.getInt("total") > 0;
            }
        }
    }

    private void insertReservation(int seanceId, int participantId) throws SQLException {
        String sql = """
            INSERT INTO reservation (
                status, reserved_at, cancell_at, notes, seance_id, participant_id,
                confirmation_email_sent_at, acceptance_email_sent_at, reminder_email_sent_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            pst.setInt(1, 0);
            pst.setTimestamp(2, Timestamp.valueOf(now));
            pst.setTimestamp(3, null);
            pst.setString(4, null);
            pst.setInt(5, seanceId);
            pst.setInt(6, participantId);
            pst.setTimestamp(7, null);
            pst.setTimestamp(8, null);
            pst.setTimestamp(9, null);
            pst.executeUpdate();
        }
    }

    private LocalDateTime readDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
