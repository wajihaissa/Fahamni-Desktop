package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MatchingService {

    public static final String VISIBILITY_PRIVATE = "individuelle";
    public static final String VISIBILITY_PUBLIC = "publique";

    public static final int DECISION_PASS = -1;
    public static final int DECISION_INTERESTED = 1;
    public static final int DECISION_SUPER = 2;

    private static final String REQUEST_STATUS_PENDING = "pending";
    private static final String REQUEST_STATUS_MATCHING = "matching";
    private static final String REQUEST_STATUS_ACCEPTED = "accepted";
    private static final String REQUEST_STATUS_PLANNED = "planned";
    private static final String REQUEST_STATUS_REFUSED = "refused";
    private static final String GENERIC_SERVICE_UNAVAILABLE_MESSAGE =
        "Le service est temporairement indisponible. Veuillez reessayer dans un instant.";
    private static final String SESSION_RECONNECT_MESSAGE =
        "Votre session n'est plus valide. Veuillez vous reconnecter.";
    private static final String MATCHING_PROPOSAL_UNAVAILABLE_MESSAGE =
        "Cette proposition n'est plus disponible. Actualisez le matching puis reessayez.";
    private static final String MATCHING_REQUEST_UNAVAILABLE_MESSAGE =
        "Cette demande n'est plus disponible. Actualisez la liste puis reessayez.";

    private final Connection cnx = MyDataBase.getInstance().getCnx();
    private final GeminiMatchingAnalysisService geminiMatchingAnalysisService = new GeminiMatchingAnalysisService();
    private boolean schemaEnsured;

    public MatchingService() {
        ensureSchema();
    }

    public MatchingRequestCreation createMatchingRequest(MatchingDraft draft) {
        if (cnx == null) {
            return MatchingRequestCreation.failure(GENERIC_SERVICE_UNAVAILABLE_MESSAGE);
        }

        String validationError = validateDraft(draft);
        if (validationError != null) {
            return MatchingRequestCreation.failure(validationError);
        }

        AnalysisResolution analysisResolution = analyzeNeed(draft);
        if (!analysisResolution.success()) {
            return MatchingRequestCreation.failure(analysisResolution.errorMessage());
        }

        MatchingNeedProfile needProfile = analysisResolution.needProfile();
        String normalizedSubject = normalizeSubject(draft.subject());
        String normalizedMode = normalizeMode(draft.mode());
        String normalizedVisibility = normalizeVisibility(draft.visibilityScope());
        String normalizedObjective = normalizeOptionalText(draft.objectiveText());
        List<RawTutorCandidate> candidates = loadCompatibleTutors(
            normalizedSubject,
            draft.startAt(),
            draft.durationMin(),
            normalizedMode,
            needProfile
        );

        String insertRequestSql = """
            INSERT INTO matching_request (
                participant_id, matiere, start_at, duration_min, mode_seance, visibility_scope,
                objective_text, objective_summary, need_keywords, requested_level,
                status, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pst = cnx.prepareStatement(insertRequestSql, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now();
            pst.setInt(1, draft.participantId());
            pst.setString(2, normalizedSubject);
            pst.setTimestamp(3, Timestamp.valueOf(draft.startAt()));
            pst.setInt(4, draft.durationMin());
            pst.setString(5, normalizedMode);
            pst.setString(6, normalizedVisibility);
            pst.setString(7, normalizedObjective);
            pst.setString(8, needProfile.summary());
            pst.setString(9, String.join(", ", needProfile.keywords()));
            pst.setString(10, needProfile.level());
            pst.setString(11, candidates.isEmpty() ? REQUEST_STATUS_PENDING : REQUEST_STATUS_MATCHING);
            pst.setTimestamp(12, Timestamp.valueOf(now));
            pst.setTimestamp(13, Timestamp.valueOf(now));
            pst.executeUpdate();

            int requestId = 0;
            try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    requestId = generatedKeys.getInt(1);
                }
            }

            if (requestId <= 0) {
                return MatchingRequestCreation.failure("Creation de la demande de matching impossible.");
            }

            List<StudentMatchCard> cards = insertCandidates(requestId, candidates);
            String message = cards.isEmpty()
                ? "Aucun tuteur compatible n'est disponible sur ce creneau pour le moment. Votre demande a bien ete enregistree."
                : cards.size() + " profil(s) compatible(s) sont prets pour le swipe.";
            return new MatchingRequestCreation(requestId, true, message, needProfile, cards);
        } catch (SQLException e) {
            logMatchingError("Creation du matching impossible", e);
            return MatchingRequestCreation.failure("Votre demande n'a pas pu etre enregistree pour le moment.");
        }
    }

    public OperationResult recordStudentDecision(int candidateId, int participantId, int decision) {
        if (cnx == null) {
            return OperationResult.failure(GENERIC_SERVICE_UNAVAILABLE_MESSAGE);
        }
        if (candidateId <= 0) {
            return OperationResult.failure(MATCHING_PROPOSAL_UNAVAILABLE_MESSAGE);
        }
        if (participantId <= 0) {
            return OperationResult.failure(SESSION_RECONNECT_MESSAGE);
        }
        if (decision != DECISION_PASS && decision != DECISION_INTERESTED && decision != DECISION_SUPER) {
            return OperationResult.failure("Cette action n'est pas disponible pour le moment.");
        }

        String selectSql = """
            SELECT mr.id AS request_id, mr.status
            FROM matching_candidate mc
            INNER JOIN matching_request mr ON mr.id = mc.request_id
            WHERE mc.id = ? AND mr.participant_id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(selectSql)) {
            pst.setInt(1, candidateId);
            pst.setInt(2, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return OperationResult.failure(MATCHING_PROPOSAL_UNAVAILABLE_MESSAGE);
                }
                String requestStatus = normalizeStatus(rs.getString("status"));
                if (REQUEST_STATUS_ACCEPTED.equals(requestStatus) || REQUEST_STATUS_PLANNED.equals(requestStatus)) {
                    return OperationResult.failure(MATCHING_PROPOSAL_UNAVAILABLE_MESSAGE);
                }
            }
        } catch (SQLException e) {
            logMatchingError("Verification du matching impossible", e);
            return OperationResult.failure("Votre choix n'a pas pu etre traite pour le moment.");
        }

        String updateCandidateSql = """
            UPDATE matching_candidate
            SET student_decision = ?, responded_at = ?
            WHERE id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(updateCandidateSql)) {
            pst.setInt(1, decision);
            pst.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            pst.setInt(3, candidateId);
            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure(MATCHING_PROPOSAL_UNAVAILABLE_MESSAGE);
            }
            return OperationResult.success("Votre choix a bien ete enregistre.");
        } catch (SQLException e) {
            logMatchingError("Enregistrement du swipe impossible", e);
            return OperationResult.failure("Votre choix n'a pas pu etre enregistre pour le moment.");
        }
    }

    public List<TutorMatchInboxItem> getTutorPendingMatches(int tutorId) {
        List<TutorMatchInboxItem> inbox = new ArrayList<>();
        if (tutorId <= 0 || cnx == null) {
            return inbox;
        }

        String sql = """
            SELECT
                mc.id AS candidate_id,
                mc.request_id,
                mc.compatibility_score,
                mc.match_reason,
                mc.supporting_signals,
                mc.student_decision,
                mr.participant_id,
                COALESCE(u.full_name, CONCAT('Etudiant #', mr.participant_id)) AS participant_name,
                mr.matiere,
                mr.start_at,
                mr.duration_min,
                mr.mode_seance,
                mr.visibility_scope,
                mr.objective_text,
                mr.objective_summary,
                mr.need_keywords,
                mr.requested_level,
                mr.created_at
            FROM matching_candidate mc
            INNER JOIN matching_request mr ON mr.id = mc.request_id
            LEFT JOIN user u ON u.id = mr.participant_id
            WHERE mc.tutor_id = ?
              AND mc.student_decision IN (?, ?)
              AND mc.tutor_decision IS NULL
              AND mr.status = ?
            ORDER BY mc.student_decision DESC, mc.compatibility_score DESC, mr.created_at DESC
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, tutorId);
            pst.setInt(2, DECISION_INTERESTED);
            pst.setInt(3, DECISION_SUPER);
            pst.setString(4, REQUEST_STATUS_MATCHING);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    inbox.add(new TutorMatchInboxItem(
                        rs.getInt("candidate_id"),
                        rs.getInt("request_id"),
                        rs.getInt("participant_id"),
                        safeText(rs.getString("participant_name"), "Etudiant #" + rs.getInt("participant_id")),
                        rs.getString("matiere"),
                        readDateTime(rs, "start_at"),
                        rs.getInt("duration_min"),
                        normalizeMode(rs.getString("mode_seance")),
                        normalizeVisibility(rs.getString("visibility_scope")),
                        normalizeOptionalText(rs.getString("objective_text")),
                        safeText(rs.getString("objective_summary"), "Besoin en attente de detail complementaire."),
                        parseKeywords(rs.getString("need_keywords")),
                        safeText(rs.getString("requested_level"), "Niveau non precise"),
                        rs.getInt("student_decision"),
                        rs.getDouble("compatibility_score"),
                        safeText(rs.getString("match_reason"), "Profil compatible avec la demande."),
                        parseSignals(rs.getString("supporting_signals")),
                        readDateTime(rs, "created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Chargement des demandes de matching impossible: " + e.getMessage());
        }
        return inbox;
    }

    public MatchingAcceptanceResult acceptTutorMatch(int candidateId, int tutorId) {
        if (cnx == null) {
            return MatchingAcceptanceResult.failure(GENERIC_SERVICE_UNAVAILABLE_MESSAGE);
        }
        if (candidateId <= 0) {
            return MatchingAcceptanceResult.failure(MATCHING_REQUEST_UNAVAILABLE_MESSAGE);
        }
        if (tutorId <= 0) {
            return MatchingAcceptanceResult.failure(SESSION_RECONNECT_MESSAGE);
        }

        TutorMatchInboxItem item = loadTutorMatchCandidate(candidateId, tutorId);
        if (item == null) {
            return MatchingAcceptanceResult.failure(MATCHING_REQUEST_UNAVAILABLE_MESSAGE);
        }

        String updateCandidateSql = """
            UPDATE matching_candidate
            SET tutor_decision = ?, responded_at = ?
            WHERE id = ? AND tutor_id = ?
            """;
        String updateRequestSql = """
            UPDATE matching_request
            SET status = ?, accepted_tutor_id = ?, updated_at = ?
            WHERE id = ?
            """;

        try (PreparedStatement updateCandidate = cnx.prepareStatement(updateCandidateSql);
             PreparedStatement updateRequest = cnx.prepareStatement(updateRequestSql)) {
            LocalDateTime now = LocalDateTime.now();

            updateCandidate.setInt(1, DECISION_INTERESTED);
            updateCandidate.setTimestamp(2, Timestamp.valueOf(now));
            updateCandidate.setInt(3, candidateId);
            updateCandidate.setInt(4, tutorId);
            updateCandidate.executeUpdate();

            updateRequest.setString(1, REQUEST_STATUS_ACCEPTED);
            updateRequest.setInt(2, tutorId);
            updateRequest.setTimestamp(3, Timestamp.valueOf(now));
            updateRequest.setInt(4, item.requestId());
            updateRequest.executeUpdate();

            return new MatchingAcceptanceResult(
                true,
                "La demande a ete acceptee. Vous pouvez maintenant planifier la seance.",
                new MatchingPlanContext(
                    item.requestId(),
                    item.participantId(),
                    item.participantName(),
                    item.subject(),
                    item.requestedStartAt(),
                    item.durationMin(),
                    item.mode(),
                    item.visibilityScope(),
                    item.objectiveText(),
                    item.objectiveSummary()
                )
            );
        } catch (SQLException e) {
            logMatchingError("Acceptation du matching impossible", e);
            return MatchingAcceptanceResult.failure("La demande n'a pas pu etre acceptee pour le moment.");
        }
    }

    public OperationResult refuseTutorMatch(int candidateId, int tutorId) {
        if (cnx == null) {
            return OperationResult.failure(GENERIC_SERVICE_UNAVAILABLE_MESSAGE);
        }
        if (candidateId <= 0) {
            return OperationResult.failure(MATCHING_REQUEST_UNAVAILABLE_MESSAGE);
        }
        if (tutorId <= 0) {
            return OperationResult.failure(SESSION_RECONNECT_MESSAGE);
        }

        String sql = """
            UPDATE matching_candidate
            SET tutor_decision = ?, responded_at = ?
            WHERE id = ? AND tutor_id = ? AND tutor_decision IS NULL
            """;

        Integer requestId = null;
        try (PreparedStatement select = cnx.prepareStatement("SELECT request_id FROM matching_candidate WHERE id = ? AND tutor_id = ?")) {
            select.setInt(1, candidateId);
            select.setInt(2, tutorId);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    requestId = rs.getInt("request_id");
                }
            }
        } catch (SQLException e) {
            logMatchingError("Verification du matching impossible", e);
            return OperationResult.failure("La demande n'a pas pu etre verifiee pour le moment.");
        }

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, DECISION_PASS);
            pst.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            pst.setInt(3, candidateId);
            pst.setInt(4, tutorId);
            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure(MATCHING_REQUEST_UNAVAILABLE_MESSAGE);
            }
            if (requestId != null && requestId > 0) {
                refreshRequestStatusAfterTutorDecision(requestId);
            }
            return OperationResult.success("La demande a ete refusee.");
        } catch (SQLException e) {
            logMatchingError("Refus du matching impossible", e);
            return OperationResult.failure("La demande n'a pas pu etre refusee pour le moment.");
        }
    }

    public OperationResult linkPlannedSession(int requestId,
                                              int participantId,
                                              String visibilityScope,
                                              int seanceId) {
        if (cnx == null) {
            return OperationResult.failure(GENERIC_SERVICE_UNAVAILABLE_MESSAGE);
        }
        if (requestId <= 0 || participantId <= 0 || seanceId <= 0) {
            return OperationResult.failure("La planification n'a pas pu etre finalisee pour le moment.");
        }

        String normalizedVisibility = normalizeVisibility(visibilityScope);

        String upsertVisibilitySql = """
            INSERT INTO matching_session_visibility (
                seance_id, request_id, participant_id, visibility_scope, created_at
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                request_id = VALUES(request_id),
                participant_id = VALUES(participant_id),
                visibility_scope = VALUES(visibility_scope)
            """;
        String updateRequestSql = """
            UPDATE matching_request
            SET status = ?, planned_seance_id = ?, updated_at = ?
            WHERE id = ?
            """;

        boolean initialAutoCommit = true;
        try {
            initialAutoCommit = cnx.getAutoCommit();
            if (initialAutoCommit) {
                cnx.setAutoCommit(false);
            }

            LocalDateTime now = LocalDateTime.now();

            try (PreparedStatement visibilityPst = cnx.prepareStatement(upsertVisibilitySql);
                 PreparedStatement requestPst = cnx.prepareStatement(updateRequestSql)) {
                visibilityPst.setInt(1, seanceId);
                visibilityPst.setInt(2, requestId);
                visibilityPst.setInt(3, participantId);
                visibilityPst.setString(4, normalizedVisibility);
                visibilityPst.setTimestamp(5, Timestamp.valueOf(now));
                visibilityPst.executeUpdate();

                OperationResult reservationLink = ensureMatchingReservation(seanceId, participantId, now);
                if (!reservationLink.isSuccess()) {
                    cnx.rollback();
                    return reservationLink;
                }

                requestPst.setString(1, REQUEST_STATUS_PLANNED);
                requestPst.setInt(2, seanceId);
                requestPst.setTimestamp(3, Timestamp.valueOf(now));
                requestPst.setInt(4, requestId);
                requestPst.executeUpdate();
            }

            cnx.commit();

            String message = VISIBILITY_PRIVATE.equals(normalizedVisibility)
                ? "La seance individuelle est liee au matching et la reservation de l'etudiant a ete creee automatiquement."
                : "La seance est liee au matching, la reservation de l'etudiant a ete creee et la seance pourra apparaitre dans le catalogue si elle est publiee.";
            return OperationResult.success(message);
        } catch (SQLException e) {
            try {
                cnx.rollback();
            } catch (SQLException rollbackException) {
                System.out.println("Rollback matching impossible: " + rollbackException.getMessage());
            }
            logMatchingError("Liaison de la seance au matching impossible", e);
            return OperationResult.failure("La planification n'a pas pu etre finalisee pour le moment.");
        } finally {
            try {
                cnx.setAutoCommit(initialAutoCommit);
            } catch (SQLException e) {
                System.out.println("Reinitialisation autocommit matching impossible: " + e.getMessage());
            }
        }
    }

    public Map<Integer, SessionVisibility> getSessionVisibilityBySeanceIds(List<Integer> seanceIds) {
        LinkedHashMap<Integer, SessionVisibility> visibilityBySeanceId = new LinkedHashMap<>();
        if (cnx == null || seanceIds == null || seanceIds.isEmpty()) {
            return visibilityBySeanceId;
        }

        StringBuilder sql = new StringBuilder("""
            SELECT seance_id, request_id, participant_id, visibility_scope
            FROM matching_session_visibility
            WHERE seance_id IN (
            """);

        for (int index = 0; index < seanceIds.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");

        try (PreparedStatement pst = cnx.prepareStatement(sql.toString())) {
            for (int index = 0; index < seanceIds.size(); index++) {
                pst.setInt(index + 1, seanceIds.get(index));
            }

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    visibilityBySeanceId.put(
                        rs.getInt("seance_id"),
                        new SessionVisibility(
                            rs.getInt("request_id"),
                            rs.getInt("participant_id"),
                            normalizeVisibility(rs.getString("visibility_scope"))
                        )
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println("Chargement de la visibilite matching impossible: " + e.getMessage());
        }

        return visibilityBySeanceId;
    }

    private List<StudentMatchCard> insertCandidates(int requestId, List<RawTutorCandidate> candidates) throws SQLException {
        List<StudentMatchCard> cards = new ArrayList<>();
        if (candidates.isEmpty()) {
            return cards;
        }

        String sql = """
            INSERT INTO matching_candidate (
                request_id, tutor_id, compatibility_score, match_reason, supporting_signals,
                student_decision, tutor_decision, created_at, responded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (RawTutorCandidate candidate : candidates) {
                LocalDateTime now = LocalDateTime.now();
                pst.setInt(1, requestId);
                pst.setInt(2, candidate.tutorId());
                pst.setDouble(3, candidate.score());
                pst.setString(4, candidate.reason());
                pst.setString(5, String.join("||", candidate.signals()));
                pst.setNull(6, Types.SMALLINT);
                pst.setNull(7, Types.SMALLINT);
                pst.setTimestamp(8, Timestamp.valueOf(now));
                pst.setNull(9, Types.TIMESTAMP);
                pst.executeUpdate();

                int candidateId = 0;
                try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        candidateId = generatedKeys.getInt(1);
                    }
                }

                cards.add(new StudentMatchCard(
                    candidateId,
                    requestId,
                    candidate.tutorId(),
                    candidate.tutorName(),
                    candidate.score(),
                    candidate.reason(),
                    candidate.signals(),
                    candidate.subjectSessions(),
                    candidate.totalSessions(),
                    candidate.acceptedReservations(),
                    candidate.matchingModeSessions()
                ));
            }
        }
        return cards;
    }

    private OperationResult ensureMatchingReservation(int seanceId,
                                                      int participantId,
                                                      LocalDateTime now) throws SQLException {
        if (!participantExists(participantId)) {
            return OperationResult.failure("La reservation automatique n'a pas pu etre finalisee pour le moment.");
        }

        String selectExistingSql = """
            SELECT id, status
            FROM reservation
            WHERE seance_id = ?
              AND participant_id = ?
              AND cancell_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """;
        try (PreparedStatement select = cnx.prepareStatement(selectExistingSql)) {
            select.setInt(1, seanceId);
            select.setInt(2, participantId);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    int reservationId = rs.getInt("id");
                    int status = rs.getInt("status");
                    if (status == 1 || status == 2) {
                        return OperationResult.success("Reservation deja creee pour cet etudiant.");
                    }
                    String updateSql = """
                        UPDATE reservation
                        SET status = ?, notes = ?, acceptance_email_sent_at = ?
                        WHERE id = ?
                        """;
                    try (PreparedStatement update = cnx.prepareStatement(updateSql)) {
                        update.setInt(1, 1);
                        update.setString(2, "Reservation creee automatiquement a partir du matching.");
                        update.setTimestamp(3, Timestamp.valueOf(now));
                        update.setInt(4, reservationId);
                        update.executeUpdate();
                    }
                    return OperationResult.success("Reservation matching mise a jour.");
                }
            }
        }

        String insertSql = """
            INSERT INTO reservation (
                status, reserved_at, cancell_at, notes, seance_id, participant_id,
                confirmation_email_sent_at, acceptance_email_sent_at, reminder_email_sent_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement insert = cnx.prepareStatement(insertSql)) {
            insert.setInt(1, 1);
            insert.setTimestamp(2, Timestamp.valueOf(now));
            insert.setTimestamp(3, null);
            insert.setString(4, "Reservation creee automatiquement a partir du matching.");
            insert.setInt(5, seanceId);
            insert.setInt(6, participantId);
            insert.setTimestamp(7, null);
            insert.setTimestamp(8, Timestamp.valueOf(now));
            insert.setTimestamp(9, null);
            insert.executeUpdate();
        }
        return OperationResult.success("Reservation matching creee.");
    }

    private List<RawTutorCandidate> loadCompatibleTutors(String subject,
                                                         LocalDateTime startAt,
                                                         int durationMin,
                                                         String requestedMode,
                                                         MatchingNeedProfile needProfile) {
        List<RawTutorCandidate> compatibleTutors = new ArrayList<>();
        if (cnx == null) {
            return compatibleTutors;
        }

        String sql = """
            SELECT
                s.tuteur_id,
                COALESCE(NULLIF(TRIM(u.full_name), ''), 'Tuteur non defini') AS tutor_name,
                COUNT(DISTINCT s.id) AS total_sessions,
                SUM(CASE WHEN LOWER(TRIM(s.matiere)) = LOWER(TRIM(?)) THEN 1 ELSE 0 END) AS subject_sessions,
                SUM(CASE WHEN LOWER(TRIM(COALESCE(s.mode_seance, ?))) = LOWER(TRIM(?)) THEN 1 ELSE 0 END) AS matching_mode_sessions,
                SUM(CASE WHEN r.cancell_at IS NULL AND r.status IN (1, 2) THEN 1 ELSE 0 END) AS accepted_reservations
            FROM seance s
            LEFT JOIN reservation r ON r.seance_id = s.id
            LEFT JOIN user u ON u.id = s.tuteur_id
            WHERE LOWER(TRIM(s.matiere)) = LOWER(TRIM(?))
              AND NOT EXISTS (
                  SELECT 1
                  FROM seance conflict
                  WHERE conflict.tuteur_id = s.tuteur_id
                    AND conflict.start_at IS NOT NULL
                    AND conflict.start_at < ?
                    AND DATE_ADD(conflict.start_at, INTERVAL conflict.duration_min MINUTE) > ?
                    AND conflict.status IN (0, 1)
              )
            GROUP BY s.tuteur_id, COALESCE(NULLIF(TRIM(u.full_name), ''), 'Tuteur non defini')
            ORDER BY subject_sessions DESC, accepted_reservations DESC, total_sessions DESC, s.tuteur_id ASC
            LIMIT 12
            """;

        LocalDateTime endAt = startAt.plusMinutes(durationMin);
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setString(1, subject);
            pst.setString(2, Seance.MODE_ONLINE);
            pst.setString(3, requestedMode);
            pst.setString(4, subject);
            pst.setTimestamp(5, Timestamp.valueOf(endAt));
            pst.setTimestamp(6, Timestamp.valueOf(startAt));

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    int tutorId = rs.getInt("tuteur_id");
                    int subjectSessions = rs.getInt("subject_sessions");
                    if (tutorId <= 0 || subjectSessions <= 0) {
                        continue;
                    }

                    int totalSessions = rs.getInt("total_sessions");
                    int acceptedReservations = rs.getInt("accepted_reservations");
                    int matchingModeSessions = rs.getInt("matching_mode_sessions");

                    List<String> signals = buildSignals(subjectSessions, acceptedReservations, matchingModeSessions, needProfile);
                    double score = computeCompatibilityScore(subjectSessions, acceptedReservations, matchingModeSessions, totalSessions);
                    String reason = buildReason(subject, subjectSessions, matchingModeSessions, needProfile);

                    compatibleTutors.add(new RawTutorCandidate(
                        tutorId,
                        safeText(rs.getString("tutor_name"), "Tuteur non defini"),
                        score,
                        reason,
                        signals,
                        subjectSessions,
                        totalSessions,
                        acceptedReservations,
                        matchingModeSessions
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Chargement des tuteurs compatibles impossible: " + e.getMessage());
        }

        compatibleTutors.sort(
            Comparator.comparingDouble(RawTutorCandidate::score).reversed()
                .thenComparing(RawTutorCandidate::subjectSessions, Comparator.reverseOrder())
                .thenComparing(RawTutorCandidate::acceptedReservations, Comparator.reverseOrder())
                .thenComparing(RawTutorCandidate::tutorId)
        );
        return compatibleTutors;
    }

    private void logMatchingError(String context, Exception exception) {
        if (exception == null) {
            System.out.println(context + ".");
            return;
        }
        System.out.println(context + ": " + exception.getMessage());
    }

    private TutorMatchInboxItem loadTutorMatchCandidate(int candidateId, int tutorId) {
        List<TutorMatchInboxItem> inbox = getTutorPendingMatches(tutorId);
        return inbox.stream()
            .filter(item -> item.candidateId() == candidateId)
            .findFirst()
            .orElse(null);
    }

    private void refreshRequestStatusAfterTutorDecision(int requestId) throws SQLException {
        if (requestId <= 0 || cnx == null) {
            return;
        }

        String sql = """
            SELECT
                SUM(CASE WHEN student_decision IN (1, 2) AND tutor_decision IS NULL THEN 1 ELSE 0 END) AS pending_tutor_choices,
                SUM(CASE WHEN tutor_decision = 1 THEN 1 ELSE 0 END) AS accepted_matches
            FROM matching_candidate
            WHERE request_id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, requestId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return;
                }

                int pendingTutorChoices = rs.getInt("pending_tutor_choices");
                int acceptedMatches = rs.getInt("accepted_matches");

                String status = REQUEST_STATUS_MATCHING;
                if (acceptedMatches > 0) {
                    status = REQUEST_STATUS_ACCEPTED;
                } else if (pendingTutorChoices == 0) {
                    status = REQUEST_STATUS_REFUSED;
                }

                try (PreparedStatement update = cnx.prepareStatement(
                    "UPDATE matching_request SET status = ?, updated_at = ? WHERE id = ?")) {
                    update.setString(1, status);
                    update.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                    update.setInt(3, requestId);
                    update.executeUpdate();
                }
            }
        }
    }

    private AnalysisResolution analyzeNeed(MatchingDraft draft) {
        String normalizedText = normalizeOptionalText(draft == null ? null : draft.objectiveText());
        if (draft == null) {
            return AnalysisResolution.failure("La demande de matching est invalide.");
        }
        if (normalizedText == null) {
            return AnalysisResolution.failure("Le besoin etudiant doit etre renseigne pour lancer l'analyse Gemini.");
        }

        GeminiMatchingAnalysisService.MatchingAiAttempt attempt = geminiMatchingAnalysisService.analyzeNeed(
            new GeminiMatchingAnalysisService.MatchingAiContext(
                safeText(normalizeSubject(draft.subject()), "Matiere non precise"),
                normalizedText,
                normalizeMode(draft.mode()),
                normalizeVisibility(draft.visibilityScope()),
                draft.durationMin()
            )
        );
        if (!attempt.success() || attempt.analysis() == null) {
            return AnalysisResolution.failure(
                safeText(attempt.errorMessage(), "Le service Gemini est temporairement indisponible.")
            );
        }

        GeminiMatchingAnalysisService.MatchingAiAnalysis analysis = attempt.analysis();
        MatchingNeedProfile needProfile = new MatchingNeedProfile(
            safeText(normalizeOptionalText(analysis.summary()), "Analyse Gemini indisponible."),
            safeText(normalizeOptionalText(analysis.level()), "Niveau non precise"),
            normalizeExternalAiKeywords(analysis.keywords()),
            safeText(normalizeOptionalText(analysis.source()), "Gemini")
        );
        return AnalysisResolution.success(needProfile);
    }

    private List<String> normalizeExternalAiKeywords(List<String> rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return List.of("matching cible");
        }

        List<String> normalizedKeywords = new ArrayList<>();
        for (String keyword : rawKeywords) {
            String normalized = normalizeOptionalText(keyword);
            if (normalized != null && !normalizedKeywords.contains(normalized)) {
                normalizedKeywords.add(normalized);
            }
            if (normalizedKeywords.size() == 5) {
                break;
            }
        }
        return normalizedKeywords.isEmpty() ? List.of("matching cible") : normalizedKeywords;
    }

    private List<String> buildSignals(int subjectSessions,
                                      int acceptedReservations,
                                      int matchingModeSessions,
                                      MatchingNeedProfile needProfile) {
        List<String> signals = new ArrayList<>();
        signals.add(subjectSessions + " seance(s) deja animee(s) dans cette matiere");
        signals.add("Disponible sur le creneau demande");

        if (acceptedReservations > 0) {
            signals.add(acceptedReservations + " reservation(s) acceptee(s)");
        }
        if (matchingModeSessions > 0) {
            signals.add("Habitude du format demande");
        }
        if (needProfile != null && needProfile.level() != null && !needProfile.level().isBlank()
            && !"Niveau non precise".equalsIgnoreCase(needProfile.level())) {
            signals.add("Cible " + needProfile.level().toLowerCase(Locale.ROOT));
        }
        if (needProfile != null && needProfile.keywords() != null && !needProfile.keywords().isEmpty()) {
            signals.add("Focus " + String.join(" / ", needProfile.keywords().stream().limit(2).toList()));
        }
        return signals;
    }

    private double computeCompatibilityScore(int subjectSessions,
                                             int acceptedReservations,
                                             int matchingModeSessions,
                                             int totalSessions) {
        double score = 32.0;
        score += Math.min(subjectSessions * 9.0, 34.0);
        score += Math.min(acceptedReservations * 1.6, 18.0);
        score += Math.min(totalSessions * 0.7, 8.0);
        if (matchingModeSessions > 0) {
            score += 8.0;
        }
        return Math.max(0.0, Math.min(100.0, score));
    }

    private String buildReason(String subject,
                               int subjectSessions,
                               int matchingModeSessions,
                               MatchingNeedProfile needProfile) {
        String focusFragment = buildNeedFocusFragment(needProfile);
        if (matchingModeSessions > 0) {
            return "Compatibilite forte: " + subjectSessions + " seance(s) deja animee(s) en " + subject
                + ", format deja pratique" + focusFragment + ".";
        }
        return "Compatibilite solide: " + subjectSessions + " seance(s) deja animee(s) en " + subject
            + ", disponibilite validee sur le creneau demande" + focusFragment + ".";
    }

    private String buildNeedFocusFragment(MatchingNeedProfile needProfile) {
        if (needProfile == null || needProfile.keywords() == null || needProfile.keywords().isEmpty()) {
            return "";
        }
        String focus = String.join(", ", needProfile.keywords().stream().limit(2).toList());
        return " avec focus " + focus;
    }

    private String validateDraft(MatchingDraft draft) {
        if (draft == null) {
            return "Le formulaire de matching est vide.";
        }
        if (draft.participantId() <= 0) {
            return "Etudiant invalide pour cette demande de matching.";
        }

        String subject = normalizeSubject(draft.subject());
        if (subject == null) {
            return "Renseignez la matiere recherchee.";
        }
        if (draft.startAt() == null) {
            return "Choisissez la date et l'heure souhaitees.";
        }
        if (draft.startAt().isBefore(LocalDateTime.now())) {
            return "Le matching doit viser un creneau futur.";
        }
        if (draft.durationMin() < SeanceService.MIN_DURATION_MINUTES || draft.durationMin() > SeanceService.MAX_DURATION_MINUTES) {
            return "La duree du matching doit etre comprise entre "
                + SeanceService.MIN_DURATION_MINUTES + " et " + SeanceService.MAX_DURATION_MINUTES + " minutes.";
        }
        if (!participantExists(draft.participantId())) {
            return "Etudiant introuvable dans la base.";
        }
        return null;
    }

    private boolean participantExists(int participantId) {
        if (cnx == null || participantId <= 0) {
            return false;
        }

        try (PreparedStatement pst = cnx.prepareStatement("SELECT COUNT(*) AS total FROM user WHERE id = ?")) {
            pst.setInt(1, participantId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() && rs.getInt("total") > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void ensureSchema() {
        if (schemaEnsured || cnx == null) {
            return;
        }

        String[] statements = {
            """
            CREATE TABLE IF NOT EXISTS matching_request (
                id INT AUTO_INCREMENT PRIMARY KEY,
                participant_id INT NOT NULL,
                matiere VARCHAR(255) NOT NULL,
                start_at DATETIME NOT NULL,
                duration_min INT NOT NULL,
                mode_seance VARCHAR(30) NOT NULL DEFAULT 'en_ligne',
                visibility_scope VARCHAR(20) NOT NULL DEFAULT 'publique',
                objective_text TEXT NULL,
                objective_summary VARCHAR(255) NULL,
                need_keywords VARCHAR(255) NULL,
                requested_level VARCHAR(60) NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'pending',
                accepted_tutor_id INT NULL,
                planned_seance_id INT NULL,
                created_at DATETIME NOT NULL,
                updated_at DATETIME NULL,
                INDEX idx_matching_request_participant (participant_id),
                INDEX idx_matching_request_status (status),
                INDEX idx_matching_request_start_at (start_at)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS matching_candidate (
                id INT AUTO_INCREMENT PRIMARY KEY,
                request_id INT NOT NULL,
                tutor_id INT NOT NULL,
                compatibility_score DOUBLE NOT NULL DEFAULT 0,
                match_reason VARCHAR(255) NULL,
                supporting_signals VARCHAR(255) NULL,
                student_decision SMALLINT NULL,
                tutor_decision SMALLINT NULL,
                created_at DATETIME NOT NULL,
                responded_at DATETIME NULL,
                UNIQUE KEY uq_matching_request_tutor (request_id, tutor_id),
                INDEX idx_matching_candidate_tutor (tutor_id),
                INDEX idx_matching_candidate_request (request_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS matching_session_visibility (
                seance_id INT PRIMARY KEY,
                request_id INT NOT NULL,
                participant_id INT NOT NULL,
                visibility_scope VARCHAR(20) NOT NULL DEFAULT 'publique',
                created_at DATETIME NOT NULL,
                INDEX idx_matching_visibility_participant (participant_id)
            )
            """
        };

        try (Statement statement = cnx.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            schemaEnsured = true;
        } catch (SQLException e) {
            System.out.println("Initialisation du schema matching impossible: " + e.getMessage());
        }
    }

    private LocalDateTime readDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawKeywords.split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .toList();
    }

    private List<String> parseSignals(String rawSignals) {
        if (rawSignals == null || rawSignals.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawSignals.split("\\|\\|"))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .toList();
    }

    private String normalizeSubject(String subject) {
        String normalized = normalizeOptionalText(subject);
        return normalized == null ? null : normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return Seance.MODE_ONLINE;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("present")) {
            return Seance.MODE_ONSITE;
        }
        return Seance.MODE_ONLINE;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return VISIBILITY_PUBLIC;
        }
        String normalized = visibility.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("individ") ? VISIBILITY_PRIVATE : VISIBILITY_PUBLIC;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String value) {
        return value == null ? REQUEST_STATUS_PENDING : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record MatchingDraft(int participantId,
                                String subject,
                                LocalDateTime startAt,
                                int durationMin,
                                String mode,
                                String visibilityScope,
                                String objectiveText) {
    }

    public record MatchingNeedProfile(String summary,
                                      String level,
                                      List<String> keywords,
                                      String source) {
        public boolean usesExternalAi() {
            if (source == null) {
                return false;
            }
            String normalizedSource = source.toLowerCase(Locale.ROOT);
            return normalizedSource.contains("externe") || normalizedSource.contains("external");
        }
    }

    private record AnalysisResolution(boolean success,
                                      MatchingNeedProfile needProfile,
                                      String errorMessage) {
        private static AnalysisResolution success(MatchingNeedProfile needProfile) {
            return new AnalysisResolution(true, needProfile, null);
        }

        private static AnalysisResolution failure(String errorMessage) {
            return new AnalysisResolution(false, null, errorMessage);
        }
    }

    public record MatchingRequestCreation(int requestId,
                                          boolean success,
                                          String message,
                                          MatchingNeedProfile needProfile,
                                          List<StudentMatchCard> candidates) {
        public static MatchingRequestCreation failure(String message) {
            return new MatchingRequestCreation(0, false, message, null, List.of());
        }
    }

    public record StudentMatchCard(int candidateId,
                                   int requestId,
                                   int tutorId,
                                   String tutorName,
                                   double score,
                                   String reason,
                                   List<String> signals,
                                   int subjectSessions,
                                   int totalSessions,
                                   int acceptedReservations,
                                   int matchingModeSessions) {
    }

    public record TutorMatchInboxItem(int candidateId,
                                      int requestId,
                                      int participantId,
                                      String participantName,
                                      String subject,
                                      LocalDateTime requestedStartAt,
                                      int durationMin,
                                      String mode,
                                      String visibilityScope,
                                      String objectiveText,
                                      String objectiveSummary,
                                      List<String> keywords,
                                      String requestedLevel,
                                      int studentDecision,
                                      double compatibilityScore,
                                      String reason,
                                      List<String> signals,
                                      LocalDateTime createdAt) {
    }

    public record MatchingPlanContext(int requestId,
                                      int participantId,
                                      String participantName,
                                      String subject,
                                      LocalDateTime requestedStartAt,
                                      int durationMin,
                                      String mode,
                                      String visibilityScope,
                                      String objectiveText,
                                      String objectiveSummary) {
    }

    public record MatchingAcceptanceResult(boolean success,
                                           String message,
                                           MatchingPlanContext planContext) {
        public static MatchingAcceptanceResult failure(String message) {
            return new MatchingAcceptanceResult(false, message, null);
        }
    }

    public record SessionVisibility(int requestId,
                                    int participantId,
                                    String visibilityScope) {
        public boolean isPrivate() {
            return VISIBILITY_PRIVATE.equals(visibilityScope);
        }
    }

    private record RawTutorCandidate(int tutorId,
                                     String tutorName,
                                     double score,
                                     String reason,
                                     List<String> signals,
                                     int subjectSessions,
                                     int totalSessions,
                                     int acceptedReservations,
                                     int matchingModeSessions) {
    }
}
