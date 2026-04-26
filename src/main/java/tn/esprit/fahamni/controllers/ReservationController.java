package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.Models.SalleEquipement;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.MatchingService;
import tn.esprit.fahamni.services.MatchingService.MatchingAcceptanceResult;
import tn.esprit.fahamni.services.MatchingService.MatchingDraft;
import tn.esprit.fahamni.services.MatchingService.MatchingNeedProfile;
import tn.esprit.fahamni.services.MatchingService.MatchingPlanContext;
import tn.esprit.fahamni.services.MatchingService.MatchingRequestCreation;
import tn.esprit.fahamni.services.MatchingService.SessionVisibility;
import tn.esprit.fahamni.services.MatchingService.StudentMatchCard;
import tn.esprit.fahamni.services.MatchingService.TutorMatchInboxItem;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.ReservationService;
import tn.esprit.fahamni.services.ReservationService.ReservationStats;
import tn.esprit.fahamni.services.ReservationService.SessionEvaluationItem;
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
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
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
import javafx.scene.Scene;
import javafx.geometry.Insets;
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
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.scene.input.MouseEvent;

public class ReservationController {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String RATING_DIALOG_STYLESHEET = "/com/fahamni/styles/frontoffice-rating-dialog.css";
    private static final String MATCHING_DIALOG_STYLESHEET = "/com/fahamni/styles/frontoffice-matching-dialog.css";
    private static final String SESSION_EVALUATIONS_STYLESHEET = "/com/fahamni/styles/frontoffice-session-evaluations.css";
    private static final String PAYMENT_DIALOG_STYLESHEET = "/com/fahamni/styles/frontoffice-reservation.css";
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
    private static final double MATCHING_SWIPE_HORIZONTAL_THRESHOLD = 150.0;
    private static final double MATCHING_SWIPE_SUPER_THRESHOLD = 120.0;
    private static final double MATCHING_SWIPE_ROTATION_FACTOR = 0.055;
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
    private final MatchingService matchingService = new MatchingService();
    private final List<Salle> availableSalles = new ArrayList<>();
    private final List<Equipement> availableEquipements = new ArrayList<>();
    private final Map<Integer, List<SalleEquipement>> roomFixedEquipementsBySalleId = new LinkedHashMap<>();
    private final Map<Integer, EquipmentSelectionControls> equipmentSelectionControls = new LinkedHashMap<>();
    private final Map<Integer, RoomSelectionControls> roomSelectionControls = new LinkedHashMap<>();
    private final Map<Integer, String> roomScheduleConflicts = new LinkedHashMap<>();
    private Integer selectedSalleId;
    private Integer editingSessionId;
    private int currentSessionPage = 1;
    private PendingMatchingPlan pendingMatchingPlan;

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
    private Button matchingSwipeButton;

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
    private TextField sessionPriceField;

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
        updateMatchingWorkspaceAvailability();

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
    private void handleOpenMatchingWorkspace() {
        openMatchingWorkspace();
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
        pendingMatchingPlan = null;
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

            String finalMessage = editing ? "La seance a ete modifiee avec succes." : successMessage;
            if (!editing && pendingMatchingPlan != null) {
                OperationResult matchingLinkResult = matchingService.linkPlannedSession(
                    pendingMatchingPlan.requestId(),
                    pendingMatchingPlan.participantId(),
                    pendingMatchingPlan.visibilityScope(),
                    seance.getId()
                );
                if (matchingLinkResult.isSuccess()) {
                    finalMessage = finalMessage + " " + matchingLinkResult.getMessage();
                } else {
                    finalMessage = finalMessage + " La liaison du matching n'a pas pu etre finalisee: "
                        + matchingLinkResult.getMessage();
                }
                pendingMatchingPlan = null;
            }

            clearSessionForm();
            resetEditMode();
            loadSessionDashboard();
            showSessionListFeedback(finalMessage, true);
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
        double priceTnd = parsePriceTnd(
            sessionPriceField.getText(),
            "Renseignez un prix valide en TND. Utilisez 0 pour une seance gratuite."
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
        sessionPriceField.setText(formatPriceTnd(priceTnd));
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
        seance.setPrice(priceTnd);
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
        List<Seance> allSessions = filterVisibleSessions(seanceService.getAll());
        long publishedCount = allSessions.stream().filter(seance -> seance.getStatus() == 1).count();
        long draftCount = allSessions.stream().filter(seance -> seance.getStatus() == 0).count();

        publishedSessionsCountLabel.setText(String.valueOf(publishedCount));
        draftSessionsCountLabel.setText(String.valueOf(draftCount));
        applySessionFilters();
    }

    private void applySessionFilters() {
        recentSessionsContainer.getChildren().clear();
        List<Seance> filteredSessions = filterVisibleSessions(seanceService.search(
            sessionSearchField.getText(),
            sessionSearchModeComboBox.getValue(),
            0
        ));
        boolean hasSessionsInDatabase = !filterVisibleSessions(seanceService.getAll()).isEmpty();

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
                + " | " + formatSessionPrice(seance.getPrice())
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

        actionRow.getChildren().add(actionSpacer);
        if (canReserveAsParticipant(seance)) {
            actionRow.getChildren().add(reserveButton);
        }
        actionRow.getChildren().add(detailsButton);
        if (canManageSession(seance)) {
            actionRow.getChildren().addAll(editButton, deleteButton);
        }

        card.getChildren().addAll(headerRow, metaLabel, descriptionLabel);
        card.getChildren().add(actionRow);
        configureSessionCardDoubleClick(card, seance, reservationStats);
        return card;
    }

    private void openMatchingWorkspace() {
        if (UserSession.isCurrentStudent()) {
            openStudentMatchingDialog();
            return;
        }
        if (UserSession.isCurrentTutor()) {
            openTutorMatchingDialog();
            return;
        }

        showAvailableSessionsSection();
        showReservationActionFeedback(
            "Connectez-vous avec un compte etudiant ou tuteur pour utiliser le matching.",
            false
        );
    }

    private void openStudentMatchingDialog() {
        Dialog<ButtonType> matchingDialog = new Dialog<>();
        DialogPane dialogPane = matchingDialog.getDialogPane();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);

        matchingDialog.setTitle("Matching swipe");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setPrefWidth(780);
        dialogPane.setPrefHeight(620);
        dialogPane.setContent(buildStudentMatchingDialogContent());
        applyCurrentTheme(dialogPane);
        appendDialogStylesheet(dialogPane, MATCHING_DIALOG_STYLESHEET);
        matchingDialog.showAndWait();
    }

