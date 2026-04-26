package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.Models.Reservation;
import tn.esprit.fahamni.utils.DatabaseSchemaUtils;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    public static final int MIN_STUDENT_RATING = 1;
    public static final int MAX_STUDENT_RATING = 5;
    private static final int MAX_STUDENT_REVIEW_LENGTH = 1000;
    public static final String PAYMENT_STATUS_NOT_REQUIRED = "not_required";
    public static final String PAYMENT_STATUS_PENDING = "pending";
    public static final String PAYMENT_STATUS_COMPLETED = "completed";
    public static final String PAYMENT_STATUS_FAILED = "failed";
    public static final String PAYMENT_STATUS_EXPIRED = "expired";
    private static final String PAYMENT_PROVIDER = "stripe";

    private final Connection cnx = MyDataBase.getInstance().getCnx();
    private final StripePaymentService stripePaymentService = new StripePaymentService();
    private final PaymentReceiptEmailService paymentReceiptEmailService = new PaymentReceiptEmailService();

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

    public ReservationService() {
        ensurePaymentSchema();
    }

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
        ReservationCreationResult result = reserveSeanceDetailed(seance, participantId);
        return result.success()
            ? OperationResult.success(result.message())
            : OperationResult.failure(result.message());
    }

    public ReservationCreationResult reserveSeanceDetailed(Seance seance, int participantId) {
        if (cnx == null) {
            return ReservationCreationResult.failure("Connexion a la base indisponible.");
        }
        if (seance == null || seance.getId() <= 0) {
            return ReservationCreationResult.failure("Selectionnez une seance valide.");
        }
        if (participantId <= 0) {
            return ReservationCreationResult.failure("Participant invalide pour la reservation.");
        }
        if (seance.getTuteurId() == participantId) {
            return ReservationCreationResult.failure("Vous ne pouvez pas reserver votre propre seance.");
        }
        if (seance.getStatus() != 1) {
            return ReservationCreationResult.failure("Seules les seances publiees peuvent etre reservees.");
        }
        if (seance.getStartAt() != null && seance.getStartAt().isBefore(LocalDateTime.now())) {
            return ReservationCreationResult.failure("Cette seance est deja passee.");
        }

        try {
            if (!participantExists(participantId)) {
                return ReservationCreationResult.failure("Participant introuvable dans la base.");
            }
            if (hasActiveReservation(seance.getId(), participantId)) {
                return ReservationCreationResult.failure("Vous avez deja reserve cette seance.");
            }
            if (countActiveReservationsBySeanceId(seance.getId()) >= seance.getMaxParticipants()) {
                return ReservationCreationResult.failure("Cette seance est complete.");
            }

            int reservationId = insertReservation(seance.getId(), participantId);
            boolean paymentRequired = seance.getPrice() > 0.0;
            return new ReservationCreationResult(
                true,
                paymentRequired
                    ? "Reservation creee. Un paiement Stripe est requis avant validation par le tuteur."
                    : "Reservation envoyee avec succes. Statut: en attente.",
                reservationId,
                paymentRequired
            );
        } catch (SQLException e) {
            return ReservationCreationResult.failure("Reservation impossible: " + e.getMessage());
        }
    }

    public PaymentLaunchResult startStripeCheckoutPayment(int reservationId, int participantId) {
        if (cnx == null) {
            return PaymentLaunchResult.failure("Connexion a la base indisponible.");
        }
        if (reservationId <= 0 || participantId <= 0) {
            return PaymentLaunchResult.failure("Reservation invalide pour le paiement.");
        }

        String sql = """
            SELECT
                r.id AS reservation_id,
                r.status AS reservation_status,
                r.cancell_at,
                s.id AS seance_id,
                s.matiere,
                s.price_tnd,
                u.full_name,
                u.email
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            INNER JOIN user u ON u.id = r.participant_id
            WHERE r.id = ? AND r.participant_id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, reservationId);
            pst.setInt(2, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return PaymentLaunchResult.failure("Reservation introuvable pour ce participant.");
                }
                if (rs.getTimestamp("cancell_at") != null) {
                    return PaymentLaunchResult.failure("Cette reservation est annulee.");
                }

                int currentStatus = normalizeStoredStatus(rs.getInt("reservation_status"));
                if (currentStatus == STATUS_REFUSED) {
                    return PaymentLaunchResult.failure("Cette reservation a ete refusee.");
                }

                BigDecimal price = rs.getBigDecimal("price_tnd");
                int amountMillimes = priceTndToMillimes(price);
                if (amountMillimes <= 0) {
                    return PaymentLaunchResult.failure("Cette seance est gratuite. Aucun paiement n'est necessaire.");
                }

                PaymentSnapshot currentPayment = findPaymentSnapshot(reservationId);
                if (currentPayment != null && PAYMENT_STATUS_COMPLETED.equals(currentPayment.status())) {
                    return new PaymentLaunchResult(
                        true,
                        "Le paiement Stripe est deja confirme pour cette reservation.",
                        reservationId,
                        currentPayment.paymentRef(),
                        currentPayment.paymentUrl(),
                        currentPayment.status()
                    );
                }

                StripePaymentService.CreatePaymentRequest request = new StripePaymentService.CreatePaymentRequest(
                    buildStripeCheckoutOrderId(reservationId),
                    price.doubleValue(),
                    "Reservation Fahamni - " + safeText(rs.getString("matiere")),
                    rs.getString("email")
                );

                StripePaymentService.PaymentInitialization initialization = stripePaymentService.initializePayment(request);
                if (!initialization.success()) {
                    return PaymentLaunchResult.failure(initialization.message());
                }

                upsertPaymentSnapshot(
                    reservationId,
                    amountMillimes,
                    PAYMENT_STATUS_PENDING,
                    initialization.sessionId(),
                    initialization.checkoutUrl(),
                    initialization.sessionStatus(),
                    initialization.paymentStatus(),
                    initialization.providerPayload(),
                    LocalDateTime.now(),
                    null,
                    null
                );

                return new PaymentLaunchResult(
                    true,
                    "Paiement Stripe initialise. Finalisez le paiement dans la fenetre ouverte.",
                    reservationId,
                    initialization.sessionId(),
                    initialization.checkoutUrl(),
                    PAYMENT_STATUS_PENDING
                );
            }
        } catch (SQLException exception) {
            return PaymentLaunchResult.failure("Initialisation du paiement impossible: " + exception.getMessage());
        }
    }

    public PaymentVerificationResult refreshReservationPaymentStatus(int reservationId, int participantId) {
        if (cnx == null) {
            return PaymentVerificationResult.failure("Connexion a la base indisponible.");
        }
        if (reservationId <= 0 || participantId <= 0) {
            return PaymentVerificationResult.failure("Reservation invalide pour verifier le paiement.");
        }

        String sql = """
            SELECT
                r.id AS reservation_id,
                r.cancell_at,
                p.payment_ref,
                p.payment_url,
                p.amount_millimes,
                p.status AS payment_status
            FROM reservation r
            LEFT JOIN reservation_payment p ON p.reservation_id = r.id
            WHERE r.id = ? AND r.participant_id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, reservationId);
            pst.setInt(2, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return PaymentVerificationResult.failure("Reservation introuvable pour ce participant.");
                }
                if (rs.getTimestamp("cancell_at") != null) {
                    return PaymentVerificationResult.failure("Cette reservation est annulee.");
                }

                String currentStatus = normalizePaymentStatus(rs.getString("payment_status"));
                if (currentStatus == null) {
                    return PaymentVerificationResult.failure("Aucun paiement Stripe n'est encore initialise pour cette reservation.");
                }
                if (PAYMENT_STATUS_COMPLETED.equals(currentStatus)) {
                    ReceiptEmailDispatchResult receiptDispatchResult = ensurePaymentReceiptSent(reservationId);
                    return new PaymentVerificationResult(
                        true,
                        appendDetailMessage("Le paiement Stripe est deja confirme.", receiptDispatchResult.message()),
                        currentStatus,
                        rs.getString("payment_ref"),
                        rs.getString("payment_url")
                    );
                }

                StripePaymentService.PaymentSessionDetails sessionDetails =
                    stripePaymentService.fetchCheckoutSession(rs.getString("payment_ref"));
                if (!sessionDetails.success()) {
                    return PaymentVerificationResult.failure(sessionDetails.message());
                }

                String normalizedStatus = normalizePaymentStatus(sessionDetails.normalizedStatus());
                String providerStatus = sessionDetails.sessionStatus();
                String transactionStatus = sessionDetails.paymentStatus();
                String providerPayload = sessionDetails.providerPayload();
                String paymentUrl = sessionDetails.checkoutUrl() != null ? sessionDetails.checkoutUrl() : rs.getString("payment_url");

                LocalDateTime checkedAt = LocalDateTime.now();
                LocalDateTime completedAt = PAYMENT_STATUS_COMPLETED.equals(normalizedStatus) ? checkedAt : null;
                upsertPaymentSnapshot(
                    reservationId,
                    rs.getInt("amount_millimes"),
                    normalizedStatus,
                    rs.getString("payment_ref"),
                    paymentUrl,
                    providerStatus,
                    transactionStatus,
                    providerPayload,
                    null,
                    checkedAt,
                    completedAt
                );

                ReceiptEmailDispatchResult receiptDispatchResult = PAYMENT_STATUS_COMPLETED.equals(normalizedStatus)
                    ? ensurePaymentReceiptSent(reservationId)
                    : ReceiptEmailDispatchResult.none();

                return new PaymentVerificationResult(
                    true,
                    appendDetailMessage(buildPaymentVerificationMessage(normalizedStatus), receiptDispatchResult.message()),
                    normalizedStatus,
                    rs.getString("payment_ref"),
                    paymentUrl
                );
            }
        } catch (SQLException exception) {
            return PaymentVerificationResult.failure("Verification du paiement impossible: " + exception.getMessage());
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
                s.price_tnd,
                r.student_rating,
                r.student_review,
                r.rated_at,
                p.amount_millimes,
                p.status AS payment_status,
                p.payment_ref,
                p.payment_url,
                p.initiated_at,
                p.completed_at
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            LEFT JOIN reservation_payment p ON p.reservation_id = r.id
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
                        getNullablePriceTnd(rs, "price_tnd"),
                        getNullableInteger(rs, "amount_millimes"),
                        normalizePaymentStatus(rs.getString("payment_status")),
                        rs.getString("payment_ref"),
                        rs.getString("payment_url"),
                        readDateTime(rs, "initiated_at"),
                        readDateTime(rs, "completed_at"),
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

                String paymentValidationError = validatePaymentBeforeAcceptance(reservationId);
                if (paymentValidationError != null) {
                    return OperationResult.failure(paymentValidationError);
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
        Double sessionPriceTnd,
        Integer paymentAmountMillimes,
        String paymentStatus,
        String paymentRef,
        String paymentUrl,
        LocalDateTime paymentInitiatedAt,
        LocalDateTime paymentCompletedAt,
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

        public boolean requiresPayment() {
            return (paymentAmountMillimes != null && paymentAmountMillimes > 0)
                || (sessionPriceTnd != null && sessionPriceTnd > 0.0);
        }

        public boolean isPaymentCompleted() {
            return PAYMENT_STATUS_COMPLETED.equals(paymentStatus);
        }

        public boolean canLaunchPayment() {
            return requiresPayment() && !isPaymentCompleted() && !isRefused();
        }

        public boolean hasRating() {
            return studentRating != null
                && studentRating >= MIN_STUDENT_RATING
                && studentRating <= MAX_STUDENT_RATING;
        }
    }

    public record ReservationCreationResult(
        boolean success,
        String message,
        int reservationId,
        boolean paymentRequired
    ) {
        public static ReservationCreationResult failure(String message) {
            return new ReservationCreationResult(false, message, 0, false);
        }
    }

    public record PaymentLaunchResult(
        boolean success,
        String message,
        int reservationId,
        String paymentRef,
        String paymentUrl,
        String paymentStatus
    ) {
        public static PaymentLaunchResult failure(String message) {
            return new PaymentLaunchResult(false, message, 0, null, null, null);
        }
    }

    public record PaymentVerificationResult(
        boolean success,
        String message,
        String paymentStatus,
        String paymentRef,
        String paymentUrl
    ) {
        public static PaymentVerificationResult failure(String message) {
            return new PaymentVerificationResult(false, message, null, null, null);
        }
    }

    private record PaymentSnapshot(
        int reservationId,
        int amountMillimes,
        String status,
        String paymentRef,
        String paymentUrl,
        String providerStatus,
        String transactionStatus,
        LocalDateTime initiatedAt,
        LocalDateTime lastCheckedAt,
        LocalDateTime completedAt,
        String receiptEmail,
        LocalDateTime receiptEmailSentAt
    ) {
    }

    private record PaymentReceiptContext(
        int reservationId,
        String participantName,
        String participantEmail,
        String tutorName,
        String seanceTitle,
        LocalDateTime seanceStartAt,
        int durationMin,
        int amountMillimes,
        String paymentRef,
        LocalDateTime paymentCompletedAt
    ) {
    }

    private record ReceiptEmailDispatchResult(String message) {
        private static ReceiptEmailDispatchResult none() {
            return new ReceiptEmailDispatchResult(null);
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

    private String validatePaymentBeforeAcceptance(int reservationId) throws SQLException {
        if (reservationId <= 0 || cnx == null) {
            return null;
        }

        String sql = """
            SELECT
                s.price_tnd,
                p.status AS payment_status
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            LEFT JOIN reservation_payment p ON p.reservation_id = r.id
            WHERE r.id = ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, reservationId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return "Reservation introuvable.";
                }

                BigDecimal price = rs.getBigDecimal("price_tnd");
                if (priceTndToMillimes(price) <= 0) {
                    return null;
                }

                String paymentStatus = normalizePaymentStatus(rs.getString("payment_status"));
                if (PAYMENT_STATUS_COMPLETED.equals(paymentStatus)) {
                    return null;
                }
                return "Le paiement Stripe doit etre confirme avant d'accepter cette reservation.";
            }
        }
    }

    private PaymentSnapshot findPaymentSnapshot(int reservationId) throws SQLException {
        if (reservationId <= 0 || cnx == null || !DatabaseSchemaUtils.tableExists(cnx, "reservation_payment")) {
            return null;
        }

        String sql = """
            SELECT
                reservation_id,
                amount_millimes,
                status,
                payment_ref,
                payment_url,
                provider_status,
                transaction_status,
                initiated_at,
                last_checked_at,
                completed_at,
                receipt_email,
                receipt_email_sent_at
            FROM reservation_payment
            WHERE reservation_id = ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, reservationId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PaymentSnapshot(
                    rs.getInt("reservation_id"),
                    rs.getInt("amount_millimes"),
                    normalizePaymentStatus(rs.getString("status")),
                    rs.getString("payment_ref"),
                    rs.getString("payment_url"),
                    rs.getString("provider_status"),
                    rs.getString("transaction_status"),
                    readDateTime(rs, "initiated_at"),
                    readDateTime(rs, "last_checked_at"),
                    readDateTime(rs, "completed_at"),
                    rs.getString("receipt_email"),
                    readDateTime(rs, "receipt_email_sent_at")
                );
            }
        }
    }

    private ReceiptEmailDispatchResult ensurePaymentReceiptSent(int reservationId) throws SQLException {
        PaymentSnapshot snapshot = findPaymentSnapshot(reservationId);
        if (snapshot == null || !PAYMENT_STATUS_COMPLETED.equals(snapshot.status())) {
            return ReceiptEmailDispatchResult.none();
        }
        if (snapshot.receiptEmailSentAt() != null) {
            String sentTo = safeText(snapshot.receiptEmail());
            return new ReceiptEmailDispatchResult(
                sentTo.isEmpty()
                    ? "Le recu a deja ete envoye."
                    : "Le recu a deja ete envoye a " + sentTo + "."
            );
        }

        PaymentReceiptContext context = findPaymentReceiptContext(reservationId);
        if (context == null) {
            return new ReceiptEmailDispatchResult(
                "Le paiement est valide, mais les informations du recu sont introuvables."
            );
        }

        String participantEmail = safeText(context.participantEmail());
        if (participantEmail.isEmpty()) {
            return new ReceiptEmailDispatchResult(
                "Le paiement est valide, mais aucune adresse email n'est disponible pour le recu."
            );
        }
        if (!paymentReceiptEmailService.isConfigured()) {
            return new ReceiptEmailDispatchResult(
                "Le paiement est valide, mais l'envoi du recu email n'est pas configure."
            );
        }

        PaymentReceiptEmailService.EmailDeliveryResult emailDeliveryResult = paymentReceiptEmailService.sendPaymentReceipt(
            new PaymentReceiptEmailService.PaymentReceipt(
                context.reservationId(),
                context.participantName(),
                context.participantEmail(),
                context.tutorName(),
                context.seanceTitle(),
                context.seanceStartAt(),
                context.durationMin(),
                context.amountMillimes(),
                context.paymentRef(),
                context.paymentCompletedAt()
            )
        );
        if (!emailDeliveryResult.success()) {
            return new ReceiptEmailDispatchResult(
                "Le paiement est valide, mais le recu email n'a pas pu etre envoye: "
                    + emailDeliveryResult.message()
            );
        }

        markReceiptEmailSent(reservationId, participantEmail, LocalDateTime.now());
        return new ReceiptEmailDispatchResult("Un recu a ete envoye a " + participantEmail + ".");
    }

    private PaymentReceiptContext findPaymentReceiptContext(int reservationId) throws SQLException {
        if (reservationId <= 0 || cnx == null) {
            return null;
        }

        String sql = """
            SELECT
                r.id AS reservation_id,
                student.full_name AS participant_name,
                student.email AS participant_email,
                tutor.full_name AS tutor_name,
                s.matiere,
                s.start_at,
                s.duration_min,
                p.amount_millimes,
                p.payment_ref,
                p.completed_at
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            INNER JOIN user student ON student.id = r.participant_id
            LEFT JOIN user tutor ON tutor.id = s.tuteur_id
            INNER JOIN reservation_payment p ON p.reservation_id = r.id
            WHERE r.id = ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, reservationId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PaymentReceiptContext(
                    rs.getInt("reservation_id"),
                    rs.getString("participant_name"),
                    rs.getString("participant_email"),
                    rs.getString("tutor_name"),
                    rs.getString("matiere"),
                    readDateTime(rs, "start_at"),
                    rs.getInt("duration_min"),
                    rs.getInt("amount_millimes"),
                    rs.getString("payment_ref"),
                    readDateTime(rs, "completed_at")
                );
            }
        }
    }

    private void markReceiptEmailSent(int reservationId, String email, LocalDateTime sentAt) throws SQLException {
        if (reservationId <= 0 || cnx == null || sentAt == null) {
            return;
        }

        String sql = """
            UPDATE reservation_payment
            SET receipt_email = ?,
                receipt_email_sent_at = ?,
                updated_at = ?
            WHERE reservation_id = ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setString(1, email);
            pst.setTimestamp(2, toTimestamp(sentAt));
            pst.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            pst.setInt(4, reservationId);
            pst.executeUpdate();
        }
    }

    private void upsertPaymentSnapshot(int reservationId,
                                       int amountMillimes,
                                       String status,
                                       String paymentRef,
                                       String paymentUrl,
                                       String providerStatus,
                                       String transactionStatus,
                                       String providerPayload,
                                       LocalDateTime initiatedAt,
                                       LocalDateTime lastCheckedAt,
                                       LocalDateTime completedAt) throws SQLException {
        if (reservationId <= 0 || cnx == null) {
            return;
        }

        PaymentSnapshot existing = findPaymentSnapshot(reservationId);
        LocalDateTime now = LocalDateTime.now();
        String sql;
        if (existing == null) {
            sql = """
                INSERT INTO reservation_payment (
                    reservation_id, provider, amount_millimes, currency_token, status, payment_ref, payment_url,
                    provider_status, transaction_status, provider_payload, initiated_at, last_checked_at,
                    completed_at, created_at, updated_at
                )
                VALUES (?, ?, ?, 'TND', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement pst = cnx.prepareStatement(sql)) {
                pst.setInt(1, reservationId);
                pst.setString(2, PAYMENT_PROVIDER);
                pst.setInt(3, amountMillimes);
                pst.setString(4, normalizePaymentStatus(status));
                pst.setString(5, paymentRef);
                pst.setString(6, paymentUrl);
                pst.setString(7, providerStatus);
                pst.setString(8, transactionStatus);
                pst.setString(9, providerPayload);
                pst.setTimestamp(10, toTimestamp(initiatedAt));
                pst.setTimestamp(11, toTimestamp(lastCheckedAt));
                pst.setTimestamp(12, toTimestamp(completedAt));
                pst.setTimestamp(13, Timestamp.valueOf(now));
                pst.setTimestamp(14, Timestamp.valueOf(now));
                pst.executeUpdate();
            }
            return;
        }

        sql = """
            UPDATE reservation_payment
            SET provider = ?,
                amount_millimes = ?,
                status = ?,
                payment_ref = ?,
                payment_url = ?,
                provider_status = ?,
                transaction_status = ?,
                provider_payload = ?,
                initiated_at = COALESCE(?, initiated_at),
                last_checked_at = COALESCE(?, last_checked_at),
                completed_at = COALESCE(?, completed_at),
                updated_at = ?
            WHERE reservation_id = ?
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setString(1, PAYMENT_PROVIDER);
            pst.setInt(2, amountMillimes > 0 ? amountMillimes : existing.amountMillimes());
            pst.setString(3, normalizePaymentStatus(status));
            pst.setString(4, paymentRef != null ? paymentRef : existing.paymentRef());
            pst.setString(5, paymentUrl != null ? paymentUrl : existing.paymentUrl());
            pst.setString(6, providerStatus != null ? providerStatus : existing.providerStatus());
            pst.setString(7, transactionStatus != null ? transactionStatus : existing.transactionStatus());
            pst.setString(8, providerPayload);
            pst.setTimestamp(9, toTimestamp(initiatedAt));
            pst.setTimestamp(10, toTimestamp(lastCheckedAt));
            pst.setTimestamp(11, toTimestamp(completedAt));
            pst.setTimestamp(12, Timestamp.valueOf(now));
            pst.setInt(13, reservationId);
            pst.executeUpdate();
        }
    }

    private void ensurePaymentSchema() {
        if (cnx == null) {
            return;
        }
        try {
            if (!DatabaseSchemaUtils.columnExists(cnx, "seance", "price_tnd")) {
                DatabaseSchemaUtils.executeDdl(
                    cnx,
                    "ALTER TABLE seance ADD COLUMN price_tnd DECIMAL(10,3) NULL DEFAULT NULL"
                );
            }
            if (!DatabaseSchemaUtils.tableExists(cnx, "reservation_payment")) {
                DatabaseSchemaUtils.executeDdl(
                    cnx,
                    """
                    CREATE TABLE reservation_payment (
                        reservation_id INT PRIMARY KEY,
                        provider VARCHAR(30) NOT NULL DEFAULT 'stripe',
                        amount_millimes INT NOT NULL,
                        currency_token VARCHAR(10) NOT NULL DEFAULT 'TND',
                        status VARCHAR(30) NOT NULL DEFAULT 'pending',
                        payment_ref VARCHAR(120) NULL,
                        payment_url TEXT NULL,
                        provider_status VARCHAR(60) NULL,
                        transaction_status VARCHAR(60) NULL,
                        provider_payload TEXT NULL,
                        initiated_at DATETIME NULL,
                        last_checked_at DATETIME NULL,
                        completed_at DATETIME NULL,
                        receipt_email VARCHAR(255) NULL,
                        receipt_email_sent_at DATETIME NULL,
                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NULL,
                        CONSTRAINT fk_reservation_payment_reservation
                            FOREIGN KEY (reservation_id) REFERENCES reservation(id)
                            ON DELETE CASCADE
                    )
                    """
                );
            }
            if (!DatabaseSchemaUtils.columnExists(cnx, "reservation_payment", "receipt_email")) {
                DatabaseSchemaUtils.executeDdl(
                    cnx,
                    "ALTER TABLE reservation_payment ADD COLUMN receipt_email VARCHAR(255) NULL AFTER completed_at"
                );
            }
            if (!DatabaseSchemaUtils.columnExists(cnx, "reservation_payment", "receipt_email_sent_at")) {
                DatabaseSchemaUtils.executeDdl(
                    cnx,
                    "ALTER TABLE reservation_payment ADD COLUMN receipt_email_sent_at DATETIME NULL AFTER receipt_email"
                );
            }
        } catch (SQLException exception) {
            System.out.println("Bootstrap schema paiement impossible: " + exception.getMessage());
        }
    }

    private Double getNullablePriceTnd(ResultSet rs, String columnName) throws SQLException {
        BigDecimal value = rs.getBigDecimal(columnName);
        return value == null ? null : value.doubleValue();
    }

    private int priceTndToMillimes(BigDecimal priceTnd) {
        if (priceTnd == null) {
            return 0;
        }
        return priceTnd.multiply(BigDecimal.valueOf(1000L)).intValue();
    }

    private String buildStripeCheckoutOrderId(int reservationId) {
        return "fahamni-reservation-" + reservationId + "-" + System.currentTimeMillis();
    }

    private String buildPaymentVerificationMessage(String paymentStatus) {
        String normalizedStatus = normalizePaymentStatus(paymentStatus);
        if (PAYMENT_STATUS_COMPLETED.equals(normalizedStatus)) {
            return "Paiement Stripe confirme avec succes.";
        }
        if (PAYMENT_STATUS_FAILED.equals(normalizedStatus)) {
            return "Le paiement Stripe a echoue. Vous pouvez relancer un nouveau lien.";
        }
        if (PAYMENT_STATUS_EXPIRED.equals(normalizedStatus)) {
            return "Le lien de paiement Stripe a expire. Generez un nouveau lien.";
        }
        return "Le paiement Stripe est encore en attente. Verifiez apres finalisation dans le navigateur.";
    }

    private String appendDetailMessage(String baseMessage, String detailMessage) {
        String normalizedBaseMessage = safeText(baseMessage);
        String normalizedDetailMessage = safeText(detailMessage);
        if (normalizedDetailMessage.isEmpty()) {
            return normalizedBaseMessage;
        }
        if (normalizedBaseMessage.isEmpty()) {
            return normalizedDetailMessage;
        }
        return normalizedBaseMessage + " " + normalizedDetailMessage;
    }

    private String normalizePaymentStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        return switch (normalized) {
            case PAYMENT_STATUS_COMPLETED -> PAYMENT_STATUS_COMPLETED;
            case PAYMENT_STATUS_FAILED -> PAYMENT_STATUS_FAILED;
            case PAYMENT_STATUS_EXPIRED -> PAYMENT_STATUS_EXPIRED;
            case PAYMENT_STATUS_NOT_REQUIRED -> PAYMENT_STATUS_NOT_REQUIRED;
            default -> PAYMENT_STATUS_PENDING;
        };
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? "" : normalized;
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
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

    private int insertReservation(int seanceId, int participantId) throws SQLException {
        String sql = """
            INSERT INTO reservation (
                status, reserved_at, cancell_at, notes, seance_id, participant_id,
                confirmation_email_sent_at, acceptance_email_sent_at, reminder_email_sent_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement pst = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            throw new SQLException("Identifiant reservation non genere.");
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
