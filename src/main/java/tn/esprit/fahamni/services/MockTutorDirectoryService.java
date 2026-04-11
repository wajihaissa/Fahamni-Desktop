package tn.esprit.fahamni.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MockTutorDirectoryService {

    private final Map<Integer, String> tutorsById = new LinkedHashMap<>();

    public MockTutorDirectoryService() {
        tutorsById.put(3, "Ahmed Ben Ali");
        tutorsById.put(8, "Sarah Mansour");
        tutorsById.put(TemporaryUserContext.getCurrentTutorId(), TemporaryUserContext.getCurrentTutorName());
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
        return tutorsById.getOrDefault(tutorId, "Tuteur #" + tutorId);
    }

    public String getSupportedTutorsHint() {
        return tutorsById.values().stream().collect(Collectors.joining(", "));
    }

    public List<String> getTutorNames() {
        return new ArrayList<>(tutorsById.values());
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
