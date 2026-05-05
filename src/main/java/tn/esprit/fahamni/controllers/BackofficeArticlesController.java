package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tn.esprit.fahamni.Models.ActivityLog;
import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.ActivityLogService;
import tn.esprit.fahamni.services.AdminArticlesService;
import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.utils.BackOfficeUiTheme;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BackofficeArticlesController {

    private final AdminArticlesService service = new AdminArticlesService();
    private final NotificationService notifService = new NotificationService();
    private final ActivityLogService logService = new ActivityLogService();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label pendingCountLabel;
    @FXML private Label approvedCountLabel;
    @FXML private Label deletedCountLabel;
    @FXML private HBox notifBanner;
    @FXML private Label notifLabel;
    @FXML private Label feedbackLabel;
    @FXML private Button btnTous;
    @FXML private Button btnPending;
    @FXML private Button btnApproved;
    @FXML private Button btnDeleted;
    @FXML private HBox bulkBar;
    @FXML private Label selectionCountLabel;
    @FXML private TextField authorSearchField;

    private List<Blog> currentFullList = new ArrayList<>();

    @FXML private TableView<Blog> articlesTable;
    @FXML private TableColumn<Blog, Boolean> colSelect;
    @FXML private TableColumn<Blog, String> colTitre;
    @FXML private TableColumn<Blog, String> colAuteur;
    @FXML private TableColumn<Blog, String> colCategorie;
    @FXML private TableColumn<Blog, String> colDate;
    @FXML private TableColumn<Blog, String> colStatut;
    @FXML private TableColumn<Blog, Void> colActions;

    @FXML private TableView<ActivityLog> activityTable;
    @FXML private TableColumn<ActivityLog, String> colLogDate;
    @FXML private TableColumn<ActivityLog, String> colLogAdmin;
    @FXML private TableColumn<ActivityLog, String> colLogAction;
    @FXML private TableColumn<ActivityLog, String> colLogTitle;
    @FXML private TableColumn<ActivityLog, String> colLogAuthor;

    private final Map<Integer, SimpleBooleanProperty> checkMap = new HashMap<>();

    @FXML
    private void initialize() {
        articlesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setupColumns();
        setupActivityColumns();
        setActiveFilterButton(btnTous);
        loadAll();

        authorSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String keyword = newVal == null ? "" : newVal.trim().toLowerCase();
            if (keyword.isEmpty()) {
                displayArticles(currentFullList, false);
                return;
            }

            List<Blog> filtered = currentFullList.stream()
                .filter(blog -> blog.getPublishedBy() != null && blog.getPublishedBy().toLowerCase().contains(keyword))
                .collect(Collectors.toList());
            displayArticles(filtered, false);
        });
    }

    @FXML
    private void filterTous() {
        setActiveFilterButton(btnTous);
        displayArticles(service.getAllArticles());
    }

    @FXML
    private void filterPending() {
        setActiveFilterButton(btnPending);
        displayArticles(service.getPendingArticles());
    }

    @FXML
    private void filterApproved() {
        setActiveFilterButton(btnApproved);
        displayArticles(service.getApprovedArticles());
    }

    @FXML
    private void filterDeleted() {
        setActiveFilterButton(btnDeleted);
        displayArticles(service.getDeletedArticles());
    }

    @FXML
    private void handleRefresh() {
        loadAll();
        showFeedback("Liste actualisee.", true);
    }

    private void loadAll() {
        refreshCounters();
        setActiveFilterButton(btnTous);
        displayArticles(service.getAllArticles());
        updateNotifBanner();
        loadActivityLog();
    }

    private void loadActivityLog() {
        activityTable.getItems().setAll(logService.getRecent(20));
    }

    private void refreshCounters() {
        pendingCountLabel.setText(String.valueOf(service.countByStatus("pending")));
        approvedCountLabel.setText(String.valueOf(service.countByStatus("published")));
        deletedCountLabel.setText(String.valueOf(service.countByStatus("deleted")));
    }

    private void updateNotifBanner() {
        // Exclure les notifications de commentaires bloqués (elles appartiennent à la page Commentaires)
        List<Notification> notifs = notifService.getUnreadForAdmin().stream()
            .filter(n -> n.getMessage() == null || !n.getMessage().contains("Commentaire bloqué"))
            .collect(java.util.stream.Collectors.toList());
        int pending = service.countByStatus("pending");

        if (!notifs.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Notification notification : notifs) {
                builder.append(notification.getMessage()).append("\n");
            }
            notifLabel.setText(builder.toString().trim());
            notifBanner.setVisible(true);
            notifBanner.setManaged(true);
            return;
        }

        if (pending > 0) {
            notifLabel.setText(pending + " article(s) en attente d'approbation.");
            notifBanner.setVisible(true);
            notifBanner.setManaged(true);
            return;
        }

        notifBanner.setVisible(false);
        notifBanner.setManaged(false);
    }

    private void displayArticles(List<Blog> blogs) {
        displayArticles(blogs, true);
    }

    private void displayArticles(List<Blog> blogs, boolean updateFullList) {
        if (updateFullList) {
            currentFullList = blogs;
        }
        checkMap.clear();
        articlesTable.getItems().setAll(blogs);
        refreshBulkBar();
    }

    private void refreshBulkBar() {
        long count = checkMap.values().stream().filter(SimpleBooleanProperty::get).count();
        if (count > 0) {
            selectionCountLabel.setText(count + " selectionne(s)");
            bulkBar.setVisible(true);
            bulkBar.setManaged(true);
            return;
        }

        bulkBar.setVisible(false);
        bulkBar.setManaged(false);
    }

    @FXML
    private void handleBulkApprove() {
        List<Integer> ids = getCheckedIds();
        if (ids.isEmpty()) {
            return;
        }

        ids.forEach(service::approveArticle);
        loadAll();
        showFeedback("✅  " + ids.size() + " article(s) approuve(s).", true);
    }

    @FXML
    private void handleBulkDelete() {
        List<Integer> ids = getCheckedIds();
        if (ids.isEmpty()) {
            return;
        }

        if (!confirm(
            "Supprimer la selection ?",
            "Vous allez supprimer " + ids.size() + " article(s).\nCette action est irreversible. Confirmer ?"
        )) {
            return;
        }

        ids.forEach(service::deleteArticle);
        loadAll();
        showFeedback("🗑  " + ids.size() + " article(s) supprime(s).", false);
    }

    private List<Integer> getCheckedIds() {
        return checkMap.entrySet().stream()
            .filter(entry -> entry.getValue().get())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private void setupColumns() {
        colSelect.setCellValueFactory(cell -> {
            int id = cell.getValue().getId();
            checkMap.putIfAbsent(id, new SimpleBooleanProperty(false));
            return checkMap.get(id).asObject();
        });
        colSelect.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkbox = new CheckBox();

            {
                checkbox.getStyleClass().setAll("backoffice-check", "backoffice-articles-check");
                checkbox.setOnAction(event -> {
                    Blog blog = getTableView().getItems().get(getIndex());
                    int id = blog.getId();
                    checkMap.putIfAbsent(id, new SimpleBooleanProperty(false));
                    checkMap.get(id).set(checkbox.isSelected());
                    refreshBulkBar();
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                if (empty) {
                    return;
                }

                Blog blog = getTableView().getItems().get(getIndex());
                int id = blog.getId();
                checkMap.putIfAbsent(id, new SimpleBooleanProperty(false));
                checkbox.setSelected(checkMap.get(id).get());
                setGraphic(checkbox);
                setAlignment(Pos.CENTER);
            }
        });
        colSelect.setSortable(false);

        colTitre.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTitre()));
        colTitre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-title-cell");
            }
        });

        colAuteur.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPublishedBy()));
        colAuteur.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-author-cell");
            }
        });

        colCategorie.setCellValueFactory(cell -> new SimpleStringProperty(prettyCat(cell.getValue().getImage())));
        colCategorie.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                if (empty || item == null) {
                    return;
                }

                Label badge = buildBadge(item, "backoffice-badge", "backoffice-articles-category-chip", categoryClass(item));
                setGraphic(badge);
            }
        });

        colDate.setCellValueFactory(cell -> {
            String formattedDate = cell.getValue().getCreatedAt() != null
                ? cell.getValue().getCreatedAt().format(FMT)
                : "-";
            return new SimpleStringProperty(formattedDate);
        });
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-date-cell");
            }
        });

        colStatut.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                if (empty || item == null) {
                    return;
                }

                Label badge = buildBadge(prettyStatus(item), "backoffice-status-chip", "backoffice-articles-status-chip", statusClass(item));
                setGraphic(badge);
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView = new Button("Voir");
            private final Button btnApprove = new Button("Approuver");
            private final Button btnDelete = new Button("Supprimer");
            private final HBox actionsBox = new HBox(6.0, btnView, btnApprove, btnDelete);

            {
                actionsBox.setAlignment(Pos.CENTER_LEFT);

                btnView.getStyleClass().setAll("backoffice-table-action-button", "backoffice-articles-action-button");
                btnApprove.getStyleClass().setAll("action-button", "accept", "backoffice-articles-action-button");
                btnDelete.getStyleClass().setAll("backoffice-table-action-button", "danger", "backoffice-articles-action-button");

                btnView.setOnAction(event -> {
                    Blog blog = getTableView().getItems().get(getIndex());
                    showPreviewPopup(blog);
                });

                btnApprove.setOnAction(event -> {
                    Blog blog = getTableView().getItems().get(getIndex());
                    if (blog.getId() > 0) {
                        service.approveArticle(blog.getId());
                        loadAll();
                        showFeedback("✅  Article \"" + blog.getTitre() + "\" approuve et publie.", true);
                    }
                });

                btnDelete.setOnAction(event -> {
                    Blog blog = getTableView().getItems().get(getIndex());
                    if (blog.getId() > 0 && confirm(
                        "Supprimer l'article ?",
                        "Etes-vous sur de vouloir supprimer :\n\"" + blog.getTitre() + "\" ?"
                    )) {
                        service.deleteArticle(blog.getId());
                        loadAll();
                        showFeedback("🗑  Article \"" + blog.getTitre() + "\" supprime.", false);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty ? null : actionsBox);

                if (empty) {
                    return;
                }

                Blog blog = getTableView().getItems().get(getIndex());
                boolean pending = "pending".equalsIgnoreCase(blog.getStatus());
                btnApprove.setManaged(pending);
                btnApprove.setVisible(pending);
            }
        });
    }

    private void setupActivityColumns() {
        colLogDate.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getCreatedAt() != null ? cell.getValue().getCreatedAt().format(DT_FMT) : "-"
        ));
        colLogDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-date-cell");
            }
        });

        colLogAdmin.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAdminName()));
        colLogAdmin.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-log-admin-cell");
            }
        });

        colLogAction.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAction()));
        colLogAction.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                if (empty || item == null) {
                    return;
                }

                boolean approved = "Approuve".equalsIgnoreCase(item);
                Label badge = buildBadge(
                    approved ? "Approuve" : "Refuse",
                    "backoffice-status-chip",
                    "backoffice-articles-log-status",
                    approved ? "approved" : "rejected"
                );
                setGraphic(badge);
            }
        });

        colLogTitle.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getArticleTitle()));
        colLogTitle.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-title-cell");
            }
        });

        colLogAuthor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getArticleAuthor()));
        colLogAuthor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-log-author-cell");
            }
        });
    }

    private void showPreviewPopup(Blog blog) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (articlesTable != null && articlesTable.getScene() != null) {
            stage.initOwner(articlesTable.getScene().getWindow());
        }
        stage.setTitle("Apercu - " + blog.getTitre());
        stage.setResizable(true);

        VBox root = new VBox();
        root.getStyleClass().add("backoffice-articles-preview-root");

        VBox header = new VBox(10.0);
        header.getStyleClass().add("backoffice-articles-preview-hero");

        Label titleLabel = new Label(blog.getTitre());
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("backoffice-articles-preview-title");

        HBox metaRow = new HBox(10.0);
        metaRow.getStyleClass().add("backoffice-articles-preview-meta");

        Label authorLabel = new Label("Par " + (blog.getPublishedBy() != null ? blog.getPublishedBy() : "Anonyme"));
        authorLabel.getStyleClass().add("backoffice-articles-preview-meta-text");

        Label dateLabel = new Label(blog.getCreatedAt() != null ? blog.getCreatedAt().format(FMT) : "-");
        dateLabel.getStyleClass().add("backoffice-articles-preview-meta-text");

        Label categoryBadge = buildBadge(prettyCat(blog.getImage()), "backoffice-badge", "backoffice-articles-category-chip", categoryClass(prettyCat(blog.getImage())));
        Label statusBadge = buildBadge(prettyStatus(blog.getStatus()), "backoffice-status-chip", "backoffice-articles-status-chip", statusClass(blog.getStatus()));

        metaRow.getChildren().addAll(authorLabel, dateLabel, categoryBadge, statusBadge);
        header.getChildren().addAll(titleLabel, metaRow);

        VBox articleBody = new VBox(10.0);
        articleBody.getStyleClass().add("backoffice-articles-preview-body");

        Label sectionTitle = new Label("Contenu");
        sectionTitle.getStyleClass().add("backoffice-section-title");

        Label contentLabel = new Label(blog.getContent() != null ? blog.getContent() : "");
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("backoffice-articles-preview-content");

        articleBody.getChildren().addAll(sectionTitle, contentLabel);

        ScrollPane scrollPane = new ScrollPane(articleBody);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("backoffice-articles-preview-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox footer = new HBox(10.0);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("backoffice-articles-preview-footer");

        Button closeButton = new Button("Fermer");
        closeButton.getStyleClass().setAll("backoffice-secondary-button", "backoffice-articles-preview-button");
        closeButton.setOnAction(event -> stage.close());

        if ("pending".equalsIgnoreCase(blog.getStatus())) {
            Button deleteButton = new Button("Refuser");
            deleteButton.getStyleClass().setAll("backoffice-danger-button", "backoffice-articles-preview-button");
            deleteButton.setOnAction(event -> {
                if (blog.getId() > 0 && confirm(
                    "Refuser l'article ?",
                    "Etes-vous sur de vouloir refuser :\n\"" + blog.getTitre() + "\" ?"
                )) {
                    service.deleteArticle(blog.getId());
                    loadAll();
                    showFeedback("🗑  Article \"" + blog.getTitre() + "\" refuse.", false);
                    stage.close();
                }
            });

            Button approveButton = new Button("Approuver");
            approveButton.getStyleClass().setAll("backoffice-primary-button", "backoffice-articles-preview-button");
            approveButton.setOnAction(event -> {
                if (blog.getId() > 0) {
                    service.approveArticle(blog.getId());
                    loadAll();
                    showFeedback("✅  Article \"" + blog.getTitre() + "\" approuve et publie.", true);
                }
                stage.close();
            });

            footer.getChildren().addAll(closeButton, deleteButton, approveButton);
        } else {
            footer.getChildren().add(closeButton);
        }

        root.getChildren().addAll(header, scrollPane, footer);

        Scene scene = new Scene(root, 720, 560);
        BackOfficeUiTheme.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    private String prettyCat(String raw) {
        if (raw == null) {
            return "Autre";
        }

        switch (raw.toLowerCase()) {
            case "mathematics":
            case "math":
                return "Mathematiques";
            case "science":
            case "physics":
                return "Sciences";
            case "computer-science":
            case "informatique":
                return "Informatique";
            case "language":
            case "langue":
                return "Langues";
            case "study-tips":
                return "Conseils";
            default:
                return "Autre";
        }
    }

    private String categoryClass(String category) {
        switch (category) {
            case "Mathematiques":
                return "category-math";
            case "Sciences":
                return "category-science";
            case "Informatique":
                return "category-tech";
            case "Langues":
                return "category-language";
            case "Conseils":
                return "category-advice";
            default:
                return "category-other";
        }
    }

    private String prettyStatus(String status) {
        if (status == null) {
            return "-";
        }

        switch (status.toLowerCase()) {
            case "pending":
                return "En attente";
            case "published":
                return "Approuve";
            case "deleted":
            case "rejected":
                return "Supprime";
            default:
                return status;
        }
    }

    private String statusClass(String status) {
        if (status == null) {
            return "status-neutral";
        }

        switch (status.toLowerCase()) {
            case "pending":
                return "status-pending";
            case "published":
                return "status-published";
            case "deleted":
            case "rejected":
                return "status-deleted";
            default:
                return "status-neutral";
        }
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        ButtonType yes = new ButtonType("Oui, supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, cancel);
        return alert.showAndWait().orElse(cancel) == yes;
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error", "backoffice-articles-feedback");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void setActiveFilterButton(Button activeButton) {
        for (Button button : List.of(btnTous, btnPending, btnApproved, btnDeleted)) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }

        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    private void updateTextCell(TableCell<?, ?> cell, String item, boolean empty, String styleClass) {
        cell.setGraphic(null);
        cell.getStyleClass().remove(styleClass);

        if (empty || item == null) {
            cell.setText(null);
            return;
        }

        cell.setText(item);
        if (!cell.getStyleClass().contains(styleClass)) {
            cell.getStyleClass().add(styleClass);
        }
    }

    private Label buildBadge(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }
}
