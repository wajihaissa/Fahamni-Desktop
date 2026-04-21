package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.Models.SalleEquipement;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.ReservationService;
import tn.esprit.fahamni.services.ReservationService.ReservationStats;
import tn.esprit.fahamni.services.ReservationService.StudentReservationItem;
import tn.esprit.fahamni.services.ReservationService.TutorReservationRequest;
import tn.esprit.fahamni.services.SalleEquipementService;
import tn.esprit.fahamni.services.SessionCreationContext;
import tn.esprit.fahamni.services.SeanceService;
import tn.esprit.fahamni.services.TutorRecommendationService.RecommendationObjective;
import tn.esprit.fahamni.services.TutorRecommendationService;
import tn.esprit.fahamni.services.TutorRecommendationService.RecommendedSession;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.geometry.Pos;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.sql.SQLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import javafx.util.StringConverter;
import javafx.scene.input.MouseEvent;

public class ReservationController {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String RATING_DIALOG_STYLESHEET = "/com/fahamni/styles/frontoffice-rating-dialog.css";
    private static final String SECTION_AVAILABLE_SESSIONS = "Seances disponibles";
    private static final String SECTION_ADD_SESSION = "Ajouter une seance";
    private static final String SECTION_MY_RESERVATIONS = "Mes reservations";
    private static final String SECTION_RESERVATION_REQUESTS = "Demandes de reservation";
    private static final String MODE_ONLINE_LABEL = "En ligne";
    private static final String MODE_ONSITE_LABEL = "Presentiel";
    private static final String STATUS_AVAILABLE = "disponible";
    private static final String STATUS_PENDING = "attente";
    private static final List<Integer> SESSION_PAGE_SIZE_OPTIONS = List.of(5, 10, 20);
    private static final int DEFAULT_SESSION_PAGE_SIZE = 5;
    private static final int STUDENT_REVIEW_MAX_LENGTH = 1000;
    private static final int STUDENT_REVIEW_SOFT_LIMIT = 850;
    private static final DateTimeFormatter DATE_PICKER_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> HOUR_OPTIONS = IntStream.range(0, 24)
        .mapToObj(value -> String.format("%02d", value))
        .toList();
    private static final List<String> MINUTE_OPTIONS = IntStream.range(0, 60)
        .mapToObj(value -> String.format("%02d", value))
        .toList();

    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();
    private final ReservationService reservationService = new ReservationService();
    private final AdminSalleService salleService = new AdminSalleService();
    private final AdminEquipementService equipementService = new AdminEquipementService();
    private final SalleEquipementService salleEquipementService = new SalleEquipementService();
    private final TutorRecommendationService tutorRecommendationService = new TutorRecommendationService();
    private final List<Salle> availableSalles = new ArrayList<>();
    private final List<Equipement> availableEquipements = new ArrayList<>();
    private final Map<Integer, List<SalleEquipement>> roomFixedEquipementsBySalleId = new LinkedHashMap<>();
    private final Map<Integer, EquipmentSelectionControls> equipmentSelectionControls = new LinkedHashMap<>();
    private final Map<Integer, RoomSelectionControls> roomSelectionControls = new LinkedHashMap<>();
    private final Map<Integer, String> roomScheduleConflicts = new LinkedHashMap<>();
    private Integer selectedSalleId;
    private Integer editingSessionId;
    private int currentSessionPage = 1;

    @FXML
    private Label publishedSessionsCountLabel;

    @FXML
    private Label draftSessionsCountLabel;

    @FXML
    private ComboBox<String> sectionMenuComboBox;

    @FXML
    private VBox sessionSearchPanel;

    @FXML
    private VBox sessionFormPanel;

    @FXML
    private VBox sessionListPanel;

    @FXML
    private TextField sessionSearchField;

    @FXML
    private ComboBox<String> sessionSearchModeComboBox;

    @FXML
    private Button recommendationBriefingButton;

    @FXML
    private TextField sessionSubjectField;

    @FXML
    private DatePicker sessionDatePicker;

    @FXML
    private ComboBox<String> sessionHourComboBox;

    @FXML
    private ComboBox<String> sessionMinuteComboBox;

    @FXML
    private TextField sessionDurationField;

    @FXML
    private TextField sessionCapacityField;

    @FXML
    private CheckBox sessionOnsiteCheckBox;

    @FXML
    private VBox sessionRoomChoicesContainer;

    @FXML
    private Label sessionRoomAvailabilityHintLabel;

    @FXML
    private HBox onsiteInfrastructureRow;

    @FXML
    private Separator sessionDescriptionDivider;

    @FXML
    private Label sessionModeDescriptionLabel;

    @FXML
    private Label sessionModeStateChipLabel;

    @FXML
    private Label sessionRoomStatusChipLabel;

    @FXML
    private Label sessionRoomPreviewTitleLabel;

    @FXML
    private Label sessionRoomPreviewSubtitleLabel;

    @FXML
    private Label sessionRoomPreviewDescriptionLabel;

    @FXML
    private FlowPane sessionRoomFactsContainer;

    @FXML
    private Label sessionRoomFixedEquipmentSummaryLabel;

    @FXML
    private VBox sessionRoomFixedEquipmentContainer;

    @FXML
    private VBox sessionEquipmentChoicesContainer;

    @FXML
    private VBox sessionEquipmentPreviewContainer;

    @FXML
    private Label sessionEquipmentSelectionSummaryLabel;

    @FXML
    private Label infrastructureHintLabel;

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
    private Label reservationActionFeedbackLabel;

    @FXML
    private VBox studentReservationsPanel;

    @FXML
    private Label studentReservationsSummaryLabel;

    @FXML
    private Label studentReservationsFeedbackLabel;

    @FXML
    private VBox studentReservationsContainer;

    @FXML
    private VBox reservationRequestsPanel;

    @FXML
    private Label tutorReservationRequestsSummaryLabel;

    @FXML
    private Label tutorReservationRequestsFeedbackLabel;

    @FXML
    private VBox tutorReservationRequestsContainer;

    @FXML
    private HBox sessionPaginationBar;

    @FXML
    private Label sessionPaginationSummaryLabel;

    @FXML
    private Button sessionPreviousPageButton;

    @FXML
    private Button sessionNextPageButton;

    @FXML
    private HBox sessionPageButtonsContainer;

    @FXML
    private ComboBox<Integer> sessionsPerPageComboBox;

    @FXML
    private void initialize() {
        configureDateTimeInputs();
        configureInfrastructureChoices();

        configureWorkspaceSections();
        sectionMenuComboBox.setValue(SECTION_AVAILABLE_SESSIONS);
        sessionSearchModeComboBox.getItems().setAll(seanceService.getAvailableSearchStatuses());
        sessionSearchModeComboBox.setValue("Toutes les seances");
        sessionsPerPageComboBox.getItems().setAll(SESSION_PAGE_SIZE_OPTIONS);
        sessionsPerPageComboBox.setValue(DEFAULT_SESSION_PAGE_SIZE);
        updateRecommendationBriefingAvailability();

        resetEditMode();
        hideFeedback();
        hideReservationActionFeedback();
        hideStudentReservationsFeedback();
        hideTutorReservationRequestsFeedback();
        loadSessionDashboard();
        showAvailableSessionsSection();
        applyPendingSessionPrefill();
    }

    @FXML
    private void handleChangeWorkspaceSection() {
        if (SECTION_ADD_SESSION.equals(sectionMenuComboBox.getValue())) {
            showAddSessionSection();
        } else if (SECTION_MY_RESERVATIONS.equals(sectionMenuComboBox.getValue())) {
            showStudentReservationsSection();
        } else if (SECTION_RESERVATION_REQUESTS.equals(sectionMenuComboBox.getValue())) {
            showReservationRequestsSection();
        } else {
            showAvailableSessionsSection();
        }
    }

    @FXML
    private void handleSearchSessions() {
        currentSessionPage = 1;
        applySessionFilters();
    }

    @FXML
    private void handleClearSessionSearch() {
        sessionSearchField.clear();
        sessionSearchModeComboBox.setValue("Toutes les seances");
        currentSessionPage = 1;
        applySessionFilters();
    }

    @FXML
    private void handleOpenRecommendationBriefing() {
        openRecommendationBriefing();
    }

    @FXML
    private void handlePreviousSessionsPage() {
        if (currentSessionPage > 1) {
            currentSessionPage--;
            applySessionFilters();
        }
    }

    @FXML
    private void handleNextSessionsPage() {
        currentSessionPage++;
        applySessionFilters();
    }

