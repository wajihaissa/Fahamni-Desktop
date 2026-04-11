package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.interfaces.ISeanceSearchService;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.ReservationService;
import tn.esprit.fahamni.services.ReservationService.ReservationStats;
import tn.esprit.fahamni.services.ReservationService.TutorReservationRequest;
import tn.esprit.fahamni.services.SeanceService;
import tn.esprit.fahamni.services.TemporaryUserContext;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
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
    private static final String SECTION_AVAILABLE_SESSIONS = "Seances disponibles";
    private static final String SECTION_ADD_SESSION = "Ajouter une seance";
    private static final String SECTION_RESERVATION_REQUESTS = "Demandes de reservation";
    private static final List<Integer> SESSION_PAGE_SIZE_OPTIONS = List.of(5, 10, 20);
    private static final int DEFAULT_SESSION_PAGE_SIZE = 5;
    private static final List<DateTimeFormatter> INPUT_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );

    private final SeanceService seanceService = new SeanceService();
    private final IServices<Seance> seanceCrudService = seanceService;
    private final ISeanceSearchService seanceSearchService = seanceService;
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();
    private final ReservationService reservationService = new ReservationService();
    private Integer editingSessionId;
    private int currentSessionPage = 1;

    @FXML
    private Label publishedSessionsCountLabel;

    @FXML
    private Label draftSessionsCountLabel;

    @FXML
    private ComboBox<String> tutorComboBox;

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
    private Label reservationActionFeedbackLabel;

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
        tutorComboBox.getItems().setAll(tutorDirectoryService.getTutorNames());
        tutorComboBox.setEditable(true);
        selectTemporaryTutor();

        configureWorkspaceSections();
        sectionMenuComboBox.setValue(SECTION_AVAILABLE_SESSIONS);
        sessionSearchModeComboBox.getItems().setAll(seanceSearchService.getAvailableSearchStatuses());
        sessionSearchModeComboBox.setValue("Toutes les seances");
        sessionsPerPageComboBox.getItems().setAll(SESSION_PAGE_SIZE_OPTIONS);
        sessionsPerPageComboBox.setValue(DEFAULT_SESSION_PAGE_SIZE);

        resetEditMode();
        hideFeedback();
        hideReservationActionFeedback();
        hideTutorReservationRequestsFeedback();
        loadSessionDashboard();
        showAvailableSessionsSection();
    }

    @FXML
    private void handleChangeWorkspaceSection() {
        if (SECTION_ADD_SESSION.equals(sectionMenuComboBox.getValue())) {
            showAddSessionSection();
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
            showAvailableSessionsSection();
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
            0
        );
        boolean hasSessionsInDatabase = !seanceCrudService.getAll().isEmpty();

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
        detailsButton.getStyleClass().addAll("action-button", "secondary");
        detailsButton.setOnAction(event -> showSessionDetails(seance, reservationStats));

        Button reserveButton = buildReserveButton(seance, reservationStats);

        Button editButton = new Button("Modifier");
        editButton.getStyleClass().addAll("action-button", "secondary");
        editButton.setOnAction(event -> startEditingSession(seance));

        Button deleteButton = new Button("Supprimer");
        deleteButton.getStyleClass().addAll("action-button", "danger");
        deleteButton.setOnAction(event -> confirmDeleteSession(seance));

        actionRow.getChildren().addAll(idChip, actionSpacer);
        if (TemporaryUserContext.isCurrentStudent()) {
            actionRow.getChildren().add(reserveButton);
        }
        actionRow.getChildren().add(detailsButton);
        if (canManageSession(seance)) {
            actionRow.getChildren().addAll(editButton, deleteButton);
        }

        card.getChildren().addAll(headerRow, metaLabel, descriptionLabel, actionRow);
        return card;
    }

    private boolean canManageSession(Seance seance) {
        return TemporaryUserContext.isCurrentTutor()
            && seance != null
            && seance.getTuteurId() == TemporaryUserContext.getCurrentTutorId();
    }

    private Button buildReserveButton(Seance seance, ReservationStats reservationStats) {
        Button reserveButton = new Button("Reserver");
        reserveButton.getStyleClass().addAll("action-button", "primary");

        if (!TemporaryUserContext.isCurrentStudent()) {
            reserveButton.setText("Compte etudiant requis");
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

        if (reservationService.hasActiveReservation(seance.getId(), TemporaryUserContext.getCurrentStudentId())) {
            reserveButton.setText("Deja reserve");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        reserveButton.setOnAction(event -> reserveSession(seance));
        return reserveButton;
    }

    private void reserveSession(Seance seance) {
        OperationResult result = reservationService.reserveSeance(seance, TemporaryUserContext.getCurrentStudentId());
        loadSessionDashboard();
        showAvailableSessionsSection();
        showReservationActionFeedback(result.getMessage(), result.isSuccess());
    }

    private void loadTutorReservationRequests() {
        tutorReservationRequestsContainer.getChildren().clear();

        List<TutorReservationRequest> requests = reservationService.getTutorReservationRequests(
            TemporaryUserContext.getCurrentTutorId()
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
                + " | capacite " + request.maxParticipants()
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
        acceptButton.getStyleClass().addAll("action-button", "primary");
        acceptButton.setOnAction(event -> acceptTutorReservationRequest(request));

        Button refuseButton = new Button("Refuser");
        refuseButton.getStyleClass().addAll("action-button", "danger");
        refuseButton.setOnAction(event -> confirmRefuseTutorReservationRequest(request));

        actionRow.getChildren().addAll(idChip, actionSpacer);
        if (request.isPending()) {
            actionRow.getChildren().addAll(acceptButton, refuseButton);
        } else {
            Button statusButton = new Button(mapReservationRequestStatusLabel(request.status()));
            statusButton.getStyleClass().addAll("action-button", "secondary");
            statusButton.setDisable(true);
            actionRow.getChildren().add(statusButton);
        }
        card.getChildren().addAll(headerRow, studentLabel, sessionLabel, actionRow);
        return card;
    }

    private void acceptTutorReservationRequest(TutorReservationRequest request) {
        hideTutorReservationRequestsFeedback();
        OperationResult result = reservationService.acceptReservation(
            request.id(),
            TemporaryUserContext.getCurrentTutorId()
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
            TemporaryUserContext.getCurrentTutorId()
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
        LocalDateTime endAt = seance.getStartAt() != null
            ? seance.getStartAt().plusMinutes(seance.getDurationMin())
            : null;
        int availableSeats = Math.max(0, seance.getMaxParticipants() - reservationStats.total());
        double occupancyRate = calculateOccupancyRate(reservationStats.total(), seance.getMaxParticipants());

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

        Label reservationChip = new Label(formatReservationCount(reservationStats.total()));
        reservationChip.getStyleClass().add("session-detail-reservation-chip");
        header.getChildren().addAll(titleBlock, headerSpacer, reservationChip);

        FlowPane metrics = new FlowPane(10.0, 10.0);
        metrics.getStyleClass().add("session-detail-metrics");
        metrics.setPrefWrapLength(560.0);
        metrics.getChildren().addAll(
            buildDetailMetric("Debut", formatDateTimeOrPlaceholder(seance.getStartAt()), "Date et heure"),
            buildDetailMetric("Fin", formatDateTimeOrPlaceholder(endAt), "Fin calculee"),
            buildDetailMetric("Duree", seance.getDurationMin() + " min", "Temps de seance"),
            buildDetailMetric("Places", availableSeats + " / " + seance.getMaxParticipants(), "Disponibles"),
            buildDetailMetric("Reservations", String.valueOf(reservationStats.total()), "Actives"),
            buildDetailMetric("Tuteur", tutorDirectoryService.getTutorDisplayName(seance.getTuteurId()), "ID " + seance.getTuteurId())
        );

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
        statusRow.getChildren().addAll(
            buildReservationStatusChip("En attente", reservationStats.pending()),
            buildReservationStatusChip("Acceptees", reservationStats.accepted()),
            buildReservationStatusChip("Refusees", reservationStats.refused())
        );
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
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        Button editActionButton = new Button("Modifier cette seance");
        editActionButton.getStyleClass().add("session-detail-action-button");
        editActionButton.setOnAction(event -> {
            detailsDialog.close();
            startEditingSession(seance);
        });
        footer.getChildren().add(createdAtLabel);
        if (seance.getUpdatedAt() != null) {
            Label updatedAtLabel = new Label("Mise a jour: " + formatDateTimeOrPlaceholder(seance.getUpdatedAt()));
            updatedAtLabel.getStyleClass().add("session-detail-muted");
            footer.getChildren().add(updatedAtLabel);
        }
        footer.getChildren().add(footerSpacer);
        if (canManageSession(seance)) {
            footer.getChildren().add(editActionButton);
        }

        root.getChildren().addAll(header, metrics, occupancyCard, descriptionBox, footer);
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
            pageButton.getStyleClass().add("pagination-page-button");
            if (page == currentSessionPage) {
                pageButton.getStyleClass().add("active");
            }
            pageButton.setDisable(page == currentSessionPage);
            pageButton.setOnAction(event -> {
                currentSessionPage = page;
                applySessionFilters();
            });
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
        if (!canManageSession(seance)) {
            showAvailableSessionsSection();
            showReservationActionFeedback("Seul le tuteur proprietaire peut modifier cette seance.", false);
            return;
        }

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
        showAddSessionSection();
    }

    private void confirmDeleteSession(Seance seance) {
        if (!canManageSession(seance)) {
            showAvailableSessionsSection();
            showReservationActionFeedback("Seul le tuteur proprietaire peut supprimer cette seance.", false);
            return;
        }

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

    private String formatReservationCount(int reservationCount) {
        if (reservationCount <= 0) {
            return "0 reservation";
        }
        return reservationCount == 1 ? "1 reservation" : reservationCount + " reservations";
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

    private void clearSessionForm() {
        sessionSubjectField.clear();
        sessionStartAtField.clear();
        sessionDurationField.clear();
        sessionCapacityField.clear();
        sessionDescriptionArea.clear();
        selectTemporaryTutor();
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
        setSectionVisible(sessionSearchPanel, true);
        setSectionVisible(sessionListPanel, true);
        setSectionVisible(sessionFormPanel, false);
        setSectionVisible(reservationRequestsPanel, false);
    }

    private void showAddSessionSection() {
        if (!TemporaryUserContext.isCurrentTutor()) {
            showAvailableSessionsSection();
            showReservationActionFeedback("Connectez-vous avec le compte tuteur pour ajouter une seance.", false);
            return;
        }

        if (sectionMenuComboBox != null && !SECTION_ADD_SESSION.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_ADD_SESSION);
        }
        setSectionVisible(sessionSearchPanel, false);
        setSectionVisible(sessionListPanel, false);
        setSectionVisible(sessionFormPanel, true);
        setSectionVisible(reservationRequestsPanel, false);
    }

    private void showReservationRequestsSection() {
        if (!TemporaryUserContext.isCurrentTutor()) {
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
        setSectionVisible(reservationRequestsPanel, true);
        loadTutorReservationRequests();
    }

    private void configureWorkspaceSections() {
        if (TemporaryUserContext.isCurrentTutor()) {
            sectionMenuComboBox.getItems().setAll(
                SECTION_AVAILABLE_SESSIONS,
                SECTION_ADD_SESSION,
                SECTION_RESERVATION_REQUESTS
            );
            return;
        }

        sectionMenuComboBox.getItems().setAll(SECTION_AVAILABLE_SESSIONS);
    }

    private void selectTemporaryTutor() {
        String temporaryTutorName = tutorDirectoryService.getTutorDisplayName(TemporaryUserContext.getCurrentTutorId());
        if (tutorComboBox.getItems().contains(temporaryTutorName)) {
            tutorComboBox.setValue(temporaryTutorName);
        } else if (!tutorComboBox.getItems().isEmpty()) {
            tutorComboBox.setValue(tutorComboBox.getItems().get(0));
        } else {
            tutorComboBox.setValue(null);
        }
    }

    private void setSectionVisible(VBox section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setManaged(visible);
        section.setVisible(visible);
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
}

