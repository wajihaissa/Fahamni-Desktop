package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.ActivityLog;
import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.ActivityLogService;
import tn.esprit.fahamni.services.AdminArticlesService;
import tn.esprit.fahamni.services.NotificationService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BackofficeArticlesController {

    private final AdminArticlesService service      = new AdminArticlesService();
    private final NotificationService  notifService = new NotificationService();
    private final ActivityLogService   logService   = new ActivityLogService();
    private static final DateTimeFormatter FMT    = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label  pendingCountLabel;
    @FXML private Label  approvedCountLabel;
    @FXML private Label  deletedCountLabel;
    @FXML private HBox   notifBanner;
    @FXML private Label  notifLabel;
    @FXML private Label  feedbackLabel;
    @FXML private Button btnTous;
    @FXML private HBox      bulkBar;
    @FXML private Label     selectionCountLabel;
    @FXML private TextField authorSearchField;

    /** Liste complète en cours (avant filtre auteur) */
    private List<Blog> currentFullList = new java.util.ArrayList<>();

    @FXML private TableView<Blog>            articlesTable;
    @FXML private TableColumn<Blog, Boolean> colSelect;
    @FXML private TableColumn<Blog, String>  colTitre;
    @FXML private TableColumn<Blog, String>  colAuteur;
    @FXML private TableColumn<Blog, String>  colCategorie;
    @FXML private TableColumn<Blog, String>  colDate;
    @FXML private TableColumn<Blog, String>  colStatut;
    @FXML private TableColumn<Blog, Void>    colActions;

    @FXML private TableView<ActivityLog>            activityTable;
    @FXML private TableColumn<ActivityLog, String>  colLogDate;
    @FXML private TableColumn<ActivityLog, String>  colLogAdmin;
    @FXML private TableColumn<ActivityLog, String>  colLogAction;
    @FXML private TableColumn<ActivityLog, String>  colLogTitle;
    @FXML private TableColumn<ActivityLog, String>  colLogAuthor;

    /** Map blogId → état checkbox */
    private final Map<Integer, SimpleBooleanProperty> checkMap = new HashMap<>();

    @FXML
    private void initialize() {
        articlesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setupColumns();
        setupActivityColumns();
        loadAll();

        // Filtre temps réel par auteur
        authorSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String kw = newVal == null ? "" : newVal.trim().toLowerCase();
            if (kw.isEmpty()) {
                displayArticles(currentFullList, false);
            } else {
                List<Blog> filtered = currentFullList.stream()
                    .filter(b -> b.getPublishedBy() != null &&
                                 b.getPublishedBy().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
                displayArticles(filtered, false);
            }
        });
    }

    // ─── filtres ──────────────────────────────────────────────────────────────

    @FXML private void filterTous()     { displayArticles(service.getAllArticles()); }
    @FXML private void filterPending()  { displayArticles(service.getPendingArticles()); }
    @FXML private void filterApproved() { displayArticles(service.getApprovedArticles()); }
    @FXML private void filterDeleted()  { displayArticles(service.getDeletedArticles()); }
    @FXML private void handleRefresh()  { loadAll(); showFeedback("Liste actualisee.", true); }

    // ─── chargement ───────────────────────────────────────────────────────────

    private void loadAll() {
        refreshCounters();
        displayArticles(service.getAllArticles());
        updateNotifBanner();
        loadActivityLog();
    }

    private void loadActivityLog() {
        activityTable.getItems().setAll(logService.getRecent(20));
    }

    private void refreshCounters() {
        pendingCountLabel .setText(String.valueOf(service.countByStatus("pending")));
        approvedCountLabel.setText(String.valueOf(service.countByStatus("published")));
        deletedCountLabel .setText(String.valueOf(service.countByStatus("deleted")));
    }

    private void updateNotifBanner() {
        List<Notification> notifs = notifService.getUnreadForAdmin();
        int pending = service.countByStatus("pending");

        if (!notifs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Notification n : notifs) sb.append(n.getMessage()).append("\n");
            notifLabel.setText(sb.toString().trim());
            notifBanner.setVisible(true);
            notifBanner.setManaged(true);
            notifService.markAllReadForAdmin();
        } else if (pending > 0) {
            notifLabel.setText(pending + " article(s) en attente d'approbation.");
            notifBanner.setVisible(true);
            notifBanner.setManaged(true);
        } else {
            notifBanner.setVisible(false);
            notifBanner.setManaged(false);
        }
    }

    private void displayArticles(List<Blog> blogs) {
        displayArticles(blogs, true);
    }

    private void displayArticles(List<Blog> blogs, boolean updateFullList) {
        if (updateFullList) currentFullList = blogs;
        checkMap.clear();
        articlesTable.getItems().setAll(blogs);
        refreshBulkBar();
    }

    private void refreshBulkBar() {
        long count = checkMap.values().stream().filter(SimpleBooleanProperty::get).count();
        if (count > 0) {
            selectionCountLabel.setText(count + " sélectionné(s)");
            bulkBar.setVisible(true);
            bulkBar.setManaged(true);
        } else {
            bulkBar.setVisible(false);
            bulkBar.setManaged(false);
        }
    }

    @FXML
    private void handleBulkApprove() {
        List<Integer> ids = getCheckedIds();
        if (ids.isEmpty()) return;
        ids.forEach(service::approveArticle);
        loadAll();
        showFeedback("✅  " + ids.size() + " article(s) approuve(s).", true);
    }

    @FXML
    private void handleBulkDelete() {
        List<Integer> ids = getCheckedIds();
        if (ids.isEmpty()) return;
        ids.forEach(service::deleteArticle);
        loadAll();
        showFeedback("🗑  " + ids.size() + " article(s) supprime(s).", false);
    }

    private List<Integer> getCheckedIds() {
        return checkMap.entrySet().stream()
            .filter(e -> e.getValue().get())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    // ─── colonnes ─────────────────────────────────────────────────────────────

    private void setupColumns() {

        // Checkbox sélection
        colSelect.setCellValueFactory(c -> {
            int id = c.getValue().getId();
            checkMap.putIfAbsent(id, new SimpleBooleanProperty(false));
            return checkMap.get(id).asObject();
        });
        colSelect.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.setStyle("-fx-cursor: hand;");
                cb.setOnAction(e -> {
                    Blog b = getTableView().getItems().get(getIndex());
                    int id = b.getId();
                    checkMap.putIfAbsent(id, new SimpleBooleanProperty(false));
                    checkMap.get(id).set(cb.isSelected());
                    refreshBulkBar();
                });
            }
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Blog b = getTableView().getItems().get(getIndex());
                int id = b.getId();
                checkMap.putIfAbsent(id, new SimpleBooleanProperty(false));
                cb.setSelected(checkMap.get(id).get());
                setGraphic(cb);
            }
        });
        colSelect.setSortable(false);

        // Titre
        colTitre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitre()));
        colTitre.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 12;");
            }
        });

        // Auteur
        colAuteur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPublishedBy()));
        colAuteur.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("-fx-text-fill: #3a7bd5; -fx-font-size: 12;");
            }
        });

        // Catégorie — affichage lisible
        colCategorie.setCellValueFactory(c -> new SimpleStringProperty(prettyCat(c.getValue().getImage())));
        colCategorie.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.setPadding(new Insets(3, 10, 3, 10));
                badge.setStyle("-fx-background-color: " + catColor(item) + "; " +
                    "-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold; " +
                    "-fx-background-radius: 20;");
                setGraphic(badge);
                setText(null);
            }
        });

        // Date
        colDate.setCellValueFactory(c -> {
            String d = c.getValue().getCreatedAt() != null
                    ? c.getValue().getCreatedAt().format(FMT) : "-";
            return new SimpleStringProperty(d);
        });
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("-fx-text-fill: #64748b; -fx-font-size: 11;");
            }
        });

        // Statut — badge coloré
        colStatut.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(prettyStatus(item));
                badge.setPadding(new Insets(4, 12, 4, 12));
                badge.setStyle("-fx-background-color: " + statusBg(item) + "; " +
                    "-fx-text-fill: " + statusFg(item) + "; " +
                    "-fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 20;");
                setGraphic(badge);
                setText(null);
            }
        });

        // Actions — Voir / Approuver / Supprimer
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView    = new Button("👁  Voir");
            private final Button btnApprove = new Button("✔  Approuver");
            private final Button btnDelete  = new Button("✕  Supprimer");
            private final HBox   box        = new HBox(6, btnView, btnApprove, btnDelete);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnView.setStyle(
                    "-fx-background-color: #3a7bd5; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-padding: 5 12; " +
                    "-fx-cursor: hand; -fx-font-size: 11; -fx-font-weight: bold;");
                btnApprove.setStyle(
                    "-fx-background-color: #10b981; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-padding: 5 12; " +
                    "-fx-cursor: hand; -fx-font-size: 11; -fx-font-weight: bold;");
                btnDelete.setStyle(
                    "-fx-background-color: #ef4444; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-padding: 5 12; " +
                    "-fx-cursor: hand; -fx-font-size: 11; -fx-font-weight: bold;");

                btnView.setOnAction(e -> {
                    Blog b = getTableView().getItems().get(getIndex());
                    showPreviewPopup(b);
                });
                btnApprove.setOnAction(e -> {
                    Blog b = getTableView().getItems().get(getIndex());
                    if (b.getId() > 0) {
                        service.approveArticle(b.getId());
                        loadAll();
                        showFeedback("✅  Article \"" + b.getTitre() + "\" approuve et publie.", true);
                    }
                });
                btnDelete.setOnAction(e -> {
                    Blog b = getTableView().getItems().get(getIndex());
                    if (b.getId() > 0) {
                        service.deleteArticle(b.getId());
                        loadAll();
                        showFeedback("🗑  Article \"" + b.getTitre() + "\" supprime.", false);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    // ─── colonnes journal ─────────────────────────────────────────────────────

    private void setupActivityColumns() {
        colLogDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt().format(DT_FMT) : "—"));
        colLogDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("-fx-text-fill: #64748b; -fx-font-size: 11;");
            }
        });

        colLogAdmin.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAdminName()));
        colLogAdmin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("-fx-text-fill: #3a7bd5; -fx-font-weight: bold; -fx-font-size: 12;");
            }
        });

        colLogAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAction()));
        colLogAction.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                boolean approved = "Approuve".equalsIgnoreCase(item);
                Label badge = new Label(approved ? "✔  Approuvé" : "✕  Refusé");
                badge.setPadding(new Insets(3, 10, 3, 10));
                badge.setStyle(
                    "-fx-background-color: " + (approved ? "#ecfdf5" : "#fff1f2") + "; " +
                    "-fx-text-fill: " + (approved ? "#065f46" : "#991b1b") + "; " +
                    "-fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 20;");
                setGraphic(badge);
                setText(null);
            }
        });

        colLogTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArticleTitle()));
        colLogTitle.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("-fx-text-fill: #1e293b; -fx-font-size: 12;");
            }
        });

        colLogAuthor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArticleAuthor()));
        colLogAuthor.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("-fx-text-fill: #8e44ad; -fx-font-size: 12;");
            }
        });
    }

    // ─── popup aperçu ─────────────────────────────────────────────────────────

    private void showPreviewPopup(Blog blog) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Aperçu — " + blog.getTitre());
        stage.setResizable(true);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        // En-tête gradient
        VBox header = new VBox(6);
        header.setPadding(new Insets(20, 28, 18, 28));
        header.setStyle("-fx-background-color: linear-gradient(to right, #3a7bd5, #00d2ff);");

        Label titre = new Label(blog.getTitre());
        titre.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: white;");
        titre.setWrapText(true);

        HBox meta = new HBox(16);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label auteur = new Label("✍  " + (blog.getPublishedBy() != null ? blog.getPublishedBy() : "Anonyme"));
        auteur.setStyle("-fx-font-size: 12; -fx-text-fill: rgba(255,255,255,0.9);");
        Label cat = new Label("🏷  " + prettyCat(blog.getImage()));
        cat.setStyle("-fx-font-size: 12; -fx-text-fill: rgba(255,255,255,0.9);");
        Label date = new Label("📅  " + (blog.getCreatedAt() != null ? blog.getCreatedAt().format(FMT) : "-"));
        date.setStyle("-fx-font-size: 12; -fx-text-fill: rgba(255,255,255,0.9);");

        // Badge statut
        Label statut = new Label(prettyStatus(blog.getStatus()));
        statut.setPadding(new Insets(3, 10, 3, 10));
        statut.setStyle("-fx-background-color: " + statusBg(blog.getStatus()) + "; " +
            "-fx-text-fill: " + statusFg(blog.getStatus()) + "; " +
            "-fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 20;");

        meta.getChildren().addAll(auteur, cat, date, statut);
        header.getChildren().addAll(titre, meta);

        // Contenu scrollable
        TextArea content = new TextArea(blog.getContent() != null ? blog.getContent() : "");
        content.setEditable(false);
        content.setWrapText(true);
        content.setStyle(
            "-fx-background-color: white; -fx-border-width: 0; " +
            "-fx-font-size: 13; -fx-text-fill: #1e293b; -fx-padding: 20;");
        content.setPrefHeight(380);
        VBox.setVgrow(content, Priority.ALWAYS);

        // Pied de page avec boutons action
        HBox footer = new HBox(12);
        footer.setPadding(new Insets(14, 20, 14, 20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        Button btnApprove = new Button("✔  Approuver");
        btnApprove.setStyle(
            "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-padding: 8 20; -fx-cursor: hand;");
        btnApprove.setOnAction(e -> {
            if (blog.getId() > 0) {
                service.approveArticle(blog.getId());
                loadAll();
                showFeedback("✅  Article \"" + blog.getTitre() + "\" approuve et publie.", true);
            }
            stage.close();
        });

        Button btnDelete = new Button("✕  Refuser");
        btnDelete.setStyle(
            "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-padding: 8 20; -fx-cursor: hand;");
        btnDelete.setOnAction(e -> {
            if (blog.getId() > 0) {
                service.deleteArticle(blog.getId());
                loadAll();
                showFeedback("🗑  Article \"" + blog.getTitre() + "\" refuse.", false);
            }
            stage.close();
        });

        Button btnClose = new Button("Fermer");
        btnClose.setStyle(
            "-fx-background-color: #e2e8f0; -fx-text-fill: #475569; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-padding: 8 20; -fx-cursor: hand;");
        btnClose.setOnAction(e -> stage.close());

        // N'afficher Approuver/Refuser que si en attente
        if ("pending".equalsIgnoreCase(blog.getStatus())) {
            footer.getChildren().addAll(btnClose, btnDelete, btnApprove);
        } else {
            footer.getChildren().add(btnClose);
        }

        root.getChildren().addAll(header, content, footer);

        Scene scene = new Scene(root, 640, 520);
        stage.setScene(scene);
        stage.show();
    }

    // ─── helpers d'affichage ──────────────────────────────────────────────────

    private String prettyCat(String raw) {
        if (raw == null) return "Autre";
        switch (raw.toLowerCase()) {
            case "mathematics":
            case "math":       return "Mathematiques";
            case "science":
            case "physics":    return "Sciences";
            case "computer-science":
            case "informatique": return "Informatique";
            case "language":
            case "langue":     return "Langues";
            case "study-tips": return "Conseils";
            default:           return "Autre";
        }
    }

    private String catColor(String pretty) {
        switch (pretty) {
            case "Mathematiques": return "#3a7bd5";
            case "Sciences":      return "#f59e0b";
            case "Informatique":  return "#8b5cf6";
            case "Langues":       return "#f97316";
            case "Conseils":      return "#10b981";
            default:              return "#6b7280";
        }
    }

    private String prettyStatus(String s) {
        if (s == null) return "—";
        switch (s.toLowerCase()) {
            case "pending":   return "En attente";
            case "published": return "Approuve";
            case "deleted":
            case "rejected":  return "Supprime";
            default:          return s;
        }
    }

    private String statusBg(String s) {
        if (s == null) return "#e2e8f0";
        switch (s.toLowerCase()) {
            case "pending":   return "#fff8e1";
            case "published": return "#ecfdf5";
            case "deleted":
            case "rejected":  return "#fff1f2";
            default:          return "#f1f5f9";
        }
    }

    private String statusFg(String s) {
        if (s == null) return "#64748b";
        switch (s.toLowerCase()) {
            case "pending":   return "#92400e";
            case "published": return "#065f46";
            case "deleted":
            case "rejected":  return "#991b1b";
            default:          return "#475569";
        }
    }

    // ─── feedback ─────────────────────────────────────────────────────────────

    private void showFeedback(String msg, boolean success) {
        feedbackLabel.setText(msg);
        feedbackLabel.setStyle(
            "-fx-font-size: 13; -fx-padding: 10 16; -fx-background-radius: 8; " +
            "-fx-font-weight: bold; " +
            (success
                ? "-fx-background-color: #ecfdf5; -fx-text-fill: #065f46; -fx-border-color: #10b981; -fx-border-radius: 8; -fx-border-width: 1;"
                : "-fx-background-color: #fff1f2; -fx-text-fill: #991b1b; -fx-border-color: #ef4444; -fx-border-radius: 8; -fx-border-width: 1;")
        );
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }
}
