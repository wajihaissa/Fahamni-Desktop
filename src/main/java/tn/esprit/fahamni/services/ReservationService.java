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
    public static final int STATUS_REFUSED = 3;
    private static final int LEGACY_ACCEPTED_STATUS = 2;
    public static final int MIN_STUDENT_RATING = 1;
    public static final int MAX_STUDENT_RATING = 5;
    private static final int MAX_STUDENT_REVIEW_LENGTH = 1000;

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
            return OperationResult.failure("Participant invalide pour la reservation.");
        }
        if (seance.getTuteurId() == participantId) {
            return OperationResult.failure("Vous ne pouvez pas reserver votre propre seance.");
        }
        if (seance.getStatus() != 1) {
            return OperationResult.failure("Seules les seances publiees peuvent etre reservees.");
        }
        if (seance.getStartAt() != null && seance.getStartAt().isBefore(LocalDateTime.now())) {
            return OperationResult.failure("Cette seance est deja passee.");
        }

        try {
            if (!participantExists(participantId)) {
                return OperationResult.failure("Participant introuvable dans la base.");
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
                (
                    SELECT COUNT(*)
                    FROM reservation accepted_reservation
                    WHERE accepted_reservation.seance_id = s.id
                      AND accepted_reservation.cancell_at IS NULL
                      AND accepted_reservation.status IN (1, 2)
                ) AS accepted_reservations,
                r.student_rating,
                r.student_review,
                r.rated_at,
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
                        normalizeStoredStatus(rs.getInt("reservation_status")),
                        readDateTime(rs, "reserved_at"),
                        rs.getString("notes"),
                        rs.getInt("seance_id"),
                        rs.getString("matiere"),
                        readDateTime(rs, "start_at"),
                        rs.getInt("duration_min"),
                        rs.getInt("max_participants"),
                        rs.getInt("participant_id"),
                        rs.getString("participant_name"),
                        rs.getString("participant_email"),
                        rs.getInt("accepted_reservations"),
                        getNullableInteger(rs, "student_rating"),
                        rs.getString("student_review"),
                        readDateTime(rs, "rated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement demandes reservation: " + e.getMessage());
        }
        return requests;
    }

    public List<StudentReservationItem> getStudentReservations(int participantId) {
        List<StudentReservationItem> reservations = new ArrayList<>();
        if (participantId <= 0 || cnx == null) {
            return reservations;
        }

        String sql = """
            SELECT
                r.id AS reservation_id,
                r.status AS reservation_status,
                r.reserved_at,
                r.notes,
                r.seance_id,
                s.matiere,
                s.start_at,
                s.duration_min,
                s.max_participants,
                s.status AS seance_status,
                s.tuteur_id,
                s.mode_seance,
                r.student_rating,
                r.student_review,
                r.rated_at
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            WHERE r.participant_id = ? AND r.cancell_at IS NULL
            ORDER BY
                CASE
                    WHEN r.status = 0 THEN 0
                    WHEN r.status IN (1, 2) THEN 1
                    WHEN r.status = 3 THEN 2
                    ELSE 3
                END,
                r.reserved_at DESC
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    reservations.add(new StudentReservationItem(
                        rs.getInt("reservation_id"),
                        normalizeStoredStatus(rs.getInt("reservation_status")),
                        readDateTime(rs, "reserved_at"),
                        rs.getString("notes"),
                        rs.getInt("seance_id"),
                        rs.getString("matiere"),
                        readDateTime(rs, "start_at"),
                        rs.getInt("duration_min"),
                        rs.getInt("max_participants"),
                        rs.getInt("seance_status"),
                        rs.getInt("tuteur_id"),
                        rs.getString("mode_seance"),
                        getNullableInteger(rs, "student_rating"),
                        rs.getString("student_review"),
                        readDateTime(rs, "rated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement reservations etudiant: " + e.getMessage());
        }
        return reservations;
    }

    public OperationResult rateCompletedReservation(int reservationId, int participantId, int rating, String review) {
        if (cnx == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }
        if (reservationId <= 0) {
            return OperationResult.failure("Selectionnez une reservation valide.");
        }
        if (participantId <= 0) {
            return OperationResult.failure("Etudiant invalide pour cette action.");
        }
        if (rating < MIN_STUDENT_RATING || rating > MAX_STUDENT_RATING) {
            return OperationResult.failure(
                "La note doit etre comprise entre " + MIN_STUDENT_RATING + " et " + MAX_STUDENT_RATING + "."
            );
        }

        String normalizedReview;
        try {
            normalizedReview = normalizeOptionalReview(review);
        } catch (IllegalArgumentException exception) {
            return OperationResult.failure(exception.getMessage());
        }

        String selectSql = """
            SELECT
                r.status,
                r.cancell_at,
                r.student_rating,
                s.start_at,
                s.duration_min
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            WHERE r.id = ? AND r.participant_id = ?
            """;

        boolean ratingAlreadyExists;
        try (PreparedStatement pst = cnx.prepareStatement(selectSql)) {
            pst.setInt(1, reservationId);
            pst.setInt(2, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return OperationResult.failure("Reservation introuvable pour cet etudiant.");
                }

                if (rs.getTimestamp("cancell_at") != null) {
                    return OperationResult.failure("Cette reservation est annulee.");
                }

                int currentStatus = normalizeStoredStatus(rs.getInt("status"));
                if (currentStatus != STATUS_ACCEPTED) {
                    return OperationResult.failure("Seules les reservations acceptees peuvent etre notees.");
                }

                LocalDateTime startAt = readDateTime(rs, "start_at");
                int durationMin = rs.getInt("duration_min");
                LocalDateTime endAt = startAt != null && durationMin > 0 ? startAt.plusMinutes(durationMin) : null;
                if (endAt == null || endAt.isAfter(LocalDateTime.now())) {
                    return OperationResult.failure("La note sera disponible apres la fin effective de la seance.");
                }

                ratingAlreadyExists = getNullableInteger(rs, "student_rating") != null;
                if (ratingAlreadyExists) {
                    return OperationResult.failure("Cette reservation est deja notee.");
                }
            }
        } catch (SQLException e) {
            return OperationResult.failure("Verification de la note impossible: " + e.getMessage());
        }

        String updateSql = """
            UPDATE reservation
            SET student_rating = ?, student_review = ?, rated_at = ?
            WHERE id = ? AND participant_id = ? AND cancell_at IS NULL
            """;
        try (PreparedStatement pst = cnx.prepareStatement(updateSql)) {
            pst.setInt(1, rating);
            pst.setString(2, normalizedReview);
            pst.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            pst.setInt(4, reservationId);
            pst.setInt(5, participantId);

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure("Aucune note enregistree.");
            }
            return OperationResult.success("Votre note a ete enregistree avec succes.");
        } catch (SQLException e) {
            return OperationResult.failure("Enregistrement de la note impossible: " + e.getMessage());
        }
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

                int currentStatus = normalizeStoredStatus(rs.getInt("status"));
                if (currentStatus == STATUS_ACCEPTED) {
                    return OperationResult.success("Cette reservation est deja acceptee.");
                }
                if (currentStatus == STATUS_REFUSED) {
                    return OperationResult.failure("Cette reservation a deja ete refusee.");
                }
                if (currentStatus != STATUS_PENDING) {
                    return OperationResult.failure("Cette reservation ne peut pas etre acceptee dans son etat actuel.");
                }

                int seanceId = rs.getInt("seance_id");
                int maxParticipants = rs.getInt("max_participants");
                if (countAcceptedReservationsBySeanceId(seanceId) >= maxParticipants) {
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

    public OperationResult refuseReservation(int reservationId, int tutorId) {
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
            SELECT r.status, r.cancell_at
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

                int currentStatus = normalizeStoredStatus(rs.getInt("status"));
                if (currentStatus == STATUS_REFUSED) {
                    return OperationResult.success("Cette reservation est deja refusee.");
                }
                if (currentStatus == STATUS_ACCEPTED) {
                    return OperationResult.failure("Cette reservation est deja acceptee. Elle ne peut plus etre refusee.");
                }
                if (currentStatus != STATUS_PENDING) {
                    return OperationResult.failure("Cette reservation ne peut pas etre refusee dans son etat actuel.");
                }
            }
        } catch (SQLException e) {
            return OperationResult.failure("Verification impossible: " + e.getMessage());
        }

        String updateSql = """
            UPDATE reservation
            SET status = ?
            WHERE id = ? AND status = ? AND cancell_at IS NULL
            """;

        try (PreparedStatement pst = cnx.prepareStatement(updateSql)) {
            pst.setInt(1, STATUS_REFUSED);
            pst.setInt(2, reservationId);
            pst.setInt(3, STATUS_PENDING);

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure("Aucune reservation mise a jour.");
            }
            return OperationResult.success("Reservation refusee avec succes.");
        } catch (SQLException e) {
            return OperationResult.failure("Refus impossible: " + e.getMessage());
        }
    }

    public OperationResult cancelStudentReservation(int reservationId, int participantId) {
        if (cnx == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }
        if (reservationId <= 0) {
            return OperationResult.failure("Selectionnez une reservation valide.");
        }
        if (participantId <= 0) {
            return OperationResult.failure("Etudiant invalide pour cette action.");
        }

        String selectSql = """
            SELECT status, cancell_at
            FROM reservation
            WHERE id = ? AND participant_id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(selectSql)) {
            pst.setInt(1, reservationId);
            pst.setInt(2, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return OperationResult.failure("Reservation introuvable pour cet etudiant.");
                }

                if (rs.getTimestamp("cancell_at") != null) {
                    return OperationResult.success("Cette reservation est deja annulee.");
                }

                int currentStatus = normalizeStoredStatus(rs.getInt("status"));
                if (currentStatus == STATUS_ACCEPTED) {
                    return OperationResult.failure("Cette reservation est deja acceptee. Elle ne peut plus etre annulee ici.");
                }
                if (currentStatus == STATUS_REFUSED) {
                    return OperationResult.failure("Cette reservation est deja refusee.");
                }
                if (currentStatus != STATUS_PENDING) {
                    return OperationResult.failure("Cette reservation ne peut pas etre annulee dans son etat actuel.");
                }
            }
        } catch (SQLException e) {
            return OperationResult.failure("Verification impossible: " + e.getMessage());
        }

        String updateSql = """
            UPDATE reservation
            SET cancell_at = ?
            WHERE id = ? AND participant_id = ? AND status = ? AND cancell_at IS NULL
            """;

        try (PreparedStatement pst = cnx.prepareStatement(updateSql)) {
            pst.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            pst.setInt(2, reservationId);
            pst.setInt(3, participantId);
            pst.setInt(4, STATUS_PENDING);

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure("Aucune reservation annulee.");
            }
            return OperationResult.success("Reservation annulee avec succes.");
        } catch (SQLException e) {
            return OperationResult.failure("Annulation impossible: " + e.getMessage());
        }
    }

    public boolean hasActiveReservation(int seanceId, int participantId) {
        if (seanceId <= 0 || participantId <= 0 || cnx == null) {
            return false;
        }

        String sql = """
            SELECT COUNT(*) AS total
            FROM reservation
            WHERE seance_id = ?
              AND participant_id = ?
              AND cancell_at IS NULL
              AND status <> ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            pst.setInt(2, participantId);
            pst.setInt(3, STATUS_REFUSED);
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
                SUM(CASE WHEN status IN (0, 1, 2) THEN 1 ELSE 0 END) AS total,
                SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS pending,
                SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END) AS accepted,
                SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS refused
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
                    rs.getInt("refused")
                );
            }
        } catch (SQLException e) {
            System.out.println("Erreur comptage reservations: " + e.getMessage());
            return ReservationStats.empty();
        }
    }

    public List<SessionEvaluationItem> getSessionEvaluations(int seanceId) {
        List<SessionEvaluationItem> evaluations = new ArrayList<>();
        if (seanceId <= 0 || cnx == null) {
            return evaluations;
        }

        String sql = """
            SELECT
                r.participant_id,
                COALESCE(NULLIF(TRIM(u.full_name), ''), 'Etudiant') AS participant_name,
                r.student_rating,
                r.student_review,
                r.rated_at
            FROM reservation r
            INNER JOIN user u ON u.id = r.participant_id
            WHERE r.seance_id = ?
              AND r.cancell_at IS NULL
              AND r.status IN (?, ?)
              AND r.student_rating BETWEEN ? AND ?
            ORDER BY r.rated_at DESC, r.reserved_at DESC
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            pst.setInt(2, STATUS_ACCEPTED);
            pst.setInt(3, LEGACY_ACCEPTED_STATUS);
            pst.setInt(4, MIN_STUDENT_RATING);
            pst.setInt(5, MAX_STUDENT_RATING);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Integer rating = getNullableInteger(rs, "student_rating");
                    if (rating == null) {
                        continue;
                    }
                    evaluations.add(new SessionEvaluationItem(
                        rs.getInt("participant_id"),
                        rs.getString("participant_name"),
                        rating,
                        rs.getString("student_review"),
                        readDateTime(rs, "rated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement evaluations seance: " + e.getMessage());
        }
        return evaluations;
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
        String participantEmail,
        int acceptedReservations,
        Integer studentRating,
        String studentReview,
        LocalDateTime ratedAt
    ) {
        public boolean isPending() {
            return status == STATUS_PENDING;
        }

        public boolean isRefused() {
            return status == STATUS_REFUSED;
        }

        public int availableAcceptedSeats() {
            return Math.max(0, maxParticipants - acceptedReservations);
        }

        public boolean isSessionCapacityReached() {
            return availableAcceptedSeats() <= 0;
        }

        public boolean hasRating() {
            return studentRating != null
                && studentRating >= MIN_STUDENT_RATING
                && studentRating <= MAX_STUDENT_RATING;
        }
    }

    public record StudentReservationItem(
        int id,
        int status,
        LocalDateTime reservedAt,
        String notes,
        int seanceId,
        String seanceTitle,
        LocalDateTime seanceStartAt,
        int durationMin,
        int maxParticipants,
        int seanceStatus,
        int tutorId,
        String sessionMode,
        Integer studentRating,
        String studentReview,
        LocalDateTime ratedAt
    ) {
        public boolean isPending() {
            return status == STATUS_PENDING;
        }

        public boolean isAccepted() {
            return status == STATUS_ACCEPTED;
        }

        public boolean isRefused() {
            return status == STATUS_REFUSED;
        }

        public boolean isCompleted() {
            if (seanceStartAt == null || durationMin <= 0) {
                return false;
            }
            return !seanceStartAt.plusMinutes(durationMin).isAfter(LocalDateTime.now());
        }

        public boolean canBeRated() {
            return isAccepted() && isCompleted();
        }

        public boolean hasRating() {
            return studentRating != null
                && studentRating >= MIN_STUDENT_RATING
                && studentRating <= MAX_STUDENT_RATING;
        }
    }

    public record ReservationStats(int total, int pending, int accepted, int refused) {
        public static ReservationStats empty() {
            return new ReservationStats(0, 0, 0, 0);
        }
    }

    public record SessionEvaluationItem(
        int participantId,
        String participantName,
        int rating,
        String review,
        LocalDateTime ratedAt
    ) {
        public boolean hasReview() {
            return review != null && !review.isBlank();
        }
    }

    private int countAcceptedReservationsBySeanceId(int seanceId) throws SQLException {
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
            pst.setInt(3, LEGACY_ACCEPTED_STATUS);
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

    private Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private static int normalizeStoredStatus(int status) {
        return status == LEGACY_ACCEPTED_STATUS ? STATUS_ACCEPTED : status;
    }

    private String normalizeOptionalReview(String review) {
        if (review == null) {
            return null;
        }

        String normalized = review.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_STUDENT_REVIEW_LENGTH) {
            throw new IllegalArgumentException(
                "Le commentaire ne doit pas depasser " + MAX_STUDENT_REVIEW_LENGTH + " caracteres."
            );
        }
        return normalized;
    }
}
