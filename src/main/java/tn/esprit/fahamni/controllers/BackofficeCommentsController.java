package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tn.esprit.fahamni.Models.CommentAdminItem;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.BlogService;
import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.utils.BackOfficeUiTheme;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;

public class BackofficeCommentsController {

    private final BlogService blogService = new BlogService();
    private final NotificationService notifService = new NotificationService();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private VBox  notifBanner;
    @FXML private Label notifLabel;
    @FXML private Label totalCountLabel;    // En attente
    @FXML private Label visibleCountLabel;  // Approuvés
    @FXML private Label deletedCountLabel;  // Supprimés
    @FXML private Label feedbackLabel;
    @FXML private Button btnTous;
    @FXML private Button btnPending;
    @FXML private Button btnVisible;
    @FXML private Button btnDeleted;
    @FXML private TextField searchField;

    @FXML private HBox  bulkBar;
    @FXML private Label selectionCountLabel;

    @FXML private TableView<CommentAdminItem> commentsTable;
    @FXML private TableColumn<CommentAdminItem, Boolean> colSelect;
    @FXML private TableColumn<CommentAdminItem, String>  colCommentaire;
    @FXML private TableColumn<CommentAdminItem, String>  colAuteur;
    @FXML private TableColumn<CommentAdminItem, String>  colArticle;
    @FXML private TableColumn<CommentAdminItem, String>  colDate;
    @FXML private TableColumn<CommentAdminItem, String>  colStatut;
    @FXML private TableColumn<CommentAdminItem, Void>    colActions;

    // Journal d'activité commentaires
    @FXML private TableView<Map<String, String>>         commentLogTable;
    @FXML private TableColumn<Map<String, String>, String> colLogDate;
    @FXML private TableColumn<Map<String, String>, String> colLogAdmin;
    @FXML private TableColumn<Map<String, String>, String> colLogAction;
    @FXML private TableColumn<Map<String, String>, String> colLogComment;
    @FXML private TableColumn<Map<String, String>, String> colLogAuteur;
    @FXML private Label logCountLabel;

    private final Map<Integer, SimpleBooleanProperty> checkMap = new HashMap<>();
    private List<CommentAdminItem> allComments = new ArrayList<>();
    private String currentFilter = "tous";

    @FXML
    private void initialize() {
        commentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        commentLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setupColumns();
        setupLogColumns();
        setActiveFilterButton(btnTous);
        loadAll();
        loadCommentLog();
        searchField.textProperty().addListener((obs, o, n) -> applySearch(n));
    }

    @FXML private void filterTous()    { currentFilter = "tous";    setActiveFilterButton(btnTous);    display(allComments); }
    @FXML private void filterPending() { currentFilter = "pending"; setActiveFilterButton(btnPending); display(filtered("pending")); }
    @FXML private void filterVisible() { currentFilter = "visible"; setActiveFilterButton(btnVisible); display(filtered("visible")); }
    @FXML private void filterDeleted() { currentFilter = "deleted"; setActiveFilterButton(btnDeleted); display(filtered("deleted")); }
    @FXML private void handleRefresh() { loadAll(); loadCommentLog(); showFeedback("Liste actualisee.", true); }

    private void loadCommentLog() {
        List<Map<String, String>> log = blogService.getRecentCommentLog(20);
        commentLogTable.setItems(FXCollections.observableArrayList(log));
        logCountLabel.setText(log.size() + " dernieres actions");
    }

