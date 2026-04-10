package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.services.AdminEquipementService;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public class BackofficeEquipementsController {

    private static final int DEFAULT_ROWS_PER_PAGE = 5;
    private static final int MAX_VISIBLE_PAGE_BUTTONS = 7;
    private static final String FILTER_ALL = "Tous les etats";
    private static final String DEFAULT_CREATION_STATUS = "disponible";

    @FXML
    private TableView<Equipement> equipementsTable;

    @FXML
    private TableColumn<Equipement, String> nomColumn;

    @FXML
    private TableColumn<Equipement, String> typeColumn;

    @FXML
    private TableColumn<Equipement, Integer> quantiteColumn;

    @FXML
    private TableColumn<Equipement, String> etatColumn;

    @FXML
    private TableColumn<Equipement, String> descriptionColumn;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> statusFilterComboBox;

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
    private ComboBox<String> typeComboBox;

    @FXML
    private Spinner<Integer> quantiteSpinner;

    @FXML
    private ComboBox<String> etatComboBox;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label totalEquipementsValueLabel;

    @FXML
    private Label availableEquipementsValueLabel;

    @FXML
    private Label maintenanceEquipementsValueLabel;

    @FXML
    private Label totalQuantityValueLabel;

    @FXML
    private Label selectionBadgeLabel;

    @FXML
    private Label feedbackLabel;

    private final AdminEquipementService equipementService = new AdminEquipementService();
    private final ObservableList<Equipement> equipements = FXCollections.observableArrayList();
    private final FilteredList<Equipement> filteredEquipements = new FilteredList<>(equipements, equipement -> true);
    private final ObservableList<Equipement> displayedEquipements = FXCollections.observableArrayList();
    private int currentPageIndex;
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;

    @FXML
    private void initialize() {
        configureTable();
        configureFilters();
        configureForm();
        configurePagination();

        equipementsTable.setItems(displayedEquipements);
        equipementsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        hideFeedback();
        clearForm();
        loadEquipements();
    }

    @FXML
    private void handleRefresh() {
        hideFeedback();
        if (loadEquipements()) {
            showFeedback("La liste des equipements a ete actualisee.", true);
        }
    }

    @FXML
    private void handleCreateEquipement() {
        hideFeedback();

        try {
            Equipement equipement = buildNewEquipement();
            equipementService.add(equipement);

            if (!loadEquipements()) {
                return;
            }

            selectEquipementById(equipement.getIdEquipement());
            showFeedback("L'equipement a ete ajoute avec succes.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Ajout impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleUpdateEquipement() {
        hideFeedback();

        Equipement selectedEquipement = equipementsTable.getSelectionModel().getSelectedItem();
        if (selectedEquipement == null) {
            showFeedback("Selectionnez un equipement avant de lancer la mise a jour.", false);
            return;
        }

        try {
            Equipement equipement = buildUpdatedEquipement(selectedEquipement.getIdEquipement());
            equipementService.update(equipement);

            if (!loadEquipements()) {
                return;
            }

            selectEquipementById(equipement.getIdEquipement());
            showFeedback("L'equipement selectionne a ete mis a jour.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Mise a jour impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleDeleteEquipement() {
        hideFeedback();

        Equipement selectedEquipement = equipementsTable.getSelectionModel().getSelectedItem();
        if (selectedEquipement == null) {
            showFeedback("Selectionnez un equipement a supprimer.", false);
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Suppression d'un equipement");
        confirmation.setHeaderText("Supprimer " + selectedEquipement.getNom() + " ?");
        confirmation.setContentText("Cette action retirera l'equipement du catalogue admin.");

        Optional<ButtonType> choice = confirmation.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }

        try {
            equipementService.delete(selectedEquipement);
            equipementsTable.getSelectionModel().clearSelection();
            clearForm();

            if (!loadEquipements()) {
                return;
            }

            showFeedback("L'equipement a ete supprime.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Suppression impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleResetForm() {
        equipementsTable.getSelectionModel().clearSelection();
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
        if (currentPageIndex >= getTotalPages(filteredEquipements.size()) - 1) {
            return;
        }

        currentPageIndex++;
        refreshTablePage();
    }

    private void configureTable() {
        nomColumn.setCellValueFactory(new PropertyValueFactory<>("nom"));
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatLabel(cellData.getValue().getTypeEquipement())));
        quantiteColumn.setCellValueFactory(new PropertyValueFactory<>("quantiteDisponible"));
        etatColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatLabel(cellData.getValue().getEtat())));
        descriptionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(buildDescriptionPreview(cellData.getValue().getDescription())));
    }

    private void configureFilters() {
        statusFilterComboBox.getItems().setAll(FILTER_ALL);
        statusFilterComboBox.getItems().addAll(equipementService.getAvailableEtats());
        statusFilterComboBox.setValue(FILTER_ALL);
    }

    private void configureForm() {
        typeComboBox.getItems().setAll(equipementService.getAvailableTypes());
        etatComboBox.getItems().setAll(equipementService.getAvailableEtats());

        quantiteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 5000, 1));
        quantiteSpinner.setEditable(true);
        etatComboBox.setDisable(true);
    }

    private void configurePagination() {
        pageSizeComboBox.getItems().setAll(5, 10, 15, 20);
        pageSizeComboBox.setValue(rowsPerPage);
        pageSizeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> handlePageSizeChange(newValue));
    }

    private boolean loadEquipements() {
        Integer selectedEquipementId = getSelectedEquipementId();

        try {
            equipements.setAll(equipementService.getAll());
            updateSummary();

            if (selectedEquipementId != null && selectEquipementById(selectedEquipementId)) {
                return true;
            }

            refreshTablePage();
            updateSelectionBadge(null);
            return true;
        } catch (SQLException | IllegalStateException exception) {
            equipements.clear();
            displayedEquipements.clear();
            currentPageIndex = 0;
            updateSummary();
            refreshPaginationControls(0, 0, 0, 0);
            updateSelectionBadge(null);
            showFeedback("Chargement impossible : " + resolveMessage(exception), false);
            return false;
        }
    }

    private void populateForm(Equipement equipement) {
        if (equipement == null) {
            clearForm();
            updateSelectionBadge(null);
            return;
        }

        nomField.setText(equipement.getNom());
        typeComboBox.setValue(equipement.getTypeEquipement());
        quantiteSpinner.getValueFactory().setValue(equipement.getQuantiteDisponible());
        etatComboBox.setValue(equipement.getEtat());
        etatComboBox.setDisable(false);
        descriptionArea.setText(defaultString(equipement.getDescription()));

        updateSelectionBadge(equipement);
    }

    private void clearForm() {
        nomField.clear();
        typeComboBox.setValue(typeComboBox.getItems().isEmpty() ? null : typeComboBox.getItems().get(0));
        quantiteSpinner.getValueFactory().setValue(1);
        etatComboBox.setValue(resolveDefaultCreationStatus());
        etatComboBox.setDisable(true);
        descriptionArea.clear();

        updateSelectionBadge(null);
    }

    private void applyFilters() {
        Integer selectedEquipementId = getSelectedEquipementId();
        String normalizedSearch = normalize(searchField.getText());
        String selectedStatus = statusFilterComboBox.getValue();

        filteredEquipements.setPredicate(equipement -> matchesFilters(equipement, normalizedSearch, selectedStatus));

        if (selectedEquipementId != null && selectEquipementById(selectedEquipementId)) {
            return;
        }

        currentPageIndex = 0;
        refreshTablePage();
        updateSelectionBadge(null);
    }

    private boolean matchesFilters(Equipement equipement, String normalizedSearch, String selectedStatus) {
        if (!normalizedSearch.isBlank() && !matchesSearch(equipement, normalizedSearch)) {
            return false;
        }

        if (selectedStatus == null || FILTER_ALL.equals(selectedStatus)) {
            return true;
        }

        return normalize(equipement.getEtat()).equals(normalize(selectedStatus));
    }

    private boolean matchesSearch(Equipement equipement, String normalizedSearch) {
        return contains(equipement.getNom(), normalizedSearch)
            || contains(equipement.getTypeEquipement(), normalizedSearch)
            || contains(equipement.getEtat(), normalizedSearch)
            || contains(equipement.getDescription(), normalizedSearch);
    }

    private Equipement buildNewEquipement() {
        return new Equipement(
            0,
            requireText(nomField.getText(), "Le nom de l'equipement est obligatoire."),
            requireText(typeComboBox.getValue(), "Le type d'equipement est obligatoire."),
            parseNonNegativeInteger(quantiteSpinner.getEditor().getText(), "La quantite disponible doit etre un entier positif ou nul."),
            DEFAULT_CREATION_STATUS,
            trimToNull(descriptionArea.getText())
        );
    }

    private Equipement buildUpdatedEquipement(int idEquipement) {
        return new Equipement(
            idEquipement,
            requireText(nomField.getText(), "Le nom de l'equipement est obligatoire."),
            requireText(typeComboBox.getValue(), "Le type d'equipement est obligatoire."),
            parseNonNegativeInteger(quantiteSpinner.getEditor().getText(), "La quantite disponible doit etre un entier positif ou nul."),
            requireText(etatComboBox.getValue(), "L'etat de l'equipement est obligatoire."),
            trimToNull(descriptionArea.getText())
        );
    }

    private int parseNonNegativeInteger(String rawValue, String errorMessage) {
        try {
            int value = Integer.parseInt(rawValue == null ? "" : rawValue.trim());
            if (value < 0) {
                throw new IllegalArgumentException(errorMessage);
            }
            return value;
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
        long availableCount = equipements.stream()
            .filter(equipement -> "disponible".equalsIgnoreCase(equipement.getEtat()))
            .count();

        long maintenanceCount = equipements.stream()
            .filter(equipement -> normalize(equipement.getEtat()).contains("maintenance"))
            .count();

        int totalQuantity = equipements.stream()
            .mapToInt(Equipement::getQuantiteDisponible)
            .sum();

        totalEquipementsValueLabel.setText(String.valueOf(equipements.size()));
        availableEquipementsValueLabel.setText(String.valueOf(availableCount));
        maintenanceEquipementsValueLabel.setText(String.valueOf(maintenanceCount));
        totalQuantityValueLabel.setText(String.valueOf(totalQuantity));
    }

    private void updateSelectionBadge(Equipement equipement) {
        if (equipement != null) {
            selectionBadgeLabel.setText(equipement.getNom() + " | " + equipement.getQuantiteDisponible() + " unite(s)");
            return;
        }

        if (filteredEquipements.isEmpty()) {
            selectionBadgeLabel.setText("Aucun equipement visible");
            return;
        }

        if (!normalize(searchField.getText()).isBlank() || !FILTER_ALL.equals(statusFilterComboBox.getValue())) {
            selectionBadgeLabel.setText(filteredEquipements.size() + " equipement(s) correspondent au filtre");
            return;
        }

        selectionBadgeLabel.setText("Nouvel equipement pret a etre configure");
    }

    private boolean selectEquipementById(int idEquipement) {
        for (int index = 0; index < filteredEquipements.size(); index++) {
            Equipement equipement = filteredEquipements.get(index);
            if (equipement.getIdEquipement() == idEquipement) {
                currentPageIndex = index / rowsPerPage;
                refreshTablePage();

                for (Equipement visibleEquipement : displayedEquipements) {
                    if (visibleEquipement.getIdEquipement() == idEquipement) {
                        equipementsTable.getSelectionModel().select(visibleEquipement);
                        equipementsTable.scrollTo(visibleEquipement);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Integer getSelectedEquipementId() {
        Equipement selectedEquipement = equipementsTable.getSelectionModel().getSelectedItem();
        return selectedEquipement == null ? null : selectedEquipement.getIdEquipement();
    }

    private void refreshTablePage() {
        int filteredSize = filteredEquipements.size();
        int totalPages = getTotalPages(filteredSize);

        if (totalPages == 0) {
            currentPageIndex = 0;
            displayedEquipements.clear();
            refreshPaginationControls(0, 0, 0, 0);
            return;
        }

        currentPageIndex = Math.max(0, Math.min(currentPageIndex, totalPages - 1));

        int fromIndex = currentPageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredSize);

        displayedEquipements.setAll(filteredEquipements.subList(fromIndex, toIndex));
        refreshPaginationControls(filteredSize, fromIndex, toIndex, totalPages);
    }

    private void refreshPaginationControls(int filteredSize, int fromIndex, int toIndex, int totalPages) {
        if (filteredSize == 0) {
            paginationSummaryLabel.setText("Aucun equipement a afficher");
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
            return "1 equipement affiche";
        }
        return startIndex + " a " + endIndex + " sur " + totalItems + " equipements";
    }

    private void handlePageSizeChange(Integer newValue) {
        if (newValue == null || newValue <= 0 || newValue == rowsPerPage) {
            return;
        }

        Integer selectedEquipementId = getSelectedEquipementId();
        rowsPerPage = newValue;

        if (selectedEquipementId != null && selectEquipementById(selectedEquipementId)) {
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

    private String buildDescriptionPreview(String description) {
        String normalizedDescription = trimToNull(description);
        if (normalizedDescription == null) {
            return "Aucune description";
        }

        if (normalizedDescription.length() <= 60) {
            return normalizedDescription;
        }

        return normalizedDescription.substring(0, 57) + "...";
    }

    private String formatLabel(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "Non defini";
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1);
    }

    private boolean contains(String value, String normalizedSearch) {
        return value != null && normalize(value).contains(normalizedSearch);
    }

    private String resolveDefaultCreationStatus() {
        for (String etat : etatComboBox.getItems()) {
            if (DEFAULT_CREATION_STATUS.equalsIgnoreCase(etat)) {
                return etat;
            }
        }
        return etatComboBox.getItems().isEmpty() ? null : etatComboBox.getItems().get(0);
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
