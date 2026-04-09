package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AdminSessionService {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<DateTimeFormatter> INPUT_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );

    private final ObservableList<AdminSession> sessions = FXCollections.observableArrayList();
    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();

    public AdminSessionService() {
        reloadSessions();
    }

    public ObservableList<AdminSession> getSessions() {
        return sessions;
    }

    public List<String> getAvailableStatuses() {
        return List.of("Draft", "Published", "Archived");
    }

    public OperationResult createSession(String subject, String tutor, String schedule, int capacity,
                                         int durationMinutes, String description, String status) {
        try {
            SessionFormData formData = validateAndParse(subject, tutor, schedule, capacity, durationMinutes, description, status);
            Seance seance = new Seance();
            seance.setMatiere(formData.subject());
            seance.setStartAt(formData.startAt());
            seance.setDurationMin(formData.durationMinutes());
            seance.setMaxParticipants(formData.capacity());
            seance.setStatus(formData.statusCode());
            seance.setDescription(formData.description());
            seance.setCreatedAt(LocalDateTime.now());
            seance.setTuteurId(formData.tutorId());

            OperationResult result = seanceService.createSeance(seance);
            if (result.isSuccess()) {
                reloadSessions();
            }
            return result;
        } catch (IllegalArgumentException e) {
            return OperationResult.failure(e.getMessage());
        }
    }

    public OperationResult updateSession(AdminSession session, String subject, String tutor, String schedule, int capacity,
                                         int durationMinutes, String description, String status) {
        if (session == null) {
            return OperationResult.failure("Selectionnez une seance a mettre a jour.");
        }

        try {
            SessionFormData formData = validateAndParse(subject, tutor, schedule, capacity, durationMinutes, description, status);
            Seance seance = new Seance();
            seance.setId(session.getId());
            seance.setMatiere(formData.subject());
            seance.setStartAt(formData.startAt());
            seance.setDurationMin(formData.durationMinutes());
            seance.setMaxParticipants(formData.capacity());
            seance.setStatus(formData.statusCode());
            seance.setDescription(formData.description());
            seance.setUpdatedAt(LocalDateTime.now());
            seance.setTuteurId(formData.tutorId());

            OperationResult result = seanceService.updateSeance(seance);
            if (result.isSuccess()) {
                reloadSessions();
            }
            return result;
        } catch (IllegalArgumentException e) {
            return OperationResult.failure(e.getMessage());
        }
    }

    public void reloadSessions() {
        sessions.setAll(loadSessions());
    }

    private List<AdminSession> loadSessions() {
        List<AdminSession> adminSessions = new ArrayList<>();
        for (Seance seance : seanceService.getAllSeances()) {
            adminSessions.add(new AdminSession(
                seance.getId(),
                seance.getMatiere(),
                tutorDirectoryService.getTutorDisplayName(seance.getTuteurId()),
                formatSchedule(seance.getStartAt()),
                seance.getMaxParticipants(),
                mapStatusToLabel(seance.getStatus()),
                seance.getTuteurId(),
                seance.getDurationMin(),
                seance.getDescription()
            ));
        }
        return adminSessions;
    }

    private SessionFormData validateAndParse(String subject, String tutor, String schedule, int capacity,
                                             int durationMinutes, String description, String status) {
        if (isBlank(subject)) {
            throw new IllegalArgumentException("Renseignez la matiere de la seance.");
        }
        if (isBlank(tutor)) {
            throw new IllegalArgumentException("Renseignez le tuteur ou son ID.");
        }
        if (isBlank(schedule)) {
            throw new IllegalArgumentException("Renseignez le planning de la seance.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacite doit etre superieure a 0.");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("La duree doit etre superieure a 0.");
        }

        int tutorId = tutorDirectoryService.resolveTutorId(tutor);
        if (tutorId <= 0) {
            throw new IllegalArgumentException(
                "Utilisez un ID tuteur valide ou un nom connu pour le moment: "
                    + tutorDirectoryService.getSupportedTutorsHint() + "."
            );
        }

        LocalDateTime startAt = parseSchedule(schedule);
        if (startAt == null) {
            throw new IllegalArgumentException("Utilisez le format de date dd/MM/yyyy HH:mm.");
        }

        return new SessionFormData(
            subject.trim(),
            tutorId,
            startAt,
            capacity,
            durationMinutes,
            normalizeDescription(description),
            mapStatusToCode(status)
        );
    }

    private LocalDateTime parseSchedule(String schedule) {
        String candidate = schedule.trim();
        for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
            try {
                return LocalDateTime.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format.
            }
        }
        return null;
    }

    private String formatSchedule(LocalDateTime startAt) {
        return startAt != null ? startAt.format(DISPLAY_FORMATTER) : "";
    }

    private int mapStatusToCode(String status) {
        if ("Published".equalsIgnoreCase(status)) {
            return 1;
        }
        if ("Archived".equalsIgnoreCase(status)) {
            return 2;
        }
        return 0;
    }

    private String mapStatusToLabel(int status) {
        return switch (status) {
            case 1 -> "Published";
            case 2 -> "Archived";
            default -> "Draft";
        };
    }

    private String normalizeDescription(String description) {
        return isBlank(description) ? null : description.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record SessionFormData(
        String subject,
        int tutorId,
        LocalDateTime startAt,
        int capacity,
        int durationMinutes,
        String description,
        int statusCode
    ) {
    }
}
