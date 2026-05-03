package tn.esprit.fahamni.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
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
import tn.esprit.fahamni.Models.SalleEquipement;
import tn.esprit.fahamni.room3d.Room3DPreviewService;
import tn.esprit.fahamni.room3d.Room3DViewMode;
import tn.esprit.fahamni.room3d.Room3DViewerLauncher;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminPlaceService;
import tn.esprit.fahamni.services.AdminSalleService;
import tn.esprit.fahamni.services.SalleEquipementService;
import tn.esprit.fahamni.services.SessionCreationContext;
import tn.esprit.fahamni.utils.PaginationSupport;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

public class SallesEquipementsController {

    private static final String FILTER_ALL_TYPES = "Tous les types";
    private static final String FILTER_ALL_STATUSES = "Tous les etats";
    private static final String STATUS_AVAILABLE = "disponible";
    private static final String STATUS_PENDING = "attente";
    private static final int CATALOG_COLUMNS = 3;
    private static final double CATALOG_CARD_GAP = 14.0;
    private static final int MIN_CATALOG_PAGE_SIZE = 6;

    @FXML
    private TextField roomSearchField;

    @FXML
    private ScrollPane catalogScrollPane;

    @FXML
    private TabPane catalogTabPane;

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
    private Label roomPaginationSummaryLabel;

    @FXML
    private Button roomPreviousPageButton;

    @FXML
    private HBox roomPageButtonsContainer;

    @FXML
    private Button roomNextPageButton;

    @FXML
    private ComboBox<Integer> roomPageSizeComboBox;

    @FXML
    private HBox roomPaginationBar;

    @FXML
    private VBox roomTabContent;

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
    private Label equipmentPaginationSummaryLabel;

    @FXML
    private Button equipmentPreviousPageButton;

    @FXML
    private HBox equipmentPageButtonsContainer;

    @FXML
    private Button equipmentNextPageButton;

    @FXML
    private ComboBox<Integer> equipmentPageSizeComboBox;

    @FXML
    private HBox equipmentPaginationBar;

    @FXML
    private VBox equipmentTabContent;

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
    private VBox room3DSpotlightContainer;

    @FXML
    private Label room3DModeLabel;

    @FXML
    private Label room3DHeadlineLabel;

    @FXML
    private Label room3DCopyLabel;

    @FXML
    private FlowPane room3DHighlightsContainer;

    @FXML
    private Button room3DPreviewButton;

    @FXML
    private Label detailFixedEquipmentSummaryLabel;

    @FXML
    private VBox detailFixedEquipementsContainer;

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
    private final SalleEquipementService salleEquipementService = new SalleEquipementService();
    private final Room3DPreviewService room3DPreviewService = new Room3DPreviewService();
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final ObservableList<Equipement> equipements = FXCollections.observableArrayList();
    private final ObservableList<Place> places = FXCollections.observableArrayList();
    private final FilteredList<Salle> filteredSalles = new FilteredList<>(salles, salle -> true);
    private final FilteredList<Equipement> filteredEquipements = new FilteredList<>(equipements, equipement -> true);
    private final Map<Integer, List<SalleEquipement>> fixedEquipementsBySalleId = new LinkedHashMap<>();
    private int roomCurrentPageIndex;
    private int equipmentCurrentPageIndex;
    private int roomRowsPerPage = MIN_CATALOG_PAGE_SIZE;
    private int equipmentRowsPerPage = MIN_CATALOG_PAGE_SIZE;
    private Object selectedElement;
    private Node selectedCard;

