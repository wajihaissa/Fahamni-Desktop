package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.interfaces.ISeanceSearchService;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.SeanceService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
import java.util.Optional;

public class ReservationController {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<DateTimeFormatter> INPUT_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );

    private final SeanceService seanceService = new SeanceService();
    private final IServices<Seance> seanceCrudService = seanceService;
    private final ISeanceSearchService seanceSearchService = seanceService;
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();
    private Integer editingSessionId;

    @FXML
    private Label publishedSessionsCountLabel;

    @FXML
    private Label draftSessionsCountLabel;

    @FXML
    private ComboBox<String> tutorComboBox;

    @FXML
    private TextField sessionSearchField;

    @FXML
    private ComboBox<String> sessionSearchModeComboBox;

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
    private Label sessionFormTitleLabel;

    @FXML
    private Label sessionFormModeChipLabel;

    @FXML
    private Label editingSessionLabel;

    @FXML
    private VBox recentSessionsContainer;

    @FXML
    private void initialize() {
        tutorComboBox.getItems().setAll(tutorDirectoryService.getTutorNames());
        tutorComboBox.setEditable(true);
        if (!tutorComboBox.getItems().isEmpty()) {
            tutorComboBox.setValue(tutorComboBox.getItems().get(0));
        }

        sessionSearchModeComboBox.getItems().setAll(seanceSearchService.getAvailableSearchStatuses());
        sessionSearchModeComboBox.setValue("Toutes les seances");

        resetEditMode();
        hideFeedback();
        loadSessionDashboard();
    }

    @FXML
    private void handleSearchSessions() {
        applySessionFilters();
    }

    @FXML
    private void handleClearSessionSearch() {
        sessionSearchField.clear();
        sessionSearchModeComboBox.setValue("Toutes les seances");
        applySessionFilters();
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
        resetEditMode();
        hideFeedback();
    }

    private void submitSession(int status, String successMessage) {
        hideFeedback();
        boolean editing = editingSessionId != null;

        try {
            Seance seance = buildSeance(status);
            if (editing) {
                seance.setId(editingSessionId);
                seance.setUpdatedAt(LocalDateTime.now());
                seanceCrudService.update(seance);
            } else {
                seance.setCreatedAt(LocalDateTime.now());
                seanceCrudService.add(seance);
            }

            clearSessionForm();
            resetEditMode();
            loadSessionDashboard();
            showFeedback(
                editing ? "La seance a ete modifiee avec succes." : successMessage,
                true
            );
        } catch (RuntimeException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    private Seance buildSeance(int status) {
        String subject = requireText(sessionSubjectField.getText(), "Renseignez la matiere de la seance.");
        String tutorName = requireText(tutorComboBox.getValue(), "Choisissez un tuteur.");
        LocalDateTime startAt = parseStartAt(sessionStartAtField.getText());
        int duration = parseBoundedInt(
            sessionDurationField.getText(),
            SeanceService.MIN_DURATION_MINUTES,
            SeanceService.MAX_DURATION_MINUTES,
            "La duree doit etre comprise entre " + SeanceService.MIN_DURATION_MINUTES + " et " + SeanceService.MAX_DURATION_MINUTES + " minutes."
        );
        int capacity = parseBoundedInt(
            sessionCapacityField.getText(),
            SeanceService.MIN_CAPACITY,
            SeanceService.MAX_CAPACITY,
            "La capacite doit etre comprise entre " + SeanceService.MIN_CAPACITY + " et " + SeanceService.MAX_CAPACITY + " participants."
        );
        String description = requireMinimumLengthText(
            sessionDescriptionArea.getText(),
            SeanceService.MIN_DESCRIPTION_LENGTH,
            "Ajoute une description de la seance.",
            "La description doit contenir au moins " + SeanceService.MIN_DESCRIPTION_LENGTH + " caracteres."
        );
        int tutorId = tutorDirectoryService.resolveTutorId(tutorName);

        if (tutorId <= 0) {
            throw new IllegalArgumentException("Le tuteur choisi est invalide pour le moment.");
        }

        sessionSubjectField.setText(subject);
        sessionStartAtField.setText(formatDateTime(startAt));
        sessionDurationField.setText(String.valueOf(duration));
        sessionCapacityField.setText(String.valueOf(capacity));
        sessionDescriptionArea.setText(description);
        tutorComboBox.setValue(tutorName);

        Seance seance = new Seance();
        seance.setMatiere(subject);
        seance.setStartAt(startAt);
        seance.setDurationMin(duration);
        seance.setMaxParticipants(capacity);
        seance.setStatus(status);
        seance.setDescription(description);
        seance.setTuteurId(tutorId);
        return seance;
    }

    private void loadSessionDashboard() {
        List<Seance> allSessions = seanceCrudService.getAll();
        long publishedCount = allSessions.stream().filter(seance -> seance.getStatus() == 1).count();
        long draftCount = allSessions.stream().filter(seance -> seance.getStatus() == 0).count();

        publishedSessionsCountLabel.setText(String.valueOf(publishedCount));
        draftSessionsCountLabel.setText(String.valueOf(draftCount));
        applySessionFilters();
    }

    private void applySessionFilters() {
        recentSessionsContainer.getChildren().clear();
        List<Seance> filteredSessions = seanceSearchService.search(
            sessionSearchField.getText(),
            sessionSearchModeComboBox.getValue(),
            8
        );

        if (filteredSessions.isEmpty() && seanceCrudService.getAll().isEmpty()) {
            Label emptyLabel = new Label("Aucune seance disponible pour le moment. La premiere publication apparaitra ici.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            return;
        }

        if (filteredSessions.isEmpty()) {
            Label emptyLabel = new Label("Aucune seance ne correspond aux filtres actuels.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            return;
        }

        filteredSessions.stream()
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

        HBox actionRow = new HBox(10.0);
        Label idChip = new Label("Seance #" + seance.getId());
        idChip.getStyleClass().add("workspace-chip");

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button editButton = new Button("Modifier");
        editButton.getStyleClass().addAll("action-button", "secondary");
        editButton.setOnAction(event -> startEditingSession(seance));

        Button deleteButton = new Button("Supprimer");
        deleteButton.getStyleClass().addAll("action-button", "danger");
        deleteButton.setOnAction(event -> confirmDeleteSession(seance));

        actionRow.getChildren().addAll(idChip, actionSpacer, editButton, deleteButton);

        card.getChildren().addAll(headerRow, metaLabel, descriptionLabel, actionRow);
        return card;
    }

    private void startEditingSession(Seance seance) {
        editingSessionId = seance.getId();
        sessionSubjectField.setText(seance.getMatiere());
        sessionStartAtField.setText(formatDateTime(seance.getStartAt()));
        sessionDurationField.setText(String.valueOf(seance.getDurationMin()));
        sessionCapacityField.setText(String.valueOf(seance.getMaxParticipants()));
        sessionDescriptionArea.setText(seance.getDescription() != null ? seance.getDescription() : "");

        String tutorValue = resolveTutorValueForEdit(seance.getTuteurId());
        tutorComboBox.setValue(tutorValue);

        sessionFormTitleLabel.setText("Modifier une seance");
        sessionFormModeChipLabel.setText("Modification");
        editingSessionLabel.setText(
            "Mode modification actif pour la seance #" + seance.getId()
                + ". Mets a jour les champs puis clique sur brouillon ou publier."
        );
        editingSessionLabel.setManaged(true);
        editingSessionLabel.setVisible(true);
        hideFeedback();
    }

    private void confirmDeleteSession(Seance seance) {
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Suppression de seance");
        confirmationAlert.setHeaderText("Confirmer la suppression");
        confirmationAlert.setContentText(
            "La seance \"" + seance.getMatiere() + "\" sera supprimee definitivement si aucune reservation ne la reference."
        );
        confirmationAlert.getButtonTypes().setAll(cancelButton, deleteButton);

        Optional<ButtonType> choice = confirmationAlert.showAndWait();
        if (choice.isPresent() && choice.get() == deleteButton) {
            deleteSession(seance);
        }
    }

    private void deleteSession(Seance seance) {
        hideFeedback();

        try {
            seanceCrudService.delete(seance);
            if (editingSessionId != null && editingSessionId == seance.getId()) {
                clearSessionForm();
                resetEditMode();
            }
            loadSessionDashboard();
            showFeedback("La seance a ete supprimee avec succes.", true);
        } catch (RuntimeException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    private LocalDateTime parseStartAt(String value) {
        String candidate = requireText(value, "Renseignez la date et l'heure de la seance.");
        for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
            try {
                LocalDateTime parsedValue = LocalDateTime.parse(candidate, formatter);
                if (!parsedValue.isAfter(LocalDateTime.now())) {
                    throw new IllegalArgumentException("La date de la seance doit etre dans le futur.");
                }
                return parsedValue;
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format.
            }
        }
        throw new IllegalArgumentException("Utilisez le format dd/MM/yyyy HH:mm pour la date.");
    }

    private int parseBoundedInt(String value, int min, int max, String errorMessage) {
        String candidate = requireText(value, errorMessage);
        try {
            int parsedValue = Integer.parseInt(candidate);
            if (parsedValue < min || parsedValue > max) {
                throw new NumberFormatException();
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String requireText(String value, String errorMessage) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalizedValue;
    }

    private String requireMinimumLengthText(String value, int minLength, String emptyMessage, String shortMessage) {
        String normalizedValue = requireText(value, emptyMessage);
        if (normalizedValue.length() < minLength) {
            throw new IllegalArgumentException(shortMessage);
        }
        return normalizedValue;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isEmpty() ? null : normalizedValue;
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
        } else {
            tutorComboBox.setValue(null);
        }
    }

    private void resetEditMode() {
        editingSessionId = null;
        sessionFormTitleLabel.setText("Publier une seance");
        sessionFormModeChipLabel.setText("Ajout direct");
        editingSessionLabel.setText("");
        editingSessionLabel.setManaged(false);
        editingSessionLabel.setVisible(false);
    }

    private String resolveTutorValueForEdit(int tutorId) {
        if (tutorId <= 0) {
            return "";
        }

        String tutorDisplayName = tutorDirectoryService.getTutorDisplayName(tutorId);
        if (tutorDisplayName.startsWith("Tuteur #")) {
            return String.valueOf(tutorId);
        }
        return tutorDisplayName;
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

