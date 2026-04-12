package tn.esprit.fahamni.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Place;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminPlaceService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.SessionCreationContext;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class SallesEquipementsController {

    private static final String FILTER_ALL_TYPES = "Tous les types";
    private static final String FILTER_ALL_STATUSES = "Tous les etats";
    private static final String STATUS_AVAILABLE = "disponible";
    private static final int CATALOG_COLUMNS = 3;
    private static final double CATALOG_CARD_GAP = 14.0;

    @FXML
    private TextField roomSearchField;

    @FXML
    private ComboBox<String> roomTypeFilterComboBox;

    @FXML
    private ComboBox<String> roomStatusFilterComboBox;

    @FXML
    private CheckBox accessibilityFilterCheckBox;

    @FXML
    private Label roomCountLabel;

    @FXML
    private GridPane roomCardsContainer;

    @FXML
    private TextField equipmentSearchField;

    @FXML
    private ComboBox<String> equipmentTypeFilterComboBox;

    @FXML
    private ComboBox<String> equipmentStatusFilterComboBox;

    @FXML
    private Label equipmentCountLabel;

    @FXML
    private GridPane equipmentCardsContainer;

    @FXML
    private Label totalRoomsStatLabel;

    @FXML
    private Label availableRoomsStatLabel;

    @FXML
    private Label totalEquipmentsStatLabel;

    @FXML
    private Label availableEquipmentsStatLabel;

    @FXML
    private Label detailTypeLabel;

    @FXML
    private Label detailStatusLabel;

    @FXML
    private Label detailTitleLabel;

    @FXML
    private Label detailSubtitleLabel;

    @FXML
    private FlowPane detailFactsContainer;

    @FXML
    private Label detailDescriptionLabel;

    @FXML
    private VBox sessionPreviewContainer;

    @FXML
    private Label sessionPreviewLabel;

    @FXML
    private Button detailUseButton;

    @FXML
    private Label detailHintLabel;

    @FXML
    private Label feedbackLabel;

    private final AdminSalleService salleService = new AdminSalleService();
    private final AdminEquipementService equipementService = new AdminEquipementService();
    private final AdminPlaceService placeService = new AdminPlaceService();
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final ObservableList<Equipement> equipements = FXCollections.observableArrayList();
    private final ObservableList<Place> places = FXCollections.observableArrayList();
    private final FilteredList<Salle> filteredSalles = new FilteredList<>(salles, salle -> true);
    private final FilteredList<Equipement> filteredEquipements = new FilteredList<>(equipements, equipement -> true);
    private Object selectedElement;
    private Node selectedCard;

    @FXML
    private void initialize() {
        configureResponsiveCatalogLayout();
        configureFilters();
        hideFeedback();
        resetDetailPanel();
        loadCatalog(false);
    }

    @FXML
    private void handleRefresh() {
        hideFeedback();
        loadCatalog(true);
    }

    @FXML
    private void handleUseSelectedElement() {
        hideFeedback();

        if (selectedElement instanceof Salle salle) {
            useRoomForSession(salle);
            return;
        }

        if (selectedElement instanceof Equipement equipement) {
            useEquipmentForSession(equipement);
            return;
        }

        showFeedback("Selectionnez une salle ou un materiel avant de continuer.", false);
    }

    private void configureFilters() {
        roomTypeFilterComboBox.getItems().setAll(FILTER_ALL_TYPES);
        roomTypeFilterComboBox.getItems().addAll(salleService.getAvailableTypes());
        roomTypeFilterComboBox.setValue(FILTER_ALL_TYPES);

        roomStatusFilterComboBox.getItems().setAll(FILTER_ALL_STATUSES);
        roomStatusFilterComboBox.getItems().addAll(salleService.getAvailableEtats());
        roomStatusFilterComboBox.setValue(FILTER_ALL_STATUSES);

        equipmentTypeFilterComboBox.getItems().setAll(FILTER_ALL_TYPES);
        equipmentTypeFilterComboBox.getItems().addAll(equipementService.getAvailableTypes());
        equipmentTypeFilterComboBox.setValue(FILTER_ALL_TYPES);

        equipmentStatusFilterComboBox.getItems().setAll(FILTER_ALL_STATUSES);
        equipmentStatusFilterComboBox.getItems().addAll(equipementService.getAvailableEtats());
        equipmentStatusFilterComboBox.setValue(FILTER_ALL_STATUSES);

        roomSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyRoomFilters());
        roomTypeFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyRoomFilters());
        roomStatusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyRoomFilters());
        accessibilityFilterCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> applyRoomFilters());

        equipmentSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyEquipmentFilters());
        equipmentTypeFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyEquipmentFilters());
        equipmentStatusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyEquipmentFilters());
    }

    private void configureResponsiveCatalogLayout() {
        configureCatalogGrid(roomCardsContainer);
        configureCatalogGrid(equipmentCardsContainer);
    }

    private void configureCatalogGrid(GridPane grid) {
        grid.setHgap(CATALOG_CARD_GAP);
        grid.setVgap(CATALOG_CARD_GAP);
        grid.getColumnConstraints().clear();

        for (int column = 0; column < CATALOG_COLUMNS; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / CATALOG_COLUMNS);
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setFillWidth(true);
            grid.getColumnConstraints().add(constraints);
        }
    }

    private void loadCatalog(boolean showSuccess) {
        try {
            salles.setAll(salleService.getAll());
            equipements.setAll(equipementService.getAll());
            places.setAll(placeService.getAll());
            updateStats();
            applyRoomFilters();
            applyEquipmentFilters();

            if (showSuccess) {
                showFeedback("Infrastructure actualisee.", true);
            }
        } catch (SQLException | IllegalStateException exception) {
            salles.clear();
            equipements.clear();
            places.clear();
            updateStats();
            applyRoomFilters();
            applyEquipmentFilters();
            showFeedback("Chargement impossible : " + resolveMessage(exception), false);
        }
    }

    private void applyRoomFilters() {
        String normalizedSearch = normalize(roomSearchField.getText());
        String selectedType = roomTypeFilterComboBox.getValue();
        String selectedStatus = roomStatusFilterComboBox.getValue();
        boolean requireAccessibility = accessibilityFilterCheckBox.isSelected();

        filteredSalles.setPredicate(salle ->
            matchesRoomSearch(salle, normalizedSearch)
                && matchesFilter(salle.getTypeSalle(), selectedType, FILTER_ALL_TYPES)
                && matchesFilter(salle.getEtat(), selectedStatus, FILTER_ALL_STATUSES)
                && (!requireAccessibility || salle.isAccesHandicape())
        );

        renderRoomCards();
        updateRoomCount();
    }

    private void applyEquipmentFilters() {
        String normalizedSearch = normalize(equipmentSearchField.getText());
        String selectedType = equipmentTypeFilterComboBox.getValue();
        String selectedStatus = equipmentStatusFilterComboBox.getValue();

        filteredEquipements.setPredicate(equipement ->
            matchesEquipmentSearch(equipement, normalizedSearch)
                && matchesFilter(equipement.getTypeEquipement(), selectedType, FILTER_ALL_TYPES)
                && matchesFilter(equipement.getEtat(), selectedStatus, FILTER_ALL_STATUSES)
        );

        renderEquipmentCards();
        updateEquipmentCount();
    }

    private void renderRoomCards() {
        roomCardsContainer.getChildren().clear();

        if (filteredSalles.isEmpty()) {
            addEmptyCatalogState(roomCardsContainer, "Aucune salle ne correspond aux filtres.");
            return;
        }

        int index = 0;
        for (Salle salle : filteredSalles) {
            addCatalogCard(roomCardsContainer, createRoomCard(salle), index++);
        }
    }

    private void renderEquipmentCards() {
        equipmentCardsContainer.getChildren().clear();

        if (filteredEquipements.isEmpty()) {
            addEmptyCatalogState(equipmentCardsContainer, "Aucun equipement ne correspond aux filtres.");
            return;
        }

        int index = 0;
        for (Equipement equipement : filteredEquipements) {
            addCatalogCard(equipmentCardsContainer, createEquipmentCard(equipement), index++);
        }
    }

    private Node createRoomCard(Salle salle) {
        VBox card = createCard();
        card.setOnMouseClicked(event -> selectRoom(salle, card, false));

        HBox header = createCardHeader("S", salle.getNom(), salle.getEtat());
        Label metaLine = createCardMeta(
            formatOptionalText(salle.getTypeSalle())
                + " | "
                + salle.getCapacite()
                + " places | "
                + formatOptionalText(salle.getLocalisation())
        );
        Region descriptionSpace = createCardDescriptionSpace();

        VBox details = new VBox(7);
        details.getChildren().addAll(
            createFact("Batiment", formatOptionalText(salle.getBatiment())),
            createFact("Etage", salle.getEtage() == null ? "Non renseigne" : String.valueOf(salle.getEtage())),
            createFact("Disposition", formatOptionalText(salle.getTypeDisposition())),
            createFact("Acces handicap", salle.isAccesHandicape() ? "Oui" : "Non")
        );

        Button detailsButton = createSecondaryButton("Voir details");
        detailsButton.setOnAction(event -> {
            selectRoom(salle, card, false);
            event.consume();
        });

        Button chooseButton = createPrimaryButton("Choisir");
        chooseButton.setDisable(!isUsable(salle.getEtat()));
        chooseButton.setOnAction(event -> {
            selectRoom(salle, card, true);
            event.consume();
        });

        HBox actions = createActions(detailsButton, chooseButton);
        card.getChildren().addAll(header, metaLine, descriptionSpace, details, actions);
        return card;
    }

    private Node createEquipmentCard(Equipement equipement) {
        VBox card = createCard();
        card.setOnMouseClicked(event -> selectEquipment(equipement, card, false));

        HBox header = createCardHeader("M", equipement.getNom(), equipement.getEtat());
        Label metaLine = createCardMeta(
            formatLabel(equipement.getTypeEquipement())
                + " | Stock global: "
                + equipement.getQuantiteDisponible()
        );
        Region descriptionSpace = createCardDescriptionSpace();

        VBox details = new VBox(7);
        details.getChildren().addAll(
            createFact("Type", formatLabel(equipement.getTypeEquipement())),
            createFact("Quantite", equipement.getQuantiteDisponible() + " unite(s)"),
            createFact("Etat", formatLabel(equipement.getEtat()))
        );

        Button detailsButton = createSecondaryButton("Voir details");
        detailsButton.setOnAction(event -> {
            selectEquipment(equipement, card, false);
            event.consume();
        });

        Button chooseButton = createPrimaryButton("Choisir");
        chooseButton.setDisable(!isUsable(equipement.getEtat()));
        chooseButton.setOnAction(event -> {
            selectEquipment(equipement, card, true);
            event.consume();
        });

        HBox actions = createActions(detailsButton, chooseButton);
        card.getChildren().addAll(header, metaLine, descriptionSpace, details, actions);
        return card;
    }

    private VBox createCard() {
        VBox card = new VBox(12);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("infrastructure-card");
        return card;
    }

    private void addCatalogCard(GridPane grid, Node card, int index) {
        int column = index % CATALOG_COLUMNS;
        int row = index / CATALOG_COLUMNS;

        GridPane.setFillWidth(card, true);
        GridPane.setHgrow(card, Priority.ALWAYS);
        grid.add(card, column, row);
    }

    private void addEmptyCatalogState(GridPane grid, String message) {
        Node emptyState = createEmptyState(message);
        GridPane.setFillWidth(emptyState, true);
        GridPane.setHgrow(emptyState, Priority.ALWAYS);
        grid.add(emptyState, 0, 0, CATALOG_COLUMNS, 1);
    }

    private HBox createCardHeader(String badgeText, String title, String status) {
        Label badge = new Label(badgeText);
        badge.getStyleClass().add("infrastructure-card-badge");

        Label titleLabel = new Label(formatOptionalText(title));
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.getStyleClass().add("infrastructure-card-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label statusChip = new Label(formatLabel(status));
        statusChip.getStyleClass().setAll("status-chip", resolveStatusStyle(status));

        HBox header = new HBox(10, badge, titleLabel, statusChip);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Label createCardMeta(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("infrastructure-card-meta");
        return label;
    }

    private Region createCardDescriptionSpace() {
        Region space = new Region();
        space.setMinHeight(28);
        space.setPrefHeight(28);
        return space;
    }

    private HBox createFact(String label, String value) {
        Label keyLabel = new Label(label);
        keyLabel.getStyleClass().add("infrastructure-fact-label");

        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        valueLabel.getStyleClass().add("infrastructure-fact-value");
        HBox.setHgrow(valueLabel, Priority.ALWAYS);

        HBox row = new HBox(8, keyLabel, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox createActions(Button detailsButton, Button chooseButton) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10, detailsButton, spacer, chooseButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("infrastructure-actions");
        return actions;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("backoffice-primary-button");
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("action-button");
        button.getStyleClass().add("secondary");
        return button;
    }

    private Node createEmptyState(String message) {
        VBox emptyState = new VBox(8);
        emptyState.setMaxWidth(Double.MAX_VALUE);
        emptyState.getStyleClass().add("infrastructure-empty-card");

        Label title = new Label(message);
        title.getStyleClass().add("backoffice-mini-title");

        Label copy = new Label("Modifiez les filtres ou actualisez le catalogue.");
        copy.setWrapText(true);
        copy.getStyleClass().add("backoffice-mini-copy");

        emptyState.getChildren().addAll(title, copy);
        return emptyState;
    }

    private void selectRoom(Salle salle, Node card, boolean showPreview) {
        selectCard(card);
        selectedElement = salle;
        RoomAvailability availability = resolveRoomAvailability(salle);
        boolean usable = isUsable(salle.getEtat());

        detailTypeLabel.setText("Salle");
        detailStatusLabel.setText(formatLabel(salle.getEtat()));
        detailStatusLabel.getStyleClass().setAll("status-chip", resolveStatusStyle(salle.getEtat()));
        detailTitleLabel.setText(formatOptionalText(salle.getNom()));
        detailSubtitleLabel.setText(
            formatOptionalText(salle.getTypeSalle())
                + " | "
                + salle.getCapacite()
                + " places | "
                + formatOptionalText(salle.getLocalisation())
        );
        detailDescriptionLabel.setText(formatDescription(salle.getDescription()));
        detailUseButton.setDisable(!usable);
        detailHintLabel.setText(buildChoiceNote(salle.getEtat(), "cette salle"));

        detailFactsContainer.getChildren().setAll(
            createDetailInfoCard("Identifiant", String.valueOf(salle.getIdSalle())),
            createDetailInfoCard("Disponibles", availability.availablePlaces() + " / " + availability.totalPlaces()),
            createDetailInfoCard("Occupees", String.valueOf(availability.occupiedPlaces())),
            createDetailInfoCard("Batiment", formatOptionalText(salle.getBatiment())),
            createDetailInfoCard("Etage", salle.getEtage() == null ? "Non renseigne" : String.valueOf(salle.getEtage())),
            createDetailInfoCard("Disposition", formatOptionalText(salle.getTypeDisposition())),
            createDetailInfoCard("Acces", salle.isAccesHandicape() ? "Handicap oui" : "Standard"),
            createDetailInfoCard("Maintenance", formatDate(salle.getDateDerniereMaintenance()))
        );

        if (showPreview) {
            showRoomPreview(salle);
        } else {
            hideSessionPreview();
        }
    }

    private void selectEquipment(Equipement equipement, Node card, boolean showPreview) {
        selectCard(card);
        selectedElement = equipement;
        EquipmentAvailability availability = resolveEquipmentAvailability(equipement);
        boolean usable = isUsable(equipement.getEtat());

        detailTypeLabel.setText("Materiel");
        detailStatusLabel.setText(formatLabel(equipement.getEtat()));
        detailStatusLabel.getStyleClass().setAll("status-chip", resolveStatusStyle(equipement.getEtat()));
        detailTitleLabel.setText(formatOptionalText(equipement.getNom()));
        detailSubtitleLabel.setText(formatLabel(equipement.getTypeEquipement()) + " | Stock global: " + availability.totalQuantity());
        detailDescriptionLabel.setText(formatDescription(equipement.getDescription()));
        detailUseButton.setDisable(!usable);
        detailHintLabel.setText(buildChoiceNote(equipement.getEtat(), "ce materiel"));

        detailFactsContainer.getChildren().setAll(
            createDetailInfoCard("Identifiant", String.valueOf(equipement.getIdEquipement())),
            createDetailInfoCard("Type", formatLabel(equipement.getTypeEquipement())),
            createDetailInfoCard("Quantite disponible", availability.availableQuantity() + " unite(s)"),
            createDetailInfoCard("Quantite occupee", availability.occupiedQuantity() + " unite(s)"),
            createDetailInfoCard("Etat", formatLabel(equipement.getEtat()))
        );

        if (showPreview) {
            showEquipmentPreview(equipement);
        } else {
            hideSessionPreview();
        }
    }

    private Node createDetailInfoCard(String label, String value) {
        VBox card = new VBox(5);
        card.setPrefWidth(132);
        card.setMinWidth(132);
        card.getStyleClass().add("infrastructure-detail-fact-card");

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("infrastructure-detail-fact-label");

        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("infrastructure-detail-fact-value");

        card.getChildren().addAll(labelNode, valueNode);
        return card;
    }

    private void selectCard(Node card) {
        if (selectedCard != null) {
            selectedCard.getStyleClass().remove("selected");
        }

        selectedCard = card;
        if (selectedCard != null && !selectedCard.getStyleClass().contains("selected")) {
            selectedCard.getStyleClass().add("selected");
        }
    }

    private void showRoomPreview(Salle salle) {
        RoomAvailability availability = resolveRoomAvailability(salle);

        sessionPreviewLabel.setText(
            "Mode: presentielle\n"
                + "Salle pre-remplie: "
                + formatOptionalText(salle.getNom())
                + "\nPlaces disponibles: "
                + availability.availablePlaces()
                + " / "
                + availability.totalPlaces()
                + "\nLocalisation: "
                + formatOptionalText(salle.getLocalisation())
        );
        sessionPreviewContainer.setManaged(true);
        sessionPreviewContainer.setVisible(true);
        detailHintLabel.setText(buildChoiceNote(salle.getEtat(), "cette salle"));
    }

    private void useRoomForSession(Salle salle) {
        if (salle == null || !isUsable(salle.getEtat())) {
            showFeedback("Cette salle n'est pas disponible pour une seance presentielle.", false);
            return;
        }

        SessionCreationContext.prepareRoomSelection(salle.getIdSalle());
        boolean opened = SessionCreationContext.requestSessionCreationOpen();
        if (opened) {
            showFeedback(
                "La salle \"" + formatOptionalText(salle.getNom()) + "\" a ete envoyee vers le formulaire de seance.",
                true
            );
            return;
        }

        showRoomPreview(salle);
        showFeedback(
            "La salle est memorisee. Ouvrez maintenant Trouver un tuteur pour terminer la creation de la seance.",
            true
        );
    }

    private void useEquipmentForSession(Equipement equipement) {
        if (equipement == null || !isUsable(equipement.getEtat()) || equipement.getQuantiteDisponible() <= 0) {
            showFeedback("Ce materiel n'est pas disponible pour une seance presentielle.", false);
            return;
        }

        SessionCreationContext.prepareEquipmentSelection(equipement.getIdEquipement(), 1);
        boolean opened = SessionCreationContext.requestSessionCreationOpen();
        if (opened) {
            showFeedback(
                "Le materiel \"" + formatOptionalText(equipement.getNom()) + "\" a ete envoye vers le formulaire de seance.",
                true
            );
            return;
        }

        showEquipmentPreview(equipement);
        showFeedback(
            "Le materiel est memorise. Ouvrez maintenant Trouver un tuteur pour terminer la creation de la seance.",
            true
        );
    }

    private void showEquipmentPreview(Equipement equipement) {
        EquipmentAvailability availability = resolveEquipmentAvailability(equipement);

        sessionPreviewLabel.setText(
            "Mode: presentielle\n"
                + "Materiel demande: "
                + formatOptionalText(equipement.getNom())
                + "\nType: "
                + formatLabel(equipement.getTypeEquipement())
                + "\nQuantite disponible: "
                + availability.availableQuantity()
                + " unite(s)"
        );
        sessionPreviewContainer.setManaged(true);
        sessionPreviewContainer.setVisible(true);
        detailHintLabel.setText(buildChoiceNote(equipement.getEtat(), "ce materiel"));
    }

    private void hideSessionPreview() {
        sessionPreviewLabel.setText("");
        sessionPreviewContainer.setManaged(false);
        sessionPreviewContainer.setVisible(false);
    }

    private void resetDetailPanel() {
        selectedElement = null;
        selectCard(null);
        detailTypeLabel.setText("Selection");
        detailStatusLabel.setText("En attente");
        detailStatusLabel.getStyleClass().setAll("status-chip", "unavailable");
        detailTitleLabel.setText("Selectionnez une carte");
        detailSubtitleLabel.setText("Les informations detaillees apparaitront ici.");
        detailFactsContainer.getChildren().clear();
        detailDescriptionLabel.setText("Aucun element selectionne.");
        detailUseButton.setDisable(true);
        detailHintLabel.setText("Choisissez une salle ou un materiel pour preparer l'integration future.");
        hideSessionPreview();
    }

    private boolean matchesRoomSearch(Salle salle, String normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            return true;
        }

        return contains(salle.getNom(), normalizedSearch)
            || contains(salle.getLocalisation(), normalizedSearch)
            || contains(salle.getBatiment(), normalizedSearch)
            || contains(salle.getTypeSalle(), normalizedSearch)
            || contains(salle.getEtat(), normalizedSearch)
            || contains(salle.getDescription(), normalizedSearch);
    }

    private boolean matchesEquipmentSearch(Equipement equipement, String normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            return true;
        }

        return contains(equipement.getNom(), normalizedSearch)
            || contains(equipement.getTypeEquipement(), normalizedSearch)
            || contains(equipement.getEtat(), normalizedSearch)
            || contains(equipement.getDescription(), normalizedSearch);
    }

    private boolean matchesFilter(String value, String selectedValue, String allValue) {
        return selectedValue == null
            || allValue.equals(selectedValue)
            || normalize(value).equals(normalize(selectedValue));
    }

    private void updateRoomCount() {
        int count = filteredSalles.size();
        roomCountLabel.setText(count == 1 ? "1 salle visible" : count + " salles visibles");
    }

    private void updateEquipmentCount() {
        int count = filteredEquipements.size();
        equipmentCountLabel.setText(count == 1 ? "1 equipement visible" : count + " equipements visibles");
    }

    private void updateStats() {
        long availableRooms = salles.stream()
            .filter(salle -> isUsable(salle.getEtat()))
            .count();

        int availableEquipmentUnits = equipements.stream()
            .mapToInt(equipement -> resolveEquipmentAvailability(equipement).availableQuantity())
            .sum();

        totalRoomsStatLabel.setText(String.valueOf(salles.size()));
        availableRoomsStatLabel.setText(String.valueOf(availableRooms));
        totalEquipmentsStatLabel.setText(String.valueOf(equipements.size()));
        availableEquipmentsStatLabel.setText(String.valueOf(availableEquipmentUnits));
    }

    private String buildChoiceNote(String status, String elementName) {
        if (isUsable(status)) {
            return "Statut: " + elementName + " est disponible. Les conflits de salle sont verifies lors de la publication de la seance.";
        }

        return "Statut: " + elementName + " necessitera une validation admin avant publication de la seance.";
    }

    private String resolveStatusStyle(String status) {
        String normalizedStatus = normalize(status);
        if (isUsable(status)) {
            return "available";
        }
        if (normalizedStatus.contains("maintenance")) {
            return "maintenance";
        }
        return "unavailable";
    }

    private boolean isUsable(String status) {
        return STATUS_AVAILABLE.equals(normalize(status));
    }

    private RoomAvailability resolveRoomAvailability(Salle salle) {
        int configuredCapacity = Math.max(0, salle.getCapacite());
        List<Place> roomPlaces = places.stream()
            .filter(place -> place.getIdSalle() == salle.getIdSalle())
            .toList();

        int totalPlaces = Math.max(configuredCapacity, roomPlaces.size());
        if (!isUsable(salle.getEtat())) {
            return new RoomAvailability(totalPlaces, 0, totalPlaces);
        }

        if (roomPlaces.isEmpty()) {
            return new RoomAvailability(totalPlaces, totalPlaces, 0);
        }

        int availablePlaces = (int) roomPlaces.stream()
            .filter(place -> isUsable(place.getEtat()))
            .count();
        int occupiedPlaces = Math.max(0, totalPlaces - availablePlaces);
        return new RoomAvailability(totalPlaces, availablePlaces, occupiedPlaces);
    }

    private EquipmentAvailability resolveEquipmentAvailability(Equipement equipement) {
        int totalQuantity = Math.max(0, equipement.getQuantiteDisponible());
        int availableQuantity = isUsable(equipement.getEtat()) ? totalQuantity : 0;
        int occupiedQuantity = Math.max(0, totalQuantity - availableQuantity);
        return new EquipmentAvailability(totalQuantity, availableQuantity, occupiedQuantity);
    }

    private String formatDescription(String description) {
        String normalizedDescription = trimToNull(description);
        return normalizedDescription == null ? "Aucune description renseignee." : normalizedDescription;
    }

    private String formatOptionalText(String value) {
        String normalizedValue = trimToNull(value);
        return normalizedValue == null ? "Non renseigne" : normalizedValue;
    }

    private String formatLabel(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "Non defini";
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "Non renseignee" : date.toString();
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

    private record RoomAvailability(int totalPlaces, int availablePlaces, int occupiedPlaces) {
    }

    private record EquipmentAvailability(int totalQuantity, int availableQuantity, int occupiedQuantity) {
    }
}