    private void setupLogColumns() {
        colLogDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get("date")));
        colLogAdmin.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get("admin")));
        colLogComment.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get("comment")));
        colLogAuteur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get("auteur")));

        colLogAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get("action")));
        colLogAction.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                boolean approved = item.equalsIgnoreCase("Approuve");
                badge.setStyle(
                    "-fx-background-color: " + (approved ? "#dcfce7" : "#fee2e2") + "; " +
                    "-fx-text-fill: "         + (approved ? "#16a34a" : "#dc2626") + "; " +
                    "-fx-font-size: 11; -fx-font-weight: bold; " +
                    "-fx-background-radius: 20; -fx-padding: 3 10;");
                setGraphic(badge);
            }
        });

        colLogAdmin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
            }
        });
        colLogAuteur.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-text-fill: #7c3aed;");
            }
        });
    }

    private List<CommentAdminItem> filtered(String f) {
        switch (f) {
            case "pending": return allComments.stream().filter(c -> c.isFlagged() && !c.isDeletedByAdmin()).collect(Collectors.toList());
            case "visible": return allComments.stream().filter(c -> !c.isFlagged() && !c.isDeletedByAdmin()).collect(Collectors.toList());
            case "deleted": return allComments.stream().filter(CommentAdminItem::isDeletedByAdmin).collect(Collectors.toList());
            default:        return allComments;
        }
    }

    private void loadAll() {
        allComments = blogService.getAllCommentsForAdmin();
        refreshCounters();
        updateNotifBanner();
        switch (currentFilter) {
            case "pending": filterPending(); break;
            case "visible": filterVisible(); break;
            case "deleted": filterDeleted(); break;
            default:        filterTous();    break;
        }
    }

    private void refreshCounters() {
        totalCountLabel.setText(String.valueOf(blogService.countCommentsForAdmin("pending")));
        visibleCountLabel.setText(String.valueOf(blogService.countCommentsForAdmin("visible")));
        deletedCountLabel.setText(String.valueOf(blogService.countCommentsForAdmin("deleted")));
    }

    private void updateNotifBanner() {
        List<Notification> notifs = notifService.getUnreadForAdmin().stream()
            .filter(n -> n.getMessage() != null && n.getMessage().contains("Commentaire bloqué"))
            .collect(Collectors.toList());

        // Garder seulement les cartes (supprimer l'ancien contenu, garder notifLabel caché)
        notifBanner.getChildren().removeIf(c -> c != notifLabel);

        if (notifs.isEmpty()) {
            notifBanner.setVisible(false);
            notifBanner.setManaged(false);
            return;
        }

        for (Notification n : notifs) {
            String raw = n.getMessage() != null ? n.getMessage() : "";
            // Extraire auteur, mot interdit et aperçu depuis le message
            String author  = extractBetween(raw, "bloqué de ", " (mot");
            String word    = extractBetween(raw, "mot interdit : \"", "\"");
            String preview = extractBetween(raw, "\") : \"", "\"");
            if (preview == null || preview.isBlank()) preview = raw;

            // Carte individuelle
            VBox card = new VBox(6);
            card.setPadding(new Insets(10, 14, 10, 14));
            card.setStyle(
                "-fx-background-color: linear-gradient(to right, #fff3e0, #fff8f0);" +
                "-fx-border-color: #e67e22; -fx-border-width: 0 0 0 4;" +
                "-fx-background-radius: 10; -fx-border-radius: 10;");

            // En-tête
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            Label iconLbl = new Label("⚠");
            iconLbl.setStyle("-fx-font-size: 16; -fx-text-fill: #e67e22;");
            Label titleLbl = new Label("Commentaire signalé");
            titleLbl.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #c0392b;");
            Region spacer = new Region(); HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Label badgeLbl = new Label("Non lu");
            badgeLbl.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;" +
                "-fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 2 7;");
            header.getChildren().addAll(iconLbl, titleLbl, spacer, badgeLbl);

            // Détails
            Label authorLbl = new Label("👤  " + (author != null ? author : "Inconnu"));
            authorLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #7f4f1e;");

            Label wordLbl = new Label("🚫  Mot interdit : \"" + (word != null ? word : "?") + "\"");
            wordLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #c0392b; -fx-font-weight: bold;");

            Label previewLbl = new Label("💬  " + preview);
            previewLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #8d6e63;");
            previewLbl.setWrapText(true);

            card.getChildren().addAll(header, authorLbl, wordLbl, previewLbl);
            notifBanner.getChildren().add(card);
        }

        notifBanner.setVisible(true);
        notifBanner.setManaged(true);

        // Marquer comme lu
        notifs.forEach(n -> {
            try {
                Notification upd = new Notification();
                upd.setId(n.getId()); upd.setRecipientId(n.getRecipientId());
                upd.setBlogId(n.getBlogId()); upd.setMessage(n.getMessage()); upd.setRead(true);
                notifService.update(upd);
            } catch (Exception ignored) {}
        });
    }

    private String extractBetween(String text, String start, String end) {
        if (text == null) return null;
        int s = text.indexOf(start);
        if (s < 0) return null;
        s += start.length();
        int e = text.indexOf(end, s);
        if (e < 0) return text.substring(s);
        return text.substring(s, e);
    }

    private void applySearch(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<CommentAdminItem> base = filtered(currentFilter);
        if (k.isEmpty()) { display(base); return; }
        display(base.stream().filter(c -> c.getAuteur() != null && c.getAuteur().toLowerCase().contains(k)).collect(Collectors.toList()));
    }

    private void display(List<CommentAdminItem> items) {
        checkMap.clear();
        commentsTable.getItems().setAll(items);
        refreshBulkBar();
    }

    private void refreshBulkBar() {
        long count = checkMap.values().stream().filter(SimpleBooleanProperty::get).count();
        if (count > 0) {
            selectionCountLabel.setText(count + " selectionne(s)");
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
        ids.forEach(blogService::approveComment);
        loadAll();
        loadCommentLog();
        showFeedback("✅  " + ids.size() + " commentaire(s) approuve(s).", true);
    }

    @FXML
    private void handleBulkDelete() {
        List<Integer> ids = getCheckedIds();
        if (ids.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer la selection ?");
        alert.setHeaderText(null);
        alert.setContentText("Vous allez supprimer " + ids.size() + " commentaire(s). Confirmer ?");
        ButtonType yes = new ButtonType("Oui, supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, cancel);
        if (alert.showAndWait().orElse(cancel) != yes) return;
        ids.forEach(blogService::deleteCommentByAdmin);
        loadAll();
        loadCommentLog();
        showFeedback("🗑  " + ids.size() + " commentaire(s) supprime(s).", false);
    }

    private List<Integer> getCheckedIds() {
        return checkMap.entrySet().stream()
            .filter(e -> e.getValue().get())
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
                checkbox.setOnAction(e -> {
                    CommentAdminItem item = getTableView().getItems().get(getIndex());
                    checkMap.putIfAbsent(item.getId(), new SimpleBooleanProperty(false));
                    checkMap.get(item.getId()).set(checkbox.isSelected());
                    refreshBulkBar();
                });
            }
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null);
                if (empty) return;
                CommentAdminItem c = getTableView().getItems().get(getIndex());
                checkMap.putIfAbsent(c.getId(), new SimpleBooleanProperty(false));
                checkbox.setSelected(checkMap.get(c.getId()).get());
                setGraphic(checkbox);
                setAlignment(Pos.CENTER);
            }
        });
        colSelect.setSortable(false);

        colCommentaire.setCellValueFactory(cell -> {
            String txt = cell.getValue().getCommentaire();
            if (txt != null && txt.length() > 90) txt = txt.substring(0, 90) + "…";
            return new SimpleStringProperty(txt);
        });
        colCommentaire.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-title-cell");
            }
        });

        colAuteur.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAuteur()));
        colAuteur.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-author-cell");
            }
        });

        colArticle.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getArticleTitre()));
        colArticle.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-title-cell");
            }
        });

        colDate.setCellValueFactory(cell -> {
            String d = cell.getValue().getCreatedAt() != null ? cell.getValue().getCreatedAt().format(FMT) : "-";
            return new SimpleStringProperty(d);
        });
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateTextCell(this, item, empty, "backoffice-articles-date-cell");
            }
        });

        colStatut.setCellValueFactory(cell -> {
            CommentAdminItem c = cell.getValue();
            String s = c.isDeletedByAdmin() ? "deleted" : c.isFlagged() ? "pending" : "visible";
            return new SimpleStringProperty(s);
        });
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null);
                if (empty || item == null) return;
                String label = "pending".equals(item) ? "En attente" : "deleted".equals(item) ? "Supprime" : "Approuve";
                String css   = "pending".equals(item) ? "status-pending" : "deleted".equals(item) ? "status-deleted" : "status-published";
                setGraphic(buildBadge(label, "backoffice-status-chip", "backoffice-articles-status-chip", css));
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnVoir     = new Button("Voir");
            private final Button btnApprove  = new Button("Approuver");
            private final Button btnDelete   = new Button("Supprimer");
            private final Button btnRestore  = new Button("Restaurer");
            private final HBox box = new HBox(6.0, btnVoir, btnApprove, btnDelete, btnRestore);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnVoir.getStyleClass().setAll("backoffice-table-action-button", "backoffice-articles-action-button");
                btnApprove.getStyleClass().setAll("action-button", "accept", "backoffice-articles-action-button");
                btnDelete.getStyleClass().setAll("backoffice-table-action-button", "danger", "backoffice-articles-action-button");
                btnRestore.getStyleClass().setAll("backoffice-table-action-button", "backoffice-articles-action-button");

                btnVoir.setOnAction(e -> showPreview(getTableView().getItems().get(getIndex())));

                btnApprove.setOnAction(e -> {
                    CommentAdminItem item = getTableView().getItems().get(getIndex());
                    blogService.approveComment(item.getId());
                    loadAll();
                    loadCommentLog();
                    showFeedback("✅  Commentaire approuve et visible.", true);
                });

                btnDelete.setOnAction(e -> {
                    CommentAdminItem item = getTableView().getItems().get(getIndex());
                    if (confirmDelete()) {
                        blogService.deleteCommentByAdmin(item.getId());
                        loadAll();
                        loadCommentLog();
                        showFeedback("🗑  Commentaire supprime.", false);
                    }
                });

                btnRestore.setOnAction(e -> {
                    CommentAdminItem item = getTableView().getItems().get(getIndex());
                    blogService.approveComment(item.getId());
                    loadAll();
                    loadCommentLog();
                    showFeedback("✅  Commentaire restaure.", true);
                });
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                if (empty) { setGraphic(null); return; }
                CommentAdminItem item = getTableView().getItems().get(getIndex());
                boolean pending = item.isFlagged() && !item.isDeletedByAdmin();
                boolean deleted = item.isDeletedByAdmin();
                boolean visible = !pending && !deleted;

                btnApprove.setManaged(pending); btnApprove.setVisible(pending);
                btnDelete.setManaged(!deleted);  btnDelete.setVisible(!deleted);
                btnRestore.setManaged(deleted);  btnRestore.setVisible(deleted);
                setGraphic(box);
            }
        });
    }

    private void showPreview(CommentAdminItem item) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Apercu du commentaire");
        stage.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white; -fx-min-width: 480;");

        Label titre = new Label("Commentaire de " + item.getAuteur());
        titre.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label article = new Label("Article : " + item.getArticleTitre());
        article.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");

        Label date = new Label("Date : " + (item.getCreatedAt() != null ? item.getCreatedAt().format(FMT) : "-"));
        date.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");

        String statutTxt   = item.isDeletedByAdmin() ? "Supprime" : item.isFlagged() ? "En attente" : "Approuve";
        String statutColor = item.isDeletedByAdmin() ? "#c62828" : item.isFlagged() ? "#e67e22" : "#2e7d32";
        Label statut = new Label("Statut : " + statutTxt);
        statut.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + statutColor + ";");

        TextArea contenu = new TextArea(item.getCommentaire());
        contenu.setWrapText(true); contenu.setEditable(false); contenu.setPrefRowCount(4);
        contenu.setStyle("-fx-font-size: 13; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8;");

        Button close = new Button("Fermer");
        close.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 7 20; -fx-cursor: hand;");
        close.setOnAction(e -> stage.close());

        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(titre, article, date, statut, contenu, footer);
        Scene scene = new Scene(root);
        BackOfficeUiTheme.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    private boolean confirmDelete() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer le commentaire ?");
        alert.setHeaderText(null);
        alert.setContentText("Ce commentaire sera retire de la plateforme. Confirmer ?");
        ButtonType yes = new ButtonType("Oui, supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, cancel);
        return alert.showAndWait().orElse(cancel) == yes;
    }

    private void showFeedback(String msg, boolean success) {
        feedbackLabel.setText(msg);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error", "backoffice-articles-feedback");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void setActiveFilterButton(Button active) {
        for (Button b : List.of(btnTous, btnPending, btnVisible, btnDeleted)) {
            if (b != null) b.getStyleClass().remove("active");
        }
        if (active != null && !active.getStyleClass().contains("active")) active.getStyleClass().add("active");
    }

    private void updateTextCell(TableCell<?, ?> cell, String item, boolean empty, String styleClass) {
        cell.setGraphic(null);
        cell.getStyleClass().remove(styleClass);
        if (empty || item == null) { cell.setText(null); return; }
        cell.setText(item);
        if (!cell.getStyleClass().contains(styleClass)) cell.getStyleClass().add(styleClass);
    }

    private Label buildBadge(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }
}
