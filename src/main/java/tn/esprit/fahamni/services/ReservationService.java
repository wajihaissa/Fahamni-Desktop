package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Place;
import tn.esprit.fahamni.Models.Reservation;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
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
    private static final String PLACE_STATUS_AVAILABLE = "disponible";
    private static final String SELECT_SEAT_OPTIONS_BY_SEANCE_SQL = """
        SELECT
            p.idPlace,
            p.numero,
            p.rang,
            p.colonne,
            p.etat,
            p.idSalle,
            CASE WHEN active_reservation.id IS NULL THEN 0 ELSE 1 END AS reserved
        FROM place p
        INNER JOIN seance s ON s.salle_id = p.idSalle
        LEFT JOIN reservation_place rp
            ON rp.idSeance = s.id AND rp.idPlace = p.idPlace
        LEFT JOIN reservation active_reservation
            ON active_reservation.id = rp.idReservation
           AND active_reservation.cancell_at IS NULL
           AND active_reservation.status <> ?
        WHERE s.id = ?
        ORDER BY p.rang ASC, p.colonne ASC, p.numero ASC
        """;
    private static final String SELECT_PLACE_BY_SEANCE_AND_ID_SQL = """
        SELECT
            p.idPlace,
            p.numero,
            p.rang,
            p.colonne,
            p.etat,
            p.idSalle,
            CASE WHEN active_reservation.id IS NULL THEN 0 ELSE 1 END AS reserved
        FROM place p
        LEFT JOIN reservation_place rp
            ON rp.idSeance = ? AND rp.idPlace = p.idPlace
        LEFT JOIN reservation active_reservation
            ON active_reservation.id = rp.idReservation
           AND active_reservation.cancell_at IS NULL
           AND active_reservation.status <> ?
        WHERE p.idPlace = ? AND p.idSalle = ?
        """;
    private static final String COUNT_PLACES_BY_SALLE_SQL =
        "SELECT COUNT(*) AS total FROM place WHERE idSalle = ?";
    private static final String INSERT_RESERVATION_SQL = """
        INSERT INTO reservation (
            status, reserved_at, cancell_at, notes, seance_id, participant_id,
            confirmation_email_sent_at, acceptance_email_sent_at, reminder_email_sent_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String INSERT_RESERVATION_PLACE_SQL = """
        INSERT INTO reservation_place (idReservation, idSeance, idPlace, created_at)
        VALUES (?, ?, ?, ?)
        """;
    private static final String DELETE_RESERVATION_PLACE_BY_RESERVATION_SQL =
        "DELETE FROM reservation_place WHERE idReservation = ?";

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
        return reserveSeance(seance, participantId, null);
    }

    public OperationResult reserveSeance(Seance seance, int participantId, Integer selectedPlaceId) {
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

            Connection connection = requireConnection();
            boolean initialAutoCommit = connection.getAutoCommit();

            try {
                connection.setAutoCommit(false);

                SelectedPlace selectedPlace = null;
                if (seance.isPresentiel()) {
                    selectedPlace = validateSelectedPlace(connection, seance, selectedPlaceId);
                }

                int reservationId = insertReservation(connection, seance.getId(), participantId);
                if (selectedPlace != null) {
                    insertReservationPlace(connection, reservationId, seance.getId(), selectedPlace.id());
                }

                connection.commit();

                String successMessage = selectedPlace == null
                    ? "Reservation envoyee avec succes. Statut: en attente."
                    : "Reservation envoyee avec succes. " + selectedPlace.label() + " est bloquee pour cette seance. Statut: en attente.";
                return OperationResult.success(successMessage);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                return OperationResult.failure(resolveReservationFailureMessage(exception));
            } finally {
                connection.setAutoCommit(initialAutoCommit);
            }
        } catch (SQLException exception) {
            return OperationResult.failure("Reservation impossible: " + exception.getMessage());
        }
    }

    public List<SeatSelectionOption> getSeatSelectionOptions(Seance seance) {
        if (seance == null || seance.getId() <= 0 || !seance.isPresentiel() || cnx == null) {
            return List.of();
        }

        List<SeatSelectionOption> options = new ArrayList<>();
        try (PreparedStatement pst = cnx.prepareStatement(SELECT_SEAT_OPTIONS_BY_SEANCE_SQL)) {
            pst.setInt(1, STATUS_REFUSED);
            pst.setInt(2, seance.getId());
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Place place = new Place(
                        rs.getInt("idPlace"),
                        rs.getInt("numero"),
                        rs.getInt("rang"),
                        rs.getInt("colonne"),
                        rs.getString("etat"),
                        rs.getInt("idSalle")
                    );
                    options.add(new SeatSelectionOption(place, rs.getInt("reserved") > 0));
                }
            }
            return options;
        } catch (SQLException exception) {
            throw new IllegalStateException("Chargement du plan de salle impossible: " + exception.getMessage(), exception);
        }
    }

    public int countConfiguredSeatsBySalle(Integer salleId) {
        if (salleId == null || salleId <= 0 || cnx == null) {
            return 0;
        }

        try (PreparedStatement pst = cnx.prepareStatement(COUNT_PLACES_BY_SALLE_SQL)) {
            pst.setInt(1, salleId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Chargement du nombre de places de la salle impossible: " + exception.getMessage(),
                exception
            );
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
                u.full_name AS participant_name,
                u.email AS participant_email,
                CASE
                    WHEN p.idPlace IS NULL THEN NULL
                    ELSE CONCAT('Place ', p.numero, ' (rang ', p.rang, ', colonne ', p.colonne, ')')
                END AS place_label
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            INNER JOIN user u ON u.id = r.participant_id
            LEFT JOIN reservation_place rp ON rp.idReservation = r.id
            LEFT JOIN place p ON p.idPlace = rp.idPlace
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
                        rs.getString("place_label")
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
                CASE
                    WHEN p.idPlace IS NULL THEN NULL
                    ELSE CONCAT('Place ', p.numero, ' (rang ', p.rang, ', colonne ', p.colonne, ')')
                END AS place_label
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            LEFT JOIN reservation_place rp ON rp.idReservation = r.id
            LEFT JOIN place p ON p.idPlace = rp.idPlace
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
                        rs.getString("place_label")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement reservations etudiant: " + e.getMessage());
        }
        return reservations;
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

        Connection connection = requireConnection();
        try {
            boolean initialAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement pst = connection.prepareStatement(updateSql)) {
                    pst.setInt(1, STATUS_REFUSED);
                    pst.setInt(2, reservationId);
                    pst.setInt(3, STATUS_PENDING);

                    int updatedRows = pst.executeUpdate();
                    if (updatedRows == 0) {
                        connection.rollback();
                        return OperationResult.failure("Aucune reservation mise a jour.");
                    }
                }

                deleteReservationPlaceByReservationId(connection, reservationId);
                connection.commit();
                return OperationResult.success("Reservation refusee avec succes.");
            } catch (SQLException exception) {
                connection.rollback();
                return OperationResult.failure("Refus impossible: " + exception.getMessage());
            } finally {
                connection.setAutoCommit(initialAutoCommit);
            }
        } catch (SQLException exception) {
            return OperationResult.failure("Refus impossible: " + exception.getMessage());
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

        Connection connection = requireConnection();
        try {
            boolean initialAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement pst = connection.prepareStatement(updateSql)) {
                    pst.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                    pst.setInt(2, reservationId);
                    pst.setInt(3, participantId);
                    pst.setInt(4, STATUS_PENDING);

                    int updatedRows = pst.executeUpdate();
                    if (updatedRows == 0) {
                        connection.rollback();
                        return OperationResult.failure("Aucune reservation annulee.");
                    }
                }

                deleteReservationPlaceByReservationId(connection, reservationId);
                connection.commit();
                return OperationResult.success("Reservation annulee avec succes.");
            } catch (SQLException exception) {
                connection.rollback();
                return OperationResult.failure("Annulation impossible: " + exception.getMessage());
            } finally {
                connection.setAutoCommit(initialAutoCommit);
            }
        } catch (SQLException exception) {
            return OperationResult.failure("Annulation impossible: " + exception.getMessage());
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
        String placeLabel
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

        public boolean hasPlaceSelection() {
            return placeLabel != null && !placeLabel.isBlank();
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
        String placeLabel
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

        public boolean hasPlaceSelection() {
            return placeLabel != null && !placeLabel.isBlank();
        }
    }

    public record ReservationStats(int total, int pending, int accepted, int refused) {
        public static ReservationStats empty() {
            return new ReservationStats(0, 0, 0, 0);
        }
    }

    public record SeatSelectionOption(Place place, boolean reserved) {
        public int placeId() {
            return place == null ? 0 : place.getIdPlace();
        }

        public int row() {
            return place == null ? 1 : Math.max(1, place.getRang());
        }

        public int column() {
            return place == null ? 1 : Math.max(1, place.getColonne());
        }

        public int rowIndex() {
            return row() - 1;
        }

        public int columnIndex() {
            return column() - 1;
        }

        public boolean selectable() {
            return place != null && !reserved && ReservationService.isPlaceUsable(place.getEtat());
        }

        public String buttonLabel() {
            return place == null ? "?" : "P" + place.getNumero();
        }

        public String displayLabel() {
            return ReservationService.buildPlaceLabel(place);
        }

        public String positionLabel() {
            if (place == null) {
                return "Position inconnue";
            }
            return "Rang " + row() + " - Colonne " + column();
        }
    }

    private record SelectedPlace(int id, String label) {
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

    private int insertReservation(Connection connection, int seanceId, int participantId) throws SQLException {
        try (PreparedStatement pst = connection.prepareStatement(INSERT_RESERVATION_SQL, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now();
            pst.setInt(1, STATUS_PENDING);
            pst.setTimestamp(2, Timestamp.valueOf(now));
            pst.setTimestamp(3, null);
            pst.setString(4, null);
            pst.setInt(5, seanceId);
            pst.setInt(6, participantId);
            pst.setTimestamp(7, null);
            pst.setTimestamp(8, null);
            pst.setTimestamp(9, null);
            pst.executeUpdate();

            try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Creation de reservation impossible: identifiant non genere.");
    }

    private void insertReservationPlace(Connection connection, int reservationId, int seanceId, int placeId) throws SQLException {
        try (PreparedStatement pst = connection.prepareStatement(INSERT_RESERVATION_PLACE_SQL)) {
            pst.setInt(1, reservationId);
            pst.setInt(2, seanceId);
            pst.setInt(3, placeId);
            pst.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            pst.executeUpdate();
        }
    }

    private void deleteReservationPlaceByReservationId(Connection connection, int reservationId) throws SQLException {
        try (PreparedStatement pst = connection.prepareStatement(DELETE_RESERVATION_PLACE_BY_RESERVATION_SQL)) {
            pst.setInt(1, reservationId);
            pst.executeUpdate();
        }
    }

    private SelectedPlace validateSelectedPlace(Connection connection, Seance seance, Integer selectedPlaceId) throws SQLException {
        if (seance == null || !seance.isPresentiel()) {
            return null;
        }
        if (seance.getSalleId() == null || seance.getSalleId() <= 0) {
            throw new IllegalArgumentException("Cette seance presentielle ne reference aucune salle valide.");
        }

        int configuredPlaces = countPlacesForSalle(connection, seance.getSalleId());
        if (configuredPlaces <= 0) {
            throw new IllegalArgumentException("Aucune place n'est configuree pour la salle de cette seance.");
        }
        if (selectedPlaceId == null || selectedPlaceId <= 0) {
            throw new IllegalArgumentException("Choisissez une place disponible avant de confirmer la reservation.");
        }

        try (PreparedStatement pst = connection.prepareStatement(SELECT_PLACE_BY_SEANCE_AND_ID_SQL)) {
            pst.setInt(1, seance.getId());
            pst.setInt(2, STATUS_REFUSED);
            pst.setInt(3, selectedPlaceId);
            pst.setInt(4, seance.getSalleId());

            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("La place selectionnee n'appartient pas a la salle de cette seance.");
                }

                Place place = new Place(
                    rs.getInt("idPlace"),
                    rs.getInt("numero"),
                    rs.getInt("rang"),
                    rs.getInt("colonne"),
                    rs.getString("etat"),
                    rs.getInt("idSalle")
                );

                if (!isPlaceUsable(place.getEtat())) {
                    throw new IllegalArgumentException(buildPlaceLabel(place) + " est indisponible pour le moment.");
                }
                if (rs.getInt("reserved") > 0) {
                    throw new IllegalArgumentException(buildPlaceLabel(place) + " vient d'etre reservee. Choisissez-en une autre.");
                }

                return new SelectedPlace(place.getIdPlace(), buildPlaceLabel(place));
            }
        }
    }

    private int countPlacesForSalle(Connection connection, int salleId) throws SQLException {
        try (PreparedStatement pst = connection.prepareStatement(COUNT_PLACES_BY_SALLE_SQL)) {
            pst.setInt(1, salleId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    private Connection requireConnection() {
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base de donnees indisponible.");
        }
        return cnx;
    }

    private String resolveReservationFailureMessage(Exception exception) {
        if (exception instanceof SQLIntegrityConstraintViolationException || isSeatAlreadyTakenError(exception)) {
            return "Cette place vient d'etre reservee par quelqu'un d'autre. Choisissez-en une autre.";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Reservation impossible: une erreur technique est survenue.";
        }

        return "Reservation impossible: " + message;
    }

    private boolean isSeatAlreadyTakenError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }

        String lowered = message.toLowerCase();
        return lowered.contains("uk_reservation_place_seance_place")
            || lowered.contains("duplicate")
            || lowered.contains("duplicata");
    }

    private LocalDateTime readDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private static int normalizeStoredStatus(int status) {
        return status == LEGACY_ACCEPTED_STATUS ? STATUS_ACCEPTED : status;
    }

    private static boolean isPlaceUsable(String status) {
        return status != null && PLACE_STATUS_AVAILABLE.equals(status.trim().toLowerCase());
    }

    private static String buildPlaceLabel(Place place) {
        if (place == null) {
            return "Place inconnue";
        }
        return "Place " + place.getNumero() + " (rang " + place.getRang() + ", colonne " + place.getColonne() + ")";
    }
}
