package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.ReservationService;
import tn.esprit.fahamni.services.ReservationService.ReservationStats;
import tn.esprit.fahamni.services.SeanceService;
import tn.esprit.fahamni.services.TemporaryUserContext;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReservationController {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SECTION_AVAILABLE_SESSIONS = "Seances disponibles";
    private static final String SECTION_ADD_SESSION = "Ajouter une seance";
    private static final String MODE_ONLINE_LABEL = "En ligne";
    private static final String MODE_ONSITE_LABEL = "Presentiel";
    private static final String STATUS_AVAILABLE = "disponible";
    private static final List<Integer> SESSION_PAGE_SIZE_OPTIONS = List.of(5, 10, 20);
    private static final int DEFAULT_SESSION_PAGE_SIZE = 5;
    private static final List<DateTimeFormatter> INPUT_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );

    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();
    private final ReservationService reservationService = new ReservationService();
    private final AdminSalleService salleService = new AdminSalleService();
    private final AdminEquipementService equipementService = new AdminEquipementService();
    private final List<Salle> availableSalles = new ArrayList<>();
    private final List<Equipement> availableEquipements = new ArrayList<>();
    private final Map<Integer, EquipmentSelectionControls> equipmentSelectionControls = new LinkedHashMap<>();
    private Integer editingSessionId;
    private int currentSessionPage = 1;

    @FXML
    private Label publishedSessionsCountLabel;

    @FXML
    private Label draftSessionsCountLabel;

    @FXML
    private ComboBox<String> tutorComboBox;

    @FXML
    private ComboBox<String> sectionMenuComboBox;

    @FXML
    private VBox sessionSearchPanel;

    @FXML
    private VBox sessionFormPanel;

    @FXML
    private VBox sessionListPanel;

    @FXML
    private TextField sessionSearchField;

    @FXML
    private ComboBox<String> sessionSearchModeComboBox;

    @FXML
    private TextField sessionSubjectField;

    @FXML
    private TextField sessionStartAtField;

    @FXML
    private TextField sessionDurationField;

    @FXML
    private TextField sessionCapacityField;

    @FXML
    private CheckBox sessionOnsiteCheckBox;

    @FXML
    private ComboBox<String> sessionRoomComboBox;

    @FXML
    private HBox onsiteInfrastructureRow;

    @FXML
    private Label sessionModeDescriptionLabel;

    @FXML
    private Label sessionModeStateChipLabel;

    @FXML
    private Label sessionRoomStatusChipLabel;

    @FXML
    private Label sessionRoomPreviewTitleLabel;

    @FXML
    private Label sessionRoomPreviewSubtitleLabel;

    @FXML
    private Label sessionRoomPreviewDescriptionLabel;

    @FXML
    private FlowPane sessionRoomFactsContainer;

    @FXML
    private FlowPane sessionEquipmentChoicesContainer;

    @FXML
    private VBox sessionEquipmentPreviewContainer;

    @FXML
    private Label sessionEquipmentSelectionSummaryLabel;

    @FXML
    private Label infrastructureHintLabel;

    @FXML
    private TextArea sessionDescriptionArea;

    @FXML
    private Label publishFeedbackLabel;

    @FXML
    private Label sessionFormTitleLabel;

    @FXML
    private Label sessionFormModeChipLabel;

    @FXML
    private Label editingSessionLabel;

    @FXML
    private VBox recentSessionsContainer;

    @FXML
    private Label reservationActionFeedbackLabel;

    @FXML
    private HBox sessionPaginationBar;

    @FXML
    private Label sessionPaginationSummaryLabel;

    @FXML
    private Button sessionPreviousPageButton;

    @FXML
    private Button sessionNextPageButton;

    @FXML
    private HBox sessionPageButtonsContainer;

    @FXML
    private ComboBox<Integer> sessionsPerPageComboBox;

    @FXML
    private void initialize() {
        tutorComboBox.getItems().setAll(tutorDirectoryService.getTutorNames());
        tutorComboBox.setEditable(true);
        selectTemporaryTutor();
        configureInfrastructureChoices();

        configureWorkspaceSections();
        sectionMenuComboBox.setValue(SECTION_AVAILABLE_SESSIONS);
        sessionSearchModeComboBox.getItems().setAll(seanceService.getAvailableSearchStatuses());
        sessionSearchModeComboBox.setValue("Toutes les seances");
        sessionsPerPageComboBox.getItems().setAll(SESSION_PAGE_SIZE_OPTIONS);
        sessionsPerPageComboBox.setValue(DEFAULT_SESSION_PAGE_SIZE);

        resetEditMode();
        hideFeedback();
        hideReservationActionFeedback();
        loadSessionDashboard();
        showAvailableSessionsSection();
    }

    @FXML
    private void handleChangeWorkspaceSection() {
        if (SECTION_ADD_SESSION.equals(sectionMenuComboBox.getValue())) {
            showAddSessionSection();
        } else {
            showAvailableSessionsSection();
        }
    }

    @FXML
    private void handleSearchSessions() {
        currentSessionPage = 1;
        applySessionFilters();
    }

    @FXML
    private void handleClearSessionSearch() {
        sessionSearchField.clear();
        sessionSearchModeComboBox.setValue("Toutes les seances");
        currentSessionPage = 1;
        applySessionFilters();
    }

    @FXML
    private void handlePreviousSessionsPage() {
        if (currentSessionPage > 1) {
            currentSessionPage--;
            applySessionFilters();
        }
    }

    @FXML
    private void handleNextSessionsPage() {
        currentSessionPage++;
        applySessionFilters();
    }

    @FXML
    private void handleChangeSessionsPageSize() {
        currentSessionPage = 1;
        applySessionFilters();
    }

    @FXML
    private void handleSaveDraftSession() {
        submitSession(0, "La seance a ete enregistree en brouillon.");
    }

    @FXML
    private void handlePublishSession() {
        submitSession(1, "La seance a ete publiee avec succes.");
    }

    @FXML
    private void handleSessionModeChange() {
        updateOnsiteOptionsVisibility();
    }

    @FXML
    private void handleRoomSelectionChange() {
        updateRoomPreview();
    }

    @FXML
    private void handleClearSessionForm() {
        clearSessionForm();
        resetEditMode();
        hideFeedback();
    }

    private void submitSession(int status, String successMessage) {
        hideFeedback();
        boolean editing = editingSessionId != null;

        try {
            Seance seance = buildSeance(status);
            if (editing) {
                seance.setId(editingSessionId);
                seance.setUpdatedAt(LocalDateTime.now());
                seanceService.update(seance);
            } else {
                seance.setCreatedAt(LocalDateTime.now());
                seanceService.add(seance);
            }

            clearSessionForm();
            resetEditMode();
            loadSessionDashboard();
            showAvailableSessionsSection();
            showFeedback(
                editing ? "La seance a ete modifiee avec succes." : successMessage,
                true
            );
        } catch (RuntimeException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    private Seance buildSeance(int status) {
        String subject = requireText(sessionSubjectField.getText(), "Renseignez la matiere de la seance.");
        String tutorName = requireText(tutorComboBox.getValue(), "Choisissez un tuteur.");
        LocalDateTime startAt = parseStartAt(sessionStartAtField.getText());
        int duration = parseBoundedInt(
            sessionDurationField.getText(),
            SeanceService.MIN_DURATION_MINUTES,
            SeanceService.MAX_DURATION_MINUTES,
            "La duree doit etre comprise entre " + SeanceService.MIN_DURATION_MINUTES + " et " + SeanceService.MAX_DURATION_MINUTES + " minutes."
        );
        int capacity = parseBoundedInt(
            sessionCapacityField.getText(),
            SeanceService.MIN_CAPACITY,
            SeanceService.MAX_CAPACITY,
            "La capacite doit etre comprise entre " + SeanceService.MIN_CAPACITY + " et " + SeanceService.MAX_CAPACITY + " participants."
        );
        String description = requireMinimumLengthText(
            sessionDescriptionArea.getText(),
            SeanceService.MIN_DESCRIPTION_LENGTH,
            "Ajoute une description de la seance.",
            "La description doit contenir au moins " + SeanceService.MIN_DESCRIPTION_LENGTH + " caracteres."
        );
        int tutorId = tutorDirectoryService.resolveTutorId(tutorName);

        if (tutorId <= 0) {
            throw new IllegalArgumentException("Le tuteur choisi est invalide pour le moment.");
        }

        String mode = resolveSelectedMode();
        Integer salleId = null;
        Map<Integer, Integer> equipementQuantites = Map.of();
        if (Seance.MODE_ONSITE.equals(mode)) {
            Salle selectedSalle = resolveSelectedSalle();
            if (selectedSalle == null) {
                throw new IllegalArgumentException("Choisissez une salle disponible pour une seance presentielle.");
            }
            if (capacity > selectedSalle.getCapacite()) {
                throw new IllegalArgumentException(
                    "La salle choisie accepte seulement " + selectedSalle.getCapacite() + " participants."
                );
            }
            salleId = selectedSalle.getIdSalle();
            equipementQuantites = getSelectedEquipmentQuantites();
        }

        sessionSubjectField.setText(subject);
        sessionStartAtField.setText(formatDateTime(startAt));
        sessionDurationField.setText(String.valueOf(duration));
        sessionCapacityField.setText(String.valueOf(capacity));
        sessionDescriptionArea.setText(description);
        tutorComboBox.setValue(tutorName);
        sessionOnsiteCheckBox.setSelected(Seance.MODE_ONSITE.equals(mode));
        updateModePresentation();

        Seance seance = new Seance();
        seance.setMatiere(subject);
        seance.setStartAt(startAt);
        seance.setDurationMin(duration);
        seance.setMaxParticipants(capacity);
        seance.setStatus(status);
        seance.setDescription(description);
        seance.setTuteurId(tutorId);
        seance.setMode(mode);
        seance.setSalleId(salleId);
        seance.setEquipementQuantites(equipementQuantites);
        return seance;
    }

    private void configureInfrastructureChoices() {
        sessionOnsiteCheckBox.setSelected(false);
        loadInfrastructureChoices();
        updateOnsiteOptionsVisibility();
    }

    private void loadInfrastructureChoices() {
        try {
            availableSalles.clear();
            availableSalles.addAll(
                salleService.getAll().stream()
                    .filter(salle -> isAvailable(salle.getEtat()))
                    .toList()
            );

            availableEquipements.clear();
            availableEquipements.addAll(
                equipementService.getAll().stream()
                    .filter(equipement -> isAvailable(equipement.getEtat()))
                    .filter(equipement -> equipement.getQuantiteDisponible() > 0)
                    .toList()
            );

            sessionRoomComboBox.getItems().setAll(availableSalles.stream().map(this::formatSalleChoice).toList());
            sessionRoomComboBox.setDisable(availableSalles.isEmpty());
            renderEquipmentChoices();
            updateInfrastructureHint();
            updateRoomPreview();
            updateEquipmentPreview();
        } catch (SQLException | IllegalStateException exception) {
            availableSalles.clear();
            availableEquipements.clear();
            sessionRoomComboBox.getItems().clear();
            sessionRoomComboBox.setDisable(true);
            renderEquipmentChoices();
            infrastructureHintLabel.setText("Chargement des salles et materiels impossible: " + resolveMessage(exception));
            resetRoomPreview();
            updateEquipmentPreview();
        }
    }

    private void renderEquipmentChoices() {
        sessionEquipmentChoicesContainer.getChildren().clear();
        equipmentSelectionControls.clear();

        if (availableEquipements.isEmpty()) {
            Label emptyLabel = new Label("Aucun materiel disponible pour le moment.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            sessionEquipmentChoicesContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Equipement equipement : availableEquipements) {
            sessionEquipmentChoicesContainer.getChildren().add(createEquipmentChoiceCard(equipement));
        }
    }

    private void updateOnsiteOptionsVisibility() {
        boolean onsite = Seance.MODE_ONSITE.equals(resolveSelectedMode());
        setInfrastructureSectionVisible(onsiteInfrastructureRow, onsite);
        updateModePresentation();

        if (!onsite) {
            sessionRoomComboBox.getSelectionModel().clearSelection();
            clearEquipmentSelection();
            resetRoomPreview();
            updateEquipmentPreview();
        } else if (sessionRoomComboBox.getValue() == null && !sessionRoomComboBox.getItems().isEmpty()) {
            sessionRoomComboBox.getSelectionModel().selectFirst();
            updateRoomPreview();
            updateEquipmentPreview();
        } else {
            updateRoomPreview();
            updateEquipmentPreview();
        }
    }

    private String resolveSelectedMode() {
        return sessionOnsiteCheckBox.isSelected() ? Seance.MODE_ONSITE : Seance.MODE_ONLINE;
    }

    private Salle resolveSelectedSalle() {
        int selectedIndex = sessionRoomComboBox.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= availableSalles.size()) {
            return null;
        }
        return availableSalles.get(selectedIndex);
    }

    private Map<Integer, Integer> getSelectedEquipmentQuantites() {
        LinkedHashMap<Integer, Integer> selectedQuantities = new LinkedHashMap<>();
        for (Map.Entry<Integer, EquipmentSelectionControls> entry : equipmentSelectionControls.entrySet()) {
            EquipmentSelectionControls controls = entry.getValue();
            if (!controls.checkBox().isSelected()) {
                continue;
            }
            int quantite = controls.quantitySpinner().getValue() == null ? 1 : controls.quantitySpinner().getValue();
            selectedQuantities.put(entry.getKey(), Math.max(1, quantite));
        }
        return selectedQuantities;
    }

    private void selectSalleById(Integer salleId) {
        sessionRoomComboBox.getSelectionModel().clearSelection();
        if (salleId == null || salleId <= 0) {
            updateRoomPreview();
            return;
        }

        for (int index = 0; index < availableSalles.size(); index++) {
            if (availableSalles.get(index).getIdSalle() == salleId) {
                sessionRoomComboBox.getSelectionModel().select(index);
                updateRoomPreview();
                return;
            }
        }
        updateRoomPreview();
    }

    private void selectEquipementsByQuantites(Map<Integer, Integer> equipementQuantites) {
        clearEquipmentSelection();
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
            EquipmentSelectionControls controls = equipmentSelectionControls.get(entry.getKey());
            if (controls == null) {
                continue;
            }
            controls.checkBox().setSelected(true);
            controls.quantitySpinner().getValueFactory().setValue(Math.max(1, entry.getValue() == null ? 1 : entry.getValue()));
            updateEquipmentSelectionState(controls);
        }
        updateEquipmentPreview();
    }

    private void clearEquipmentSelection() {
        equipmentSelectionControls.values().forEach(controls -> {
            controls.checkBox().setSelected(false);
            controls.quantitySpinner().getValueFactory().setValue(1);
            updateEquipmentSelectionState(controls);
        });
        updateEquipmentPreview();
    }

    private String formatSalleChoice(Salle salle) {
        return salle.getNom()
            + " | "
            + salle.getLocalisation()
            + " | "
            + salle.getCapacite()
            + " places";
    }

    private String formatEquipementChoice(Equipement equipement) {
        return equipement.getNom()
            + " ("
            + equipement.getTypeEquipement()
            + ", "
            + equipement.getQuantiteDisponible()
            + " dispo)";
    }

    private VBox createEquipmentChoiceCard(Equipement equipement) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("reservation-equipment-choice-card");
        card.setPrefWidth(400.0);
        card.setMaxWidth(Double.MAX_VALUE);

        CheckBox checkBox = new CheckBox(formatEquipementChoice(equipement));
        checkBox.setWrapText(true);
        checkBox.setMaxWidth(Double.MAX_VALUE);
        checkBox.getStyleClass().add("reservation-equipment-check");

        Spinner<Integer> quantitySpinner = new Spinner<>();
        quantitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1,
            Math.max(1, equipement.getQuantiteDisponible()),
            1
        ));
        quantitySpinner.setEditable(true);
        quantitySpinner.setPrefWidth(92.0);
        quantitySpinner.getStyleClass().add("reservation-equipment-quantity-spinner");
        quantitySpinner.setDisable(true);

        EquipmentSelectionControls controls = new EquipmentSelectionControls(checkBox, quantitySpinner, card);
        equipmentSelectionControls.put(equipement.getIdEquipement(), controls);

        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            updateEquipmentSelectionState(controls);
            updateEquipmentPreview();
        });
        quantitySpinner.valueProperty().addListener((obs, oldValue, newValue) -> updateEquipmentPreview());

        Label quantityLabel = new Label("Quantite");
        quantityLabel.getStyleClass().add("reservation-equipment-quantity-label");

        VBox quantityBox = new VBox(4.0, quantityLabel, quantitySpinner);
        quantityBox.setMinWidth(112.0);
        quantityBox.setPrefWidth(112.0);
        quantityBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        HBox header = new HBox(12.0, checkBox, quantityBox);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(checkBox, Priority.ALWAYS);

        Label description = new Label(formatDescription(equipement.getDescription()));
        description.setWrapText(true);
        description.getStyleClass().add("reservation-section-copy");

        card.getChildren().addAll(header, description);
        updateEquipmentSelectionState(controls);
        return card;
    }

    private void updateEquipmentSelectionState(EquipmentSelectionControls controls) {
        boolean selected = controls.checkBox().isSelected();
        controls.quantitySpinner().setDisable(!selected);
        controls.card().getStyleClass().remove("selected");
        if (selected) {
            controls.card().getStyleClass().add("selected");
        }
    }

    private void updateModePresentation() {
        boolean onsite = Seance.MODE_ONSITE.equals(resolveSelectedMode());
        sessionModeStateChipLabel.setText(onsite ? MODE_ONSITE_LABEL : MODE_ONLINE_LABEL);
        sessionModeStateChipLabel.getStyleClass().setAll(
            "workspace-chip",
            onsite ? "reservation-mode-chip-onsite" : "workspace-chip-muted"
        );
        sessionModeDescriptionLabel.setText(
            onsite
                ? "Presentiel. Choisissez une salle adaptee puis le materiel disponible avec previsualisation detaillee."
                : "En ligne. Aucun espace physique n'est requis pour cette seance."
        );
    }

    private void updateRoomPreview() {
        if (!Seance.MODE_ONSITE.equals(resolveSelectedMode())) {
            resetRoomPreview();
            return;
        }

        Salle selectedSalle = resolveSelectedSalle();
        if (selectedSalle == null) {
            resetRoomPreview();
            return;
        }

        sessionRoomStatusChipLabel.setText(formatLabel(selectedSalle.getEtat()));
        sessionRoomStatusChipLabel.getStyleClass().setAll("status-chip", resolveStatusStyle(selectedSalle.getEtat()));
        sessionRoomPreviewTitleLabel.setText(formatOptionalText(selectedSalle.getNom()));
        sessionRoomPreviewSubtitleLabel.setText(
            formatOptionalText(selectedSalle.getTypeSalle())
                + " | "
                + selectedSalle.getCapacite()
                + " places | "
                + formatOptionalText(selectedSalle.getLocalisation())
        );
        sessionRoomPreviewDescriptionLabel.setText(formatDescription(selectedSalle.getDescription()));

        sessionRoomFactsContainer.getChildren().setAll(
            buildInfrastructureFactCard("Batiment", formatOptionalText(selectedSalle.getBatiment())),
            buildInfrastructureFactCard("Etage", selectedSalle.getEtage() == null ? "Non renseigne" : String.valueOf(selectedSalle.getEtage())),
            buildInfrastructureFactCard("Disposition", formatOptionalText(selectedSalle.getTypeDisposition())),
            buildInfrastructureFactCard("Acces", selectedSalle.isAccesHandicape() ? "Handicap" : "Standard")
        );
    }

    private void resetRoomPreview() {
        sessionRoomStatusChipLabel.setText("En attente");
        sessionRoomStatusChipLabel.getStyleClass().setAll("status-chip", "unavailable");
        sessionRoomPreviewTitleLabel.setText("Choisissez une salle");
        sessionRoomPreviewSubtitleLabel.setText("La salle choisie apparaitra ici avec ses details principaux.");
        sessionRoomPreviewDescriptionLabel.setText("Aucune salle selectionnee.");
        sessionRoomFactsContainer.getChildren().clear();
    }

    private void updateEquipmentPreview() {
        sessionEquipmentPreviewContainer.getChildren().clear();

        if (!Seance.MODE_ONSITE.equals(resolveSelectedMode())) {
            sessionEquipmentSelectionSummaryLabel.setText("0 selection");
            sessionEquipmentPreviewContainer.getChildren().add(createEmptyEquipmentPreview(
                "Le materiel n'est necessaire que pour une seance presentielle."
            ));
            return;
        }

        Map<Integer, Integer> selectedEquipementQuantites = getSelectedEquipmentQuantites();
        int selectedCount = selectedEquipementQuantites.size();
        int totalUnits = selectedEquipementQuantites.values().stream().mapToInt(Integer::intValue).sum();
        sessionEquipmentSelectionSummaryLabel.setText(
            selectedCount == 0
                ? "0 selection"
                : selectedCount + " choix | " + totalUnits + " unite(s)"
        );

        if (selectedEquipementQuantites.isEmpty()) {
            sessionEquipmentPreviewContainer.getChildren().add(createEmptyEquipmentPreview(
                "Aucun materiel selectionne. Vous pouvez garder la seance presentielle sans materiel particulier."
            ));
            return;
        }

        selectedEquipementQuantites.entrySet().stream()
            .map(entry -> createEquipmentPreviewCard(findEquipementById(entry.getKey()), entry.getValue()))
            .filter(card -> card != null)
            .forEach(sessionEquipmentPreviewContainer.getChildren()::add);
    }

    private VBox createEmptyEquipmentPreview(String text) {
        VBox card = new VBox(6.0);
        card.getStyleClass().addAll("infrastructure-detail-preview", "reservation-equipment-preview-card");

        Label title = new Label("Apercu materiel");
        title.getStyleClass().add("infrastructure-detail-section-title");

        Label copy = new Label(text);
        copy.setWrapText(true);
        copy.getStyleClass().add("infrastructure-detail-section-copy");

        card.getChildren().addAll(title, copy);
        return card;
    }

    private VBox createEquipmentPreviewCard(Equipement equipement, int quantiteChoisie) {
        if (equipement == null) {
            return null;
        }

        VBox card = new VBox(8.0);
        card.getStyleClass().addAll("infrastructure-detail-preview", "reservation-equipment-preview-card");

        HBox header = new HBox(8.0);
        Label title = new Label(formatOptionalText(equipement.getNom()));
        title.getStyleClass().add("infrastructure-detail-section-title");
        title.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(formatLabel(equipement.getEtat()));
        status.getStyleClass().setAll("status-chip", resolveStatusStyle(equipement.getEtat()));
        header.getChildren().addAll(title, spacer, status);

        Label subtitle = new Label(
            formatLabel(equipement.getTypeEquipement())
                + " | "
                + quantiteChoisie
                + " unite(s) choisie(s) sur "
                + equipement.getQuantiteDisponible()
                + " disponible(s)"
        );
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("infrastructure-detail-section-copy");

        FlowPane facts = new FlowPane(10.0, 10.0);
        facts.getChildren().addAll(
            buildInfrastructureFactCard("Type", formatLabel(equipement.getTypeEquipement())),
            buildInfrastructureFactCard("Quantite", quantiteChoisie + " unite(s)"),
            buildInfrastructureFactCard("Stock", equipement.getQuantiteDisponible() + " unite(s)")
        );

        Label description = new Label(formatDescription(equipement.getDescription()));
        description.setWrapText(true);
        description.getStyleClass().add("infrastructure-detail-section-copy");

        card.getChildren().addAll(header, subtitle, facts, description);
        return card;
    }

    private VBox buildInfrastructureFactCard(String label, String value) {
        VBox card = new VBox(5.0);
        card.getStyleClass().add("infrastructure-detail-fact-card");
        card.setPrefWidth(150.0);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("infrastructure-detail-fact-label");

        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("infrastructure-detail-fact-value");

        card.getChildren().addAll(labelNode, valueNode);
        return card;
    }

    private Equipement findEquipementById(Integer equipementId) {
        if (equipementId == null || equipementId <= 0) {
            return null;
        }

        return availableEquipements.stream()
            .filter(equipement -> equipement.getIdEquipement() == equipementId)
            .findFirst()
            .orElse(null);
    }

    private void updateInfrastructureHint() {
        infrastructureHintLabel.setText(
            availableSalles.size()
                + " salle(s) disponible(s), "
                + availableEquipements.size()
                + " type(s) de materiel disponible(s). Precisez la quantite voulue uniquement pour le materiel existant."
        );
    }

    private void loadSessionDashboard() {
        List<Seance> allSessions = seanceService.getAll();
        long publishedCount = allSessions.stream().filter(seance -> seance.getStatus() == 1).count();
        long draftCount = allSessions.stream().filter(seance -> seance.getStatus() == 0).count();

        publishedSessionsCountLabel.setText(String.valueOf(publishedCount));
        draftSessionsCountLabel.setText(String.valueOf(draftCount));
        applySessionFilters();
    }

    private void applySessionFilters() {
        recentSessionsContainer.getChildren().clear();
        List<Seance> filteredSessions = seanceService.search(
            sessionSearchField.getText(),
            sessionSearchModeComboBox.getValue(),
            0
        );
        boolean hasSessionsInDatabase = !seanceService.getAll().isEmpty();

        if (filteredSessions.isEmpty() && !hasSessionsInDatabase) {
            Label emptyLabel = new Label("Aucune seance en base de donnees n'est disponible pour le moment.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            hideSessionPagination();
            return;
        }

        if (filteredSessions.isEmpty()) {
            Label emptyLabel = new Label("Aucune seance en base ne correspond aux filtres actuels.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("reservation-section-copy");
            recentSessionsContainer.getChildren().add(emptyLabel);
            hideSessionPagination();
            return;
        }

        int totalItems = filteredSessions.size();
        int pageSize = getSelectedSessionPageSize();
        int totalPages = calculateTotalPages(totalItems, pageSize);
        currentSessionPage = clamp(currentSessionPage, 1, totalPages);

        int fromIndex = (currentSessionPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        filteredSessions.subList(fromIndex, toIndex).stream()
            .map(this::buildRecentSessionCard)
            .forEach(recentSessionsContainer.getChildren()::add);

        updateSessionPagination(totalItems, fromIndex + 1, toIndex, totalPages);
    }

    private VBox buildRecentSessionCard(Seance seance) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("reservation-form-shell");
        ReservationStats reservationStats = reservationService.getStatsBySeanceId(seance.getId());

        HBox headerRow = new HBox(10.0);
        Label titleLabel = new Label(seance.getMatiere());
        titleLabel.getStyleClass().add("subsection-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusChip = new Label(mapStatusLabel(seance.getStatus()));
        statusChip.getStyleClass().addAll("reservation-status", mapStatusStyle(seance.getStatus()));
        headerRow.getChildren().addAll(titleLabel, spacer, statusChip);

        Label metaLabel = new Label(
            "Tuteur: " + tutorDirectoryService.getTutorDisplayName(seance.getTuteurId())
                + " | " + formatDateTime(seance.getStartAt())
                + " | " + seance.getDurationMin() + " min"
                + " | " + seance.getMaxParticipants() + " places"
                + " | " + buildInfrastructureSummary(seance)
                + " | " + formatReservationCount(reservationStats.total())
        );
        metaLabel.setWrapText(true);
        metaLabel.getStyleClass().add("reservation-section-copy");

        Label descriptionLabel = new Label(
            seance.getDescription() != null && !seance.getDescription().isBlank()
                ? seance.getDescription()
                : "Aucune description ajoutee pour cette seance."
        );
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("reservation-section-copy");

        HBox actionRow = new HBox(10.0);
        Label idChip = new Label("Seance #" + seance.getId());
        idChip.getStyleClass().add("workspace-chip");

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button detailsButton = new Button("Voir detail");
        detailsButton.getStyleClass().add("backoffice-secondary-button");
        detailsButton.setOnAction(event -> showSessionDetails(seance, reservationStats));

        Button reserveButton = buildReserveButton(seance, reservationStats);

        Button editButton = new Button("Modifier");
        editButton.getStyleClass().add("backoffice-secondary-button");
        editButton.setOnAction(event -> startEditingSession(seance));

        Button deleteButton = new Button("Supprimer");
        deleteButton.getStyleClass().add("backoffice-danger-button");
        deleteButton.setOnAction(event -> confirmDeleteSession(seance));

        actionRow.getChildren().addAll(idChip, actionSpacer);
        if (TemporaryUserContext.isCurrentStudent()) {
            actionRow.getChildren().add(reserveButton);
        }
        actionRow.getChildren().add(detailsButton);
        if (canManageSession(seance)) {
            actionRow.getChildren().addAll(editButton, deleteButton);
        }

        card.getChildren().addAll(headerRow, metaLabel, descriptionLabel, actionRow);
        return card;
    }

    private boolean canManageSession(Seance seance) {
        return TemporaryUserContext.isCurrentTutor()
            && seance != null
            && seance.getTuteurId() == TemporaryUserContext.getCurrentTutorId();
    }

    private Button buildReserveButton(Seance seance, ReservationStats reservationStats) {
        Button reserveButton = new Button("Reserver");
        reserveButton.getStyleClass().add("backoffice-primary-button");

        if (!TemporaryUserContext.isCurrentStudent()) {
            reserveButton.setText("Compte etudiant requis");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (seance.getStatus() != 1) {
            reserveButton.setText("Indisponible");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (reservationStats.total() >= seance.getMaxParticipants()) {
            reserveButton.setText("Complet");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        if (reservationService.hasActiveReservation(seance.getId(), TemporaryUserContext.getCurrentStudentId())) {
            reserveButton.setText("Deja reserve");
            reserveButton.setDisable(true);
            return reserveButton;
        }

        reserveButton.setOnAction(event -> reserveSession(seance));
        return reserveButton;
    }

    private void reserveSession(Seance seance) {
        OperationResult result = reservationService.reserveSeance(seance, TemporaryUserContext.getCurrentStudentId());
        loadSessionDashboard();
        showAvailableSessionsSection();
        showReservationActionFeedback(result.getMessage(), result.isSuccess());
    }

    private void showSessionDetails(Seance seance, ReservationStats reservationStats) {
        Dialog<ButtonType> detailsDialog = new Dialog<>();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane dialogPane = detailsDialog.getDialogPane();

        detailsDialog.setTitle("Detail de la seance");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setContent(buildSessionDetailsContent(seance, reservationStats, detailsDialog));
        dialogPane.setPrefWidth(720);
        dialogPane.getStyleClass().add("session-detail-dialog");
        applyCurrentTheme(dialogPane);
        detailsDialog.showAndWait();
    }

    private VBox buildSessionDetailsContent(Seance seance, ReservationStats reservationStats, Dialog<ButtonType> detailsDialog) {
        LocalDateTime endAt = seance.getStartAt() != null
            ? seance.getStartAt().plusMinutes(seance.getDurationMin())
            : null;
        int availableSeats = Math.max(0, seance.getMaxParticipants() - reservationStats.total());
        double occupancyRate = calculateOccupancyRate(reservationStats.total(), seance.getMaxParticipants());

        VBox root = new VBox(16.0);
        root.getStyleClass().add("session-detail-root");

        HBox header = new HBox(12.0);
        header.getStyleClass().add("session-detail-header");

        VBox titleBlock = new VBox(5.0);
        HBox titleRow = new HBox(8.0);
        Label titleLabel = new Label(safeText(seance.getMatiere()));
        titleLabel.getStyleClass().add("session-detail-title");
        Label statusChip = new Label(mapStatusLabel(seance.getStatus()));
        statusChip.getStyleClass().addAll("reservation-status", mapStatusStyle(seance.getStatus()));
        titleRow.getChildren().addAll(titleLabel, statusChip);

        titleBlock.getChildren().add(titleRow);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Label reservationChip = new Label(formatReservationCount(reservationStats.total()));
        reservationChip.getStyleClass().add("session-detail-reservation-chip");
        header.getChildren().addAll(titleBlock, headerSpacer, reservationChip);

        FlowPane metrics = new FlowPane(10.0, 10.0);
        metrics.getStyleClass().add("session-detail-metrics");
        metrics.setPrefWrapLength(560.0);
        metrics.getChildren().addAll(
            buildDetailMetric("Debut", formatDateTimeOrPlaceholder(seance.getStartAt()), "Date et heure"),
            buildDetailMetric("Fin", formatDateTimeOrPlaceholder(endAt), "Fin calculee"),
            buildDetailMetric("Duree", seance.getDurationMin() + " min", "Temps de seance"),
            buildDetailMetric("Places", availableSeats + " / " + seance.getMaxParticipants(), "Disponibles"),
            buildDetailMetric("Reservations", String.valueOf(reservationStats.total()), "Total lie a la seance"),
            buildDetailMetric("Tuteur", tutorDirectoryService.getTutorDisplayName(seance.getTuteurId()), "ID " + seance.getTuteurId()),
            buildDetailMetric("Mode", mapModeLabel(seance.getMode()), "Format choisi"),
            buildDetailMetric("Salle", resolveSalleName(seance.getSalleId()), "Presentiel"),
            buildDetailMetric("Materiel", resolveEquipementNames(seance.getEquipementQuantites()), "Existant")
        );

        VBox occupancyCard = new VBox(9.0);
        occupancyCard.getStyleClass().add("session-detail-occupancy-card");
        HBox occupancyHeader = new HBox(10.0);
        Label occupancyTitle = new Label("Remplissage de la seance");
        occupancyTitle.getStyleClass().add("session-detail-section-title");
        Region occupancySpacer = new Region();
        HBox.setHgrow(occupancySpacer, Priority.ALWAYS);
        Label occupancyValue = new Label(formatPercent(occupancyRate));
        occupancyValue.getStyleClass().add("session-detail-occupancy-value");
        occupancyHeader.getChildren().addAll(occupancyTitle, occupancySpacer, occupancyValue);

        ProgressBar occupancyProgress = new ProgressBar(occupancyRate);
        occupancyProgress.setMaxWidth(Double.MAX_VALUE);
        occupancyProgress.getStyleClass().add("session-detail-progress");

        HBox statusRow = new HBox(8.0);
        statusRow.getStyleClass().add("session-detail-status-row");
        statusRow.getChildren().addAll(
            buildReservationStatusChip("En attente", reservationStats.pending()),
            buildReservationStatusChip("Acceptees", reservationStats.accepted()),
            buildReservationStatusChip("Payees", reservationStats.paid())
        );
        occupancyCard.getChildren().addAll(occupancyHeader, occupancyProgress, statusRow);

        VBox descriptionBox = new VBox(8.0);
        descriptionBox.getStyleClass().add("session-detail-description-card");
        Label descriptionTitle = new Label("Description");
        descriptionTitle.getStyleClass().add("session-detail-section-title");
        Label descriptionText = new Label(safeText(seance.getDescription()));
        descriptionText.setWrapText(true);
        descriptionText.getStyleClass().add("session-detail-description-text");
        descriptionBox.getChildren().addAll(descriptionTitle, descriptionText);

        HBox footer = new HBox(10.0);
        footer.getStyleClass().add("session-detail-footer");
        footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label createdAtLabel = new Label("Creation: " + formatDateTimeOrPlaceholder(seance.getCreatedAt()));
        createdAtLabel.getStyleClass().add("session-detail-muted");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        Button editActionButton = new Button("Modifier cette seance");
        editActionButton.getStyleClass().add("backoffice-primary-button");
        editActionButton.setOnAction(event -> {
            detailsDialog.close();
            startEditingSession(seance);
        });
        footer.getChildren().add(createdAtLabel);
        if (seance.getUpdatedAt() != null) {
            Label updatedAtLabel = new Label("Mise a jour: " + formatDateTimeOrPlaceholder(seance.getUpdatedAt()));
            updatedAtLabel.getStyleClass().add("session-detail-muted");
            footer.getChildren().add(updatedAtLabel);
        }
        footer.getChildren().addAll(footerSpacer, editActionButton);

        root.getChildren().addAll(header, metrics, occupancyCard, descriptionBox, footer);
        return root;
    }

    private VBox buildDetailMetric(String label, String value, String hint) {
        VBox metric = new VBox(4.0);
        metric.getStyleClass().add("session-detail-metric-card");
        metric.setPrefWidth(172.0);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("session-detail-metric-label");
        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("session-detail-metric-value");
        Label hintNode = new Label(hint);
        hintNode.setWrapText(true);
        hintNode.getStyleClass().add("session-detail-metric-hint");

        metric.getChildren().addAll(labelNode, valueNode, hintNode);
        return metric;
    }

    private Label buildReservationStatusChip(String label, int count) {
        Label chip = new Label(label + ": " + count);
        chip.getStyleClass().add("session-detail-status-chip");
        return chip;
    }

    private int getSelectedSessionPageSize() {
        Integer selectedValue = sessionsPerPageComboBox != null ? sessionsPerPageComboBox.getValue() : null;
        return selectedValue != null && selectedValue > 0 ? selectedValue : DEFAULT_SESSION_PAGE_SIZE;
    }

    private int calculateTotalPages(int totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void updateSessionPagination(int totalItems, int fromItem, int toItem, int totalPages) {
        sessionPaginationBar.setManaged(true);
        sessionPaginationBar.setVisible(true);
        sessionPaginationSummaryLabel.setText(fromItem + "-" + toItem + " sur " + totalItems + " seances affichees");
        sessionPreviousPageButton.setDisable(currentSessionPage <= 1);
        sessionNextPageButton.setDisable(currentSessionPage >= totalPages);

        sessionPageButtonsContainer.getChildren().clear();
        for (int page : buildVisiblePageNumbers(totalPages)) {
            Button pageButton = new Button(String.valueOf(page));
            pageButton.getStyleClass().add("backoffice-page-button");
            if (page == currentSessionPage) {
                pageButton.getStyleClass().add("active-page");
            } else {
                pageButton.setOnAction(event -> {
                    currentSessionPage = page;
                    applySessionFilters();
                });
            }
            sessionPageButtonsContainer.getChildren().add(pageButton);
        }
    }

    private List<Integer> buildVisiblePageNumbers(int totalPages) {
        int firstPage = Math.max(1, currentSessionPage - 2);
        int lastPage = Math.min(totalPages, firstPage + 4);
        firstPage = Math.max(1, lastPage - 4);

        java.util.ArrayList<Integer> pages = new java.util.ArrayList<>();
        for (int page = firstPage; page <= lastPage; page++) {
            pages.add(page);
        }
        return pages;
    }

    private void hideSessionPagination() {
        sessionPaginationBar.setManaged(false);
        sessionPaginationBar.setVisible(false);
        sessionPaginationSummaryLabel.setText("");
        sessionPageButtonsContainer.getChildren().clear();
    }

    private void startEditingSession(Seance seance) {
        editingSessionId = seance.getId();
        sessionSubjectField.setText(seance.getMatiere());
        sessionStartAtField.setText(formatDateTime(seance.getStartAt()));
        sessionDurationField.setText(String.valueOf(seance.getDurationMin()));
        sessionCapacityField.setText(String.valueOf(seance.getMaxParticipants()));
        sessionDescriptionArea.setText(seance.getDescription() != null ? seance.getDescription() : "");

        String tutorValue = resolveTutorValueForEdit(seance.getTuteurId());
        tutorComboBox.setValue(tutorValue);
        sessionOnsiteCheckBox.setSelected(Seance.MODE_ONSITE.equals(seance.getMode()));
        updateOnsiteOptionsVisibility();
        selectSalleById(seance.getSalleId());
        selectEquipementsByQuantites(seance.getEquipementQuantites());

        sessionFormTitleLabel.setText("Modifier une seance");
        sessionFormModeChipLabel.setText("Modification");
        editingSessionLabel.setText(
            "Mode modification actif pour la seance #" + seance.getId()
                + ". Mets a jour les champs puis clique sur brouillon ou publier."
        );
        editingSessionLabel.setManaged(true);
        editingSessionLabel.setVisible(true);
        hideFeedback();
        showAddSessionSection();
    }

    private void confirmDeleteSession(Seance seance) {
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Suppression de seance");
        confirmationAlert.setHeaderText("Confirmer la suppression");
        confirmationAlert.setContentText(
            "La seance \"" + seance.getMatiere() + "\" sera supprimee definitivement si aucune reservation ne la reference."
        );
        confirmationAlert.getButtonTypes().setAll(cancelButton, deleteButton);

        Optional<ButtonType> choice = confirmationAlert.showAndWait();
        if (choice.isPresent() && choice.get() == deleteButton) {
            deleteSession(seance);
        }
    }

    private void deleteSession(Seance seance) {
        hideFeedback();

        try {
            seanceService.delete(seance);
            if (editingSessionId != null && editingSessionId == seance.getId()) {
                clearSessionForm();
                resetEditMode();
            }
            loadSessionDashboard();
            showFeedback("La seance a ete supprimee avec succes.", true);
        } catch (RuntimeException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    private LocalDateTime parseStartAt(String value) {
        String candidate = requireText(value, "Renseignez la date et l'heure de la seance.");
        for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
            try {
                LocalDateTime parsedValue = LocalDateTime.parse(candidate, formatter);
                if (!parsedValue.isAfter(LocalDateTime.now())) {
                    throw new IllegalArgumentException("La date de la seance doit etre dans le futur.");
                }
                return parsedValue;
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format.
            }
        }
        throw new IllegalArgumentException("Utilisez le format dd/MM/yyyy HH:mm pour la date.");
    }

    private int parseBoundedInt(String value, int min, int max, String errorMessage) {
        String candidate = requireText(value, errorMessage);
        try {
            int parsedValue = Integer.parseInt(candidate);
            if (parsedValue < min || parsedValue > max) {
                throw new NumberFormatException();
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String requireText(String value, String errorMessage) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalizedValue;
    }

    private String requireMinimumLengthText(String value, int minLength, String emptyMessage, String shortMessage) {
        String normalizedValue = requireText(value, emptyMessage);
        if (normalizedValue.length() < minLength) {
            throw new IllegalArgumentException(shortMessage);
        }
        return normalizedValue;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private String formatOptionalText(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Non renseigne";
    }

    private String formatDescription(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Aucune description renseignee.";
    }

    private String formatLabel(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return "Non defini";
        }

        String lowered = normalizedValue.toLowerCase();
        return Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1);
    }

    private boolean isAvailable(String status) {
        String normalizedStatus = status == null ? "" : status.toLowerCase().trim();
        return STATUS_AVAILABLE.equals(normalizedStatus);
    }

    private String resolveStatusStyle(String status) {
        String normalizedStatus = status == null ? "" : status.toLowerCase().trim();
        if (STATUS_AVAILABLE.equals(normalizedStatus)) {
            return "available";
        }
        if (normalizedStatus.contains("maintenance")) {
            return "maintenance";
        }
        return "unavailable";
    }

    private String resolveMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Une erreur technique est survenue." : message;
    }

    private String formatReservationCount(int reservationCount) {
        if (reservationCount <= 0) {
            return "0 reservation";
        }
        return reservationCount == 1 ? "1 reservation" : reservationCount + " reservations";
    }

    private double calculateOccupancyRate(int reservationCount, int capacity) {
        if (reservationCount <= 0 || capacity <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) reservationCount / capacity);
    }

    private String formatPercent(double value) {
        return Math.round(value * 100) + "%";
    }

    private String safeText(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Non renseigne";
    }

    private String formatDateTimeOrPlaceholder(LocalDateTime value) {
        return value != null ? value.format(DISPLAY_FORMATTER) : "Non renseigne";
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DISPLAY_FORMATTER) : "";
    }

    private String mapStatusLabel(int status) {
        return switch (status) {
            case 1 -> "Publiee";
            case 2 -> "Archivee";
            default -> "Brouillon";
        };
    }

    private String mapStatusStyle(int status) {
        return switch (status) {
            case 1 -> "confirmed";
            case 2 -> "completed";
            default -> "pending";
        };
    }

    private String mapModeLabel(String mode) {
        return Seance.MODE_ONSITE.equals(mode) ? MODE_ONSITE_LABEL : MODE_ONLINE_LABEL;
    }

    private String buildInfrastructureSummary(Seance seance) {
        if (seance == null || !seance.isPresentiel()) {
            return MODE_ONLINE_LABEL;
        }

        String equipmentSummary = seance.getEquipementQuantites().isEmpty()
            ? "sans materiel"
            : resolveEquipementNames(seance.getEquipementQuantites());
        return MODE_ONSITE_LABEL + " - " + resolveSalleName(seance.getSalleId()) + " - " + equipmentSummary;
    }

    private String resolveSalleName(Integer salleId) {
        if (salleId == null || salleId <= 0) {
            return "Non requis";
        }

        return availableSalles.stream()
            .filter(salle -> salle.getIdSalle() == salleId)
            .map(Salle::getNom)
            .findFirst()
            .orElse("Salle #" + salleId);
    }

    private String resolveEquipementNames(Map<Integer, Integer> equipementQuantites) {
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            return "Aucun materiel";
        }

        List<String> names = equipementQuantites.entrySet().stream()
            .map(entry -> resolveEquipementName(entry.getKey()) + " x" + Math.max(1, entry.getValue()))
            .toList();
        return String.join(", ", names);
    }

    private String resolveEquipementName(Integer equipementId) {
        if (equipementId == null || equipementId <= 0) {
            return "Materiel inconnu";
        }

        return availableEquipements.stream()
            .filter(equipement -> equipement.getIdEquipement() == equipementId)
            .map(Equipement::getNom)
            .findFirst()
            .orElse("Materiel #" + equipementId);
    }

    private void clearSessionForm() {
        sessionSubjectField.clear();
        sessionStartAtField.clear();
        sessionDurationField.clear();
        sessionCapacityField.clear();
        sessionDescriptionArea.clear();
        sessionOnsiteCheckBox.setSelected(false);
        sessionRoomComboBox.getSelectionModel().clearSelection();
        clearEquipmentSelection();
        updateOnsiteOptionsVisibility();
        selectTemporaryTutor();
    }

    private void resetEditMode() {
        editingSessionId = null;
        sessionFormTitleLabel.setText("Publier une seance");
        sessionFormModeChipLabel.setText("Ajout direct");
        editingSessionLabel.setText("");
        editingSessionLabel.setManaged(false);
        editingSessionLabel.setVisible(false);
    }

    private void showAvailableSessionsSection() {
        if (sectionMenuComboBox != null && !SECTION_AVAILABLE_SESSIONS.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_AVAILABLE_SESSIONS);
        }
        setSectionVisible(sessionSearchPanel, true);
        setSectionVisible(sessionListPanel, true);
        setSectionVisible(sessionFormPanel, false);
    }

    private void showAddSessionSection() {
        if (!TemporaryUserContext.isCurrentTutor()) {
            showAvailableSessionsSection();
            showReservationActionFeedback("Connectez-vous avec le compte tuteur pour ajouter une seance.", false);
            return;
        }

        if (sectionMenuComboBox != null && !SECTION_ADD_SESSION.equals(sectionMenuComboBox.getValue())) {
            sectionMenuComboBox.setValue(SECTION_ADD_SESSION);
        }
        setSectionVisible(sessionSearchPanel, false);
        setSectionVisible(sessionListPanel, false);
        setSectionVisible(sessionFormPanel, true);
    }

    private void configureWorkspaceSections() {
        if (TemporaryUserContext.isCurrentTutor()) {
            sectionMenuComboBox.getItems().setAll(SECTION_AVAILABLE_SESSIONS, SECTION_ADD_SESSION);
            return;
        }

        sectionMenuComboBox.getItems().setAll(SECTION_AVAILABLE_SESSIONS);
    }

    private void selectTemporaryTutor() {
        String temporaryTutorName = tutorDirectoryService.getTutorDisplayName(TemporaryUserContext.getCurrentTutorId());
        if (tutorComboBox.getItems().contains(temporaryTutorName)) {
            tutorComboBox.setValue(temporaryTutorName);
        } else if (!tutorComboBox.getItems().isEmpty()) {
            tutorComboBox.setValue(tutorComboBox.getItems().get(0));
        } else {
            tutorComboBox.setValue(null);
        }
    }

    private void setInfrastructureSectionVisible(HBox section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setManaged(visible);
        section.setVisible(visible);
    }

    private void setSectionVisible(VBox section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setManaged(visible);
        section.setVisible(visible);
    }

    private String resolveTutorValueForEdit(int tutorId) {
        if (tutorId <= 0) {
            return "";
        }

        String tutorDisplayName = tutorDirectoryService.getTutorDisplayName(tutorId);
        if (tutorDisplayName.startsWith("Tuteur #")) {
            return String.valueOf(tutorId);
        }
        return tutorDisplayName;
    }

    private void showFeedback(String message, boolean success) {
        publishFeedbackLabel.setText(message);
        publishFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", success ? "success" : "error");
        publishFeedbackLabel.setManaged(true);
        publishFeedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        publishFeedbackLabel.setText("");
        publishFeedbackLabel.getStyleClass().setAll("frontoffice-feedback");
        publishFeedbackLabel.setManaged(false);
        publishFeedbackLabel.setVisible(false);
    }

    private void showReservationActionFeedback(String message, boolean success) {
        reservationActionFeedbackLabel.setText(message);
        reservationActionFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", success ? "success" : "error");
        reservationActionFeedbackLabel.setManaged(true);
        reservationActionFeedbackLabel.setVisible(true);
    }

    private void hideReservationActionFeedback() {
        reservationActionFeedbackLabel.setText("");
        reservationActionFeedbackLabel.getStyleClass().setAll("frontoffice-feedback");
        reservationActionFeedbackLabel.setManaged(false);
        reservationActionFeedbackLabel.setVisible(false);
    }

    private void applyCurrentTheme(DialogPane dialogPane) {
        if (dialogPane == null || recentSessionsContainer == null || recentSessionsContainer.getScene() == null) {
            return;
        }
        dialogPane.getStylesheets().setAll(recentSessionsContainer.getScene().getStylesheets());
    }

    private record EquipmentSelectionControls(CheckBox checkBox, Spinner<Integer> quantitySpinner, VBox card) {
    }
}

