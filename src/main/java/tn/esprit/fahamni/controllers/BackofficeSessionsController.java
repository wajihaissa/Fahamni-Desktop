package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.services.AdminSessionService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.Optional;

public class BackofficeSessionsController {

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
    private TableColumn<AdminSession, String> statusColumn;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Label sessionsCountLabel;

    private final AdminSessionService sessionService = new AdminSessionService();

    @FXML
    private void initialize() {
        sessionIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        tutorColumn.setCellValueFactory(new PropertyValueFactory<>("tutor"));
        scheduleColumn.setCellValueFactory(new PropertyValueFactory<>("schedule"));
        capacityColumn.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        sessionsTable.setItems(sessionService.getSessions());
        sessionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> hideFeedback());

        updateSessionsCount();
        hideFeedback();
    }

    @FXML
    private void handleRefreshSessions() {
        sessionService.reloadSessions();
        sessionsTable.refresh();
        updateSessionsCount();
        hideFeedback();
    }

    @FXML
    private void handleDeleteSelectedSession() {
        hideFeedback();

        AdminSession selectedSession = sessionsTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showFeedback("Selectionnez une seance a supprimer.", false);
            return;
        }

        if (!confirmDeletion(selectedSession)) {
            return;
        }

        OperationResult result = sessionService.deleteSession(selectedSession);
        if (result.isSuccess()) {
            sessionsTable.getSelectionModel().clearSelection();
            sessionsTable.refresh();
            updateSessionsCount();
        }

        showFeedback(result.getMessage(), result.isSuccess());
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
        int count = sessionService.getSessions().size();
        String suffix = count > 1 ? "seances affichees" : "seance affichee";
        sessionsCountLabel.setText(count + " " + suffix);
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
