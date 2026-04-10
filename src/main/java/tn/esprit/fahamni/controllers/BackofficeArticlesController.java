package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.AdminArticlesService;
import tn.esprit.fahamni.services.NotificationService;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class BackofficeArticlesController {

    private final AdminArticlesService service = new AdminArticlesService();
    private final NotificationService notifService = new NotificationService();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label pendingCountLabel;
    @FXML private Label approvedCountLabel;
    @FXML private Label deletedCountLabel;
    @FXML private HBox  notifBanner;
    @FXML private Label notifLabel;
    @FXML private Label feedbackLabel;
    @FXML private Button btnTous;

    @FXML private TableView<Blog>           articlesTable;
    @FXML private TableColumn<Blog, String> colTitre;
    @FXML private TableColumn<Blog, String> colAuteur;
    @FXML private TableColumn<Blog, String> colCategorie;
    @FXML private TableColumn<Blog, String> colDate;
    @FXML private TableColumn<Blog, String> colStatut;
    @FXML private TableColumn<Blog, Void>   colActions;

    @FXML
    private void initialize() {
        articlesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setupColumns();
        loadAll();
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
        articlesTable.getItems().setAll(blogs);
    }

    // ─── colonnes ─────────────────────────────────────────────────────────────

    private void setupColumns() {

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

        // Actions — Approuver / Supprimer
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnApprove = new Button("✔  Approuver");
            private final Button btnDelete  = new Button("✕  Supprimer");
            private final HBox   box        = new HBox(6, btnApprove, btnDelete);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnApprove.setStyle(
                    "-fx-background-color: #10b981; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-padding: 5 12; " +
                    "-fx-cursor: hand; -fx-font-size: 11; -fx-font-weight: bold;");
                btnDelete.setStyle(
                    "-fx-background-color: #ef4444; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-padding: 5 12; " +
                    "-fx-cursor: hand; -fx-font-size: 11; -fx-font-weight: bold;");

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