    @FXML
    private void initialize() {
        configureResponsiveCatalogLayout();
        configurePagination();
        configureFilters();
        configureCatalogTabPane();
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
    private void handlePreviewSelectedRoom3D() {
        hideFeedback();

        if (!(selectedElement instanceof Salle salle)) {
            showFeedback("Selectionnez une salle pour ouvrir son studio 3D.", false);
            return;
        }

        openRoom3DPreview(salle, true);
    }

    @FXML
    private void handlePreviousRoomPage() {
        if (roomCurrentPageIndex <= 0) {
            return;
        }

        roomCurrentPageIndex--;
        renderRoomCards();
    }

    @FXML
    private void handleNextRoomPage() {
        int totalPages = PaginationSupport.slice(roomCurrentPageIndex + 1, filteredSalles.size(), roomRowsPerPage).totalPages();
        if (roomCurrentPageIndex >= totalPages - 1) {
            return;
        }

        roomCurrentPageIndex++;
        renderRoomCards();
    }

    @FXML
    private void handlePreviousEquipmentPage() {
        if (equipmentCurrentPageIndex <= 0) {
            return;
        }

        equipmentCurrentPageIndex--;
        renderEquipmentCards();
    }

    @FXML
    private void handleNextEquipmentPage() {
        int totalPages = PaginationSupport.slice(equipmentCurrentPageIndex + 1, filteredEquipements.size(), equipmentRowsPerPage).totalPages();
        if (equipmentCurrentPageIndex >= totalPages - 1) {
            return;
        }

        equipmentCurrentPageIndex++;
        renderEquipmentCards();
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

    private void configurePagination() {
        configurePageSizeCombo(roomPageSizeComboBox, roomRowsPerPage, newValue -> {
            if (newValue == roomRowsPerPage) {
                return;
            }
            roomRowsPerPage = newValue;
            roomCurrentPageIndex = 0;
            renderRoomCards();
            ensureNodeVisible(roomPaginationBar);
        });

        configurePageSizeCombo(equipmentPageSizeComboBox, equipmentRowsPerPage, newValue -> {
            if (newValue == equipmentRowsPerPage) {
                return;
            }
            equipmentRowsPerPage = newValue;
            equipmentCurrentPageIndex = 0;
            renderEquipmentCards();
            ensureNodeVisible(equipmentPaginationBar);
        });
    }

    private void configureCatalogTabPane() {
        if (catalogTabPane == null) {
            return;
        }

        catalogTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> refreshCatalogTabPaneHeight());
        catalogTabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                refreshCatalogTabPaneHeight();
            }
        });
    }

    private void configurePageSizeCombo(ComboBox<Integer> comboBox, int initialValue, IntConsumer onChange) {
        if (comboBox == null) {
            return;
        }

        comboBox.getItems().setAll(MIN_CATALOG_PAGE_SIZE, 9, 12, 15);
        comboBox.setValue(initialValue);
        comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue < MIN_CATALOG_PAGE_SIZE) {
                comboBox.setValue(Math.max(initialValue, MIN_CATALOG_PAGE_SIZE));
                return;
            }
            onChange.accept(newValue);
        });
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
            fixedEquipementsBySalleId.clear();
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
            fixedEquipementsBySalleId.clear();
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

        roomCurrentPageIndex = 0;
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

        equipmentCurrentPageIndex = 0;
        renderEquipmentCards();
        updateEquipmentCount();
    }

    private void renderRoomCards() {
        roomCardsContainer.getChildren().clear();

        int filteredSize = filteredSalles.size();
        PaginationSupport.PageSlice pageSlice = PaginationSupport.slice(roomCurrentPageIndex + 1, filteredSize, roomRowsPerPage);
        roomCurrentPageIndex = Math.max(0, pageSlice.currentPage() - 1);

        if (pageSlice.isEmpty()) {
            addEmptyCatalogState(roomCardsContainer, "Aucune salle ne correspond aux filtres.");
            refreshCatalogPagination(
                roomPaginationSummaryLabel,
                roomPreviousPageButton,
                roomNextPageButton,
                roomPageButtonsContainer,
                pageSlice,
                "salle",
                "salles",
                this::showRoomPage
            );
            return;
        }

        int index = 0;
        for (Salle salle : filteredSalles.subList(pageSlice.fromIndex(), pageSlice.toIndex())) {
            addCatalogCard(roomCardsContainer, createRoomCard(salle), index++);
        }

        refreshCatalogPagination(
            roomPaginationSummaryLabel,
            roomPreviousPageButton,
            roomNextPageButton,
            roomPageButtonsContainer,
            pageSlice,
            "salle",
            "salles",
            this::showRoomPage
        );
        refreshCatalogTabPaneHeight();
    }

    private void renderEquipmentCards() {
        equipmentCardsContainer.getChildren().clear();

        int filteredSize = filteredEquipements.size();
        PaginationSupport.PageSlice pageSlice = PaginationSupport.slice(equipmentCurrentPageIndex + 1, filteredSize, equipmentRowsPerPage);
        equipmentCurrentPageIndex = Math.max(0, pageSlice.currentPage() - 1);

        if (pageSlice.isEmpty()) {
            addEmptyCatalogState(equipmentCardsContainer, "Aucun equipement ne correspond aux filtres.");
            refreshCatalogPagination(
                equipmentPaginationSummaryLabel,
                equipmentPreviousPageButton,
                equipmentNextPageButton,
                equipmentPageButtonsContainer,
                pageSlice,
                "equipement",
                "equipements",
                this::showEquipmentPage
            );
            return;
        }

        int index = 0;
        for (Equipement equipement : filteredEquipements.subList(pageSlice.fromIndex(), pageSlice.toIndex())) {
            addCatalogCard(equipmentCardsContainer, createEquipmentCard(equipement), index++);
        }

        refreshCatalogPagination(
            equipmentPaginationSummaryLabel,
            equipmentPreviousPageButton,
            equipmentNextPageButton,
            equipmentPageButtonsContainer,
            pageSlice,
            "equipement",
            "equipements",
            this::showEquipmentPage
        );
        refreshCatalogTabPaneHeight();
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

        Button preview3DButton = createSecondaryButton("Vue 3D");
        preview3DButton.getStyleClass().add("infrastructure-room-3d-action");
        preview3DButton.setDisable(salle.getCapacite() <= 0);
        preview3DButton.setOnAction(event -> {
            selectRoom(salle, card, false);
            openRoom3DPreview(salle, false);
            event.consume();
        });

        Button chooseButton = createPrimaryButton("Choisir");
        chooseButton.setDisable(!isUsable(salle.getEtat()));
        chooseButton.setOnAction(event -> {
            selectRoom(salle, card, true);
            event.consume();
        });

        HBox actions = createRoomActions(detailsButton, preview3DButton, chooseButton);
        card.getChildren().addAll(header, metaLine, descriptionSpace, details, actions);
        syncCardSelection(card, salle);
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
        syncCardSelection(card, equipement);
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

    private HBox createRoomActions(Button detailsButton, Button preview3DButton, Button chooseButton) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8, detailsButton, preview3DButton, spacer, chooseButton);
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
        updateRoom3DSpotlight(salle, availability);
        updateFixedEquipmentDetails(salle.getIdSalle());

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
        hideRoom3DSpotlight();
        resetFixedEquipmentDetails();

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

    private void syncCardSelection(Node card, Object element) {
        if (selectedElement != element) {
            return;
        }

        selectedCard = card;
        if (!card.getStyleClass().contains("selected")) {
            card.getStyleClass().add("selected");
        }
    }

    private Node createRoom3DHighlight(String label, String value) {
        VBox card = new VBox(4);
        card.setPrefWidth(88);
        card.setMinWidth(88);
        card.getStyleClass().add("infrastructure-room-3d-metric");

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("infrastructure-room-3d-metric-label");

        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("infrastructure-room-3d-metric-value");

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
                + "\nEquipements inclus: "
                + buildFixedEquipmentSummary(salle.getIdSalle())
        );
        sessionPreviewContainer.setManaged(true);
        sessionPreviewContainer.setVisible(true);
        detailHintLabel.setText(buildChoiceNote(salle.getEtat(), "cette salle"));
    }

    private void updateRoom3DSpotlight(Salle salle, RoomAvailability availability) {
        if (room3DSpotlightContainer == null) {
            return;
        }

        int configuredSeats = resolveConfiguredSeatCount(salle);
        boolean usesConfiguredPlan = configuredSeats > 0;
        String roomName = formatOptionalText(salle.getNom());
        String disposition = formatOptionalText(salle.getTypeDisposition());

        room3DModeLabel.setText(usesConfiguredPlan ? "Plan reel" : "Generation auto");
        room3DHeadlineLabel.setText("Entrez dans " + roomName + " avant de la reserver.");
        room3DCopyLabel.setText(
            usesConfiguredPlan
                ? "La scene 3D reprend les places deja configurees pour cette salle et respecte sa disposition."
                : "Aucun plan detaille n'est disponible : la scene 3D est composee automatiquement a partir de la capacite et de la disposition."
        );
        room3DHighlightsContainer.getChildren().setAll(
            createRoom3DHighlight("Source", usesConfiguredPlan ? configuredSeats + " places reelles" : "Capacite " + salle.getCapacite()),
            createRoom3DHighlight("Disponibles", availability.availablePlaces() + " / " + availability.totalPlaces()),
            createRoom3DHighlight("Disposition", disposition),
            createRoom3DHighlight("Acces", salle.isAccesHandicape() ? "PMR" : "Standard")
        );
        room3DPreviewButton.setDisable(salle.getCapacite() <= 0);
        room3DSpotlightContainer.setManaged(true);
        room3DSpotlightContainer.setVisible(true);
    }

    private void hideRoom3DSpotlight() {
        if (room3DSpotlightContainer == null) {
            return;
        }

        room3DModeLabel.setText("Modele genere");
        room3DHeadlineLabel.setText("Explorez la salle en immersion.");
        room3DCopyLabel.setText("Le moteur 3D reprend la disposition et la capacite de la salle selectionnee.");
        room3DHighlightsContainer.getChildren().clear();
        room3DPreviewButton.setDisable(true);
        room3DSpotlightContainer.setManaged(false);
        room3DSpotlightContainer.setVisible(false);
    }

    private void openRoom3DPreview(Salle salle, boolean showSuccessFeedback) {
        hideFeedback();

        if (salle == null) {
            showFeedback("Selectionnez une salle pour ouvrir son studio 3D.", false);
            return;
        }

        try {
            Room3DViewerLauncher.showPreview(room3DPreviewService.buildPreview(salle, false, Room3DViewMode.PREVIEW));
            if (showSuccessFeedback) {
                showFeedback("Le studio 3D de \"" + formatOptionalText(salle.getNom()) + "\" est ouvert.", true);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showFeedback("Ouverture 3D impossible : " + resolveMessage(exception), false);
        }
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
        detailStatusLabel.getStyleClass().setAll("status-chip", "pending");
        detailTitleLabel.setText("Selectionnez une carte");
        detailSubtitleLabel.setText("Les informations detaillees apparaitront ici.");
        detailFactsContainer.getChildren().clear();
        detailDescriptionLabel.setText("Aucun element selectionne.");
        detailUseButton.setDisable(true);
        detailHintLabel.setText("Choisissez une salle ou un materiel pour preparer l'integration future.");
        hideRoom3DSpotlight();
        resetFixedEquipmentDetails();
        hideSessionPreview();
    }

    private void updateFixedEquipmentDetails(int salleId) {
        if (detailFixedEquipementsContainer == null || detailFixedEquipmentSummaryLabel == null) {
            return;
        }

        detailFixedEquipementsContainer.getChildren().clear();
        List<SalleEquipement> fixedEquipements = getFixedEquipementsBySalleId(salleId);
        if (fixedEquipements.isEmpty()) {
            detailFixedEquipmentSummaryLabel.setText("Aucun fixe");
            detailFixedEquipementsContainer.getChildren().add(createFixedEquipmentInfoLabel(
                "Aucun equipement fixe n'est rattache a cette salle."
            ));
            return;
        }

        int totalUnits = fixedEquipements.stream().mapToInt(SalleEquipement::getQuantite).sum();
        detailFixedEquipmentSummaryLabel.setText(fixedEquipements.size() + " type(s) | " + totalUnits + " unite(s)");
        fixedEquipements.stream()
            .map(this::createFixedEquipmentInfoLabel)
            .forEach(detailFixedEquipementsContainer.getChildren()::add);
    }

    private void resetFixedEquipmentDetails() {
        if (detailFixedEquipmentSummaryLabel != null) {
            detailFixedEquipmentSummaryLabel.setText("Aucun fixe");
        }
        if (detailFixedEquipementsContainer != null) {
            detailFixedEquipementsContainer.getChildren().setAll(createFixedEquipmentInfoLabel(
                "Selectionnez une salle pour voir les equipements fixes qui lui sont rattaches."
            ));
        }
    }

    private Label createFixedEquipmentInfoLabel(SalleEquipement equipement) {
        return createFixedEquipmentInfoLabel(
            formatOptionalText(equipement.getNomEquipement())
                + " x"
                + equipement.getQuantite()
                + " | "
                + formatLabel(equipement.getTypeEquipement())
                + " | "
                + formatLabel(equipement.getEtatEquipement())
        );
    }

    private Label createFixedEquipmentInfoLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("infrastructure-detail-section-copy");
        return label;
    }

    private List<SalleEquipement> getFixedEquipementsBySalleId(int salleId) {
        if (salleId <= 0) {
            return List.of();
        }

        List<SalleEquipement> cached = fixedEquipementsBySalleId.get(salleId);
        if (cached != null) {
            return cached;
        }

        try {
            List<SalleEquipement> loaded = salleEquipementService.getEquipementsBySalleId(salleId);
            fixedEquipementsBySalleId.put(salleId, loaded);
            return loaded;
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            fixedEquipementsBySalleId.put(salleId, List.of());
            return List.of();
        }
    }

    private String buildFixedEquipmentSummary(int salleId) {
        List<SalleEquipement> fixedEquipements = getFixedEquipementsBySalleId(salleId);
        if (fixedEquipements.isEmpty()) {
            return "aucun";
        }

        return fixedEquipements.stream()
            .map(equipement -> formatOptionalText(equipement.getNomEquipement()) + " x" + equipement.getQuantite())
            .reduce((left, right) -> left + ", " + right)
            .orElse("aucun");
    }

    private int resolveConfiguredSeatCount(Salle salle) {
        if (salle == null || salle.getIdSalle() <= 0) {
            return 0;
        }

        return (int) places.stream()
            .filter(place -> place.getIdSalle() == salle.getIdSalle())
            .count();
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

    private void showRoomPage(int pageIndex) {
        roomCurrentPageIndex = Math.max(0, pageIndex - 1);
        renderRoomCards();
    }

    private void showEquipmentPage(int pageIndex) {
        equipmentCurrentPageIndex = Math.max(0, pageIndex - 1);
        renderEquipmentCards();
    }

    private void refreshCatalogPagination(
        Label summaryLabel,
        Button previousButton,
        Button nextButton,
        HBox pageButtonsContainer,
        PaginationSupport.PageSlice pageSlice,
        String singularLabel,
        String pluralLabel,
        IntConsumer pageChangeHandler
    ) {
        if (summaryLabel == null || previousButton == null || nextButton == null || pageButtonsContainer == null) {
            return;
        }

        summaryLabel.setText(buildPaginationSummary(pageSlice, singularLabel, pluralLabel));
        previousButton.setDisable(pageSlice.isEmpty() || pageSlice.currentPage() <= 1);
        nextButton.setDisable(pageSlice.isEmpty() || pageSlice.currentPage() >= pageSlice.totalPages());
        PaginationSupport.populatePageButtons(
            pageButtonsContainer,
            pageSlice.currentPage(),
            pageSlice.totalPages(),
            pageChangeHandler
        );
    }

    private String buildPaginationSummary(PaginationSupport.PageSlice pageSlice, String singularLabel, String pluralLabel) {
        String singularSuffix = "salle".equals(singularLabel) ? "affichee" : "affiche";
        String pluralSuffix = "salles".equals(pluralLabel) ? "affichees" : "affiches";
        return PaginationSupport.buildRangeSummary(pageSlice, singularLabel, pluralLabel, singularSuffix, pluralSuffix);
    }

    private void refreshCatalogTabPaneHeight() {
        if (catalogTabPane == null) {
            return;
        }

        Platform.runLater(() -> {
            VBox activeContent = resolveActiveCatalogContent();
            if (activeContent == null) {
                return;
            }

            activeContent.applyCss();
            activeContent.requestLayout();
            catalogTabPane.applyCss();
            catalogTabPane.setMinHeight(Region.USE_COMPUTED_SIZE);
            catalogTabPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            catalogTabPane.requestLayout();

            Parent parent = catalogTabPane.getParent();
            while (parent != null) {
                parent.requestLayout();
                parent = parent.getParent();
            }
        });
    }

    private VBox resolveActiveCatalogContent() {
        if (catalogTabPane == null) {
            return null;
        }

        int selectedIndex = catalogTabPane.getSelectionModel().getSelectedIndex();
        return selectedIndex == 1 ? equipmentTabContent : roomTabContent;
    }

    private void ensureNodeVisible(Node target) {
        if (catalogScrollPane == null || target == null) {
            return;
        }

        Platform.runLater(() -> {
            Node content = catalogScrollPane.getContent();
            if (content == null || target.getScene() == null || content.getScene() == null) {
                return;
            }

            content.applyCss();
            if (content instanceof Parent parent) {
                parent.layout();
            }

            Bounds viewportBounds = catalogScrollPane.getViewportBounds();
            Bounds contentBounds = content.getLayoutBounds();
            Bounds targetBounds = content.sceneToLocal(target.localToScene(target.getBoundsInLocal()));

            double viewportHeight = viewportBounds.getHeight();
            double scrollableHeight = Math.max(0, contentBounds.getHeight() - viewportHeight);
            if (scrollableHeight <= 0) {
                catalogScrollPane.setVvalue(0);
                return;
            }

            double currentPixelOffset = catalogScrollPane.getVvalue() * scrollableHeight;
            double desiredPixelOffset = currentPixelOffset;

            if (targetBounds.getMinY() < currentPixelOffset) {
                desiredPixelOffset = Math.max(0, targetBounds.getMinY() - 16);
            } else if (targetBounds.getMaxY() > currentPixelOffset + viewportHeight) {
                desiredPixelOffset = Math.min(scrollableHeight, targetBounds.getMaxY() - viewportHeight + 16);
            }

            catalogScrollPane.setVvalue(desiredPixelOffset / scrollableHeight);
        });
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
        if (normalizedStatus.contains(STATUS_PENDING)) {
            return "pending";
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
