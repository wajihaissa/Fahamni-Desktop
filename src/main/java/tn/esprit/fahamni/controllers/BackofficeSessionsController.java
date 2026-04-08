package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminSession;
import tn.esprit.fahamni.services.AdminSessionService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class BackofficeSessionsController {

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
    private TableColumn<AdminSession, String> statusColumn;

    @FXML
    private TextField subjectField;

    @FXML
    private TextField tutorField;

    @FXML
    private TextField scheduleField;

    @FXML
    private Spinner<Integer> capacitySpinner;

    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private Label feedbackLabel;

    private final AdminSessionService sessionService = new AdminSessionService();

    @FXML
    private void initialize() {
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        tutorColumn.setCellValueFactory(new PropertyValueFactory<>("tutor"));
        scheduleColumn.setCellValueFactory(new PropertyValueFactory<>("schedule"));
        capacityColumn.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        statusComboBox.getItems().setAll(sessionService.getAvailableStatuses());
        statusComboBox.setValue("Draft");
        capacitySpinner.getValueFactory().setValue(10);

        sessionsTable.setItems(sessionService.getSessions());
        sessionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));

        hideFeedback();
    }

    @FXML
    private void handleCreateSession() {
        hideFeedback();

        OperationResult result = sessionService.createSession(
            subjectField.getText(),
            tutorField.getText(),
            scheduleField.getText(),
            capacitySpinner.getValue(),
            statusComboBox.getValue()
        );
        if (result.isSuccess()) {
            clearForm();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleUpdateSession() {
        hideFeedback();

        OperationResult result = sessionService.updateSession(
            sessionsTable.getSelectionModel().getSelectedItem(),
            subjectField.getText(),
            tutorField.getText(),
            scheduleField.getText(),
            capacitySpinner.getValue(),
            statusComboBox.getValue()
        );
        if (result.isSuccess()) {
            sessionsTable.refresh();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleResetForm() {
        sessionsTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
    }

    private void populateForm(AdminSession session) {
        if (session == null) {
            return;
        }

        subjectField.setText(session.getSubject());
        tutorField.setText(session.getTutor());
        scheduleField.setText(session.getSchedule());
        capacitySpinner.getValueFactory().setValue(session.getCapacity());
        statusComboBox.setValue(session.getStatus());
    }

    private void clearForm() {
        subjectField.clear();
        tutorField.clear();
        scheduleField.clear();
        capacitySpinner.getValueFactory().setValue(10);
        statusComboBox.setValue("Draft");
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

