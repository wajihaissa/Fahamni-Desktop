package tn.esprit.fahamni.controllers;

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
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.Scene;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BackofficeSessionsController {

    private static final String ALL_STATUSES = "Tous les statuts";

    @FXML
    private TableView<AdminSession> sessionsTable;

    @FXML
    private TableColumn<AdminSession, String> subjectColumn;

    @FXML
    private TableColumn<AdminSession, String> tutorColumn;

    @FXML
    private TableColumn<AdminSession, String> scheduleColumn;

    @FXML
    private TableColumn<AdminSession, Integer> capacityColumn;

    @FXML
    private TableColumn<AdminSession, Integer> durationColumn;

    @FXML
    private TableColumn<AdminSession, Integer> reservationsColumn;

    @FXML
    private TableColumn<AdminSession, String> statusColumn;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Label sessionsCountLabel;

    @FXML
    private TextField sessionSearchField;

    @FXML
    private ComboBox<String> sessionStatusFilterComboBox;

    @FXML
    private DatePicker sessionDatePicker;

    @FXML
    private Button viewSessionDetailsButton;

    @FXML
    private Button reviewCustomizationButton;

    @FXML
    private Button deleteSelectedSessionButton;

    private final AdminSessionService sessionService = new AdminSessionService();
    private final AdminSalleService salleService = new AdminSalleService();
    private final SessionRoomCustomizationService sessionRoomCustomizationService = new SessionRoomCustomizationService();
    private final Room3DPreviewService room3DPreviewService = new Room3DPreviewService();
    private FilteredList<AdminSession> filteredSessions;

    @FXML
    private void initialize() {
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        tutorColumn.setCellValueFactory(new PropertyValueFactory<>("tutor"));
        scheduleColumn.setCellValueFactory(new PropertyValueFactory<>("schedule"));
        capacityColumn.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        reservationsColumn.setCellValueFactory(new PropertyValueFactory<>("reservationCount"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        sessionStatusFilterComboBox.getItems().setAll(
            ALL_STATUSES,
            "Brouillon",
            "Publiee",
            "Archivee"
        );
        sessionStatusFilterComboBox.setValue(ALL_STATUSES);

        filteredSessions = new FilteredList<>(sessionService.getSessions(), session -> true);
        SortedList<AdminSession> sortedSessions = new SortedList<>(filteredSessions);
        sortedSessions.comparatorProperty().bind(sessionsTable.comparatorProperty());
        sessionsTable.setItems(sortedSessions);
        sessionsTable.setRowFactory(tableView -> buildSessionRow());

        sessionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> handleSessionSelectionChange(newValue));
        sessionSearchField.textProperty().addListener((observable, oldValue, newValue) -> applySessionFilters());
        sessionStatusFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySessionFilters());
        sessionDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> applySessionFilters());
        filteredSessions.addListener((ListChangeListener<AdminSession>) change -> updateSessionsCount());

        updateSessionsCount();
        updateActionButtons(null);
        showDatabaseStateIfNeeded();
    }

    @FXML
    private void handleRefreshSessions() {
        sessionService.reloadSessions();
        sessionsTable.getSelectionModel().clearSelection();
        applySessionFilters();
        sessionsTable.refresh();
        updateSessionsCount();
        updateActionButtons(null);
        showDatabaseStateIfNeeded();
    }

    @FXML
    private void handleViewSessionDetails() {
        AdminSession selectedSession = sessionsTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showFeedback("Selectionnez une seance pour afficher ses details.", false);
            return;
        }

        showSessionDetails(selectedSession);
    }

    @FXML
    private void handleReviewCustomization() {
        hideFeedback();

        AdminSession selectedSession = sessionsTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showFeedback("Selectionnez une seance pour traiter sa personnalisation.", false);
            return;
        }

        SessionRoomCustomizationRequest request = resolveCustomizationRequest(selectedSession.getId());
        if (request == null) {
            showFeedback("Aucune personnalisation n'est liee a cette seance.", false);
            updateActionButtons(selectedSession);
            return;
        }

        openCustomizationReviewDialog(selectedSession, request);
    }

    @FXML
    private void handleDeleteSelectedSession() {
        hideFeedback();

        AdminSession selectedSession = sessionsTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showFeedback("Selectionnez une seance a supprimer.", false);
            return;
        }

        if (selectedSession.getReservationCount() > 0) {
            showFeedback(buildDeleteBlockedMessage(selectedSession.getReservationCount()), false);
            updateActionButtons(selectedSession);
            return;
        }

        if (!confirmDeletion(selectedSession)) {
            return;
        }

        OperationResult result = sessionService.deleteSession(selectedSession);
        if (result.isSuccess()) {
            sessionsTable.getSelectionModel().clearSelection();
            applySessionFilters();
            sessionsTable.refresh();
            updateSessionsCount();
        }

        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleResetSessionFilters() {
        sessionSearchField.clear();
        sessionStatusFilterComboBox.setValue(ALL_STATUSES);
        sessionDatePicker.setValue(null);
        applySessionFilters();
        hideFeedback();
    }

    private void applySessionFilters() {
        if (filteredSessions == null) {
            return;
        }

        String query = normalize(sessionSearchField.getText());
        String selectedStatus = sessionStatusFilterComboBox.getValue();
        LocalDate selectedDate = sessionDatePicker.getValue();

        filteredSessions.setPredicate(session -> matchesSearch(session, query)
            && matchesStatus(session, selectedStatus)
            && matchesDate(session, selectedDate));
        updateSessionsCount();
    }

    private boolean matchesSearch(AdminSession session, String query) {
        if (query.isBlank()) {
            return true;
        }

        return normalize(session.getSubject()).contains(query)
            || normalize(session.getTutor()).contains(query)
            || normalize(session.getSchedule()).contains(query)
            || normalize(session.getStatus()).contains(query)
            || normalize(session.getDescription()).contains(query)
            || String.valueOf(session.getId()).contains(query)
            || String.valueOf(session.getTutorId()).contains(query)
            || String.valueOf(session.getReservationCount()).contains(query);
    }

    private boolean matchesStatus(AdminSession session, String selectedStatus) {
        return selectedStatus == null
            || ALL_STATUSES.equals(selectedStatus)
            || selectedStatus.equals(session.getStatus());
    }

    private boolean matchesDate(AdminSession session, LocalDate selectedDate) {
        return selectedDate == null || selectedDate.equals(session.getScheduleLocalDate());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void handleSessionSelectionChange(AdminSession selectedSession) {
        updateActionButtons(selectedSession);

        if (selectedSession == null) {
            showDatabaseStateIfNeeded();
            return;
        }

        if (selectedSession.getReservationCount() > 0) {
            showFeedback(buildDeleteBlockedMessage(selectedSession.getReservationCount()), false);
            return;
        }

        hideFeedback();
    }

    private void updateActionButtons(AdminSession selectedSession) {
        viewSessionDetailsButton.setDisable(selectedSession == null);
        SessionRoomCustomizationRequest request = selectedSession == null
            ? null
            : resolveCustomizationRequest(selectedSession.getId());
        reviewCustomizationButton.setDisable(selectedSession == null || request == null);
        reviewCustomizationButton.setText(request != null && request.isReadOnlyForAdmin()
            ? "Voir personnalisation"
            : "Traiter personnalisation");
        deleteSelectedSessionButton.setDisable(selectedSession == null || selectedSession.getReservationCount() > 0);
    }

    private String buildDeleteBlockedMessage(int reservationCount) {
        String suffix = reservationCount > 1 ? " reservations." : " reservation.";
        return "Suppression indisponible: cette seance possede " + reservationCount + suffix;
    }

    private boolean confirmDeletion(AdminSession session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Suppression de seance");
        alert.setHeaderText("Supprimer la seance selectionnee ?");
        alert.setContentText(session.getSubject() + " - " + session.getSchedule());

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showSessionDetails(AdminSession session) {
        Dialog<ButtonType> detailsDialog = new Dialog<>();
        DialogPane dialogPane = detailsDialog.getDialogPane();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);

        detailsDialog.setTitle("Detail de la seance");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setPrefWidth(720.0);
        dialogPane.setContent(buildSessionDetailsContent(session));

        detailsDialog.showAndWait();
    }

    private VBox buildSessionDetailsContent(AdminSession session) {
        VBox root = new VBox(16.0);
        root.setPrefWidth(660.0);
        root.setStyle("-fx-padding: 20; -fx-background-color: white;");

        HBox header = new HBox(12.0);
        VBox titleBlock = new VBox(6.0);
        Label titleLabel = new Label(safeText(session.getSubject()));
        titleLabel.setStyle("-fx-font-size: 23px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        Label subtitleLabel = new Label("Tuteur: " + safeText(session.getTutor()));
        subtitleLabel.setStyle("-fx-text-fill: #5d7196; -fx-font-size: 13px;");
        titleBlock.getChildren().addAll(titleLabel, subtitleLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Label statusChip = buildBadge(session.getStatus(), "#e8f0ff", "#2d5cc9");
        header.getChildren().addAll(titleBlock, headerSpacer, statusChip);

        FlowPane metrics = new FlowPane(12.0, 12.0);
        metrics.setPrefWrapLength(620.0);
        metrics.getChildren().addAll(
            buildDetailMetric("Date", safeText(session.getSchedule()), "Horaire planifie"),
            buildDetailMetric("Duree", session.getDurationMinutes() + " min", "Temps total"),
            buildDetailMetric("Capacite", session.getCapacity() + " places", "Capacite max"),
            buildDetailMetric("Reservations", formatReservationCount(session.getReservationCount()), "Demandes liees"),
            buildDetailMetric("Suppression", session.getReservationCount() > 0 ? "Bloquee" : "Disponible", "Etat de suppression"),
            buildDetailMetric("Tuteur", safeText(session.getTutor()), "Intervenant")
        );

        VBox descriptionCard = new VBox(8.0);
        descriptionCard.setStyle("-fx-background-color: #f8fbff; -fx-border-color: #d8e4ff; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16;");
        Label descriptionTitle = new Label("Description");
        descriptionTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        Label descriptionText = new Label(safeText(session.getDescription()));
        descriptionText.setWrapText(true);
        descriptionText.setStyle("-fx-text-fill: #334d78; -fx-font-size: 13px;");
        descriptionCard.getChildren().addAll(descriptionTitle, descriptionText);

        VBox policyCard = new VBox(8.0);
        policyCard.setStyle("-fx-background-color: #f8fbff; -fx-border-color: #d8e4ff; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16;");
        Label policyTitle = new Label("Etat administratif");
        policyTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        Label policyText = new Label(buildAdministrativeMessage(session));
        policyText.setWrapText(true);
        policyText.setStyle("-fx-text-fill: #334d78; -fx-font-size: 13px;");
        policyCard.getChildren().addAll(policyTitle, policyText);

        root.getChildren().addAll(header, metrics, descriptionCard, policyCard);
        return root;
    }

    private void openCustomizationReviewDialog(AdminSession session, SessionRoomCustomizationRequest request) {
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
            new Label("Personnalisation liee a la seance"),
            new Label(safeText(session.getSubject()) + " | " + safeText(session.getSchedule()))
        );
        header.getChildren().get(0).setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #17356d;");
        header.getChildren().get(1).setStyle("-fx-text-fill: #5d7196; -fx-font-size: 13px;");

        FlowPane sessionFacts = new FlowPane(12.0, 12.0);
        sessionFacts.setPrefWrapLength(720.0);
        sessionFacts.getChildren().addAll(
            buildDetailMetric("Tuteur", safeText(session.getTutor()), "Intervenant concerne"),
            buildDetailMetric("Capacite session", session.getCapacity() + " places", "Capacite actuellement publiee"),
            buildDetailMetric("Salle de base", baseSalle == null ? "Introuvable" : safeText(baseSalle.getNom()), "Salle physique de reference"),
            buildDetailMetric("Statut demande", formatCustomizationStatusLabel(request.status()), "Workflow admin")
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
        VBox adminCommentBox = new VBox(
            6.0,
            buildSectionLabel("Commentaire admin"),
            adminCommentArea
        );

        Button previewButton = new Button("Apercu 3D");
        Button markInReviewButton = new Button("En analyse");
        Button approveButton = new Button("Approuver");
        Button rejectButton = new Button("Rejeter");
        Button closeButton = new Button("Fermer");

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
        applyCurrentTheme(scene);

        Stage stage = new Stage();
        stage.setTitle("Traitement personnalisation");
        stage.initModality(Modality.WINDOW_MODAL);
        Window owner = resolveBackofficeWindow();
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
                showModalFeedback(dialogFeedbackLabel, "Impossible d'ouvrir l'apercu 3D sans salle de base ou configuration.", false);
                return;
            }

            Room3DViewerLauncher.showPreview(buildCustomizationPreviewData(
                baseSalle,
                request.isApproved() && request.effectiveApprovedConfig() != null
                    ? request.effectiveApprovedConfig()
                    : configToReview,
                request.isApproved() ? "Configuration approuvee" : "Configuration demandee"
            ));
            showModalFeedback(dialogFeedbackLabel, "Apercu 3D ouvert dans une fenetre dediee.", true);
        });

        markInReviewButton.setOnAction(event -> {
            try {
                sessionRoomCustomizationService.markInReview(session.getId(), adminCommentArea.getText());
                sessionService.reloadSessions();
                sessionsTable.refresh();
                updateActionButtons(session);
                showFeedback("La demande de personnalisation est maintenant en cours d'analyse.", true);
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
                sessionRoomCustomizationService.approveRequest(session.getId(), configToReview, adminCommentArea.getText());
                sessionService.reloadSessions();
                sessionsTable.refresh();
                updateActionButtons(session);
                showFeedback("La personnalisation de salle a ete approuvee.", true);
                stage.close();
            } catch (Exception exception) {
                showModalFeedback(dialogFeedbackLabel, resolveReadableMessage(exception), false);
            }
        });

        rejectButton.setOnAction(event -> {
            try {
                sessionRoomCustomizationService.rejectRequest(session.getId(), adminCommentArea.getText());
                sessionService.reloadSessions();
                sessionsTable.refresh();
                updateActionButtons(session);
                showFeedback("La personnalisation de salle a ete rejetee.", true);
                stage.close();
            } catch (Exception exception) {
                showModalFeedback(dialogFeedbackLabel, resolveReadableMessage(exception), false);
            }
        });

        closeButton.setOnAction(event -> stage.close());
        stage.showAndWait();
    }

    private SessionRoomCustomizationRequest resolveCustomizationRequest(int seanceId) {
        return sessionRoomCustomizationService.findBySeanceIdQuietly(seanceId);
    }

    private Salle resolveBaseSalle(int salleId) {
        if (salleId <= 0) {
            return null;
        }

        try {
            return salleService.recupererParId(salleId);
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

    private TableRow<AdminSession> buildSessionRow() {
        TableRow<AdminSession> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                showSessionDetails(row.getItem());
            }
        });
        return row;
    }

    private void updateSessionsCount() {
        int count = filteredSessions == null ? sessionService.getSessions().size() : filteredSessions.size();
        String suffix = count > 1 ? "seances affichees" : "seance affichee";
        sessionsCountLabel.setText(count + " " + suffix);
    }

    private void showDatabaseStateIfNeeded() {
        if (!sessionService.hasDatabaseConnection()) {
            updateActionButtons(null);
            showFeedback("Base de donnees indisponible. Demarrez MySQL/XAMPP puis cliquez sur Actualiser.", false);
            return;
        }
        if (sessionService.getSessions().isEmpty()) {
            updateActionButtons(null);
            showFeedback("Aucune seance trouvee dans la base de donnees.", false);
            return;
        }
        hideFeedback();
    }

    private String buildAdministrativeMessage(AdminSession session) {
        if (session.getReservationCount() > 0) {
            return "Cette seance possede deja " + formatReservationCount(session.getReservationCount())
                + ". La suppression reste bloquee tant que des reservations y sont rattachees.";
        }
        return "Aucune reservation liee. La suppression reste autorisee si l'admin confirme l'action.";
    }

    private String formatReservationCount(int reservationCount) {
        if (reservationCount <= 0) {
            return "0 reservation";
        }
        return reservationCount == 1 ? "1 reservation" : reservationCount + " reservations";
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

    private Window resolveBackofficeWindow() {
        if (sessionsTable != null && sessionsTable.getScene() != null) {
            return sessionsTable.getScene().getWindow();
        }
        if (feedbackLabel != null && feedbackLabel.getScene() != null) {
            return feedbackLabel.getScene().getWindow();
        }
        return null;
    }

    private void applyCurrentTheme(Scene scene) {
        if (scene == null) {
            return;
        }

        Window owner = resolveBackofficeWindow();
        if (owner == null || owner.getScene() == null) {
            return;
        }
        scene.getStylesheets().setAll(owner.getScene().getStylesheets());
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
}
