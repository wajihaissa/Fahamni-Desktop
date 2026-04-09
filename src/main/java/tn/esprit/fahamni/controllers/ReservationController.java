package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.SeanceService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class ReservationController {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<DateTimeFormatter> INPUT_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );

    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();

    @FXML
    private Label publishedSessionsCountLabel;

    @FXML
    private Label draftSessionsCountLabel;

    @FXML
    private ComboBox<String> tutorComboBox;

    @FXML
    private TextField sessionSubjectField;

    @FXML
    private TextField sessionStartAtField;

    @FXML
    private TextField sessionDurationField;

    @FXML
    private TextField sessionCapacityField;

    @FXML
    private TextArea sessionDescriptionArea;

    @FXML
    private Label publishFeedbackLabel;

    @FXML
    private VBox recentSessionsContainer;

    @FXML
    private void initialize() {
        tutorComboBox.getItems().setAll(tutorDirectoryService.getTutorNames());
        if (!tutorComboBox.getItems().isEmpty()) {
            tutorComboBox.setValue(tutorComboBox.getItems().get(0));
        }
        hideFeedback();
        loadSessionDashboard();
    }

    @FXML
    private void handleSaveDraftSession() {
        submitSession(0, "La seance a ete enregistree en brouillon.");
    }

    @FXML
    private void handlePublishSession() {
        submitSession(1, "La seance a ete publiee avec succes.");
    }

    @FXML
    private void handleClearSessionForm() {
        clearSessionForm();
        hideFeedback();
    }

    private void submitSession(int status, String successMessage) {
        hideFeedback();

        try {
            Seance seance = buildSeance(status);
            OperationResult result = seanceService.createSeance(seance);
            if (result.isSuccess()) {
                clearSessionForm();
                loadSessionDashboard();
                showFeedback(successMessage, true);
                return;
            }
            showFeedback(result.getMessage(), false);
        } catch (IllegalArgumentException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    private Seance buildSeance(int status) {
        String subject = requireText(sessionSubjectField.getText(), "Renseignez la matiere de la seance.");
        String tutorName = requireText(tutorComboBox.getValue(), "Choisissez un tuteur.");
        LocalDateTime startAt = parseStartAt(sessionStartAtField.getText());
        int duration = parsePositiveInt(sessionDurationField.getText(), "La duree doit etre un entier positif.");
        int capacity = parsePositiveInt(sessionCapacityField.getText(), "Le nombre de participants doit etre un entier positif.");
        int tutorId = tutorDirectoryService.resolveTutorId(tutorName);

        if (tutorId <= 0) {
            throw new IllegalArgumentException("Le tuteur choisi est invalide pour le moment.");
        }

        Seance seance = new Seance();
        seance.setMatiere(subject);
        seance.setStartAt(startAt);
        seance.setDurationMin(duration);
        seance.setMaxParticipants(capacity);
        seance.setStatus(status);
        seance.setDescription(blankToNull(sessionDescriptionArea.getText()));
        seance.setCreatedAt(LocalDateTime.now());
        seance.setTuteurId(tutorId);
        return seance;
    }

    private void loadSessionDashboard() {
        List<Seance> seances = seanceService.getAllSeances();
        long publishedCount = seances.stream().filter(seance -> seance.getStatus() == 1).count();
        long draftCount = seances.stream().filter(seance -> seance.getStatus() == 0).count();

        publishedSessionsCountLabel.setText(String.valueOf(publishedCount));
        draftSessionsCountLabel.setText(String.valueOf(draftCount));

        recentSessionsContainer.getChildren().clear();
        if (seances.isEmpty()) {
            Label emptyLabel = new Label("Aucune seance disponible pour le moment. La premiere publication apparaitra ici.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            return;
        }

        seances.stream()
            .limit(4)
            .map(this::buildRecentSessionCard)
            .forEach(recentSessionsContainer.getChildren()::add);
    }

    private VBox buildRecentSessionCard(Seance seance) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-form-shell");

        HBox headerRow = new HBox(10.0);
        Label titleLabel = new Label(seance.getMatiere());
        titleLabel.getStyleClass().add("subsection-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusChip = new Label(mapStatusLabel(seance.getStatus()));
        statusChip.getStyleClass().addAll("reservation-status", mapStatusStyle(seance.getStatus()));
        headerRow.getChildren().addAll(titleLabel, spacer, statusChip);

        Label metaLabel = new Label(
            "Tuteur: " + tutorDirectoryService.getTutorDisplayName(seance.getTuteurId())
                + " | " + formatDateTime(seance.getStartAt())
                + " | " + seance.getDurationMin() + " min"
                + " | " + seance.getMaxParticipants() + " places"
        );
        metaLabel.setWrapText(true);
        metaLabel.getStyleClass().add("reservation-section-copy");

        Label descriptionLabel = new Label(
            seance.getDescription() != null && !seance.getDescription().isBlank()
                ? seance.getDescription()
                : "Aucune description ajoutee pour cette seance."
        );
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("reservation-section-copy");

        card.getChildren().addAll(headerRow, metaLabel, descriptionLabel);
        return card;
    }

    private LocalDateTime parseStartAt(String value) {
        String candidate = requireText(value, "Renseignez la date et l'heure de la seance.");
        for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
            try {
                return LocalDateTime.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format.
            }
        }
        throw new IllegalArgumentException("Utilisez le format dd/MM/yyyy HH:mm pour la date.");
    }

    private int parsePositiveInt(String value, String errorMessage) {
        String candidate = requireText(value, errorMessage);
        try {
            int parsedValue = Integer.parseInt(candidate);
            if (parsedValue <= 0) {
                throw new NumberFormatException();
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String requireText(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DISPLAY_FORMATTER) : "";
    }

    private String mapStatusLabel(int status) {
        return switch (status) {
            case 1 -> "Publiee";
            case 2 -> "Archivee";
            default -> "Brouillon";
        };
    }

    private String mapStatusStyle(int status) {
        return switch (status) {
            case 1 -> "confirmed";
            case 2 -> "completed";
            default -> "pending";
        };
    }

    private void clearSessionForm() {
        sessionSubjectField.clear();
        sessionStartAtField.clear();
        sessionDurationField.clear();
        sessionCapacityField.clear();
        sessionDescriptionArea.clear();
        if (!tutorComboBox.getItems().isEmpty()) {
            tutorComboBox.setValue(tutorComboBox.getItems().get(0));
        }
    }

    private void showFeedback(String message, boolean success) {
        publishFeedbackLabel.setText(message);
        publishFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", success ? "success" : "error");
        publishFeedbackLabel.setManaged(true);
        publishFeedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        publishFeedbackLabel.setText("");
        publishFeedbackLabel.getStyleClass().setAll("frontoffice-feedback");
        publishFeedbackLabel.setManaged(false);
        publishFeedbackLabel.setVisible(false);
    }
}

