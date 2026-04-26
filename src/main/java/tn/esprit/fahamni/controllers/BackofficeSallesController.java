package tn.esprit.fahamni.controllers;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.geometry.Pos;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.SalleEquipementService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.util.Duration;

public class BackofficeSallesController {
    private static final int DEFAULT_ROWS_PER_PAGE = 20;
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
    private ComboBox<String> batimentComboBox;

    @FXML
    private ComboBox<String> typeComboBox;

    @FXML
    private ComboBox<String> etatComboBox;

    @FXML
    private ComboBox<Integer> etageComboBox;

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

    @FXML
    private Button manageFixedEquipementsButton;

    private final AdminSalleService salleService = new AdminSalleService();
    private final AdminEquipementService equipementService = new AdminEquipementService();
    private final SalleEquipementService salleEquipementService = new SalleEquipementService();
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final ObservableList<Equipement> equipementCatalog = FXCollections.observableArrayList();
    private final FilteredList<Salle> filteredSalles = new FilteredList<>(salles, salle -> true);
    private final ObservableList<Salle> displayedSalles = FXCollections.observableArrayList();
    private final Map<Integer, FixedEquipmentSelectionControls> fixedEquipmentSelectionControls = new LinkedHashMap<>();
    private final PauseTransition recentSalleHighlight = new PauseTransition(Duration.seconds(4));
    private int currentPageIndex;
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;
    private Integer highlightedSalleId;

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
            highlightSalle(salle.getIdSalle());
            selectSalleById(salle.getIdSalle());
            showFeedback("La salle a ete ajoutee avec succes. Vous pouvez maintenant gerer ses equipements fixes.", true);
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
            if (!hasSalleDataChanged(selectedSalle, salle)) {
                showFeedback("Aucune modification detectee pour cette salle.", true);
                return;
            }
            salleService.update(salle);

            if (!loadSalles()) {
                return;
            }
            highlightSalle(salle.getIdSalle());
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
    private void handleManageFixedEquipements() {
        hideFeedback();

        Salle selectedSalle = sallesTable.getSelectionModel().getSelectedItem();
        if (selectedSalle == null || selectedSalle.getIdSalle() <= 0) {
            showFeedback("Enregistrez ou selectionnez une salle avant de gerer ses equipements fixes.", false);
            return;
        }

        if (!loadEquipementCatalog()) {
            return;
        }

        Dialog<Map<Integer, Integer>> dialog = new Dialog<>();
        dialog.setTitle("Equipements fixes");
        dialog.setHeaderText(null);
        dialog.setResizable(false);

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveButtonType);
        applyCurrentTheme(dialog.getDialogPane());
        dialog.getDialogPane().getStyleClass().add("backoffice-room-equipment-dialog");

        Label summaryLabel = new Label("Aucun fixe");
        summaryLabel.getStyleClass().setAll("workspace-chip", "workspace-chip-muted");

        VBox content = new VBox(12.0);
        content.setFillWidth(true);
        content.getStyleClass().add("backoffice-room-equipment-dialog-content");
        Label dialogTitle = new Label("Gerer les equipements fixes de " + selectedSalle.getNom());
        dialogTitle.getStyleClass().add("backoffice-room-equipment-dialog-title");
        content.getChildren().addAll(
            dialogTitle,
            buildDialogIntro(
                "Definissez ici uniquement le materiel present en permanence dans cette salle. "
                    + "Le tuteur pourra ensuite ajouter du materiel complementaire a sa seance."
            ),
            buildDialogHeader(summaryLabel)
        );

        VBox choicesContainer = new VBox(8.0);
        choicesContainer.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(choicesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(300.0);
        scrollPane.setPrefHeight(332.0);
        scrollPane.setMaxHeight(332.0);
        scrollPane.getStyleClass().addAll("reservation-choice-scroll", "backoffice-room-equipment-dialog-scroll");
        content.getChildren().add(scrollPane);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(760.0);
        dialog.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        if (saveButton != null) {
            saveButton.getStyleClass().addAll("backoffice-primary-button", "backoffice-dialog-save-button");
            saveButton.setDefaultButton(true);
            saveButton.setMinWidth(138.0);
        }
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) {
            cancelButton.getStyleClass().addAll("backoffice-secondary-button", "backoffice-dialog-cancel-button");
            cancelButton.setCancelButton(true);
            cancelButton.setMinWidth(124.0);
        }