    private ScrollPane buildStudentMatchingDialogContent() {
        StudentMatchingDialogState state = new StudentMatchingDialogState();

        VBox root = new VBox(16.0);
        root.setPadding(new Insets(18.0));
        root.getStyleClass().add("matching-dialog-root");

        ScrollPane scrollPane = new ScrollPane();

        ComboBox<String> subjectComboBox = new ComboBox<>();
        subjectComboBox.setEditable(true);
        subjectComboBox.setPromptText("Matiere visee");
        subjectComboBox.getItems().setAll(
            seanceService.getSubjects().stream()
                .filter(Objects::nonNull)
                .filter(subject -> !seanceService.getDefaultSubject().equals(subject))
                .toList()
        );
        subjectComboBox.getStyleClass().add("filter-combo");

        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1));
        datePicker.setPromptText("Choisir une date");

        ComboBox<String> hourComboBox = new ComboBox<>();
        hourComboBox.getItems().setAll(HOUR_OPTIONS);
        hourComboBox.setValue("18");
        hourComboBox.setPrefWidth(110.0);
        hourComboBox.getStyleClass().add("filter-combo");

        ComboBox<String> minuteComboBox = new ComboBox<>();
        minuteComboBox.getItems().setAll(MINUTE_OPTIONS);
        minuteComboBox.setValue("00");
        minuteComboBox.setPrefWidth(110.0);
        minuteComboBox.getStyleClass().add("filter-combo");

        TextField durationField = new TextField("60");
        durationField.setPromptText("Duree en minutes");
        durationField.getStyleClass().add("form-field");

        ComboBox<String> modeComboBox = new ComboBox<>();
        modeComboBox.getItems().setAll(MODE_ONLINE_LABEL, MODE_ONSITE_LABEL);
        modeComboBox.setValue(MODE_ONLINE_LABEL);
        modeComboBox.getStyleClass().add("filter-combo");

        ComboBox<String> visibilityComboBox = new ComboBox<>();
        visibilityComboBox.getItems().setAll("Seance individuelle", "Seance ouverte");
        visibilityComboBox.setValue("Seance individuelle");
        visibilityComboBox.getStyleClass().add("filter-combo");

        TextArea objectiveArea = new TextArea();
        objectiveArea.setPromptText(
            "Ex: J'ai besoin d'une revision en Java sur les exceptions avec des exercices pratiques."
        );
        objectiveArea.setWrapText(true);
        objectiveArea.setPrefRowCount(4);
        objectiveArea.getStyleClass().add("text-area");

        VBox formCard = new VBox(12.0);
        formCard.getStyleClass().addAll("reservation-form-shell", "matching-dialog-panel", "matching-stage-panel");
        formCard.getChildren().addAll(
            buildMatchingFieldBlock("Matiere souhaitee", subjectComboBox),
            buildMatchingDateTimeRow(datePicker, hourComboBox, minuteComboBox, durationField),
            buildMatchingTwinFieldRow(
                buildMatchingFieldBlock("Format souhaite", modeComboBox),
                buildMatchingFieldBlock("Type de seance", visibilityComboBox)
            ),
            buildMatchingFieldBlock("Decris ton besoin", objectiveArea)
        );

        Label feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.getStyleClass().add("frontoffice-feedback");
        setInlineFeedback(feedbackLabel, null, false);

        Label eyebrowLabel = new Label("MATCHING SWIPE");
        eyebrowLabel.getStyleClass().add("workspace-eyebrow");

        Label titleLabel = new Label("Prepare ta demande de matching");
        titleLabel.getStyleClass().add("workspace-title");
        titleLabel.setWrapText(true);

        Label introLabel = new Label(
            "Choisis la matiere, le creneau et le format, puis laisse le swipe composer une short-list de tuteurs compatibles."
        );
        introLabel.setWrapText(true);
        introLabel.getStyleClass().add("reservation-section-copy");

        Button launchButton = new Button("Lancer le swipe");
        launchButton.getStyleClass().add("backoffice-primary-button");

        Label formHintLabel = new Label(
            "Prepare la demande, puis laisse le moteur composer un deck de tuteurs compatibles a swiper."
        );
        formHintLabel.setWrapText(true);
        formHintLabel.getStyleClass().addAll("reservation-section-copy", "matching-dialog-hint");

        HBox formActionRow = new HBox(12.0);
        formActionRow.setAlignment(Pos.CENTER_LEFT);
        formActionRow.getChildren().addAll(launchButton, buildChoiceChip("Deck intelligent"));

        VBox formView = new VBox(16.0);
        formView.getStyleClass().add("matching-stage-shell");
        formView.setManaged(false);
        formView.setVisible(false);
        formView.setMaxWidth(Double.MAX_VALUE);
        formView.getChildren().addAll(
            eyebrowLabel,
            titleLabel,
            introLabel,
            formCard,
            formHintLabel,
            formActionRow
        );

        VBox resultSummaryCard = new VBox(10.0);
        resultSummaryCard.getStyleClass().addAll("reservation-form-shell", "matching-dialog-panel", "matching-results-panel");

        Label resultKickerLabel = new Label("TUTEUR SUGGERE");
        resultKickerLabel.getStyleClass().add("workspace-eyebrow");

        Label resultTitleLabel = new Label("Tuteur compatible pret a valider");
        resultTitleLabel.getStyleClass().add("workspace-title");
        resultTitleLabel.setWrapText(true);

        Label resultSummaryLabel = new Label(
            "Le matching a retenu le profil le plus pertinent pour ce besoin."
        );
        resultSummaryLabel.setWrapText(true);
        resultSummaryLabel.getStyleClass().add("reservation-section-copy");

        FlowPane resultSignalRow = new FlowPane(10.0, 10.0);

        Button editRequestButton = new Button("Modifier la demande");
        editRequestButton.getStyleClass().add("backoffice-secondary-button");

        Label progressLabel = new Label("Aucune carte de matching n'est encore affichee.");
        progressLabel.getStyleClass().addAll("reservation-section-copy", "matching-dialog-hint");

        Label swipeGuideLabel = new Label("Choisis simplement passer, interesser ou super match.");
        swipeGuideLabel.setWrapText(true);
        swipeGuideLabel.getStyleClass().addAll("reservation-section-copy", "matching-swipe-guide");

        HBox resultToolbar = new HBox(12.0);
        resultToolbar.setAlignment(Pos.CENTER_LEFT);
        resultToolbar.getStyleClass().add("matching-results-toolbar");
        resultToolbar.getChildren().addAll(editRequestButton, progressLabel);

        VBox quickActionPanel = new VBox(10.0);
        quickActionPanel.getStyleClass().addAll("matching-dialog-panel", "matching-quick-actions-panel");
        quickActionPanel.setManaged(false);
        quickActionPanel.setVisible(false);

        Label quickActionTitle = new Label("Action sur ce tuteur");
        quickActionTitle.getStyleClass().add("matching-quick-actions-title");

        HBox quickActionRow = new HBox(10.0);
        quickActionRow.getStyleClass().addAll("matching-action-row", "matching-quick-actions-row");

        Button quickPassButton = new Button("Passer");
        quickPassButton.getStyleClass().addAll(
            "backoffice-secondary-button",
            "matching-action-button",
            "matching-action-pass",
            "matching-quick-action-button"
        );

        Button quickInterestedButton = new Button("Interesse");
        quickInterestedButton.getStyleClass().addAll(
            "backoffice-primary-button",
            "matching-action-button",
            "matching-action-like",
            "matching-quick-action-button"
        );

        Button quickSuperMatchButton = new Button("Super match");
        quickSuperMatchButton.getStyleClass().addAll(
            "backoffice-edit-button",
            "matching-action-button",
            "matching-action-super",
            "matching-quick-action-button"
        );

        HBox.setHgrow(quickPassButton, Priority.ALWAYS);
        HBox.setHgrow(quickInterestedButton, Priority.ALWAYS);
        HBox.setHgrow(quickSuperMatchButton, Priority.ALWAYS);
        quickPassButton.setMaxWidth(Double.MAX_VALUE);
        quickInterestedButton.setMaxWidth(Double.MAX_VALUE);
        quickSuperMatchButton.setMaxWidth(Double.MAX_VALUE);

        quickActionRow.getChildren().addAll(quickPassButton, quickInterestedButton, quickSuperMatchButton);
        quickActionPanel.getChildren().addAll(quickActionTitle, quickActionRow);

        StackPane cardHost = new StackPane();
        cardHost.getStyleClass().add("matching-card-host");

        VBox cardStage = new VBox(cardHost);
        cardStage.getStyleClass().add("matching-card-stage");

        VBox resultsView = new VBox(14.0);
        resultsView.getStyleClass().add("matching-results-shell");
        resultsView.setManaged(false);
        resultsView.setVisible(false);
        resultsView.setMaxWidth(Double.MAX_VALUE);
        resultsView.getChildren().addAll(
            resultSummaryCard,
            resultToolbar,
            swipeGuideLabel,
            quickActionPanel,
            cardStage
        );

        resultSummaryCard.getChildren().addAll(
            resultKickerLabel,
            resultTitleLabel,
            resultSummaryLabel,
            resultSignalRow
        );

        launchButton.setOnAction(event -> {
            try {
                MatchingDraft draft = buildStudentMatchingDraft(
                    subjectComboBox,
                    datePicker,
                    hourComboBox,
                    minuteComboBox,
                    durationField,
                    modeComboBox,
                    visibilityComboBox,
                    objectiveArea
                );
                MatchingRequestCreation creation = matchingService.createMatchingRequest(draft);
                if (!creation.success()) {
                    setInlineFeedback(feedbackLabel, creation.message(), false);
                    return;
                }

                state.requestId = creation.requestId();
                state.cards = new ArrayList<>(creation.candidates());
                state.currentIndex = 0;
                state.positiveMatches.clear();
                state.needProfile = creation.needProfile();

                updateStudentMatchingResultsSummary(
                    draft,
                    creation,
                    resultTitleLabel,
                    resultSummaryLabel,
                    resultSignalRow
                );
                setInlineFeedback(feedbackLabel, null, false);
                refreshStudentMatchCard(
                    cardHost,
                    resultSummaryCard,
                    resultToolbar,
                    progressLabel,
                    feedbackLabel,
                    swipeGuideLabel,
                    quickActionPanel,
                    quickActionTitle,
                    quickPassButton,
                    quickInterestedButton,
                    quickSuperMatchButton,
                    state
                );
                animateMatchingWorkspaceSwap(formView, resultsView);
            } catch (IllegalArgumentException exception) {
                setInlineFeedback(feedbackLabel, exception.getMessage(), false);
            }
        });

        quickPassButton.setOnAction(event -> handleCurrentStudentMatchingAction(
            MatchingService.DECISION_PASS,
            state,
            cardHost,
            resultSummaryCard,
            resultToolbar,
            progressLabel,
            feedbackLabel,
            swipeGuideLabel,
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton
        ));
        quickInterestedButton.setOnAction(event -> handleCurrentStudentMatchingAction(
            MatchingService.DECISION_INTERESTED,
            state,
            cardHost,
            resultSummaryCard,
            resultToolbar,
            progressLabel,
            feedbackLabel,
            swipeGuideLabel,
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton
        ));
        quickSuperMatchButton.setOnAction(event -> handleCurrentStudentMatchingAction(
            MatchingService.DECISION_SUPER,
            state,
            cardHost,
            resultSummaryCard,
            resultToolbar,
            progressLabel,
            feedbackLabel,
            swipeGuideLabel,
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton
        ));

        VBox landingView = new VBox();
        landingView.getStyleClass().add("matching-landing-shell");
        landingView.setFillWidth(true);
        landingView.setAlignment(Pos.CENTER);
        landingView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox landingHero = buildStudentMatchingLandingHero(subjectComboBox.getItems(), () -> {
            setInlineFeedback(feedbackLabel, null, false);
            animateMatchingWorkspaceSwap(landingView, formView);
            Platform.runLater(() -> {
                scrollPane.setVvalue(scrollPane.getVmin());
                subjectComboBox.requestFocus();
            });
        }, subjectComboBox);
        landingHero.setMaxWidth(Double.MAX_VALUE);
        landingHero.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(landingHero, Priority.ALWAYS);
        landingView.getChildren().add(landingHero);

        editRequestButton.setOnAction(event -> {
            setInlineFeedback(feedbackLabel, null, false);
            animateMatchingWorkspaceSwap(resultsView, formView);
            Platform.runLater(() -> scrollPane.setVvalue(scrollPane.getVmin()));
        });

        StackPane workspaceHost = new StackPane(landingView, formView, resultsView);
        workspaceHost.getStyleClass().add("matching-workspace-host");
        workspaceHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(workspaceHost, Priority.ALWAYS);
        root.setFillWidth(true);
        root.getChildren().addAll(feedbackLabel, workspaceHost);

        scrollPane.setContent(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("matching-scroll");
        scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            double viewportHeight = Math.max(0.0, newBounds.getHeight());
            double landingHeight = Math.max(320.0, viewportHeight - 36.0);
            landingView.setMinHeight(landingHeight);
            landingHero.setMinHeight(landingHeight);
        });
        return scrollPane;
    }

    private VBox buildStudentMatchingLandingHero(List<String> subjectSamples, Runnable startAction, Node focusTarget) {
        VBox hero = new VBox(16.0);
        hero.getStyleClass().add("matching-showcase-card");

        StackPane visualStage = new StackPane(buildStudentMatchingShowcaseOrbit(subjectSamples));
        visualStage.getStyleClass().add("matching-showcase-stage");

        Label eyebrowLabel = new Label("MATCH, SWIPE, PLANIFIE");
        eyebrowLabel.getStyleClass().add("matching-showcase-eyebrow");

        Label titleLabel = new Label("Trouve ton\nmatch tuteur");
        titleLabel.getStyleClass().add("matching-showcase-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(
            "Decris ton besoin, vise un creneau et laisse le matching rapprocher les profils qui collent vraiment."
        );
        subtitleLabel.getStyleClass().add("matching-showcase-subtitle");
        subtitleLabel.setWrapText(true);

        Button ctaButton = new Button("Commencer le matching");
        ctaButton.getStyleClass().addAll("backoffice-primary-button", "matching-showcase-button");
        ctaButton.setOnAction(event -> {
            if (startAction != null) {
                startAction.run();
            } else if (focusTarget != null) {
                focusTarget.requestFocus();
            }
        });

        FlowPane chipRow = new FlowPane(8.0, 8.0);
        chipRow.getStyleClass().add("matching-showcase-chip-row");
        chipRow.getChildren().addAll(
            buildMatchingShowcaseChip("Swipe intuitif"),
            buildMatchingShowcaseChip("Profils verifies"),
            buildMatchingShowcaseChip("Creneau cible")
        );

        VBox copyBlock = new VBox(14.0);
        copyBlock.getStyleClass().add("matching-showcase-copy");
        copyBlock.getChildren().addAll(eyebrowLabel, titleLabel, subtitleLabel, ctaButton);
        HBox.setHgrow(copyBlock, Priority.ALWAYS);

        HBox heroBody = new HBox(22.0);
        heroBody.setAlignment(Pos.CENTER_LEFT);
        heroBody.getStyleClass().add("matching-showcase-body");
        heroBody.getChildren().addAll(copyBlock, visualStage);

        hero.getChildren().addAll(heroBody, chipRow);
        return hero;
    }

    private Label buildMatchingShowcaseChip(String text) {
        Label chip = new Label(safeText(text));
        chip.getStyleClass().add("matching-showcase-chip");
        return chip;
    }

    private Pane buildStudentMatchingShowcaseOrbit(List<String> subjectSamples) {
        List<String> showcaseLabels = resolveMatchingShowcaseLabels(subjectSamples);

        Pane orbitPane = new Pane();
        orbitPane.setMinSize(248.0, 194.0);
        orbitPane.setPrefSize(248.0, 194.0);
        orbitPane.setMaxSize(248.0, 194.0);
        orbitPane.getStyleClass().add("matching-showcase-orbit-pane");

        orbitPane.getChildren().addAll(
            buildMatchingShowcaseOrbit(124.0, 96.0, 30.0, 0.88),
            buildMatchingShowcaseOrbit(124.0, 96.0, 56.0, 0.62),
            buildMatchingShowcaseOrbit(124.0, 96.0, 82.0, 0.42)
        );

        StackPane centerCore = new StackPane();
        centerCore.getStyleClass().add("matching-showcase-heart-core");
        centerCore.setPrefSize(52.0, 52.0);
        centerCore.relocate(98.0, 70.0);
        Label heartLabel = new Label("❤");
        heartLabel.getStyleClass().add("matching-showcase-heart-text");
        heartLabel.setText("\uD83D\uDCD6");
        centerCore.getChildren().add(heartLabel);

        StackPane avatarOne = buildMatchingShowcaseAvatar(showcaseLabels.get(0), "matching-showcase-avatar-coral", 46.0);
        avatarOne.relocate(101.0, 6.0);

        StackPane avatarTwo = buildMatchingShowcaseAvatar(showcaseLabels.get(1), "matching-showcase-avatar-peach", 50.0);
        avatarTwo.relocate(26.0, 52.0);

        StackPane avatarThree = buildMatchingShowcaseAvatar(showcaseLabels.get(2), "matching-showcase-avatar-navy", 58.0);
        avatarThree.relocate(166.0, 46.0);

        StackPane avatarFour = buildMatchingShowcaseAvatar(showcaseLabels.get(3), "matching-showcase-avatar-sunset", 46.0);
        avatarFour.relocate(58.0, 136.0);

        StackPane avatarFive = buildMatchingShowcaseAvatar(showcaseLabels.get(4), "matching-showcase-avatar-rose", 40.0);
        avatarFive.relocate(186.0, 140.0);

        Label bubbleOne = buildMatchingShowcaseBubble("❤");
        bubbleOne.relocate(8.0, 110.0);

        Label bubbleTwo = buildMatchingShowcaseBubble("❤");
        bubbleTwo.relocate(206.0, 16.0);

        Label bubbleThree = buildMatchingShowcaseBubble("❤");
        bubbleThree.relocate(206.0, 128.0);

        orbitPane.getChildren().addAll(
            centerCore,
            avatarOne,
            avatarTwo,
            avatarThree,
            avatarFour,
            avatarFive,
            bubbleOne,
            bubbleTwo,
            bubbleThree
        );
        return orbitPane;
    }

    private Circle buildMatchingShowcaseOrbit(double centerX, double centerY, double radius, double opacity) {
        Circle orbit = new Circle(centerX, centerY, radius);
        orbit.setFill(Color.TRANSPARENT);
        orbit.getStyleClass().add("matching-showcase-orbit");
        orbit.setOpacity(opacity);
        orbit.getStrokeDashArray().setAll(7.0, 10.0);
        return orbit;
    }

    private StackPane buildMatchingShowcaseAvatar(String label, String toneClass, double size) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("matching-showcase-avatar");
        if (toneClass != null && !toneClass.isBlank()) {
            avatar.getStyleClass().add(toneClass);
        }
        avatar.setPrefSize(size, size);
        avatar.setMinSize(size, size);
        avatar.setMaxSize(size, size);

        Label initialsLabel = new Label(resolveMatchingInitials(label));
        initialsLabel.getStyleClass().add("matching-showcase-avatar-text");
        avatar.getChildren().add(initialsLabel);
        return avatar;
    }

    private Label buildMatchingShowcaseBubble(String text) {
        Label bubble = new Label("\uD83D\uDCD8");
        bubble.getStyleClass().add("matching-showcase-bubble");
        return bubble;
    }

    private List<String> resolveMatchingShowcaseLabels(List<String> subjectSamples) {
        List<String> normalizedLabels = subjectSamples == null
            ? new ArrayList<>()
            : subjectSamples.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> fallbacks = List.of("Java", "Algo", "Web", "Data", "Math");
        for (String fallback : fallbacks) {
            if (normalizedLabels.size() >= 5) {
                break;
            }
            if (!normalizedLabels.contains(fallback)) {
                normalizedLabels.add(fallback);
            }
        }
        return normalizedLabels;
    }

    private MatchingDraft buildStudentMatchingDraft(ComboBox<String> subjectComboBox,
                                                    DatePicker datePicker,
                                                    ComboBox<String> hourComboBox,
                                                    ComboBox<String> minuteComboBox,
                                                    TextField durationField,
                                                    ComboBox<String> modeComboBox,
                                                    ComboBox<String> visibilityComboBox,
                                                    TextArea objectiveArea) {
        String rawSubject = subjectComboBox.getEditor() != null
            ? subjectComboBox.getEditor().getText()
            : subjectComboBox.getValue();
        if ((rawSubject == null || rawSubject.isBlank()) && subjectComboBox.getValue() != null) {
            rawSubject = subjectComboBox.getValue();
        }

        String subject = requireText(rawSubject, "Renseignez la matiere recherchee.");
        if (datePicker.getValue() == null) {
            throw new IllegalArgumentException("Choisissez la date souhaitee.");
        }

        String hour = requireText(hourComboBox.getValue(), "Choisissez l'heure souhaitee.");
        String minute = requireText(minuteComboBox.getValue(), "Choisissez la minute souhaitee.");
        int duration = parseBoundedInt(
            durationField.getText(),
            SeanceService.MIN_DURATION_MINUTES,
            SeanceService.MAX_DURATION_MINUTES,
            "La duree du matching doit etre comprise entre " + SeanceService.MIN_DURATION_MINUTES
                + " et " + SeanceService.MAX_DURATION_MINUTES + " minutes."
        );

        LocalDateTime startAt = LocalDateTime.of(
            datePicker.getValue(),
            LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute))
        );
        if (startAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Le matching doit viser un creneau futur.");
        }

        String mode = MODE_ONSITE_LABEL.equals(modeComboBox.getValue()) ? Seance.MODE_ONSITE : Seance.MODE_ONLINE;
        String visibility = visibilityComboBox.getValue() != null && visibilityComboBox.getValue().toLowerCase().contains("individ")
            ? MatchingService.VISIBILITY_PRIVATE
            : MatchingService.VISIBILITY_PUBLIC;

        return new MatchingDraft(
            getCurrentReservationParticipantId(),
            subject,
            startAt,
            duration,
            mode,
            visibility,
            objectiveArea == null ? null : objectiveArea.getText()
        );
    }

    private void refreshStudentMatchCard(Pane cardHost,
                                         Node resultSummaryCard,
                                         Node resultToolbar,
                                         Label progressLabel,
                                         Label feedbackLabel,
                                         Label swipeGuideLabel,
                                         Pane quickActionPanel,
                                         Label quickActionTitle,
                                         Button quickPassButton,
                                         Button quickInterestedButton,
                                         Button quickSuperMatchButton,
                                         StudentMatchingDialogState state) {
        updateStudentMatchingResultsChrome(resultSummaryCard, resultToolbar, swipeGuideLabel, feedbackLabel);
        cardHost.getChildren().clear();
        if (state.cards.isEmpty()) {
            progressLabel.setText("Aucun tuteur compatible n'a ete trouve.");
            swipeGuideLabel.setText("Modifie le besoin puis relance le matching pour obtenir une nouvelle proposition.");
            updateStudentQuickActionBar(
                quickActionPanel,
                quickActionTitle,
                quickPassButton,
                quickInterestedButton,
                quickSuperMatchButton,
                null,
                false
            );
            VBox emptyCard = buildStudentMatchingStateCard(
                "Aucun profil disponible",
                "Aucun profil swipeable n'est disponible sur ce creneau. Tu peux changer la date, la duree ou la matiere.",
                "matching-state-card"
            );
            cardHost.getChildren().add(emptyCard);
            return;
        }

        if (state.currentIndex >= state.cards.size()) {
            int positiveChoices = state.positiveMatches.size();
            progressLabel.setText(
                positiveChoices > 0
                    ? "Matching termine. 1 tuteur retenu."
                    : "Aucun tuteur retenu."
            );
            swipeGuideLabel.setText(
                positiveChoices > 0
                    ? "Le tuteur retenu verra maintenant ta demande dans son inbox."
                    : "Aucun tuteur compatible n'a ete retenu pour ce besoin."
            );
            updateStudentQuickActionBar(
                quickActionPanel,
                quickActionTitle,
                quickPassButton,
                quickInterestedButton,
                quickSuperMatchButton,
                null,
                false
            );
            VBox doneCard = buildStudentMatchingCompletionView(state);
            cardHost.getChildren().add(doneCard);
            animateMatchingCardEntrance(doneCard);
            return;
        }

        StudentMatchCard currentCard = state.cards.get(state.currentIndex);
        progressLabel.setText("Tuteur propose");
        swipeGuideLabel.setText("Valide ce tuteur, ou passe si le profil ne te convient pas.");
        updateStudentQuickActionBar(
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton,
            currentCard,
            !state.animating
        );
        StackPane swipeCard = buildStudentMatchCard(
            currentCard,
            state,
            cardHost,
            resultSummaryCard,
            resultToolbar,
            progressLabel,
            feedbackLabel,
            swipeGuideLabel,
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton
        );
        cardHost.getChildren().add(swipeCard);
        animateMatchingCardEntrance(swipeCard);
    }

    private StackPane buildStudentMatchCard(StudentMatchCard card,
                                            StudentMatchingDialogState state,
                                            Pane cardHost,
                                            Node resultSummaryCard,
                                            Node resultToolbar,
                                            Label progressLabel,
                                            Label feedbackLabel,
                                            Label swipeGuideLabel,
                                            Pane quickActionPanel,
                                            Label quickActionTitle,
                                            Button quickPassButton,
                                            Button quickInterestedButton,
                                            Button quickSuperMatchButton) {
        VBox cardBox = new VBox(14.0);
        cardBox.getStyleClass().addAll("reservation-form-shell", "matching-swipe-card");

        VBox heroCard = new VBox(12.0);
        heroCard.getStyleClass().addAll("matching-hero-card", "matching-hero-card-student");

        HBox titleRow = new HBox(14.0);
        titleRow.getStyleClass().add("matching-hero-header");

        StackPane avatarBadge = buildMatchingAvatarBadge(card.tutorName(), "matching-avatar-badge-student");

        VBox identityBox = new VBox(4.0);
        identityBox.getStyleClass().add("matching-hero-content");

        Label eyebrowLabel = new Label("PROFIL COMPATIBLE");
        eyebrowLabel.getStyleClass().add("matching-hero-eyebrow");

        Label tutorNameLabel = new Label(card.tutorName());
        tutorNameLabel.getStyleClass().addAll("subsection-title", "matching-hero-title");

        Label heroSubtitleLabel = new Label(buildStudentMatchingHeroSubtitle(card, state.needProfile));
        heroSubtitleLabel.setWrapText(true);
        heroSubtitleLabel.getStyleClass().add("matching-hero-subtitle");
        identityBox.getChildren().addAll(eyebrowLabel, tutorNameLabel, heroSubtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(
            avatarBadge,
            identityBox,
            spacer,
            buildMatchingScoreBox(card.score(), resolveMatchingConfidenceLabel(card.score()))
        );

        FlowPane heroChipRow = new FlowPane(8.0, 8.0);
        heroChipRow.getStyleClass().add("matching-hero-chip-row");
        heroChipRow.getChildren().addAll(
            buildChoiceChip(resolveMatchingConfidenceLabel(card.score())),
            buildChoiceChipHighlight(Math.round(card.score()) + "/100"),
            buildChoiceChip(card.matchingModeSessions() > 0 ? "Format coherent" : "Format compatible")
        );

        FlowPane metricRow = new FlowPane(10.0, 10.0);
        metricRow.getStyleClass().add("matching-metric-row");
        metricRow.getChildren().addAll(
            buildMatchingMetricCard(String.valueOf(card.subjectSessions()), "Seances matiere"),
            buildMatchingMetricCard(String.valueOf(card.totalSessions()), "Seances total"),
            buildMatchingMetricCard(String.valueOf(card.acceptedReservations()), "Reservations OK")
        );
        heroCard.getChildren().addAll(titleRow, heroChipRow, metricRow);

        VBox spotlightPanel = buildMatchingSpotlightPanel(
            "Resume rapide",
            buildStudentMatchingSpotlight(card, state.needProfile),
            "matching-spotlight-panel-student"
        );

        FlowPane signalsRow = new FlowPane(10.0, 10.0);
        if (state.needProfile != null
            && state.needProfile.level() != null
            && !state.needProfile.level().isBlank()
            && !"Niveau non precise".equalsIgnoreCase(state.needProfile.level())) {
            signalsRow.getChildren().add(buildChoiceChip(state.needProfile.level()));
        }
        if (state.needProfile != null && state.needProfile.keywords() != null) {
            state.needProfile.keywords().stream()
                .limit(2)
                .map(keyword -> buildChoiceChipHighlight("Focus " + keyword))
                .forEach(signalsRow.getChildren()::add);
        }
        if (card.matchingModeSessions() > 0) {
            signalsRow.getChildren().add(buildChoiceChip("Format deja pratique"));
        }
        card.signals().stream()
            .limit(2)
            .map(this::buildChoiceChip)
            .forEach(signalsRow.getChildren()::add);
        if (signalsRow.getChildren().isEmpty()) {
            signalsRow.getChildren().add(buildChoiceChip("Profil compatible"));
        }

        Label decisionBadge = new Label("Swipe");
        decisionBadge.getStyleClass().addAll("matching-decision-badge", "matching-decision-neutral");
        decisionBadge.setManaged(false);
        decisionBadge.setVisible(false);

        StackPane swipeSurface = new StackPane(cardBox, decisionBadge);
        swipeSurface.getStyleClass().add("matching-swipe-surface");
        StackPane.setAlignment(decisionBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(decisionBadge, new Insets(14.0, 18.0, 0.0, 0.0));

        cardBox.getChildren().addAll(
            heroCard,
            spotlightPanel,
            signalsRow
        );
        installStudentSwipeInteractions(
            swipeSurface,
            card,
            state,
            cardHost,
            resultSummaryCard,
            resultToolbar,
            progressLabel,
            feedbackLabel,
            swipeGuideLabel,
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton,
            decisionBadge
        );
        return swipeSurface;
    }

    private void handleCurrentStudentMatchingAction(int decision,
                                                    StudentMatchingDialogState state,
                                                    Pane cardHost,
                                                    Node resultSummaryCard,
                                                    Node resultToolbar,
                                                    Label progressLabel,
                                                    Label feedbackLabel,
                                                    Label swipeGuideLabel,
                                                    Pane quickActionPanel,
                                                    Label quickActionTitle,
                                                    Button quickPassButton,
                                                    Button quickInterestedButton,
                                                    Button quickSuperMatchButton) {
        if (state == null || state.animating || state.cards == null) {
            return;
        }
        if (state.currentIndex < 0 || state.currentIndex >= state.cards.size()) {
            updateStudentQuickActionBar(
                quickActionPanel,
                quickActionTitle,
                quickPassButton,
                quickInterestedButton,
                quickSuperMatchButton,
                null,
                false
            );
            return;
        }

        StudentMatchCard currentCard = state.cards.get(state.currentIndex);
        handleStudentSwipeDecision(
            decision,
            currentCard,
            state,
            cardHost,
            resultSummaryCard,
            resultToolbar,
            progressLabel,
            feedbackLabel,
            swipeGuideLabel,
            quickActionPanel,
            quickActionTitle,
            quickPassButton,
            quickInterestedButton,
            quickSuperMatchButton,
            null,
            null
        );
    }

    private void updateStudentQuickActionBar(Pane quickActionPanel,
                                             Label quickActionTitle,
                                             Button quickPassButton,
                                             Button quickInterestedButton,
                                             Button quickSuperMatchButton,
                                             StudentMatchCard currentCard,
                                             boolean enabled) {
        boolean visible = quickActionPanel != null && currentCard != null;
        setNodeVisible(quickActionPanel, visible);
        if (quickActionTitle != null) {
            quickActionTitle.setText(
                visible
                    ? "Valider le profil de " + currentCard.tutorName()
                    : "Action sur ce tuteur"
            );
        }

        boolean disableButtons = !visible || !enabled;
        if (quickPassButton != null) {
            quickPassButton.setDisable(disableButtons);
        }
        if (quickInterestedButton != null) {
            quickInterestedButton.setDisable(disableButtons);
        }
        if (quickSuperMatchButton != null) {
            quickSuperMatchButton.setDisable(disableButtons);
        }
    }

    private void openTutorMatchingDialog() {
        Dialog<ButtonType> matchingDialog = new Dialog<>();
        DialogPane dialogPane = matchingDialog.getDialogPane();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);

        matchingDialog.setTitle("Demandes de matching");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setPrefWidth(940);
        dialogPane.setPrefHeight(760);
        dialogPane.setContent(buildTutorMatchingDialogContent(matchingDialog));
        applyCurrentTheme(dialogPane);
        appendDialogStylesheet(dialogPane, MATCHING_DIALOG_STYLESHEET);
        matchingDialog.showAndWait();
    }

    private ScrollPane buildTutorMatchingDialogContent(Dialog<ButtonType> matchingDialog) {
        TutorMatchingDialogState state = new TutorMatchingDialogState();
        state.cards = new ArrayList<>(matchingService.getTutorPendingMatches(getCurrentTutorId()));

        VBox root = new VBox(16.0);
        root.setPadding(new Insets(18.0));
        root.getStyleClass().add("matching-dialog-root");

        Label eyebrowLabel = new Label("INBOX TUTEUR");
        eyebrowLabel.getStyleClass().add("workspace-eyebrow");

        Label titleLabel = new Label("Demandes de matching pre-qualifiees");
        titleLabel.getStyleClass().add("workspace-title");
        titleLabel.setWrapText(true);

        Label introLabel = new Label(
            "Chaque carte provient d'un etudiant qui a deja swipe ce profil. "
                + "Accepte pour passer directement a la planification, ou refuse pour laisser tourner l'inbox."
        );
        introLabel.setWrapText(true);
        introLabel.getStyleClass().add("reservation-section-copy");

        Label progressLabel = new Label();
        progressLabel.getStyleClass().addAll("reservation-section-copy", "matching-dialog-hint");

        Label feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.getStyleClass().add("frontoffice-feedback");
        setInlineFeedback(feedbackLabel, null, false);

        VBox cardHost = new VBox(12.0);
        cardHost.getStyleClass().add("matching-card-host");

        refreshTutorMatchCard(cardHost, progressLabel, feedbackLabel, state, matchingDialog);

        root.getChildren().addAll(eyebrowLabel, titleLabel, introLabel, progressLabel, feedbackLabel, cardHost);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("matching-scroll");
        return scrollPane;
    }

    private void refreshTutorMatchCard(VBox cardHost,
                                       Label progressLabel,
                                       Label feedbackLabel,
                                       TutorMatchingDialogState state,
                                       Dialog<ButtonType> matchingDialog) {
        cardHost.getChildren().clear();
        if (state.cards.isEmpty()) {
            progressLabel.setText("Aucune demande de matching n'est en attente.");
            Label emptyLabel = new Label(
                "Les prochains swipes positifs des etudiants apparaitront ici des qu'un besoin compatible sera detecte."
            );
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            cardHost.getChildren().add(emptyLabel);
            return;
        }

        if (state.currentIndex >= state.cards.size()) {
            progressLabel.setText("Toutes les demandes de matching affichees ont ete traitees.");
            Label doneLabel = new Label("Reviens plus tard pour consulter les nouveaux profils compatibles.");
            doneLabel.setWrapText(true);
            doneLabel.getStyleClass().add("reservation-section-copy");
            cardHost.getChildren().add(doneLabel);
            return;
        }

        TutorMatchInboxItem item = state.cards.get(state.currentIndex);
        progressLabel.setText("Demande " + (state.currentIndex + 1) + " / " + state.cards.size());
        VBox tutorCard = buildTutorMatchCard(item, state, cardHost, progressLabel, feedbackLabel, matchingDialog);
        cardHost.getChildren().add(tutorCard);
        animateMatchingCardEntrance(tutorCard);
    }

    private VBox buildTutorMatchCard(TutorMatchInboxItem item,
                                     TutorMatchingDialogState state,
                                     VBox cardHost,
                                     Label progressLabel,
                                     Label feedbackLabel,
                                     Dialog<ButtonType> matchingDialog) {
        VBox card = new VBox(14.0);
        card.getStyleClass().addAll("reservation-form-shell", "matching-swipe-card");

        VBox heroCard = new VBox(12.0);
        heroCard.getStyleClass().addAll("matching-hero-card", "matching-hero-card-tutor");

        HBox titleRow = new HBox(14.0);
        titleRow.getStyleClass().add("matching-hero-header");

        StackPane avatarBadge = buildMatchingAvatarBadge(item.participantName(), "matching-avatar-badge-tutor");

        VBox identityBox = new VBox(4.0);
        identityBox.getStyleClass().add("matching-hero-content");

        Label eyebrowLabel = new Label("DEMANDE ETUDIANT");
        eyebrowLabel.getStyleClass().add("matching-hero-eyebrow");

        Label subjectLabel = new Label(safeText(item.subject()));
        subjectLabel.getStyleClass().addAll("subsection-title", "matching-hero-title");

        Label heroSubtitleLabel = new Label(buildTutorMatchingHeroSubtitle(item));
        heroSubtitleLabel.setWrapText(true);
        heroSubtitleLabel.getStyleClass().add("matching-hero-subtitle");
        identityBox.getChildren().addAll(eyebrowLabel, subjectLabel, heroSubtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(
            avatarBadge,
            identityBox,
            spacer,
            buildMatchingScoreBox(item.compatibilityScore(), resolveMatchingConfidenceLabel(item.compatibilityScore()))
        );

        FlowPane heroChipRow = new FlowPane(8.0, 8.0);
        heroChipRow.getStyleClass().add("matching-hero-chip-row");
        heroChipRow.getChildren().addAll(
            item.studentDecision() == MatchingService.DECISION_SUPER
                ? buildChoiceChipHighlight("Super match")
                : buildChoiceChip("Interesse"),
            buildChoiceChipHighlight(Math.round(item.compatibilityScore()) + "/100"),
            buildChoiceChip(formatMatchingVisibility(item.visibilityScope()))
        );

        FlowPane metricRow = new FlowPane(10.0, 10.0);
        metricRow.getStyleClass().add("matching-metric-row");
        metricRow.getChildren().addAll(
            buildMatchingMetricCard(item.durationMin() + " min", "Duree"),
            buildMatchingMetricCard(mapModeLabel(item.mode()), "Format"),
            buildMatchingMetricCard(resolveMatchingPriorityLabel(item.studentDecision()), "Priorite")
        );
        heroCard.getChildren().addAll(titleRow, heroChipRow, metricRow);

        Label metaLabel = new Label(
            "Etudiant: " + item.participantName()
                + " | " + formatDateTimeOrPlaceholder(item.requestedStartAt())
                + " | " + item.durationMin() + " min"
                + " | " + mapModeLabel(item.mode())
                + " | " + formatMatchingVisibility(item.visibilityScope())
        );
        metaLabel.setWrapText(true);
        metaLabel.getStyleClass().add("reservation-section-copy");

        VBox aiPanel = new VBox(8.0);
        aiPanel.getStyleClass().add("matching-ai-panel");

        Label aiKicker = new Label("Pourquoi cette demande vous correspond");
        aiKicker.getStyleClass().add("reservation-infrastructure-kicker");

        Label aiSummaryLabel = new Label(buildTutorMatchingAiExplanation(item));
        aiSummaryLabel.setWrapText(true);
        aiSummaryLabel.getStyleClass().addAll("reservation-section-copy", "matching-ai-copy");

        Label aiDecisionLabel = new Label(buildTutorMatchingActionHint(item));
        aiDecisionLabel.setWrapText(true);
        aiDecisionLabel.getStyleClass().addAll("reservation-section-copy", "matching-ai-secondary-copy");
        aiPanel.getChildren().addAll(aiKicker, aiSummaryLabel, aiDecisionLabel);

        VBox objectivePanel = buildMatchingSpotlightPanel(
            "Brief etudiant",
            buildTutorMatchingObjectivePreview(item),
            item.studentDecision() == MatchingService.DECISION_SUPER
                ? "matching-spotlight-panel-priority"
                : "matching-spotlight-panel-tutor"
        );

        Label summaryLabel = new Label(buildTutorMatchingRequestSummary(item));
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("reservation-section-copy");

        FlowPane keywordsRow = new FlowPane(10.0, 10.0);
        keywordsRow.getChildren().add(buildChoiceChip(resolveMatchingConfidenceLabel(item.compatibilityScore())));
        keywordsRow.getChildren().add(buildChoiceChip(item.requestedLevel()));
        item.keywords().stream()
            .limit(4)
            .map(this::buildChoiceChip)
            .forEach(keywordsRow.getChildren()::add);
        item.signals().stream()
            .limit(3)
            .map(this::buildChoiceChipHighlight)
            .forEach(keywordsRow.getChildren()::add);

        Label detailLabel = new Label(
            buildTutorMatchingRequestDetail(item)
        );
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);
        actionRow.getStyleClass().add("matching-action-row");
        Button refuseButton = new Button("Refuser");
        refuseButton.getStyleClass().addAll("backoffice-secondary-button", "matching-action-button", "matching-action-pass");
        refuseButton.setOnAction(event -> {
            OperationResult result = matchingService.refuseTutorMatch(item.candidateId(), getCurrentTutorId());
            setInlineFeedback(feedbackLabel, result.getMessage(), result.isSuccess());
            if (result.isSuccess()) {
                state.currentIndex++;
                refreshTutorMatchCard(cardHost, progressLabel, feedbackLabel, state, matchingDialog);
            }
        });

        Button acceptButton = new Button("Accepter et planifier");
        acceptButton.getStyleClass().addAll(
            "backoffice-primary-button",
            "matching-action-button",
            "matching-action-accept"
        );
        acceptButton.setOnAction(event -> {
            MatchingAcceptanceResult result = matchingService.acceptTutorMatch(item.candidateId(), getCurrentTutorId());
            setInlineFeedback(feedbackLabel, result.message(), result.success());
            if (!result.success() || result.planContext() == null) {
                return;
            }

            matchingDialog.close();
            prepareMatchingSessionPlanning(result.planContext());
        });

        actionRow.getChildren().addAll(refuseButton, acceptButton);
        card.getChildren().addAll(heroCard, metaLabel, objectivePanel, aiPanel, summaryLabel, keywordsRow, detailLabel, actionRow);
        return card;
    }

    private StackPane buildMatchingAvatarBadge(String sourceText, String toneClass) {
        StackPane badge = new StackPane();
        badge.getStyleClass().add("matching-avatar-badge");
        if (toneClass != null && !toneClass.isBlank()) {
            badge.getStyleClass().add(toneClass);
        }

        Label initialsLabel = new Label(resolveMatchingInitials(sourceText));
        initialsLabel.getStyleClass().add("matching-avatar-text");
        badge.getChildren().add(initialsLabel);
        return badge;
    }

    private VBox buildMatchingScoreBox(double score, String caption) {
        VBox scoreBox = new VBox(6.0);
        scoreBox.getStyleClass().add("matching-score-shell");

        Label scoreValueLabel = new Label(Math.round(score) + "/100");
        scoreValueLabel.getStyleClass().add("matching-score-value");

        Label captionLabel = new Label(safeText(caption));
        captionLabel.setWrapText(true);
        captionLabel.setMaxWidth(150.0);
        captionLabel.getStyleClass().add("matching-score-caption");

        ProgressBar scoreBar = new ProgressBar(Math.max(0.0, Math.min(1.0, score / 100.0)));
        scoreBar.setMaxWidth(Double.MAX_VALUE);
        scoreBar.getStyleClass().addAll("matching-score-progress", resolveMatchingScoreProgressStyle(score));

        scoreBox.getChildren().addAll(scoreValueLabel, captionLabel, scoreBar);
        return scoreBox;
    }

    private VBox buildMatchingMetricCard(String value, String label) {
        VBox metricCard = new VBox(4.0);
        metricCard.getStyleClass().add("matching-metric-card");
        metricCard.setMinWidth(118.0);
        metricCard.setPrefWidth(132.0);
        metricCard.setMaxWidth(Double.MAX_VALUE);

        Label valueLabel = new Label(safeText(value));
        valueLabel.setWrapText(true);
        valueLabel.getStyleClass().add("matching-metric-value");

        Label labelLabel = new Label(safeText(label));
        labelLabel.setWrapText(true);
        labelLabel.getStyleClass().add("matching-metric-label");

        metricCard.getChildren().addAll(valueLabel, labelLabel);
        return metricCard;
    }

    private VBox buildMatchingSpotlightPanel(String title, String body, String toneClass) {
        VBox panel = new VBox(6.0);
        panel.getStyleClass().add("matching-spotlight-panel");
        if (toneClass != null && !toneClass.isBlank()) {
            panel.getStyleClass().add(toneClass);
        }

        Label titleLabel = new Label(safeText(title));
        titleLabel.getStyleClass().add("matching-spotlight-title");

        Label bodyLabel = new Label(safeText(body));
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("matching-spotlight-copy");

        panel.getChildren().addAll(titleLabel, bodyLabel);
        return panel;
    }

    private String buildStudentMatchingHeroSubtitle(StudentMatchCard card, MatchingNeedProfile needProfile) {
        List<String> fragments = new ArrayList<>();
        String needContext = buildStudentNeedContextLabel(needProfile);
        if (needContext != null) {
            fragments.add(needContext);
        }
        fragments.add(card.matchingModeSessions() > 0 ? "format deja pratique" : "format compatible confirme");
        return String.join(" | ", fragments);
    }

    private String buildStudentMatchingSpotlight(StudentMatchCard card, MatchingNeedProfile needProfile) {
        if (card == null) {
            return "Le matching a repere un profil exploitable pour continuer.";
        }

        String needSummary = needProfile == null ? null : normalizeText(needProfile.summary());
        List<String> fragments = new ArrayList<>();
        if (needSummary != null) {
            fragments.add(needSummary);
        }
        fragments.add(
            card.tutorName()
                + " a deja anime "
                + card.subjectSessions()
                + " seance(s) dans cette matiere"
        );
        if (card.matchingModeSessions() > 0) {
            fragments.add("format deja pratique");
        }
        if (card.acceptedReservations() > 0) {
            fragments.add(card.acceptedReservations() + " reservation(s) deja acceptee(s)");
        }
        return String.join(". ", fragments) + ".";
    }

    private String buildTutorMatchingHeroSubtitle(TutorMatchInboxItem item) {
        if (item == null) {
            return "Demande pre-qualifiee par le matching.";
        }

        List<String> fragments = new ArrayList<>();
        String participantName = normalizeText(item.participantName());
        if (participantName != null) {
            fragments.add(participantName);
        }
        fragments.add(formatDateTimeOrPlaceholder(item.requestedStartAt()));
        fragments.add(formatMatchingVisibility(item.visibilityScope()));
        return String.join(" | ", fragments);
    }

    private String buildTutorMatchingObjectivePreview(TutorMatchInboxItem item) {
        if (item == null) {
            return "Le brief etudiant sera visible ici des qu'une demande sera qualifiee.";
        }

        String summary = normalizeText(item.objectiveSummary());
        if (summary != null) {
            return summary;
        }

        String objective = normalizeText(item.objectiveText());
        if (objective == null) {
            return "Le besoin a ete qualifie automatiquement et reste coherent avec le creneau demande.";
        }
        return ellipsizeText(objective, 220);
    }

    private String resolveMatchingPriorityLabel(int decision) {
        return decision == MatchingService.DECISION_SUPER ? "Priorite haute" : "Interet confirme";
    }

    private String resolveMatchingScoreProgressStyle(double score) {
        if (score >= 85.0) {
            return "matching-score-progress-elite";
        }
        if (score >= 70.0) {
            return "matching-score-progress-strong";
        }
        return "matching-score-progress-regular";
    }

    private String resolveMatchingInitials(String sourceText) {
        String normalizedValue = normalizeText(sourceText);
        if (normalizedValue == null) {
            return "FM";
        }

        StringBuilder initials = new StringBuilder();
        for (String token : normalizedValue.split(" ")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            initials.append(token.substring(0, 1).toUpperCase(Locale.ROOT));
            if (initials.length() >= 2) {
                return initials.toString();
            }
        }

        if (initials.length() > 0) {
            return initials.toString();
        }
        return normalizedValue.substring(0, Math.min(2, normalizedValue.length())).toUpperCase(Locale.ROOT);
    }

    private String ellipsizeText(String value, int maxLength) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null || maxLength <= 0 || normalizedValue.length() <= maxLength) {
            return normalizedValue != null ? normalizedValue : "";
        }
        return normalizedValue.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void prepareMatchingSessionPlanning(MatchingPlanContext planContext) {
        if (planContext == null) {
            return;
        }

        pendingMatchingPlan = new PendingMatchingPlan(
            planContext.requestId(),
            planContext.participantId(),
            planContext.participantName(),
            planContext.visibilityScope()
        );

        clearSessionForm();
        resetEditMode();
        hideFeedback();

        sessionSubjectField.setText(safeText(planContext.subject()));
        setStartAtSelection(planContext.requestedStartAt());
        sessionDurationField.setText(String.valueOf(planContext.durationMin()));
        sessionCapacityField.setText(
            MatchingService.VISIBILITY_PRIVATE.equals(planContext.visibilityScope()) ? "1" : "8"
        );
        sessionDescriptionArea.setText(buildMatchingPlanDescription(planContext));
        sessionOnsiteCheckBox.setSelected(Seance.MODE_ONSITE.equals(planContext.mode()));
        updateOnsiteOptionsVisibility();

        showAddSessionSection();
        sessionFormTitleLabel.setText("Planifier la seance issue du matching");
        sessionFormModeChipLabel.setText("Matching");
        editingSessionLabel.setText(
            "Matching accepte pour " + safeText(planContext.participantName())
                + ". Finalisez la planification puis publiez la seance."
        );
        editingSessionLabel.setManaged(true);
        editingSessionLabel.setVisible(true);
        showFeedback(
            MatchingService.VISIBILITY_PRIVATE.equals(planContext.visibilityScope())
                ? "Seance individuelle pre-remplie. Si elle est publiee, elle restera visible uniquement pour l'etudiant concerne."
                : "Seance publique pre-remplie a partir du matching. Publiez-la pour l'ouvrir au catalogue.",
            true
        );
    }

    private String buildMatchingPlanDescription(MatchingPlanContext planContext) {
        List<String> fragments = new ArrayList<>();
        fragments.add("Demande issue du matching pour " + safeText(planContext.participantName()) + ".");
        if (planContext.objectiveSummary() != null && !planContext.objectiveSummary().isBlank()) {
            fragments.add(planContext.objectiveSummary());
        }
        if (planContext.objectiveText() != null && !planContext.objectiveText().isBlank()) {
            fragments.add("Besoin detaille: " + planContext.objectiveText());
        }
        return String.join(" ", fragments);
    }

    private VBox buildMatchingFieldBlock(String labelText, Node field) {
        VBox block = new VBox(6.0);
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        block.getChildren().addAll(label, field);
        return block;
    }

    private HBox buildMatchingDateTimeRow(DatePicker datePicker,
                                          ComboBox<String> hourComboBox,
                                          ComboBox<String> minuteComboBox,
                                          TextField durationField) {
        HBox row = new HBox(12.0);
        row.getChildren().addAll(
            buildMatchingFieldBlock("Date souhaitee", datePicker),
            buildMatchingFieldBlock("Heure", hourComboBox),
            buildMatchingFieldBlock("Minute", minuteComboBox),
            buildMatchingFieldBlock("Duree", durationField)
        );
        HBox.setHgrow(datePicker, Priority.ALWAYS);
        HBox.setHgrow(durationField, Priority.ALWAYS);
        return row;
    }

    private HBox buildMatchingTwinFieldRow(VBox leftBlock, VBox rightBlock) {
        HBox row = new HBox(12.0);
        HBox.setHgrow(leftBlock, Priority.ALWAYS);
        HBox.setHgrow(rightBlock, Priority.ALWAYS);
        row.getChildren().addAll(leftBlock, rightBlock);
        return row;
    }

    private void updateStudentMatchingResultsSummary(MatchingDraft draft,
                                                     MatchingRequestCreation creation,
                                                     Label titleLabel,
                                                     Label summaryLabel,
                                                     FlowPane signalRow) {
        boolean hasCandidate = creation != null && creation.candidates() != null && !creation.candidates().isEmpty();
        if (titleLabel != null) {
            titleLabel.setText(
                hasCandidate
                    ? "Tuteur compatible pour " + safeText(draft.subject())
                    : "Aucun tuteur compatible pour " + safeText(draft.subject())
            );
        }

        if (summaryLabel != null) {
            StringBuilder summary = new StringBuilder()
                .append("Demande du ")
                .append(formatDateTimeOrPlaceholder(draft.startAt()))
                .append(" | ")
                .append(draft.durationMin())
                .append(" min | ")
                .append(mapModeLabel(draft.mode()))
                .append(" | ")
                .append(formatMatchingVisibility(draft.visibilityScope()))
                .append(". ");

            summary.append(buildStudentMatchingRequestSummary(creation.needProfile(), hasCandidate ? 1 : 0));
            summaryLabel.setText(summary.toString());
        }

        if (signalRow != null) {
            signalRow.getChildren().clear();
            signalRow.getChildren().add(buildChoiceChip(safeText(draft.subject())));
            signalRow.getChildren().add(buildChoiceChip(mapModeLabel(draft.mode())));
            signalRow.getChildren().add(
                hasCandidate
                    ? buildChoiceChipHighlight("1 tuteur")
                    : buildChoiceChip("Aucun tuteur")
            );
            if (creation != null && creation.needProfile() != null && creation.needProfile().keywords() != null) {
                creation.needProfile().keywords().stream()
                    .limit(1)
                    .map(keyword -> buildChoiceChipHighlight("Focus " + keyword))
                    .forEach(signalRow.getChildren()::add);
            }
        }
    }

    private VBox buildStudentMatchingStateCard(String title, String body, String toneClass) {
        VBox card = new VBox(10.0);
        card.getStyleClass().addAll("reservation-form-shell", "matching-swipe-card", "matching-state-card");
        if (toneClass != null && !toneClass.isBlank()) {
            card.getStyleClass().add(toneClass);
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("subsection-title");

        Label bodyLabel = new Label(body);
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("reservation-section-copy");

        card.getChildren().addAll(titleLabel, bodyLabel);
        return card;
    }

    private VBox buildStudentMatchingCompletionView(StudentMatchingDialogState state) {
        if (state == null || state.positiveMatches.isEmpty()) {
            return buildStudentMatchingStateCard(
                "Aucun tuteur retenu",
                "Aucun tuteur compatible n'a ete retenu pour ce besoin. Modifie la demande puis relance le matching pour essayer avec un autre creneau ou une autre formulation.",
                "matching-state-card"
            );
        }

        VBox shell = new VBox(16.0);
        shell.getStyleClass().add("matching-results-finale");
        shell.setMaxWidth(620.0);

        Label eyebrowLabel = new Label("MATCH CONFIRME");
        eyebrowLabel.getStyleClass().add("matching-results-finale-eyebrow");

        Label titleLabel = new Label("Tuteur retenu");
        titleLabel.getStyleClass().add("matching-results-finale-title");

        String needSummary = state.needProfile == null ? null : normalizeText(state.needProfile.summary());
        Label subtitleLabel = new Label(
            needSummary != null
                ? needSummary + " Le tuteur retenu a maintenant recu ta demande."
                : "Le tuteur retenu a maintenant recu ta demande."
        );
        subtitleLabel.setWrapText(true);
        subtitleLabel.getStyleClass().add("matching-results-finale-copy");

        FlowPane matchGrid = new FlowPane(16.0, 16.0);
        matchGrid.getStyleClass().add("matching-match-grid");
        matchGrid.setAlignment(Pos.TOP_CENTER);
        matchGrid.setMaxWidth(Double.MAX_VALUE);
        buildStudentPositiveMatchCard(state.positiveMatches.get(0))
            .ifPresent(matchGrid.getChildren()::add);

        shell.getChildren().addAll(eyebrowLabel, titleLabel, subtitleLabel, matchGrid);
        return shell;
    }

    private Optional<VBox> buildStudentPositiveMatchCard(StudentPositiveMatch positiveMatch) {
        if (positiveMatch == null || positiveMatch.card() == null) {
            return Optional.empty();
        }
        String studentName = safeText(UserSession.getDisplayName());
        String tutorName = safeText(positiveMatch.card().tutorName());

        VBox card = new VBox(14.0);
        card.getStyleClass().add("matching-match-card");
        if (positiveMatch.decision() == MatchingService.DECISION_SUPER) {
            card.getStyleClass().add("matching-match-card-super");
        }
        card.setPrefWidth(272.0);

        Label toneChip = new Label(
            positiveMatch.decision() == MatchingService.DECISION_SUPER
                ? "Super match"
                : "Match envoye"
        );
        toneChip.getStyleClass().add("matching-match-chip");
        HBox toneChipRow = new HBox(toneChip);
        toneChipRow.setAlignment(Pos.CENTER);

        HBox portraitStage = new HBox(24.0);
        portraitStage.getStyleClass().add("matching-match-portrait-stage");
        portraitStage.setAlignment(Pos.CENTER);

        StackPane studentAvatar = buildMatchingAvatarBadge(studentName, "matching-avatar-badge-student");
        studentAvatar.getStyleClass().addAll("matching-match-avatar", "matching-match-avatar-student");

        StackPane tutorAvatar = buildMatchingAvatarBadge(tutorName, "matching-avatar-badge-tutor");
        tutorAvatar.getStyleClass().addAll("matching-match-avatar", "matching-match-avatar-tutor");

        portraitStage.getChildren().addAll(studentAvatar, tutorAvatar);
        HBox.setMargin(studentAvatar, new Insets(0.0, 0.0, 12.0, 0.0));
        HBox.setMargin(tutorAvatar, new Insets(18.0, 0.0, 0.0, 0.0));

        Label namesLabel = new Label(studentName + "\n&\n" + tutorName);
        namesLabel.setWrapText(true);
        namesLabel.getStyleClass().add("matching-match-names");

        Label copyLabel = new Label(buildStudentPositiveMatchCopy(positiveMatch));
        copyLabel.setWrapText(true);
        copyLabel.getStyleClass().add("matching-match-copy");

        Label detailLabel = new Label(buildStudentPositiveMatchDetail(positiveMatch));
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("matching-match-detail");

        card.getChildren().addAll(toneChipRow, portraitStage, namesLabel, copyLabel, detailLabel);
        return Optional.of(card);
    }

    private String buildStudentPositiveMatchCopy(StudentPositiveMatch positiveMatch) {
        if (positiveMatch != null && positiveMatch.decision() == MatchingService.DECISION_SUPER) {
            return "Ce tuteur a recu ta demande avec une priorite elevee.";
        }
        return "Ce tuteur a recu ta demande.";
    }

    private String buildStudentPositiveMatchDetail(StudentPositiveMatch positiveMatch) {
        if (positiveMatch == null || positiveMatch.card() == null) {
            return "Le tuteur verra maintenant ta demande dans son inbox.";
        }

        StudentMatchCard card = positiveMatch.card();
        return safeText(card.tutorName())
            + " a deja anime "
            + card.subjectSessions()
            + " seance(s) dans cette matiere"
            + (positiveMatch.decision() == MatchingService.DECISION_SUPER ? ". Priorite elevee envoyee." : ".");
    }

    private void animateMatchingWorkspaceSwap(Node outgoing, Node incoming) {
        if (incoming == null) {
            return;
        }
        if (outgoing == null || !outgoing.isVisible()) {
            incoming.setManaged(true);
            incoming.setVisible(true);
            animateMatchingCardEntrance(incoming);
            return;
        }

        outgoing.setDisable(true);
        incoming.setManaged(true);
        incoming.setVisible(true);
        incoming.setOpacity(0.0);
        incoming.setTranslateY(28.0);
        incoming.setScaleX(0.985);
        incoming.setScaleY(0.985);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(170), outgoing);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(190), outgoing);
        slideOut.setToY(-18.0);
        slideOut.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
        exit.setOnFinished(event -> {
            outgoing.setVisible(false);
            outgoing.setManaged(false);
            outgoing.setDisable(false);
            outgoing.setOpacity(1.0);
            outgoing.setTranslateY(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), incoming);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(240), incoming);
            slideIn.setFromY(28.0);
            slideIn.setToY(0.0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), incoming);
            scaleIn.setFromX(0.985);
            scaleIn.setFromY(0.985);
            scaleIn.setToX(1.0);
            scaleIn.setToY(1.0);
            scaleIn.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(fadeIn, slideIn, scaleIn).play();
        });
        exit.play();
    }

    private void handleStudentSwipeDecision(int decision,
                                            StudentMatchCard card,
                                            StudentMatchingDialogState state,
                                            Pane cardHost,
                                            Node resultSummaryCard,
                                            Node resultToolbar,
                                            Label progressLabel,
                                            Label feedbackLabel,
                                            Label swipeGuideLabel,
                                            Pane quickActionPanel,
                                            Label quickActionTitle,
                                            Button quickPassButton,
                                            Button quickInterestedButton,
                                            Button quickSuperMatchButton,
                                            StackPane swipeSurface,
                                            Label decisionBadge) {
        if (state == null || state.animating) {
            return;
        }

        OperationResult result = matchingService.recordStudentDecision(
            card.candidateId(),
            getCurrentReservationParticipantId(),
            decision
        );
        String message = decision == MatchingService.DECISION_SUPER
            ? "Super match envoye. " + result.getMessage()
            : result.getMessage();
        setInlineFeedback(feedbackLabel, result.isSuccess() ? null : message, false);
        if (!result.isSuccess()) {
            if (swipeSurface != null && decisionBadge != null) {
                animateStudentSwipeReset(swipeSurface, decisionBadge);
            }
            return;
        }

        rememberStudentPositiveMatch(state, card, decision);
        swipeGuideLabel.setText(resolveMatchingGuideCopy(decision, true));
        if (swipeSurface == null || decisionBadge == null) {
            state.currentIndex++;
            refreshStudentMatchCard(
                cardHost,
                resultSummaryCard,
                resultToolbar,
                progressLabel,
                feedbackLabel,
                swipeGuideLabel,
                quickActionPanel,
                quickActionTitle,
                quickPassButton,
                quickInterestedButton,
                quickSuperMatchButton,
                state
            );
            return;
        }

        state.animating = true;
        animateStudentSwipeOut(swipeSurface, decisionBadge, decision, () -> {
            state.currentIndex++;
            state.animating = false;
            refreshStudentMatchCard(
                cardHost,
                resultSummaryCard,
                resultToolbar,
                progressLabel,
                feedbackLabel,
                swipeGuideLabel,
                quickActionPanel,
                quickActionTitle,
                quickPassButton,
                quickInterestedButton,
                quickSuperMatchButton,
                state
            );
        });
    }

    private void installStudentSwipeInteractions(StackPane swipeSurface,
                                                 StudentMatchCard card,
                                                 StudentMatchingDialogState state,
                                                 Pane cardHost,
                                                 Node resultSummaryCard,
                                                 Node resultToolbar,
                                                 Label progressLabel,
                                                 Label feedbackLabel,
                                                 Label swipeGuideLabel,
                                                 Pane quickActionPanel,
                                                 Label quickActionTitle,
                                                 Button quickPassButton,
                                                 Button quickInterestedButton,
                                                 Button quickSuperMatchButton,
                                                 Label decisionBadge) {
        final double[] dragAnchor = new double[2];

        swipeSurface.setOnMousePressed(event -> {
            if (state.animating || isSwipeGestureIgnored(event)) {
                return;
            }
            dragAnchor[0] = event.getSceneX() - swipeSurface.getTranslateX();
            dragAnchor[1] = event.getSceneY() - swipeSurface.getTranslateY();
        });

        swipeSurface.setOnMouseDragged(event -> {
            if (state.animating || isSwipeGestureIgnored(event)) {
                return;
            }

            double offsetX = event.getSceneX() - dragAnchor[0];
            double offsetY = event.getSceneY() - dragAnchor[1];
            swipeSurface.setTranslateX(offsetX);
            swipeSurface.setTranslateY(offsetY);
            swipeSurface.setRotate(offsetX * MATCHING_SWIPE_ROTATION_FACTOR);

            int hoverDecision = resolveMatchingSwipeDecision(offsetX, offsetY);
            double emphasis = Math.min(
                1.0,
                Math.max(Math.abs(offsetX) / MATCHING_SWIPE_HORIZONTAL_THRESHOLD,
                    Math.abs(offsetY) / MATCHING_SWIPE_SUPER_THRESHOLD)
            );
            updateMatchingDecisionBadge(decisionBadge, hoverDecision, emphasis);
            swipeGuideLabel.setText(resolveMatchingGuideCopy(hoverDecision, false));
        });

        swipeSurface.setOnMouseReleased(event -> {
            if (state.animating || isSwipeGestureIgnored(event)) {
                return;
            }

            int decision = resolveMatchingSwipeDecision(swipeSurface.getTranslateX(), swipeSurface.getTranslateY());
            if (decision == 0) {
                animateStudentSwipeReset(swipeSurface, decisionBadge);
                swipeGuideLabel.setText(
                    "Glisse a gauche pour passer, a droite pour retenir, ou vers le haut pour envoyer un super match."
                );
                return;
            }

            handleStudentSwipeDecision(
                decision,
                card,
                state,
                cardHost,
                resultSummaryCard,
                resultToolbar,
                progressLabel,
                feedbackLabel,
                swipeGuideLabel,
                quickActionPanel,
                quickActionTitle,
                quickPassButton,
                quickInterestedButton,
                quickSuperMatchButton,
                swipeSurface,
                decisionBadge
            );
        });
    }

    private boolean isSwipeGestureIgnored(MouseEvent event) {
        Object target = event == null ? null : event.getTarget();
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null) {
            if (node instanceof Button) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private void animateMatchingCardEntrance(Node node) {
        if (node == null) {
            return;
        }

        node.setOpacity(0.0);
        node.setTranslateY(26.0);
        node.setScaleX(0.97);
        node.setScaleY(0.97);

        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), node);
        slide.setFromY(26.0);
        slide.setToY(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(240), node);
        scale.setFromX(0.97);
        scale.setFromY(0.97);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide, scale).play();
    }

    private void animateStudentSwipeReset(StackPane swipeSurface, Label decisionBadge) {
        if (swipeSurface == null) {
            return;
        }

        TranslateTransition translate = new TranslateTransition(Duration.millis(190), swipeSurface);
        translate.setToX(0.0);
        translate.setToY(0.0);
        translate.setInterpolator(Interpolator.EASE_BOTH);

        javafx.animation.Timeline rotateReset = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                Duration.millis(190),
                new javafx.animation.KeyValue(swipeSurface.rotateProperty(), 0.0, Interpolator.EASE_BOTH)
            )
        );

        ParallelTransition reset = new ParallelTransition(translate, rotateReset);
        reset.setOnFinished(event -> updateMatchingDecisionBadge(decisionBadge, 0, 0.0));
        reset.play();
    }

    private void animateStudentSwipeOut(StackPane swipeSurface,
                                        Label decisionBadge,
                                        int decision,
                                        Runnable onFinished) {
        if (swipeSurface == null) {
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }

        double targetX = 0.0;
        double targetY = 0.0;
        double targetRotation = 0.0;

        if (decision == MatchingService.DECISION_PASS) {
            targetX = -520.0;
            targetY = 48.0;
            targetRotation = -20.0;
        } else if (decision == MatchingService.DECISION_INTERESTED) {
            targetX = 520.0;
            targetY = 32.0;
            targetRotation = 20.0;
        } else if (decision == MatchingService.DECISION_SUPER) {
            targetY = -360.0;
            targetRotation = 0.0;
        }

        updateMatchingDecisionBadge(decisionBadge, decision, 1.0);

        TranslateTransition translate = new TranslateTransition(Duration.millis(250), swipeSurface);
        translate.setToX(targetX);
        translate.setToY(targetY);
        translate.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fade = new FadeTransition(Duration.millis(220), swipeSurface);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), swipeSurface);
        scale.setToX(0.93);
        scale.setToY(0.93);
        scale.setInterpolator(Interpolator.EASE_IN);

        javafx.animation.Timeline rotate = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                Duration.millis(250),
                new javafx.animation.KeyValue(swipeSurface.rotateProperty(), targetRotation, Interpolator.EASE_IN)
            )
        );

        ParallelTransition transition = new ParallelTransition(translate, fade, scale, rotate);
        transition.setOnFinished(event -> {
            if (onFinished != null) {
                onFinished.run();
            }
        });
        transition.play();
    }

    private int resolveMatchingSwipeDecision(double translateX, double translateY) {
        if (Math.abs(translateY) > Math.abs(translateX) && translateY <= -MATCHING_SWIPE_SUPER_THRESHOLD) {
            return MatchingService.DECISION_SUPER;
        }
        if (translateX >= MATCHING_SWIPE_HORIZONTAL_THRESHOLD) {
            return MatchingService.DECISION_INTERESTED;
        }
        if (translateX <= -MATCHING_SWIPE_HORIZONTAL_THRESHOLD) {
            return MatchingService.DECISION_PASS;
        }
        return 0;
    }

    private void updateMatchingDecisionBadge(Label badge, int decision, double emphasis) {
        if (badge == null) {
            return;
        }

        badge.getStyleClass().removeAll(
            "matching-decision-neutral",
            "matching-decision-pass",
            "matching-decision-like",
            "matching-decision-super"
        );
        badge.getStyleClass().add("matching-decision-badge");

        if (decision == 0) {
            badge.setText("Swipe");
            badge.getStyleClass().add("matching-decision-neutral");
            badge.setVisible(false);
            badge.setOpacity(0.0);
            return;
        }

        badge.setText(resolveMatchingDecisionLabel(decision));
        badge.setVisible(true);
        badge.setManaged(false);
        badge.setOpacity(Math.max(0.35, Math.min(1.0, emphasis)));
        if (decision == MatchingService.DECISION_PASS) {
            badge.getStyleClass().add("matching-decision-pass");
        } else if (decision == MatchingService.DECISION_INTERESTED) {
            badge.getStyleClass().add("matching-decision-like");
        } else {
            badge.getStyleClass().add("matching-decision-super");
        }
    }

    private String resolveMatchingDecisionLabel(int decision) {
        if (decision == MatchingService.DECISION_PASS) {
            return "Passer";
        }
        if (decision == MatchingService.DECISION_INTERESTED) {
            return "Interesse";
        }
        if (decision == MatchingService.DECISION_SUPER) {
            return "Super match";
        }
        return "Swipe";
    }

    private String resolveMatchingGuideCopy(int decision, boolean committed) {
        if (decision == MatchingService.DECISION_PASS) {
            return committed
                ? "Profil ignore."
                : "Relache pour ignorer ce profil.";
        }
        if (decision == MatchingService.DECISION_INTERESTED) {
            return committed
                ? "Interet enregistre. Le tuteur verra ta demande."
                : "Relache pour retenir ce tuteur.";
        }
        if (decision == MatchingService.DECISION_SUPER) {
            return committed
                ? "Priorite elevee envoyee au tuteur."
                : "Relache pour envoyer un super match.";
        }
        return "Glisse a gauche pour passer, a droite pour retenir, ou vers le haut pour envoyer un super match.";
    }

    private void rememberStudentPositiveMatch(StudentMatchingDialogState state,
                                              StudentMatchCard card,
                                              int decision) {
        if (state == null || card == null || decision == MatchingService.DECISION_PASS) {
            return;
        }
        boolean alreadyStored = state.positiveMatches.stream()
            .filter(Objects::nonNull)
            .anyMatch(match -> match.card() != null && match.card().candidateId() == card.candidateId());
        if (!alreadyStored) {
            state.positiveMatches.add(new StudentPositiveMatch(card, decision));
        }
    }

    private String resolveMatchingConfidenceLabel(double score) {
        if (score >= 85.0) {
            return "Confiance Gemini tres forte";
        }
        if (score >= 72.0) {
            return "Confiance Gemini forte";
        }
        if (score >= 58.0) {
            return "Confiance Gemini solide";
        }
        return "Confiance Gemini moderee";
    }

    private String buildStudentMatchingAiExplanation(StudentMatchCard card, MatchingNeedProfile needProfile) {
        List<String> fragments = new ArrayList<>();
        String needContext = buildStudentNeedContextLabel(needProfile);
        if (needContext != null) {
            fragments.add("Ce profil correspond bien a " + needContext + ".");
        }

        fragments.add(
            card.tutorName() + " ressort ici car ce tuteur a deja anime "
                + card.subjectSessions() + " seance(s) pertinentes dans la matiere."
        );

        if (card.matchingModeSessions() > 0) {
            fragments.add("Le format souhaite fait deja partie de ses seances habituelles, ce qui renforce la pertinence de la proposition.");
        } else {
            fragments.add("Le format reste compatible avec votre demande et sa disponibilite a bien ete confirmee.");
        }

        if (card.acceptedReservations() > 0) {
            fragments.add(
                card.acceptedReservations() + " reservation(s) acceptee(s) renforcent aussi la fiabilite de ce profil."
            );
        }
        return String.join(" ", fragments);
    }

    private String buildStudentMatchingRequestSummary(MatchingNeedProfile needProfile, int candidateCount) {
        StringBuilder summary = new StringBuilder("Analyse Gemini personnalisee finalisee.");
        String needContext = buildStudentNeedContextLabel(needProfile);
        if (needContext != null) {
            summary.append(" La recherche a ete orientee vers ").append(needContext).append(".");
        }
        if (candidateCount > 0) {
            summary.append(" ").append(candidateCount).append(candidateCount > 1 ? " profils compatibles ont ete identifies." : " profil compatible a ete identifie.");
        } else {
            summary.append(" Aucun profil compatible n'a pu etre retenu pour le moment.");
        }
        return summary.toString();
    }

    private String buildStudentNeedContextLabel(MatchingNeedProfile needProfile) {
        if (needProfile == null) {
            return null;
        }

        List<String> fragments = new ArrayList<>();
        if (needProfile.level() != null
            && !needProfile.level().isBlank()
            && !"Niveau non precise".equalsIgnoreCase(needProfile.level())) {
            fragments.add("un accompagnement de niveau " + needProfile.level().toLowerCase());
        }

        List<String> visibleKeywords = needProfile.keywords() == null
            ? List.of()
            : needProfile.keywords().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .distinct()
                .limit(2)
                .toList();
        if (!visibleKeywords.isEmpty()) {
            fragments.add("un focus sur " + String.join(" et ", visibleKeywords));
        }

        if (fragments.isEmpty()) {
            return null;
        }
        return String.join(" avec ", fragments);
    }

    private String buildTutorMatchingAiExplanation(TutorMatchInboxItem item) {
        List<String> fragments = new ArrayList<>();
        String needContext = buildTutorNeedContextLabel(item);
        if (needContext != null) {
            fragments.add("La demande porte sur " + needContext + ".");
        }
        fragments.add("Votre profil ressort ici car votre experience et votre disponibilite correspondent deja a ce creneau.");
        if (item.reason() != null && !item.reason().isBlank()) {
            fragments.add(item.reason());
        }
        return String.join(" ", fragments);
    }

    private String buildTutorMatchingRequestSummary(TutorMatchInboxItem item) {
        if (item == null) {
            return "Demande de seance en attente de consultation.";
        }

        StringBuilder summary = new StringBuilder("Demande preparee pour une seance de ")
            .append(item.durationMin())
            .append(" min en ")
            .append(mapModeLabel(item.mode()))
            .append(", au format ")
            .append(formatMatchingVisibility(item.visibilityScope()).toLowerCase())
            .append(".");

        String needContext = buildTutorNeedContextLabel(item);
        if (needContext != null) {
            summary.append(" Le besoin vise ").append(needContext).append(".");
        }
        return summary.toString();
    }

    private String buildTutorMatchingRequestDetail(TutorMatchInboxItem item) {
        if (item == null) {
            return "Aucun detail complementaire n'est disponible pour cette demande.";
        }

        List<String> visibleKeywords = item.keywords() == null
            ? List.of()
            : item.keywords().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .toList();
        if (!visibleKeywords.isEmpty()) {
            return "Points d'attention annonces: " + String.join(", ", visibleKeywords) + ".";
        }
        return "Le besoin a ete qualifie automatiquement a partir de la demande et reste coherent avec le creneau propose.";
    }

    private String buildTutorNeedContextLabel(TutorMatchInboxItem item) {
        if (item == null) {
            return null;
        }

        List<String> fragments = new ArrayList<>();
        String normalizedSubject = normalizeText(item.subject());
        if (normalizedSubject != null) {
            fragments.add("un besoin en " + normalizedSubject);
        }
        if (item.requestedLevel() != null
            && !item.requestedLevel().isBlank()
            && !"Niveau non precise".equalsIgnoreCase(item.requestedLevel())) {
            fragments.add("de niveau " + item.requestedLevel().toLowerCase());
        }

        List<String> visibleKeywords = item.keywords() == null
            ? List.of()
            : item.keywords().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .distinct()
                .limit(2)
                .toList();
        if (!visibleKeywords.isEmpty()) {
            fragments.add("avec un focus sur " + String.join(" et ", visibleKeywords));
        }

        if (fragments.isEmpty()) {
            return null;
        }
        return String.join(" ", fragments);
    }

    private String buildTutorMatchingActionHint(TutorMatchInboxItem item) {
        if (item.studentDecision() == MatchingService.DECISION_SUPER) {
            return "Cette demande est prioritaire. Une acceptation ouvre directement la planification de la seance.";
        }
        return "Cette demande presente un interet confirme. Acceptez pour poursuivre vers la planification de la seance.";
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
        return filterVisibleSessions(seanceService.search(
            sessionSearchField.getText(),
            sessionSearchModeComboBox.getValue(),
            0
        )).stream()
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
                + " | " + formatSessionPrice(seance.getPrice())
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
        configureSessionCardDoubleClick(card, seance, reservationStats);
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
        Button reserveButton = new Button(seance.getPrice() > 0.0 ? "Reserver & payer" : "Reserver cette seance");
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

    private List<Seance> filterVisibleSessions(List<Seance> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        List<Integer> seanceIds = sessions.stream()
            .filter(Objects::nonNull)
            .map(Seance::getId)
            .filter(id -> id > 0)
            .distinct()
            .toList();
        Map<Integer, SessionVisibility> visibilityBySeanceId = matchingService.getSessionVisibilityBySeanceIds(seanceIds);

        return sessions.stream()
            .filter(Objects::nonNull)
            .filter(seance -> canCurrentUserViewSession(seance, visibilityBySeanceId.get(seance.getId())))
            .toList();
    }

    private boolean canCurrentUserViewSession(Seance seance, SessionVisibility visibility) {
        if (seance == null) {
            return false;
        }
        if (visibility == null || !visibility.isPrivate()) {
            return true;
        }
        return UserSession.isCurrentTutor() && seance.getTuteurId() == getCurrentTutorId();
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

    private String formatMatchingVisibility(String visibilityScope) {
        return MatchingService.VISIBILITY_PRIVATE.equals(visibilityScope)
            ? "Seance individuelle"
            : "Seance ouverte";
    }

    private Button buildReserveButton(Seance seance, ReservationStats reservationStats) {
        Button reserveButton = new Button(seance.getPrice() > 0.0 ? "Reserver & payer" : "Reserver");
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
        int participantId = getCurrentReservationParticipantId();
        ReservationService.ReservationCreationResult result = reservationService.reserveSeanceDetailed(seance, participantId);
        boolean success = result.success();
        String message = result.message();

        if (success && result.paymentRequired()) {
            ReservationService.PaymentLaunchResult paymentLaunchResult =
                reservationService.startStripeCheckoutPayment(result.reservationId(), participantId);
            if (paymentLaunchResult.success()) {
                ReservationService.PaymentVerificationResult paymentResult =
                    openEmbeddedStripeCheckoutDialog(paymentLaunchResult, participantId);
                if (paymentResult != null) {
                    success = paymentResult.success();
                    message = paymentResult.message();
                } else {
                    message = paymentLaunchResult.message()
                        + " Finalisez le paiement Stripe dans la fenetre ouverte puis verifiez le statut si necessaire.";
                }
            } else {
                success = false;
                message = result.message() + " " + paymentLaunchResult.message();
            }
        }

        loadSessionDashboard();
        showAvailableSessionsSection();
        showReservationActionFeedback(message, success);
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
                + " | " + formatSessionPrice(reservation.sessionPriceTnd() == null ? 0.0 : reservation.sessionPriceTnd())
        );
        sessionLabel.setWrapText(true);
        sessionLabel.getStyleClass().add("reservation-section-copy");

        Label requestLabel = new Label(
            "Reservation envoyee: " + formatDateTimeOrPlaceholder(reservation.reservedAt())
        );
        requestLabel.setWrapText(true);
        requestLabel.getStyleClass().add("reservation-section-copy");

        Label paymentLabel = new Label(buildStudentPaymentSummary(reservation));
        paymentLabel.setWrapText(true);
        paymentLabel.getStyleClass().add("reservation-section-copy");

        Label ratingLabel = new Label(buildStudentRatingSummary(reservation));
        ratingLabel.setWrapText(true);
        ratingLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actionRow.getChildren().add(actionSpacer);

        if (canCurrentUserRateReservation(reservation)) {
            Button ratingButton = new Button("Noter la seance");
            ratingButton.getStyleClass().add("backoffice-primary-button");
            ratingButton.setOnAction(event -> openStudentRatingDialog(reservation));
            actionRow.getChildren().add(ratingButton);
        }

        if (reservation.canLaunchPayment()) {
            Button payButton = new Button("Payer avec Stripe");
            payButton.getStyleClass().add("backoffice-primary-button");
            payButton.setOnAction(event -> handleLaunchReservationPayment(reservation));
            actionRow.getChildren().add(payButton);

            Button verifyButton = new Button("Verifier paiement");
            verifyButton.getStyleClass().add("backoffice-secondary-button");
            verifyButton.setOnAction(event -> handleVerifyReservationPayment(reservation));
            actionRow.getChildren().add(verifyButton);
        } else if (reservation.requiresPayment()) {
            Button verifyButton = new Button("Verifier paiement");
            verifyButton.getStyleClass().add("backoffice-secondary-button");
            verifyButton.setOnAction(event -> handleVerifyReservationPayment(reservation));
            actionRow.getChildren().add(verifyButton);
        }

        if (reservation.isPending()) {
            Button cancelButton = new Button("Annuler ma reservation");
            cancelButton.getStyleClass().addAll("action-button", "danger");
            cancelButton.setOnAction(event -> confirmCancelStudentReservation(reservation));
            actionRow.getChildren().add(cancelButton);
        }

        card.getChildren().addAll(headerRow, sessionLabel, requestLabel, paymentLabel, ratingLabel, actionRow);
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

    private String buildStudentPaymentSummary(StudentReservationItem reservation) {
        if (reservation == null) {
            return "Paiement: indisponible.";
        }
        if (!reservation.requiresPayment()) {
            return "Paiement: aucun paiement requis pour cette seance.";
        }
        if (reservation.isPaymentCompleted()) {
            String summary = "Paiement: confirme via Stripe";
            if (reservation.paymentCompletedAt() != null) {
                summary = summary + " le " + formatDateTimeOrPlaceholder(reservation.paymentCompletedAt());
            }
            return summary + ".";
        }
        if (ReservationService.PAYMENT_STATUS_FAILED.equals(reservation.paymentStatus())) {
            return "Paiement: tentative echouee. Relancez un nouveau lien Stripe.";
        }
        if (ReservationService.PAYMENT_STATUS_EXPIRED.equals(reservation.paymentStatus())) {
            return "Paiement: lien expire. Generez un nouveau lien Stripe.";
        }
        if (reservation.paymentInitiatedAt() != null) {
            return "Paiement: en attente depuis le " + formatDateTimeOrPlaceholder(reservation.paymentInitiatedAt())
                + ". Finalisez le checkout Stripe puis verifiez le statut.";
        }
        return "Paiement: en attente d'initialisation Stripe.";
    }

    private void handleLaunchReservationPayment(StudentReservationItem reservation) {
        if (reservation == null) {
            return;
        }
        ReservationService.PaymentLaunchResult result = reservationService.startStripeCheckoutPayment(
            reservation.id(),
            getCurrentReservationParticipantId()
        );
        if (result.success()) {
            ReservationService.PaymentVerificationResult paymentResult =
                openEmbeddedStripeCheckoutDialog(result, getCurrentReservationParticipantId());
            loadStudentReservations();
            if (paymentResult != null) {
                showStudentReservationsFeedback(paymentResult.message(), paymentResult.success());
                return;
            }
            showStudentReservationsFeedback(
                result.message() + " Finalisez le paiement Stripe dans la fenetre ouverte puis verifiez le statut si necessaire.",
                true
            );
            return;
        }
        loadStudentReservations();
        showStudentReservationsFeedback(result.message(), false);
    }

    private void handleVerifyReservationPayment(StudentReservationItem reservation) {
        if (reservation == null) {
            return;
        }
        ReservationService.PaymentVerificationResult result = reservationService.refreshReservationPaymentStatus(
            reservation.id(),
            getCurrentReservationParticipantId()
        );
        loadStudentReservations();
        showStudentReservationsFeedback(result.message(), result.success());
    }

    private String buildTutorReservationRatingSummary(TutorReservationRequest request) {
        if (request == null) {
            return "Evaluation recue: indisponible.";
        }
        if (request.hasRating()) {
            StringBuilder summary = new StringBuilder("Evaluation recue: " + request.studentRating() + "/5");
            if (request.ratedAt() != null) {
                summary.append(" | Notee le ").append(formatDateTimeOrPlaceholder(request.ratedAt()));
            }
            if (request.studentReview() != null && !request.studentReview().isBlank()) {
                summary.append(" | Avis: ").append(request.studentReview());
            }
            return summary.toString();
        }
        if (request.status() == ReservationService.STATUS_ACCEPTED) {
            return "Evaluation recue: disponible apres la fin de la seance.";
        }
        if (request.isRefused()) {
            return "Evaluation recue: aucune, la reservation a ete refusee.";
        }
        return "Evaluation recue: en attente d'une reservation acceptee puis terminee.";
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

        Label titleLabel = new Label("Veuillez noter votre experience");
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
        );
        sessionLabel.setWrapText(true);
        sessionLabel.getStyleClass().add("reservation-section-copy");

        Label ratingLabel = new Label(buildTutorReservationRatingSummary(request));
        ratingLabel.setWrapText(true);
        ratingLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button acceptButton = new Button("Accepter");
        acceptButton.getStyleClass().addAll("action-button", "accept");
        acceptButton.setOnAction(event -> acceptTutorReservationRequest(request));

        Button refuseButton = new Button("Refuser");
        refuseButton.getStyleClass().addAll("action-button", "danger");
        refuseButton.setOnAction(event -> confirmRefuseTutorReservationRequest(request));

        actionRow.getChildren().add(actionSpacer);
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

        card.getChildren().addAll(headerRow, studentLabel, sessionLabel, ratingLabel, actionRow);
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
        VBox content = buildSessionDetailsContent(seance, reservationStats, detailsDialog);

        detailsDialog.setTitle("Detail de la seance");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setContent(content);
        dialogPane.setPrefWidth(720);
        dialogPane.getStyleClass().add("session-detail-dialog");
        applyCurrentTheme(dialogPane);
        appendDialogStylesheet(dialogPane, SESSION_EVALUATIONS_STYLESHEET);
        detailsDialog.setOnShown(event -> animateSessionDetailsEntrance(content));
        detailsDialog.showAndWait();
    }

    private VBox buildSessionDetailsContent(Seance seance, ReservationStats reservationStats, Dialog<ButtonType> detailsDialog) {
        boolean canManageCurrentSession = canManageSession(seance);
        int reservationTotal = reservationStats != null ? reservationStats.total() : 0;
        List<SessionEvaluationItem> sessionEvaluations = canManageCurrentSession
            ? reservationService.getSessionEvaluations(seance.getId())
            : List.of();
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
            buildDetailMetric("Tuteur", tutorDirectoryService.getTutorDisplayName(seance.getTuteurId()), "Intervenant"),
            buildDetailMetric("Mode", mapModeLabel(seance.getMode()), "Format choisi"),
            buildDetailMetric("Prix", formatSessionPrice(seance.getPrice()), "Tarif"),
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

        VBox evaluationsBox = buildSessionEvaluationsSection(sessionEvaluations);

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
            root.getChildren().add(evaluationsBox);
        }
        root.getChildren().addAll(descriptionBox, footer);
        return root;
    }

    private void configureSessionCardDoubleClick(VBox card, Seance seance, ReservationStats reservationStats) {
        if (card == null || seance == null) {
            return;
        }
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() < 2 || isSessionCardInteractiveTarget(event.getTarget())) {
                return;
            }
            showSessionDetails(seance, reservationStats);
        });
    }

    private boolean isSessionCardInteractiveTarget(Object target) {
        Node current = target instanceof Node node ? node : null;
        while (current != null) {
            if (current instanceof Button) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private VBox buildSessionEvaluationsSection(List<SessionEvaluationItem> evaluations) {
        VBox section = new VBox(12.0);
        section.getStyleClass().add("session-detail-evaluations-card");

        HBox header = new HBox(10.0);
        header.getStyleClass().add("session-detail-evaluations-header");

        VBox titleBlock = new VBox(4.0);
        Label title = new Label("Evaluations recues");
        title.getStyleClass().add("session-detail-section-title");
        Label subtitle = new Label(
            evaluations == null || evaluations.isEmpty()
                ? "Les avis des etudiants apparaitront ici des qu'une seance acceptee sera notee."
                : "Les derniers retours envoyes par les etudiants apres la seance."
        );
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("session-detail-evaluation-subtitle");
        titleBlock.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label summaryChip = new Label(buildSessionEvaluationsSummary(evaluations));
        summaryChip.getStyleClass().add("session-detail-evaluation-summary");
        header.getChildren().addAll(titleBlock, spacer, summaryChip);

        VBox content = new VBox(10.0);
        List<Node> animatedCards = new ArrayList<>();
        if (evaluations == null || evaluations.isEmpty()) {
            Label emptyLabel = new Label("Aucune evaluation enregistree pour cette seance pour le moment.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("session-detail-evaluation-empty");
            content.getChildren().add(emptyLabel);
            animatedCards.add(emptyLabel);
        } else {
            evaluations.stream()
                .limit(4)
                .map(this::buildSessionEvaluationCard)
                .forEach(card -> {
                    content.getChildren().add(card);
                    animatedCards.add(card);
                });

            if (evaluations.size() > 4) {
                Label moreLabel = new Label(
                    (evaluations.size() - 4) + " autre(s) avis sont deja enregistres pour cette seance."
                );
                moreLabel.setWrapText(true);
                moreLabel.getStyleClass().add("session-detail-evaluation-empty");
                content.getChildren().add(moreLabel);
                animatedCards.add(moreLabel);
            }
        }

        section.getProperties().put("session-detail-stagger-children", animatedCards);
        section.getChildren().addAll(header, content);
        return section;
    }

    private VBox buildSessionEvaluationCard(SessionEvaluationItem evaluation) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("session-detail-evaluation-card");

        HBox header = new HBox(10.0);
        header.getStyleClass().add("session-detail-evaluation-header");

        VBox identityBlock = new VBox(3.0);
        Label authorLabel = new Label(formatSessionEvaluationAuthor(evaluation));
        authorLabel.getStyleClass().add("session-detail-evaluation-author");
        Label dateLabel = new Label(
            evaluation != null && evaluation.ratedAt() != null
                ? "Notee le " + formatDateTimeOrPlaceholder(evaluation.ratedAt())
                : "Note envoyee recemment"
        );
        dateLabel.getStyleClass().add("session-detail-evaluation-date");
        identityBlock.getChildren().addAll(authorLabel, dateLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label starsLabel = new Label(buildRatingStars(evaluation != null ? evaluation.rating() : 0));
        starsLabel.getStyleClass().add("session-detail-evaluation-stars");
        Label scoreLabel = new Label((evaluation != null ? evaluation.rating() : 0) + "/5");
        scoreLabel.getStyleClass().add("session-detail-evaluation-score");

        header.getChildren().addAll(identityBlock, spacer, starsLabel, scoreLabel);

        Label reviewLabel = new Label(
            evaluation != null && evaluation.hasReview()
                ? evaluation.review()
                : "Aucun commentaire detaille, mais la note a bien ete partagee."
        );
        reviewLabel.setWrapText(true);
        reviewLabel.getStyleClass().add("session-detail-evaluation-review");

        card.getChildren().addAll(header, reviewLabel);
        return card;
    }

    private String buildSessionEvaluationsSummary(List<SessionEvaluationItem> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return "0 avis";
        }

        double averageRating = evaluations.stream()
            .mapToInt(SessionEvaluationItem::rating)
            .average()
            .orElse(0.0);
        return formatAverageRating(averageRating) + "/5  |  " + evaluations.size() + " avis";
    }

    private String formatAverageRating(double value) {
        return String.format(Locale.US, "%.1f", value).replace('.', ',');
    }

    private String formatSessionEvaluationAuthor(SessionEvaluationItem evaluation) {
        if (evaluation == null) {
            return "Etudiant Fahamni";
        }
        String normalizedValue = normalizeText(evaluation.participantName());
        return normalizedValue != null ? normalizedValue : "Etudiant Fahamni";
    }

    private String buildRatingStars(int rating) {
        int normalizedRating = Math.max(0, Math.min(ReservationService.MAX_STUDENT_RATING, rating));
        return "★".repeat(normalizedRating)
            + "☆".repeat(Math.max(0, ReservationService.MAX_STUDENT_RATING - normalizedRating));
    }

    private void animateSessionDetailsEntrance(VBox root) {
        if (root == null) {
            return;
        }

        List<Node> sections = new ArrayList<>(root.getChildren());
        double delay = 0.0;
        for (Node section : sections) {
            animateSessionDetailNode(section, delay);
            delay += 55.0;

            Object staggerChildren = section.getProperties().get("session-detail-stagger-children");
            if (staggerChildren instanceof List<?> childNodes) {
                double childDelay = delay + 40.0;
                for (Object childNode : childNodes) {
                    if (childNode instanceof Node animatedChild) {
                        animateSessionDetailNode(animatedChild, childDelay);
                        childDelay += 70.0;
                    }
                }
            }
        }
    }

    private void animateSessionDetailNode(Node node, double delayMillis) {
        if (node == null) {
            return;
        }

        node.setOpacity(0.0);
        node.setTranslateY(18.0);
        node.setScaleX(0.98);
        node.setScaleY(0.98);

        Duration delay = Duration.millis(delayMillis);

        FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setDelay(delay);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(280), node);
        slide.setDelay(delay);
        slide.setFromY(18.0);
        slide.setToY(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(240), node);
        scale.setDelay(delay);
        scale.setFromX(0.98);
        scale.setFromY(0.98);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide, scale).play();
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
        pendingMatchingPlan = null;
        editingSessionId = seance.getId();
        sessionSubjectField.setText(seance.getMatiere());
        setStartAtSelection(seance.getStartAt());
        sessionDurationField.setText(String.valueOf(seance.getDurationMin()));
        sessionCapacityField.setText(String.valueOf(seance.getMaxParticipants()));
        sessionPriceField.setText(formatPriceTnd(seance.getPrice()));
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
        return "Veuillez reessayer dans un instant.";
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

    private String formatSessionPrice(double priceTnd) {
        return priceTnd <= 0.0 ? "Gratuit" : formatPriceTnd(priceTnd) + " TND";
    }

    private String formatPriceTnd(double priceTnd) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0.0, priceTnd));
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

    private ReservationService.PaymentVerificationResult openEmbeddedStripeCheckoutDialog(
        ReservationService.PaymentLaunchResult launchResult,
        int participantId
    ) {
        if (launchResult == null || !launchResult.success()) {
            return ReservationService.PaymentVerificationResult.failure("Session Stripe invalide pour le paiement.");
        }

        String checkoutUrl = normalizeText(launchResult.paymentUrl());
        if (checkoutUrl == null) {
            return ReservationService.PaymentVerificationResult.failure(
                "Stripe Checkout n'a pas retourne de lien de paiement exploitable."
            );
        }

        ReservationService.PaymentVerificationResult[] resultHolder = new ReservationService.PaymentVerificationResult[1];
        StudentReservationItem reservationSummary = findStudentReservationItem(participantId, launchResult.reservationId());
        String amountLabel = resolvePaymentDialogAmount(reservationSummary);
        String sessionTitle = reservationSummary != null
            ? safeText(reservationSummary.seanceTitle())
            : "Reservation Fahamni";
        String currentUserEmail = UserSession.hasCurrentUser()
            ? safeText(UserSession.getCurrentUser().getEmail())
            : "Email non renseigne";
        String currentUserName = UserSession.hasCurrentUser()
            ? safeText(UserSession.getCurrentUser().getFullName())
            : "Utilisateur Fahamni";
        String cardholderPreview = currentUserName.toUpperCase(Locale.ROOT);
        String cardNumberPreview = resolvePaymentPreviewCardNumber();
        String expiryPreview = isStripeTestMode() ? "12/34" : "MM / YY";
        String cvvPreview = isStripeTestMode() ? "123" : "***";
        boolean[] loadFailedHolder = new boolean[1];

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        Window owner = resolveReservationWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Paiement Stripe");
        stage.setMinWidth(980.0);
        stage.setMinHeight(720.0);

        Label statusLabel = new Label("Chargement du formulaire Stripe...");
        statusLabel.getStyleClass().add("payment-dialog-status-label");
        statusLabel.setWrapText(true);

        ProgressBar loadingBar = new ProgressBar();
        loadingBar.setMaxWidth(Double.MAX_VALUE);
        loadingBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        loadingBar.getStyleClass().add("payment-dialog-loading-bar");

        VBox statusCard = new VBox(10.0, statusLabel, loadingBar);
        statusCard.getStyleClass().add("payment-dialog-status-card");

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.setMinSize(560.0, 380.0);
        webView.setPrefHeight(420.0);

        StackPane webViewShell = new StackPane(webView);
        webViewShell.getStyleClass().add("payment-dialog-browser-shell");
        VBox.setVgrow(webViewShell, Priority.ALWAYS);

        HBox stepperRow = new HBox(
            buildPaymentStepItem("YOUR ORDER", false),
            buildPaymentStepItem("SHIPPING DETAILS", false),
            buildPaymentStepItem("PAYMENT DETAILS", true),
            buildPaymentStepItem("CONFIRMATION", false)
        );
        stepperRow.getStyleClass().add("payment-dialog-stepper");

        Label previewCaption = new Label("Card Preview");
        previewCaption.getStyleClass().add("payment-dialog-preview-caption");

        Label previewCopy = new Label("Apercu visuel inspire du checkout, la saisie reelle reste securisee dans Stripe.");
        previewCopy.getStyleClass().add("payment-dialog-preview-copy");
        previewCopy.setWrapText(true);

        Region cardChip = new Region();
        cardChip.getStyleClass().add("payment-dialog-card-chip");

        Label cardBrand = new Label("VISA");
        cardBrand.getStyleClass().add("payment-dialog-card-brand");

        Region cardHeaderSpacer = new Region();
        HBox.setHgrow(cardHeaderSpacer, Priority.ALWAYS);
        HBox cardHeader = new HBox(cardChip, cardHeaderSpacer, cardBrand);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        Label cardNumberLabel = new Label(cardNumberPreview);
        cardNumberLabel.getStyleClass().add("payment-dialog-card-number");

        Label cardholderLabel = new Label(cardholderPreview);
        cardholderLabel.getStyleClass().add("payment-dialog-card-meta-value");

        Label expiryLabel = new Label(expiryPreview);
        expiryLabel.getStyleClass().add("payment-dialog-card-meta-value");

        Label cardholderCaption = new Label("CARD HOLDER");
        cardholderCaption.getStyleClass().add("payment-dialog-card-meta-caption");

        Label expiryCaption = new Label("VALID THRU");
        expiryCaption.getStyleClass().add("payment-dialog-card-meta-caption");

        VBox cardholderBlock = new VBox(4.0, cardholderCaption, cardholderLabel);
        VBox expiryBlock = new VBox(4.0, expiryCaption, expiryLabel);
        Region cardFooterSpacer = new Region();
        HBox.setHgrow(cardFooterSpacer, Priority.ALWAYS);
        HBox cardFooter = new HBox(cardholderBlock, cardFooterSpacer, expiryBlock);
        cardFooter.setAlignment(Pos.BOTTOM_LEFT);

        VBox visualCard = new VBox(28.0, cardHeader, cardNumberLabel, cardFooter);
        visualCard.getStyleClass().add("payment-dialog-visual-card");

        Button previousStepButton = new Button("< Retour");
        previousStepButton.getStyleClass().add("payment-dialog-link-button");
        previousStepButton.setOnAction(event -> stage.close());

        VBox previewPanel = new VBox(18.0, previewCaption, previewCopy, visualCard, previousStepButton);
        previewPanel.getStyleClass().add("payment-dialog-preview-panel");
        previewPanel.setPrefWidth(280.0);
        previewPanel.setMinWidth(260.0);

        Label formTitle = new Label("Payment Details");
        formTitle.getStyleClass().add("payment-dialog-form-title");

        VBox nameField = buildPaymentDetailField("Name on Card", currentUserName, false);
        VBox cardField = buildPaymentDetailField("Card Number", cardNumberPreview, false);
        HBox inlineFieldRow = new HBox(
            16.0,
            buildPaymentDetailField("Valid Through", expiryPreview, true),
            buildPaymentDetailField("CVV", cvvPreview, true)
        );
        inlineFieldRow.getStyleClass().add("payment-dialog-inline-fields");

        Label detailHint = new Label(
            "Les champs reels seront completes dans Stripe Checkout juste en dessous."
        );
        detailHint.getStyleClass().add("payment-dialog-detail-hint");
        detailHint.setWrapText(true);

        VBox detailStack = new VBox(16.0, nameField, cardField, inlineFieldRow, detailHint);
        if (isStripeTestMode()) {
            Label testModeHint = new Label("Mode test: 4242 4242 4242 4242 | date future | CVC libre.");
            testModeHint.getStyleClass().addAll("payment-dialog-detail-hint", "payment-dialog-detail-hint-test");
            testModeHint.setWrapText(true);
            detailStack.getChildren().add(testModeHint);
        }

        Button openBrowserButton = new Button("Ouvrir dans le navigateur");
        openBrowserButton.getStyleClass().add("backoffice-secondary-button");
        openBrowserButton.setOnAction(event -> {
            boolean opened = openExternalUrl(checkoutUrl);
            statusLabel.setText(
                opened
                    ? "Le navigateur par defaut a ete ouvert. Finalisez le paiement puis revenez ici pour verifier."
                    : "Impossible d'ouvrir le navigateur automatiquement sur cette machine."
            );
        });

        Button payButton = new Button("Payer " + amountLabel);
        payButton.getStyleClass().add("payment-dialog-pay-button");
        payButton.setMaxWidth(Double.MAX_VALUE);
        payButton.setOnAction(event -> {
            if (loadFailedHolder[0]) {
                boolean opened = openExternalUrl(checkoutUrl);
                statusLabel.setText(
                    opened
                        ? "Le navigateur a ete ouvert. Finalisez le paiement puis revenez verifier le statut."
                        : "Impossible d'ouvrir le navigateur automatiquement sur cette machine."
                );
                return;
            }
            if (loadingBar.isVisible()) {
                statusLabel.setText("Le formulaire Stripe est encore en chargement...");
                return;
            }
            webView.requestFocus();
            statusLabel.setText("Completez maintenant les champs de carte dans Stripe Checkout ci-dessous.");
        });

        VBox detailPanel = new VBox(18.0, formTitle, detailStack, payButton);
        detailPanel.getStyleClass().add("payment-dialog-details-panel");
        HBox.setHgrow(detailPanel, Priority.ALWAYS);

        Button verifyButton = new Button("Verifier le paiement");
        verifyButton.getStyleClass().add("backoffice-primary-button");
        verifyButton.setOnAction(event -> {
            statusLabel.setText("Verification du paiement en cours...");
            ReservationService.PaymentVerificationResult verificationResult =
                reservationService.refreshReservationPaymentStatus(launchResult.reservationId(), participantId);
            if (!verificationResult.success()) {
                statusLabel.setText(verificationResult.message());
                return;
            }
            if (isTerminalPaymentStatus(verificationResult.paymentStatus())) {
                resultHolder[0] = verificationResult;
                stage.close();
                return;
            }
            statusLabel.setText(verificationResult.message());
        });

        Button closeButton = new Button("Fermer");
        closeButton.getStyleClass().add("backoffice-secondary-button");
        closeButton.setOnAction(event -> stage.close());

        HBox actionRow = new HBox(10.0, closeButton, openBrowserButton, verifyButton);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.getStyleClass().add("payment-dialog-action-row");

        HBox overviewRow = new HBox(24.0, previewPanel, detailPanel);
        overviewRow.getStyleClass().add("payment-dialog-overview-row");
        HBox.setHgrow(detailPanel, Priority.ALWAYS);

        Label checkoutTitle = new Label("Stripe Checkout");
        checkoutTitle.getStyleClass().add("payment-dialog-checkout-title");

        Label checkoutCopy = new Label(
            "Saisissez ici vos informations bancaires. Le paiement et le recu seront ensuite verifies automatiquement."
        );
        checkoutCopy.getStyleClass().add("payment-dialog-checkout-copy");
        checkoutCopy.setWrapText(true);

        VBox checkoutHeader = new VBox(4.0, checkoutTitle, checkoutCopy);
        checkoutHeader.getStyleClass().add("payment-dialog-checkout-header");

        VBox checkoutPanel = new VBox(14.0, checkoutHeader, statusCard, webViewShell, actionRow);
        checkoutPanel.getStyleClass().add("payment-dialog-checkout-panel");
        VBox.setVgrow(webViewShell, Priority.ALWAYS);

        VBox card = new VBox(16.0, stepperRow, overviewRow, checkoutPanel);
        card.getStyleClass().add("payment-dialog-card");

        StackPane root = new StackPane(card);
        root.getStyleClass().add("payment-dialog-root");
        root.setPadding(new Insets(10.0));

        Scene scene = new Scene(root, 1040.0, 760.0);
        applyCurrentTheme(scene);
        appendSceneStylesheet(scene, PAYMENT_DIALOG_STYLESHEET);
        stage.setScene(scene);

        WebEngine webEngine = webView.getEngine();
        String successPrefix = normalizeText(resolveStripeSuccessUrlPrefix());
        String cancelPrefix = normalizeText(resolveStripeCancelUrlPrefix());

        webEngine.locationProperty().addListener((observable, previous, current) -> {
            String currentUrl = normalizeText(current);
            if (currentUrl == null) {
                return;
            }
            if (successPrefix != null && currentUrl.startsWith(successPrefix)) {
                statusLabel.setText("Paiement confirme. Verification en cours...");
                resultHolder[0] = reservationService.refreshReservationPaymentStatus(
                    launchResult.reservationId(),
                    participantId
                );
                stage.close();
                return;
            }
            if (cancelPrefix != null && currentUrl.startsWith(cancelPrefix)) {
                resultHolder[0] = ReservationService.PaymentVerificationResult.failure(
                    "Paiement Stripe annule. Vous pouvez relancer le checkout."
                );
                stage.close();
                return;
            }
            if (currentUrl.startsWith("https://checkout.stripe.com/")) {
                statusLabel.setText("Formulaire Stripe pret. Saisissez vos informations bancaires dans Checkout.");
                loadingBar.setVisible(false);
                loadingBar.setManaged(false);
            }
        });

        webEngine.getLoadWorker().exceptionProperty().addListener((observable, previous, current) -> {
            if (current != null) {
                loadFailedHolder[0] = true;
                loadingBar.setVisible(false);
                loadingBar.setManaged(false);
                statusLabel.setText(
                    "Impossible d'afficher Stripe dans la fenetre integree. Utilisez le bouton navigateur."
                );
            }
        });

        stage.setOnCloseRequest(event -> {
            if (resultHolder[0] == null) {
                resultHolder[0] = ReservationService.PaymentVerificationResult.failure(
                    "Paiement Stripe interrompu avant confirmation."
                );
            }
        });

        webEngine.load(checkoutUrl);
        stage.showAndWait();
        return resultHolder[0];
    }

    private Window resolveReservationWindow() {
        if (studentReservationsContainer != null && studentReservationsContainer.getScene() != null) {
            return studentReservationsContainer.getScene().getWindow();
        }
        if (recentSessionsContainer != null && recentSessionsContainer.getScene() != null) {
            return recentSessionsContainer.getScene().getWindow();
        }
        if (reservationActionFeedbackLabel != null && reservationActionFeedbackLabel.getScene() != null) {
            return reservationActionFeedbackLabel.getScene().getWindow();
        }
        if (studentReservationsFeedbackLabel != null && studentReservationsFeedbackLabel.getScene() != null) {
            return studentReservationsFeedbackLabel.getScene().getWindow();
        }
        return null;
    }

    private String resolveStripeSuccessUrlPrefix() {
        String configured = normalizeText(tn.esprit.fahamni.utils.LocalConfig.get("STRIPE_SUCCESS_URL"));
        if (configured == null) {
            return null;
        }
        int tokenIndex = configured.indexOf("{CHECKOUT_SESSION_ID}");
        return tokenIndex >= 0 ? configured.substring(0, tokenIndex) : configured;
    }

    private String resolveStripeCancelUrlPrefix() {
        return normalizeText(tn.esprit.fahamni.utils.LocalConfig.get("STRIPE_CANCEL_URL"));
    }

    private void applyCurrentTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        Window owner = resolveReservationWindow();
        if (owner == null || owner.getScene() == null) {
            return;
        }
        scene.getStylesheets().setAll(owner.getScene().getStylesheets());
    }

    private void appendSceneStylesheet(Scene scene, String stylesheetPath) {
        if (scene == null || stylesheetPath == null || stylesheetPath.isBlank()) {
            return;
        }

        URL resource = getClass().getResource(stylesheetPath);
        if (resource == null) {
            return;
        }

        String stylesheet = resource.toExternalForm();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }

    private StudentReservationItem findStudentReservationItem(int participantId, int reservationId) {
        return reservationService.getStudentReservations(participantId).stream()
            .filter(item -> item.id() == reservationId)
            .findFirst()
            .orElse(null);
    }

    private VBox buildPaymentStepItem(String title, boolean active) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("payment-dialog-step-title");

        Region line = new Region();
        line.getStyleClass().add("payment-dialog-step-line");

        VBox item = new VBox(8.0, titleLabel, line);
        item.getStyleClass().add("payment-dialog-step-item");
        if (active) {
            item.getStyleClass().add("active");
            titleLabel.getStyleClass().add("active");
            line.getStyleClass().add("active");
        }
        item.setAlignment(Pos.CENTER);
        item.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(item, Priority.ALWAYS);
        return item;
    }

    private VBox buildPaymentDetailField(String labelText, String valueText, boolean compact) {
        Label label = new Label(labelText);
        label.getStyleClass().add("payment-dialog-field-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("payment-dialog-field-value");

        Region divider = new Region();
        divider.getStyleClass().add("payment-dialog-field-divider");

        VBox field = new VBox(5.0, label, value, divider);
        field.getStyleClass().add("payment-dialog-detail-field");
        if (compact) {
            field.getStyleClass().add("compact");
        }
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private String resolvePaymentDialogAmount(StudentReservationItem reservation) {
        if (reservation == null) {
            return "Montant a confirmer";
        }
        if (reservation.sessionPriceTnd() != null && reservation.sessionPriceTnd() > 0.0) {
            return formatSessionPrice(reservation.sessionPriceTnd());
        }
        if (reservation.paymentAmountMillimes() != null && reservation.paymentAmountMillimes() > 0) {
            return formatPriceTnd(reservation.paymentAmountMillimes() / 1000.0) + " TND";
        }
        return "Montant a confirmer";
    }

    private String resolvePaymentPreviewCardNumber() {
        return "XXXX XXXX XXXX XXXX";
    }

    private boolean isStripeTestMode() {
        String secretKey = normalizeText(tn.esprit.fahamni.utils.LocalConfig.get("STRIPE_SECRET_KEY"));
        return secretKey != null && secretKey.startsWith("sk_test_");
    }

    private boolean isTerminalPaymentStatus(String paymentStatus) {
        return ReservationService.PAYMENT_STATUS_COMPLETED.equals(paymentStatus)
            || ReservationService.PAYMENT_STATUS_FAILED.equals(paymentStatus)
            || ReservationService.PAYMENT_STATUS_EXPIRED.equals(paymentStatus);
    }

    private boolean openExternalUrl(String url) {
        String normalizedUrl = normalizeText(url);
        if (normalizedUrl == null) {
            return false;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop.getDesktop().browse(URI.create(normalizedUrl));
            return true;
        } catch (Exception exception) {
            return false;
        }
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
        sessionPriceField.clear();
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
        updateMatchingWorkspaceAvailability();
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

    private void updateMatchingWorkspaceAvailability() {
        boolean visible = UserSession.isCurrentStudent() || UserSession.isCurrentTutor();
        setNodeVisible(matchingSwipeButton, visible);
        if (matchingSwipeButton != null) {
            matchingSwipeButton.setText(UserSession.isCurrentTutor() ? "Inbox matching" : "Matching swipe");
        }
    }

    private double parsePriceTnd(String value, String errorMessage) {
        String candidate = requireText(value, errorMessage).replace(',', '.');
        try {
            double parsedValue = Double.parseDouble(candidate);
            if (parsedValue < SeanceService.MIN_PRICE_TND || parsedValue > SeanceService.MAX_PRICE_TND) {
                throw new NumberFormatException();
            }
            return Math.round(parsedValue * 1000.0) / 1000.0;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
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

    private void updateStudentMatchingResultsChrome(Node resultSummaryCard,
                                                    Node resultToolbar,
                                                    Label swipeGuideLabel,
                                                    Label feedbackLabel) {
        boolean feedbackVisible = feedbackLabel != null
            && feedbackLabel.getText() != null
            && !feedbackLabel.getText().isBlank()
            && feedbackLabel.getStyleClass().contains("error");
        setNodeVisible(resultSummaryCard, false);
        setNodeVisible(resultToolbar, false);
        setNodeVisible(swipeGuideLabel, false);
        setNodeVisible(feedbackLabel, feedbackVisible);
    }

    private void setInlineFeedback(Label label, String message, boolean success) {
        if (label == null) {
            return;
        }

        boolean visible = message != null && !message.isBlank();
        label.setText(visible ? message : "");
        label.getStyleClass().removeAll("success", "error");
        if (visible) {
            label.getStyleClass().add(success ? "success" : "error");
        }
        label.setManaged(visible);
        label.setVisible(visible);
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

    private static final class StudentMatchingDialogState {
        private int requestId;
        private int currentIndex;
        private boolean animating;
        private MatchingNeedProfile needProfile;
        private List<StudentMatchCard> cards = List.of();
        private List<StudentPositiveMatch> positiveMatches = new ArrayList<>();
    }

    private static final class TutorMatchingDialogState {
        private int currentIndex;
        private List<TutorMatchInboxItem> cards = List.of();
    }

    private record StudentPositiveMatch(StudentMatchCard card, int decision) {
    }

    private record PendingMatchingPlan(int requestId,
                                       int participantId,
                                       String participantName,
                                       String visibilityScope) {
    }

    private record EquipmentSelectionControls(CheckBox checkBox, Spinner<Integer> quantitySpinner, VBox card, boolean selectable) {
    }

    private record RoomSelectionControls(VBox card, Label statusChip) {
    }
}

