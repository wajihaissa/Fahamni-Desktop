package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SeanceService implements IServices<Seance> {

    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 480;
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 50;
    public static final int MIN_DESCRIPTION_LENGTH = 15;

    private static final DateTimeFormatter SEARCH_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<String> FALLBACK_SUBJECTS = List.of(
        "Mathematics",
        "Physics",
        "Chemistry",
        "English",
        "French",
        "Biology"
    );

    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();

    public boolean hasDatabaseConnection() {
        return getConnection() != null;
    }

    public List<String> getSubjects() {
        List<String> subjects = new ArrayList<>();
        subjects.add(getDefaultSubject());
        Connection cnx = getConnection();

        if (cnx == null) {
            subjects.addAll(FALLBACK_SUBJECTS);
            return subjects;
        }

        String sql = "SELECT DISTINCT matiere FROM seance ORDER BY matiere";
        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                String subject = rs.getString("matiere");
                if (subject != null && !subject.isBlank()) {
                    subjects.add(subject);
                }
            }
        } catch (SQLException e) {
            if (subjects.size() == 1) {
                subjects.addAll(FALLBACK_SUBJECTS);
            }
        }

        if (subjects.size() == 1) {
            subjects.addAll(FALLBACK_SUBJECTS);
        }
        return subjects;
    }

    public String getDefaultSubject() {
        return "All Subjects";
    }

    public String buildSearchSummary(String searchText, String selectedSubject) {
        return "Searching for: " + searchText + " in " + selectedSubject;
    }

    public List<String> getAvailableSearchStatuses() {
        return List.of(
            "Toutes les seances",
            "Publiees",
            "Brouillons",
            "Archivees"
        );
    }

    public List<Seance> getAllSeances() {
        return getAll();
    }

    public Map<Integer, Integer> getReservationCountsBySeanceId() {
        Map<Integer, Integer> reservationCounts = new HashMap<>();
        Connection cnx = getConnection();
        if (cnx == null) {
            return reservationCounts;
        }

        String sql = """
            SELECT seance_id, COUNT(*) AS reservation_count
            FROM reservation
            GROUP BY seance_id
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                reservationCounts.put(rs.getInt("seance_id"), rs.getInt("reservation_count"));
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement compteur reservations: " + e.getMessage());
        }
        return reservationCounts;
    }

    @Override
    public List<Seance> getAll() {
        List<Seance> seances = new ArrayList<>();
        Connection cnx = getConnection();
        if (cnx == null) {
            return seances;
        }

        String sql = """
            SELECT id, matiere, start_at, duration_min, max_participants, status,
                   description, created_at, updated_at, tuteur_id
            FROM seance
            ORDER BY id DESC
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                seances.add(mapSeance(rs));
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement seances: " + e.getMessage());
        }
        return seances;
    }

    public List<Seance> search(String keyword, String statusFilter, int limit) {
        return getAll().stream()
            .filter(seance -> matchesStatusFilter(seance, statusFilter))
            .filter(seance -> matchesKeywordFilter(seance, keyword))
            .limit(limit > 0 ? limit : Long.MAX_VALUE)
            .toList();
    }

    public OperationResult createSeance(Seance seance) {
        try {
            add(seance);
            return OperationResult.success("Seance ajoutee avec succes.");
        } catch (RuntimeException e) {
            return OperationResult.failure(e.getMessage());
        }
    }

    @Override
    public void add(Seance seance) {
        String validationError = validateSeance(seance);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        String schedulingConflict = validateScheduleConflict(seance);
        if (schedulingConflict != null) {
            throw new IllegalArgumentException(schedulingConflict);
        }

        String sql = """
            INSERT INTO seance (matiere, start_at, duration_min, max_participants, status, description, created_at, updated_at, tuteur_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            String normalizedSubject = normalizeText(seance.getMatiere());
            String normalizedDescription = normalizeText(seance.getDescription());

            pst.setString(1, normalizedSubject);
            pst.setTimestamp(2, Timestamp.valueOf(seance.getStartAt()));
            pst.setInt(3, seance.getDurationMin());
            pst.setInt(4, seance.getMaxParticipants());
            pst.setInt(5, seance.getStatus());
            pst.setString(6, blankToNull(normalizedDescription));
            pst.setTimestamp(7, Timestamp.valueOf(seance.getCreatedAt() != null ? seance.getCreatedAt() : now));
            pst.setTimestamp(8, seance.getUpdatedAt() != null ? Timestamp.valueOf(seance.getUpdatedAt()) : null);
            pst.setInt(9, seance.getTuteurId());
            pst.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Ajout impossible: " + e.getMessage(), e);
        }
    }

    public OperationResult updateSeance(Seance seance) {
        try {
            update(seance);
            return OperationResult.success("Seance mise a jour.");
        } catch (RuntimeException e) {
            return OperationResult.failure(e.getMessage());
        }
    }

    @Override
    public void update(Seance seance) {
        String validationError = validateSeance(seance);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        if (seance.getId() <= 0) {
            throw new IllegalArgumentException("Selectionnez une seance valide.");
        }
        String schedulingConflict = validateScheduleConflict(seance);
        if (schedulingConflict != null) {
            throw new IllegalArgumentException(schedulingConflict);
        }

        String sql = """
            UPDATE seance
            SET matiere = ?, start_at = ?, duration_min = ?, max_participants = ?,
                status = ?, description = ?, updated_at = ?, tuteur_id = ?
            WHERE id = ?
            """;

        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            String normalizedSubject = normalizeText(seance.getMatiere());
            String normalizedDescription = normalizeText(seance.getDescription());

            pst.setString(1, normalizedSubject);
            pst.setTimestamp(2, Timestamp.valueOf(seance.getStartAt()));
            pst.setInt(3, seance.getDurationMin());
            pst.setInt(4, seance.getMaxParticipants());
            pst.setInt(5, seance.getStatus());
            pst.setString(6, blankToNull(normalizedDescription));
            pst.setTimestamp(7, Timestamp.valueOf(seance.getUpdatedAt() != null ? seance.getUpdatedAt() : LocalDateTime.now()));
            pst.setInt(8, seance.getTuteurId());
            pst.setInt(9, seance.getId());

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                throw new IllegalStateException("Aucune seance mise a jour.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Mise a jour impossible: " + e.getMessage(), e);
        }
    }

    public OperationResult deleteSeance(int seanceId) {
        try {
            Seance seance = new Seance();
            seance.setId(seanceId);
            delete(seance);
            return OperationResult.success("Seance supprimee avec succes.");
        } catch (RuntimeException e) {
            return OperationResult.failure(e.getMessage());
        }
    }

    @Override
    public void delete(Seance seance) {
        if (seance == null || seance.getId() <= 0) {
            throw new IllegalArgumentException("Selectionnez une seance valide.");
        }

        int linkedReservations = countReservationsForSeance(seance.getId());
        if (linkedReservations > 0) {
            String suffix = linkedReservations > 1 ? " reservations." : " reservation.";
            throw new IllegalStateException(
                "Suppression impossible: cette seance possede " + linkedReservations + suffix
            );
        }

        String sql = "DELETE FROM seance WHERE id = ?";
        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            pst.setInt(1, seance.getId());
            int deletedRows = pst.executeUpdate();
            if (deletedRows == 0) {
                throw new IllegalStateException("Aucune seance supprimee.");
            }
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                throw new IllegalStateException("Suppression impossible: cette seance est deja liee a une reservation.", e);
            }
            throw new IllegalStateException("Suppression impossible: " + e.getMessage(), e);
        }
    }

    private int countReservationsForSeance(int seanceId) {
        String sql = "SELECT COUNT(*) FROM reservation WHERE seance_id = ?";
        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Verification des reservations impossible: " + e.getMessage(), e);
        }
    }

    private Seance mapSeance(ResultSet rs) throws SQLException {
        return new Seance(
            rs.getInt("id"),
            rs.getString("matiere"),
            readDateTime(rs, "start_at"),
            rs.getInt("duration_min"),
            rs.getInt("max_participants"),
            rs.getInt("status"),
            rs.getString("description"),
            readDateTime(rs, "created_at"),
            readDateTime(rs, "updated_at"),
            rs.getInt("tuteur_id")
        );
    }

    private LocalDateTime readDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private String validateSeance(Seance seance) {
        if (seance == null) {
            return "Aucune seance a enregistrer.";
        }
        String normalizedSubject = normalizeText(seance.getMatiere());
        String normalizedDescription = normalizeText(seance.getDescription());

        if (normalizedSubject == null || normalizedSubject.isEmpty()) {
            return "Renseignez la matiere.";
        }
        if (seance.getStartAt() == null) {
            return "Renseignez la date et l'heure de la seance.";
        }
        if (!seance.getStartAt().isAfter(LocalDateTime.now())) {
            return "La date de la seance doit etre dans le futur.";
        }
        if (seance.getDurationMin() < MIN_DURATION_MINUTES || seance.getDurationMin() > MAX_DURATION_MINUTES) {
            return "La duree doit etre comprise entre " + MIN_DURATION_MINUTES + " et " + MAX_DURATION_MINUTES + " minutes.";
        }
        if (seance.getMaxParticipants() < MIN_CAPACITY || seance.getMaxParticipants() > MAX_CAPACITY) {
            return "La capacite doit etre comprise entre " + MIN_CAPACITY + " et " + MAX_CAPACITY + " participants.";
        }
        if (normalizedDescription == null || normalizedDescription.length() < MIN_DESCRIPTION_LENGTH) {
            return "Ajoutez une description d'au moins " + MIN_DESCRIPTION_LENGTH + " caracteres.";
        }
        if (seance.getTuteurId() <= 0) {
            return "Le tuteur doit avoir un identifiant valide.";
        }
        return null;
    }

    private String validateScheduleConflict(Seance candidate) {
        if (candidate == null || candidate.getStatus() == 2 || candidate.getStartAt() == null || candidate.getDurationMin() <= 0) {
            return null;
        }

        LocalDateTime candidateEndAt = calculateEndAt(candidate.getStartAt(), candidate.getDurationMin());
        if (candidateEndAt == null) {
            return null;
        }

        return getAll().stream()
            .filter(existing -> existing.getId() != candidate.getId())
            .filter(existing -> existing.getTuteurId() == candidate.getTuteurId())
            .filter(existing -> existing.getStatus() != 2)
            .filter(existing -> existing.getStartAt() != null)
            .filter(existing -> existing.getDurationMin() > 0)
            .filter(existing -> hasOverlap(
                candidate.getStartAt(),
                candidateEndAt,
                existing.getStartAt(),
                calculateEndAt(existing.getStartAt(), existing.getDurationMin())
            ))
            .findFirst()
            .map(this::buildConflictMessage)
            .orElse(null);
    }

    private boolean hasOverlap(LocalDateTime firstStartAt, LocalDateTime firstEndAt,
                               LocalDateTime secondStartAt, LocalDateTime secondEndAt) {
        if (firstStartAt == null || firstEndAt == null || secondStartAt == null || secondEndAt == null) {
            return false;
        }
        return firstStartAt.isBefore(secondEndAt) && secondStartAt.isBefore(firstEndAt);
    }

    private LocalDateTime calculateEndAt(LocalDateTime startAt, int durationMinutes) {
        if (startAt == null || durationMinutes <= 0) {
            return null;
        }
        return startAt.plusMinutes(durationMinutes);
    }

    private String buildConflictMessage(Seance conflictingSeance) {
        String subject = normalizeText(conflictingSeance.getMatiere());
        String safeSubject = subject != null ? subject : "sans titre";
        return "Conflit d'horaire: ce tuteur a deja une seance \"" + safeSubject
            + "\" prevue le " + formatDateTime(conflictingSeance.getStartAt())
            + " pour " + conflictingSeance.getDurationMin() + " min.";
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private Connection getConnection() {
        return MyDataBase.getInstance().getCnx();
    }

    private Connection requireConnection() {
        Connection cnx = getConnection();
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base indisponible.");
        }
        return cnx;
    }

    private boolean matchesStatusFilter(Seance seance, String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "Toutes les seances".equalsIgnoreCase(statusFilter)) {
            return true;
        }
        return switch (statusFilter) {
            case "Publiees" -> seance.getStatus() == 1;
            case "Brouillons" -> seance.getStatus() == 0;
            case "Archivees" -> seance.getStatus() == 2;
            default -> true;
        };
    }

    private boolean matchesKeywordFilter(Seance seance, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }

        String normalizedKeyword = normalize(keyword);
        String searchableText = String.join(" ",
            String.valueOf(seance.getId()),
            safe(seance.getMatiere()),
            safe(seance.getDescription()),
            String.valueOf(seance.getTuteurId()),
            safe(tutorDirectoryService.getTutorDisplayName(seance.getTuteurId())),
            safe(formatDateTime(seance.getStartAt())),
            safe(mapStatusLabel(seance.getStatus()))
        );
        return normalize(searchableText).contains(normalizedKeyword);
    }

    private String mapStatusLabel(int status) {
        return switch (status) {
            case 1 -> "Publiee";
            case 2 -> "Archivee";
            default -> "Brouillon";
        };
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(SEARCH_DATE_FORMATTER) : "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
