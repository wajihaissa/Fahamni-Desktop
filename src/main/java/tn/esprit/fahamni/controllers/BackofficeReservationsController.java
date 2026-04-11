package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminReservation;
import tn.esprit.fahamni.services.AdminReservationService;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class BackofficeReservationsController {

    @FXML
    private TableView<AdminReservation> reservationsTable;

    @FXML
    private TableColumn<AdminReservation, Integer> reservationIdColumn;

    @FXML
    private TableColumn<AdminReservation, String> studentColumn;

    @FXML
    private TableColumn<AdminReservation, String> sessionColumn;

    @FXML
    private TableColumn<AdminReservation, String> requestDateColumn;

    @FXML
    private TableColumn<AdminReservation, String> statusColumn;

    private final AdminReservationService reservationService = new AdminReservationService();

    @FXML
    private void initialize() {
        reservationIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        studentColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        sessionColumn.setCellValueFactory(new PropertyValueFactory<>("sessionTitle"));
        requestDateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        reservationsTable.setItems(reservationService.getReservations());
    }

    @FXML
    private void handleRefreshReservations() {
        reservationService.reloadReservations();
        reservationsTable.refresh();
    }
}

