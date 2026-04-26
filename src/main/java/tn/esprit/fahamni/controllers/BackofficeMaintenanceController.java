package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import tn.esprit.fahamni.Models.MaintenanceSalle;
import tn.esprit.fahamni.services.AdminMaintenanceSalleService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BackofficeMaintenanceController {

    private static final int DEFAULT_ROWS_PER_PAGE = 5;
    private static final int MAX_VISIBLE_PAGE_BUTTONS = 7;
    private static final String FILTER_ALL = "Tous les statuts";
    private static final String FILTER_LINKED_CLAIM = "Liees a une reclamation";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private TableView<MaintenanceSalle> maintenanceTable;

    @FXML
    private TableColumn<MaintenanceSalle, String> salleColumn;

    @FXML
    private TableColumn<MaintenanceSalle, String> typeColumn;

    @FXML
    private TableColumn<MaintenanceSalle, String> statutColumn;

    @FXML
    private TableColumn<MaintenanceSalle, String> origineColumn;

    @FXML
    private TableColumn<MaintenanceSalle, String> dateColumn;

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
    private Label openMaintenanceValueLabel;

    @FXML
    private Label plannedMaintenanceValueLabel;

    @FXML
    private Label linkedClaimsValueLabel;

    @FXML
    private Label completedMaintenanceValueLabel;

    @FXML
    private Label selectionBadgeLabel;

    @FXML
    private Label selectedMaintenanceTitleLabel;

    @FXML
    private Label selectedMaintenanceMetaLabel;

    @FXML
    private Label selectedMaintenanceContextLabel;

    @FXML
    private Label currentStatusValueLabel;

    @FXML
    private Label linkedClaimValueLabel;

    @FXML
    private Label roomStateValueLabel;

    @FXML
    private Label roomDescriptionValueLabel;

    @FXML
    private ComboBox<String> typeMaintenanceComboBox;

    @FXML
    private ComboBox<String> maintenanceStatusComboBox;

    @FXML
    private TextField responsibleField;

    @FXML
    private DatePicker plannedDatePicker;

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private TextArea detailsArea;

    @FXML
    private Button saveMaintenanceButton;

    @FXML
    private Button startTodayButton;

    @FXML
    private Button markCompletedButton;

    @FXML
    private Button resetSelectionButton;

    @FXML
    private Label feedbackLabel;

    private final AdminMaintenanceSalleService maintenanceService = new AdminMaintenanceSalleService();
    private final ObservableList<MaintenanceSalle> maintenances = FXCollections.observableArrayList();
    private final FilteredList<MaintenanceSalle> filteredMaintenances = new FilteredList<>(maintenances, maintenance -> true);
    private final ObservableList<MaintenanceSalle> displayedMaintenances = FXCollections.observableArrayList();
    private int currentPageIndex;
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;

    @FXML
    private void initialize() {
        configureTable();
        configureFilters();
        configureForm();
        configurePagination();

        maintenanceTable.setItems(displayedMaintenances);
        maintenanceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateDetails(newValue));
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        hideFeedback();
        clearDetails();
        loadMaintenances();
    }

    @FXML
    private void handleRefresh() {
        hideFeedback();
        if (loadMaintenances()) {
            showFeedback("Les dossiers de maintenance ont ete actualises.", true);
        }
    }

    @FXML
    private void handleSaveMaintenance() {
        hideFeedback();

        MaintenanceSalle selectedMaintenance = maintenanceTable.getSelectionModel().getSelectedItem();
        if (selectedMaintenance == null) {
            showFeedback("Selectionnez un dossier avant de le mettre a jour.", false);
            return;
        }

        try {
            MaintenanceSalle updatedMaintenance = buildUpdatedMaintenance(selectedMaintenance);
            maintenanceService.update(updatedMaintenance);

            if (!loadMaintenances()) {
                return;
            }

            selectMaintenanceById(updatedMaintenance.getIdMaintenance());
            showFeedback("Le dossier de maintenance a ete mis a jour.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Mise a jour impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleUseToday() {
        if (startTodayButton.isDisabled()) {
            return;
        }

        if (startDatePicker.getValue() == null) {
            startDatePicker.setValue(LocalDate.now());
        }

        if ("planifiee".equalsIgnoreCase(defaultString(maintenanceStatusComboBox.getValue()).trim())) {
            maintenanceStatusComboBox.setValue("en cours");
        }
    }

    @FXML
    private void handleMarkCompleted() {
        if (markCompletedButton.isDisabled()) {
            return;
        }

        if (startDatePicker.getValue() == null) {
            startDatePicker.setValue(LocalDate.now());
        }
        endDatePicker.setValue(LocalDate.now());
        maintenanceStatusComboBox.setValue("terminee");
        handleSaveMaintenance();
    }

    @FXML
    private void handleResetSelection() {
        maintenanceTable.getSelectionModel().clearSelection();
        clearDetails();
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
        if (currentPageIndex >= getTotalPages(filteredMaintenances.size()) - 1) {
            return;
        }

        currentPageIndex++;
        refreshTablePage();
    }

    private void configureTable() {
        salleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getNomSalle())));
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatLabel(cellData.getValue().getTypeMaintenance())));
        statutColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatLabel(cellData.getValue().getStatut())));
        origineColumn.setCellValueFactory(cellData -> new SimpleStringProperty(buildOriginLabel(cellData.getValue())));
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatDate(resolveKeyDate(cellData.getValue()))));
    }

    private void configureFilters() {
        statusFilterComboBox.getItems().setAll(FILTER_ALL, FILTER_LINKED_CLAIM);
        statusFilterComboBox.getItems().addAll(maintenanceService.getAvailableStatuts());
        statusFilterComboBox.setValue(FILTER_ALL);
    }

    private void configureForm() {
        typeMaintenanceComboBox.getItems().setAll(maintenanceService.getAvailableTypes());
        maintenanceStatusComboBox.getItems().setAll(maintenanceService.getAvailableStatuts());
        setEditingDisabled(true);
    }

    private void configurePagination() {
        pageSizeComboBox.getItems().setAll(5, 10, 15, 20);
        pageSizeComboBox.setValue(rowsPerPage);
        pageSizeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> handlePageSizeChange(newValue));
    }

    private boolean loadMaintenances() {
        Integer selectedMaintenanceId = getSelectedMaintenanceId();

        try {
            maintenances.setAll(maintenanceService.getAll());
            updateSummary();

            if (selectedMaintenanceId != null && selectMaintenanceById(selectedMaintenanceId)) {
                return true;
            }

            refreshTablePage();
            clearDetails();
            return true;
        } catch (SQLException | IllegalStateException exception) {
            maintenances.clear();
            displayedMaintenances.clear();
            currentPageIndex = 0;
            updateSummary();
            refreshPaginationControls(0, 0, 0, 0);
            clearDetails();
            showFeedback("Chargement impossible : " + resolveMessage(exception), false);
            return false;
        }
    }

    private void applyFilters() {
        Integer selectedMaintenanceId = getSelectedMaintenanceId();
        String normalizedSearch = normalize(searchField.getText());
        String selectedStatus = statusFilterComboBox.getValue();

        filteredMaintenances.setPredicate(maintenance -> matchesFilters(maintenance, normalizedSearch, selectedStatus));

        if (selectedMaintenanceId != null && selectMaintenanceById(selectedMaintenanceId)) {
            return;
        }

        currentPageIndex = 0;
        refreshTablePage();
        clearDetails();
    }

    private boolean matchesFilters(MaintenanceSalle maintenance, String normalizedSearch, String selectedStatus) {
        if (!normalizedSearch.isBlank() && !matchesSearch(maintenance, normalizedSearch)) {
            return false;
        }

        if (selectedStatus == null || FILTER_ALL.equals(selectedStatus)) {
            return true;
        }

        if (FILTER_LINKED_CLAIM.equals(selectedStatus)) {
            return maintenance.getIdReclamation() != null;
        }

        return normalize(maintenance.getStatut()).equals(normalize(selectedStatus));
    }

    private boolean matchesSearch(MaintenanceSalle maintenance, String normalizedSearch) {
        return contains(maintenance.getNomSalle(), normalizedSearch)
            || contains(maintenance.getTypeMaintenance(), normalizedSearch)
            || contains(maintenance.getStatut(), normalizedSearch)
            || contains(maintenance.getResponsable(), normalizedSearch)
            || contains(maintenance.getDetailsIntervention(), normalizedSearch)
            || contains(buildOriginLabel(maintenance), normalizedSearch);
    }

    private void populateDetails(MaintenanceSalle maintenance) {
        if (maintenance == null) {
            clearDetails();
            return;
        }

        selectedMaintenanceTitleLabel.setText(formatOptionalText(maintenance.getNomSalle()));
        selectedMaintenanceMetaLabel.setText(buildMetaLine(maintenance));
        selectedMaintenanceContextLabel.setText(buildContextLine(maintenance));
        currentStatusValueLabel.setText(formatLabel(maintenance.getStatut()));
        linkedClaimValueLabel.setText(buildOriginLabel(maintenance));
        roomStateValueLabel.setText(formatLabel(maintenance.getEtatSalle()));
        roomDescriptionValueLabel.setText(
            trimToNull(maintenance.getDescriptionSalle()) == null
                ? "Aucun contexte complementaire n'a encore ete defini pour cette salle."
                : maintenance.getDescriptionSalle().trim()
        );

        typeMaintenanceComboBox.setValue(maintenance.getTypeMaintenance());
        maintenanceStatusComboBox.setValue(maintenance.getStatut());
        responsibleField.setText(defaultString(maintenance.getResponsable()));
        plannedDatePicker.setValue(maintenance.getDatePlanifiee());
        startDatePicker.setValue(maintenance.getDateDebut());
        endDatePicker.setValue(maintenance.getDateFin());
        detailsArea.setText(defaultString(maintenance.getDetailsIntervention()));

        setEditingDisabled(false);
        updateSelectionBadge(maintenance);
    }

    private void clearDetails() {
        selectedMaintenanceTitleLabel.setText("Aucun dossier selectionne");
        selectedMaintenanceMetaLabel.setText("Selectionnez une maintenance depuis la liste pour suivre l'intervention.");
        selectedMaintenanceContextLabel.setText("Les informations de la salle, de l'origine et du pilotage apparaitront ici.");
        currentStatusValueLabel.setText("Non defini");
        linkedClaimValueLabel.setText("Aucune source reliee");
        roomStateValueLabel.setText("Non defini");
        roomDescriptionValueLabel.setText("La description de la salle apparaitra ici apres selection.");

        typeMaintenanceComboBox.setValue(null);
        maintenanceStatusComboBox.setValue(null);
        responsibleField.clear();
        plannedDatePicker.setValue(null);
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        detailsArea.clear();

        setEditingDisabled(true);
        updateSelectionBadge(null);
    }

    private void setEditingDisabled(boolean disabled) {
        typeMaintenanceComboBox.setDisable(disabled);
        maintenanceStatusComboBox.setDisable(disabled);
        responsibleField.setDisable(disabled);
        plannedDatePicker.setDisable(disabled);
        startDatePicker.setDisable(disabled);
        endDatePicker.setDisable(disabled);
        detailsArea.setDisable(disabled);
        saveMaintenanceButton.setDisable(disabled);
        startTodayButton.setDisable(disabled);
        markCompletedButton.setDisable(disabled);
        resetSelectionButton.setDisable(disabled);
    }

    private MaintenanceSalle buildUpdatedMaintenance(MaintenanceSalle selectedMaintenance) {
        return new MaintenanceSalle(
            selectedMaintenance.getIdMaintenance(),
            selectedMaintenance.getIdSalle(),
            selectedMaintenance.getIdReclamation(),
            selectedMaintenance.getNomSalle(),
            selectedMaintenance.getBatimentSalle(),
            selectedMaintenance.getLocalisationSalle(),
            selectedMaintenance.getTypeSalle(),
            selectedMaintenance.getEtatSalle(),
            selectedMaintenance.getDescriptionSalle(),
            requireText(typeMaintenanceComboBox.getValue(), "Le type de maintenance est obligatoire."),
            requireText(maintenanceStatusComboBox.getValue(), "Le statut de maintenance est obligatoire."),
            trimToNull(responsibleField.getText()),
            trimToNull(detailsArea.getText()),
            plannedDatePicker.getValue(),
            startDatePicker.getValue(),
            endDatePicker.getValue(),
            selectedMaintenance.getDateCreation()
        );
    }

    private void updateSummary() {
        LocalDate today = LocalDate.now();

        long openCount = maintenances.stream()
            .filter(maintenance -> isOpenStatus(maintenance.getStatut()))
            .count();

        long plannedThisMonth = maintenances.stream()
            .map(MaintenanceSalle::getDatePlanifiee)
            .filter(date -> date != null && date.getMonthValue() == today.getMonthValue() && date.getYear() == today.getYear())
            .count();

        long linkedClaims = maintenances.stream()
            .filter(maintenance -> maintenance.getIdReclamation() != null)
            .count();

        long completed = maintenances.stream()
            .filter(maintenance -> "terminee".equals(normalize(maintenance.getStatut())))
            .count();

        openMaintenanceValueLabel.setText(String.valueOf(openCount));
        plannedMaintenanceValueLabel.setText(String.valueOf(plannedThisMonth));
        linkedClaimsValueLabel.setText(String.valueOf(linkedClaims));
        completedMaintenanceValueLabel.setText(String.valueOf(completed));
    }

    private boolean isOpenStatus(String statut) {
        String normalized = normalize(statut);
        return "planifiee".equals(normalized) || "en cours".equals(normalized);
    }

    private void updateSelectionBadge(MaintenanceSalle maintenance) {
        if (maintenance != null) {
            selectionBadgeLabel.setText(formatOptionalText(maintenance.getNomSalle()) + " | " + formatLabel(maintenance.getStatut()));
            return;
        }

        if (filteredMaintenances.isEmpty()) {
            selectionBadgeLabel.setText("Aucun dossier de maintenance visible");
            return;
        }

        if (!normalize(searchField.getText()).isBlank() || !FILTER_ALL.equals(statusFilterComboBox.getValue())) {
            selectionBadgeLabel.setText(filteredMaintenances.size() + " dossier(s) correspondent au filtre");
            return;
        }

        selectionBadgeLabel.setText("Suivi des interventions pret");
    }

    private boolean selectMaintenanceById(int idMaintenance) {
        for (int index = 0; index < filteredMaintenances.size(); index++) {
            MaintenanceSalle maintenance = filteredMaintenances.get(index);
            if (maintenance.getIdMaintenance() == idMaintenance) {
                currentPageIndex = index / rowsPerPage;
                refreshTablePage();

                for (MaintenanceSalle visibleMaintenance : displayedMaintenances) {
                    if (visibleMaintenance.getIdMaintenance() == idMaintenance) {
                        maintenanceTable.getSelectionModel().select(visibleMaintenance);
                        maintenanceTable.scrollTo(visibleMaintenance);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Integer getSelectedMaintenanceId() {
        MaintenanceSalle selectedMaintenance = maintenanceTable.getSelectionModel().getSelectedItem();
        return selectedMaintenance == null ? null : selectedMaintenance.getIdMaintenance();
    }

    private void refreshTablePage() {
        int filteredSize = filteredMaintenances.size();
        int totalPages = getTotalPages(filteredSize);

        if (totalPages == 0) {
            currentPageIndex = 0;
            displayedMaintenances.clear();
            refreshPaginationControls(0, 0, 0, 0);
            return;
        }

        currentPageIndex = Math.max(0, Math.min(currentPageIndex, totalPages - 1));

        int fromIndex = currentPageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredSize);

        displayedMaintenances.setAll(filteredMaintenances.subList(fromIndex, toIndex));
        refreshPaginationControls(filteredSize, fromIndex, toIndex, totalPages);
    }

    private void refreshPaginationControls(int filteredSize, int fromIndex, int toIndex, int totalPages) {
        if (filteredSize == 0) {
            paginationSummaryLabel.setText("Aucune maintenance a afficher");
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
            return "1 maintenance affichee";
        }
        return startIndex + " a " + endIndex + " sur " + totalItems + " maintenances";
    }

    private void handlePageSizeChange(Integer newValue) {
        if (newValue == null || newValue <= 0 || newValue == rowsPerPage) {
            return;
        }

        Integer selectedMaintenanceId = getSelectedMaintenanceId();
        rowsPerPage = newValue;

        if (selectedMaintenanceId != null && selectMaintenanceById(selectedMaintenanceId)) {
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

    private String buildMetaLine(MaintenanceSalle maintenance) {
        return formatLabel(maintenance.getTypeMaintenance())
            + " | "
            + formatOptionalText(maintenance.getBatimentSalle())
            + " | "
            + formatOptionalText(maintenance.getTypeSalle());
    }

    private String buildContextLine(MaintenanceSalle maintenance) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatOptionalText(maintenance.getLocalisationSalle()));
        builder.append(" | Cree le ").append(formatDateTime(maintenance.getDateCreation()));
        builder.append(" | ").append(buildOriginLabel(maintenance));

        if (trimToNull(maintenance.getResponsable()) != null) {
            builder.append(" | Responsable ").append(maintenance.getResponsable().trim());
        }

        return builder.toString();
    }

    private String buildOriginLabel(MaintenanceSalle maintenance) {
        return maintenance.getIdReclamation() == null
            ? "Dossier autonome"
            : "Signalement associe";
    }

    private LocalDate resolveKeyDate(MaintenanceSalle maintenance) {
        if (maintenance.getDateFin() != null) {
            return maintenance.getDateFin();
        }
        if (maintenance.getDateDebut() != null) {
            return maintenance.getDateDebut();
        }
        if (maintenance.getDatePlanifiee() != null) {
            return maintenance.getDatePlanifiee();
        }
        return maintenance.getDateCreation() == null ? null : maintenance.getDateCreation().toLocalDate();
    }

    private String formatDate(LocalDate date) {
        return date == null ? "Non renseignee" : DATE_FORMATTER.format(date);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "Non renseignee" : DATE_TIME_FORMATTER.format(dateTime);
    }

    private String formatLabel(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "Non defini";
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1);
    }

    private String formatOptionalText(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "Non renseigne" : normalized;
    }

    private String requireText(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private boolean contains(String value, String normalizedSearch) {
        return value != null && normalize(value).contains(normalizedSearch);
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
