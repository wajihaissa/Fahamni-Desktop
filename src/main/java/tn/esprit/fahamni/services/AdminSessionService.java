package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

public class AdminSessionService {

    private final ObservableList<AdminSession> sessions = FXCollections.observableArrayList(
        new AdminSession("Mathematics - Algebra", "Ahmed Ben Ali", "03 Apr 2026 - 10:00", 12, "Published"),
        new AdminSession("Physics - Mechanics", "Sarah Mansour", "04 Apr 2026 - 14:00", 10, "Published"),
        new AdminSession("Chemistry - Organic", "Mohamed Trabelsi", "05 Apr 2026 - 16:30", 8, "Draft")
    );

    public ObservableList<AdminSession> getSessions() {
        return sessions;
    }

    public List<String> getAvailableStatuses() {
        return List.of("Draft", "Published", "Archived");
    }

    public OperationResult createSession(String subject, String tutor, String schedule, int capacity, String status) {
        if (isBlank(subject) || isBlank(tutor) || isBlank(schedule)) {
            return OperationResult.failure("Completez les informations de la seance.");
        }

        sessions.add(new AdminSession(subject.trim(), tutor.trim(), schedule.trim(), capacity, status));
        return OperationResult.success("Seance ajoutee au planning admin.");
    }

    public OperationResult updateSession(AdminSession session, String subject, String tutor, String schedule, int capacity, String status) {
        if (session == null) {
            return OperationResult.failure("Selectionnez une seance a mettre a jour.");
        }

        session.setSubject(subject.trim());
        session.setTutor(tutor.trim());
        session.setSchedule(schedule.trim());
        session.setCapacity(capacity);
        session.setStatus(status);
        return OperationResult.success("Seance mise a jour.");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

