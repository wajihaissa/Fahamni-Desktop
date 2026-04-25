package tn.esprit.fahamni.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.UserSession;

public class MockTutorDirectoryService {

    private final Map<Integer, String> tutorsById = new LinkedHashMap<>();
    private final Connection cnx = MyDataBase.getInstance().getCnx();

    public MockTutorDirectoryService() {
        loadTutorsFromDatabase();
        tutorsById.putIfAbsent(3, "Ahmed Ben Ali");
        tutorsById.putIfAbsent(8, "Sarah Mansour");
        if (UserSession.isCurrentTutor()) {
            tutorsById.put(UserSession.getCurrentUserId(), UserSession.getDisplayName());
        }
    }

    public int resolveTutorId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }

        String candidate = value.trim();
        if (candidate.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(candidate);
        }

        String normalizedCandidate = normalize(candidate);
        for (Map.Entry<Integer, String> entry : tutorsById.entrySet()) {
            if (normalize(entry.getValue()).equals(normalizedCandidate)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public String getTutorDisplayName(int tutorId) {
        if (tutorId <= 0) {
            return "Tuteur non defini";
        }

        String cachedName = tutorsById.get(tutorId);
        if (cachedName != null && !cachedName.isBlank()) {
            return cachedName;
        }

        String databaseName = loadTutorNameById(tutorId);
        if (databaseName != null) {
            tutorsById.put(tutorId, databaseName);
            return databaseName;
        }

        return "Tuteur non defini";
    }

    public String getSupportedTutorsHint() {
        return tutorsById.values().stream().collect(Collectors.joining(", "));
    }

    public List<String> getTutorNames() {
        return new ArrayList<>(tutorsById.values());
    }

    private void loadTutorsFromDatabase() {
        if (cnx == null) {
            return;
        }

        String sql = """
            SELECT id, full_name
            FROM user
            WHERE full_name IS NOT NULL
              AND TRIM(full_name) <> ''
            ORDER BY full_name ASC
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                int tutorId = rs.getInt("id");
                String fullName = normalizeDisplayName(rs.getString("full_name"));
                if (tutorId > 0 && fullName != null) {
                    tutorsById.putIfAbsent(tutorId, fullName);
                }
            }
        } catch (SQLException e) {
            System.out.println("Chargement des noms tuteurs impossible: " + e.getMessage());
        }
    }

    private String loadTutorNameById(int tutorId) {
        if (cnx == null || tutorId <= 0) {
            return null;
        }

        String sql = "SELECT full_name FROM user WHERE id = ?";
        try (PreparedStatement pst = cnx.prepareStatement(sql)) {
            pst.setInt(1, tutorId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return normalizeDisplayName(rs.getString("full_name"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Resolution du tuteur impossible: " + e.getMessage());
        }
        return null;
    }

    private String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
