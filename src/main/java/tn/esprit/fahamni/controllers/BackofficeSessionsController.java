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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public class BackofficeSessionsController {

    private static final String ALL_STATUSES = "Tous les statuts";

    @FXML
    private TableView<AdminSession> sessionsTable;

    @FXML
    private TableColumn<AdminSession, Integer> sessionIdColumn;

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
    private Button deleteSelectedSessionButton;

    private final AdminSessionService sessionService = new AdminSessionService();
    private FilteredList<AdminSession> filteredSessions;

    @FXML
    private void initialize() {
        sessionIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
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

        sessionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> handleSessionSelectionChange(newValue));
        sessionSearchField.textProperty().addListener((observable, oldValue, newValue) -> applySessionFilters());
        sessionStatusFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySessionFilters());
        sessionDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> applySessionFilters());
        filteredSessions.addListener((ListChangeListener<AdminSession>) change -> updateSessionsCount());

        updateSessionsCount();
        updateDeleteActionState(null);
        showDatabaseStateIfNeeded();
    }

    @FXML
    private void handleRefreshSessions() {
        sessionService.reloadSessions();
        sessionsTable.getSelectionModel().clearSelection();
        applySessionFilters();
        sessionsTable.refresh();
        updateSessionsCount();
        updateDeleteActionState(null);
        showDatabaseStateIfNeeded();
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
            updateDeleteActionState(selectedSession);
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
        updateDeleteActionState(selectedSession);

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

    private void updateDeleteActionState(AdminSession selectedSession) {
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

    private void updateSessionsCount() {
        int count = filteredSessions == null ? sessionService.getSessions().size() : filteredSessions.size();
        String suffix = count > 1 ? "seances affichees" : "seance affichee";
        sessionsCountLabel.setText(count + " " + suffix);
    }

    private void showDatabaseStateIfNeeded() {
        if (!sessionService.hasDatabaseConnection()) {
            updateDeleteActionState(null);
            showFeedback("Base de donnees indisponible. Demarrez MySQL/XAMPP puis cliquez sur Actualiser.", false);
            return;
        }
        if (sessionService.getSessions().isEmpty()) {
            updateDeleteActionState(null);
            showFeedback("Aucune seance trouvee dans la base de donnees.", false);
            return;
        }
        hideFeedback();
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
