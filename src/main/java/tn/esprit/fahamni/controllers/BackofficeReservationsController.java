package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminReservation;
import tn.esprit.fahamni.services.AdminReservationService;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.Locale;

public class BackofficeReservationsController {

    private static final String ALL_STATUSES = "Tous les statuts";

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
    private TextField searchField;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private DatePicker reservationDatePicker;

    @FXML
    private Label resultsCountLabel;

    private final AdminReservationService reservationService = new AdminReservationService();
    private FilteredList<AdminReservation> filteredReservations;

    @FXML
    private void initialize() {
        studentColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        sessionColumn.setCellValueFactory(new PropertyValueFactory<>("sessionTitle"));
        requestDateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        statusFilterComboBox.getItems().setAll(
            ALL_STATUSES,
            "En attente",
            "Acceptee",
            "Refusee",
            "Annulee"
        );
        statusFilterComboBox.setValue(ALL_STATUSES);

        filteredReservations = new FilteredList<>(reservationService.getReservations(), reservation -> true);
        SortedList<AdminReservation> sortedReservations = new SortedList<>(filteredReservations);
        sortedReservations.comparatorProperty().bind(reservationsTable.comparatorProperty());
        reservationsTable.setItems(sortedReservations);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyReservationFilters());
        statusFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyReservationFilters());
        reservationDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> applyReservationFilters());
        filteredReservations.addListener((ListChangeListener<AdminReservation>) change -> updateResultsCount());
        updateResultsCount();
    }

    @FXML
    private void handleRefreshReservations() {
        reservationService.reloadReservations();
        applyReservationFilters();
        reservationsTable.refresh();
    }

    @FXML
    private void handleResetReservationFilters() {
        searchField.clear();
        statusFilterComboBox.setValue(ALL_STATUSES);
        reservationDatePicker.setValue(null);
        applyReservationFilters();
    }

    private void applyReservationFilters() {
        if (filteredReservations == null) {
            return;
        }

        String query = normalize(searchField.getText());
        String selectedStatus = statusFilterComboBox.getValue();
        LocalDate selectedDate = reservationDatePicker.getValue();

        filteredReservations.setPredicate(reservation -> matchesSearch(reservation, query)
            && matchesStatus(reservation, selectedStatus)
            && matchesDate(reservation, selectedDate));
        updateResultsCount();
    }

    private boolean matchesSearch(AdminReservation reservation, String query) {
        if (query.isBlank()) {
            return true;
        }

        return normalize(reservation.getStudentName()).contains(query)
            || normalize(reservation.getSessionTitle()).contains(query)
            || normalize(reservation.getRequestDate()).contains(query)
            || normalize(reservation.getStatus()).contains(query)
            || String.valueOf(reservation.getId()).contains(query);
    }

    private boolean matchesStatus(AdminReservation reservation, String selectedStatus) {
        return selectedStatus == null
            || ALL_STATUSES.equals(selectedStatus)
            || selectedStatus.equals(reservation.getStatus());
    }

    private boolean matchesDate(AdminReservation reservation, LocalDate selectedDate) {
        return selectedDate == null || selectedDate.equals(reservation.getRequestLocalDate());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void updateResultsCount() {
        int count = filteredReservations == null ? 0 : filteredReservations.size();
        String suffix = count > 1 ? "reservations trouvees" : "reservation trouvee";
        resultsCountLabel.setText(count + " " + suffix);
    }
}

