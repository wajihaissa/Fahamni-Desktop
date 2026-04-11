package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AdminSessionService {

    private static final String STATUS_DRAFT_LABEL = "Brouillon";
    private static final String STATUS_PUBLISHED_LABEL = "Publiee";
    private static final String STATUS_ARCHIVED_LABEL = "Archivee";
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ObservableList<AdminSession> sessions = FXCollections.observableArrayList();
    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();

    public AdminSessionService() {
        reloadSessions();
    }

    public ObservableList<AdminSession> getSessions() {
        return sessions;
    }

    public OperationResult deleteSession(AdminSession session) {
        if (session == null || session.getId() <= 0) {
            return OperationResult.failure("Selectionnez une seance a supprimer.");
        }

        OperationResult result = seanceService.deleteSeance(session.getId());
        if (result.isSuccess()) {
            reloadSessions();
        }
        return result;
    }

    public void reloadSessions() {
        sessions.setAll(loadSessions());
    }

    private List<AdminSession> loadSessions() {
        List<AdminSession> adminSessions = new ArrayList<>();
        for (Seance seance : seanceService.getAll()) {
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

    private String formatSchedule(LocalDateTime startAt) {
        return startAt != null ? startAt.format(DISPLAY_FORMATTER) : "";
    }

    private String mapStatusToLabel(int status) {
        return switch (status) {
            case 1 -> STATUS_PUBLISHED_LABEL;
            case 2 -> STATUS_ARCHIVED_LABEL;
            default -> STATUS_DRAFT_LABEL;
        };
    }
}
