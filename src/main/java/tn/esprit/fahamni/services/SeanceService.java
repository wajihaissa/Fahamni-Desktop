package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.interfaces.IServices;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private final Connection cnx = MyDataBase.getInstance().getCnx();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();

    public List<String> getSubjects() {
        List<String> subjects = new ArrayList<>();
        subjects.add(getDefaultSubject());

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

    @Override
    public List<Seance> getAll() {
        List<Seance> seances = new ArrayList<>();
        if (cnx == null) {
            return seances;
        }

        String sql = """
            SELECT id, matiere, start_at, duration_min, max_participants, status,
                   description, created_at, updated_at, tuteur_id, mode_seance, salle_id
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
        attachEquipementIds(seances);
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
        normalizeInfrastructureFields(seance);
        String validationError = validateSeance(seance);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        String schedulingConflict = validateScheduleConflict(seance);
        if (schedulingConflict != null) {
            throw new IllegalArgumentException(schedulingConflict);
        }
        String roomConflict = validateRoomScheduleConflict(seance);
        if (roomConflict != null) {
            throw new IllegalArgumentException(roomConflict);
        }

        String sql = """
            INSERT INTO seance (
                matiere, start_at, duration_min, max_participants, status, description,
                created_at, updated_at, tuteur_id, mode_seance, salle_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pst = requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            pst.setString(10, seance.getMode());
            setNullableInteger(pst, 11, seance.getSalleId());
            pst.executeUpdate();

            try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    seance.setId(generatedKeys.getInt(1));
                }
            }
            replaceSeanceEquipements(seance);
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
        normalizeInfrastructureFields(seance);
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
        String roomConflict = validateRoomScheduleConflict(seance);
        if (roomConflict != null) {
            throw new IllegalArgumentException(roomConflict);
        }

        String sql = """
            UPDATE seance
            SET matiere = ?, start_at = ?, duration_min = ?, max_participants = ?,
                status = ?, description = ?, updated_at = ?, tuteur_id = ?,
                mode_seance = ?, salle_id = ?
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
            pst.setString(9, seance.getMode());
            setNullableInteger(pst, 10, seance.getSalleId());
            pst.setInt(11, seance.getId());

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                throw new IllegalStateException("Aucune seance mise a jour.");
            }
            replaceSeanceEquipements(seance);
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

        String sql = "DELETE FROM seance WHERE id = ?";
        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            deleteSeanceEquipements(seance.getId());
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

    private Seance mapSeance(ResultSet rs) throws SQLException {
        Seance seance = new Seance(
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
        seance.setMode(normalizeMode(rs.getString("mode_seance")));
        seance.setSalleId(getNullableInteger(rs, "salle_id"));
        return seance;
    }

    private LocalDateTime readDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
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
        String infrastructureError = validateInfrastructure(seance);
        if (infrastructureError != null) {
            return infrastructureError;
        }
        return null;
    }

    private String validateInfrastructure(Seance seance) {
        String mode = normalizeMode(seance.getMode());
        if (!Seance.MODE_ONLINE.equals(mode) && !Seance.MODE_ONSITE.equals(mode)) {
            return "Choisissez un mode de seance valide.";
        }

        if (!Seance.MODE_ONSITE.equals(mode)) {
            return null;
        }

        if (seance.getSalleId() == null || seance.getSalleId() <= 0) {
            return "Choisissez une salle pour la seance presentielle.";
        }

        String roomError = validateSelectedRoom(seance);
        if (roomError != null) {
            return roomError;
        }

        return validateSelectedEquipements(seance);
    }

    private String validateSelectedRoom(Seance seance) {
        if (cnx == null) {
            return "Connexion a la base indisponible pour verifier la salle.";
        }

        String sql = "SELECT nom, capacite, etat FROM salle WHERE idSalle = ?";
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seance.getSalleId());
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return "La salle choisie est introuvable.";
                }
                String roomName = rs.getString("nom");
                int capacity = rs.getInt("capacite");
                String status = rs.getString("etat");

                if (!isAvailable(status)) {
                    return "La salle \"" + roomName + "\" n'est pas disponible.";
                }
                if (seance.getMaxParticipants() > capacity) {
                    return "La capacite de la salle \"" + roomName + "\" est limitee a " + capacity + " places.";
                }
            }
        } catch (SQLException e) {
            return "Verification de la salle impossible: " + e.getMessage();
        }
        return null;
    }

    private String validateSelectedEquipements(Seance seance) {
        Map<Integer, Integer> equipementQuantites = seance.getEquipementQuantites();
        if (equipementQuantites.isEmpty()) {
            return null;
        }
        if (cnx == null) {
            return "Connexion a la base indisponible pour verifier le materiel.";
        }

        String sql = "SELECT nom, quantiteDisponible, etat FROM equipement WHERE idEquipement = ?";
        for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
            Integer equipementId = entry.getKey();
            int quantiteDemandee = entry.getValue() == null ? 1 : entry.getValue();
            if (equipementId == null || equipementId <= 0) {
                return "Le materiel choisi est invalide.";
            }
            if (quantiteDemandee <= 0) {
                return "La quantite de materiel doit etre superieure a zero.";
            }

            try (PreparedStatement pst = cnx.prepareStatement(sql)) {
                pst.setInt(1, equipementId);
                try (ResultSet rs = pst.executeQuery()) {
                    if (!rs.next()) {
                        return "Le materiel #" + equipementId + " est introuvable.";
                    }
                    String equipmentName = rs.getString("nom");
                    int quantity = rs.getInt("quantiteDisponible");
                    String status = rs.getString("etat");

                    if (!isAvailable(status) || quantity <= 0) {
                        return "Le materiel \"" + equipmentName + "\" n'est pas disponible.";
                    }
                    if (quantiteDemandee > quantity) {
                        return "Le materiel \"" + equipmentName + "\" est disponible a hauteur de " + quantity + " unite(s).";
                    }
                }
            } catch (SQLException e) {
                return "Verification du materiel impossible: " + e.getMessage();
            }
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

    private String validateRoomScheduleConflict(Seance candidate) {
        if (candidate == null
            || !candidate.isPresentiel()
            || candidate.getSalleId() == null
            || candidate.getStatus() == 2
            || candidate.getStartAt() == null
            || candidate.getDurationMin() <= 0) {
            return null;
        }

        LocalDateTime candidateEndAt = calculateEndAt(candidate.getStartAt(), candidate.getDurationMin());
        if (candidateEndAt == null) {
            return null;
        }

        return getAll().stream()
            .filter(existing -> existing.getId() != candidate.getId())
            .filter(Seance::isPresentiel)
            .filter(existing -> candidate.getSalleId().equals(existing.getSalleId()))
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
            .map(conflict -> "Conflit de salle: cette salle est deja utilisee le "
                + formatDateTime(conflict.getStartAt())
                + " pour la seance \"" + safe(conflict.getMatiere()) + "\".")
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

    private void normalizeInfrastructureFields(Seance seance) {
        if (seance == null) {
            return;
        }

        String mode = normalizeMode(seance.getMode());
        seance.setMode(mode);
        if (!Seance.MODE_ONSITE.equals(mode)) {
            seance.setSalleId(null);
            seance.setEquipementQuantites(Map.of());
            return;
        }

        LinkedHashMap<Integer, Integer> normalizedQuantities = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : seance.getEquipementQuantites().entrySet()) {
            Integer equipementId = entry.getKey();
            if (equipementId == null || equipementId <= 0) {
                continue;
            }
            int quantite = entry.getValue() == null || entry.getValue() <= 0 ? 1 : entry.getValue();
            normalizedQuantities.put(equipementId, quantite);
        }

        for (Integer equipementId : new LinkedHashSet<>(seance.getEquipementIds())) {
            if (equipementId == null || equipementId <= 0 || normalizedQuantities.containsKey(equipementId)) {
                continue;
            }
            normalizedQuantities.put(equipementId, 1);
        }

        seance.setEquipementQuantites(normalizedQuantities);
    }

    private void attachEquipementIds(List<Seance> seances) {
        if (seances == null || seances.isEmpty()) {
            return;
        }

        for (Seance seance : seances) {
            seance.setEquipementQuantites(loadEquipementQuantitesBySeanceId(seance.getId()));
        }
    }

    private Map<Integer, Integer> loadEquipementQuantitesBySeanceId(int seanceId) {
        LinkedHashMap<Integer, Integer> equipementQuantites = new LinkedHashMap<>();
        if (seanceId <= 0 || cnx == null) {
            return equipementQuantites;
        }

        String sql = "SELECT idEquipement, quantite FROM seance_equipement WHERE seance_id = ? ORDER BY idEquipement";
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    equipementQuantites.put(rs.getInt("idEquipement"), Math.max(1, rs.getInt("quantite")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Chargement materiel seance impossible: " + e.getMessage());
        }
        return equipementQuantites;
    }

    private void replaceSeanceEquipements(Seance seance) throws SQLException {
        if (seance == null || seance.getId() <= 0) {
            return;
        }

        deleteSeanceEquipements(seance.getId());
        if (!seance.isPresentiel() || seance.getEquipementQuantites().isEmpty()) {
            return;
        }

        String sql = "INSERT INTO seance_equipement (seance_id, idEquipement, quantite) VALUES (?, ?, ?)";
        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            for (Map.Entry<Integer, Integer> entry : seance.getEquipementQuantites().entrySet()) {
                Integer equipementId = entry.getKey();
                if (equipementId == null || equipementId <= 0) {
                    continue;
                }
                pst.setInt(1, seance.getId());
                pst.setInt(2, equipementId);
                pst.setInt(3, Math.max(1, entry.getValue() == null ? 1 : entry.getValue()));
                pst.addBatch();
            }
            pst.executeBatch();
        }
    }

    private void deleteSeanceEquipements(int seanceId) throws SQLException {
        if (seanceId <= 0) {
            return;
        }

        String sql = "DELETE FROM seance_equipement WHERE seance_id = ?";
        try (PreparedStatement pst = requireConnection().prepareStatement(sql)) {
            pst.setInt(1, seanceId);
            pst.executeUpdate();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private String normalizeMode(String value) {
        String normalized = normalize(value);
        if ("presentiel".equals(normalized)) {
            return Seance.MODE_ONSITE;
        }
        return Seance.MODE_ONLINE;
    }

    private boolean isAvailable(String status) {
        return "disponible".equals(normalize(status));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private Connection requireConnection() {
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
            safe(mapStatusLabel(seance.getStatus())),
            safe(mapModeLabel(seance.getMode())),
            seance.getSalleId() == null ? "" : "salle " + seance.getSalleId(),
            seance.getEquipementQuantites().toString()
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

    private String mapModeLabel(String mode) {
        return Seance.MODE_ONSITE.equals(mode) ? "Presentiel" : "En ligne";
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
