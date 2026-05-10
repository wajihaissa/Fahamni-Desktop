package tn.esprit.fahamni.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import tn.esprit.fahamni.Models.AdminCustomizationRequestItem;
import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.Models.SessionRoomCustomizationConfig;
import tn.esprit.fahamni.Models.SessionRoomCustomizationRequest;
import tn.esprit.fahamni.room3d.Room3DPreviewData;
import tn.esprit.fahamni.room3d.Room3DPreviewService;
import tn.esprit.fahamni.room3d.Room3DViewMode;
import tn.esprit.fahamni.room3d.Room3DViewerLauncher;
import tn.esprit.fahamni.room3d.RoomSeatVisualState;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.AdminSessionService;
import tn.esprit.fahamni.services.SessionRoomCustomizationService;
import tn.esprit.fahamni.utils.BackOfficeUiTheme;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class BackofficeCustomizationRequestsController {

    private static final String ALL_STATUSES = "Tous les statuts";
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private Label totalRequestsLabel;

    @FXML
    private Label pendingRequestsLabel;

    @FXML
    private Label approvedRequestsLabel;

    @FXML
    private Label requestsCountLabel;

    @FXML
    private Label feedbackLabel;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private FlowPane requestCardsContainer;

    private final AdminSessionService sessionService = new AdminSessionService();
    private final AdminSalleService salleService = new AdminSalleService();
    private final SessionRoomCustomizationService customizationService = new SessionRoomCustomizationService();
    private final Room3DPreviewService room3DPreviewService = new Room3DPreviewService();
    private final ObservableList<AdminCustomizationRequestItem> requestItems = FXCollections.observableArrayList();
    private final Map<Integer, String> roomNameCache = new HashMap<>();
    private final Map<Integer, Salle> roomCache = new HashMap<>();

    private FilteredList<AdminCustomizationRequestItem> filteredRequests;
    private Map<Integer, AdminSession> sessionsById = Map.of();

    @FXML
    private void initialize() {
        statusFilterComboBox.getItems().setAll(
            ALL_STATUSES,
            "En attente",
            "En analyse",
            "Approuvee",
            "Rejetee",
            "Annulee"
        );
        statusFilterComboBox.setValue(ALL_STATUSES);

        filteredRequests = new FilteredList<>(requestItems, item -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());

        loadRequests();
    }

    @FXML
    private void handleRefreshRequests() {
        loadRequests();
        showFeedback("La liste des demandes a ete actualisee.", true);
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        statusFilterComboBox.setValue(ALL_STATUSES);
        applyFilters();
        hideFeedback();
    }

    private void loadRequests() {
        roomNameCache.clear();
        roomCache.clear();

        sessionService.reloadSessions();
        sessionsById = sessionService.getSessions().stream()
            .collect(Collectors.toMap(AdminSession::getId, session -> session, (left, right) -> left, LinkedHashMap::new));

        List<AdminCustomizationRequestItem> loadedItems = customizationService.listAllRequests().stream()
            .map(this::buildAdminRequestItem)
            .toList();

        requestItems.setAll(loadedItems);
        updateCounters();
        applyFilters();
    }

    private AdminCustomizationRequestItem buildAdminRequestItem(SessionRoomCustomizationRequest request) {
        AdminSession session = sessionsById.get(request.seanceId());
        SessionRoomCustomizationConfig effectiveConfig = request.requestedConfig() != null
            ? request.requestedConfig()
            : request.effectiveApprovedConfig();

        String sessionSubject = session == null
            ? "Seance #" + request.seanceId()
            : safeText(session.getSubject());
        String tutor = session == null
            ? "Tuteur #" + request.tuteurId()
            : safeText(session.getTutor());
        String roomName = resolveRoomName(request.baseSalleId());

        return new AdminCustomizationRequestItem(
            request.idDemande(),
            request.seanceId(),
            sessionSubject,
            tutor,
            roomName,
            formatCustomizationStatusLabel(request.status()),
            formatDateTime(request.createdAt()),
            formatDateTime(request.reviewedAt()),
            compactText(buildSummary(effectiveConfig, request.commentTuteur()), 160),
            request.createdAt(),
            request
        );
    }

    private String buildSummary(SessionRoomCustomizationConfig configuration, String tutorComment) {
        List<String> parts = new ArrayList<>();
        parts.add(formatCustomizationConfigSummary(configuration));
        String normalizedComment = normalize(tutorComment);
        if (!normalizedComment.isBlank()) {
            parts.add("Note: " + tutorComment.trim());
        }
        return String.join(" | ", parts);
    }

    private String resolveRoomName(int roomId) {
        if (roomId <= 0) {
            return "Salle inconnue";
        }
        return roomNameCache.computeIfAbsent(roomId, id -> {
            Salle salle = resolveBaseSalle(id);
            return salle == null ? "Salle #" + id : safeText(salle.getNom());
        });
    }

    private void updateCounters() {
        long pendingCount = requestItems.stream().filter(item -> item.getRequest().isPendingReview()).count();
        long approvedCount = requestItems.stream().filter(item -> item.getRequest().isApproved()).count();

        totalRequestsLabel.setText(String.valueOf(requestItems.size()));
        pendingRequestsLabel.setText(String.valueOf(pendingCount));
        approvedRequestsLabel.setText(String.valueOf(approvedCount));
    }

    private void applyFilters() {
        if (filteredRequests == null) {
            return;
        }

        String query = normalize(searchField.getText());
        String selectedStatus = statusFilterComboBox.getValue();

        filteredRequests.setPredicate(item -> matchesSearch(item, query) && matchesStatus(item, selectedStatus));
        renderRequestCards(filteredRequests);
        updateRequestsCount();
    }

    private boolean matchesSearch(AdminCustomizationRequestItem item, String query) {
        if (query.isBlank()) {
            return true;
        }

        return normalize(item.getSessionSubject()).contains(query)
            || normalize(item.getTutor()).contains(query)
            || normalize(item.getRoomName()).contains(query)
            || normalize(item.getStatus()).contains(query)
            || normalize(item.getSummary()).contains(query)
            || String.valueOf(item.getSessionId()).contains(query);
    }

    private boolean matchesStatus(AdminCustomizationRequestItem item, String selectedStatus) {
        return selectedStatus == null
            || ALL_STATUSES.equals(selectedStatus)
            || selectedStatus.equals(item.getStatus());
    }

    private void renderRequestCards(List<AdminCustomizationRequestItem> items) {
        requestCardsContainer.getChildren().clear();

        if (items == null || items.isEmpty()) {
            requestCardsContainer.getChildren().add(buildEmptyStateCard());
            return;
        }

        for (AdminCustomizationRequestItem item : items) {
            requestCardsContainer.getChildren().add(buildRequestCard(item));
        }
    }

    private VBox buildRequestCard(AdminCustomizationRequestItem item) {
        SessionRoomCustomizationRequest request = item.getRequest();
        SessionRoomCustomizationConfig configuration = request.requestedConfig() != null
            ? request.requestedConfig()
            : request.effectiveApprovedConfig();

        VBox card = new VBox(14.0);
        card.getStyleClass().add("backoffice-request-card");
        card.setPrefWidth(334.0);
        card.setMinWidth(310.0);
        card.setMaxWidth(360.0);

        Label titleLabel = new Label(item.getSessionSubject());
        titleLabel.getStyleClass().add("backoffice-section-title");
        titleLabel.setWrapText(true);

        Label tutorLabel = new Label(item.getTutor());
        tutorLabel.getStyleClass().add("backoffice-panel-copy");

        VBox titleBlock = new VBox(4.0, titleLabel, tutorLabel);
        HBox.setHgrow(titleBlock, Priority.ALWAYS);

        VBox rightBlock = new VBox(8.0);
        rightBlock.setAlignment(Pos.TOP_RIGHT);

        HBox actionBox = new HBox(8.0);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button viewButton = buildActionButton("\u25CE", "Voir les details et ouvrir le modele 3D");
        viewButton.getStyleClass().add("view-action");
        viewButton.setOnAction(event -> openCustomizationReviewDialog(resolveSession(item), request));

        Button approveButton = buildActionButton("\u2713", "Approuver la demande");
        approveButton.getStyleClass().add("approve-action");
        approveButton.setDisable(!request.canBeReviewedByAdmin());
        approveButton.setOnAction(event -> handleQuickApproval(item));

        Button rejectButton = buildActionButton("\u2715", "Refuser la demande");
        rejectButton.getStyleClass().add("reject-action");
        rejectButton.setDisable(!request.canBeReviewedByAdmin());
        rejectButton.setOnAction(event -> handleQuickRejection(item));

        actionBox.getChildren().addAll(viewButton, approveButton, rejectButton);
        rightBlock.getChildren().addAll(buildCustomizationStatusBadge(request.status()), actionBox);

        HBox header = new HBox(14.0, titleBlock, rightBlock);
        header.setAlignment(Pos.TOP_LEFT);

        FlowPane meta = new FlowPane(8.0, 8.0);
        meta.getChildren().addAll(
            buildMetaChip(item.getRoomName()),
            buildMetaChip(item.getRequestDate())
        );
        if (configuration != null && configuration.capacity() != null) {
            meta.getChildren().add(buildMetaChip(configuration.capacity() + " places"));
        }
        if (configuration != null && !normalize(configuration.disposition()).isBlank()) {
            meta.getChildren().add(buildMetaChip("Disposition " + configuration.disposition()));
        }

        Label helperLabel = new Label(request.canBeReviewedByAdmin()
            ? "Visualisez, validez ou refusez cette demande depuis les actions rapides."
            : "Cette demande est deja traitee. Seule la visualisation reste disponible.");
        helperLabel.getStyleClass().add("backoffice-panel-copy");
        helperLabel.setWrapText(true);

        card.getChildren().addAll(header, meta, helperLabel);
        return card;
    }

    private AdminSession resolveSession(AdminCustomizationRequestItem item) {
        return item == null ? null : sessionsById.get(item.getSessionId());
    }

    private Button buildActionButton(String symbol, String tooltipText) {
        Button button = new Button(symbol);
        button.getStyleClass().add("backoffice-request-action-button");
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    private Label buildMetaChip(String value) {
        Label chip = new Label(value);
        chip.getStyleClass().add("backoffice-request-chip");
        return chip;
    }

    private VBox buildEmptyStateCard() {
        VBox emptyCard = new VBox(8.0);
        emptyCard.getStyleClass().add("backoffice-request-card");
        emptyCard.setPrefWidth(1040.0);

        Label title = new Label("Aucune demande visible");
        title.getStyleClass().add("backoffice-section-title");

        Label copy = new Label("Ajustez vos filtres ou attendez une nouvelle personnalisation tuteur.");
        copy.getStyleClass().add("backoffice-panel-copy");
        copy.setWrapText(true);

        emptyCard.getChildren().addAll(title, copy);
        return emptyCard;
    }

    private void updateRequestsCount() {
        int count = filteredRequests == null ? requestItems.size() : filteredRequests.size();
        String suffix = count > 1 ? "demandes affichees" : "demande affichee";
        requestsCountLabel.setText(count + " " + suffix);
    }

    private void handleQuickApproval(AdminCustomizationRequestItem item) {
        hideFeedback();

        AdminSession session = resolveSession(item);
        if (session == null) {
            showFeedback("La seance liee a cette demande est introuvable.", false);
            return;
        }

        SessionRoomCustomizationRequest request = item.getRequest();
        SessionRoomCustomizationConfig configuration = request.requestedConfig() != null
            ? request.requestedConfig()
            : request.effectiveApprovedConfig();
        if (configuration == null) {
            showFeedback("La configuration demandee est introuvable.", false);
            return;
        }

        String blockingReason = validateCustomizationApproval(session, configuration);
        if (blockingReason != null) {
            showFeedback(blockingReason, false);
            return;
        }

        if (!confirmDecision(
            "Approuver la demande ?",
            "Cette action validera la personnalisation pour " + safeText(session.getSubject()) + ".",
            "Approuver"
        )) {
            return;
        }

        try {
            customizationService.approveRequest(session.getId(), configuration, request.commentAdmin());
            loadRequests();
            showFeedback("La personnalisation de salle a ete approuvee.", true);
        } catch (Exception exception) {
            showFeedback(resolveReadableMessage(exception), false);
        }
    }

    private void handleQuickRejection(AdminCustomizationRequestItem item) {
        hideFeedback();

        AdminSession session = resolveSession(item);
        if (session == null) {
            showFeedback("La seance liee a cette demande est introuvable.", false);
            return;
        }

        if (!confirmDecision(
            "Refuser la demande ?",
            "Cette action rejettera la personnalisation pour " + safeText(session.getSubject()) + ".",
            "Refuser"
        )) {
            return;
        }

        try {
            customizationService.rejectRequest(session.getId(), item.getRequest().commentAdmin());
            loadRequests();
            showFeedback("La personnalisation de salle a ete rejetee.", true);
        } catch (Exception exception) {
            showFeedback(resolveReadableMessage(exception), false);
        }
    }

    private boolean confirmDecision(String title, String content, String confirmLabel) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        ButtonType confirmButton = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        return alert.showAndWait().orElse(cancelButton) == confirmButton;
    }

    private void openCustomizationReviewDialog(AdminSession session, SessionRoomCustomizationRequest request) {
        if (session == null || request == null) {
            showFeedback("Impossible d'ouvrir cette demande.", false);
            return;
        }

        Salle baseSalle = resolveBaseSalle(request.baseSalleId());
        SessionRoomCustomizationConfig configToReview = request.requestedConfig() != null
            ? request.requestedConfig()
            : request.effectiveApprovedConfig();

        Label dialogFeedbackLabel = new Label();
        dialogFeedbackLabel.setWrapText(true);
        dialogFeedbackLabel.setManaged(false);
        dialogFeedbackLabel.setVisible(false);

        Label statusBadge = buildCustomizationStatusBadge(request.status());

        VBox header = new VBox(
            6.0,
            new Label("Details de la personnalisation"),
            new Label(safeText(session.getSubject()) + " | " + safeText(session.getSchedule()))
        );
        header.getChildren().get(0).setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        header.getChildren().get(1).setStyle("-fx-text-fill: #5d7196; -fx-font-size: 13px;");

        FlowPane sessionFacts = new FlowPane(12.0, 12.0);
        sessionFacts.setPrefWrapLength(720.0);
        sessionFacts.getChildren().addAll(
            buildDetailMetric("Demande", "#" + request.idDemande(), "Reference admin"),
            buildDetailMetric("Tuteur", safeText(session.getTutor()), "Intervenant concerne"),
            buildDetailMetric("Capacite session", session.getCapacity() + " places", "Capacite actuellement publiee"),
            buildDetailMetric("Salle de base", baseSalle == null ? "Introuvable" : safeText(baseSalle.getNom()), "Salle physique de reference"),
            buildDetailMetric("Statut", formatCustomizationStatusLabel(request.status()), "Workflow admin")
        );

        VBox requestedCard = buildTextCard(
            "Configuration demandee",
            configToReview == null ? "Aucune configuration detaillee." : formatCustomizationConfigSummary(configToReview)
        );
        VBox tutorCommentCard = buildTextCard(
            "Commentaire tuteur",
            normalize(request.commentTuteur()).isBlank() ? "Aucun commentaire tuteur." : request.commentTuteur()
        );

        VBox approvedCard = new VBox();
        if (request.isApproved() && request.effectiveApprovedConfig() != null) {
            approvedCard = buildTextCard(
                "Configuration approuvee",
                formatCustomizationConfigSummary(request.effectiveApprovedConfig())
            );
        }

        TextArea adminCommentArea = new TextArea(request.commentAdmin() == null ? "" : request.commentAdmin());
        adminCommentArea.setPromptText("Commentaire admin visible dans le suivi de la demande.");
        adminCommentArea.setPrefRowCount(4);
        adminCommentArea.setWrapText(true);
        adminCommentArea.setDisable(request.isReadOnlyForAdmin());
        VBox adminCommentBox = new VBox(6.0, buildSectionLabel("Commentaire admin"), adminCommentArea);

        Button previewButton = new Button("Ouvrir le modele 3D");
        Button markInReviewButton = new Button("En analyse");
        Button approveButton = new Button("Approuver");
        Button rejectButton = new Button("Rejeter");
        Button closeButton = new Button("Fermer");

        previewButton.getStyleClass().setAll("backoffice-dialog-action-button", "preview-action");
        markInReviewButton.getStyleClass().setAll("backoffice-dialog-action-button", "review-action");
        approveButton.getStyleClass().setAll("backoffice-dialog-action-button", "approve-action");
        rejectButton.getStyleClass().setAll("backoffice-dialog-action-button", "reject-action");
        closeButton.getStyleClass().setAll("backoffice-dialog-action-button", "secondary-action");

        HBox actions = new HBox(10.0, previewButton, markInReviewButton, approveButton, rejectButton, closeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(
            16.0,
            header,
            statusBadge,
            sessionFacts,
            requestedCard,
            tutorCommentCard,
            approvedCard,
            adminCommentBox,
            dialogFeedbackLabel,
            new Separator(),
            actions
        );
        content.setPadding(new Insets(20.0));
        content.setStyle("-fx-background-color: white;");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scrollPane, 780.0, 640.0);
        BackOfficeUiTheme.apply(scene);

        Stage stage = new Stage();
        stage.setTitle("Traitement personnalisation");
        stage.initModality(Modality.WINDOW_MODAL);
        Window owner = resolveWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setScene(scene);

        previewButton.setDisable(baseSalle == null || configToReview == null);
        markInReviewButton.setDisable(!request.canBeReviewedByAdmin());
        approveButton.setDisable(!request.canBeReviewedByAdmin());
        rejectButton.setDisable(!request.canBeReviewedByAdmin());
        previewButton.setOnAction(event -> {
            if (baseSalle == null || configToReview == null) {
                showModalFeedback(dialogFeedbackLabel, "Impossible d'ouvrir l'apercu 3D sans salle ou configuration.", false);
                return;
            }

            Room3DViewerLauncher.showPreview(buildCustomizationPreviewData(
                baseSalle,
                request.isApproved() && request.effectiveApprovedConfig() != null
                    ? request.effectiveApprovedConfig()
                    : configToReview,
                request.isApproved() ? "Configuration approuvee" : "Configuration demandee"
            ));
            showModalFeedback(dialogFeedbackLabel, "Le modele 3D a ete ouvert dans une fenetre dediee.", true);
        });

        markInReviewButton.setOnAction(event -> {
            try {
                customizationService.markInReview(session.getId(), adminCommentArea.getText());
                loadRequests();
                showFeedback("La demande est maintenant en cours d'analyse.", true);
                stage.close();
            } catch (Exception exception) {
                showModalFeedback(dialogFeedbackLabel, resolveReadableMessage(exception), false);
            }
        });

        approveButton.setOnAction(event -> {
            if (configToReview == null) {
                showModalFeedback(dialogFeedbackLabel, "La configuration demandee est introuvable.", false);
                return;
            }

            String blockingReason = validateCustomizationApproval(session, configToReview);
            if (blockingReason != null) {
                showModalFeedback(dialogFeedbackLabel, blockingReason, false);
                return;
            }

            try {
                customizationService.approveRequest(session.getId(), configToReview, adminCommentArea.getText());
                loadRequests();
                showFeedback("La personnalisation de salle a ete approuvee.", true);
                stage.close();
            } catch (Exception exception) {
                showModalFeedback(dialogFeedbackLabel, resolveReadableMessage(exception), false);
            }
        });

        rejectButton.setOnAction(event -> {
            try {
                customizationService.rejectRequest(session.getId(), adminCommentArea.getText());
                loadRequests();
                showFeedback("La personnalisation de salle a ete rejetee.", true);
                stage.close();
            } catch (Exception exception) {
                showModalFeedback(dialogFeedbackLabel, resolveReadableMessage(exception), false);
            }
        });

        closeButton.setOnAction(event -> stage.close());
        stage.showAndWait();
    }

    private Salle resolveBaseSalle(int salleId) {
        if (salleId <= 0) {
            return null;
        }

        Salle cachedSalle = roomCache.get(salleId);
        if (cachedSalle != null) {
            return cachedSalle;
        }

        try {
            Salle salle = salleService.recupererParId(salleId);
            if (salle != null) {
                roomCache.put(salleId, salle);
            }
            return salle;
        } catch (Exception exception) {
            return null;
        }
    }

    private String validateCustomizationApproval(AdminSession session, SessionRoomCustomizationConfig configuration) {
        if (session == null || configuration == null) {
            return null;
        }

        int effectiveLimit = configuration.hasSeatLayout()
            ? configuration.seatLayoutSize()
            : configuration.capacity() == null ? 0 : configuration.capacity();
        if (effectiveLimit > 0 && session.getCapacity() > effectiveLimit) {
            return "La seance est reglee sur " + session.getCapacity()
                + " participant(s), mais la personnalisation ne couvre que "
                + effectiveLimit + " place(s). Ajustez d'abord la capacite de la seance.";
        }
        return null;
    }

    private VBox buildTextCard(String title, String body) {
        VBox card = new VBox(8.0);
        card.setStyle("-fx-background-color: #f8fbff; -fx-border-color: #d8e4ff; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16;");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        Label bodyLabel = new Label(body);
        bodyLabel.setWrapText(true);
        bodyLabel.setStyle("-fx-text-fill: #334d78; -fx-font-size: 13px;");
        card.getChildren().addAll(titleLabel, bodyLabel);
        return card;
    }

    private Label buildSectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        return label;
    }

    private Label buildCustomizationStatusBadge(String status) {
        return switch (normalize(status)) {
            case "approved" -> buildBadge("Approuvee", "#e6f6ee", "#17663d");
            case "rejected" -> buildBadge("Rejetee", "#ffefef", "#b42318");
            case "in_review" -> buildBadge("En analyse", "#fff6e6", "#9a6700");
            case "cancelled" -> buildBadge("Annulee", "#edf2f7", "#4a5a73");
            default -> buildBadge("En attente", "#e8f0ff", "#2d5cc9");
        };
    }

    private String formatCustomizationStatusLabel(String status) {
        return switch (normalize(status)) {
            case "approved" -> "Approuvee";
            case "rejected" -> "Rejetee";
            case "in_review" -> "En analyse";
            case "cancelled" -> "Annulee";
            default -> "En attente";
        };
    }

    private String formatCustomizationConfigSummary(SessionRoomCustomizationConfig configuration) {
        if (configuration == null) {
            return "Aucune configuration detaillee.";
        }

        List<String> fragments = new ArrayList<>();
        fragments.add("Disposition " + safeText(configuration.disposition()));
        if (configuration.capacity() != null) {
            fragments.add(configuration.capacity() + " places");
        }
        if (!normalize(configuration.tableStyle()).isBlank()) {
            fragments.add("tables " + configuration.tableStyle());
        }
        if (!normalize(configuration.chairStyle()).isBlank()) {
            fragments.add("chaises " + configuration.chairStyle());
        }
        if (Boolean.TRUE.equals(configuration.accessibilityRequired())) {
            fragments.add("acces PMR renforce");
        }
        if (configuration.hasSeatLayout()) {
            fragments.add(configuration.seatLayoutSize() + " place(s) dans le layout");
        }
        return String.join(" | ", fragments);
    }

    private Room3DPreviewData buildCustomizationPreviewData(
        Salle baseSalle,
        SessionRoomCustomizationConfig configuration,
        String headline
    ) {
        Salle previewSalle = new Salle(
            baseSalle.getIdSalle(),
            baseSalle.getNom(),
            configuration.capacity() == null ? baseSalle.getCapacite() : configuration.capacity(),
            baseSalle.getLocalisation(),
            baseSalle.getTypeSalle(),
            baseSalle.getEtat(),
            baseSalle.getDescription(),
            baseSalle.getBatiment(),
            baseSalle.getEtage(),
            firstNonBlank(configuration.disposition(), baseSalle.getTypeDisposition(), "classe"),
            configuration.accessibilityRequiredOrDefault(baseSalle.isAccesHandicape()),
            baseSalle.getStatutDetaille(),
            baseSalle.getDateDerniereMaintenance()
        );

        List<Room3DPreviewData.SeatPreview> seats = configuration.hasSeatLayout()
            ? mapCustomizationSeats(configuration)
            : room3DPreviewService.buildPreview(previewSalle, true, Room3DViewMode.DESIGN_REVIEW).seats();

        return room3DPreviewService.buildPreviewFromSeats(
            previewSalle,
            Room3DViewMode.DESIGN_REVIEW,
            configuration.disposition(),
            seats
        ).withAnnotations(
            headline,
            "La comparaison 3D aide a verifier la capacite et la disposition approuvees.",
            formatCustomizationConfigSummary(configuration)
        ).withFurnitureStyles(configuration.tableStyle(), configuration.chairStyle());
    }

    private List<Room3DPreviewData.SeatPreview> mapCustomizationSeats(SessionRoomCustomizationConfig configuration) {
        List<Room3DPreviewData.SeatPreview> seats = new ArrayList<>();
        int number = 1;
        for (SessionRoomCustomizationConfig.SeatLayoutSlot slot : configuration.seatLayout()) {
            seats.add(new Room3DPreviewData.SeatPreview(
                slot.seatKey(),
                number++,
                slot.row(),
                slot.column(),
                RoomSeatVisualState.AVAILABLE,
                false
            ));
        }
        return seats;
    }

    private VBox buildDetailMetric(String label, String value, String hint) {
        VBox metricCard = new VBox(4.0);
        metricCard.setPrefWidth(190.0);
        metricCard.setStyle("-fx-background-color: #f8fbff; -fx-border-color: #d8e4ff; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 14;");

        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #6a80a8;");
        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        Label hintNode = new Label(hint);
        hintNode.setWrapText(true);
        hintNode.setStyle("-fx-font-size: 11px; -fx-text-fill: #7a8db2;");

        metricCard.getChildren().addAll(labelNode, valueNode, hintNode);
        return metricCard;
    }

    private Label buildBadge(String text, String backgroundColor, String textColor) {
        Label badge = new Label(text);
        badge.setStyle(
            "-fx-background-color: " + backgroundColor + "; "
                + "-fx-text-fill: " + textColor + "; "
                + "-fx-background-radius: 999; "
                + "-fx-padding: 7 14 7 14; "
                + "-fx-font-weight: bold;"
        );
        return badge;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Non renseignee" : value.format(DISPLAY_FORMATTER);
    }

    private String compactText(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeText(String value) {
        String normalizedValue = normalize(value);
        return normalizedValue.isBlank() ? "Non renseigne" : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            String normalizedValue = normalize(value);
            if (!normalizedValue.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String resolveReadableMessage(Throwable throwable) {
        if (throwable == null) {
            return "Veuillez reessayer dans un instant.";
        }

        String directMessage = normalize(throwable.getMessage());
        if (!directMessage.isBlank()) {
            return throwable.getMessage().trim();
        }

        Throwable cause = throwable.getCause();
        while (cause != null) {
            String causeMessage = normalize(cause.getMessage());
            if (!causeMessage.isBlank()) {
                return cause.getMessage().trim();
            }
            cause = cause.getCause();
        }
        return "Veuillez reessayer dans un instant.";
    }

    private void showModalFeedback(Label feedbackLabel, String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(
            "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (success ? "#17663d" : "#b42318") + ";"
        );
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private Window resolveWindow() {
        if (requestCardsContainer != null && requestCardsContainer.getScene() != null) {
            return requestCardsContainer.getScene().getWindow();
        }
        if (feedbackLabel != null && feedbackLabel.getScene() != null) {
            return feedbackLabel.getScene().getWindow();
        }
        return null;
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.getStyleClass().setAll("backoffice-feedback");
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
