package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Seance;
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

public class SeanceService {

    private static final List<String> FALLBACK_SUBJECTS = List.of(
        "Mathematics",
        "Physics",
        "Chemistry",
        "English",
        "French",
        "Biology"
    );

    private final Connection cnx = MyDataBase.getInstance().getCnx();

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

    public List<Seance> getAllSeances() {
        List<Seance> seances = new ArrayList<>();
        if (cnx == null) {
            return seances;
        }

        String sql = """
            SELECT id, matiere, start_at, duration_min, max_participants, status,
                   description, created_at, updated_at, tuteur_id
            FROM seance
            ORDER BY start_at DESC
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

    public OperationResult createSeance(Seance seance) {
        String validationError = validateSeance(seance);
        if (validationError != null) {
            return OperationResult.failure(validationError);
        }
        if (cnx == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }

        String sql = """
            INSERT INTO seance (matiere, start_at, duration_min, max_participants, status, description, created_at, updated_at, tuteur_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            pst.setString(1, seance.getMatiere().trim());
            pst.setTimestamp(2, Timestamp.valueOf(seance.getStartAt()));
            pst.setInt(3, seance.getDurationMin());
            pst.setInt(4, seance.getMaxParticipants());
            pst.setInt(5, seance.getStatus());
            pst.setString(6, blankToNull(seance.getDescription()));
            pst.setTimestamp(7, Timestamp.valueOf(seance.getCreatedAt() != null ? seance.getCreatedAt() : now));
            pst.setTimestamp(8, seance.getUpdatedAt() != null ? Timestamp.valueOf(seance.getUpdatedAt()) : null);
            pst.setInt(9, seance.getTuteurId());
            pst.executeUpdate();
            return OperationResult.success("Seance ajoutee avec succes.");
        } catch (SQLException e) {
            return OperationResult.failure("Ajout impossible: " + e.getMessage());
        }
    }

    public OperationResult updateSeance(Seance seance) {
        String validationError = validateSeance(seance);
        if (validationError != null) {
            return OperationResult.failure(validationError);
        }
        if (seance.getId() <= 0) {
            return OperationResult.failure("Selectionnez une seance valide.");
        }
        if (cnx == null) {
            return OperationResult.failure("Connexion a la base indisponible.");
        }

        String sql = """
            UPDATE seance
            SET matiere = ?, start_at = ?, duration_min = ?, max_participants = ?,
                status = ?, description = ?, updated_at = ?, tuteur_id = ?
            WHERE id = ?
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setString(1, seance.getMatiere().trim());
            pst.setTimestamp(2, Timestamp.valueOf(seance.getStartAt()));
            pst.setInt(3, seance.getDurationMin());
            pst.setInt(4, seance.getMaxParticipants());
            pst.setInt(5, seance.getStatus());
            pst.setString(6, blankToNull(seance.getDescription()));
            pst.setTimestamp(7, Timestamp.valueOf(seance.getUpdatedAt() != null ? seance.getUpdatedAt() : LocalDateTime.now()));
            pst.setInt(8, seance.getTuteurId());
            pst.setInt(9, seance.getId());

            int updatedRows = pst.executeUpdate();
            if (updatedRows == 0) {
                return OperationResult.failure("Aucune seance mise a jour.");
            }
            return OperationResult.success("Seance mise a jour.");
        } catch (SQLException e) {
            return OperationResult.failure("Mise a jour impossible: " + e.getMessage());
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
        if (seance.getMatiere() == null || seance.getMatiere().trim().isEmpty()) {
            return "Renseignez la matiere.";
        }
        if (seance.getStartAt() == null) {
            return "Renseignez la date et l'heure de la seance.";
        }
        if (seance.getDurationMin() <= 0) {
            return "La duree doit etre superieure a 0.";
        }
        if (seance.getMaxParticipants() <= 0) {
            return "La capacite doit etre superieure a 0.";
        }
        if (seance.getTuteurId() <= 0) {
            return "Le tuteur doit avoir un identifiant valide.";
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
