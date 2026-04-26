package tn.esprit.fahamni.controllers;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.AiRoomDesignProposal;
import tn.esprit.fahamni.Models.AiRoomDesignRequest;
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.room3d.Room3DPreviewData;
import tn.esprit.fahamni.room3d.Room3DPreviewService;
import tn.esprit.fahamni.room3d.Room3DViewerLauncher;
import tn.esprit.fahamni.services.AiRoomDesignService;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.SalleEquipementService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class BackofficeSallesController {
    private static final int DEFAULT_ROWS_PER_PAGE = 20;
    private static final int MAX_VISIBLE_PAGE_BUTTONS = 7;
    private static final List<PromptSuggestion> AI_PROMPT_SUGGESTIONS = List.of(
        new PromptSuggestion("Conference", "Je veux une salle de conference pour 30 personnes, en U, avec ecran et acces handicape."),
        new PromptSuggestion("Laboratoire", "Je veux un laboratoire informatique pour 24 etudiants avec postes fixes et circulation fluide."),
        new PromptSuggestion("Amphi hybride", "Je veux un amphitheatre hybride pour presentations et captation video."),
        new PromptSuggestion("Salle modulable", "Je veux une salle de cours lumineuse, modulaire, pour 35 places avec tableau interactif."),
        new PromptSuggestion("Salle PMR", "Je veux une salle PMR pour ateliers collaboratifs avec mobilier flexible.")
    );

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
    private final Room3DPreviewService room3DPreviewService = new Room3DPreviewService();
    private final AiRoomDesignService aiRoomDesignService = new AiRoomDesignService(salleService);
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final ObservableList<Equipement> equipementCatalog = FXCollections.observableArrayList();
    private final FilteredList<Salle> filteredSalles = new FilteredList<>(salles, salle -> true);
    private final ObservableList<Salle> displayedSalles = FXCollections.observableArrayList();
    private final Map<Integer, FixedEquipmentSelectionControls> fixedEquipmentSelectionControls = new LinkedHashMap<>();
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
            Salle salle = buildSalleFromControls(mainFormControls(), 0, null);
            salleService.add(salle);

            if (!loadSalles()) {
                return;
            }
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
            Salle salle = buildSalleFromControls(mainFormControls(), selectedSalle.getIdSalle(), selectedSalle.getDateDerniereMaintenance());
            if (!hasSalleDataChanged(selectedSalle, salle)) {
                showFeedback("Aucune modification detectee pour cette salle.", true);
                return;
            }
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
    private void handleDesignSalleWithAi() {
        hideFeedback();

        if (!loadEquipementCatalog()) {
            return;
        }

        openAiRoomDesignDialog();
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
    private void handlePreviewSalle3D() {
        hideFeedback();

        try {
            Salle selectedSalle = sallesTable == null ? null : sallesTable.getSelectionModel().getSelectedItem();
            Salle previewSalle = buildSalleFromControls(
                mainFormControls(),
                selectedSalle == null ? 0 : selectedSalle.getIdSalle(),
                selectedSalle == null ? null : selectedSalle.getDateDerniereMaintenance()
            );
            boolean preferGeneratedLayout = selectedSalle == null || hasSalleDataChanged(selectedSalle, previewSalle);
            Room3DViewerLauncher.showPreview(room3DPreviewService.buildPreview(previewSalle, preferGeneratedLayout));
            showFeedback("L'apercu 3D a ete ouvert ou actualise dans une fenetre dediee.", true);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showFeedback("Apercu 3D impossible : " + resolveMessage(exception), false);
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

        batimentComboBox.getItems().setAll(salleService.getAvailableBatiments());
        typeComboBox.getItems().setAll(salleService.getAvailableTypes());
        etatComboBox.getItems().setAll(salleService.getAvailableEtats());
        etageComboBox.getItems().setAll(0, 1, 2, 3, 4);
        typeComboBox.valueProperty().addListener((obs, oldValue, newValue) ->
            refreshDispositionOptions(newValue, dispositionComboBox == null ? null : dispositionComboBox.getValue())
        );
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

        applySalleDraftToControls(salle, mainFormControls());
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
        refreshDispositionOptions(typeComboBox.getValue(), null);
        accesHandicapeCheckBox.setSelected(false);
        statutDetailleField.clear();
        descriptionArea.clear();
        updateFixedEquipmentSummary(null);

        updateSelectionBadge(null);
    }

    private SalleFormControls mainFormControls() {
        return new SalleFormControls(
            nomField,
            capaciteSpinner,
            localisationField,
            batimentComboBox,
            typeComboBox,
            etatComboBox,
            etageComboBox,
            dispositionComboBox,
            accesHandicapeCheckBox,
            statutDetailleField,
            descriptionArea
        );
    }

    private void applySalleDraftToControls(Salle salle, SalleFormControls controls) {
        if (salle == null) {
            return;
        }

        controls.nomField().setText(defaultString(salle.getNom()));
        controls.capaciteSpinner().getValueFactory().setValue(Math.max(1, salle.getCapacite()));
        controls.localisationField().setText(defaultString(salle.getLocalisation()));
        controls.batimentComboBox().setValue(resolveAllowedValue(controls.batimentComboBox(), salle.getBatiment()));
        controls.typeComboBox().setValue(resolveAllowedValue(controls.typeComboBox(), salle.getTypeSalle()));
        controls.etatComboBox().setValue(resolveAllowedValue(controls.etatComboBox(), salle.getEtat()));
        controls.etageComboBox().setValue(resolveAllowedValue(controls.etageComboBox(), normalizeEtageValue(salle.getEtage())));
        refreshDispositionOptions(controls.dispositionComboBox(), salle.getTypeSalle(), salle.getTypeDisposition());
        controls.accesHandicapeCheckBox().setSelected(salle.isAccesHandicape());
        controls.statutDetailleField().setText(defaultString(salle.getStatutDetaille()));
        controls.descriptionArea().setText(defaultString(salle.getDescription()));
    }

    private void refreshDispositionOptions(String typeSalle, String preferredDisposition) {
        refreshDispositionOptions(dispositionComboBox, typeSalle, preferredDisposition);
    }

    private void refreshDispositionOptions(ComboBox<String> targetComboBox, String typeSalle, String preferredDisposition) {
        if (targetComboBox == null) {
            return;
        }

        targetComboBox.getItems().setAll(salleService.getAvailableDispositionsForType(typeSalle));
        targetComboBox.setDisable(targetComboBox.getItems().size() <= 1);

        String resolvedDisposition = salleService.resolveDispositionForType(typeSalle, preferredDisposition);
        targetComboBox.setValue(resolvedDisposition);
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

    private Salle buildSalleFromControls(SalleFormControls controls, int idSalle, LocalDate maintenanceDate) {
        String nom = requireText(controls.nomField().getText(), "Le nom de la salle est obligatoire.");
        int capacite = parsePositiveInteger(
            controls.capaciteSpinner().getEditor().getText(),
            "La capacite doit etre un entier positif."
        );
        String localisation = requireText(controls.localisationField().getText(), "La localisation est obligatoire.");
        String batiment = requireText(controls.batimentComboBox().getValue(), "Le batiment est obligatoire.");
        String typeSalle = requireText(controls.typeComboBox().getValue(), "Le type de salle est obligatoire.");
        String etat = requireText(controls.etatComboBox().getValue(), "L'etat de la salle est obligatoire.");
        String disposition = salleService.resolveDispositionForType(typeSalle, controls.dispositionComboBox().getValue());

        return new Salle(
            idSalle,
            nom,
            capacite,
            localisation,
            typeSalle,
            etat,
            trimToNull(controls.descriptionArea().getText()),
            batiment,
            controls.etageComboBox().getValue(),
            disposition,
            controls.accesHandicapeCheckBox().isSelected(),
            trimToNull(controls.statutDetailleField().getText()),
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

    private <T> T resolveAllowedValue(ComboBox<T> comboBox, T value) {
        if (comboBox == null || value == null || !comboBox.getItems().contains(value)) {
            return null;
        }
        return value;
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
        renderFixedEquipmentChoices(container, summaryLabel, salleId, fixedEquipmentSelectionControls, null);
    }

    private void renderFixedEquipmentChoices(
        VBox container,
        Label summaryLabel,
        int salleId,
        Map<Integer, FixedEquipmentSelectionControls> selectionControls,
        Consumer<Map<Integer, Integer>> onSelectionChanged
    ) {
        selectionControls.clear();
        container.getChildren().clear();

        if (equipementCatalog.isEmpty()) {
            Label emptyLabel = buildDialogIntro("Aucun equipement n'est disponible pour une affectation fixe.");
            container.getChildren().add(emptyLabel);
            updateDialogFixedEquipmentSummary(summaryLabel, selectionControls);
            return;
        }

        for (Equipement equipement : equipementCatalog) {
            container.getChildren().add(
                createFixedEquipmentChoiceCard(equipement, summaryLabel, salleId, selectionControls, onSelectionChanged)
            );
        }

        updateDialogFixedEquipmentSummary(summaryLabel, selectionControls);
    }

    private VBox createFixedEquipmentChoiceCard(Equipement equipement, Label summaryLabel, int salleId) {
        return createFixedEquipmentChoiceCard(equipement, summaryLabel, salleId, fixedEquipmentSelectionControls, null);
    }

    private VBox createFixedEquipmentChoiceCard(
        Equipement equipement,
        Label summaryLabel,
        int salleId,
        Map<Integer, FixedEquipmentSelectionControls> selectionControls,
        Consumer<Map<Integer, Integer>> onSelectionChanged
    ) {
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
        selectionControls.put(equipement.getIdEquipement(), controls);

        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            updateFixedEquipmentSelectionState(controls);
            updateDialogFixedEquipmentSummary(summaryLabel, selectionControls);
            notifyEquipmentSelectionChanged(selectionControls, onSelectionChanged);
        });
        quantitySpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            updateDialogFixedEquipmentSummary(summaryLabel, selectionControls);
            notifyEquipmentSelectionChanged(selectionControls, onSelectionChanged);
        });

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
        clearFixedEquipmentSelection(summaryLabel, fixedEquipmentSelectionControls);
    }

    private void clearFixedEquipmentSelection(
        Label summaryLabel,
        Map<Integer, FixedEquipmentSelectionControls> selectionControls
    ) {
        selectionControls.values().forEach(controls -> {
            controls.checkBox().setSelected(false);
            controls.quantitySpinner().getValueFactory().setValue(1);
            updateFixedEquipmentSelectionState(controls);
        });
        updateDialogFixedEquipmentSummary(summaryLabel, selectionControls);
    }

    private Map<Integer, Integer> getSelectedFixedEquipementQuantites() {
        return getSelectedFixedEquipementQuantites(fixedEquipmentSelectionControls);
    }

    private Map<Integer, Integer> getSelectedFixedEquipementQuantites(
        Map<Integer, FixedEquipmentSelectionControls> selectionControls
    ) {
        LinkedHashMap<Integer, Integer> equipementQuantites = new LinkedHashMap<>();
        for (Map.Entry<Integer, FixedEquipmentSelectionControls> entry : selectionControls.entrySet()) {
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
        updateDialogFixedEquipmentSummary(summaryLabel, fixedEquipmentSelectionControls);
    }

    private void updateDialogFixedEquipmentSummary(
        Label summaryLabel,
        Map<Integer, FixedEquipmentSelectionControls> selectionControls
    ) {
        Map<Integer, Integer> selectedQuantities = getSelectedFixedEquipementQuantites(selectionControls);
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

    private void applyFixedEquipementSelection(
        Map<Integer, Integer> suggestedQuantities,
        Label summaryLabel,
        Map<Integer, FixedEquipmentSelectionControls> selectionControls,
        Consumer<Map<Integer, Integer>> onSelectionChanged
    ) {
        clearFixedEquipmentSelection(summaryLabel, selectionControls);
        if (suggestedQuantities == null || suggestedQuantities.isEmpty()) {
            notifyEquipmentSelectionChanged(selectionControls, onSelectionChanged);
            return;
        }

        for (Map.Entry<Integer, Integer> entry : suggestedQuantities.entrySet()) {
            FixedEquipmentSelectionControls controls = selectionControls.get(entry.getKey());
            if (controls == null || !controls.selectable()) {
                continue;
            }

            controls.checkBox().setSelected(true);
            if (controls.quantitySpinner().getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory) {
                valueFactory.setMax(Math.max(valueFactory.getMax(), Math.max(1, entry.getValue())));
            }
            controls.quantitySpinner().getValueFactory().setValue(Math.max(1, entry.getValue()));
            updateFixedEquipmentSelectionState(controls);
        }

        updateDialogFixedEquipmentSummary(summaryLabel, selectionControls);
        notifyEquipmentSelectionChanged(selectionControls, onSelectionChanged);
    }

    private void notifyEquipmentSelectionChanged(
        Map<Integer, FixedEquipmentSelectionControls> selectionControls,
        Consumer<Map<Integer, Integer>> onSelectionChanged
    ) {
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(getSelectedFixedEquipementQuantites(selectionControls));
        }
    }

    private void updateFixedEquipmentSummary(Integer salleId) {
        if (manageFixedEquipementsButton != null) {
            manageFixedEquipementsButton.setDisable(salleId == null || salleId <= 0);
        }
    }

    private VBox labelledField(String labelText, Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("backoffice-form-label");

        VBox wrapper = new VBox(6.0, label, field);
        wrapper.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private String defaultAiBrief() {
        String type = trimToNull(typeComboBox == null ? null : typeComboBox.getValue());
        String disposition = trimToNull(dispositionComboBox == null ? null : dispositionComboBox.getValue());
        int capacity = resolveCurrentCapacityDraft();

        StringBuilder builder = new StringBuilder("Je veux une salle");
        if (type != null) {
            builder.append(" de type ").append(type);
        }
        builder.append(" pour ").append(capacity).append(" places");
        if (disposition != null) {
            builder.append(" avec une disposition ").append(disposition);
        }
        builder.append(".");
        return builder.toString();
    }

    private int resolveCurrentCapacityDraft() {
        return resolveCapacityDraft(capaciteSpinner);
    }

    private void openAiRoomDesignDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Conception AI de salle");
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        ButtonType previewButtonType = new ButtonType("Apercu 3D", ButtonBar.ButtonData.LEFT);
        ButtonType createButtonType = new ButtonType("Creer la salle", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, previewButtonType, createButtonType);
        applyCurrentTheme(dialog.getDialogPane());
        dialog.getDialogPane().getStyleClass().add("backoffice-room-equipment-dialog");

        TextArea briefArea = new TextArea(defaultAiBrief());
        briefArea.setPromptText("Ex: Je veux une salle de conference pour 30 personnes, en U, avec projecteur et acces handicape.");
        briefArea.setWrapText(true);
        briefArea.setPrefRowCount(5);
        briefArea.getStyleClass().add("backoffice-form-textarea");

        Button generateProposalButton = new Button("Construction intelligente \u2728");
        generateProposalButton.setMaxWidth(Region.USE_PREF_SIZE);
        generateProposalButton.getStyleClass().addAll("backoffice-room-preview-button", "ai-dialog-generate-button");
        generateProposalButton.setContentDisplay(ContentDisplay.LEFT);
        generateProposalButton.setGraphicTextGap(8.0);
        ProgressIndicator generateButtonLoadingIndicator = new ProgressIndicator();
        generateButtonLoadingIndicator.setPrefSize(16.0, 16.0);
        generateButtonLoadingIndicator.setMaxSize(16.0, 16.0);
        generateButtonLoadingIndicator.getStyleClass().add("ai-dialog-button-spinner");
        Button variantButton = new Button("Nouvelle variante");
        variantButton.setMaxWidth(Region.USE_PREF_SIZE);
        variantButton.getStyleClass().addAll("backoffice-secondary-button", "ai-dialog-variant-button");
        Button cancelGenerationButton = new Button("Annuler");
        cancelGenerationButton.setMaxWidth(Region.USE_PREF_SIZE);
        cancelGenerationButton.getStyleClass().addAll("backoffice-secondary-button", "ai-dialog-cancel-generation-button");

        Label dialogFeedbackLabel = new Label();
        dialogFeedbackLabel.setWrapText(true);
        dialogFeedbackLabel.setManaged(false);
        dialogFeedbackLabel.setVisible(false);
        dialogFeedbackLabel.getStyleClass().add("backoffice-feedback");

        ProgressIndicator generationIndicator = new ProgressIndicator();
        generationIndicator.setPrefSize(28.0, 28.0);
        generationIndicator.getStyleClass().add("ai-dialog-progress");

        Label generationStatusTitle = new Label("Construction intelligente en cours...");
        generationStatusTitle.getStyleClass().add("ai-dialog-status-title");
        Label generationStatusCopy = new Label("Analyse du prompt, preparation des champs et suggestion des equipements...");
        generationStatusCopy.setWrapText(true);
        generationStatusCopy.getStyleClass().add("ai-dialog-status-copy");

        VBox generationStatusTexts = new VBox(2.0, generationStatusTitle, generationStatusCopy);
        HBox generationStatusBanner = new HBox(12.0, generationIndicator, generationStatusTexts);
        generationStatusBanner.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        generationStatusBanner.setManaged(false);
        generationStatusBanner.setVisible(false);
        generationStatusBanner.getStyleClass().add("ai-dialog-status-banner");

        SalleFormControls dialogControls = createAiDialogFormControls();
        initializeAiDialogFormControls(dialogControls);
        AiDesignDialogState dialogState = new AiDesignDialogState();
        Map<Integer, FixedEquipmentSelectionControls> aiEquipmentSelectionControls = new LinkedHashMap<>();

        Label dialogTitle = new Label("Concevoir une salle avec assistance intelligente");
        dialogTitle.getStyleClass().addAll("backoffice-room-equipment-dialog-title", "ai-dialog-hero-title");

        Label dialogSubtitle = buildDialogIntro(
            "Racontez l'espace voulu, laissez l'intelligence proposer une base, puis ajustez-la avant l'aperçu 3D."
        );
        dialogSubtitle.getStyleClass().add("ai-dialog-hero-copy");

        VBox heroBlock = new VBox(8.0, dialogTitle, dialogSubtitle);
        heroBlock.getStyleClass().add("ai-dialog-hero");

        Label formSectionTitle = new Label("Ajustez les champs avant creation");
        formSectionTitle.getStyleClass().addAll("backoffice-section-title", "ai-dialog-section-title");

        Label equipmentSummaryChip = new Label("Aucun fixe");
        equipmentSummaryChip.getStyleClass().setAll("workspace-chip", "workspace-chip-muted");
        Label equipmentRecapLabel = buildDialogIntro("Materiel fixe retenu: aucun.");

        Consumer<Map<Integer, Integer>> equipmentSelectionHandler = selectedQuantities ->
            handleAiDialogEquipmentSelectionChanged(dialogControls, dialogState, equipmentRecapLabel, selectedQuantities);

        VBox equipmentChoicesContainer = new VBox(8.0);
        equipmentChoicesContainer.setFillWidth(true);
        renderFixedEquipmentChoices(
            equipmentChoicesContainer,
            equipmentSummaryChip,
            0,
            aiEquipmentSelectionControls,
            equipmentSelectionHandler
        );
        handleAiDialogEquipmentSelectionChanged(
            dialogControls,
            dialogState,
            equipmentRecapLabel,
            getSelectedFixedEquipementQuantites(aiEquipmentSelectionControls)
        );

        attachAiDialogReactiveBehaviors(dialogControls, dialogState);

        VBox formContent = new VBox(
            16.0,
            heroBlock,
            buildAiDialogPromptSection(
                briefArea,
                generateProposalButton,
                variantButton,
                cancelGenerationButton,
                generationStatusBanner,
                dialogFeedbackLabel
            ),
            formSectionTitle,
            buildAiDialogFormContent(dialogControls),
            buildAiDialogEquipmentSection(equipmentSummaryChip, equipmentRecapLabel, equipmentChoicesContainer)
        );
        formContent.setFillWidth(true);
        formContent.getStyleClass().add("backoffice-room-equipment-dialog-content");

        ScrollPane scrollPane = new ScrollPane(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(640.0);
        scrollPane.getStyleClass().addAll("reservation-choice-scroll", "backoffice-room-equipment-dialog-scroll");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setPrefWidth(780.0);
        dialog.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Button previewButton = (Button) dialog.getDialogPane().lookupButton(previewButtonType);
        if (previewButton != null) {
            previewButton.getStyleClass().addAll("backoffice-room-preview-button");
        }

        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        if (createButton != null) {
            createButton.getStyleClass().addAll("backoffice-primary-button", "backoffice-dialog-save-button");
        }

        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) {
            cancelButton.getStyleClass().addAll("backoffice-secondary-button", "backoffice-dialog-cancel-button");
            cancelButton.setCancelButton(true);
        }

        updateAiDialogActionState(
            briefArea,
            dialogState,
            generateProposalButton,
            generateButtonLoadingIndicator,
            variantButton,
            cancelGenerationButton,
            previewButton,
            createButton,
            generationStatusBanner
        );
        briefArea.textProperty().addListener((obs, oldValue, newValue) ->
            updateAiDialogActionState(
                briefArea,
                dialogState,
                generateProposalButton,
                generateButtonLoadingIndicator,
                variantButton,
                cancelGenerationButton,
                previewButton,
                createButton,
                generationStatusBanner
            )
        );

        generateProposalButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            startAiDialogGeneration(
                briefArea,
                dialogControls,
                dialogState,
                aiEquipmentSelectionControls,
                equipmentSummaryChip,
                equipmentRecapLabel,
                dialogFeedbackLabel,
                generateProposalButton,
                generateButtonLoadingIndicator,
                variantButton,
                cancelGenerationButton,
                previewButton,
                createButton,
                generationStatusBanner
            );
        });

        variantButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            startAiDialogGeneration(
                briefArea,
                dialogControls,
                dialogState,
                aiEquipmentSelectionControls,
                equipmentSummaryChip,
                equipmentRecapLabel,
                dialogFeedbackLabel,
                generateProposalButton,
                generateButtonLoadingIndicator,
                variantButton,
                cancelGenerationButton,
                previewButton,
                createButton,
                generationStatusBanner
            );
        });

        cancelGenerationButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            Task<AiRoomDesignProposal> generationTask = dialogState.generationTask();
            if (generationTask != null) {
                generationTask.cancel(true);
            }
        });

        if (previewButton != null) {
            previewButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume();
                hideDialogFeedback(dialogFeedbackLabel);

                try {
                    Salle draftSalle = buildSalleFromControls(dialogControls, 0, null);
                    showAiDialogPreview(draftSalle, dialogState.proposal(), dialogState.selectedFixedEquipmentSummary());
                    showDialogFeedback(dialogFeedbackLabel, "L'apercu 3D de la proposition a ete ouvert.", true);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    showDialogFeedback(dialogFeedbackLabel, "Apercu 3D impossible : " + resolveMessage(exception), false);
                }
            });
        }

        if (createButton != null) {
            createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                hideDialogFeedback(dialogFeedbackLabel);

                try {
                    Salle salle = buildSalleFromControls(dialogControls, 0, null);
                    salleService.add(salle);
                    String aiEquipmentMessage = applyAiFixedEquipementsIfNeeded(
                        salle.getIdSalle(),
                        getSelectedFixedEquipementQuantites(aiEquipmentSelectionControls)
                    );

                    if (!loadSalles()) {
                        showFeedback("La salle a ete creee, mais la liste n'a pas pu etre rechargee automatiquement.", true);
                    } else {
                        selectSalleById(salle.getIdSalle());
                        showFeedback(
                            aiEquipmentMessage == null
                                ? "La salle creee depuis la proposition intelligente a ete enregistree avec succes."
                                : "La salle creee depuis la proposition intelligente a ete enregistree avec succes. " + aiEquipmentMessage,
                            true
                        );
                    }
                } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
                    event.consume();
                    showDialogFeedback(dialogFeedbackLabel, "Creation impossible : " + resolveMessage(exception), false);
                }
            });
        }

        dialog.setOnCloseRequest(event -> {
            Task<AiRoomDesignProposal> generationTask = dialogState.generationTask();
            if (generationTask != null) {
                generationTask.cancel(true);
            }
        });

        dialog.showAndWait();
    }

    private VBox buildAiDialogPromptSection(
        TextArea briefArea,
        Button generateProposalButton,
        Button variantButton,
        Button cancelGenerationButton,
        HBox generationStatusBanner,
        Label dialogFeedbackLabel
    ) {
        Label promptTitle = new Label("Prompt de conception");
        promptTitle.getStyleClass().addAll("backoffice-section-title", "ai-dialog-section-title");

        Label promptHint = buildDialogIntro(
            "Decrivez simplement l'ambiance, le type de salle, la capacite ou les contraintes importantes."
        );
        promptHint.getStyleClass().add("ai-dialog-prompt-copy");

        FlowPane suggestionFlow = buildAiPromptSuggestionFlow(briefArea);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        HBox actionsRow = new HBox(
            10.0,
            actionSpacer,
            cancelGenerationButton,
            variantButton,
            generateProposalButton
        );
        actionsRow.setFillHeight(true);
        actionsRow.getStyleClass().add("ai-dialog-prompt-actions");

        VBox promptSection = new VBox(
            10.0,
            promptTitle,
            promptHint,
            suggestionFlow,
            briefArea,
            actionsRow,
            generationStatusBanner,
            dialogFeedbackLabel
        );
        promptSection.getStyleClass().add("ai-dialog-prompt-card");
        return promptSection;
    }

    private FlowPane buildAiPromptSuggestionFlow(TextArea briefArea) {
        FlowPane suggestionFlow = new FlowPane();
        suggestionFlow.setHgap(8.0);
        suggestionFlow.setVgap(8.0);
        suggestionFlow.getStyleClass().add("ai-dialog-suggestions");

        for (PromptSuggestion suggestion : AI_PROMPT_SUGGESTIONS) {
            Button suggestionButton = new Button(suggestion.label());
            suggestionButton.getStyleClass().addAll("backoffice-secondary-button", "ai-dialog-suggestion-button");
            suggestionButton.setOnAction(event -> {
                briefArea.setText(suggestion.prompt());
                briefArea.requestFocus();
                briefArea.positionCaret(briefArea.getText().length());
            });
            suggestionFlow.getChildren().add(suggestionButton);
        }

        return suggestionFlow;
    }

    private VBox buildAiDialogEquipmentSection(
        Label equipmentSummaryChip,
        Label equipmentRecapLabel,
        VBox equipmentChoicesContainer
    ) {
        Label equipmentTitle = new Label("Equipements fixes a valider");
        equipmentTitle.getStyleClass().addAll("backoffice-section-title", "ai-dialog-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10.0, equipmentTitle, spacer, equipmentSummaryChip);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label helperCopy = buildDialogIntro(
            "Les suggestions peuvent etre corrigees avant la creation. Seuls les equipements retenus seront appliques a la salle."
        );

        ScrollPane equipmentScroll = new ScrollPane(equipmentChoicesContainer);
        equipmentScroll.setFitToWidth(true);
        equipmentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        equipmentScroll.setPrefViewportHeight(240.0);
        equipmentScroll.setPrefHeight(252.0);
        equipmentScroll.setMaxHeight(252.0);
        equipmentScroll.getStyleClass().addAll("reservation-choice-scroll", "backoffice-room-equipment-dialog-scroll");

        VBox equipmentSection = new VBox(10.0, header, helperCopy, equipmentRecapLabel, equipmentScroll);
        equipmentSection.getStyleClass().add("ai-dialog-form-section");
        return equipmentSection;
    }

    private VBox buildAiDialogFormContent(SalleFormControls controls) {
        VBox content = new VBox(
            12.0,
            buildDialogFormRow(
                labelledField("Nom de la salle *", controls.nomField()),
                labelledField("Capacite *", controls.capaciteSpinner())
            ),
            buildDialogFormRow(
                labelledField("Localisation *", controls.localisationField()),
                labelledField("Batiment *", controls.batimentComboBox())
            ),
            buildDialogFormRow(
                labelledField("Type de salle *", controls.typeComboBox()),
                labelledField("Etat *", controls.etatComboBox())
            ),
            buildDialogFormRow(
                labelledField("Etage", controls.etageComboBox()),
                labelledField("Disposition", controls.dispositionComboBox())
            ),
            labelledField("Statut detaille", controls.statutDetailleField()),
            labelledField("Description", controls.descriptionArea())
        );
        content.getChildren().add(4, buildDialogField("Accessibilite", controls.accesHandicapeCheckBox()));
        content.getStyleClass().add("ai-dialog-form-section");
        return content;
    }

    private void startAiDialogGeneration(
        TextArea briefArea,
        SalleFormControls dialogControls,
        AiDesignDialogState dialogState,
        Map<Integer, FixedEquipmentSelectionControls> aiEquipmentSelectionControls,
        Label equipmentSummaryChip,
        Label equipmentRecapLabel,
        Label dialogFeedbackLabel,
        Button generateProposalButton,
        ProgressIndicator generateButtonLoadingIndicator,
        Button variantButton,
        Button cancelGenerationButton,
        Button previewButton,
        Button createButton,
        HBox generationStatusBanner
    ) {
        hideDialogFeedback(dialogFeedbackLabel);

        final AiRoomDesignRequest request;
        try {
            request = buildAiDesignRequest(briefArea, dialogControls, dialogState.nextVariantIndex());
        } catch (IllegalArgumentException exception) {
            showDialogFeedback(dialogFeedbackLabel, "Conception impossible : " + resolveMessage(exception), false);
            return;
        }

        Task<AiRoomDesignProposal> generationTask = new Task<>() {
            @Override
            protected AiRoomDesignProposal call() {
                return aiRoomDesignService.generateDesign(request, List.copyOf(equipementCatalog));
            }
        };

        dialogState.setGenerationTask(generationTask);
        updateAiDialogActionState(
            briefArea,
            dialogState,
            generateProposalButton,
            generateButtonLoadingIndicator,
            variantButton,
            cancelGenerationButton,
            previewButton,
            createButton,
            generationStatusBanner
        );
        showDialogFeedback(dialogFeedbackLabel, "Generation en cours. Le formulaire sera mis a jour automatiquement des que la proposition est prete.", true);

        generationTask.setOnSucceeded(event -> {
            if (dialogState.generationTask() != generationTask) {
                return;
            }

            AiRoomDesignProposal proposal = generationTask.getValue();
            dialogState.setProposal(proposal);
            dialogState.setNextVariantIndex(proposal.variantIndex() + 1);

            dialogState.setDescriptionSyncMuted(true);
            applySalleDraftToControls(proposal.salle(), dialogControls);
            applyFixedEquipementSelection(
                proposal.suggestedFixedEquipements(),
                equipmentSummaryChip,
                aiEquipmentSelectionControls,
                selectedQuantities -> handleAiDialogEquipmentSelectionChanged(dialogControls, dialogState, equipmentRecapLabel, selectedQuantities)
            );
            dialogState.setDescriptionSyncMuted(false);
            dialogState.setAutoDescriptionEnabled(true);
            dialogState.setLastAutoDescription(trimToNull(dialogControls.descriptionArea().getText()));
            handleAiDialogEquipmentSelectionChanged(
                dialogControls,
                dialogState,
                equipmentRecapLabel,
                getSelectedFixedEquipementQuantites(aiEquipmentSelectionControls)
            );

            dialogState.setGenerationTask(null);
            updateAiDialogActionState(
                briefArea,
                dialogState,
                generateProposalButton,
                generateButtonLoadingIndicator,
                variantButton,
                cancelGenerationButton,
                previewButton,
                createButton,
                generationStatusBanner
            );
            showDialogFeedback(
                dialogFeedbackLabel,
                "Proposition generee avec succes.",
                true
            );
        });

        generationTask.setOnFailed(event -> {
            if (dialogState.generationTask() != generationTask) {
                return;
            }

            dialogState.setGenerationTask(null);
            updateAiDialogActionState(
                briefArea,
                dialogState,
                generateProposalButton,
                generateButtonLoadingIndicator,
                variantButton,
                cancelGenerationButton,
                previewButton,
                createButton,
                generationStatusBanner
            );

            Throwable exception = generationTask.getException();
            String message = resolveThrowableMessage(exception);
            showDialogFeedback(dialogFeedbackLabel, "Conception impossible : " + message, false);
        });

        generationTask.setOnCancelled(event -> {
            if (dialogState.generationTask() != generationTask) {
                return;
            }

            dialogState.setGenerationTask(null);
            updateAiDialogActionState(
                briefArea,
                dialogState,
                generateProposalButton,
                generateButtonLoadingIndicator,
                variantButton,
                cancelGenerationButton,
                previewButton,
                createButton,
                generationStatusBanner
            );
            showDialogFeedback(dialogFeedbackLabel, "Generation annulee. Vous pouvez reprendre ou essayer une nouvelle variante.", false);
        });

        Thread generationThread = new Thread(generationTask, "fahamni-ai-room-generation");
        generationThread.setDaemon(true);
        generationThread.start();
    }

    private void updateAiDialogActionState(
        TextArea briefArea,
        AiDesignDialogState dialogState,
        Button generateProposalButton,
        ProgressIndicator generateButtonLoadingIndicator,
        Button variantButton,
        Button cancelGenerationButton,
        Button previewButton,
        Button createButton,
        HBox generationStatusBanner
    ) {
        boolean hasPrompt = trimToNull(briefArea.getText()) != null;
        boolean isGenerating = dialogState.isGenerating();
        boolean hasProposal = dialogState.proposal() != null;

        generateProposalButton.setDisable(!hasPrompt || isGenerating);
        generateProposalButton.setText(isGenerating ? "Chargement..." : "Construction intelligente \u2728");
        generateProposalButton.setGraphic(isGenerating ? generateButtonLoadingIndicator : null);
        variantButton.setDisable(!hasPrompt || isGenerating || !hasProposal);
        cancelGenerationButton.setDisable(!isGenerating);
        cancelGenerationButton.setManaged(isGenerating);
        cancelGenerationButton.setVisible(isGenerating);
        generationStatusBanner.setManaged(isGenerating);
        generationStatusBanner.setVisible(isGenerating);

        if (previewButton != null) {
            previewButton.setDisable(isGenerating || !hasProposal);
        }
        if (createButton != null) {
            createButton.setDisable(isGenerating || !hasProposal);
            createButton.setDefaultButton(hasProposal && !isGenerating);
        }

        generateProposalButton.setDefaultButton(!hasProposal && hasPrompt && !isGenerating);
    }

    private void attachAiDialogReactiveBehaviors(SalleFormControls controls, AiDesignDialogState dialogState) {
        controls.typeComboBox().valueProperty().addListener((obs, oldValue, newValue) ->
            synchronizeAiDialogDescriptionIfNeeded(controls, dialogState, false)
        );
        controls.dispositionComboBox().valueProperty().addListener((obs, oldValue, newValue) ->
            synchronizeAiDialogDescriptionIfNeeded(controls, dialogState, false)
        );
        controls.capaciteSpinner().valueProperty().addListener((obs, oldValue, newValue) ->
            synchronizeAiDialogDescriptionIfNeeded(controls, dialogState, false)
        );
        controls.capaciteSpinner().getEditor().textProperty().addListener((obs, oldValue, newValue) ->
            synchronizeAiDialogDescriptionIfNeeded(controls, dialogState, false)
        );
        controls.accesHandicapeCheckBox().selectedProperty().addListener((obs, oldValue, newValue) ->
            synchronizeAiDialogDescriptionIfNeeded(controls, dialogState, false)
        );
        controls.descriptionArea().textProperty().addListener((obs, oldValue, newValue) -> {
            if (dialogState.descriptionSyncMuted()) {
                return;
            }

            String normalizedNewValue = trimToNull(newValue);
            String normalizedAutoValue = trimToNull(dialogState.lastAutoDescription());
            dialogState.setAutoDescriptionEnabled(
                normalizedNewValue == null || Objects.equals(normalizedNewValue, normalizedAutoValue)
            );
        });
    }

    private void handleAiDialogEquipmentSelectionChanged(
        SalleFormControls controls,
        AiDesignDialogState dialogState,
        Label equipmentRecapLabel,
        Map<Integer, Integer> selectedQuantities
    ) {
        Map<Integer, Integer> safeSelection = selectedQuantities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(selectedQuantities));
        dialogState.setSelectedFixedEquipements(safeSelection);
        String equipmentSummary = buildSelectedFixedEquipmentSummary(safeSelection);
        dialogState.setSelectedFixedEquipmentSummary(equipmentSummary);
        equipmentRecapLabel.setText("Materiel fixe retenu: " + equipmentSummary + ".");
        synchronizeAiDialogDescriptionIfNeeded(controls, dialogState, false);
    }

    private void synchronizeAiDialogDescriptionIfNeeded(
        SalleFormControls controls,
        AiDesignDialogState dialogState,
        boolean force
    ) {
        if (dialogState.descriptionSyncMuted() || (!force && !dialogState.autoDescriptionEnabled())) {
            return;
        }

        String description = AiRoomDesignService.buildCompactDescription(
            controls.typeComboBox().getValue(),
            controls.dispositionComboBox().getValue(),
            resolveCapacityDraft(controls.capaciteSpinner()),
            controls.accesHandicapeCheckBox().isSelected(),
            dialogState.selectedFixedEquipmentSummary()
        );

        dialogState.setDescriptionSyncMuted(true);
        controls.descriptionArea().setText(description);
        dialogState.setDescriptionSyncMuted(false);
        dialogState.setLastAutoDescription(description);
        dialogState.setAutoDescriptionEnabled(true);
    }

    private String buildSelectedFixedEquipmentSummary(Map<Integer, Integer> selectedQuantities) {
        if (selectedQuantities == null || selectedQuantities.isEmpty()) {
            return "aucun";
        }

        List<String> parts = new java.util.ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : selectedQuantities.entrySet()) {
            Equipement equipement = findEquipementById(entry.getKey());
            if (equipement != null) {
                parts.add(formatOptionalText(equipement.getNom()) + " x" + Math.max(1, entry.getValue()));
            }
        }

        return parts.isEmpty() ? "aucun" : String.join(", ", parts);
    }

    private Equipement findEquipementById(int equipementId) {
        for (Equipement equipement : equipementCatalog) {
            if (equipement != null && equipement.getIdEquipement() == equipementId) {
                return equipement;
            }
        }
        return null;
    }

    private HBox buildDialogFormRow(VBox leftField, VBox rightField) {
        leftField.setMaxWidth(Double.MAX_VALUE);
        rightField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(leftField, Priority.ALWAYS);
        HBox.setHgrow(rightField, Priority.ALWAYS);

        HBox row = new HBox(12.0, leftField, rightField);
        row.setFillHeight(true);
        return row;
    }

    private VBox buildDialogField(String labelText, Node field) {
        return labelledField(labelText, field);
    }

    private SalleFormControls createAiDialogFormControls() {
        TextField dialogNomField = new TextField();
        dialogNomField.setPromptText("Ex: Salle Atlas");
        dialogNomField.getStyleClass().add("backoffice-form-input");

        Spinner<Integer> dialogCapaciteSpinner = new Spinner<>();
        dialogCapaciteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, resolveCurrentCapacityDraft()));
        dialogCapaciteSpinner.setEditable(true);
        dialogCapaciteSpinner.getStyleClass().add("backoffice-form-spinner");

        TextField dialogLocalisationField = new TextField();
        dialogLocalisationField.setPromptText("Ex: Bloc B - Aile Est");
        dialogLocalisationField.getStyleClass().add("backoffice-form-input");

        ComboBox<String> dialogBatimentComboBox = new ComboBox<>();
        dialogBatimentComboBox.getItems().setAll(salleService.getAvailableBatiments());
        dialogBatimentComboBox.getStyleClass().add("backoffice-form-input");

        ComboBox<String> dialogTypeComboBox = new ComboBox<>();
        dialogTypeComboBox.getItems().setAll(salleService.getAvailableTypes());
        dialogTypeComboBox.getStyleClass().add("backoffice-form-input");

        ComboBox<String> dialogEtatComboBox = new ComboBox<>();
        dialogEtatComboBox.getItems().setAll(salleService.getAvailableEtats());
        dialogEtatComboBox.getStyleClass().add("backoffice-form-input");

        ComboBox<Integer> dialogEtageComboBox = new ComboBox<>();
        dialogEtageComboBox.getItems().setAll(0, 1, 2, 3, 4);
        dialogEtageComboBox.getStyleClass().add("backoffice-form-input");

        ComboBox<String> dialogDispositionComboBox = new ComboBox<>();
        dialogDispositionComboBox.getStyleClass().add("backoffice-form-input");

        CheckBox dialogAccesCheckBox = new CheckBox("Acces handicape");
        dialogAccesCheckBox.getStyleClass().add("backoffice-check");

        TextField dialogStatutDetailleField = new TextField();
        dialogStatutDetailleField.setPromptText("Ex: Projecteur en revision ou salle reservee");
        dialogStatutDetailleField.getStyleClass().add("backoffice-form-input");

        TextArea dialogDescriptionArea = new TextArea();
        dialogDescriptionArea.setPromptText("Ex: Salle de conference lumineuse pour 30 places.");
        dialogDescriptionArea.setPrefRowCount(3);
        dialogDescriptionArea.setWrapText(true);
        dialogDescriptionArea.getStyleClass().add("backoffice-form-textarea");

        dialogTypeComboBox.valueProperty().addListener((obs, oldValue, newValue) ->
            refreshDispositionOptions(dialogDispositionComboBox, newValue, dialogDispositionComboBox.getValue())
        );

        return new SalleFormControls(
            dialogNomField,
            dialogCapaciteSpinner,
            dialogLocalisationField,
            dialogBatimentComboBox,
            dialogTypeComboBox,
            dialogEtatComboBox,
            dialogEtageComboBox,
            dialogDispositionComboBox,
            dialogAccesCheckBox,
            dialogStatutDetailleField,
            dialogDescriptionArea
        );
    }

    private void initializeAiDialogFormControls(SalleFormControls controls) {
        controls.nomField().setText(defaultString(trimToNull(nomField.getText())));
        controls.capaciteSpinner().getValueFactory().setValue(resolveCurrentCapacityDraft());
        controls.localisationField().setText(defaultString(trimToNull(localisationField.getText())));
        controls.batimentComboBox().setValue(resolveAllowedValue(controls.batimentComboBox(), batimentComboBox.getValue()));

        String preferredType = hasMeaningfulMainFormDraft()
            ? resolveAllowedValue(controls.typeComboBox(), typeComboBox.getValue())
            : null;
        controls.typeComboBox().setValue(preferredType);

        String preferredEtat = resolveAllowedValue(controls.etatComboBox(), etatComboBox.getValue());
        if (preferredEtat == null && !controls.etatComboBox().getItems().isEmpty()) {
            preferredEtat = controls.etatComboBox().getItems().get(0);
        }
        controls.etatComboBox().setValue(preferredEtat);
        controls.etageComboBox().setValue(resolveAllowedValue(controls.etageComboBox(), etageComboBox.getValue()));
        refreshDispositionOptions(
            controls.dispositionComboBox(),
            preferredType,
            hasMeaningfulMainFormDraft() ? dispositionComboBox.getValue() : null
        );
        controls.accesHandicapeCheckBox().setSelected(accesHandicapeCheckBox.isSelected());
        controls.statutDetailleField().setText(defaultString(trimToNull(statutDetailleField.getText())));
        controls.descriptionArea().setText(defaultString(trimToNull(descriptionArea.getText())));
    }

    private AiRoomDesignRequest buildAiDesignRequest(TextArea briefArea, SalleFormControls controls, int variantIndex) {
        String preferredType = resolveAiRequestTypePreference(controls);
        String preferredDisposition = resolveAiRequestDispositionPreference(controls, preferredType);
        return new AiRoomDesignRequest(
            requireText(briefArea.getText(), "Le prompt AI est obligatoire."),
            trimToNull(controls.nomField().getText()),
            controls.batimentComboBox().getValue(),
            controls.etageComboBox().getValue(),
            trimToNull(controls.localisationField().getText()),
            resolveCapacityDraft(controls.capaciteSpinner()),
            preferredType,
            preferredDisposition,
            controls.accesHandicapeCheckBox().isSelected(),
            null,
            variantIndex
        );
    }

    private boolean hasMeaningfulMainFormDraft() {
        Salle selectedSalle = sallesTable == null ? null : sallesTable.getSelectionModel().getSelectedItem();
        if (selectedSalle != null) {
            return true;
        }

        String defaultType = typeComboBox == null || typeComboBox.getItems().isEmpty() ? null : typeComboBox.getItems().get(0);
        String suggestedDisposition = salleService.getSuggestedDispositionForType(typeComboBox == null ? null : typeComboBox.getValue());
        return trimToNull(nomField == null ? null : nomField.getText()) != null
            || trimToNull(localisationField == null ? null : localisationField.getText()) != null
            || (batimentComboBox != null && batimentComboBox.getValue() != null)
            || (etageComboBox != null && etageComboBox.getValue() != null)
            || !Objects.equals(normalizeComparableText(typeComboBox == null ? null : typeComboBox.getValue()), normalizeComparableText(defaultType))
            || !Objects.equals(normalizeComparableText(dispositionComboBox == null ? null : dispositionComboBox.getValue()), normalizeComparableText(suggestedDisposition))
            || (accesHandicapeCheckBox != null && accesHandicapeCheckBox.isSelected())
            || trimToNull(descriptionArea == null ? null : descriptionArea.getText()) != null
            || trimToNull(statutDetailleField == null ? null : statutDetailleField.getText()) != null;
    }

    private String resolveAiRequestTypePreference(SalleFormControls controls) {
        String preferredType = controls.typeComboBox().getValue();
        if (trimToNull(preferredType) == null) {
            return null;
        }

        String defaultType = controls.typeComboBox().getItems().isEmpty() ? null : controls.typeComboBox().getItems().get(0);
        if (!hasMeaningfulAiDialogDraft(controls)
            && Objects.equals(normalizeComparableText(preferredType), normalizeComparableText(defaultType))) {
            return null;
        }
        return preferredType;
    }

    private String resolveAiRequestDispositionPreference(SalleFormControls controls, String preferredType) {
        String preferredDisposition = controls.dispositionComboBox().getValue();
        if (trimToNull(preferredDisposition) == null || trimToNull(preferredType) == null) {
            return null;
        }

        String suggestedDisposition = salleService.getSuggestedDispositionForType(preferredType);
        if (!hasMeaningfulAiDialogDraft(controls)
            && Objects.equals(normalizeComparableText(preferredDisposition), normalizeComparableText(suggestedDisposition))) {
            return null;
        }
        return preferredDisposition;
    }

    private boolean hasMeaningfulAiDialogDraft(SalleFormControls controls) {
        if (controls == null) {
            return false;
        }

        String defaultType = controls.typeComboBox().getItems().isEmpty() ? null : controls.typeComboBox().getItems().get(0);
        String suggestedDisposition = salleService.getSuggestedDispositionForType(controls.typeComboBox().getValue());
        return trimToNull(controls.nomField().getText()) != null
            || trimToNull(controls.localisationField().getText()) != null
            || controls.batimentComboBox().getValue() != null
            || controls.etageComboBox().getValue() != null
            || !Objects.equals(normalizeComparableText(controls.typeComboBox().getValue()), normalizeComparableText(defaultType))
            || !Objects.equals(normalizeComparableText(controls.dispositionComboBox().getValue()), normalizeComparableText(suggestedDisposition))
            || controls.accesHandicapeCheckBox().isSelected()
            || trimToNull(controls.descriptionArea().getText()) != null
            || trimToNull(controls.statutDetailleField().getText()) != null;
    }

    private int resolveCapacityDraft(Spinner<Integer> spinner) {
        try {
            return parsePositiveInteger(spinner.getEditor().getText(), "Capacite invalide");
        } catch (IllegalArgumentException exception) {
            Integer fallbackValue = spinner.getValue();
            return fallbackValue == null || fallbackValue <= 0 ? 24 : fallbackValue;
        }
    }

    private void showAiDialogPreview(Salle salle, AiRoomDesignProposal proposal, String fixedEquipmentSummary) {
        Room3DPreviewData previewData = room3DPreviewService.buildPreview(
            salle,
            true,
            tn.esprit.fahamni.room3d.Room3DViewMode.DESIGN_REVIEW
        );
        if (proposal != null) {
            previewData = previewData.withAnnotations(
                "Proposition 3D non enregistree | " + salle.getNom(),
                null,
                null
            );
        }
        Room3DViewerLauncher.showPreview(previewData);
    }

    private String buildAiDialogPreviewSummary(Salle salle, AiRoomDesignProposal proposal, String fixedEquipmentSummary) {
        String disposition = trimToNull(salle == null ? null : salle.getTypeDisposition());
        return "Proposition non enregistree | Source: " + proposal.sourceLabelOrDefault() + "."
            + "\nDisposition: " + (disposition == null ? "non renseignee" : disposition)
            + " | Capacite: " + (salle == null ? 0 : salle.getCapacite()) + " places"
            + " | Accessibilite: " + (salle != null && salle.isAccesHandicape() ? "oui" : "non")
            + "\nMateriel fixe retenu: " + firstNonBlank(fixedEquipmentSummary, proposal.fixedEquipmentSummaryOrDefault(), "aucun");
    }

    private String buildAiDialogPreviewLegend(AiRoomDesignProposal proposal, String fixedEquipmentSummary) {
        return proposal.adminSummaryOrDefault()
            + "\nMateriel fixe valide: " + firstNonBlank(fixedEquipmentSummary, proposal.fixedEquipmentSummaryOrDefault(), "aucun")
            + "\nAucune sauvegarde automatique tant que vous n'avez pas confirme la creation.";
    }

    private String applyAiFixedEquipementsIfNeeded(int salleId, Map<Integer, Integer> selectedEquipements) {
        if (selectedEquipements == null || selectedEquipements.isEmpty() || salleId <= 0) {
            return null;
        }

        try {
            salleEquipementService.replaceEquipementsForSalle(salleId, selectedEquipements);
            return "Le materiel fixe valide dans la proposition a aussi ete applique. Vous pourrez le modifier ensuite.";
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            return "La salle est enregistree, mais le materiel fixe valide n'a pas pu etre applique : " + resolveMessage(exception);
        }
    }

    private void showDialogFeedback(Label feedbackLabel, String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void hideDialogFeedback(Label feedbackLabel) {
        feedbackLabel.setText("");
        feedbackLabel.getStyleClass().setAll("backoffice-feedback");
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
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

    private String resolveThrowableMessage(Throwable throwable) {
        if (throwable == null) {
            return "Une erreur technique est survenue.";
        }
        if (throwable instanceof Exception exception) {
            return resolveMessage(exception);
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "Une erreur technique est survenue." : message;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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

    private record SalleFormControls(
        TextField nomField,
        Spinner<Integer> capaciteSpinner,
        TextField localisationField,
        ComboBox<String> batimentComboBox,
        ComboBox<String> typeComboBox,
        ComboBox<String> etatComboBox,
        ComboBox<Integer> etageComboBox,
        ComboBox<String> dispositionComboBox,
        CheckBox accesHandicapeCheckBox,
        TextField statutDetailleField,
        TextArea descriptionArea
    ) {
    }

    private static final class AiDesignDialogState {
        private AiRoomDesignProposal proposal;
        private int nextVariantIndex = 1;
        private Task<AiRoomDesignProposal> generationTask;
        private boolean autoDescriptionEnabled;
        private boolean descriptionSyncMuted;
        private String lastAutoDescription;
        private Map<Integer, Integer> selectedFixedEquipements = Map.of();
        private String selectedFixedEquipmentSummary = "aucun";

        public AiRoomDesignProposal proposal() {
            return proposal;
        }

        public void setProposal(AiRoomDesignProposal proposal) {
            this.proposal = proposal;
        }

        public int nextVariantIndex() {
            return nextVariantIndex;
        }

        public void setNextVariantIndex(int nextVariantIndex) {
            this.nextVariantIndex = Math.max(1, nextVariantIndex);
        }

        public Task<AiRoomDesignProposal> generationTask() {
            return generationTask;
        }

        public void setGenerationTask(Task<AiRoomDesignProposal> generationTask) {
            this.generationTask = generationTask;
        }

        public boolean isGenerating() {
            return generationTask != null && !generationTask.isDone();
        }

        public boolean autoDescriptionEnabled() {
            return autoDescriptionEnabled;
        }

        public void setAutoDescriptionEnabled(boolean autoDescriptionEnabled) {
            this.autoDescriptionEnabled = autoDescriptionEnabled;
        }

        public boolean descriptionSyncMuted() {
            return descriptionSyncMuted;
        }

        public void setDescriptionSyncMuted(boolean descriptionSyncMuted) {
            this.descriptionSyncMuted = descriptionSyncMuted;
        }

        public String lastAutoDescription() {
            return lastAutoDescription;
        }

        public void setLastAutoDescription(String lastAutoDescription) {
            this.lastAutoDescription = lastAutoDescription;
        }

        public Map<Integer, Integer> selectedFixedEquipements() {
            return selectedFixedEquipements;
        }

        public void setSelectedFixedEquipements(Map<Integer, Integer> selectedFixedEquipements) {
            this.selectedFixedEquipements = selectedFixedEquipements == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(selectedFixedEquipements));
        }

        public String selectedFixedEquipmentSummary() {
            return selectedFixedEquipmentSummary;
        }

        public void setSelectedFixedEquipmentSummary(String selectedFixedEquipmentSummary) {
            this.selectedFixedEquipmentSummary = selectedFixedEquipmentSummary == null || selectedFixedEquipmentSummary.isBlank()
                ? "aucun"
                : selectedFixedEquipmentSummary.trim();
        }
    }

    private record PromptSuggestion(String label, String prompt) {
    }

    private record FixedEquipmentSelectionControls(
        CheckBox checkBox,
        Spinner<Integer> quantitySpinner,
        VBox card,
        boolean selectable
    ) {
    }
}
