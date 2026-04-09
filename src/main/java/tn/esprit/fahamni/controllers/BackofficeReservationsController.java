package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminReservation;
import tn.esprit.fahamni.services.AdminReservationService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class BackofficeReservationsController {

    @FXML
    private TableView<AdminReservation> reservationsTable;

    @FXML
    private TableColumn<AdminReservation, String> studentColumn;

    @FXML
    private TableColumn<AdminReservation, String> sessionColumn;

    @FXML
    private TableColumn<AdminReservation, String> requestDateColumn;

    @FXML
    private TableColumn<AdminReservation, String> statusColumn;

    @FXML
    private TextField studentField;

    @FXML
    private TextField sessionField;

    @FXML
    private TextField requestDateField;

    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private Label feedbackLabel;

    private final AdminReservationService reservationService = new AdminReservationService();

    @FXML
    private void initialize() {
        studentColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        sessionColumn.setCellValueFactory(new PropertyValueFactory<>("sessionTitle"));
        requestDateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        statusComboBox.getItems().setAll(reservationService.getAvailableStatuses());
        statusComboBox.setValue("Pending");

        reservationsTable.setItems(reservationService.getReservations());
        reservationsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));

        hideFeedback();
    }

    @FXML
    private void handleConfirmReservation() {
        applyReservationResult(
            reservationService.confirmReservation(reservationsTable.getSelectionModel().getSelectedItem())
        );
    }

    @FXML
    private void handleCancelReservation() {
        applyReservationResult(
            reservationService.cancelReservation(reservationsTable.getSelectionModel().getSelectedItem())
        );
    }

    @FXML
    private void handleSaveReservation() {
        hideFeedback();

        applyReservationResult(
            reservationService.saveReservation(
                reservationsTable.getSelectionModel().getSelectedItem(),
                studentField.getText(),
                sessionField.getText(),
                requestDateField.getText(),
                statusComboBox.getValue()
            )
        );
    }

    private void populateForm(AdminReservation reservation) {
        if (reservation == null) {
            return;
        }

        studentField.setText(reservation.getStudentName());
        sessionField.setText(reservation.getSessionTitle());
        requestDateField.setText(reservation.getRequestDate());
        statusComboBox.setValue(reservation.getStatus());
    }

    private void applyReservationResult(OperationResult result) {
        if (result.isSuccess()) {
            AdminReservation selectedReservation = reservationsTable.getSelectionModel().getSelectedItem();
            if (selectedReservation != null) {
                statusComboBox.setValue(selectedReservation.getStatus());
            }
            reservationsTable.refresh();
        }

        showFeedback(result.getMessage(), result.isSuccess());
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

