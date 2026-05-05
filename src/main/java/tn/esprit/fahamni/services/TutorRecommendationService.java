package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.ReservationService.StudentReservationItem;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TutorRecommendationService {

    private static final double MIN_STRONG_SCORE = 55.0;

    private final Connection cnx = MyDataBase.getInstance().getCnx();
    private final ReservationService reservationService = new ReservationService();

    public List<RecommendedSession> rankSessionsForStudent(int participantId, List<Seance> sessions) {
        return rankSessionsForStudent(participantId, sessions, RecommendationObjective.GENERAL);
    }

    public List<RecommendedSession> rankSessionsForStudent(int participantId,
                                                           List<Seance> sessions,
                                                           RecommendationObjective objective) {
        if (participantId <= 0 || sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        RecommendationObjective effectiveObjective =
            objective == null ? RecommendationObjective.GENERAL : objective;
        StudentRecommendationProfile profile = buildStudentProfile(participantId);
        Map<Integer, Integer> acceptedCountsByTutorId = loadAcceptedCountsByTutorId();
        Map<Integer, Integer> acceptedCountsBySeanceId = loadAcceptedCountsBySeanceId();

        List<RecommendedSession> scoredSessions = sessions.stream()
            .map(session -> scoreSession(session, profile, acceptedCountsByTutorId, acceptedCountsBySeanceId, effectiveObjective))
            .filter(recommendation -> recommendation.seance() != null)
            .sorted(Comparator
                .comparingDouble(RecommendedSession::score).reversed()
                .thenComparing(RecommendedSession::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(recommendation -> recommendation.seance().getId(), Comparator.reverseOrder()))
            .toList();

        List<RecommendedSession> diversifiedSessions = diversifyRankedSessions(scoredSessions, effectiveObjective);

        List<RecommendedSession> personalized = new ArrayList<>();
        for (int index = 0; index < diversifiedSessions.size(); index++) {
            RecommendedSession recommendation = diversifiedSessions.get(index);
            boolean highlighted = index < 3 && recommendation.score() >= MIN_STRONG_SCORE;
            personalized.add(recommendation.withRank(index + 1, highlighted));
        }
        return personalized;
    }

    private RecommendedSession scoreSession(Seance session,
                                            StudentRecommendationProfile profile,
                                            Map<Integer, Integer> acceptedCountsByTutorId,
                                            Map<Integer, Integer> acceptedCountsBySeanceId,
                                            RecommendationObjective objective) {
        if (session == null) {
            return RecommendedSession.empty(objective);
        }

        ObjectiveWeights weights = resolveObjectiveWeights(objective);
        List<WeightedReason> reasons = new ArrayList<>();
        List<String> signals = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = session.getStartAt();
        double score = 0.0;

        if (session.getStatus() == 1) {
            score += 12.0;
        } else {
            score -= 45.0;
            addSignal(signals, "Non disponible");
        }

        if (startAt == null) {
            score -= 10.0;
        } else {
            long hoursUntil = Duration.between(now, startAt).toHours();
            if (hoursUntil < 0) {
                score -= 35.0;
                addSignal(signals, "Deja passee");
            } else if (hoursUntil <= 48) {
                double timeScore = 12.0 + weights.immediate72hBonus();
                score += timeScore;
                reasons.add(new WeightedReason(timeScore, "Creneau tres proche, utile pour agir rapidement."));
                addSignal(signals, "Tres proche");
            } else if (hoursUntil <= 24L * 7L) {
                double timeScore = 8.0 + weights.immediate7dBonus();
                score += timeScore;
                reasons.add(new WeightedReason(timeScore, "Disponibilite interessante a court terme."));
                addSignal(signals, "Disponible cette semaine");
            } else if (hoursUntil <= 24L * 14L) {
                score += 5.0;
            } else if (hoursUntil <= 24L * 30L) {
                score += 2.0;
            } else {
                score -= 4.0;
                addSignal(signals, "Plus lointaine");
            }
        }

        if (profile.reservedSeanceIds().contains(session.getId())) {
            score -= 40.0;
            addSignal(signals, "Deja reservee");
        }

        String normalizedSubject = normalize(session.getMatiere());
        SubjectPreference subjectPreference = profile.subjectPreferences().get(normalizedSubject);
        boolean familiarSubject = subjectPreference != null && subjectPreference.acceptedCount() > 0;
        if (familiarSubject) {
            double subjectScore = Math.min(22.0, subjectPreference.acceptedCount() * 6.0)
                * weights.familiarSubjectMultiplier();
            score += subjectScore;
            reasons.add(new WeightedReason(subjectScore, "Cette seance prolonge une matiere deja validee dans votre historique."));
            addSignal(signals, "Continuite");
        } else if (profile.acceptedReservations() >= 2) {
            if (objective == RecommendationObjective.DIVERSIFICATION) {
                score += weights.newSubjectBonus();
                reasons.add(new WeightedReason(weights.newSubjectBonus(), "Cette suggestion ouvre une nouvelle matiere sans quitter un cadre fiable."));
                addSignal(signals, "Nouvelle matiere");
            } else if (objective == RecommendationObjective.CONTINUITE) {
                score -= weights.unfamiliarSubjectPenalty();
            }
        }

        TutorPreference tutorPreference = profile.tutorPreferences().get(session.getTuteurId());
        boolean familiarTutor = tutorPreference != null && tutorPreference.acceptedCount() > 0;
        if (familiarTutor) {
            double tutorHistoryScore = Math.min(14.0, tutorPreference.acceptedCount() * 4.5)
                * weights.familiarTutorMultiplier();
            score += tutorHistoryScore;
            reasons.add(new WeightedReason(tutorHistoryScore, "Ce tuteur est deja coherent avec vos reservations acceptees."));
            addSignal(signals, "Tuteur connu");
        } else if (profile.acceptedReservations() >= 2 && objective == RecommendationObjective.DIVERSIFICATION) {
            score += weights.newTutorBonus();
            reasons.add(new WeightedReason(weights.newTutorBonus(), "Le systeme ouvre un nouveau tuteur tout en gardant un niveau de fiabilite suffisant."));
            addSignal(signals, "Nouveau tuteur");
        }

        int tutorAcceptedCount = acceptedCountsByTutorId.getOrDefault(session.getTuteurId(), 0);
        TutorExperienceTier tutorTier = resolveTutorExperienceTier(tutorAcceptedCount);
        score += tutorTier.scoreBonus();
        addSignal(signals, tutorTier.signal());
        if (tutorTier.scoreBonus() >= 6.0) {
            reasons.add(new WeightedReason(tutorTier.scoreBonus(), tutorTier.reason()));
        }

        String normalizedMode = normalizeMode(session.getMode());
        boolean preferredModeMatch = profile.preferredMode() != null
            && profile.preferredMode().equals(normalizedMode);
        if (preferredModeMatch) {
            score += weights.preferredModeBonus();
            reasons.add(new WeightedReason(weights.preferredModeBonus(), "Le format correspond a votre habitude de reservation."));
            addSignal(signals, "Format habituel");
        } else if (profile.acceptedReservations() >= 3 && profile.preferredMode() != null) {
            score -= 2.0;
        }

        int acceptedCount = acceptedCountsBySeanceId.getOrDefault(session.getId(), 0);
        int maxParticipants = Math.max(1, session.getMaxParticipants());
        int availableSeats = Math.max(0, maxParticipants - acceptedCount);
        double occupancyRate = Math.min(1.0, (double) acceptedCount / maxParticipants);

        if (availableSeats <= 0) {
            score -= 20.0;
        } else if (availableSeats == 1) {
            score -= 7.0;
            addSignal(signals, "Places limitees");
        } else if (availableSeats >= 2 && availableSeats <= Math.max(2, maxParticipants / 2)) {
            score += 2.0;
        }

        if (occupancyRate >= 0.25 && occupancyRate <= 0.80) {
            score += 4.0;
            reasons.add(new WeightedReason(4.0, "La seance presente un niveau de demande equilibre."));
            addSignal(signals, "Demande equilibree");
        } else if (occupancyRate > 0.85) {
            score -= 8.0;
            addSignal(signals, "Presque complete");
        }

        if (profile.acceptedReservations() == 0) {
            if (startAt != null && !startAt.isBefore(now) && startAt.isBefore(now.plusDays(7))) {
                score += 6.0;
            }
            if (tutorAcceptedCount >= 5) {
                score += 4.0;
                reasons.add(new WeightedReason(4.0, "Suggestion retenue pour bien demarrer avec un tuteur deja confirme."));
            }
        }

        double normalizedScore = clampScore(score);
        double confidenceProgress = computeConfidenceProgress(
            profile,
            normalizedScore,
            familiarSubject,
            familiarTutor,
            preferredModeMatch,
            tutorTier
        );
        String confidenceLabel = resolveConfidenceLabel(confidenceProgress);
        String reason = reasons.stream()
            .max(Comparator.comparingDouble(WeightedReason::weight))
            .map(WeightedReason::message)
            .orElseGet(() -> buildFallbackReason(session, objective, profile));
        boolean fallback = reasons.isEmpty() || normalizedScore < MIN_STRONG_SCORE;

        return new RecommendedSession(
            session,
            normalizedScore,
            reason,
            signals.stream().distinct().limit(4).toList(),
            0,
            false,
            objective,
            confidenceLabel,
            confidenceProgress,
            tutorTier.label(),
            fallback
        );
    }

    private List<RecommendedSession> diversifyRankedSessions(List<RecommendedSession> ranked,
                                                             RecommendationObjective objective) {
        if (ranked.isEmpty()) {
            return ranked;
        }

        List<RecommendedSession> remaining = new ArrayList<>(ranked);
        List<RecommendedSession> diversified = new ArrayList<>(ranked.size());
        Map<Integer, Integer> tutorUsage = new HashMap<>();

        while (!remaining.isEmpty()) {
            RecommendedSession selected = remaining.stream()
                .max((left, right) -> compareDiversifiedScore(left, right, tutorUsage, objective))
                .orElse(remaining.get(0));
            diversified.add(selected);
            if (selected.seance() != null) {
                tutorUsage.merge(selected.seance().getTuteurId(), 1, Integer::sum);
            }
            remaining.remove(selected);
        }
        return diversified;
    }

    private int compareDiversifiedScore(RecommendedSession left,
                                        RecommendedSession right,
                                        Map<Integer, Integer> tutorUsage,
                                        RecommendationObjective objective) {
        double leftAdjusted = computeDiversifiedScore(left, tutorUsage, objective);
        double rightAdjusted = computeDiversifiedScore(right, tutorUsage, objective);
        return Comparator
            .comparingDouble((RecommendedSession recommendation) ->
                computeDiversifiedScore(recommendation, tutorUsage, objective))
            .thenComparing(RecommendedSession::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(recommendation -> recommendation.seance().getId(), Comparator.reverseOrder())
            .compare(left, right);
    }

    private double computeDiversifiedScore(RecommendedSession recommendation,
                                           Map<Integer, Integer> tutorUsage,
                                           RecommendationObjective objective) {
        if (recommendation == null || recommendation.seance() == null) {
            return Double.NEGATIVE_INFINITY;
        }

        int repeats = tutorUsage.getOrDefault(recommendation.seance().getTuteurId(), 0);
        return recommendation.score() - (repeats * objective.repeatedTutorPenalty());
    }

    private StudentRecommendationProfile buildStudentProfile(int participantId) {
        Map<String, SubjectPreferenceAccumulator> subjectPreferences = new HashMap<>();
        Map<Integer, TutorPreferenceAccumulator> tutorPreferences = new HashMap<>();
        Map<String, Integer> modeCounts = new HashMap<>();
        Set<Integer> reservedSeanceIds = new HashSet<>();
        int acceptedReservations = 0;
        int completedReservations = 0;

        for (StudentReservationItem reservation : reservationService.getStudentReservations(participantId)) {
            if (reservation == null) {
                continue;
            }

            if (!reservation.isRefused()) {
                reservedSeanceIds.add(reservation.seanceId());
            }

            if (!reservation.isAccepted()) {
                continue;
            }

            acceptedReservations++;
            if (reservation.isCompleted()) {
                completedReservations++;
            }

            String normalizedSubject = normalize(reservation.seanceTitle());
            if (!normalizedSubject.isEmpty()) {
                subjectPreferences
                    .computeIfAbsent(normalizedSubject, key -> new SubjectPreferenceAccumulator())
                    .register();
            }

            tutorPreferences
                .computeIfAbsent(reservation.tutorId(), key -> new TutorPreferenceAccumulator())
                .register();

            String mode = normalizeModeFromReservation(reservation);
            if (!mode.isEmpty()) {
                modeCounts.merge(mode, 1, Integer::sum);
            }
        }

        Map<String, SubjectPreference> finalSubjectPreferences = new LinkedHashMap<>();
        subjectPreferences.forEach((subject, accumulator) ->
            finalSubjectPreferences.put(subject, accumulator.toPreference())
        );

        Map<Integer, TutorPreference> finalTutorPreferences = new LinkedHashMap<>();
        tutorPreferences.forEach((tutorId, accumulator) ->
            finalTutorPreferences.put(tutorId, accumulator.toPreference())
        );

        String preferredMode = modeCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        return new StudentRecommendationProfile(
            finalSubjectPreferences,
            finalTutorPreferences,
            preferredMode,
            reservedSeanceIds,
            acceptedReservations,
            completedReservations
        );
    }

    private Map<Integer, Integer> loadAcceptedCountsByTutorId() {
        Map<Integer, Integer> counts = new HashMap<>();
        if (cnx == null) {
            return counts;
        }

        String sql = """
            SELECT s.tuteur_id, COUNT(*) AS accepted_count
            FROM reservation r
            INNER JOIN seance s ON s.id = r.seance_id
            WHERE r.cancell_at IS NULL
              AND r.status IN (1, 2)
            GROUP BY s.tuteur_id
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getInt("tuteur_id"), rs.getInt("accepted_count"));
            }
        } catch (SQLException e) {
            System.out.println("Chargement de l'experience tuteur impossible: " + e.getMessage());
        }
        return counts;
    }

    private Map<Integer, Integer> loadAcceptedCountsBySeanceId() {
        Map<Integer, Integer> counts = new HashMap<>();
        if (cnx == null) {
            return counts;
        }

        String sql = """
            SELECT seance_id, COUNT(*) AS accepted_count
            FROM reservation
            WHERE cancell_at IS NULL
              AND status IN (1, 2)
            GROUP BY seance_id
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getInt("seance_id"), rs.getInt("accepted_count"));
            }
        } catch (SQLException e) {
            System.out.println("Chargement du volume des reservations impossible: " + e.getMessage());
        }
        return counts;
    }

    private ObjectiveWeights resolveObjectiveWeights(RecommendationObjective objective) {
        return switch (objective) {
            case RAPIDE -> new ObjectiveWeights(10.0, 6.0, 0.75, 0.85, 3.0, 0.0, 0.0, 0.0);
            case CONTINUITE -> new ObjectiveWeights(2.0, 1.0, 1.25, 1.20, 5.0, 0.0, 0.0, 8.0);
            case DIVERSIFICATION -> new ObjectiveWeights(3.0, 2.0, 0.55, 0.40, 4.0, 10.0, 7.0, 0.0);
            case GENERAL -> new ObjectiveWeights(4.0, 2.0, 1.0, 1.0, 5.0, 0.0, 0.0, 0.0);
        };
    }

    private TutorExperienceTier resolveTutorExperienceTier(int acceptedCount) {
        if (acceptedCount >= 20) {
            return new TutorExperienceTier("Tuteur expert", "Tuteur expert", 16.0,
                "Le tuteur dispose d'un historique expert, utile pour une recommandation premium.");
        }
        if (acceptedCount >= 10) {
            return new TutorExperienceTier("Tuteur confirme", "Tuteur confirme", 12.0,
                "Le tuteur dispose d'un historique confirme, ce qui renforce la fiabilite de la recommandation.");
        }
        if (acceptedCount >= 5) {
            return new TutorExperienceTier("Tuteur fiable", "Tuteur fiable", 8.0,
                "Le tuteur a depasse le seuil minimal de confiance recommande.");
        }
        if (acceptedCount >= 3) {
            return new TutorExperienceTier("Tuteur en progression", "Tuteur en progression", 4.0,
                "Le tuteur commence a disposer d'un historique suffisant pour etre recommande.");
        }
        return new TutorExperienceTier("Nouveau tuteur", "Nouveau tuteur", 1.5,
            "Le tuteur reste recent dans l'historique disponible.");
    }

    private double computeConfidenceProgress(StudentRecommendationProfile profile,
                                             double normalizedScore,
                                             boolean familiarSubject,
                                             boolean familiarTutor,
                                             boolean preferredModeMatch,
                                             TutorExperienceTier tutorTier) {
        double historySignal = clamp01(profile.acceptedReservations() / 5.0);
        double completionSignal = clamp01(profile.completedReservations() / 4.0);
        double personalizationSignal = 0.0;
        if (familiarSubject) {
            personalizationSignal += 0.40;
        }
        if (familiarTutor) {
            personalizationSignal += 0.25;
        }
        if (preferredModeMatch) {
            personalizationSignal += 0.20;
        }
        double tutorSignal = clamp01(tutorTier.scoreBonus() / 16.0);

        return clamp01(
            (historySignal * 0.35)
                + (completionSignal * 0.15)
                + (clamp01(normalizedScore / 100.0) * 0.20)
                + (personalizationSignal * 0.20)
                + (tutorSignal * 0.10)
        );
    }

    private String resolveConfidenceLabel(double confidenceProgress) {
        if (confidenceProgress >= 0.78) {
            return "Confiance elevee";
        }
        if (confidenceProgress >= 0.58) {
            return "Confiance consolidee";
        }
        if (confidenceProgress >= 0.40) {
            return "Confiance intermediaire";
        }
        return "Confiance initiale";
    }

    private String buildFallbackReason(Seance session,
                                       RecommendationObjective objective,
                                       StudentRecommendationProfile profile) {
        if (profile.acceptedReservations() == 0) {
            return "Cette suggestion repose surtout sur la disponibilite et sur la maturite du tuteur pour bien demarrer.";
        }
        if (session != null && session.getStartAt() != null && session.getStartAt().isAfter(LocalDateTime.now())) {
            return switch (objective) {
                case RAPIDE -> "Aucun signal fort n'a domine. Cette seance reste la meilleure option disponible a court terme.";
                case CONTINUITE -> "Aucun historique fort n'a domine. Cette seance reste la plus proche de votre trajectoire actuelle.";
                case DIVERSIFICATION -> "Aucun signal fort n'a domine. Cette seance ouvre une piste nouvelle tout en restant exploitable.";
                case GENERAL -> "Aucun signal fort n'a domine. Cette seance conserve le meilleur equilibre global.";
            };
        }
        return "Selection pertinente selon l'historique et les contraintes disponibles.";
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void addSignal(List<String> signals, String signal) {
        if (signal == null || signal.isBlank() || signals.contains(signal)) {
            return;
        }
        signals.add(signal);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String normalizeMode(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? Seance.MODE_ONLINE : normalized;
    }

    private String normalizeModeFromReservation(StudentReservationItem reservation) {
        return reservation == null ? "" : normalizeMode(reservation.sessionMode());
    }

    public enum RecommendationObjective {
        GENERAL("Priorite generale", "Classement equilibre entre continuite, disponibilite, format et experience tuteur.", "Equilibre", 6.0),
        RAPIDE("Disponibilite immediate", "Priorite aux creneaux proches encore accessibles.", "Rapide", 5.0),
        CONTINUITE("Continuite pedagogique", "Priorite aux matieres et tuteurs deja coherents avec votre historique accepte.", "Continuite", 3.0),
        DIVERSIFICATION("Diversification maitrisee", "Priorite a des pistes nouvelles portees par des tuteurs fiables.", "Diversification", 10.0);

        private final String label;
        private final String description;
        private final String chipLabel;
        private final double repeatedTutorPenalty;

        RecommendationObjective(String label, String description, String chipLabel, double repeatedTutorPenalty) {
            this.label = label;
            this.description = description;
            this.chipLabel = chipLabel;
            this.repeatedTutorPenalty = repeatedTutorPenalty;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public String chipLabel() {
            return chipLabel;
        }

        public double repeatedTutorPenalty() {
            return repeatedTutorPenalty;
        }
    }

    public record RecommendedSession(
        Seance seance,
        double score,
        String reason,
        List<String> signals,
        int rank,
        boolean highlighted,
        RecommendationObjective objective,
        String confidenceLabel,
        double confidenceProgress,
        String tutorTierLabel,
        boolean fallback
    ) {
        private static RecommendedSession empty(RecommendationObjective objective) {
            return new RecommendedSession(
                null,
                0.0,
                "Seance indisponible.",
                List.of(),
                0,
                false,
                objective,
                "Confiance initiale",
                0.0,
                "Nouveau tuteur",
                true
            );
        }

        public LocalDateTime startAt() {
            return seance != null ? seance.getStartAt() : null;
        }

        public RecommendedSession withRank(int rank, boolean highlighted) {
            return new RecommendedSession(
                seance,
                score,
                reason,
                signals,
                rank,
                highlighted,
                objective,
                confidenceLabel,
                confidenceProgress,
                tutorTierLabel,
                fallback
            );
        }
    }

    private record WeightedReason(double weight, String message) {
    }

    private record ObjectiveWeights(
        double immediate72hBonus,
        double immediate7dBonus,
        double familiarSubjectMultiplier,
        double familiarTutorMultiplier,
        double preferredModeBonus,
        double newSubjectBonus,
        double newTutorBonus,
        double unfamiliarSubjectPenalty
    ) {
    }

    private record StudentRecommendationProfile(
        Map<String, SubjectPreference> subjectPreferences,
        Map<Integer, TutorPreference> tutorPreferences,
        String preferredMode,
        Set<Integer> reservedSeanceIds,
        int acceptedReservations,
        int completedReservations
    ) {
    }

    private record SubjectPreference(int acceptedCount) {
    }

    private record TutorPreference(int acceptedCount) {
    }

    private record TutorExperienceTier(String label, String signal, double scoreBonus, String reason) {
    }

    private static final class SubjectPreferenceAccumulator {
        private int acceptedCount;

        private void register() {
            acceptedCount++;
        }

        private SubjectPreference toPreference() {
            return new SubjectPreference(acceptedCount);
        }
    }

    private static final class TutorPreferenceAccumulator {
        private int acceptedCount;

        private void register() {
            acceptedCount++;
        }

        private TutorPreference toPreference() {
            return new TutorPreference(acceptedCount);
        }
    }
}
