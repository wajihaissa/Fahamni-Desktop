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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.services.AdminEquipementService;
import tn.esprit.fahamni.services.AdminSalleService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;

public class SallesEquipementsController {

    private static final String FILTER_ALL_TYPES = "Tous les types";
    private static final String FILTER_ALL_STATUSES = "Tous les etats";
    private static final String STATUS_AVAILABLE = "disponible";

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
    private FlowPane roomCardsContainer;

    @FXML
    private TextField equipmentSearchField;

    @FXML
    private ComboBox<String> equipmentTypeFilterComboBox;

    @FXML
    private ComboBox<String> equipmentStatusFilterComboBox;

    @FXML
    private Label equipmentCountLabel;

    @FXML
    private FlowPane equipmentCardsContainer;

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
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final ObservableList<Equipement> equipements = FXCollections.observableArrayList();
    private final FilteredList<Salle> filteredSalles = new FilteredList<>(salles, salle -> true);
    private final FilteredList<Equipement> filteredEquipements = new FilteredList<>(equipements, equipement -> true);
    private Object selectedElement;
    private Node selectedCard;

    @FXML
    private void initialize() {
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
            showRoomPreview(salle);
            return;
        }

        if (selectedElement instanceof Equipement equipement) {
            showEquipmentPreview(equipement);
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

    private void loadCatalog(boolean showSuccess) {
        try {
            salles.setAll(salleService.getAll());
            equipements.setAll(equipementService.getAll());
            updateStats();
            applyRoomFilters();
            applyEquipmentFilters();

            if (showSuccess) {
                showFeedback("Infrastructure actualisee.", true);
            }
        } catch (SQLException | IllegalStateException exception) {
            salles.clear();
            equipements.clear();
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
            roomCardsContainer.getChildren().add(createEmptyState("Aucune salle ne correspond aux filtres."));
            return;
        }

        for (Salle salle : filteredSalles) {
            roomCardsContainer.getChildren().add(createRoomCard(salle));
        }
    }

    private void renderEquipmentCards() {
        equipmentCardsContainer.getChildren().clear();

        if (filteredEquipements.isEmpty()) {
            equipmentCardsContainer.getChildren().add(createEmptyState("Aucun equipement ne correspond aux filtres."));
            return;
        }

        for (Equipement equipement : filteredEquipements) {
            equipmentCardsContainer.getChildren().add(createEquipmentCard(equipement));
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
        Label description = createCardDescription(salle.getDescription());

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
        chooseButton.setOnAction(event -> {
            selectRoom(salle, card, true);
            event.consume();
        });

        HBox actions = createActions(detailsButton, chooseButton);
        card.getChildren().addAll(header, metaLine, description, details, actions);
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
        Label description = createCardDescription(equipement.getDescription());

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
        chooseButton.setOnAction(event -> {
            selectEquipment(equipement, card, true);
            event.consume();
        });

        HBox actions = createActions(detailsButton, chooseButton);
        card.getChildren().addAll(header, metaLine, description, details, actions);
        return card;
    }

    private VBox createCard() {
        VBox card = new VBox(12);
        card.setPrefWidth(300);
        card.setMinWidth(280);
        card.getStyleClass().add("infrastructure-card");
        return card;
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

    private Label createCardDescription(String description) {
        Label label = new Label(buildDescriptionPreview(description));
        label.setWrapText(true);
        label.setMinHeight(46);
        label.getStyleClass().add("infrastructure-card-copy");
        return label;
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
        emptyState.setPrefWidth(620);
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
        detailUseButton.setDisable(false);
        detailHintLabel.setText(buildChoiceNote(salle.getEtat(), "cette salle"));

        detailFactsContainer.getChildren().setAll(
            createDetailInfoCard("Identifiant", String.valueOf(salle.getIdSalle())),
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

        detailTypeLabel.setText("Materiel");
        detailStatusLabel.setText(formatLabel(equipement.getEtat()));
        detailStatusLabel.getStyleClass().setAll("status-chip", resolveStatusStyle(equipement.getEtat()));
        detailTitleLabel.setText(formatOptionalText(equipement.getNom()));
        detailSubtitleLabel.setText(formatLabel(equipement.getTypeEquipement()) + " | Stock global: " + equipement.getQuantiteDisponible());
        detailDescriptionLabel.setText(formatDescription(equipement.getDescription()));
        detailUseButton.setDisable(false);
        detailHintLabel.setText(buildChoiceNote(equipement.getEtat(), "ce materiel"));

        detailFactsContainer.getChildren().setAll(
            createDetailInfoCard("Identifiant", String.valueOf(equipement.getIdEquipement())),
            createDetailInfoCard("Type", formatLabel(equipement.getTypeEquipement())),
            createDetailInfoCard("Quantite", equipement.getQuantiteDisponible() + " unite(s)"),
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
        card.setPrefWidth(145);
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
        sessionPreviewLabel.setText(
            "Mode: presentielle\n"
                + "Salle pre-remplie: "
                + formatOptionalText(salle.getNom())
                + "\nCapacite: "
                + salle.getCapacite()
                + " places\nLocalisation: "
                + formatOptionalText(salle.getLocalisation())
        );
        sessionPreviewContainer.setManaged(true);
        sessionPreviewContainer.setVisible(true);
        detailHintLabel.setText(buildChoiceNote(salle.getEtat(), "cette salle"));
    }

    private void showEquipmentPreview(Equipement equipement) {
        sessionPreviewLabel.setText(
            "Mode: presentielle\n"
                + "Materiel demande: "
                + formatOptionalText(equipement.getNom())
                + "\nType: "
                + formatLabel(equipement.getTypeEquipement())
                + "\nQuantite disponible: "
                + equipement.getQuantiteDisponible()
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
            .filter(salle -> STATUS_AVAILABLE.equals(normalize(salle.getEtat())))
            .count();

        int availableEquipmentUnits = equipements.stream()
            .filter(equipement -> STATUS_AVAILABLE.equals(normalize(equipement.getEtat())))
            .mapToInt(Equipement::getQuantiteDisponible)
            .sum();

        totalRoomsStatLabel.setText(String.valueOf(salles.size()));
        availableRoomsStatLabel.setText(String.valueOf(availableRooms));
        totalEquipmentsStatLabel.setText(String.valueOf(equipements.size()));
        availableEquipmentsStatLabel.setText(String.valueOf(availableEquipmentUnits));
    }

    private String buildDescriptionPreview(String description) {
        String normalizedDescription = trimToNull(description);
        if (normalizedDescription == null) {
            return "Aucune description renseignee.";
        }

        if (normalizedDescription.length() <= 105) {
            return normalizedDescription;
        }

        return normalizedDescription.substring(0, 102) + "...";
    }

    private String buildChoiceNote(String status, String elementName) {
        if (STATUS_AVAILABLE.equals(normalize(status))) {
            return "Statut: " + elementName + " est disponible. La verification par horaire sera ajoutee avec le module seance.";
        }

        return "Statut: " + elementName + " necessitera une validation admin avant publication de la seance.";
    }

    private String resolveStatusStyle(String status) {
        String normalizedStatus = normalize(status);
        if (STATUS_AVAILABLE.equals(normalizedStatus)) {
            return "available";
        }
        if (normalizedStatus.contains("maintenance")) {
            return "maintenance";
        }
        return "unavailable";
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
}
