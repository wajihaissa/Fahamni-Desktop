package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import tn.esprit.fahamni.Models.ReclamationSalle;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.services.AdminReclamationSalleService;
import tn.esprit.fahamni.services.AdminSalleService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BackofficeReclamationsController {

    private static final int DEFAULT_ROWS_PER_PAGE = 5;
    private static final int MAX_VISIBLE_PAGE_BUTTONS = 7;
    private static final String FILTER_ALL = "Tous les statuts";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private TableView<ReclamationSalle> reclamationsTable;

    @FXML
    private TableColumn<ReclamationSalle, String> salleColumn;

    @FXML
    private TableColumn<ReclamationSalle, String> declarantColumn;

    @FXML
    private TableColumn<ReclamationSalle, String> typeColumn;

    @FXML
    private TableColumn<ReclamationSalle, String> prioriteColumn;

    @FXML
    private TableColumn<ReclamationSalle, String> statutColumn;

    @FXML
    private TableColumn<ReclamationSalle, String> dateColumn;

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
    private Label newClaimsValueLabel;

    @FXML
    private Label analysisClaimsValueLabel;

    @FXML
    private Label maintenanceClaimsValueLabel;

    @FXML
    private Label highPriorityClaimsValueLabel;

    @FXML
    private Label selectionBadgeLabel;

    @FXML
    private ComboBox<Salle> roomComboBox;

    @FXML
    private TextField declarantField;

    @FXML
    private TextField seanceReferenceField;

    @FXML
    private ComboBox<String> typeProblemeComboBox;

    @FXML
    private ComboBox<String> prioriteComboBox;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label selectedClaimTitleLabel;

    @FXML
    private Label selectedClaimMetaLabel;

    @FXML
    private Label selectedClaimStatusLabel;

    @FXML
    private Label selectedClaimDescriptionLabel;

    @FXML
    private Label selectedClaimAdminHintLabel;

    @FXML
    private TextArea commentaireAdminArea;

    @FXML
    private Button startAnalysisButton;

    @FXML
    private Button convertMaintenanceButton;

    @FXML
    private Button resolveButton;

    @FXML
    private Button rejectButton;

    @FXML
    private Button resetSelectionButton;

    @FXML
    private Label feedbackLabel;

    private final AdminReclamationSalleService reclamationService = new AdminReclamationSalleService();
    private final AdminSalleService salleService = new AdminSalleService();
    private final ObservableList<Salle> salles = FXCollections.observableArrayList();
    private final ObservableList<ReclamationSalle> reclamations = FXCollections.observableArrayList();
    private final FilteredList<ReclamationSalle> filteredReclamations = new FilteredList<>(reclamations, reclamation -> true);
    private final ObservableList<ReclamationSalle> displayedReclamations = FXCollections.observableArrayList();
    private int currentPageIndex;
    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;

    @FXML
    private void initialize() {
        configureTable();
        configureCreationForm();
        configurePagination();

        reclamationsTable.setItems(displayedReclamations);
        reclamationsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateDetails(newValue));
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        hideFeedback();
        clearCreationForm();
        clearDetails();
        loadData();
    }

    @FXML
    private void handleRefresh() {
        hideFeedback();
        if (loadData()) {
            showFeedback("Les reclamations ont ete rechargees.", true);
        }
    }

    @FXML
    private void handleCreateReclamation() {
        hideFeedback();

        try {
            ReclamationSalle reclamation = buildReclamation();
            reclamationService.add(reclamation);

            if (!loadData()) {
                return;
            }

            clearCreationForm();
            selectReclamationById(reclamation.getIdReclamation());
            showFeedback("La reclamation a ete enregistree.", true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Creation impossible : " + resolveMessage(exception), false);
        }
    }

    @FXML
    private void handleStartAnalysis() {
        processSelectedReclamation("La reclamation est passee en analyse.", (id, commentaire) -> reclamationService.demarrerAnalyse(id, commentaire));
    }

    @FXML
    private void handleConvertToMaintenance() {
        processSelectedReclamation(
            "La reclamation a ete convertie en maintenance. Vous pouvez poursuivre dans l'ecran Maintenance.",
            (id, commentaire) -> reclamationService.convertirEnMaintenance(id, commentaire)
        );
    }

    @FXML
    private void handleResolve() {
        processSelectedReclamation("La reclamation a ete marquee comme resolue.", (id, commentaire) -> reclamationService.marquerResolue(id, commentaire));
    }

    @FXML
    private void handleReject() {
        processSelectedReclamation("La reclamation a ete rejetee.", (id, commentaire) -> reclamationService.rejeter(id, commentaire));
    }

    @FXML
    private void handleResetSelection() {
        reclamationsTable.getSelectionModel().clearSelection();
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
        if (currentPageIndex >= getTotalPages(filteredReclamations.size()) - 1) {
            return;
        }

        currentPageIndex++;
        refreshTablePage();
    }

    private void configureTable() {
        salleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getNomSalle())));
        declarantColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getNomDeclarant())));
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatOptionalText(cellData.getValue().getTypeProbleme())));
        prioriteColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatPriorite(cellData.getValue().getPriorite())));
        statutColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatStatut(cellData.getValue().getStatut())));
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatDate(cellData.getValue().getDateReclamation())));
    }

    private void configureCreationForm() {
        roomComboBox.setItems(salles);
        roomComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Salle salle) {
                return salle == null ? "" : salle.getNom();
            }

            @Override
            public Salle fromString(String string) {
                return null;
            }
        });

        typeProblemeComboBox.getItems().setAll(reclamationService.getAvailableTypesProbleme());
        prioriteComboBox.getItems().setAll(reclamationService.getAvailablePriorites());
        statusFilterComboBox.getItems().setAll(FILTER_ALL);
        statusFilterComboBox.getItems().addAll(reclamationService.getAvailableStatuts());
        statusFilterComboBox.setValue(FILTER_ALL);
    }

    private void configurePagination() {
        pageSizeComboBox.getItems().setAll(5, 10, 15, 20);
        pageSizeComboBox.setValue(rowsPerPage);
        pageSizeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> handlePageSizeChange(newValue));
    }

    private boolean loadData() {
        Integer selectedReclamationId = getSelectedReclamationId();

        try {
            salles.setAll(salleService.getAll());
            reclamations.setAll(reclamationService.getAll());
            updateSummary();

            if (roomComboBox.getValue() == null && !salles.isEmpty()) {
                roomComboBox.setValue(salles.get(0));
            }

            if (selectedReclamationId != null && selectReclamationById(selectedReclamationId)) {
                return true;
            }

            refreshTablePage();
            clearDetails();
            return true;
        } catch (SQLException | IllegalStateException exception) {
            salles.clear();
            reclamations.clear();
            displayedReclamations.clear();
            currentPageIndex = 0;
            updateSummary();
            refreshPaginationControls(0, 0, 0, 0);
            clearDetails();
            showFeedback("Chargement impossible : " + resolveMessage(exception), false);
            return false;
        }
    }

    private ReclamationSalle buildReclamation() {
        Salle selectedSalle = roomComboBox.getValue();
        if (selectedSalle == null) {
            throw new IllegalArgumentException("La salle concernee est obligatoire.");
        }

        return new ReclamationSalle(
            0,
            selectedSalle.getIdSalle(),
            selectedSalle.getNom(),
            requireText(declarantField.getText(), "Le nom du declarant est obligatoire."),
            trimToNull(seanceReferenceField.getText()),
            requireText(typeProblemeComboBox.getValue(), "Le type de probleme est obligatoire."),
            requireText(prioriteComboBox.getValue(), "La priorite est obligatoire."),
            "nouvelle",
            requireText(descriptionArea.getText(), "La description du signalement est obligatoire."),
            null,
            LocalDateTime.now(),
            null
        );
    }

    private void clearCreationForm() {
        if (!salles.isEmpty()) {
            roomComboBox.setValue(salles.get(0));
        }
        declarantField.clear();
        seanceReferenceField.clear();
        if (!typeProblemeComboBox.getItems().isEmpty()) {
            typeProblemeComboBox.setValue(typeProblemeComboBox.getItems().get(0));
        }
        if (!prioriteComboBox.getItems().isEmpty()) {
            prioriteComboBox.setValue(prioriteComboBox.getItems().get(1 < prioriteComboBox.getItems().size() ? 1 : 0));
        }
        descriptionArea.clear();
    }

    private void processSelectedReclamation(String successMessage, ReclamationAction action) {
        hideFeedback();

        ReclamationSalle selectedReclamation = reclamationsTable.getSelectionModel().getSelectedItem();
        if (selectedReclamation == null) {
            showFeedback("Selectionnez une reclamation avant de lancer cette action.", false);
            return;
        }

        try {
            action.execute(selectedReclamation.getIdReclamation(), trimToNull(commentaireAdminArea.getText()));

            if (!loadData()) {
                return;
            }

            selectReclamationById(selectedReclamation.getIdReclamation());
            showFeedback(successMessage, true);
        } catch (IllegalArgumentException | SQLException | IllegalStateException exception) {
            showFeedback("Traitement impossible : " + resolveMessage(exception), false);
        }
    }

    private void applyFilters() {
        Integer selectedReclamationId = getSelectedReclamationId();
        String normalizedSearch = normalize(searchField.getText());
        String selectedStatus = statusFilterComboBox.getValue();

        filteredReclamations.setPredicate(reclamation -> matchesFilters(reclamation, normalizedSearch, selectedStatus));

        if (selectedReclamationId != null && selectReclamationById(selectedReclamationId)) {
            return;
        }

        currentPageIndex = 0;
        refreshTablePage();
        clearDetails();
    }

    private boolean matchesFilters(ReclamationSalle reclamation, String normalizedSearch, String selectedStatus) {
        if (!normalizedSearch.isBlank() && !matchesSearch(reclamation, normalizedSearch)) {
            return false;
        }

        if (selectedStatus == null || FILTER_ALL.equals(selectedStatus)) {
            return true;
        }

        return normalize(reclamation.getStatut()).equals(normalize(selectedStatus));
    }

    private boolean matchesSearch(ReclamationSalle reclamation, String normalizedSearch) {
        return contains(reclamation.getNomSalle(), normalizedSearch)
            || contains(reclamation.getNomDeclarant(), normalizedSearch)
            || contains(reclamation.getReferenceSeance(), normalizedSearch)
            || contains(reclamation.getTypeProbleme(), normalizedSearch)
            || contains(reclamation.getPriorite(), normalizedSearch)
            || contains(reclamation.getStatut(), normalizedSearch)
            || contains(reclamation.getDescription(), normalizedSearch);
    }

    private void populateDetails(ReclamationSalle reclamation) {
        if (reclamation == null) {
            clearDetails();
            return;
        }

        selectedClaimTitleLabel.setText(formatOptionalText(reclamation.getNomSalle()));
        selectedClaimMetaLabel.setText(buildMetaLine(reclamation));
        selectedClaimStatusLabel.setText(formatStatut(reclamation.getStatut()));
        selectedClaimDescriptionLabel.setText(formatOptionalText(reclamation.getDescription()));
        selectedClaimAdminHintLabel.setText(buildHint(reclamation));
        commentaireAdminArea.setText(defaultString(reclamation.getCommentaireAdmin()));

        commentaireAdminArea.setDisable(false);
        updateActionAvailability(reclamation);
        updateSelectionBadge(reclamation);
    }

    private void clearDetails() {
        selectedClaimTitleLabel.setText("Aucune reclamation selectionnee");
        selectedClaimMetaLabel.setText("Selectionnez une ligne pour consulter le signalement et le traiter.");
        selectedClaimStatusLabel.setText("Aucun statut");
        selectedClaimDescriptionLabel.setText("La description du probleme apparaitra ici.");
        selectedClaimAdminHintLabel.setText("Vous pourrez ajouter un commentaire admin puis lancer une action de traitement.");
        commentaireAdminArea.clear();
        commentaireAdminArea.setDisable(true);
        startAnalysisButton.setDisable(true);
        convertMaintenanceButton.setDisable(true);
        resolveButton.setDisable(true);
        rejectButton.setDisable(true);
        resetSelectionButton.setDisable(true);
        updateSelectionBadge(null);
    }

    private void updateActionAvailability(ReclamationSalle reclamation) {
        String statut = normalize(reclamation.getStatut());
        boolean terminal = "convertie en maintenance".equals(statut) || "resolue".equals(statut) || "rejetee".equals(statut);

        startAnalysisButton.setDisable(terminal || "en cours d'analyse".equals(statut));
        convertMaintenanceButton.setDisable(terminal);
        resolveButton.setDisable(terminal);
        rejectButton.setDisable(terminal);
        resetSelectionButton.setDisable(false);
    }

    private void updateSummary() {
        long nouvelles = reclamations.stream()
            .filter(reclamation -> "nouvelle".equals(normalize(reclamation.getStatut())))
            .count();

        long analyses = reclamations.stream()
            .filter(reclamation -> "en cours d'analyse".equals(normalize(reclamation.getStatut())))
            .count();

        long converties = reclamations.stream()
            .filter(reclamation -> "convertie en maintenance".equals(normalize(reclamation.getStatut())))
            .count();

        long prioritaires = reclamations.stream()
            .filter(reclamation -> {
                String priorite = normalize(reclamation.getPriorite());
                return "haute".equals(priorite) || "critique".equals(priorite);
            })
            .count();

        newClaimsValueLabel.setText(String.valueOf(nouvelles));
        analysisClaimsValueLabel.setText(String.valueOf(analyses));
        maintenanceClaimsValueLabel.setText(String.valueOf(converties));
        highPriorityClaimsValueLabel.setText(String.valueOf(prioritaires));
    }

    private void updateSelectionBadge(ReclamationSalle reclamation) {
        if (reclamation != null) {
            selectionBadgeLabel.setText(formatOptionalText(reclamation.getNomSalle()) + " | " + formatStatut(reclamation.getStatut()));
            return;
        }

        if (filteredReclamations.isEmpty()) {
            selectionBadgeLabel.setText("Aucune reclamation visible");
            return;
        }

        if (!normalize(searchField.getText()).isBlank() || !FILTER_ALL.equals(statusFilterComboBox.getValue())) {
            selectionBadgeLabel.setText(filteredReclamations.size() + " reclamation(s) correspondent au filtre");
            return;
        }

        selectionBadgeLabel.setText("Nouveau signalement pret a etre enregistre");
    }

    private boolean selectReclamationById(int idReclamation) {
        for (int index = 0; index < filteredReclamations.size(); index++) {
            ReclamationSalle reclamation = filteredReclamations.get(index);
            if (reclamation.getIdReclamation() == idReclamation) {
                currentPageIndex = index / rowsPerPage;
                refreshTablePage();

                for (ReclamationSalle visibleReclamation : displayedReclamations) {
                    if (visibleReclamation.getIdReclamation() == idReclamation) {
                        reclamationsTable.getSelectionModel().select(visibleReclamation);
                        reclamationsTable.scrollTo(visibleReclamation);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Integer getSelectedReclamationId() {
        ReclamationSalle selectedReclamation = reclamationsTable.getSelectionModel().getSelectedItem();
        return selectedReclamation == null ? null : selectedReclamation.getIdReclamation();
    }

    private void refreshTablePage() {
        int filteredSize = filteredReclamations.size();
        int totalPages = getTotalPages(filteredSize);

        if (totalPages == 0) {
            currentPageIndex = 0;
            displayedReclamations.clear();
            refreshPaginationControls(0, 0, 0, 0);
            return;
        }

        currentPageIndex = Math.max(0, Math.min(currentPageIndex, totalPages - 1));

        int fromIndex = currentPageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredSize);

        displayedReclamations.setAll(filteredReclamations.subList(fromIndex, toIndex));
        refreshPaginationControls(filteredSize, fromIndex, toIndex, totalPages);
    }

    private void refreshPaginationControls(int filteredSize, int fromIndex, int toIndex, int totalPages) {
        if (filteredSize == 0) {
            paginationSummaryLabel.setText("Aucune reclamation a afficher");
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
            return "1 reclamation affichee";
        }
        return startIndex + " a " + endIndex + " sur " + totalItems + " reclamations";
    }

    private void handlePageSizeChange(Integer newValue) {
        if (newValue == null || newValue <= 0 || newValue == rowsPerPage) {
            return;
        }

        Integer selectedReclamationId = getSelectedReclamationId();
        rowsPerPage = newValue;

        if (selectedReclamationId != null && selectReclamationById(selectedReclamationId)) {
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

    private String buildMetaLine(ReclamationSalle reclamation) {
        StringBuilder builder = new StringBuilder();
        builder.append("Declarant : ").append(formatOptionalText(reclamation.getNomDeclarant()));
        builder.append(" | Priorite : ").append(formatPriorite(reclamation.getPriorite()));
        builder.append(" | Recue le ").append(formatDate(reclamation.getDateReclamation()));

        if (trimToNull(reclamation.getReferenceSeance()) != null) {
            builder.append(" | Seance : ").append(reclamation.getReferenceSeance().trim());
        }

        return builder.toString();
    }

    private String buildHint(ReclamationSalle reclamation) {
        if (reclamation.getDateTraitement() != null) {
            return "Dernier traitement enregistre le " + formatDate(reclamation.getDateTraitement()) + ".";
        }

        return "Ajoutez un commentaire admin puis choisissez l'action adapte au signalement.";
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? "Non renseignee" : DATE_FORMATTER.format(dateTime);
    }

    private String formatStatut(String statut) {
        return formatLabel(statut);
    }

    private String formatPriorite(String priorite) {
        return formatLabel(priorite);
    }

    private String formatOptionalText(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "Non renseigne" : normalized;
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

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String requireText(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
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

    @FunctionalInterface
    private interface ReclamationAction {
        void execute(int idReclamation, String commentaireAdmin) throws SQLException;
    }
}
