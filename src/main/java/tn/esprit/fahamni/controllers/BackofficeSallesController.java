package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.services.AdminSalleService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public class BackofficeSallesController {
    private static final int DEFAULT_ROWS_PER_PAGE = 5;
    private static final int MAX_VISIBLE_PAGE_BUTTONS = 7;

    @FXML
    private TableView<Salle> sallesTable;

    @FXML
    private TableColumn<Salle, String> nomColumn;

    @FXML
    private TableColumn<Salle, String> batimentColumn;

    @FXML
    private TableColumn<Salle, String> localisationColumn;

    @FXML
    private TableColumn<Salle, String> typeColumn;

    @FXML
    private TableColumn<Salle, Integer> capaciteColumn;

    @FXML
    private TableColumn<Salle, String> etatColumn;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<Integer> pageSizeComboBox;

    @FXML
    private Button previousPageButton;

    @FXML
    private Button nextPageButton;

    @FXML
    private Label paginationSummaryLabel;

    @FXML
    private HBox pageButtonsContainer;

    @FXML
    private TextField nomField;

    @FXML
    private Spinner<Integer> capaciteSpinner;

    @FXML
    private TextField localisationField;

    @FXML
    private TextField batimentField;

    @FXML
    private ComboBox<String> typeComboBox;

    @FXML
    private ComboBox<String> etatComboBox;

    @FXML
    private TextField etageField;

    @FXML
    private ComboBox<String> dispositionComboBox;

    @FXML
    private CheckBox accesHandicapeCheckBox;

    @FXML
    private TextField statutDetailleField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label totalRoomsValueLabel;

    @FXML
    private Label availableRoomsValueLabel;

    @FXML
    private Label maintenanceRoomsValueLabel;

    @FXML
    private Label totalCapacityValueLabel;

    @FXML
    private Label selectionBadgeLabel;

    @FXML
    private Label feedbackLabel;

    private final AdminSalleService salleService = new AdminSalleService();
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final FilteredList<Salle> filteredSalles = new FilteredList<>(salles, salle -> true);
    private final ObservableList<Salle> displayedSalles = FXCollections.observableArrayList();
    private int currentPageIndex;
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;

    @FXML
    private void initialize() {
        configureTable();
        configureForm();
        configurePagination();

        sallesTable.setItems(displayedSalles);
        sallesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter(newValue));

        hideFeedback();
        clearForm();
        loadSalles();
    }

    @FXML
    private void handleRefresh() {
        hideFeedback();
        if (loadSalles()) {
            showFeedback("La liste des salles a ete actualisee.", true);
        }
    }

    @FXML
    private void handleCreateSalle() {
        hideFeedback();

        try {
            Salle salle = buildSalle(0, null);
            salleService.add(salle);

            if (!loadSalles()) {
                return;
            }
            selectSalleById(salle.getIdSalle());
            showFeedback("La salle a ete ajoutee avec succes.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Ajout impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleUpdateSalle() {
        hideFeedback();

        Salle selectedSalle = sallesTable.getSelectionModel().getSelectedItem();
        if (selectedSalle == null) {
            showFeedback("Selectionnez une salle avant de lancer la mise a jour.", false);
            return;
        }

        try {
            Salle salle = buildSalle(selectedSalle.getIdSalle(), selectedSalle.getDateDerniereMaintenance());
            salleService.update(salle);

            if (!loadSalles()) {
                return;
            }
            selectSalleById(salle.getIdSalle());
            showFeedback("La salle selectionnee a ete mise a jour.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Mise a jour impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleDeleteSalle() {
        hideFeedback();

        Salle selectedSalle = sallesTable.getSelectionModel().getSelectedItem();
        if (selectedSalle == null) {
            showFeedback("Selectionnez une salle a supprimer.", false);
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Suppression d'une salle");
        confirmation.setHeaderText("Supprimer " + selectedSalle.getNom() + " ?");
        confirmation.setContentText("Cette action retirera la salle de la gestion admin.");

        Optional<ButtonType> choice = confirmation.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }

        try {
            salleService.delete(selectedSalle);
            sallesTable.getSelectionModel().clearSelection();
            clearForm();
            if (!loadSalles()) {
                return;
            }
            showFeedback("La salle a ete supprimee.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Suppression impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleResetForm() {
        sallesTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
    }

    @FXML
    private void handlePreviousPage() {
        if (currentPageIndex <= 0) {
            return;
        }

        currentPageIndex--;
        refreshTablePage();
    }

    @FXML
    private void handleNextPage() {
        if (currentPageIndex >= getTotalPages(filteredSalles.size()) - 1) {
            return;
        }

        currentPageIndex++;
        refreshTablePage();
    }

    private void configureTable() {
        nomColumn.setCellValueFactory(new PropertyValueFactory<>("nom"));
        batimentColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getBatiment())));
        localisationColumn.setCellValueFactory(new PropertyValueFactory<>("localisation"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("typeSalle"));
        capaciteColumn.setCellValueFactory(new PropertyValueFactory<>("capacite"));
        etatColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatEtat(cellData.getValue().getEtat())));
    }

    private void configureForm() {
        capaciteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, 24));
        capaciteSpinner.setEditable(true);

        typeComboBox.getItems().setAll(salleService.getAvailableTypes());
        etatComboBox.getItems().setAll(salleService.getAvailableEtats());
        dispositionComboBox.getItems().setAll(salleService.getAvailableDispositions());
    }

    private void configurePagination() {
        pageSizeComboBox.getItems().setAll(5, 10, 15, 20);
        pageSizeComboBox.setValue(rowsPerPage);
        pageSizeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> handlePageSizeChange(newValue));
    }

    private boolean loadSalles() {
        Integer selectedSalleId = getSelectedSalleId();

        try {
            salles.setAll(salleService.getAll());
            updateSummary();

            if (selectedSalleId != null && selectSalleById(selectedSalleId)) {
                return true;
            }

            refreshTablePage();
            updateSelectionBadge(null);
            return true;
        } catch (SQLException | IllegalStateException exception) {
            salles.clear();
            displayedSalles.clear();
            currentPageIndex = 0;
            updateSummary();
            refreshPaginationControls(0, 0, 0, 0);
            updateSelectionBadge(null);
            showFeedback("Chargement impossible : " + resolveMessage(exception), false);
            return false;
        }
    }

    private void populateForm(Salle salle) {
        if (salle == null) {
            updateSelectionBadge(null);
            return;
        }

        nomField.setText(salle.getNom());
        capaciteSpinner.getValueFactory().setValue(salle.getCapacite());
        localisationField.setText(salle.getLocalisation());
        batimentField.setText(defaultString(salle.getBatiment()));
        typeComboBox.setValue(salle.getTypeSalle());
        etatComboBox.setValue(salle.getEtat());
        etageField.setText(salle.getEtage() == null ? "" : String.valueOf(salle.getEtage()));
        dispositionComboBox.setValue(salle.getTypeDisposition());
        accesHandicapeCheckBox.setSelected(salle.isAccesHandicape());
        statutDetailleField.setText(defaultString(salle.getStatutDetaille()));
        descriptionArea.setText(defaultString(salle.getDescription()));

        updateSelectionBadge(salle);
    }

    private void clearForm() {
        nomField.clear();
        capaciteSpinner.getValueFactory().setValue(24);
        localisationField.clear();
        batimentField.clear();
        typeComboBox.setValue(typeComboBox.getItems().isEmpty() ? null : typeComboBox.getItems().get(0));
        etatComboBox.setValue(etatComboBox.getItems().isEmpty() ? null : etatComboBox.getItems().get(0));
        etageField.clear();
        dispositionComboBox.setValue(null);
        accesHandicapeCheckBox.setSelected(false);
        statutDetailleField.clear();
        descriptionArea.clear();

        updateSelectionBadge(null);
    }

    private void applyFilter(String filterText) {
        Integer selectedSalleId = getSelectedSalleId();
        String normalizedFilter = normalize(filterText);
        filteredSalles.setPredicate(salle -> matchesFilter(salle, normalizedFilter));

        if (selectedSalleId != null && selectSalleById(selectedSalleId)) {
            return;
        }

        currentPageIndex = 0;
        refreshTablePage();
        updateSelectionBadge(null);
    }

    private boolean matchesFilter(Salle salle, String filterText) {
        if (filterText.isBlank()) {
            return true;
        }

        return contains(salle.getNom(), filterText)
            || contains(salle.getLocalisation(), filterText)
            || contains(salle.getBatiment(), filterText)
            || contains(salle.getTypeSalle(), filterText)
            || contains(salle.getEtat(), filterText);
    }

    private Salle buildSalle(int idSalle, LocalDate maintenanceDate) {
        String nom = requireText(nomField.getText(), "Le nom de la salle est obligatoire.");
        int capacite = parsePositiveInteger(capaciteSpinner.getEditor().getText(), "La capacite doit etre un entier positif.");
        String localisation = requireText(localisationField.getText(), "La localisation est obligatoire.");
        String typeSalle = requireText(typeComboBox.getValue(), "Le type de salle est obligatoire.");
        String etat = requireText(etatComboBox.getValue(), "L'etat de la salle est obligatoire.");

        return new Salle(
            idSalle,
            nom,
            capacite,
            localisation,
            typeSalle,
            etat,
            trimToNull(descriptionArea.getText()),
            trimToNull(batimentField.getText()),
            parseOptionalInteger(etageField.getText(), "L'etage doit etre un nombre entier."),
            trimToNull(dispositionComboBox.getValue()),
            accesHandicapeCheckBox.isSelected(),
            trimToNull(statutDetailleField.getText()),
            maintenanceDate
        );
    }

    private int parsePositiveInteger(String rawValue, String errorMessage) {
        try {
            int value = Integer.parseInt(rawValue == null ? "" : rawValue.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(errorMessage);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private Integer parseOptionalInteger(String rawValue, String errorMessage) {
        String normalizedValue = trimToNull(rawValue);
        if (normalizedValue == null) {
            return null;
        }

        try {
            return Integer.parseInt(normalizedValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String requireText(String value, String errorMessage) {
        String normalizedValue = trimToNull(value);
        if (normalizedValue == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalizedValue;
    }

    private void updateSummary() {
        long availableRooms = salles.stream()
            .filter(salle -> "disponible".equalsIgnoreCase(salle.getEtat()))
            .count();

        long maintenanceRooms = salles.stream()
            .filter(salle -> salle.getEtat() != null && salle.getEtat().toLowerCase(Locale.ROOT).contains("maintenance"))
            .count();

        int totalCapacity = salles.stream()
            .mapToInt(Salle::getCapacite)
            .sum();

        totalRoomsValueLabel.setText(String.valueOf(salles.size()));
        availableRoomsValueLabel.setText(String.valueOf(availableRooms));
        maintenanceRoomsValueLabel.setText(String.valueOf(maintenanceRooms));
        totalCapacityValueLabel.setText(totalCapacity + " places");
    }

    private void updateSelectionBadge(Salle salle) {
        if (salle != null) {
            selectionBadgeLabel.setText(salle.getNom() + " | " + salle.getCapacite() + " places");
            return;
        }

        if (searchField != null && searchField.getText() != null && !searchField.getText().isBlank()) {
            selectionBadgeLabel.setText(filteredSalles.size() + " salle(s) correspondent au filtre");
            return;
        }

        selectionBadgeLabel.setText("Nouvelle salle prete a etre configuree");
    }

    private boolean selectSalleById(int idSalle) {
        for (int index = 0; index < filteredSalles.size(); index++) {
            Salle salle = filteredSalles.get(index);
            if (salle.getIdSalle() == idSalle) {
                currentPageIndex = index / rowsPerPage;
                refreshTablePage();

                for (Salle visibleSalle : displayedSalles) {
                    if (visibleSalle.getIdSalle() == idSalle) {
                        sallesTable.getSelectionModel().select(visibleSalle);
                        sallesTable.scrollTo(visibleSalle);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Integer getSelectedSalleId() {
        Salle selectedSalle = sallesTable.getSelectionModel().getSelectedItem();
        return selectedSalle == null ? null : selectedSalle.getIdSalle();
    }

    private String formatEtat(String etat) {
        if (etat == null || etat.isBlank()) {
            return "Non defini";
        }

        String normalized = etat.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String formatOptionalText(String value) {
        return trimToNull(value) == null ? "Non renseigne" : value.trim();
    }

    private boolean contains(String value, String filterText) {
        return value != null && normalize(value).contains(filterText);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String resolveMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Une erreur technique est survenue." : message;
    }

    private void refreshTablePage() {
        int filteredSize = filteredSalles.size();
        int totalPages = getTotalPages(filteredSize);

        if (totalPages == 0) {
            currentPageIndex = 0;
            displayedSalles.clear();
            refreshPaginationControls(0, 0, 0, 0);
            return;
        }

        currentPageIndex = Math.max(0, Math.min(currentPageIndex, totalPages - 1));

        int fromIndex = currentPageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredSize);

        displayedSalles.setAll(filteredSalles.subList(fromIndex, toIndex));
        refreshPaginationControls(filteredSize, fromIndex, toIndex, totalPages);
    }

    private void refreshPaginationControls(int filteredSize, int fromIndex, int toIndex, int totalPages) {
        if (filteredSize == 0) {
            paginationSummaryLabel.setText("Aucune salle a afficher");
            pageButtonsContainer.getChildren().clear();
            previousPageButton.setDisable(true);
            nextPageButton.setDisable(true);
            return;
        }

        paginationSummaryLabel.setText(buildPaginationSummary(fromIndex + 1, toIndex, filteredSize));
        refreshPageButtons(totalPages);
        previousPageButton.setDisable(currentPageIndex == 0);
        nextPageButton.setDisable(currentPageIndex >= totalPages - 1);
    }

    private int getTotalPages(int itemCount) {
        if (itemCount <= 0) {
            return 0;
        }
        return (itemCount + rowsPerPage - 1) / rowsPerPage;
    }

    private String buildPaginationSummary(int startIndex, int endIndex, int totalItems) {
        if (totalItems == 1) {
            return "1 salle affichee";
        }
        return startIndex + " a " + endIndex + " sur " + totalItems + " salles";
    }

    private void handlePageSizeChange(Integer newValue) {
        if (newValue == null || newValue <= 0 || newValue == rowsPerPage) {
            return;
        }

        Integer selectedSalleId = getSelectedSalleId();
        rowsPerPage = newValue;

        if (selectedSalleId != null && selectSalleById(selectedSalleId)) {
            return;
        }

        currentPageIndex = 0;
        refreshTablePage();
        updateSelectionBadge(null);
    }

    private void refreshPageButtons(int totalPages) {
        pageButtonsContainer.getChildren().clear();

        if (totalPages <= 0) {
            return;
        }

        if (totalPages <= MAX_VISIBLE_PAGE_BUTTONS) {
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                addPageButton(pageIndex);
            }
            return;
        }

        addPageButton(0);

        int windowStart = Math.max(1, currentPageIndex - 1);
        int windowEnd = Math.min(totalPages - 2, currentPageIndex + 1);

        if (windowStart <= 2) {
            windowStart = 1;
            windowEnd = 3;
        } else if (windowEnd >= totalPages - 3) {
            windowStart = totalPages - 4;
            windowEnd = totalPages - 2;
        }

        if (windowStart > 1) {
            addPaginationDots();
        }

        for (int pageIndex = windowStart; pageIndex <= windowEnd; pageIndex++) {
            addPageButton(pageIndex);
        }

        if (windowEnd < totalPages - 2) {
            addPaginationDots();
        }

        addPageButton(totalPages - 1);
    }

    private void addPageButton(int pageIndex) {
        Button button = new Button(String.valueOf(pageIndex + 1));
        button.getStyleClass().setAll("backoffice-page-button");
        if (pageIndex == currentPageIndex) {
            button.getStyleClass().add("active-page");
            button.setDisable(true);
        }
        button.setOnAction(event -> {
            currentPageIndex = pageIndex;
            refreshTablePage();
        });
        pageButtonsContainer.getChildren().add(button);
    }

    private void addPaginationDots() {
        Label dots = new Label("...");
        dots.getStyleClass().add("backoffice-pagination-dots");
        pageButtonsContainer.getChildren().add(dots);
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