        renderFixedEquipmentChoices(choicesContainer, summaryLabel, selectedSalle.getIdSalle());
        populateFixedEquipementSelection(selectedSalle.getIdSalle(), summaryLabel);

        Platform.runLater(() -> {
            Node buttonBar = dialog.getDialogPane().lookup(".button-bar");
            if (buttonBar != null) {
                buttonBar.toFront();
            }
        });

        dialog.setResultConverter(buttonType -> buttonType == saveButtonType ? getSelectedFixedEquipementQuantites() : null);

        Optional<Map<Integer, Integer>> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            salleEquipementService.replaceEquipementsForSalle(selectedSalle.getIdSalle(), result.get());
            updateFixedEquipmentSummary(selectedSalle.getIdSalle());
            showFeedback("Les equipements fixes de la salle ont ete mis a jour.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Mise a jour des equipements fixes impossible : " + resolveMessage(exception), false);
        }
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
        nomColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getNom())));
        batimentColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getBatiment())));
        localisationColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getLocalisation())));
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getTypeSalle())));
        capaciteColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getCapacite()));
        etatColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatEtat(cellData.getValue().getEtat())));

        nomColumn.setCellFactory(column -> createTextCell(Pos.CENTER_LEFT));
        batimentColumn.setCellFactory(column -> createTextCell(Pos.CENTER_LEFT));
        localisationColumn.setCellFactory(column -> createTextCell(Pos.CENTER_LEFT));
        typeColumn.setCellFactory(column -> createTextCell(Pos.CENTER_LEFT));
        capaciteColumn.setCellFactory(column -> createTextCell(Pos.CENTER));
        etatColumn.setCellFactory(column -> createTextCell(Pos.CENTER_LEFT));

        sallesTable.setRowFactory(tableView -> buildSalleRow());
        recentSalleHighlight.setOnFinished(event -> {
            highlightedSalleId = null;
            sallesTable.refresh();
        });
    }

    private void configureForm() {
        capaciteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, 24));
        capaciteSpinner.setEditable(true);

        batimentComboBox.getItems().setAll(salleService.getAvailableBatiments());
        typeComboBox.getItems().setAll(salleService.getAvailableTypes());
        etatComboBox.getItems().setAll(salleService.getAvailableEtats());
        dispositionComboBox.getItems().setAll(salleService.getAvailableDispositions());
        etageComboBox.getItems().setAll(0, 1, 2, 3, 4);
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
        batimentComboBox.setValue(normalizeBatimentValue(salle.getBatiment()));
        typeComboBox.setValue(salle.getTypeSalle());
        etatComboBox.setValue(salle.getEtat());
        etageComboBox.setValue(normalizeEtageValue(salle.getEtage()));
        dispositionComboBox.setValue(salle.getTypeDisposition());
        accesHandicapeCheckBox.setSelected(salle.isAccesHandicape());
        statutDetailleField.setText(defaultString(salle.getStatutDetaille()));
        descriptionArea.setText(defaultString(salle.getDescription()));
        updateFixedEquipmentSummary(salle.getIdSalle());

        updateSelectionBadge(salle);
    }

    private void clearForm() {
        nomField.clear();
        capaciteSpinner.getValueFactory().setValue(24);
        localisationField.clear();
        batimentComboBox.setValue(null);
        typeComboBox.setValue(typeComboBox.getItems().isEmpty() ? null : typeComboBox.getItems().get(0));
        etatComboBox.setValue(etatComboBox.getItems().isEmpty() ? null : etatComboBox.getItems().get(0));
        etageComboBox.setValue(null);
        dispositionComboBox.setValue(null);
        accesHandicapeCheckBox.setSelected(false);
        statutDetailleField.clear();
        descriptionArea.clear();
        updateFixedEquipmentSummary(null);

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
        String batiment = requireText(batimentComboBox.getValue(), "Le batiment est obligatoire.");
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
            batiment,
            etageComboBox.getValue(),
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

    private Integer normalizeEtageValue(Integer etage) {
        if (etage == null || etage < 0 || etage > 4) {
            return null;
        }
        return etage;
    }

    private String normalizeBatimentValue(String batiment) {
        String normalizedBatiment = trimToNull(batiment);
        if (normalizedBatiment == null || !batimentComboBox.getItems().contains(normalizedBatiment)) {
            return null;
        }
        return normalizedBatiment;
    }

    private boolean loadEquipementCatalog() {
        try {
            equipementCatalog.setAll(
                equipementService.getAll().stream()
                    .sorted(
                        Comparator.comparingInt(this::resolveFixedEquipmentSortRank)
                            .thenComparing(equipement -> normalize(equipement.getNom()))
                    )
                    .toList()
            );
            return true;
        } catch (SQLException | IllegalStateException exception) {
            equipementCatalog.clear();
            showFeedback("Chargement des equipements impossible : " + resolveMessage(exception), false);
            return false;
        }
    }

    private void renderFixedEquipmentChoices(VBox container, Label summaryLabel, int salleId) {
        fixedEquipmentSelectionControls.clear();
        container.getChildren().clear();

        if (equipementCatalog.isEmpty()) {
            Label emptyLabel = buildDialogIntro("Aucun equipement n'est disponible pour une affectation fixe.");
            container.getChildren().add(emptyLabel);
            updateDialogFixedEquipmentSummary(summaryLabel);
            return;
        }

        for (Equipement equipement : equipementCatalog) {
            container.getChildren().add(createFixedEquipmentChoiceCard(equipement, summaryLabel, salleId));
        }

        updateDialogFixedEquipmentSummary(summaryLabel);
    }

    private VBox createFixedEquipmentChoiceCard(Equipement equipement, Label summaryLabel, int salleId) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("reservation-equipment-choice-card");
        card.setMaxWidth(Double.MAX_VALUE);
        int quantiteRestante = resolveRemainingFixedStock(equipement, salleId);
        boolean selectable = isSelectableFixedEquipement(equipement) && quantiteRestante > 0;

        CheckBox checkBox = new CheckBox(
            formatOptionalText(equipement.getNom()) + " (" + formatOptionalText(equipement.getTypeEquipement()) + ")"
        );
        checkBox.setWrapText(true);
        checkBox.setMaxWidth(Double.MAX_VALUE);
        checkBox.getStyleClass().add("reservation-equipment-check");

        Spinner<Integer> quantitySpinner = new Spinner<>();
        quantitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1,
            Math.max(1, quantiteRestante),
            1
        ));
        quantitySpinner.setEditable(true);
        quantitySpinner.setPrefWidth(96.0);
        quantitySpinner.getStyleClass().add("reservation-equipment-quantity-spinner");
        quantitySpinner.setDisable(true);

        Label quantityLabel = new Label("Quantite fixe");
        quantityLabel.getStyleClass().add("backoffice-form-label");
        VBox quantityBox = new VBox(4.0, quantityLabel, quantitySpinner);
        quantityBox.setMinWidth(112.0);
        quantityBox.setPrefWidth(112.0);
        quantityBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Label statusChip = new Label(formatEtat(equipement.getEtat()));
        statusChip.getStyleClass().setAll("status-chip", resolveStatusStyle(equipement.getEtat()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(10.0, checkBox, spacer, statusChip, quantityBox);
        topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(checkBox, Priority.ALWAYS);

        Label stockLabel = new Label(
            formatOptionalText(equipement.getTypeEquipement())
                + " | Stock global: "
                + equipement.getQuantiteDisponible()
                + " | Libre: "
                + quantiteRestante
                + " unite(s)"
        );
        stockLabel.setWrapText(true);
        stockLabel.getStyleClass().add("backoffice-panel-copy");

        FixedEquipmentSelectionControls controls = new FixedEquipmentSelectionControls(checkBox, quantitySpinner, card, selectable);
        fixedEquipmentSelectionControls.put(equipement.getIdEquipement(), controls);

        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            updateFixedEquipmentSelectionState(controls);
            updateDialogFixedEquipmentSummary(summaryLabel);
        });
        quantitySpinner.valueProperty().addListener((obs, oldValue, newValue) -> updateDialogFixedEquipmentSummary(summaryLabel));

        card.getChildren().addAll(topRow, stockLabel);
        if (!selectable) {
            Label noteLabel = new Label(
                quantiteRestante <= 0
                    ? "Aucune unite libre pour cette salle avec le stock actuel."
                    : "Non modifiable tant que cet equipement est en maintenance ou indisponible."
            );
            noteLabel.setWrapText(true);
            noteLabel.getStyleClass().add("fixed-equipment-status-note");
            card.getChildren().add(noteLabel);
        }
        updateFixedEquipmentSelectionState(controls);
        return card;
    }

    private void populateFixedEquipementSelection(int salleId, Label summaryLabel) {
        clearFixedEquipmentSelection(summaryLabel);
        if (salleId <= 0 || fixedEquipmentSelectionControls.isEmpty()) {
            return;
        }

        try {
            Map<Integer, Integer> equipementQuantites = salleEquipementService.getEquipementQuantitesBySalleId(salleId);
            for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
                FixedEquipmentSelectionControls controls = fixedEquipmentSelectionControls.get(entry.getKey());
                if (controls == null) {
                    continue;
                }
                controls.checkBox().setSelected(true);
                if (controls.quantitySpinner().getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory) {
                    valueFactory.setMax(Math.max(valueFactory.getMax(), Math.max(1, entry.getValue())));
                }
                controls.quantitySpinner().getValueFactory().setValue(Math.max(1, entry.getValue()));
                updateFixedEquipmentSelectionState(controls);
            }
            updateDialogFixedEquipmentSummary(summaryLabel);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            clearFixedEquipmentSelection(summaryLabel);
            showFeedback("Chargement des equipements fixes impossible : " + resolveMessage(exception), false);
        }
    }

    private void clearFixedEquipmentSelection(Label summaryLabel) {
        fixedEquipmentSelectionControls.values().forEach(controls -> {
            controls.checkBox().setSelected(false);
            controls.quantitySpinner().getValueFactory().setValue(1);
            updateFixedEquipmentSelectionState(controls);
        });
        updateDialogFixedEquipmentSummary(summaryLabel);
    }

    private Map<Integer, Integer> getSelectedFixedEquipementQuantites() {
        LinkedHashMap<Integer, Integer> equipementQuantites = new LinkedHashMap<>();
        for (Map.Entry<Integer, FixedEquipmentSelectionControls> entry : fixedEquipmentSelectionControls.entrySet()) {
            FixedEquipmentSelectionControls controls = entry.getValue();
            if (!controls.checkBox().isSelected()) {
                continue;
            }
            Integer quantite = controls.quantitySpinner().getValue();
            equipementQuantites.put(entry.getKey(), quantite == null ? 1 : Math.max(1, quantite));
        }
        return equipementQuantites;
    }

    private void updateFixedEquipmentSelectionState(FixedEquipmentSelectionControls controls) {
        boolean selected = controls.checkBox().isSelected();
        controls.checkBox().setDisable(!controls.selectable());
        controls.quantitySpinner().setDisable(!controls.selectable() || !selected);
        controls.card().getStyleClass().removeAll("selected", "unavailable");
        if (!controls.selectable()) {
            controls.card().getStyleClass().add("unavailable");
        }
        if (selected) {
            controls.card().getStyleClass().add("selected");
        }
    }

    private void updateDialogFixedEquipmentSummary(Label summaryLabel) {
        Map<Integer, Integer> selectedQuantities = getSelectedFixedEquipementQuantites();
        int selectedCount = selectedQuantities.size();
        int totalUnits = selectedQuantities.values().stream().mapToInt(Integer::intValue).sum();
        summaryLabel.setText(
            selectedCount == 0
                ? "Aucun fixe"
                : selectedCount + " type(s) | " + totalUnits + " unite(s)"
        );
        summaryLabel.getStyleClass().setAll(
            "workspace-chip",
            selectedCount == 0 ? "workspace-chip-muted" : "reservation-mode-chip-onsite"
        );
    }

    private void updateFixedEquipmentSummary(Integer salleId) {
        if (manageFixedEquipementsButton != null) {
            manageFixedEquipementsButton.setDisable(salleId == null || salleId <= 0);
        }
    }

    private Label buildDialogIntro(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("backoffice-panel-copy");
        return label;
    }

    private HBox buildDialogHeader(Label summaryLabel) {
        Label titleLabel = new Label("Resume actuel");
        titleLabel.getStyleClass().add("backoffice-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10.0, titleLabel, spacer, summaryLabel);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return header;
    }

    private void applyCurrentTheme(DialogPane dialogPane) {
        if (dialogPane == null || feedbackLabel == null || feedbackLabel.getScene() == null) {
            return;
        }
        dialogPane.getStylesheets().setAll(feedbackLabel.getScene().getStylesheets());
    }

    private String resolveStatusStyle(String status) {
        if (status == null || status.isBlank()) {
            return "pending";
        }

        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        if ("disponible".equals(normalizedStatus)) {
            return "available";
        }
        if (normalizedStatus.contains("maintenance")) {
            return "maintenance";
        }
        return "unavailable";
    }

    private boolean isSelectableFixedEquipement(Equipement equipement) {
        return equipement != null && "available".equals(resolveStatusStyle(equipement.getEtat()));
    }

    private int resolveRemainingFixedStock(Equipement equipement, int salleId) {
        if (equipement == null || equipement.getIdEquipement() <= 0) {
            return 0;
        }

        try {
            return salleEquipementService.getRemainingQuantiteForEquipement(equipement.getIdEquipement(), salleId);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            return Math.max(0, equipement.getQuantiteDisponible());
        }
    }

    private boolean hasSalleDataChanged(Salle originalSalle, Salle updatedSalle) {
        if (originalSalle == null || updatedSalle == null) {
            return true;
        }

        return !Objects.equals(normalizeComparableText(originalSalle.getNom()), normalizeComparableText(updatedSalle.getNom()))
            || originalSalle.getCapacite() != updatedSalle.getCapacite()
            || !Objects.equals(normalizeComparableText(originalSalle.getLocalisation()), normalizeComparableText(updatedSalle.getLocalisation()))
            || !Objects.equals(normalizeComparableText(originalSalle.getBatiment()), normalizeComparableText(updatedSalle.getBatiment()))
            || !Objects.equals(normalizeComparableText(originalSalle.getTypeSalle()), normalizeComparableText(updatedSalle.getTypeSalle()))
            || !Objects.equals(normalizeComparableText(originalSalle.getEtat()), normalizeComparableText(updatedSalle.getEtat()))
            || !Objects.equals(normalizeComparableText(originalSalle.getDescription()), normalizeComparableText(updatedSalle.getDescription()))
            || !Objects.equals(originalSalle.getEtage(), updatedSalle.getEtage())
            || !Objects.equals(normalizeComparableText(originalSalle.getTypeDisposition()), normalizeComparableText(updatedSalle.getTypeDisposition()))
            || originalSalle.isAccesHandicape() != updatedSalle.isAccesHandicape()
            || !Objects.equals(normalizeComparableText(originalSalle.getStatutDetaille()), normalizeComparableText(updatedSalle.getStatutDetaille()))
            || !Objects.equals(originalSalle.getDateDerniereMaintenance(), updatedSalle.getDateDerniereMaintenance());
    }

    private String normalizeComparableText(String value) {
        return trimToNull(value);
    }

    private int resolveFixedEquipmentSortRank(Equipement equipement) {
        String statusStyle = resolveStatusStyle(equipement == null ? null : equipement.getEtat());
        if ("available".equals(statusStyle)) {
            return 0;
        }
        if ("maintenance".equals(statusStyle)) {
            return 1;
        }
        return 2;
    }

    private String resolveMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Une erreur technique est survenue." : message;
    }

    private TableRow<Salle> buildSalleRow() {
        return new TableRow<>() {
            @Override
            protected void updateItem(Salle item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("backoffice-recent-row");

                if (!empty && item != null && highlightedSalleId != null && item.getIdSalle() == highlightedSalleId) {
                    getStyleClass().add("backoffice-recent-row");
                }
            }
        };
    }

    private void highlightSalle(int idSalle) {
        highlightedSalleId = idSalle > 0 ? idSalle : null;
        recentSalleHighlight.playFromStart();
        sallesTable.refresh();
    }

    private <T> TableCell<Salle, T> createTextCell(Pos alignment) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(alignment);
                setTextAlignment(alignment == Pos.CENTER ? TextAlignment.CENTER : TextAlignment.LEFT);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                setText(String.valueOf(item));
                setGraphic(null);
            }
        };
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

    private record FixedEquipmentSelectionControls(CheckBox checkBox, Spinner<Integer> quantitySpinner, VBox card,
                                                  boolean selectable) {
    }
}