    @FXML
    private void handleChangeSessionsPageSize() {
        currentSessionPage = 1;
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
    private void handleSessionModeChange() {
        updateOnsiteOptionsVisibility();
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
                seanceService.update(seance);
            } else {
                seance.setCreatedAt(LocalDateTime.now());
                seanceService.add(seance);
            }

            clearSessionForm();
            resetEditMode();
            loadSessionDashboard();
            showSessionListFeedback(
                editing ? "La seance a ete modifiee avec succes." : successMessage,
                true
            );
        } catch (RuntimeException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    private Seance buildSeance(int status) {
        String subject = requireText(sessionSubjectField.getText(), "Renseignez la matiere de la seance.");
        LocalDateTime startAt = parseStartAt();
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
        int tutorId = getCurrentTutorId();

        if (tutorId <= 0) {
            throw new IllegalArgumentException("Connectez-vous avec le compte tuteur pour publier une seance.");
        }

        String mode = resolveSelectedMode();
        Integer salleId = null;
        Map<Integer, Integer> equipementQuantites = Map.of();
        if (Seance.MODE_ONSITE.equals(mode)) {
            Salle selectedSalle = resolveSelectedSalle();
            if (selectedSalle == null) {
                throw new IllegalArgumentException("Choisissez une salle disponible pour une seance presentielle.");
            }
            findRoomScheduleConflict(selectedSalle.getIdSalle(), startAt, duration)
                .ifPresent(conflict -> {
                    throw new IllegalArgumentException(buildRoomScheduleConflictReason(conflict));
                });
            if (capacity > selectedSalle.getCapacite()) {
                throw new IllegalArgumentException(
                    "La salle choisie accepte seulement " + selectedSalle.getCapacite() + " participants."
                );
            }
            salleId = selectedSalle.getIdSalle();
            equipementQuantites = getSelectedEquipmentQuantites();
        }

        sessionSubjectField.setText(subject);
        setStartAtSelection(startAt);
        sessionDurationField.setText(String.valueOf(duration));
        sessionCapacityField.setText(String.valueOf(capacity));
        sessionDescriptionArea.setText(description);
        sessionOnsiteCheckBox.setSelected(Seance.MODE_ONSITE.equals(mode));
        updateModePresentation();

        Seance seance = new Seance();
        seance.setMatiere(subject);
        seance.setStartAt(startAt);
        seance.setDurationMin(duration);
        seance.setMaxParticipants(capacity);
        seance.setStatus(status);
        seance.setDescription(description);
        seance.setTuteurId(tutorId);
        seance.setMode(mode);
        seance.setSalleId(salleId);
        seance.setEquipementQuantites(equipementQuantites);
        return seance;
    }

    private void configureInfrastructureChoices() {
        sessionOnsiteCheckBox.setSelected(false);
        configureRoomScheduleAvailability();
        loadInfrastructureChoices();
        updateOnsiteOptionsVisibility();
    }

    private void configureRoomScheduleAvailability() {
        sessionDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshRoomChoicesForSchedule());
        sessionHourComboBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshRoomChoicesForSchedule());
        sessionMinuteComboBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshRoomChoicesForSchedule());
        sessionDurationField.textProperty().addListener((obs, oldValue, newValue) -> refreshRoomChoicesForSchedule());
    }

    private void loadInfrastructureChoices() {
        try {
            availableSalles.clear();
            availableSalles.addAll(
                salleService.getAll().stream()
                    .filter(salle -> isAvailable(salle.getEtat()))
                    .toList()
            );
            roomFixedEquipementsBySalleId.clear();

            refreshRoomChoicesForSchedule();
        } catch (SQLException | IllegalStateException exception) {
            availableSalles.clear();
            availableEquipements.clear();
            roomFixedEquipementsBySalleId.clear();
            selectedSalleId = null;
            roomSelectionControls.clear();
            roomScheduleConflicts.clear();
            sessionRoomChoicesContainer.getChildren().clear();
            renderEquipmentChoices();
            updateRoomAvailabilityHint();
            infrastructureHintLabel.setText("Chargement des salles et materiels impossible: " + resolveMessage(exception));
            resetRoomPreview();
            updateEquipmentPreview();
        }
    }

    private void renderEquipmentChoices() {
        sessionEquipmentChoicesContainer.getChildren().clear();
        equipmentSelectionControls.clear();

        if (!Seance.MODE_ONSITE.equals(resolveSelectedMode())) {
            Label emptyLabel = new Label("Passez en presentiel pour ajouter du materiel complementaire.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            sessionEquipmentChoicesContainer.getChildren().add(emptyLabel);
            return;
        }

        Salle selectedSalle = resolveSelectedSalle();
        if (selectedSalle == null) {
            Label emptyLabel = new Label("Choisissez d'abord une salle pour voir le materiel complementaire disponible.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            sessionEquipmentChoicesContainer.getChildren().add(emptyLabel);
            return;
        }

        List<Equipement> complementaryEquipements = getComplementaryEquipementsForSalle(selectedSalle.getIdSalle());
        if (complementaryEquipements.isEmpty()) {
            Label emptyLabel = new Label("Aucun materiel complementaire n'est necessaire ou disponible pour cette salle.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            sessionEquipmentChoicesContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Equipement equipement : complementaryEquipements) {
            sessionEquipmentChoicesContainer.getChildren().add(createEquipmentChoiceCard(equipement));
        }
    }

    private void updateOnsiteOptionsVisibility() {
        boolean onsite = Seance.MODE_ONSITE.equals(resolveSelectedMode());
        setInfrastructureSectionVisible(onsiteInfrastructureRow, onsite);
        setSeparatorVisible(sessionDescriptionDivider, onsite);
        updateModePresentation();

        if (!onsite) {
            selectedSalleId = null;
            updateRoomSelectionStyles();
            clearEquipmentSelection();
            renderEquipmentChoices();
            resetRoomPreview();
            updateEquipmentPreview();
            updateInfrastructureHint();
        } else if (selectedSalleId == null && !availableSalles.isEmpty()) {
            selectFirstSelectableSalle();
            updateRoomPreview();
            updateEquipmentPreview();
            updateInfrastructureHint();
        } else {
            renderEquipmentChoices();
            updateRoomPreview();
            updateEquipmentPreview();
            updateInfrastructureHint();
        }
    }

    private String resolveSelectedMode() {
        return sessionOnsiteCheckBox.isSelected() ? Seance.MODE_ONSITE : Seance.MODE_ONLINE;
    }

    private Salle resolveSelectedSalle() {
        if (selectedSalleId == null) {
            return null;
        }
        return availableSalles.stream()
            .filter(salle -> salle.getIdSalle() == selectedSalleId)
            .findFirst()
            .orElse(null);
    }

    private Integer resolveSelectedSalleId() {
        return selectedSalleId;
    }

    private void refreshRoomChoicesForSchedule() {
        if (sessionRoomChoicesContainer == null) {
            return;
        }

        Integer preferredSalleId = resolveSelectedSalleId();
        updateRoomScheduleConflicts();
        if (preferredSalleId != null && roomScheduleConflicts.containsKey(preferredSalleId)) {
            selectedSalleId = null;
        }
        renderRoomChoices();
        updateRoomAvailabilityHint();
        refreshEquipmentChoicesForSchedule();
        if (preferredSalleId != null && roomScheduleConflicts.containsKey(preferredSalleId)) {
            showRoomUnavailablePreview(roomScheduleConflicts.get(preferredSalleId));
        } else {
            updateRoomPreview();
        }
        updateInfrastructureHint();
    }

    private void refreshEquipmentChoicesForSchedule() {
        Map<Integer, Integer> previousSelection = getSelectedEquipmentQuantites();

        try {
            availableEquipements.clear();
            Map<Integer, Integer> reservedQuantities = resolveReservedComplementaryQuantitiesForSelectedSchedule();

            for (Equipement equipement : equipementService.getAll()) {
                if (!isAvailable(equipement.getEtat())) {
                    continue;
                }

                int quantiteBase = salleEquipementService.getRemainingQuantiteForEquipement(equipement.getIdEquipement());
                if (quantiteBase <= 0) {
                    continue;
                }

                int quantiteRestante = Math.max(0, quantiteBase - reservedQuantities.getOrDefault(equipement.getIdEquipement(), 0));

                availableEquipements.add(new Equipement(
                    equipement.getIdEquipement(),
                    equipement.getNom(),
                    equipement.getTypeEquipement(),
                    quantiteRestante,
                    equipement.getEtat(),
                    equipement.getDescription()
                ));
            }
        } catch (SQLException | IllegalStateException exception) {
            availableEquipements.clear();
        }

        renderEquipmentChoices();
        reapplyEquipmentSelection(previousSelection);
    }

    private Map<Integer, Integer> resolveReservedComplementaryQuantitiesForSelectedSchedule() {
        LocalDateTime candidateStartAt = resolveTentativeStartAt();
        Integer candidateDuration = resolveTentativeDuration();
        if (candidateStartAt == null || candidateDuration == null || candidateDuration <= 0) {
            return Map.of();
        }

        return seanceService.getReservedComplementaryQuantites(
            candidateStartAt,
            candidateDuration,
            editingSessionId
        );
    }

    private void reapplyEquipmentSelection(Map<Integer, Integer> equipementQuantites) {
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            updateEquipmentPreview();
            return;
        }

        for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
            EquipmentSelectionControls controls = equipmentSelectionControls.get(entry.getKey());
            if (controls == null || !controls.selectable()) {
                continue;
            }

            controls.checkBox().setSelected(true);
            setEquipmentQuantity(controls, entry.getValue());
            updateEquipmentSelectionState(controls);
        }
        updateEquipmentPreview();
    }

    private void renderRoomChoices() {
        sessionRoomChoicesContainer.getChildren().clear();
        roomSelectionControls.clear();

        if (availableSalles.isEmpty()) {
            Label emptyLabel = new Label("Aucune salle disponible administrativement pour le moment.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            sessionRoomChoicesContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Salle salle : availableSalles) {
            sessionRoomChoicesContainer.getChildren().add(createRoomChoiceCard(salle));
        }
        updateRoomSelectionStyles();
    }

    private VBox createRoomChoiceCard(Salle salle) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-room-choice-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(136.0);
        card.setPrefHeight(136.0);

        String conflictReason = roomScheduleConflicts.get(salle.getIdSalle());
        boolean unavailableForSlot = conflictReason != null;

        HBox header = new HBox(10.0);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox titleBlock = new VBox(4.0);
        Label title = new Label(formatOptionalText(salle.getNom()));
        title.setWrapText(true);
        title.getStyleClass().add("reservation-room-choice-title");
        Label subtitle = new Label(formatLabel(salle.getTypeSalle()));
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("reservation-choice-subtitle");
        titleBlock.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label statusChip = new Label(unavailableForSlot ? "Occupee" : "Libre");
        statusChip.getStyleClass().setAll("status-chip", unavailableForSlot ? "unavailable" : "available");
        header.getChildren().addAll(titleBlock, spacer, statusChip);

        FlowPane facts = new FlowPane(8.0, 8.0);
        facts.getStyleClass().add("reservation-choice-facts");
        facts.getChildren().addAll(
            buildChoiceChip(formatOptionalText(salle.getBatiment())),
            buildChoiceChip(formatOptionalText(salle.getLocalisation())),
            buildChoiceChip(salle.getCapacite() + " places")
        );

        Region contentSpacer = new Region();
        VBox.setVgrow(contentSpacer, Priority.ALWAYS);

        card.getChildren().addAll(header, facts, contentSpacer);
        if (unavailableForSlot) {
            card.getStyleClass().add("unavailable");
            card.setOnMouseClicked(event -> {
                selectedSalleId = null;
                updateRoomSelectionStyles();
                showRoomUnavailablePreview(conflictReason);
            });
        } else {
            card.setOnMouseClicked(event -> selectSalle(salle));
        }

        RoomSelectionControls controls = new RoomSelectionControls(card, statusChip);
        roomSelectionControls.put(salle.getIdSalle(), controls);
        return card;
    }

    private void selectSalle(Salle salle) {
        if (salle == null || isSalleUnavailableForSelectedSchedule(salle)) {
            return;
        }
        boolean roomChanged = !Objects.equals(selectedSalleId, salle.getIdSalle());
        selectedSalleId = salle.getIdSalle();
        if (roomChanged) {
            renderEquipmentChoices();
        }
        updateRoomSelectionStyles();
        updateRoomPreview();
        updateEquipmentPreview();
        updateInfrastructureHint();
    }

    private void updateRoomSelectionStyles() {
        for (Map.Entry<Integer, RoomSelectionControls> entry : roomSelectionControls.entrySet()) {
            VBox card = entry.getValue().card();
            card.getStyleClass().remove("selected");
            if (selectedSalleId != null && selectedSalleId.equals(entry.getKey())) {
                card.getStyleClass().add("selected");
            }
        }
    }

    private void updateRoomScheduleConflicts() {
        roomScheduleConflicts.clear();

        LocalDateTime candidateStartAt = resolveTentativeStartAt();
        Integer candidateDuration = resolveTentativeDuration();
        if (candidateStartAt == null || candidateDuration == null || candidateDuration <= 0) {
            return;
        }

        List<Seance> existingSeances = seanceService.getAll();
        for (Salle salle : availableSalles) {
            findRoomScheduleConflict(salle.getIdSalle(), candidateStartAt, candidateDuration, existingSeances)
                .ifPresent(conflict -> roomScheduleConflicts.put(
                    salle.getIdSalle(),
                    buildRoomScheduleConflictReason(conflict)
                ));
        }
    }

    private Optional<Seance> findRoomScheduleConflict(int salleId, LocalDateTime candidateStartAt, int candidateDuration) {
        return findRoomScheduleConflict(salleId, candidateStartAt, candidateDuration, seanceService.getAll());
    }

    private Optional<Seance> findRoomScheduleConflict(int salleId, LocalDateTime candidateStartAt, int candidateDuration,
                                                     List<Seance> existingSeances) {
        if (salleId <= 0 || candidateStartAt == null || candidateDuration <= 0) {
            return Optional.empty();
        }

        LocalDateTime candidateEndAt = candidateStartAt.plusMinutes(candidateDuration);
        int currentEditingId = editingSessionId == null ? 0 : editingSessionId;

        return existingSeances.stream()
            .filter(existing -> existing.getId() != currentEditingId)
            .filter(Seance::isPresentiel)
            .filter(existing -> existing.getSalleId() != null && existing.getSalleId() == salleId)
            .filter(existing -> existing.getStatus() != 2)
            .filter(existing -> existing.getStartAt() != null)
            .filter(existing -> existing.getDurationMin() > 0)
            .filter(existing -> hasScheduleOverlap(
                candidateStartAt,
                candidateEndAt,
                existing.getStartAt(),
                existing.getStartAt().plusMinutes(existing.getDurationMin())
            ))
            .findFirst();
    }

    private boolean hasScheduleOverlap(LocalDateTime firstStartAt, LocalDateTime firstEndAt,
                                       LocalDateTime secondStartAt, LocalDateTime secondEndAt) {
        if (firstStartAt == null || firstEndAt == null || secondStartAt == null || secondEndAt == null) {
            return false;
        }
        return firstStartAt.isBefore(secondEndAt) && secondStartAt.isBefore(firstEndAt);
    }

    private String buildRoomScheduleConflictReason(Seance conflict) {
        return "Indisponible sur ce creneau: deja reservee pour \""
            + safeText(conflict.getMatiere())
            + "\" le "
            + formatDateTime(conflict.getStartAt())
            + ".";
    }

    private LocalDateTime resolveTentativeStartAt() {
        LocalDate selectedDate = sessionDatePicker.getValue();
        String selectedHour = sessionHourComboBox.getValue();
        String selectedMinute = sessionMinuteComboBox.getValue();
        if (selectedDate == null || selectedHour == null || selectedMinute == null) {
            return null;
        }

        try {
            return LocalDateTime.of(
                selectedDate,
                LocalTime.of(Integer.parseInt(selectedHour), Integer.parseInt(selectedMinute))
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Integer resolveTentativeDuration() {
        String durationText = sessionDurationField.getText();
        if (durationText == null || durationText.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(durationText.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void selectFirstSelectableSalle() {
        for (Salle salle : availableSalles) {
            if (!isSalleUnavailableForSelectedSchedule(salle)) {
                selectSalle(salle);
                return;
            }
        }
        selectedSalleId = null;
        updateRoomSelectionStyles();
    }

    private boolean isSalleUnavailableForSelectedSchedule(Salle salle) {
        return salle != null && roomScheduleConflicts.containsKey(salle.getIdSalle());
    }

    private void updateRoomAvailabilityHint() {
        if (sessionRoomAvailabilityHintLabel == null) {
            return;
        }

        if (availableSalles.isEmpty()) {
            updateOptionalHint(sessionRoomAvailabilityHintLabel, "Aucune salle disponible administrativement pour le moment.");
            return;
        }

        if (resolveTentativeStartAt() == null || resolveTentativeDuration() == null) {
            updateOptionalHint(sessionRoomAvailabilityHintLabel, null);
            return;
        }

        int blockedCount = roomScheduleConflicts.size();
        if (blockedCount == 0) {
            updateOptionalHint(sessionRoomAvailabilityHintLabel, "Toutes les salles affichees sont libres sur ce creneau.");
            return;
        }

        updateOptionalHint(
            sessionRoomAvailabilityHintLabel,
            blockedCount == 1
                ? "1 salle est grisee car elle est deja reservee sur ce creneau."
                : blockedCount + " salles sont grisees car elles sont deja reservees sur ce creneau."
        );
    }

    private Map<Integer, Integer> getSelectedEquipmentQuantites() {
        LinkedHashMap<Integer, Integer> selectedQuantities = new LinkedHashMap<>();
        for (Map.Entry<Integer, EquipmentSelectionControls> entry : equipmentSelectionControls.entrySet()) {
            EquipmentSelectionControls controls = entry.getValue();
            if (!controls.selectable() || !controls.checkBox().isSelected()) {
                continue;
            }
            int quantite = controls.quantitySpinner().getValue() == null ? 1 : controls.quantitySpinner().getValue();
            selectedQuantities.put(entry.getKey(), Math.max(1, quantite));
        }
        return selectedQuantities;
    }

    private void selectSalleById(Integer salleId) {
        selectedSalleId = null;
        if (salleId == null || salleId <= 0) {
            updateRoomSelectionStyles();
            updateRoomPreview();
            return;
        }

        for (Salle salle : availableSalles) {
            if (salle.getIdSalle() == salleId) {
                if (isSalleUnavailableForSelectedSchedule(salle)) {
                    updateRoomSelectionStyles();
                    updateRoomPreview();
                    return;
                }
                selectSalle(salle);
                return;
            }
        }
        updateRoomSelectionStyles();
        updateRoomPreview();
    }

    private void selectEquipementsByQuantites(Map<Integer, Integer> equipementQuantites) {
        clearEquipmentSelection();
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
            EquipmentSelectionControls controls = equipmentSelectionControls.get(entry.getKey());
            if (controls == null || !controls.selectable()) {
                continue;
            }
            controls.checkBox().setSelected(true);
            setEquipmentQuantity(controls, entry.getValue());
            updateEquipmentSelectionState(controls);
        }
        updateEquipmentPreview();
    }

    private void setEquipmentQuantity(EquipmentSelectionControls controls, Integer quantity) {
        if (controls == null) {
            return;
        }

        SpinnerValueFactory<Integer> valueFactory = controls.quantitySpinner().getValueFactory();
        if (!(valueFactory instanceof SpinnerValueFactory.IntegerSpinnerValueFactory integerFactory)) {
            valueFactory.setValue(quantity);
            return;
        }

        int requestedQuantity = quantity == null ? integerFactory.getMin() : quantity;
        int boundedQuantity = Math.max(integerFactory.getMin(), Math.min(integerFactory.getMax(), requestedQuantity));
        integerFactory.setValue(boundedQuantity);
    }

    private void clearEquipmentSelection() {
        equipmentSelectionControls.values().forEach(controls -> {
            controls.checkBox().setSelected(false);
            setEquipmentQuantity(controls, null);
            updateEquipmentSelectionState(controls);
        });
        updateEquipmentPreview();
    }

    private void applyPendingSessionPrefill() {
        SessionCreationContext.PendingSelection pendingSelection = SessionCreationContext.consumePendingSelection();
        if (pendingSelection == null) {
            return;
        }

        clearSessionForm();
        resetEditMode();
        sessionOnsiteCheckBox.setSelected(true);
        updateOnsiteOptionsVisibility();

        if (pendingSelection.salleId() != null) {
            selectSalleById(pendingSelection.salleId());
        }

        if (!pendingSelection.equipementQuantites().isEmpty()) {
            selectEquipementsByQuantites(pendingSelection.equipementQuantites());
        }

        showAddSessionSection();

        Salle selectedSalle = resolveSelectedSalle();
        if (pendingSelection.salleId() != null && selectedSalle == null) {
            showFeedback("La salle choisie n'est plus disponible. Choisissez-en une autre avant de publier.", false);
            return;
        }

        if (!pendingSelection.equipementQuantites().isEmpty() && getSelectedEquipmentQuantites().isEmpty()) {
            showFeedback("Le materiel choisi n'est plus disponible. Choisissez-en un autre avant de publier.", false);
            return;
        }

        if (selectedSalle != null) {
            showFeedback(
                "La salle \"" + formatOptionalText(selectedSalle.getNom()) + "\" est preselectionnee pour votre seance.",
                true
            );
            return;
        }

        if (!pendingSelection.equipementQuantites().isEmpty()) {
            showFeedback("Le materiel selectionne a ete pre-rempli pour votre seance.", true);
        }
    }

    private String formatEquipementChoice(Equipement equipement) {
        return formatOptionalText(equipement == null ? null : equipement.getNom());
    }

    private VBox createEquipmentChoiceCard(Equipement equipement) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-equipment-choice-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(136.0);
        card.setPrefHeight(136.0);
        boolean unavailableForSlot = equipement.getQuantiteDisponible() <= 0;

        CheckBox checkBox = new CheckBox();

        Spinner<Integer> quantitySpinner = new Spinner<>();
        quantitySpinner.setValueFactory(unavailableForSlot
            ? new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 0, 0)
            : new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Math.max(1, equipement.getQuantiteDisponible()), 1)
        );
        quantitySpinner.setEditable(true);
        quantitySpinner.setPrefWidth(60.0);
        quantitySpinner.getStyleClass().add("reservation-equipment-quantity-spinner");
        quantitySpinner.setDisable(true);

        EquipmentSelectionControls controls = new EquipmentSelectionControls(checkBox, quantitySpinner, card, !unavailableForSlot);
        equipmentSelectionControls.put(equipement.getIdEquipement(), controls);

        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            updateEquipmentSelectionState(controls);
            updateEquipmentPreview();
        });
        quantitySpinner.valueProperty().addListener((obs, oldValue, newValue) -> updateEquipmentPreview());

        Label quantityLabel = new Label("Quantite");
        quantityLabel.getStyleClass().add("reservation-equipment-quantity-label");

        VBox quantityBox = new VBox(4.0, quantityLabel, quantitySpinner);
        quantityBox.getStyleClass().add("reservation-quantity-box");
        quantityBox.setMinWidth(78.0);
        quantityBox.setPrefWidth(78.0);
        quantityBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);

        Label title = new Label(formatEquipementChoice(equipement));
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.getStyleClass().add("reservation-room-choice-title");

        Label subtitle = new Label(formatLabel(equipement.getTypeEquipement()));
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("reservation-choice-subtitle");

        Label availabilityChip = unavailableForSlot
            ? buildChoiceChip("0 dispo")
            : buildChoiceChipHighlight(equipement.getQuantiteDisponible() + " dispo");
        availabilityChip.setWrapText(false);

        HBox titleRow = new HBox(title);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        VBox infoBox = new VBox(6.0, titleRow, subtitle);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox header = new HBox(10.0, infoBox, quantityBox);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        FlowPane facts = new FlowPane(8.0, 8.0);
        facts.getStyleClass().add("reservation-choice-facts");
        Label typeChip = buildChoiceChip("Type " + formatLabel(equipement.getTypeEquipement()));
        typeChip.setWrapText(false);
        facts.getChildren().addAll(typeChip, availabilityChip);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(header, facts, spacer);
        if (unavailableForSlot) {
            card.getStyleClass().add("unavailable");
        }
        card.setOnMouseClicked(event -> handleEquipmentCardClick(event, controls, quantityBox));
        updateEquipmentSelectionState(controls);
        return card;
    }

    private void updateEquipmentSelectionState(EquipmentSelectionControls controls) {
        if (!controls.selectable()) {
            controls.quantitySpinner().setDisable(true);
            controls.card().getStyleClass().remove("selected");
            return;
        }

        boolean selected = controls.checkBox().isSelected();
        controls.quantitySpinner().setDisable(!selected);
        controls.card().getStyleClass().remove("selected");
        if (selected) {
            controls.card().getStyleClass().add("selected");
        }
    }

    private void updateModePresentation() {
        boolean onsite = Seance.MODE_ONSITE.equals(resolveSelectedMode());
        sessionModeStateChipLabel.setText(onsite ? MODE_ONSITE_LABEL : MODE_ONLINE_LABEL);
        sessionModeStateChipLabel.getStyleClass().setAll(
            "workspace-chip",
            onsite ? "reservation-mode-chip-onsite" : "workspace-chip-muted"
        );
        sessionModeDescriptionLabel.setText(
            onsite
                ? "Presentiel. Choisissez une salle, consultez son materiel fixe inclus puis ajoutez uniquement le complementaire."
                : "En ligne. Aucun espace physique n'est requis pour cette seance."
        );
    }

    private void updateRoomPreview() {
        if (!Seance.MODE_ONSITE.equals(resolveSelectedMode())) {
            resetRoomPreview();
            return;
        }

        Salle selectedSalle = resolveSelectedSalle();
        if (selectedSalle == null) {
            resetRoomPreview();
            return;
        }
        String conflictReason = roomScheduleConflicts.get(selectedSalle.getIdSalle());
        if (conflictReason != null) {
            showRoomUnavailablePreview(conflictReason);
            return;
        }

        sessionRoomStatusChipLabel.setText(formatLabel(selectedSalle.getEtat()));
        sessionRoomStatusChipLabel.getStyleClass().setAll("status-chip", resolveStatusStyle(selectedSalle.getEtat()));
        sessionRoomPreviewTitleLabel.setText(formatOptionalText(selectedSalle.getNom()));
        sessionRoomPreviewSubtitleLabel.setText(
            formatOptionalText(selectedSalle.getTypeSalle())
                + " | "
                + selectedSalle.getCapacite()
                + " places | "
                + formatOptionalText(selectedSalle.getLocalisation())
        );
        sessionRoomPreviewDescriptionLabel.setText(formatDescription(selectedSalle.getDescription()));

        sessionRoomFactsContainer.getChildren().setAll(
            buildInfrastructureFactCard("Batiment", formatOptionalText(selectedSalle.getBatiment())),
            buildInfrastructureFactCard("Etage", selectedSalle.getEtage() == null ? "Non renseigne" : String.valueOf(selectedSalle.getEtage())),
            buildInfrastructureFactCard("Disposition", formatOptionalText(selectedSalle.getTypeDisposition())),
            buildInfrastructureFactCard("Acces", selectedSalle.isAccesHandicape() ? "Handicap" : "Standard")
        );
        updateRoomFixedEquipmentPreview(selectedSalle.getIdSalle());
    }

    private void showRoomUnavailablePreview(String reason) {
        sessionRoomStatusChipLabel.setText("Indisponible");
        sessionRoomStatusChipLabel.getStyleClass().setAll("status-chip", "unavailable");
        sessionRoomPreviewTitleLabel.setText("Salle indisponible sur ce creneau");
        sessionRoomPreviewSubtitleLabel.setText(reason == null ? "Choisissez une autre salle ou modifiez le creneau." : reason);
        sessionRoomPreviewDescriptionLabel.setText("Choisissez une autre salle ou changez la date, l'heure ou la duree.");
        sessionRoomFactsContainer.getChildren().clear();
        resetRoomFixedEquipmentPreview();
    }

    private void resetRoomPreview() {
        sessionRoomStatusChipLabel.setText("En attente");
        sessionRoomStatusChipLabel.getStyleClass().setAll("status-chip", "pending");
        sessionRoomPreviewTitleLabel.setText("Choisissez une salle");
        sessionRoomPreviewSubtitleLabel.setText("La salle choisie apparaitra ici avec ses details principaux.");
        sessionRoomPreviewDescriptionLabel.setText("Aucune salle selectionnee.");
        sessionRoomFactsContainer.getChildren().clear();
        resetRoomFixedEquipmentPreview();
    }

    private void updateEquipmentPreview() {
        sessionEquipmentPreviewContainer.getChildren().clear();

        if (!Seance.MODE_ONSITE.equals(resolveSelectedMode())) {
            sessionEquipmentSelectionSummaryLabel.setText("0 ajout");
            sessionEquipmentPreviewContainer.getChildren().add(createEmptyEquipmentPreview(
                "Le materiel complementaire n'est propose que pour une seance presentielle."
            ));
            return;
        }

        Map<Integer, Integer> selectedEquipementQuantites = getSelectedEquipmentQuantites();
        int selectedCount = selectedEquipementQuantites.size();
        int totalUnits = selectedEquipementQuantites.values().stream().mapToInt(Integer::intValue).sum();
        sessionEquipmentSelectionSummaryLabel.setText(
            selectedCount == 0
                ? "0 ajout"
                : selectedCount + " ajout(s) | " + totalUnits + " unite(s)"
        );

        if (selectedEquipementQuantites.isEmpty()) {
            sessionEquipmentPreviewContainer.getChildren().add(createEmptyEquipmentPreview(
                "Aucun materiel complementaire selectionne. La seance peut utiliser uniquement l'equipement fixe de la salle."
            ));
            return;
        }

        selectedEquipementQuantites.entrySet().stream()
            .map(entry -> createEquipmentPreviewCard(findEquipementById(entry.getKey()), entry.getValue()))
            .filter(card -> card != null)
            .forEach(sessionEquipmentPreviewContainer.getChildren()::add);
    }

    private VBox createEmptyEquipmentPreview(String text) {
        VBox card = new VBox(6.0);
        card.getStyleClass().addAll("infrastructure-detail-preview", "reservation-equipment-preview-card");

        Label title = new Label("Apercu materiel complementaire");
        title.getStyleClass().add("infrastructure-detail-section-title");

        Label copy = new Label(text);
        copy.setWrapText(true);
        copy.getStyleClass().add("infrastructure-detail-section-copy");

        card.getChildren().addAll(title, copy);
        return card;
    }

    private VBox createEquipmentPreviewCard(Equipement equipement, int quantiteChoisie) {
        if (equipement == null) {
            return null;
        }

        VBox card = new VBox(8.0);
        card.getStyleClass().addAll("infrastructure-detail-preview", "reservation-equipment-preview-card");

        HBox header = new HBox(8.0);
        Label title = new Label(formatOptionalText(equipement.getNom()));
        title.getStyleClass().add("infrastructure-detail-section-title");
        title.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(formatLabel(equipement.getEtat()));
        status.getStyleClass().setAll("status-chip", resolveStatusStyle(equipement.getEtat()));
        header.getChildren().addAll(title, spacer, status);

        Label subtitle = new Label(
            formatLabel(equipement.getTypeEquipement())
                + " | "
                + quantiteChoisie
                + " unite(s) ajoutee(s) sur "
                + equipement.getQuantiteDisponible()
                + " disponible(s)"
        );
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("infrastructure-detail-section-copy");

        FlowPane facts = new FlowPane(10.0, 10.0);
        facts.getChildren().addAll(
            buildInfrastructureFactCard("Type", formatLabel(equipement.getTypeEquipement())),
            buildInfrastructureFactCard("Quantite", quantiteChoisie + " unite(s)"),
            buildInfrastructureFactCard("Stock", equipement.getQuantiteDisponible() + " unite(s)")
        );

        Label description = new Label(formatDescription(equipement.getDescription()));
        description.setWrapText(true);
        description.getStyleClass().add("infrastructure-detail-section-copy");

        card.getChildren().addAll(header, subtitle, facts, description);
        return card;
    }

    private void updateRoomFixedEquipmentPreview(int salleId) {
        if (sessionRoomFixedEquipmentContainer == null || sessionRoomFixedEquipmentSummaryLabel == null) {
            return;
        }

        sessionRoomFixedEquipmentContainer.getChildren().clear();
        List<SalleEquipement> fixedEquipements = getRoomFixedEquipements(salleId);
        if (fixedEquipements.isEmpty()) {
            sessionRoomFixedEquipmentSummaryLabel.setText("Aucun fixe");
            sessionRoomFixedEquipmentContainer.getChildren().add(createEmptyFixedRoomEquipmentPreview(
                "Cette salle n'a pas de materiel fixe enregistre. Vous pouvez ajouter uniquement du materiel complementaire si besoin."
            ));
            return;
        }

        int totalUnits = fixedEquipements.stream().mapToInt(SalleEquipement::getQuantite).sum();
        sessionRoomFixedEquipmentSummaryLabel.setText(fixedEquipements.size() + " type(s) | " + totalUnits + " unite(s)");
        fixedEquipements.stream()
            .map(this::createFixedRoomEquipmentPreviewCard)
            .forEach(sessionRoomFixedEquipmentContainer.getChildren()::add);
    }

    private void resetRoomFixedEquipmentPreview() {
        if (sessionRoomFixedEquipmentSummaryLabel != null) {
            sessionRoomFixedEquipmentSummaryLabel.setText("Aucun fixe");
        }
        if (sessionRoomFixedEquipmentContainer != null) {
            sessionRoomFixedEquipmentContainer.getChildren().clear();
        }
    }

    private VBox createEmptyFixedRoomEquipmentPreview(String text) {
        VBox card = new VBox(6.0);
        card.getStyleClass().addAll("infrastructure-detail-preview", "reservation-equipment-preview-card");

        Label title = new Label("Equipements inclus");
        title.getStyleClass().add("infrastructure-detail-section-title");

        Label copy = new Label(text);
        copy.setWrapText(true);
        copy.getStyleClass().add("infrastructure-detail-section-copy");

        card.getChildren().addAll(title, copy);
        return card;
    }

    private VBox createFixedRoomEquipmentPreviewCard(SalleEquipement equipement) {
        VBox card = new VBox(8.0);
        card.getStyleClass().addAll("infrastructure-detail-preview", "reservation-equipment-preview-card");

        HBox header = new HBox(8.0);
        Label title = new Label(formatOptionalText(equipement.getNomEquipement()));
        title.getStyleClass().add("infrastructure-detail-section-title");
        title.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(formatLabel(equipement.getEtatEquipement()));
        status.getStyleClass().setAll("status-chip", resolveStatusStyle(equipement.getEtatEquipement()));
        header.getChildren().addAll(title, spacer, status);

        Label subtitle = new Label(
            formatOptionalText(equipement.getTypeEquipement())
                + " | "
                + equipement.getQuantite()
                + " unite(s) fixes"
        );
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("infrastructure-detail-section-copy");

        Label description = new Label(formatDescription(equipement.getDescriptionEquipement()));
        description.setWrapText(true);
        description.getStyleClass().add("infrastructure-detail-section-copy");

        card.getChildren().addAll(header, subtitle, description);
        return card;
    }

    private VBox buildInfrastructureFactCard(String label, String value) {
        VBox card = new VBox(5.0);
        card.getStyleClass().add("infrastructure-detail-fact-card");
        card.setPrefWidth(150.0);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("infrastructure-detail-fact-label");

        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("infrastructure-detail-fact-value");

        card.getChildren().addAll(labelNode, valueNode);
        return card;
    }

    private void handleEquipmentCardClick(MouseEvent event, EquipmentSelectionControls controls, VBox quantityBox) {
        if (event == null || controls == null || !controls.selectable()) {
            return;
        }
        if (isEventInsideNode(event, controls.checkBox()) || isEventInsideNode(event, quantityBox)) {
            return;
        }

        controls.checkBox().setSelected(!controls.checkBox().isSelected());
    }

    private boolean isEventInsideNode(MouseEvent event, Node container) {
        if (event == null || container == null) {
            return false;
        }

        Object target = event.getTarget();
        if (!(target instanceof Node current)) {
            return false;
        }

        while (current != null) {
            if (current == container) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void updateOptionalHint(Label label, String text) {
        if (label == null) {
            return;
        }

        boolean visible = text != null && !text.isBlank();
        label.setText(visible ? text : "");
        label.setManaged(visible);
        label.setVisible(visible);
    }

    private Label buildChoiceChip(String text) {
        Label chip = new Label(text);
        chip.setWrapText(true);
        chip.getStyleClass().add("reservation-choice-chip");
        return chip;
    }

    private Label buildChoiceChipHighlight(String text) {
        Label chip = buildChoiceChip(text);
        chip.getStyleClass().add("reservation-choice-chip-highlight");
        return chip;
    }

    private Equipement findEquipementById(Integer equipementId) {
        if (equipementId == null || equipementId <= 0) {
            return null;
        }

        return availableEquipements.stream()
            .filter(equipement -> equipement.getIdEquipement() == equipementId)
            .findFirst()
            .orElse(null);
    }

    private List<Equipement> getComplementaryEquipementsForSalle(int salleId) {
        return new ArrayList<>(availableEquipements);
    }

    private List<SalleEquipement> getRoomFixedEquipements(Integer salleId) {
        if (salleId == null || salleId <= 0) {
            return List.of();
        }

        List<SalleEquipement> cached = roomFixedEquipementsBySalleId.get(salleId);
        if (cached != null) {
            return cached;
        }

        try {
            List<SalleEquipement> loaded = salleEquipementService.getEquipementsBySalleId(salleId);
            roomFixedEquipementsBySalleId.put(salleId, loaded);
            return loaded;
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            roomFixedEquipementsBySalleId.put(salleId, List.of());
            return List.of();
        }
    }

    private void updateInfrastructureHint() {
        if (!Seance.MODE_ONSITE.equals(resolveSelectedMode())) {
            infrastructureHintLabel.setText("Passez en presentiel pour voir une salle, son equipement fixe et les ajouts possibles.");
            return;
        }

        Salle selectedSalle = resolveSelectedSalle();
        if (selectedSalle == null) {
            infrastructureHintLabel.setText("Choisissez une salle pour distinguer le materiel deja inclus de celui a ajouter pour la seance.");
            return;
        }

        List<SalleEquipement> fixedEquipements = getRoomFixedEquipements(selectedSalle.getIdSalle());
        long complementaryCount = getComplementaryEquipementsForSalle(selectedSalle.getIdSalle()).stream()
            .filter(equipement -> equipement.getQuantiteDisponible() > 0)
            .count();
        infrastructureHintLabel.setText(
            formatOptionalText(selectedSalle.getNom())
                + " inclut "
                + fixedEquipements.size()
                + " type(s) de materiel fixe. "
                + complementaryCount
                + " type(s) de materiel complementaire restent ajoutables pour cette seance."
        );
    }

    private void loadSessionDashboard() {
        List<Seance> allSessions = seanceService.getAll();
        long publishedCount = allSessions.stream().filter(seance -> seance.getStatus() == 1).count();
        long draftCount = allSessions.stream().filter(seance -> seance.getStatus() == 0).count();

        publishedSessionsCountLabel.setText(String.valueOf(publishedCount));
        draftSessionsCountLabel.setText(String.valueOf(draftCount));
        applySessionFilters();
    }

    private void applySessionFilters() {
        recentSessionsContainer.getChildren().clear();
        List<Seance> filteredSessions = seanceService.search(
            sessionSearchField.getText(),
            sessionSearchModeComboBox.getValue(),
            0
        );
        boolean hasSessionsInDatabase = !seanceService.getAll().isEmpty();

        if (filteredSessions.isEmpty() && !hasSessionsInDatabase) {
            Label emptyLabel = new Label("Aucune seance en base de donnees n'est disponible pour le moment.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            hideSessionPagination();
            return;
        }

        if (filteredSessions.isEmpty()) {
            Label emptyLabel = new Label("Aucune seance en base ne correspond aux filtres actuels.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            hideSessionPagination();
            return;
        }

        int totalItems = filteredSessions.size();
        int pageSize = getSelectedSessionPageSize();
        int totalPages = calculateTotalPages(totalItems, pageSize);
        currentSessionPage = clamp(currentSessionPage, 1, totalPages);

        int fromIndex = (currentSessionPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        filteredSessions.subList(fromIndex, toIndex).stream()
            .map(this::buildRecentSessionCard)
            .forEach(recentSessionsContainer.getChildren()::add);

        updateSessionPagination(totalItems, fromIndex + 1, toIndex, totalPages);
    }

    private VBox buildRecentSessionCard(Seance seance) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-form-shell");
        ReservationStats reservationStats = reservationService.getStatsBySeanceId(seance.getId());

        HBox headerRow = new HBox(10.0);
        Label titleLabel = new Label(seance.getMatiere());
        titleLabel.getStyleClass().add("subsection-title");
        headerRow.getChildren().add(titleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusChip = new Label(mapStatusLabel(seance.getStatus()));
        statusChip.getStyleClass().addAll("reservation-status", mapStatusStyle(seance.getStatus()));
        headerRow.getChildren().addAll(spacer, statusChip);

        Label metaLabel = new Label(
            "Tuteur: " + tutorDirectoryService.getTutorDisplayName(seance.getTuteurId())
                + " | " + formatDateTime(seance.getStartAt())
                + " | " + seance.getDurationMin() + " min"
                + " | " + seance.getMaxParticipants() + " places"
                + " | " + buildInfrastructureSummary(seance)
                + " | " + formatReservationCount(reservationStats.total())
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

        Button detailsButton = new Button("Voir detail");
        detailsButton.getStyleClass().add("backoffice-secondary-button");
        detailsButton.setOnAction(event -> showSessionDetails(seance, reservationStats));

        Button reserveButton = buildReserveButton(seance, reservationStats);

        Button editButton = new Button("Modifier");
        editButton.getStyleClass().add("backoffice-edit-button");
        editButton.setOnAction(event -> startEditingSession(seance));

        Button deleteButton = new Button("Supprimer");
        deleteButton.getStyleClass().add("backoffice-danger-button");
        deleteButton.setOnAction(event -> confirmDeleteSession(seance));

        actionRow.getChildren().addAll(idChip, actionSpacer);
        if (canReserveAsParticipant(seance)) {
            actionRow.getChildren().add(reserveButton);
        }
        actionRow.getChildren().add(detailsButton);
        if (canManageSession(seance)) {
            actionRow.getChildren().addAll(editButton, deleteButton);
        }

        card.getChildren().addAll(headerRow, metaLabel, descriptionLabel);
        card.getChildren().add(actionRow);
        return card;
    }

    private void openRecommendationBriefing() {
        if (!UserSession.isCurrentStudent()) {
            showAvailableSessionsSection();
            showReservationActionFeedback(
                "Connectez-vous avec un compte etudiant pour ouvrir l'analyse personnalisee.",
                false
            );
            return;
        }

        List<Seance> candidateSessions = buildRecommendationCandidateSessions();
        if (candidateSessions.isEmpty()) {
            showAvailableSessionsSection();
            showReservationActionFeedback(
                "Aucune recommandation exploitable n'est disponible avec les filtres actuels.",
                false
            );
            return;
        }

        Dialog<ButtonType> briefingDialog = new Dialog<>();
        DialogPane dialogPane = briefingDialog.getDialogPane();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);

        briefingDialog.setTitle("Analyse personnalisee");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setPrefWidth(900);
        dialogPane.setPrefHeight(760);
        dialogPane.setContent(
            buildRecommendationBriefingContent(candidateSessions, briefingDialog)
        );
        applyCurrentTheme(dialogPane);
        briefingDialog.showAndWait();
    }

    private List<Seance> buildRecommendationCandidateSessions() {
        return seanceService.search(
            sessionSearchField.getText(),
            sessionSearchModeComboBox.getValue(),
            0
        ).stream()
            .filter(Objects::nonNull)
            .filter(seance -> seance.getStatus() == 1)
            .filter(seance -> seance.getStartAt() != null && !seance.getStartAt().isBefore(LocalDateTime.now()))
            .toList();
    }

    private List<RecommendedSession> buildRecommendationBriefingRecommendations(List<Seance> candidateSessions,
                                                                                RecommendationObjective objective) {
        List<RecommendedSession> ranked = tutorRecommendationService.rankSessionsForStudent(
            getCurrentReservationParticipantId(),
            candidateSessions,
            objective
        );

        List<RecommendedSession> selectedRecommendations = new ArrayList<>();

        for (RecommendedSession recommendation : ranked) {
            if (recommendation == null || recommendation.seance() == null || recommendation.score() < 55.0) {
                continue;
            }
            selectedRecommendations.add(recommendation);
            if (selectedRecommendations.size() >= 5) {
                return selectedRecommendations;
            }
        }

        for (RecommendedSession recommendation : ranked) {
            if (recommendation == null || recommendation.seance() == null || recommendation.score() < 40.0) {
                continue;
            }
            boolean alreadySelected = selectedRecommendations.stream()
                .anyMatch(selected -> selected.seance().getId() == recommendation.seance().getId());
            if (alreadySelected) {
                continue;
            }
            selectedRecommendations.add(recommendation);
            if (selectedRecommendations.size() >= 5) {
                return selectedRecommendations;
            }
        }

        for (RecommendedSession recommendation : ranked) {
            if (recommendation == null || recommendation.seance() == null) {
                continue;
            }
            boolean alreadySelected = selectedRecommendations.stream()
                .anyMatch(selected -> selected.seance().getId() == recommendation.seance().getId());
            if (alreadySelected) {
                continue;
            }
            selectedRecommendations.add(recommendation);
            if (selectedRecommendations.size() >= 5) {
                break;
            }
        }

        return selectedRecommendations;
    }

    private ScrollPane buildRecommendationBriefingContent(List<Seance> candidateSessions,
                                                          Dialog<ButtonType> briefingDialog) {
        VBox root = new VBox(12.0);
        Label introLabel = new Label(
            "Choisissez une orientation de recommandation. Les seances ci-dessous sont classees selon "
                + "votre historique accepte, la disponibilite, le format prefere et l'experience du tuteur."
        );
        introLabel.setWrapText(true);
        introLabel.getStyleClass().add("reservation-section-copy");

        FlowPane objectiveBar = new FlowPane(10.0, 10.0);
        Label summaryLabel = new Label();
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("reservation-section-copy");

        VBox recommendationList = new VBox(12.0);
        Map<RecommendationObjective, Button> objectiveButtons = new LinkedHashMap<>();

        for (RecommendationObjective objective : RecommendationObjective.values()) {
            Button objectiveButton = new Button(objective.label());
            objectiveButton.getStyleClass().add("backoffice-secondary-button");
            objectiveButton.setOnAction(event ->
                refreshRecommendationBriefing(
                    candidateSessions,
                    objective,
                    objectiveButtons,
                    recommendationList,
                    summaryLabel,
                    briefingDialog
                )
            );
            objectiveButtons.put(objective, objectiveButton);
            objectiveBar.getChildren().add(objectiveButton);
        }

        refreshRecommendationBriefing(
            candidateSessions,
            RecommendationObjective.GENERAL,
            objectiveButtons,
            recommendationList,
            summaryLabel,
            briefingDialog
        );

        root.getChildren().addAll(introLabel, objectiveBar, summaryLabel, recommendationList);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(620);
        scrollPane.getStyleClass().add("reservation-choice-scroll");
        return scrollPane;
    }

    private void refreshRecommendationBriefing(List<Seance> candidateSessions,
                                               RecommendationObjective objective,
                                               Map<RecommendationObjective, Button> objectiveButtons,
                                               VBox recommendationList,
                                               Label summaryLabel,
                                               Dialog<ButtonType> briefingDialog) {
        updateRecommendationObjectiveButtons(objectiveButtons, objective);
        List<RecommendedSession> recommendations = buildRecommendationBriefingRecommendations(candidateSessions, objective);
        summaryLabel.setText(buildRecommendationObjectiveSummary(objective, recommendations));
        renderRecommendationBriefingCards(recommendationList, recommendations, briefingDialog);
    }

    private void updateRecommendationObjectiveButtons(Map<RecommendationObjective, Button> objectiveButtons,
                                                      RecommendationObjective activeObjective) {
        objectiveButtons.forEach((objective, button) -> {
            button.getStyleClass().removeAll("backoffice-primary-button", "backoffice-secondary-button");
            button.getStyleClass().add(
                objective == activeObjective ? "backoffice-primary-button" : "backoffice-secondary-button"
            );
        });
    }

    private String buildRecommendationObjectiveSummary(RecommendationObjective objective,
                                                       List<RecommendedSession> recommendations) {
        if (recommendations.isEmpty()) {
            return objective.description() + " Aucune seance n'est exploitable avec les filtres actuels.";
        }

        RecommendedSession leadRecommendation = recommendations.get(0);
        StringBuilder summary = new StringBuilder(objective.description())
            .append(" Meilleure proposition actuelle: ")
            .append(safeText(leadRecommendation.seance().getMatiere()))
            .append(" avec ")
            .append(Math.round(leadRecommendation.score()))
            .append("/100");

        if (leadRecommendation.fallback()) {
            summary.append(". Aucun score fort n'a domine, la meilleure option disponible a ete retenue.");
        } else {
            summary.append(" en ").append(leadRecommendation.confidenceLabel().toLowerCase()).append(".");
        }
        return summary.toString();
    }

    private void renderRecommendationBriefingCards(VBox recommendationList,
                                                   List<RecommendedSession> recommendations,
                                                   Dialog<ButtonType> briefingDialog) {
        recommendationList.getChildren().clear();
        if (recommendations.isEmpty()) {
            Label emptyLabel = new Label(
                "Aucune seance recommandee n'est disponible pour cette orientation avec les filtres actuels."
            );
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recommendationList.getChildren().add(emptyLabel);
            return;
        }

        recommendationList.getChildren().addAll(
            recommendations.stream()
                .map(recommendation -> buildRecommendationBriefingCard(recommendation, briefingDialog))
                .toList()
        );
    }

    private VBox buildRecommendationBriefingCard(RecommendedSession recommendation,
                                                 Dialog<ButtonType> briefingDialog) {
        Seance seance = recommendation.seance();
        ReservationStats reservationStats = reservationService.getStatsBySeanceId(seance.getId());
        int availableSeats = Math.max(0, seance.getMaxParticipants() - reservationStats.total());

        VBox card = new VBox(12.0);
        card.getStyleClass().add("reservation-form-shell");

        HBox titleRow = new HBox(10.0);
        Label titleLabel = new Label(safeText(seance.getMatiere()));
        titleLabel.getStyleClass().add("subsection-title");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(
            titleLabel,
            titleSpacer,
            recommendation.highlighted()
                ? buildChoiceChipHighlight("Top recommande")
                : buildChoiceChip("Rang #" + recommendation.rank()),
            buildChoiceChipHighlight(Math.round(recommendation.score()) + "/100"),
            buildRecommendationConfidenceChip(recommendation)
        );

        Label metaLabel = new Label(
            "Tuteur: " + tutorDirectoryService.getTutorDisplayName(seance.getTuteurId())
                + " | " + formatDateTimeOrPlaceholder(seance.getStartAt())
                + " | " + seance.getDurationMin() + " min"
                + " | " + mapModeLabel(seance.getMode())
                + " | " + formatAvailableSeats(availableSeats)
                + " | " + formatReservationCount(reservationStats.total())
        );
        metaLabel.setWrapText(true);
        metaLabel.getStyleClass().add("reservation-section-copy");

        FlowPane signalRow = new FlowPane(10.0, 10.0);
        signalRow.getChildren().add(buildChoiceChip(recommendation.objective().chipLabel()));
        signalRow.getChildren().add(buildChoiceChip(recommendation.tutorTierLabel()));
        signalRow.getChildren().add(buildChoiceChip(buildRecommendationStrengthLabel(recommendation.score())));
        if (recommendation.fallback()) {
            signalRow.getChildren().add(buildChoiceChip("Selection de secours"));
        }
        recommendation.signals().stream()
            .limit(4)
            .map(this::buildChoiceChip)
            .forEach(signalRow.getChildren()::add);

        Label reasonKicker = new Label("Motif principal");
        reasonKicker.getStyleClass().add("reservation-infrastructure-kicker");

        Label reasonLabel = new Label(recommendation.reason());
        reasonLabel.setWrapText(true);
        reasonLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);

        Button detailButton = new Button("Voir detail");
        detailButton.getStyleClass().add("backoffice-secondary-button");
        detailButton.setOnAction(event -> showSessionDetails(seance, reservationStats));

        Button catalogButton = new Button("Afficher dans le catalogue");
        catalogButton.getStyleClass().add("backoffice-secondary-button");
        catalogButton.setOnAction(event -> focusRecommendedSessionInCatalogue(recommendation, briefingDialog));

        Button reserveButton = buildRecommendationReserveButton(seance, reservationStats, briefingDialog);

        actionRow.getChildren().addAll(detailButton, catalogButton);
        if (canReserveAsParticipant(seance)) {
            actionRow.getChildren().add(reserveButton);
        }

        card.getChildren().addAll(titleRow, metaLabel, signalRow, reasonKicker, reasonLabel, actionRow);
        return card;
    }

    private String buildRecommendationStrengthLabel(double score) {
        if (score >= 85.0) {
            return "Priorite forte";
        }
        if (score >= 70.0) {
            return "Pertinence elevee";
        }
        if (score >= 55.0) {
            return "Pertinence solide";
        }
        return "Option complementaire";
    }

    private Label buildRecommendationConfidenceChip(RecommendedSession recommendation) {
        if (recommendation.confidenceProgress() >= 0.58) {
            return buildChoiceChipHighlight(recommendation.confidenceLabel());
        }
        return buildChoiceChip(recommendation.confidenceLabel());
    }

    private void focusRecommendedSessionInCatalogue(RecommendedSession recommendation, Dialog<ButtonType> briefingDialog) {
        if (recommendation == null || recommendation.seance() == null) {
            return;
        }

        briefingDialog.close();
        showAvailableSessionsSection();
        sessionSearchField.setText(safeText(recommendation.seance().getMatiere()));
        sessionSearchModeComboBox.setValue("Toutes les seances");
        currentSessionPage = 1;
        applySessionFilters();
        showReservationActionFeedback(
            "Le catalogue est centre sur la recommandation \"" + safeText(recommendation.seance().getMatiere()) + "\".",
            true
        );
    }

    private Button buildRecommendationReserveButton(Seance seance,
                                                    ReservationStats reservationStats,
                                                    Dialog<ButtonType> briefingDialog) {
        Button reserveButton = new Button("Reserver cette seance");
        reserveButton.getStyleClass().add("backoffice-primary-button");

        if (!canReserveAsParticipant(seance)) {
            reserveButton.setText(UserSession.isCurrentTutor() ? "Votre seance" : "Compte requis");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (seance.getStatus() != 1) {
            reserveButton.setText("Indisponible");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (reservationStats.total() >= seance.getMaxParticipants()) {
            reserveButton.setText("Complet");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        int participantId = getCurrentReservationParticipantId();
        if (reservationService.hasActiveReservation(seance.getId(), participantId)) {
            reserveButton.setText("Deja reserve");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        reserveButton.setOnAction(event -> {
            briefingDialog.close();
            reserveSession(seance);
        });
        return reserveButton;
    }

    private boolean canManageSession(Seance seance) {
        return UserSession.isCurrentTutor()
            && seance != null
            && seance.getTuteurId() == getCurrentTutorId();
    }

    private boolean canReserveAsParticipant(Seance seance) {
        if (UserSession.isCurrentStudent()) {
            return true;
        }
        return UserSession.isCurrentTutor()
            && seance != null
            && !canManageSession(seance);
    }

    private int getCurrentReservationParticipantId() {
        return UserSession.getCurrentUserId();
    }

    private int getCurrentTutorId() {
        return UserSession.isCurrentTutor() ? UserSession.getCurrentUserId() : 0;
    }

    private Button buildReserveButton(Seance seance, ReservationStats reservationStats) {
        Button reserveButton = new Button("Reserver");
        reserveButton.getStyleClass().add("backoffice-primary-button");

        if (!canReserveAsParticipant(seance)) {
            reserveButton.setText(UserSession.isCurrentTutor() ? "Votre seance" : "Compte requis");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (seance.getStatus() != 1) {
            reserveButton.setText("Indisponible");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (reservationStats.total() >= seance.getMaxParticipants()) {
            reserveButton.setText("Complet");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        int participantId = getCurrentReservationParticipantId();
        if (reservationService.hasActiveReservation(seance.getId(), participantId)) {
            reserveButton.setText("Deja reserve");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        reserveButton.setOnAction(event -> reserveSession(seance));
        return reserveButton;
    }

    private void reserveSession(Seance seance) {
        OperationResult result = reservationService.reserveSeance(seance, getCurrentReservationParticipantId());
        loadSessionDashboard();
        showAvailableSessionsSection();
        showReservationActionFeedback(result.getMessage(), result.isSuccess());
    }

    private void loadStudentReservations() {
        studentReservationsContainer.getChildren().clear();

        List<StudentReservationItem> reservations = reservationService.getStudentReservations(
            getCurrentReservationParticipantId()
        );
        long pendingCount = reservations.stream().filter(StudentReservationItem::isPending).count();
        long acceptedCount = reservations.stream().filter(StudentReservationItem::isAccepted).count();
        long refusedCount = reservations.stream().filter(StudentReservationItem::isRefused).count();
        studentReservationsSummaryLabel.setText(
            formatStudentReservationsSummary(reservations.size(), pendingCount, acceptedCount, refusedCount)
        );

        if (reservations.isEmpty()) {
            Label emptyLabel = new Label("Aucune reservation active pour le moment. Reserve une seance depuis le catalogue.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            studentReservationsContainer.getChildren().add(emptyLabel);
            return;
        }

        reservations.stream()
            .map(this::buildStudentReservationCard)
            .forEach(studentReservationsContainer.getChildren()::add);
    }

    private VBox buildStudentReservationCard(StudentReservationItem reservation) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-form-shell");

        HBox headerRow = new HBox(10.0);
        Label titleLabel = new Label(safeText(reservation.seanceTitle()));
        titleLabel.getStyleClass().add("subsection-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusChip = new Label(mapReservationRequestStatusLabel(reservation.status()));
        statusChip.getStyleClass().addAll("reservation-status", mapReservationRequestStatusStyle(reservation.status()));
        headerRow.getChildren().addAll(titleLabel, spacer, statusChip);

        Label sessionLabel = new Label(
            "Tuteur: " + tutorDirectoryService.getTutorDisplayName(reservation.tutorId())
                + " | Seance: " + formatDateTimeOrPlaceholder(reservation.seanceStartAt())
                + " | " + reservation.durationMin() + " min"
                + " | " + reservation.maxParticipants() + " places"
        );
        sessionLabel.setWrapText(true);
        sessionLabel.getStyleClass().add("reservation-section-copy");

        Label requestLabel = new Label(
            "Reservation envoyee: " + formatDateTimeOrPlaceholder(reservation.reservedAt())
                + " | ID reservation #" + reservation.id()
                + " | Seance #" + reservation.seanceId()
        );
        requestLabel.setWrapText(true);
        requestLabel.getStyleClass().add("reservation-section-copy");

        Label ratingLabel = new Label(buildStudentRatingSummary(reservation));
        ratingLabel.setWrapText(true);
        ratingLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);
        Label idChip = new Label("Reservation #" + reservation.id());
        idChip.getStyleClass().add("workspace-chip");
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actionRow.getChildren().addAll(idChip, actionSpacer);

        if (canCurrentUserRateReservation(reservation)) {
            Button ratingButton = new Button("Noter la seance");
            ratingButton.getStyleClass().add("backoffice-primary-button");
            ratingButton.setOnAction(event -> openStudentRatingDialog(reservation));
            actionRow.getChildren().add(ratingButton);
        }

        if (reservation.isPending()) {
            Button cancelButton = new Button("Annuler ma reservation");
            cancelButton.getStyleClass().addAll("action-button", "danger");
            cancelButton.setOnAction(event -> confirmCancelStudentReservation(reservation));
            actionRow.getChildren().add(cancelButton);
        }

        card.getChildren().addAll(headerRow, sessionLabel, requestLabel, ratingLabel, actionRow);
        return card;
    }

    private boolean canCurrentUserRateReservation(StudentReservationItem reservation) {
        return UserSession.isCurrentStudent()
            && reservation != null
            && reservation.canBeRated()
            && !reservation.hasRating();
    }

    private String buildStudentRatingSummary(StudentReservationItem reservation) {
        if (reservation == null) {
            return "Evaluation: indisponible.";
        }
        if (reservation.hasRating()) {
            StringBuilder summary = new StringBuilder("Evaluation: " + reservation.studentRating() + "/5");
            if (reservation.ratedAt() != null) {
                summary.append(" | Notee le ").append(formatDateTimeOrPlaceholder(reservation.ratedAt()));
            }
            if (reservation.studentReview() != null && !reservation.studentReview().isBlank()) {
                summary.append(" | Avis: ").append(reservation.studentReview());
            }
            return summary.toString();
        }
        if (reservation.canBeRated()) {
            return "Evaluation: cette seance est terminee, vous pouvez maintenant la noter.";
        }
        if (reservation.isAccepted()) {
            return "Evaluation: disponible apres la fin de la seance.";
        }
        return "Evaluation: la note sera ouverte apres acceptation puis apres la fin de la seance.";
    }

    private void openStudentRatingDialog(StudentReservationItem reservation) {
        if (!canCurrentUserRateReservation(reservation)) {
            showStudentReservationsFeedback(
                reservation != null && reservation.hasRating()
                    ? "Cette reservation a deja ete notee."
                    : "Cette reservation ne peut pas etre notee pour le moment.",
                false
            );
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogPane dialogPane = dialog.getDialogPane();
        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);

        dialog.setTitle("Noter la seance");
        dialogPane.getButtonTypes().setAll(cancelButtonType, saveButtonType);
        dialogPane.getStyleClass().add("rating-dialog-pane");
        dialogPane.setContent(buildStudentRatingDialogContent(reservation));
        dialogPane.setPrefWidth(560);
        applyCurrentTheme(dialogPane);
        appendDialogStylesheet(dialogPane, RATING_DIALOG_STYLESHEET);

        @SuppressWarnings("unchecked")
        ComboBox<Integer> ratingComboBox = (ComboBox<Integer>) dialogPane.lookup("#studentRatingComboBox");
        TextArea reviewArea = (TextArea) dialogPane.lookup("#studentRatingReviewArea");
        Button saveButton = (Button) dialogPane.lookupButton(saveButtonType);
        Button cancelButton = (Button) dialogPane.lookupButton(cancelButtonType);

        if (saveButton != null) {
            saveButton.setText("Envoyer mon avis");
            saveButton.getStyleClass().addAll("backoffice-primary-button", "rating-dialog-action-button");
            if (ratingComboBox != null) {
                saveButton.disableProperty().bind(ratingComboBox.valueProperty().isNull());
                updateStudentRatingActionButton(saveButton, ratingComboBox.getValue());
                ratingComboBox.valueProperty().addListener((observable, previous, rating) ->
                    updateStudentRatingActionButton(saveButton, rating)
                );
            }
        }
        if (cancelButton != null) {
            cancelButton.getStyleClass().addAll("backoffice-secondary-button", "rating-dialog-action-button");
        }

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isPresent() && choice.get() == saveButtonType && ratingComboBox != null) {
            saveStudentRating(reservation, ratingComboBox.getValue(), reviewArea == null ? null : reviewArea.getText());
        }
    }

    private StackPane buildStudentRatingDialogContent(StudentReservationItem reservation) {
        StackPane root = new StackPane();
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("rating-dialog-root");

        VBox card = new VBox(16.0);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420.0);
        card.getStyleClass().add("rating-dialog-card");

        Label titleLabel = new Label("Please rate your experience");
        titleLabel.getStyleClass().add("rating-dialog-main-title");

        Label subtitleLabel = new Label("Votre retour compte vraiment.");
        subtitleLabel.getStyleClass().add("rating-dialog-main-subtitle");

        Label contextLabel = new Label(
            safeText(reservation.seanceTitle())
                + " | "
                + formatDateTimeOrPlaceholder(reservation.seanceStartAt())
                + " | Tuteur: "
                + tutorDirectoryService.getTutorDisplayName(reservation.tutorId())
        );
        contextLabel.setWrapText(true);
        contextLabel.setAlignment(Pos.CENTER);
        contextLabel.getStyleClass().add("rating-dialog-context");

        StudentRatingMascotParts mascot = buildStudentRatingMascot();

        ComboBox<Integer> ratingComboBox = new ComboBox<>();
        ratingComboBox.setId("studentRatingComboBox");
        ratingComboBox.getItems().setAll(
            ReservationService.MIN_STUDENT_RATING,
            2,
            3,
            4,
            ReservationService.MAX_STUDENT_RATING
        );
        ratingComboBox.setManaged(false);
        ratingComboBox.setVisible(false);

        HBox starsRow = new HBox(10.0);
        starsRow.setAlignment(Pos.CENTER);
        List<Button> starButtons = new ArrayList<>();
        for (int value = 1; value <= ReservationService.MAX_STUDENT_RATING; value++) {
            final int ratingValue = value;
            Button starButton = new Button("★");
            starButton.getStyleClass().add("rating-dialog-star");
            starButton.setText("★");
            starButton.setOnAction(event -> ratingComboBox.setValue(ratingValue));
            starButtons.add(starButton);
            starsRow.getChildren().add(starButton);
        }

        Label scoreLabel = new Label("Choisissez une note");
        scoreLabel.getStyleClass().add("rating-dialog-score-title");

        Label ratingSummaryCopy = new Label("Une note rapide suffit. Le commentaire reste optionnel.");
        ratingSummaryCopy.setWrapText(true);
        ratingSummaryCopy.setAlignment(Pos.CENTER);
        ratingSummaryCopy.getStyleClass().add("rating-dialog-summary-copy");

        ratingComboBox.valueProperty().addListener((observable, previous, rating) -> {
            updateStudentRatingStars(starButtons, rating);
            updateStudentRatingSelection(rating, scoreLabel, ratingSummaryCopy);
            updateStudentRatingMascot(mascot, rating);
        });

        TextArea reviewArea = new TextArea();
        reviewArea.setId("studentRatingReviewArea");
        reviewArea.setPromptText("Ajoutez un commentaire si vous voulez preciser votre ressenti.");
        reviewArea.setWrapText(true);
        reviewArea.setPrefRowCount(4);
        reviewArea.getStyleClass().addAll("text-area", "rating-dialog-text-area");

        Label reviewTitle = new Label("Commentaire optionnel");
        reviewTitle.getStyleClass().add("rating-dialog-review-title");

        Label reviewCounter = new Label("0 / " + STUDENT_REVIEW_MAX_LENGTH);
        reviewCounter.getStyleClass().add("rating-dialog-counter");

        reviewArea.textProperty().addListener((observable, previous, current) -> {
            int length = current == null ? 0 : current.length();
            reviewCounter.setText(length + " / " + STUDENT_REVIEW_MAX_LENGTH);
            reviewCounter.getStyleClass().remove("rating-dialog-counter-warning");
            if (length >= STUDENT_REVIEW_SOFT_LIMIT) {
                reviewCounter.getStyleClass().add("rating-dialog-counter-warning");
            }
        });

        VBox reviewBox = new VBox(8.0);
        reviewBox.setAlignment(Pos.CENTER_LEFT);
        reviewBox.getStyleClass().add("rating-dialog-review-box");
        reviewBox.getChildren().addAll(reviewTitle, reviewArea, reviewCounter);

        card.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            mascot.root(),
            starsRow,
            scoreLabel,
            ratingSummaryCopy,
            contextLabel,
            reviewBox,
            ratingComboBox
        );

        root.getChildren().add(card);
        updateStudentRatingStars(starButtons, ratingComboBox.getValue());
        updateStudentRatingSelection(ratingComboBox.getValue(), scoreLabel, ratingSummaryCopy);
        updateStudentRatingMascot(mascot, ratingComboBox.getValue());
        return root;
    }

    private StudentRatingMascotParts buildStudentRatingMascot() {
        StackPane mascot = new StackPane();
        mascot.getStyleClass().add("rating-dialog-mascot");
        mascot.setMinSize(110.0, 110.0);
        mascot.setPrefSize(110.0, 110.0);
        mascot.setMaxSize(110.0, 110.0);

        Pane face = new Pane();
        face.setPrefSize(110.0, 110.0);

        Circle leftEyeWhite = new Circle(36.0, 42.0, 14.0, Color.WHITE);
        Circle rightEyeWhite = new Circle(74.0, 42.0, 14.0, Color.WHITE);
        Circle leftEyePupil = new Circle(32.0, 38.0, 6.0, Color.web("#101828"));
        Circle rightEyePupil = new Circle(70.0, 38.0, 6.0, Color.web("#101828"));
        Circle leftCheek = new Circle(33.0, 66.0, 5.0, Color.web("#ffc2b4"));
        Circle rightCheek = new Circle(77.0, 66.0, 5.0, Color.web("#ffc2b4"));
        leftCheek.setOpacity(0.0);
        rightCheek.setOpacity(0.0);

        Line leftLid = new Line(25.0, 40.0, 47.0, 40.0);
        Line rightLid = new Line(63.0, 40.0, 85.0, 40.0);
        leftLid.setStrokeWidth(4.0);
        rightLid.setStrokeWidth(4.0);
        leftLid.setStroke(Color.web("#7b7772"));
        rightLid.setStroke(Color.web("#7b7772"));

        Line leftBrow = new Line(24.0, 26.0, 44.0, 22.0);
        Line rightBrow = new Line(66.0, 22.0, 86.0, 26.0);
        leftBrow.setStrokeWidth(4.0);
        rightBrow.setStrokeWidth(4.0);
        leftBrow.setStroke(Color.web("#101828"));
        rightBrow.setStroke(Color.web("#101828"));

        Line mouthNeutral = new Line(44.0, 68.0, 66.0, 68.0);
        mouthNeutral.setStrokeWidth(4.0);
        mouthNeutral.setStroke(Color.web("#101828"));

        Arc smile = new Arc(55.0, 67.0, 16.0, 12.0, 200.0, 140.0);
        smile.setType(ArcType.OPEN);
        smile.setStroke(Color.web("#101828"));
        smile.setStrokeWidth(4.0);
        smile.setFill(Color.TRANSPARENT);

        face.getChildren().addAll(
            leftCheek,
            rightCheek,
            leftEyeWhite,
            rightEyeWhite,
            leftEyePupil,
            rightEyePupil,
            leftLid,
            rightLid,
            leftBrow,
            rightBrow,
            mouthNeutral,
            smile
        );
        mascot.getChildren().add(face);

        StudentRatingMascotParts parts = new StudentRatingMascotParts(
            mascot,
            leftEyeWhite,
            rightEyeWhite,
            leftEyePupil,
            rightEyePupil,
            leftLid,
            rightLid,
            leftBrow,
            rightBrow,
            mouthNeutral,
            smile,
            leftCheek,
            rightCheek
        );
        updateStudentRatingMascot(parts, null);
        return parts;
    }

    private void updateStudentRatingStars(List<Button> starButtons, Integer rating) {
        String accentColor = resolveStudentRatingAccent(rating);
        for (int index = 0; index < starButtons.size(); index++) {
            Button starButton = starButtons.get(index);
            starButton.getStyleClass().remove("rating-dialog-star-active");
            starButton.setStyle(null);
            if (rating != null && index < rating) {
                starButton.getStyleClass().add("rating-dialog-star-active");
                starButton.setStyle("-fx-text-fill: " + accentColor + ";");
            }
        }
    }

    private void updateStudentRatingMascot(StudentRatingMascotParts mascot, Integer rating) {
        if (mascot == null) {
            return;
        }

        mascot.root().setStyle(resolveStudentRatingMascotStyle(rating));
        mascot.leftEyeWhite().setVisible(rating != null);
        mascot.rightEyeWhite().setVisible(rating != null);
        mascot.leftEyePupil().setVisible(rating != null);
        mascot.rightEyePupil().setVisible(rating != null);
        mascot.leftLid().setVisible(rating == null);
        mascot.rightLid().setVisible(rating == null);
        mascot.leftBrow().setVisible(false);
        mascot.rightBrow().setVisible(false);
        mascot.mouthNeutral().setVisible(false);
        mascot.smile().setVisible(false);
        mascot.leftCheek().setOpacity(0.0);
        mascot.rightCheek().setOpacity(0.0);

        if (rating == null) {
            mascot.mouthNeutral().setVisible(true);
            mascot.mouthNeutral().setStroke(Color.web("#908a84"));
            mascot.mouthNeutral().setStartX(45.0);
            mascot.mouthNeutral().setEndX(65.0);
            mascot.mouthNeutral().setStartY(69.0);
            mascot.mouthNeutral().setEndY(69.0);
            return;
        }

        Color accentColor = Color.web(resolveStudentRatingAccent(rating));
        mascot.leftEyePupil().setCenterY(39.0);
        mascot.rightEyePupil().setCenterY(39.0);
        mascot.smile().setStartAngle(200.0);
        mascot.smile().setLength(140.0);
        mascot.smile().setCenterX(55.0);
        mascot.smile().setStroke(Color.web("#101828"));

        switch (rating) {
            case 1 -> {
                configureStudentRatingBrows(mascot.leftBrow(), mascot.rightBrow(), 24.0, 31.0, 44.0, 24.0, 66.0, 24.0, 86.0, 31.0);
                mascot.leftBrow().setVisible(true);
                mascot.rightBrow().setVisible(true);
                mascot.smile().setVisible(true);
                mascot.smile().setCenterY(73.0);
                mascot.smile().setRadiusX(13.0);
                mascot.smile().setRadiusY(9.0);
                mascot.smile().setScaleY(-1.0);
            }
            case 2 -> {
                configureStudentRatingBrows(mascot.leftBrow(), mascot.rightBrow(), 24.0, 27.0, 44.0, 24.0, 66.0, 24.0, 86.0, 27.0);
                mascot.leftBrow().setVisible(true);
                mascot.rightBrow().setVisible(true);
                mascot.smile().setVisible(true);
                mascot.smile().setCenterY(72.0);
                mascot.smile().setRadiusX(14.0);
                mascot.smile().setRadiusY(10.0);
                mascot.smile().setScaleY(-0.85);
            }
            case 3 -> {
                mascot.mouthNeutral().setVisible(true);
                mascot.mouthNeutral().setStroke(Color.web("#101828"));
                mascot.mouthNeutral().setStartX(44.0);
                mascot.mouthNeutral().setEndX(66.0);
                mascot.mouthNeutral().setStartY(68.0);
                mascot.mouthNeutral().setEndY(68.0);
            }
            case 4 -> {
                mascot.smile().setVisible(true);
                mascot.smile().setCenterY(69.0);
                mascot.smile().setRadiusX(15.0);
                mascot.smile().setRadiusY(10.0);
                mascot.smile().setScaleY(0.7);
                mascot.leftCheek().setFill(accentColor.deriveColor(0, 1, 1, 0.28));
                mascot.rightCheek().setFill(accentColor.deriveColor(0, 1, 1, 0.28));
                mascot.leftCheek().setOpacity(1.0);
                mascot.rightCheek().setOpacity(1.0);
            }
            case 5 -> {
                mascot.smile().setVisible(true);
                mascot.smile().setCenterY(67.0);
                mascot.smile().setRadiusX(17.0);
                mascot.smile().setRadiusY(12.0);
                mascot.smile().setScaleY(1.0);
                mascot.leftCheek().setFill(accentColor.deriveColor(0, 1, 1, 0.34));
                mascot.rightCheek().setFill(accentColor.deriveColor(0, 1, 1, 0.34));
                mascot.leftCheek().setOpacity(1.0);
                mascot.rightCheek().setOpacity(1.0);
            }
            default -> {
                mascot.mouthNeutral().setVisible(true);
                mascot.mouthNeutral().setStroke(Color.web("#101828"));
            }
        }
    }

    private void configureStudentRatingBrows(Line leftBrow,
                                             Line rightBrow,
                                             double leftStartX,
                                             double leftStartY,
                                             double leftEndX,
                                             double leftEndY,
                                             double rightStartX,
                                             double rightStartY,
                                             double rightEndX,
                                             double rightEndY) {
        leftBrow.setStartX(leftStartX);
        leftBrow.setStartY(leftStartY);
        leftBrow.setEndX(leftEndX);
        leftBrow.setEndY(leftEndY);
        rightBrow.setStartX(rightStartX);
        rightBrow.setStartY(rightStartY);
        rightBrow.setEndX(rightEndX);
        rightBrow.setEndY(rightEndY);
    }

    private void updateStudentRatingActionButton(Button saveButton, Integer rating) {
        if (saveButton == null) {
            return;
        }

        String background = resolveStudentRatingActionBackground(rating);
        String shadow = resolveStudentRatingActionShadow(rating);
        saveButton.setStyle(
            "-fx-background-color: " + background + ";"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;"
                + "-fx-text-fill: white;"
                + "-fx-font-weight: bold;"
                + "-fx-padding: 10px 24px;"
                + "-fx-effect: dropshadow(gaussian, " + shadow + ", 12, 0.18, 0, 4);"
        );
    }

    private String resolveStudentRatingAccent(Integer rating) {
        if (rating == null) {
            return "#b8b4af";
        }

        return switch (rating) {
            case 1 -> "#ff4d4f";
            case 2 -> "#ff6b57";
            case 3 -> "#ff8a3d";
            case 4 -> "#ffa63d";
            case 5 -> "#ffb23f";
            default -> "#ff8a3d";
        };
    }

    private String resolveStudentRatingMascotStyle(Integer rating) {
        String background = switch (rating == null ? 0 : rating) {
            case 1 -> "linear-gradient(to bottom, #ff5c57 0%, #ff3d4d 100%)";
            case 2 -> "linear-gradient(to bottom, #ff7d5a 0%, #ff654f 100%)";
            case 3 -> "linear-gradient(to bottom, #ff9851 0%, #ff8244 100%)";
            case 4 -> "linear-gradient(to bottom, #ffb14a 0%, #ff9d39 100%)";
            case 5 -> "linear-gradient(to bottom, #ffbb49 0%, #ffa53b 100%)";
            default -> "linear-gradient(to bottom, #f1efec 0%, #dedad6 100%)";
        };

        String shadow = switch (rating == null ? 0 : rating) {
            case 1 -> "rgba(255, 77, 79, 0.25)";
            case 2 -> "rgba(255, 107, 87, 0.24)";
            case 3 -> "rgba(255, 138, 61, 0.22)";
            case 4 -> "rgba(255, 166, 61, 0.24)";
            case 5 -> "rgba(255, 178, 63, 0.26)";
            default -> "rgba(140, 136, 131, 0.18)";
        };

        return "-fx-background-color: " + background + ";"
            + "-fx-background-radius: 30px;"
            + "-fx-border-radius: 30px;"
            + "-fx-effect: dropshadow(gaussian, " + shadow + ", 20, 0.24, 0, 5);";
    }

    private String resolveStudentRatingActionBackground(Integer rating) {
        return "linear-gradient(to right, #5e4dc7 0%, #4c67cb 100%)";
    }

    private String resolveStudentRatingActionShadow(Integer rating) {
        return "rgba(94, 77, 199, 0.20)";
    }

    private void updateStudentRatingSelection(Integer rating,
                                              Label scoreLabel,
                                              Label summaryCopy) {
        if (rating == null) {
            scoreLabel.setText("Choisissez une note");
            summaryCopy.setText("Une note rapide suffit. Le commentaire reste optionnel.");
            return;
        }

        scoreLabel.setText(rating + " / 5  " + resolveStudentRatingHeadline(rating));
        summaryCopy.setText(resolveStudentRatingCopy(rating));
    }

    private String resolveStudentRatingHeadline(int rating) {
        return switch (rating) {
            case 1 -> "A revoir";
            case 2 -> "Peut mieux faire";
            case 3 -> "Correct";
            case 4 -> "Tres satisfaisant";
            case 5 -> "Excellent";
            default -> "Evaluation de la seance";
        };
    }

    private String resolveStudentRatingCopy(int rating) {
        return switch (rating) {
            case 1 -> "Le ressenti semble insuffisant. Un commentaire court peut aider a contextualiser le point faible.";
            case 2 -> "La seance reste perfectible. Un exemple rapide sur le rythme ou la clarte peut etre utile.";
            case 3 -> "La base est correcte. Vous pouvez ajouter ce qui a fonctionne et ce qui pourrait etre renforce.";
            case 4 -> "La seance a bien repondu au besoin. Vous pouvez mentionner le point fort principal si vous le souhaitez.";
            case 5 -> "La seance a produit une tres bonne impression. Vous pouvez souligner ce qui a fait la difference.";
            default -> "Decrivez librement votre ressenti.";
        };
    }

    private record StudentRatingMascotParts(StackPane root,
                                            Circle leftEyeWhite,
                                            Circle rightEyeWhite,
                                            Circle leftEyePupil,
                                            Circle rightEyePupil,
                                            Line leftLid,
                                            Line rightLid,
                                            Line leftBrow,
                                            Line rightBrow,
                                            Line mouthNeutral,
                                            Arc smile,
                                            Circle leftCheek,
                                            Circle rightCheek) {
    }

    private void saveStudentRating(StudentReservationItem reservation, Integer rating, String review) {
        hideStudentReservationsFeedback();
        if (rating == null) {
            showStudentReservationsFeedback("Choisissez une note avant de valider.", false);
            return;
        }

        OperationResult result = reservationService.rateCompletedReservation(
            reservation.id(),
            getCurrentReservationParticipantId(),
            rating,
            review
        );
        loadStudentReservations();
        showStudentReservationsFeedback(result.getMessage(), result.isSuccess());
    }

    private void confirmCancelStudentReservation(StudentReservationItem reservation) {
        ButtonType cancelButton = new ButtonType("Garder", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirmButton = new ButtonType("Annuler la reservation", ButtonBar.ButtonData.OK_DONE);

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Annulation de reservation");
        confirmationAlert.setHeaderText("Confirmer l'annulation");
        confirmationAlert.setContentText(
            "Ta demande pour la seance \"" + safeText(reservation.seanceTitle()) + "\" sera annulee."
        );
        confirmationAlert.getButtonTypes().setAll(cancelButton, confirmButton);

        Optional<ButtonType> choice = confirmationAlert.showAndWait();
        if (choice.isPresent() && choice.get() == confirmButton) {
            cancelStudentReservation(reservation);
        }
    }

    private void cancelStudentReservation(StudentReservationItem reservation) {
        hideStudentReservationsFeedback();
        OperationResult result = reservationService.cancelStudentReservation(
            reservation.id(),
            getCurrentReservationParticipantId()
        );
        loadSessionDashboard();
        loadStudentReservations();
        showStudentReservationsFeedback(result.getMessage(), result.isSuccess());
    }

    private void loadTutorReservationRequests() {
        tutorReservationRequestsContainer.getChildren().clear();

        List<TutorReservationRequest> requests = reservationService.getTutorReservationRequests(
            getCurrentTutorId()
        );
        long pendingCount = requests.stream().filter(TutorReservationRequest::isPending).count();
        tutorReservationRequestsSummaryLabel.setText(formatTutorRequestsSummary(requests.size(), pendingCount));

        if (requests.isEmpty()) {
            Label emptyLabel = new Label("Aucune demande de reservation pour vos seances pour le moment.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            tutorReservationRequestsContainer.getChildren().add(emptyLabel);
            return;
        }

        requests.stream()
            .map(this::buildTutorReservationRequestCard)
            .forEach(tutorReservationRequestsContainer.getChildren()::add);
    }

    private VBox buildTutorReservationRequestCard(TutorReservationRequest request) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-form-shell");

        HBox headerRow = new HBox(10.0);
        Label titleLabel = new Label(safeText(request.seanceTitle()));
        titleLabel.getStyleClass().add("subsection-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusChip = new Label(mapReservationRequestStatusLabel(request.status()));
        statusChip.getStyleClass().addAll("reservation-status", mapReservationRequestStatusStyle(request.status()));
        headerRow.getChildren().addAll(titleLabel, spacer, statusChip);

        Label studentLabel = new Label(
            "Etudiant: " + safeText(request.participantName())
                + " | Email: " + safeText(request.participantEmail())
                + " | Demande envoyee: " + formatDateTimeOrPlaceholder(request.reservedAt())
        );
        studentLabel.setWrapText(true);
        studentLabel.getStyleClass().add("reservation-section-copy");

        Label sessionLabel = new Label(
            "Seance: " + formatDateTimeOrPlaceholder(request.seanceStartAt())
                + " | " + request.durationMin() + " min"
                + " | " + request.acceptedReservations() + "/" + request.maxParticipants() + " acceptee(s)"
                + " | " + formatAvailableSeats(request.availableAcceptedSeats())
                + " | ID reservation #" + request.id()
        );
        sessionLabel.setWrapText(true);
        sessionLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);
        Label idChip = new Label("Seance #" + request.seanceId());
        idChip.getStyleClass().add("workspace-chip");
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button acceptButton = new Button("Accepter");
        acceptButton.getStyleClass().addAll("action-button", "accept");
        acceptButton.setOnAction(event -> acceptTutorReservationRequest(request));

        Button refuseButton = new Button("Refuser");
        refuseButton.getStyleClass().addAll("action-button", "danger");
        refuseButton.setOnAction(event -> confirmRefuseTutorReservationRequest(request));

        actionRow.getChildren().addAll(idChip, actionSpacer);
        if (request.isPending()) {
            if (request.isSessionCapacityReached()) {
                Button fullButton = new Button("Capacite atteinte");
                fullButton.getStyleClass().addAll("action-button", "secondary");
                fullButton.setDisable(true);
                actionRow.getChildren().addAll(fullButton, refuseButton);
            } else {
                actionRow.getChildren().addAll(acceptButton, refuseButton);
            }
        }

        card.getChildren().addAll(headerRow, studentLabel, sessionLabel, actionRow);
        return card;
    }

    private void acceptTutorReservationRequest(TutorReservationRequest request) {
        hideTutorReservationRequestsFeedback();
        if (request.isSessionCapacityReached()) {
            showTutorReservationRequestsFeedback("La capacite de cette seance est deja atteinte.", false);
            loadTutorReservationRequests();
            return;
        }

        OperationResult result = reservationService.acceptReservation(
            request.id(),
            getCurrentTutorId()
        );
        loadSessionDashboard();
        loadTutorReservationRequests();
        showTutorReservationRequestsFeedback(result.getMessage(), result.isSuccess());
    }

    private void confirmRefuseTutorReservationRequest(TutorReservationRequest request) {
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType refuseButton = new ButtonType("Refuser", ButtonBar.ButtonData.OK_DONE);

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Refus de reservation");
        confirmationAlert.setHeaderText("Confirmer le refus");
        confirmationAlert.setContentText(
            "La demande de " + safeText(request.participantName())
                + " pour la seance \"" + safeText(request.seanceTitle()) + "\" sera marquee comme refusee."
        );
        confirmationAlert.getButtonTypes().setAll(cancelButton, refuseButton);

        Optional<ButtonType> choice = confirmationAlert.showAndWait();
        if (choice.isPresent() && choice.get() == refuseButton) {
            refuseTutorReservationRequest(request);
        }
    }

    private void refuseTutorReservationRequest(TutorReservationRequest request) {
        hideTutorReservationRequestsFeedback();
        OperationResult result = reservationService.refuseReservation(
            request.id(),
            getCurrentTutorId()
        );
        loadSessionDashboard();
        loadTutorReservationRequests();
        showTutorReservationRequestsFeedback(result.getMessage(), result.isSuccess());
    }

    private void showSessionDetails(Seance seance, ReservationStats reservationStats) {
        Dialog<ButtonType> detailsDialog = new Dialog<>();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane dialogPane = detailsDialog.getDialogPane();

        detailsDialog.setTitle("Detail de la seance");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setContent(buildSessionDetailsContent(seance, reservationStats, detailsDialog));
        dialogPane.setPrefWidth(720);
        dialogPane.getStyleClass().add("session-detail-dialog");
        applyCurrentTheme(dialogPane);
        detailsDialog.showAndWait();
    }

    private VBox buildSessionDetailsContent(Seance seance, ReservationStats reservationStats, Dialog<ButtonType> detailsDialog) {
        boolean canManageCurrentSession = canManageSession(seance);
        int reservationTotal = reservationStats != null ? reservationStats.total() : 0;
        LocalDateTime endAt = seance.getStartAt() != null
            ? seance.getStartAt().plusMinutes(seance.getDurationMin())
            : null;
        int availableSeats = Math.max(0, seance.getMaxParticipants() - reservationTotal);
        double occupancyRate = calculateOccupancyRate(reservationTotal, seance.getMaxParticipants());

        VBox root = new VBox(16.0);
        root.getStyleClass().add("session-detail-root");

        HBox header = new HBox(12.0);
        header.getStyleClass().add("session-detail-header");

        VBox titleBlock = new VBox(5.0);
        HBox titleRow = new HBox(8.0);
        Label titleLabel = new Label(safeText(seance.getMatiere()));
        titleLabel.getStyleClass().add("session-detail-title");
        Label statusChip = new Label(mapStatusLabel(seance.getStatus()));
        statusChip.getStyleClass().addAll("reservation-status", mapStatusStyle(seance.getStatus()));
        titleRow.getChildren().addAll(titleLabel, statusChip);

        titleBlock.getChildren().add(titleRow);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        header.getChildren().addAll(titleBlock, headerSpacer);
        if (canManageCurrentSession) {
            Label reservationChip = new Label(formatReservationCount(reservationTotal));
            reservationChip.getStyleClass().add("session-detail-reservation-chip");
            header.getChildren().add(reservationChip);
        }

        FlowPane metrics = new FlowPane(10.0, 10.0);
        metrics.getStyleClass().add("session-detail-metrics");
        metrics.setPrefWrapLength(560.0);
        metrics.getChildren().addAll(
            buildDetailMetric("Debut", formatDateTimeOrPlaceholder(seance.getStartAt()), "Date et heure"),
            buildDetailMetric("Fin", formatDateTimeOrPlaceholder(endAt), "Fin calculee"),
            buildDetailMetric("Duree", seance.getDurationMin() + " min", "Temps de seance")
        );
        if (canManageCurrentSession) {
            metrics.getChildren().addAll(
                buildDetailMetric("Places", availableSeats + " / " + seance.getMaxParticipants(), "Disponibles"),
                buildDetailMetric("Reservations", String.valueOf(reservationTotal), "Total lie a la seance")
            );
        } else {
            metrics.getChildren().add(buildDetailMetric("Places", availableSeats + " / " + seance.getMaxParticipants(), "Disponibles"));
        }
        metrics.getChildren().addAll(
            buildDetailMetric("Tuteur", tutorDirectoryService.getTutorDisplayName(seance.getTuteurId()), "ID " + seance.getTuteurId()),
            buildDetailMetric("Mode", mapModeLabel(seance.getMode()), "Format choisi"),
            buildDetailMetric("Salle", resolveSalleName(seance.getSalleId()), "Presentiel")
        );
        if (seance.isPresentiel()) {
            metrics.getChildren().addAll(
                buildDetailMetric("Materiel salle", resolveFixedEquipementNames(seance.getSalleId()), "Inclus"),
                buildDetailMetric("Ajouts seance", resolveAddedEquipementNames(seance.getEquipementQuantites()), "Complementaire")
            );
        } else {
            metrics.getChildren().add(buildDetailMetric("Materiel", "Aucun materiel", "Non requis"));
        }

        VBox occupancyCard = new VBox(9.0);
        occupancyCard.getStyleClass().add("session-detail-occupancy-card");
        HBox occupancyHeader = new HBox(10.0);
        Label occupancyTitle = new Label("Remplissage de la seance");
        occupancyTitle.getStyleClass().add("session-detail-section-title");
        Region occupancySpacer = new Region();
        HBox.setHgrow(occupancySpacer, Priority.ALWAYS);
        Label occupancyValue = new Label(formatPercent(occupancyRate));
        occupancyValue.getStyleClass().add("session-detail-occupancy-value");
        occupancyHeader.getChildren().addAll(occupancyTitle, occupancySpacer, occupancyValue);

        ProgressBar occupancyProgress = new ProgressBar(occupancyRate);
        occupancyProgress.setMaxWidth(Double.MAX_VALUE);
        occupancyProgress.getStyleClass().add("session-detail-progress");

        HBox statusRow = new HBox(8.0);
        statusRow.getStyleClass().add("session-detail-status-row");
        if (reservationStats != null) {
            statusRow.getChildren().addAll(
                buildReservationStatusChip("En attente", reservationStats.pending()),
                buildReservationStatusChip("Acceptees", reservationStats.accepted()),
                buildReservationStatusChip("Refusees", reservationStats.refused())
            );
        }
        occupancyCard.getChildren().addAll(occupancyHeader, occupancyProgress, statusRow);

        VBox descriptionBox = new VBox(8.0);
        descriptionBox.getStyleClass().add("session-detail-description-card");
        Label descriptionTitle = new Label("Description");
        descriptionTitle.getStyleClass().add("session-detail-section-title");
        Label descriptionText = new Label(safeText(seance.getDescription()));
        descriptionText.setWrapText(true);
        descriptionText.getStyleClass().add("session-detail-description-text");
        descriptionBox.getChildren().addAll(descriptionTitle, descriptionText);

        HBox footer = new HBox(10.0);
        footer.getStyleClass().add("session-detail-footer");
        footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label createdAtLabel = new Label("Creation: " + formatDateTimeOrPlaceholder(seance.getCreatedAt()));
        createdAtLabel.getStyleClass().add("session-detail-muted");
        footer.getChildren().add(createdAtLabel);
        if (seance.getUpdatedAt() != null) {
            Label updatedAtLabel = new Label("Mise a jour: " + formatDateTimeOrPlaceholder(seance.getUpdatedAt()));
            updatedAtLabel.getStyleClass().add("session-detail-muted");
            footer.getChildren().add(updatedAtLabel);
        }
        if (canManageCurrentSession) {
            Region footerSpacer = new Region();
            HBox.setHgrow(footerSpacer, Priority.ALWAYS);
            Button editActionButton = new Button("Modifier cette seance");
            editActionButton.getStyleClass().add("backoffice-primary-button");
            editActionButton.setOnAction(event -> {
                detailsDialog.close();
                startEditingSession(seance);
            });
            footer.getChildren().addAll(footerSpacer, editActionButton);
        }

        root.getChildren().addAll(header, metrics);
        if (canManageCurrentSession) {
            root.getChildren().add(occupancyCard);
        }
        root.getChildren().addAll(descriptionBox, footer);
        return root;
    }

    private VBox buildDetailMetric(String label, String value, String hint) {
        VBox metric = new VBox(4.0);
        metric.getStyleClass().add("session-detail-metric-card");
        metric.setPrefWidth(172.0);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("session-detail-metric-label");
        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("session-detail-metric-value");
        Label hintNode = new Label(hint);
        hintNode.setWrapText(true);
        hintNode.getStyleClass().add("session-detail-metric-hint");

        metric.getChildren().addAll(labelNode, valueNode, hintNode);
        return metric;
    }

    private Label buildReservationStatusChip(String label, int count) {
        Label chip = new Label(label + ": " + count);
        chip.getStyleClass().add("session-detail-status-chip");
        return chip;
    }

    private int getSelectedSessionPageSize() {
        Integer selectedValue = sessionsPerPageComboBox != null ? sessionsPerPageComboBox.getValue() : null;
        return selectedValue != null && selectedValue > 0 ? selectedValue : DEFAULT_SESSION_PAGE_SIZE;
    }

    private int calculateTotalPages(int totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void updateSessionPagination(int totalItems, int fromItem, int toItem, int totalPages) {
        sessionPaginationBar.setManaged(true);
        sessionPaginationBar.setVisible(true);
        sessionPaginationSummaryLabel.setText(fromItem + "-" + toItem + " sur " + totalItems + " seances affichees");
        sessionPreviousPageButton.setDisable(currentSessionPage <= 1);
        sessionNextPageButton.setDisable(currentSessionPage >= totalPages);

        sessionPageButtonsContainer.getChildren().clear();
        for (int page : buildVisiblePageNumbers(totalPages)) {
            Button pageButton = new Button(String.valueOf(page));
            pageButton.getStyleClass().add("backoffice-page-button");
            if (page == currentSessionPage) {
                pageButton.getStyleClass().add("active-page");
            } else {
                pageButton.setOnAction(event -> {
                    currentSessionPage = page;
                    applySessionFilters();
                });
            }
            sessionPageButtonsContainer.getChildren().add(pageButton);
        }
    }

    private List<Integer> buildVisiblePageNumbers(int totalPages) {
        int firstPage = Math.max(1, currentSessionPage - 2);
        int lastPage = Math.min(totalPages, firstPage + 4);
        firstPage = Math.max(1, lastPage - 4);

        java.util.ArrayList<Integer> pages = new java.util.ArrayList<>();
        for (int page = firstPage; page <= lastPage; page++) {
            pages.add(page);
        }
        return pages;
    }

    private void hideSessionPagination() {
        sessionPaginationBar.setManaged(false);
        sessionPaginationBar.setVisible(false);
        sessionPaginationSummaryLabel.setText("");
        sessionPageButtonsContainer.getChildren().clear();
    }

    private void startEditingSession(Seance seance) {
        editingSessionId = seance.getId();
        sessionSubjectField.setText(seance.getMatiere());
        setStartAtSelection(seance.getStartAt());
        sessionDurationField.setText(String.valueOf(seance.getDurationMin()));
        sessionCapacityField.setText(String.valueOf(seance.getMaxParticipants()));
        sessionDescriptionArea.setText(seance.getDescription() != null ? seance.getDescription() : "");
        sessionOnsiteCheckBox.setSelected(Seance.MODE_ONSITE.equals(seance.getMode()));
        updateOnsiteOptionsVisibility();
        selectSalleById(seance.getSalleId());
        selectEquipementsByQuantites(seance.getEquipementQuantites());

        sessionFormTitleLabel.setText("Modifier une seance");
        sessionFormModeChipLabel.setText("Modification");
        editingSessionLabel.setText(
            "Mode modification actif pour la seance #" + seance.getId()
                + ". Mets a jour les champs puis clique sur brouillon ou publier."
        );
        editingSessionLabel.setManaged(true);
        editingSessionLabel.setVisible(true);
        hideFeedback();
        showAddSessionSection();
    }

    private void confirmDeleteSession(Seance seance) {
        if (!canManageSession(seance)) {
            showSessionListFeedback("Seul le tuteur proprietaire peut supprimer cette seance.", false);
            return;
        }

        int linkedReservations;
        try {
            linkedReservations = seanceService.countReservationsForSeance(seance.getId());
        } catch (RuntimeException exception) {
            showSessionListFeedback(exception.getMessage(), false);
            return;
        }

        if (linkedReservations > 0) {
            showSessionListFeedback(buildDeleteBlockedMessage(linkedReservations), false);
            return;
        }

        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Suppression de seance");
        confirmationAlert.setHeaderText("Confirmer la suppression");
        confirmationAlert.setContentText(
            "La seance \"" + seance.getMatiere() + "\" sera supprimee definitivement."
        );
        confirmationAlert.getButtonTypes().setAll(cancelButton, deleteButton);

        Optional<ButtonType> choice = confirmationAlert.showAndWait();
        if (choice.isPresent() && choice.get() == deleteButton) {
            deleteSession(seance);
        }
    }

    private void deleteSession(Seance seance) {
        hideFeedback();
        hideReservationActionFeedback();

        OperationResult result = seanceService.deleteSeance(seance.getId());
        if (result.isSuccess()) {
            if (editingSessionId != null && editingSessionId == seance.getId()) {
                clearSessionForm();
                resetEditMode();
            }
            loadSessionDashboard();
            showSessionListFeedback(result.getMessage(), true);
        } else {
            showSessionListFeedback(result.getMessage(), false);
        }
    }

    private String buildDeleteBlockedMessage(int reservationCount) {
        String suffix = reservationCount > 1 ? " reservations." : " reservation.";
        return "Suppression impossible: cette seance possede " + reservationCount + suffix;
    }

    private LocalDateTime parseStartAt() {
        LocalDate selectedDate = sessionDatePicker.getValue();
        if (selectedDate == null) {
            throw new IllegalArgumentException("Choisissez la date de la seance.");
        }

        String selectedHour = requireText(sessionHourComboBox.getValue(), "Choisissez l'heure de la seance.");
        String selectedMinute = requireText(sessionMinuteComboBox.getValue(), "Choisissez les minutes de la seance.");

        LocalDateTime parsedValue = LocalDateTime.of(
            selectedDate,
            LocalTime.of(Integer.parseInt(selectedHour), Integer.parseInt(selectedMinute))
        );
        if (!parsedValue.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("La date de la seance doit etre dans le futur.");
        }
        return parsedValue;
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

    private String formatOptionalText(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Non renseigne";
    }

    private String formatDescription(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Aucune description renseignee.";
    }

    private String formatLabel(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return "Non defini";
        }

        String lowered = normalizedValue.toLowerCase();
        return Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1);
    }

    private boolean isAvailable(String status) {
        String normalizedStatus = status == null ? "" : status.toLowerCase().trim();
        return STATUS_AVAILABLE.equals(normalizedStatus);
    }

    private String resolveStatusStyle(String status) {
        String normalizedStatus = status == null ? "" : status.toLowerCase().trim();
        if (STATUS_AVAILABLE.equals(normalizedStatus)) {
            return "available";
        }
        if (normalizedStatus.contains(STATUS_PENDING)) {
            return "pending";
        }
        if (normalizedStatus.contains("maintenance")) {
            return "maintenance";
        }
        return "unavailable";
    }

    private String resolveMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Une erreur technique est survenue." : message;
    }

    private String formatReservationCount(int reservationCount) {
        if (reservationCount <= 0) {
            return "0 reservation";
        }
        return reservationCount == 1 ? "1 reservation" : reservationCount + " reservations";
    }

    private String formatAvailableSeats(int availableSeats) {
        if (availableSeats <= 0) {
            return "capacite atteinte";
        }
        return availableSeats == 1 ? "1 place disponible" : availableSeats + " places disponibles";
    }

    private double calculateOccupancyRate(int reservationCount, int capacity) {
        if (reservationCount <= 0 || capacity <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) reservationCount / capacity);
    }

    private String formatPercent(double value) {
        return Math.round(value * 100) + "%";
    }

    private String safeText(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Non renseigne";
    }

    private String formatDateTimeOrPlaceholder(LocalDateTime value) {
        return value != null ? value.format(DISPLAY_FORMATTER) : "Non renseigne";
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DISPLAY_FORMATTER) : "";
    }

    private void configureDateTimeInputs() {
        sessionDatePicker.setEditable(false);
        sessionDatePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate value) {
                return value != null ? value.format(DATE_PICKER_FORMATTER) : "";
            }

            @Override
            public LocalDate fromString(String value) {
                return value == null || value.isBlank() ? null : LocalDate.parse(value, DATE_PICKER_FORMATTER);
            }
        });
        sessionDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item == null || item.isBefore(LocalDate.now()));
            }
        });

        sessionHourComboBox.getItems().setAll(HOUR_OPTIONS);
        sessionMinuteComboBox.getItems().setAll(MINUTE_OPTIONS);
        sessionHourComboBox.setEditable(false);
        sessionMinuteComboBox.setEditable(false);
    }

    private void setStartAtSelection(LocalDateTime value) {
        if (value == null) {
            sessionDatePicker.setValue(null);
            sessionHourComboBox.getSelectionModel().clearSelection();
            sessionMinuteComboBox.getSelectionModel().clearSelection();
            return;
        }

        sessionDatePicker.setValue(value.toLocalDate());
        sessionHourComboBox.setValue(String.format("%02d", value.getHour()));
        sessionMinuteComboBox.setValue(String.format("%02d", value.getMinute()));
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

    private String mapReservationRequestStatusLabel(int status) {
        return switch (status) {
            case ReservationService.STATUS_ACCEPTED -> "Acceptee";
            case ReservationService.STATUS_REFUSED -> "Refusee";
            default -> "En attente";
        };
    }

    private String mapReservationRequestStatusStyle(int status) {
        return switch (status) {
            case ReservationService.STATUS_ACCEPTED -> "confirmed";
            case ReservationService.STATUS_REFUSED -> "refused";
            default -> "pending";
        };
    }

    private String formatTutorRequestsSummary(int totalRequests, long pendingRequests) {
        String totalLabel = totalRequests <= 1 ? totalRequests + " demande" : totalRequests + " demandes";
        String pendingLabel = pendingRequests <= 1 ? pendingRequests + " en attente" : pendingRequests + " en attente";
        return totalLabel + " | " + pendingLabel;
    }

    private String formatStudentReservationsSummary(int totalReservations, long pendingReservations,
                                                    long acceptedReservations, long refusedReservations) {
        String totalLabel = totalReservations <= 1
            ? totalReservations + " reservation"
            : totalReservations + " reservations";
        return totalLabel
            + " | " + pendingReservations + " en attente"
            + " | " + acceptedReservations + " acceptee(s)"
            + " | " + refusedReservations + " refusee(s)";
    }

    private String mapModeLabel(String mode) {
        return Seance.MODE_ONSITE.equals(mode) ? MODE_ONSITE_LABEL : MODE_ONLINE_LABEL;
    }

    private String buildInfrastructureSummary(Seance seance) {
        if (seance == null || !seance.isPresentiel()) {
            return MODE_ONLINE_LABEL;
        }

        String fixedEquipmentSummary = resolveFixedEquipementNames(seance.getSalleId());
        String addedEquipmentSummary = resolveAddedEquipementNames(seance.getEquipementQuantites());
        if ("Aucun materiel fixe".equals(fixedEquipmentSummary) && "Aucun ajout".equals(addedEquipmentSummary)) {
            return MODE_ONSITE_LABEL + " - " + resolveSalleName(seance.getSalleId()) + " - sans materiel";
        }
        if ("Aucun ajout".equals(addedEquipmentSummary)) {
            return MODE_ONSITE_LABEL + " - " + resolveSalleName(seance.getSalleId()) + " - inclus: " + fixedEquipmentSummary;
        }
        if ("Aucun materiel fixe".equals(fixedEquipmentSummary)) {
            return MODE_ONSITE_LABEL + " - " + resolveSalleName(seance.getSalleId()) + " - ajouts: " + addedEquipmentSummary;
        }
        return MODE_ONSITE_LABEL
            + " - "
            + resolveSalleName(seance.getSalleId())
            + " - inclus: "
            + fixedEquipmentSummary
            + " - ajouts: "
            + addedEquipmentSummary;
    }

    private String resolveSalleName(Integer salleId) {
        if (salleId == null || salleId <= 0) {
            return "Non requis";
        }

        return availableSalles.stream()
            .filter(salle -> salle.getIdSalle() == salleId)
            .map(Salle::getNom)
            .findFirst()
            .orElseGet(() -> {
                try {
                    Salle salle = salleService.recupererParId(salleId);
                    if (salle != null && salle.getNom() != null && !salle.getNom().isBlank()) {
                        return salle.getNom();
                    }
                } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
                    return "Salle #" + salleId;
                }
                return "Salle #" + salleId;
            });
    }

    private String resolveEquipementNames(Map<Integer, Integer> equipementQuantites) {
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            return "Aucun materiel";
        }

        List<String> names = equipementQuantites.entrySet().stream()
            .map(entry -> resolveEquipementName(entry.getKey()) + " x" + Math.max(1, entry.getValue()))
            .toList();
        return String.join(", ", names);
    }

    private String resolveAddedEquipementNames(Map<Integer, Integer> equipementQuantites) {
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            return "Aucun ajout";
        }
        return resolveEquipementNames(equipementQuantites);
    }

    private String resolveFixedEquipementNames(Integer salleId) {
        List<SalleEquipement> fixedEquipements = getRoomFixedEquipements(salleId);
        if (fixedEquipements.isEmpty()) {
            return "Aucun materiel fixe";
        }

        return fixedEquipements.stream()
            .map(equipement -> formatOptionalText(equipement.getNomEquipement()) + " x" + equipement.getQuantite())
            .reduce((left, right) -> left + ", " + right)
            .orElse("Aucun materiel fixe");
    }

    private String resolveEquipementName(Integer equipementId) {
        if (equipementId == null || equipementId <= 0) {
            return "Materiel inconnu";
        }

        return availableEquipements.stream()
            .filter(equipement -> equipement.getIdEquipement() == equipementId)
            .map(Equipement::getNom)
            .findFirst()
            .orElse("Materiel #" + equipementId);
    }

    private void clearSessionForm() {
        sessionSubjectField.clear();
        setStartAtSelection(null);
        sessionDurationField.clear();
        sessionCapacityField.clear();
        sessionDescriptionArea.clear();
        sessionOnsiteCheckBox.setSelected(false);
        selectedSalleId = null;
        updateRoomSelectionStyles();
        clearEquipmentSelection();
        updateOnsiteOptionsVisibility();
    }

    private void resetEditMode() {
        editingSessionId = null;
        sessionFormTitleLabel.setText("Publier une seance");
        sessionFormModeChipLabel.setText("Ajout direct");
        editingSessionLabel.setText("");
        editingSessionLabel.setManaged(false);
        editingSessionLabel.setVisible(false);
    }

    private void showAvailableSessionsSection() {
        if (sectionMenuComboBox != null && !SECTION_AVAILABLE_SESSIONS.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_AVAILABLE_SESSIONS);
        }
        updateRecommendationBriefingAvailability();
        setSectionVisible(sessionSearchPanel, true);
        setSectionVisible(sessionListPanel, true);
        setSectionVisible(sessionFormPanel, false);
        setSectionVisible(studentReservationsPanel, false);
        setSectionVisible(reservationRequestsPanel, false);
    }

    private void showSessionListFeedback(String message, boolean success) {
        hideFeedback();
        showAvailableSessionsSection();
        showReservationActionFeedback(message, success);
    }

    private void showAddSessionSection() {
        if (!UserSession.isCurrentTutor()) {
            showSessionListFeedback("Connectez-vous avec le compte tuteur pour ajouter une seance.", false);
            return;
        }

        if (sectionMenuComboBox != null && !SECTION_ADD_SESSION.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_ADD_SESSION);
        }
        hideReservationActionFeedback();
        setSectionVisible(sessionSearchPanel, false);
        setSectionVisible(sessionListPanel, false);
        setSectionVisible(sessionFormPanel, true);
        setSectionVisible(studentReservationsPanel, false);
        setSectionVisible(reservationRequestsPanel, false);
    }

    private void showStudentReservationsSection() {
        if (!UserSession.canUseReservationWorkspace()) {
            showSessionListFeedback("Connectez-vous avec un compte etudiant ou tuteur pour consulter vos reservations.", false);
            return;
        }

        if (sectionMenuComboBox != null && !SECTION_MY_RESERVATIONS.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_MY_RESERVATIONS);
        }

        hideStudentReservationsFeedback();
        setSectionVisible(sessionSearchPanel, false);
        setSectionVisible(sessionListPanel, false);
        setSectionVisible(sessionFormPanel, false);
        setSectionVisible(studentReservationsPanel, true);
        setSectionVisible(reservationRequestsPanel, false);
        loadStudentReservations();
    }

    private void showReservationRequestsSection() {
        if (!UserSession.isCurrentTutor()) {
            showAvailableSessionsSection();
            showReservationActionFeedback("Connectez-vous avec le compte tuteur pour consulter les demandes.", false);
            return;
        }

        if (sectionMenuComboBox != null && !SECTION_RESERVATION_REQUESTS.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_RESERVATION_REQUESTS);
        }

        hideTutorReservationRequestsFeedback();
        setSectionVisible(sessionSearchPanel, false);
        setSectionVisible(sessionListPanel, false);
        setSectionVisible(sessionFormPanel, false);
        setSectionVisible(studentReservationsPanel, false);
        setSectionVisible(reservationRequestsPanel, true);
        loadTutorReservationRequests();
    }

    private void configureWorkspaceSections() {
        if (UserSession.isCurrentTutor()) {
            sectionMenuComboBox.getItems().setAll(
                SECTION_AVAILABLE_SESSIONS,
                SECTION_MY_RESERVATIONS,
                SECTION_ADD_SESSION,
                SECTION_RESERVATION_REQUESTS
            );
            return;
        }

        sectionMenuComboBox.getItems().setAll(SECTION_AVAILABLE_SESSIONS, SECTION_MY_RESERVATIONS);
    }

    private void updateRecommendationBriefingAvailability() {
        setNodeVisible(recommendationBriefingButton, UserSession.isCurrentStudent());
    }

    private void setInfrastructureSectionVisible(HBox section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setManaged(visible);
        section.setVisible(visible);
    }

    private void setNodeVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setManaged(visible);
        node.setVisible(visible);
    }

    private void setSeparatorVisible(Separator separator, boolean visible) {
        if (separator == null) {
            return;
        }
        separator.setManaged(visible);
        separator.setVisible(visible);
    }

    private void setSectionVisible(VBox section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setManaged(visible);
        section.setVisible(visible);
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

    private void showReservationActionFeedback(String message, boolean success) {
        reservationActionFeedbackLabel.setText(message);
        reservationActionFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", success ? "success" : "error");
        reservationActionFeedbackLabel.setManaged(true);
        reservationActionFeedbackLabel.setVisible(true);
    }

    private void hideReservationActionFeedback() {
        reservationActionFeedbackLabel.setText("");
        reservationActionFeedbackLabel.getStyleClass().setAll("frontoffice-feedback");
        reservationActionFeedbackLabel.setManaged(false);
        reservationActionFeedbackLabel.setVisible(false);
    }

    private void showStudentReservationsFeedback(String message, boolean success) {
        studentReservationsFeedbackLabel.setText(message);
        studentReservationsFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", success ? "success" : "error");
        studentReservationsFeedbackLabel.setManaged(true);
        studentReservationsFeedbackLabel.setVisible(true);
    }

    private void hideStudentReservationsFeedback() {
        studentReservationsFeedbackLabel.setText("");
        studentReservationsFeedbackLabel.getStyleClass().setAll("frontoffice-feedback");
        studentReservationsFeedbackLabel.setManaged(false);
        studentReservationsFeedbackLabel.setVisible(false);
    }

    private void showTutorReservationRequestsFeedback(String message, boolean success) {
        tutorReservationRequestsFeedbackLabel.setText(message);
        tutorReservationRequestsFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", success ? "success" : "error");
        tutorReservationRequestsFeedbackLabel.setManaged(true);
        tutorReservationRequestsFeedbackLabel.setVisible(true);
    }

    private void hideTutorReservationRequestsFeedback() {
        tutorReservationRequestsFeedbackLabel.setText("");
        tutorReservationRequestsFeedbackLabel.getStyleClass().setAll("frontoffice-feedback");
        tutorReservationRequestsFeedbackLabel.setManaged(false);
        tutorReservationRequestsFeedbackLabel.setVisible(false);
    }

    private void applyCurrentTheme(DialogPane dialogPane) {
        if (dialogPane == null || recentSessionsContainer == null || recentSessionsContainer.getScene() == null) {
            return;
        }
        dialogPane.getStylesheets().setAll(recentSessionsContainer.getScene().getStylesheets());
    }

    private void appendDialogStylesheet(DialogPane dialogPane, String stylesheetPath) {
        if (dialogPane == null || stylesheetPath == null || stylesheetPath.isBlank()) {
            return;
        }

        URL resource = getClass().getResource(stylesheetPath);
        if (resource == null) {
            return;
        }

        String stylesheet = resource.toExternalForm();
        if (!dialogPane.getStylesheets().contains(stylesheet)) {
            dialogPane.getStylesheets().add(stylesheet);
        }
    }

    private record EquipmentSelectionControls(CheckBox checkBox, Spinner<Integer> quantitySpinner, VBox card, boolean selectable) {
    }

    private record RoomSelectionControls(VBox card, Label statusChip) {
    }
}

