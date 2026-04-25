package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.services.AdminSessionService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
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
    private Button deleteSelectedSessionButton;

    private final AdminSessionService sessionService = new AdminSessionService();
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
