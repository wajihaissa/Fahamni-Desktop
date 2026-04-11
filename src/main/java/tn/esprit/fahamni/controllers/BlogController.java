package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.Interaction;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.BlogService;
import tn.esprit.fahamni.services.NotificationService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BlogController {

    private final BlogService blogService = new BlogService();
    private final NotificationService notifService = new NotificationService();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private Popup notifPopup;

    private static final int PAGE_SIZE = 6;
    private int currentPage = 1;
    private List<Blog> currentBlogList = new ArrayList<>();

    @FXML private TextField searchField;
    @FXML private VBox blogsContainer;
    @FXML private HBox paginationBar;
    @FXML private Label notifBellBadge;
    @FXML private Button btnTous;
    @FXML private Button btnMath;
    @FXML private Button btnScience;
    @FXML private Button btnInfo;
    @FXML private Button btnLangue;
    @FXML private Button btnAutre;
    @FXML private ComboBox<String> sortComboBox;

    @FXML
    private void initialize() {
        try {
            loadBlogs(blogService.getAllBlogs());
        } catch (Exception e) {
            System.err.println("Erreur initialisation blog: " + e.getMessage());
            e.printStackTrace();
            Label errLabel = new Label("Impossible de charger les articles. Vérifiez la connexion à la base de données.\n" + e.getMessage());
            errLabel.setWrapText(true);
            errLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 13; -fx-padding: 30;");
            blogsContainer.getChildren().add(errLabel);
        }
        // Recherche en temps réel à chaque frappe
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String kw = newVal == null ? "" : newVal.trim();
            List<Blog> base = kw.isEmpty()
                    ? blogService.getAllBlogs()
                    : blogService.searchBlogs(kw);
            loadBlogs(applySorting(base));
        });
        // Conserver aussi la touche Entrée
        searchField.setOnAction(ev -> handleSearch());

        // Dropdown tri
        sortComboBox.getItems().setAll(
            "Les plus récents",
            "Les plus aimés",
            "Les plus commentés"
        );
        sortComboBox.setValue("Les plus récents");
        sortComboBox.setOnAction(ev -> loadBlogs(applySorting(currentBlogList)));

        // Badge cloche + bannières
        refreshBellBadge();
        showTuteurNotifications();
    }

    /** Affiche un toast temporaire (3 s) pour chaque notification non lue du tuteur */
    private void showTuteurNotifications() {
        try {
            int uid = resolveCurrentUserId();
            if (uid <= 0) return;
            java.util.List<Notification> notifs = notifService.getUnreadForUser(uid);
            if (notifs.isEmpty()) return;

            // Afficher les toasts avec un léger décalage entre chacun
            for (int i = 0; i < notifs.size(); i++) {
                Notification n = notifs.get(i);
                long delayMs = i * 400L;
                Timeline delay = new Timeline(new KeyFrame(Duration.millis(delayMs), ev ->
                    showToast(n.getMessage())
                ));
                delay.play();
            }
            notifService.markAllReadForUser(uid);
        } catch (Exception e) {
            System.err.println("showTuteurNotifications: " + e.getMessage());
        }
    }

    /** Affiche un toast en haut à droite qui disparaît après 3,5 secondes */
    private void showToast(String message) {
        if (blogsContainer == null || blogsContainer.getScene() == null) return;

        boolean approved = message != null &&
            (message.contains("approuve") || message.contains("publie"));

        // Construction du toast
        HBox toast = new HBox(10);
        toast.setPadding(new Insets(12, 18, 12, 14));
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxWidth(420);
        toast.setStyle(
            "-fx-background-color: " + (approved ? "#1e7e34" : "#c0392b") + ";" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 4);");

        Label icon = new Label(approved ? "✅" : "❌");
        icon.setStyle("-fx-font-size: 15;");

        Label msg = new Label(message != null ? message : "");
        msg.setStyle("-fx-text-fill: white; -fx-font-size: 12; -fx-font-weight: bold;");
        msg.setWrapText(true);
        msg.setMaxWidth(360);

        toast.getChildren().addAll(icon, msg);
        toast.setOpacity(0);

        // Insérer dans le conteneur (sera retiré après animation)
        blogsContainer.getChildren().add(0, toast);

        // Fade in → attendre → fade out → supprimer
        Timeline fadeIn = new Timeline(
            new KeyFrame(Duration.ZERO,     new KeyValue(toast.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(300), new KeyValue(toast.opacityProperty(), 1))
        );
        Timeline fadeOut = new Timeline(
            new KeyFrame(Duration.ZERO,     new KeyValue(toast.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(400), new KeyValue(toast.opacityProperty(), 0))
        );
        fadeOut.setOnFinished(e -> blogsContainer.getChildren().remove(toast));

        fadeIn.setOnFinished(e -> {
            Timeline wait = new Timeline(new KeyFrame(Duration.millis(3000), ev -> fadeOut.play()));
            wait.play();
        });
        fadeIn.play();
    }

    private void loadBlogs(List<Blog> blogs) {
        currentBlogList = blogs;
        currentPage = 1;
        renderPage();
    }

    private void renderPage() {
        blogsContainer.getChildren().clear();
        List<Blog> blogs = currentBlogList;

        if (blogs.isEmpty()) {
            Label empty = new Label("Aucun article trouvé pour cette catégorie.");
            empty.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14; -fx-padding: 30;");
            blogsContainer.getChildren().add(empty);
            buildPaginationBar(0);
            return;
        }

        int total = blogs.size();
        int fromIdx = (currentPage - 1) * PAGE_SIZE;
        int toIdx = Math.min(fromIdx + PAGE_SIZE, total);
        List<Blog> pageBlogs = blogs.subList(fromIdx, toIdx);

        // Grille 3 colonnes — toutes les cartes d'une rangée ont la même hauteur
        final int COLS = 3;
        for (int i = 0; i < pageBlogs.size(); i += COLS) {
            HBox row = new HBox(14);
            row.setFillHeight(true); // toutes les cartes s'étendent à la même hauteur
            for (int j = 0; j < COLS; j++) {
                if (i + j < pageBlogs.size()) {
                    VBox card;
                    try { card = createBlogCard(pageBlogs.get(i + j)); }
                    catch (Exception ex) { card = new VBox(); }
                    // largeur fixe égale pour toutes, hauteur dictée par la plus haute
                    HBox.setHgrow(card, Priority.ALWAYS);
                    row.getChildren().add(card);
                } else {
                    // cellule vide pour garder la grille alignée
                    Region filler = new Region();
                    HBox.setHgrow(filler, Priority.ALWAYS);
                    row.getChildren().add(filler);
                }
            }
            VBox.setMargin(row, new Insets(0, 0, 14, 0));
            blogsContainer.getChildren().add(row);
        }

        buildPaginationBar(total);
    }

    private void buildPaginationBar(int total) {
        if (paginationBar == null) return;
        paginationBar.getChildren().clear();

        if (total == 0) {
            paginationBar.setManaged(false);
            paginationBar.setVisible(false);
            return;
        }
        paginationBar.setManaged(true);
        paginationBar.setVisible(true);

        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        int fromIdx = (currentPage - 1) * PAGE_SIZE + 1;
        int toIdx = Math.min(currentPage * PAGE_SIZE, total);

        // Label "Affichage X-Y de Z article(s)"
        Label info = new Label("Affichage " + fromIdx + "-" + toIdx + " de " + total + " article(s)");
        info.setStyle("-fx-font-size: 12; -fx-text-fill: #64748b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        paginationBar.getChildren().addAll(info, spacer);

        // Bouton ← précédent
        Button prev = new Button("←");
        stylePaginationBtn(prev, false);
        prev.setDisable(currentPage == 1);
        prev.setOnAction(e -> { currentPage--; renderPage(); });
        paginationBar.getChildren().add(prev);

        // Numéros de pages (avec ellipses si > 7 pages)
        for (int p = 1; p <= totalPages; p++) {
            if (totalPages > 7 && p > 2 && p < totalPages - 1
                    && (p < currentPage - 1 || p > currentPage + 1)) {
                // Ajouter "..." une seule fois par groupe
                if (p == currentPage - 2 || p == currentPage + 2) {
                    Label dots = new Label("...");
                    dots.setStyle("-fx-font-size: 12; -fx-text-fill: #94a3b8; -fx-padding: 0 4;");
                    paginationBar.getChildren().add(dots);
                }
                continue;
            }
            final int page = p;
            Button btn = new Button(String.valueOf(p));
            stylePaginationBtn(btn, p == currentPage);
            btn.setOnAction(e -> { currentPage = page; renderPage(); });
            paginationBar.getChildren().add(btn);
        }

        // Bouton → suivant
        Button next = new Button("→");
        stylePaginationBtn(next, false);
        next.setDisable(currentPage == totalPages);
        next.setOnAction(e -> { currentPage++; renderPage(); });
        paginationBar.getChildren().add(next);
    }

    private void stylePaginationBtn(Button btn, boolean active) {
        if (active) {
            btn.setStyle("-fx-background-color: #3a7bd5; -fx-text-fill: white; " +
                    "-fx-font-weight: bold; -fx-background-radius: 8; " +
                    "-fx-padding: 5 11; -fx-font-size: 12; -fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; " +
                    "-fx-background-radius: 8; -fx-padding: 5 11; -fx-font-size: 12; -fx-cursor: hand;");
        }
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    private VBox createBlogCard(Blog blog) {
        String accent = getAvatarColor(blog.getImage());

        // Carte — hauteur naturelle, toutes les cartes d'une même ligne s'égalisent via HBox
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.09), 10, 0, 0, 3);");

        // ── Bande colorée fine en haut ──
        HBox topBar = new HBox(8);
        topBar.setPrefHeight(28);
        topBar.setMinHeight(28);
        topBar.setMaxHeight(28);
        topBar.setPadding(new Insets(4, 10, 4, 10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: " + getBannerColor(blog.getImage()) + "; -fx-background-radius: 12 12 0 0;");

        Label catIcon = new Label(getCategoryIcon(blog.getImage()));
        catIcon.setStyle("-fx-font-size: 14;");
        Label catBadge = new Label(getCategoryLabel(blog.getImage()));
        catBadge.setStyle("-fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold; " +
                "-fx-background-color: rgba(0,0,0,0.2); -fx-background-radius: 8; -fx-padding: 2 6;");

        Region barSpacer = new Region(); HBox.setHgrow(barSpacer, Priority.ALWAYS);

        String dateStr2 = blog.getPublishedAt() != null
                ? blog.getPublishedAt().format(DATE_FORMATTER)
                : (blog.getCreatedAt() != null ? blog.getCreatedAt().format(DATE_FORMATTER) : "");
        Label dateSmall = new Label(dateStr2);
        dateSmall.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 10;");

        topBar.getChildren().addAll(catIcon, catBadge, barSpacer, dateSmall);
        card.getChildren().add(topBar);

        // ── Corps principal ──
        VBox body = new VBox(5);
        body.setPadding(new Insets(8, 12, 6, 12));

        // Titre — 1 ligne tronquée
        Label title = new Label(blog.getTitre());
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        title.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

        // Auteur inline
        HBox authorRow = new HBox(5);
        authorRow.setAlignment(Pos.CENTER_LEFT);
        StackPane avatar = new StackPane();
        avatar.setPrefSize(20, 20); avatar.setMinSize(20, 20); avatar.setMaxSize(20, 20);
        avatar.setStyle("-fx-background-radius: 10; -fx-background-color: " + accent + ";");
        String initial = (blog.getPublishedBy() != null && !blog.getPublishedBy().isEmpty())
                ? String.valueOf(blog.getPublishedBy().charAt(0)).toUpperCase() : "?";
        Label avatarLabel = new Label(initial);
        avatarLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 9;");
        avatar.getChildren().add(avatarLabel);
        Label authorName = new Label(blog.getPublishedBy() != null ? blog.getPublishedBy() : "Anonyme");
        authorName.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b;");
        authorRow.getChildren().addAll(avatar, authorName);

        // Séparateur fin
        Separator sep1 = new Separator();
        sep1.setStyle("-fx-opacity: 0.2;");

        // Extrait — tronqué à 80 chars, 2 lignes max
        String rawContent = blog.getContent() != null ? blog.getContent() : "";
        String cleanContent = cleanMarkdown(rawContent);
        boolean isTruncated = cleanContent.length() > 80;
        String excerpt = isTruncated ? cleanContent.substring(0, 80) + "..." : cleanContent;

        Label contentLabel = new Label(excerpt);
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11;");

        body.getChildren().addAll(title, authorRow, sep1, contentLabel);
        card.getChildren().add(body);

        // ── Barre d'actions ──
        List<Interaction> interactions = blogService.getInteractionsByBlogId(blog.getId());
        long likesCount = interactions.stream().filter(i -> "like".equalsIgnoreCase(i.getType())).count();
        long commentsCount = interactions.stream()
                .filter(i -> i.getCommentaire() != null && !i.getCommentaire().isEmpty()).count();

        // Identité du user connecté
        tn.esprit.fahamni.Models.User currentUser = tn.esprit.fahamni.utils.SessionManager.getCurrentUser();
        int currentUserId = currentUser != null ? currentUser.getId() : 0;
        // Pour les opérations DB, utiliser au minimum id=1 si id=0 (user mock)
        int dbUserId = currentUserId > 0 ? currentUserId : 1;
        String currentUserName = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();

        // DEBUG : afficher les valeurs pour diagnostiquer isMyArticle
        System.out.println("DEBUG CARD [" + blog.getTitre() + "] -> publisherId=" + blog.getPublisherId()
                + " | currentUserId=" + currentUserId + " | dbUserId=" + dbUserId
                + " | publishedBy='" + blog.getPublishedBy() + "' | currentUserName='" + currentUserName + "'");

        // isMyArticle : par ID OU par nom (comparaison souple)
        boolean isMyArticle = (blog.getPublisherId() > 0
                                && (blog.getPublisherId() == currentUserId || blog.getPublisherId() == dbUserId))
                || (blog.getPublishedBy() != null && currentUserName != null
                    && !currentUserName.equals("Utilisateur")
                    && blog.getPublishedBy().trim().equalsIgnoreCase(currentUserName.trim()));

        System.out.println("DEBUG -> isMyArticle=" + isMyArticle);
        boolean alreadyLiked = blogService.hasUserLiked(blog.getId(), dbUserId);

        Separator sepAct = new Separator(); sepAct.setStyle("-fx-opacity: 0.15;");
        card.getChildren().add(sepAct);

        HBox actBar = new HBox(5);
        actBar.setPadding(new Insets(6, 10, 8, 10));
        actBar.setAlignment(Pos.CENTER_LEFT);

        // ── Réactions multiples 👍 ❤️ 😮 ──
        int userReaction = blogService.getUserReaction(blog.getId(), dbUserId);
        long r1 = blogService.countReaction(blog.getId(), 1);
        long r2 = blogService.countReaction(blog.getId(), 2);
        long r3 = blogService.countReaction(blog.getId(), 3);

        Button btn1 = new Button("👍 " + r1);
        Button btn2 = new Button("❤️ " + r2);
        Button btn3 = new Button("😮 " + r3);
        Button likeBtn = btn1; // alias pour compatibilité animation

        styleReactionBtn(btn1, userReaction == 1);
        styleReactionBtn(btn2, userReaction == 2);
        styleReactionBtn(btn3, userReaction == 3);

        final int[] currentReaction = {userReaction};

        java.util.function.BiConsumer<Button, Integer> onReact = (btn, type) -> {
            if (currentReaction[0] == type) {
                // Retirer la réaction
                blogService.removeReaction(blog.getId(), dbUserId);
                currentReaction[0] = 0;
                styleReactionBtn(btn1, false);
                styleReactionBtn(btn2, false);
                styleReactionBtn(btn3, false);
            } else {
                // Nouvelle réaction
                blogService.addReaction(blog.getId(), type, currentUserName);
                currentReaction[0] = type;
                styleReactionBtn(btn1, type == 1);
                styleReactionBtn(btn2, type == 2);
                styleReactionBtn(btn3, type == 3);
            }
            animateCounter(btn1, "👍 " + blogService.countReaction(blog.getId(), 1));
            animateCounter(btn2, "❤️ " + blogService.countReaction(blog.getId(), 2));
            animateCounter(btn3, "😮 " + blogService.countReaction(blog.getId(), 3));
        };

        btn1.setOnAction(e -> onReact.accept(btn1, 1));
        btn2.setOnAction(e -> onReact.accept(btn2, 2));
        btn3.setOnAction(e -> onReact.accept(btn3, 3));

        Button commentBtn = new Button("\uD83D\uDCAC " + commentsCount);
        styleCompactBtn(commentBtn, "#f0f4ff", "#3a7bd5");

        Region actSpacer = new Region(); HBox.setHgrow(actSpacer, Priority.ALWAYS);

        // Bouton Lire la suite
        Button readBtn = new Button("Lire \u2192");
        readBtn.setStyle("-fx-background-color: " + accent + "; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 4 10; -fx-font-size: 11; -fx-font-weight: bold; -fx-cursor: hand;");
        readBtn.setMinWidth(Region.USE_PREF_SIZE);
        if (!isTruncated) { readBtn.setVisible(false); readBtn.setManaged(false); }
        final boolean[] expanded = {false};
        readBtn.setOnAction(e -> {
            expanded[0] = !expanded[0];
            contentLabel.setText(expanded[0] ? cleanContent : excerpt);
            readBtn.setText(expanded[0] ? "\u2191 Moins" : "Lire \u2192");
        });

        // Bouton Partager
        Button shareBtn = new Button("\uD83D\uDD17");
        styleCompactBtn(shareBtn, "#f0f9ff", "#0284c7");
        shareBtn.setTooltip(new Tooltip("Copier le titre et l'auteur"));
        shareBtn.setOnAction(e -> {
            String texte = "« " + blog.getTitre() + " »"
                + " — par " + blog.getPublishedBy()
                + "\n[Fahamni — Blog Éducatif]";
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(texte);
            clipboard.setContent(cc);
            // Feedback visuel temporaire
            String oldStyle = shareBtn.getStyle();
            shareBtn.setText("✔ Copié !");
            shareBtn.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; " +
                "-fx-background-radius: 8; -fx-padding: 4 10; -fx-font-size: 11; -fx-cursor: hand;");
            Timeline reset = new Timeline(new KeyFrame(Duration.millis(1800), ev -> {
                shareBtn.setText("\uD83D\uDD17");
                shareBtn.setStyle(oldStyle);
            }));
            reset.play();
        });

        // Boutons Modifier et Supprimer — seulement pour l'auteur de l'article
        if (isMyArticle) {
            Button editBtn = new Button("\u270F");
            styleCompactBtn(editBtn, "#f5f0ff", "#8e44ad");
            editBtn.setTooltip(new Tooltip("Modifier mon article"));
            editBtn.setOnAction(e -> openEditForm(blog));

            Button deleteArticleBtn = new Button("\uD83D\uDDD1");
            styleCompactBtn(deleteArticleBtn, "#fee2e2", "#dc2626");
            deleteArticleBtn.setTooltip(new Tooltip("Supprimer mon article"));
            deleteArticleBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer \"" + blog.getTitre() + "\" ?");
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(r -> {
                    if (r == javafx.scene.control.ButtonType.OK) {
                        blogService.deleteBlog(blog.getId());
                        loadBlogs(blogService.getAllBlogs());
                    }
                });
            });
            actBar.getChildren().addAll(btn1, btn2, btn3, commentBtn, actSpacer, shareBtn, editBtn, deleteArticleBtn, readBtn);
        } else {
            actBar.getChildren().addAll(btn1, btn2, btn3, commentBtn, actSpacer, shareBtn, readBtn);
        }
        card.getChildren().add(actBar);

        // ── Section commentaires (cachée par défaut) ──

        VBox commentsSection = new VBox(6);
        commentsSection.setPadding(new Insets(0, 10, 10, 10));
        commentsSection.setManaged(false);
        commentsSection.setVisible(false);
        commentsSection.setStyle("-fx-background-color: #f8faff; -fx-background-radius: 0 0 12 12;");

        Separator sepCom = new Separator(); sepCom.setStyle("-fx-opacity: 0.15;");

        // ── Commentaires paginés (3 par défaut + "Voir plus") ──
        List<Interaction> commentList = new ArrayList<>();
        for (Interaction inter : interactions) {
            if (inter.getCommentaire() != null && !inter.getCommentaire().isEmpty())
                commentList.add(inter);
        }
        final int COMMENTS_PAGE = 3;
        final int[] shownCount = {0};

        VBox commentsBox = new VBox(6);
        commentsBox.setPadding(new Insets(6, 0, 4, 0));

        // Bouton "Voir plus" réutilisable
        Button voirPlusBtn = new Button();
        voirPlusBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #3a7bd5; " +
            "-fx-font-size: 11; -fx-font-weight: bold; -fx-cursor: hand; " +
            "-fx-padding: 4 0; -fx-underline: true;");

        // Affiche les N prochains commentaires
        Runnable loadNextComments = new Runnable() {
            @Override public void run() {
                // Retirer le bouton s'il est déjà là
                commentsBox.getChildren().remove(voirPlusBtn);
                int end = Math.min(shownCount[0] + COMMENTS_PAGE, commentList.size());
                for (int i = shownCount[0]; i < end; i++) {
                    Interaction inter = commentList.get(i);
                    boolean mine = inter.getCreatedBy() != null && inter.getCreatedBy().equals(currentUserName);
                    commentsBox.getChildren().add(
                        buildCommentRow(inter.getCreatedBy(), inter.getCommentaire(), mine, commentsBox, commentBtn));
                }
                shownCount[0] = end;
                // Remettre le bouton si encore des commentaires
                if (shownCount[0] < commentList.size()) {
                    int remaining = commentList.size() - shownCount[0];
                    voirPlusBtn.setText("▼  Voir " + remaining + " commentaire(s) de plus");
                    commentsBox.getChildren().add(voirPlusBtn);
                }
            }
        };

        voirPlusBtn.setOnAction(e -> loadNextComments.run());

        // Chargement initial
        loadNextComments.run();

        final int MAX_COMMENT = 300;

        VBox inputWrapper = new VBox(4);

        HBox inputRow = new HBox(6);
        inputRow.setAlignment(Pos.CENTER);
        TextField commentField = new TextField();
        commentField.setPromptText("Écrire un commentaire...");
        commentField.setStyle("-fx-background-color: white; -fx-background-radius: 16; " +
                "-fx-border-radius: 16; -fx-border-color: #d1d9e6; -fx-padding: 6 12; -fx-font-size: 11;");
        HBox.setHgrow(commentField, Priority.ALWAYS);

        Button sendBtn = new Button("Envoyer");
        sendBtn.setDisable(true); // désactivé par défaut (champ vide)
        sendBtn.setStyle("-fx-background-color: #b0bec5; -fx-text-fill: white; " +
                "-fx-background-radius: 16; -fx-padding: 6 14; -fx-font-size: 11; -fx-font-weight: bold;");

        // Compteur caractères sous le champ
        Label commentCounter = new Label("0/" + MAX_COMMENT);
        commentCounter.setStyle("-fx-font-size: 10; -fx-text-fill: #94a3b8;");

        // Activer/désactiver bouton + compteur en temps réel
        commentField.textProperty().addListener((obs, o, n) -> {
            if (n != null && n.length() > MAX_COMMENT) {
                commentField.setText(o);
                return;
            }
            int len = n == null ? 0 : n.trim().length();
            boolean empty = len == 0;
            sendBtn.setDisable(empty);
            sendBtn.setStyle(empty
                ? "-fx-background-color: #b0bec5; -fx-text-fill: white; -fx-background-radius: 16; -fx-padding: 6 14; -fx-font-size: 11; -fx-font-weight: bold;"
                : "-fx-background-color: " + accent + "; -fx-text-fill: white; -fx-background-radius: 16; -fx-padding: 6 14; -fx-font-size: 11; -fx-font-weight: bold; -fx-cursor: hand;");
            int rawLen = n == null ? 0 : n.length();
            commentCounter.setText(rawLen + "/" + MAX_COMMENT);
            String color = rawLen > 270 ? "#f59e0b" : "#94a3b8";
            commentCounter.setStyle("-fx-font-size: 10; -fx-text-fill: " + color + ";");
        });

        sendBtn.setOnAction(e -> {
            String comment = commentField.getText().trim();
            if (!comment.isEmpty()) {
                String err = blogService.addInteraction(blog.getId(), "commentaire", comment, currentUserName);
                if (err != null) {
                    new Alert(Alert.AlertType.ERROR, err).showAndWait();
                    return;
                }
                int insertIdx = commentsBox.getChildren().indexOf(voirPlusBtn);
                HBox newRow = buildCommentRow(currentUserName, comment, true, commentsBox, commentBtn);
                if (insertIdx >= 0) commentsBox.getChildren().add(insertIdx, newRow);
                else commentsBox.getChildren().add(newRow);
                commentField.clear();
                commentCounter.setText("0/" + MAX_COMMENT);
                long n = blogService.countComments(blog.getId());
                animateCounter(commentBtn, "\uD83D\uDCAC " + n);
            }
        });

        inputRow.getChildren().addAll(commentField, sendBtn);
        inputWrapper.getChildren().addAll(inputRow, commentCounter);
        commentsSection.getChildren().addAll(sepCom, commentsBox, inputWrapper);
        card.getChildren().add(commentsSection);

        commentBtn.setOnAction(e -> {
            boolean v = commentsSection.isVisible();
            commentsSection.setManaged(!v);
            commentsSection.setVisible(!v);
        });

        return card;
    }

    /** Supprime les balises markdown courantes du texte */
    private String cleanMarkdown(String text) {
        if (text == null) return "";
        return text
            .replaceAll("(?m)^#{1,6}\\s*", "")   // titres ## ### etc
            .replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1") // gras/italique
            .replaceAll("_{1,2}([^_]+)_{1,2}", "$1")     // soulignement
            .replaceAll("`([^`]+)`", "$1")               // code inline
            .replaceAll("!?\\[([^\\]]+)\\]\\([^)]*\\)", "$1") // liens/images
            .replaceAll("(?m)^[-*+]\\s+", "• ")         // listes
            .replaceAll("\\n{3,}", "\n\n")               // sauts multiples
            .trim();
    }

    private void animateCounter(Button btn, String newText) {
        btn.setText(newText);
        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(btn.scaleXProperty(), 1.0),
                new KeyValue(btn.scaleYProperty(), 1.0)),
            new KeyFrame(Duration.millis(120),
                new KeyValue(btn.scaleXProperty(), 1.25),
                new KeyValue(btn.scaleYProperty(), 1.25)),
            new KeyFrame(Duration.millis(240),
                new KeyValue(btn.scaleXProperty(), 1.0),
                new KeyValue(btn.scaleYProperty(), 1.0))
        );
        tl.play();
    }

    private void styleFooterBtn(Button btn, String bg, String fg, String border) {
        btn.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                "-fx-background-radius: 8; -fx-padding: 4 10; -fx-font-size: 11; " +
                "-fx-cursor: hand; -fx-border-color: " + border + "; -fx-border-radius: 8;");
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    private void styleIconBtn(Button btn, String bg, String fg) {
        btn.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                "-fx-background-radius: 8; -fx-padding: 4 8; -fx-font-size: 12; -fx-cursor: hand;");
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    private void styleCompactBtn(Button btn, String bg, String fg) {
        btn.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                "-fx-background-radius: 8; -fx-padding: 4 10; -fx-font-size: 11; -fx-cursor: hand;");
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    private void styleReactionBtn(Button btn, boolean active) {
        btn.setStyle(active
            ? "-fx-background-color: #fef9c3; -fx-text-fill: #854d0e; -fx-background-radius: 8; " +
              "-fx-padding: 4 10; -fx-font-size: 12; -fx-cursor: hand; " +
              "-fx-border-color: #f59e0b; -fx-border-radius: 8; -fx-border-width: 1.5;"
            : "-fx-background-color: #f8fafc; -fx-text-fill: #475569; -fx-background-radius: 8; " +
              "-fx-padding: 4 10; -fx-font-size: 12; -fx-cursor: hand;");
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    // ---- Construction d'une ligne de commentaire ----
    private HBox buildCommentRow(String author, String commentText, boolean isMine,
                                  VBox commentsBox, Button commentBtn) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);

        // Avatar avec initiale de l'auteur
        StackPane avatar = new StackPane();
        avatar.setPrefSize(32, 32);
        avatar.setMinSize(32, 32);
        String color = isMine ? "#3a7bd5" : "#8e44ad";
        avatar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 16;");
        String initial = (author != null && !author.isEmpty())
                ? String.valueOf(author.charAt(0)).toUpperCase() : "?";
        Label initLabel = new Label(initial);
        initLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;");
        avatar.getChildren().add(initLabel);

        // Bulle avec nom + texte
        VBox bubble = new VBox(4);
        bubble.setStyle("-fx-background-color: " + (isMine ? "#eaf4ff" : "#f5f7fa") + "; " +
                "-fx-padding: 8 12; -fx-background-radius: 12;");
        HBox.setHgrow(bubble, Priority.ALWAYS);

        Label nameLabel = new Label(author != null ? author : "Anonyme");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + "; -fx-font-size: 11;");

        Label textLabel = new Label(commentText);
        textLabel.setWrapText(true);
        textLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12;");

        bubble.getChildren().addAll(nameLabel, textLabel);

        if (isMine) {
            // Zone d'édition inline (cachée par défaut)
            TextField editField = new TextField(commentText);
            editField.setStyle("-fx-background-color: white; -fx-border-color: #3a7bd5; " +
                    "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 10; -fx-font-size: 12;");
            editField.setManaged(false);
            editField.setVisible(false);
            bubble.getChildren().add(editField);

            // Boutons à droite du commentaire
            Button editBtn = new Button("\u270F");
            editBtn.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; " +
                    "-fx-font-size: 13; -fx-cursor: hand; -fx-padding: 3 8; " +
                    "-fx-background-radius: 6; -fx-border-radius: 6;");

            Button deleteBtn = new Button("\u2716");
            deleteBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; " +
                    "-fx-font-size: 13; -fx-cursor: hand; -fx-padding: 3 8; " +
                    "-fx-background-radius: 6; -fx-border-radius: 6;");

            VBox actionBox = new VBox(4);
            actionBox.setAlignment(Pos.CENTER);
            actionBox.getChildren().addAll(editBtn, deleteBtn);

            final boolean[] editing = {false};

            editBtn.setOnAction(e -> {
                editing[0] = !editing[0];
                if (editing[0]) {
                    editField.setText(textLabel.getText());
                    textLabel.setManaged(false);
                    textLabel.setVisible(false);
                    editField.setManaged(true);
                    editField.setVisible(true);
                    editBtn.setText("\u2713");
                    editBtn.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; " +
                            "-fx-font-size: 13; -fx-cursor: hand; -fx-padding: 3 8; " +
                            "-fx-background-radius: 6; -fx-border-radius: 6;");
                } else {
                    String newText = editField.getText().trim();
                    if (!newText.isEmpty()) textLabel.setText(newText);
                    textLabel.setManaged(true);
                    textLabel.setVisible(true);
                    editField.setManaged(false);
                    editField.setVisible(false);
                    editBtn.setText("\u270F");
                    editBtn.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; " +
                            "-fx-font-size: 13; -fx-cursor: hand; -fx-padding: 3 8; " +
                            "-fx-background-radius: 6; -fx-border-radius: 6;");
                }
            });

            deleteBtn.setOnAction(e -> {
                commentsBox.getChildren().remove(row);
                long count = commentsBox.getChildren().stream()
                        .filter(n -> n instanceof HBox).count();
                commentBtn.setText("\uD83D\uDCAC  " + count + " Commentaires");
            });

            row.getChildren().addAll(avatar, bubble, actionBox);
        } else {
            row.getChildren().addAll(avatar, bubble);
        }
        return row;
    }

    // ---- Helpers visuels ----
    private String getBannerColor(String cat) {
        if (cat == null) return "linear-gradient(to right, #3a7bd5, #00d2ff)";
        switch (cat.toLowerCase()) {
            case "math": return "linear-gradient(to right, #3a7bd5, #1a5fb4)";
            case "physique": case "science": return "linear-gradient(to right, #f7971e, #ffd200)";
            case "informatique": return "linear-gradient(to right, #8e44ad, #e91e8c)";
            case "langue": return "linear-gradient(to right, #11998e, #38ef7d)";
            default: return "linear-gradient(to right, #636e72, #b2bec3)";
        }
    }

    private String getAvatarColor(String cat) {
        if (cat == null) return "#3a7bd5";
        switch (cat.toLowerCase()) {
            case "math": return "#3a7bd5";
            case "physique": case "science": return "#f7971e";
            case "informatique": return "#8e44ad";
            case "langue": return "#11998e";
            default: return "#636e72";
        }
    }

    private String getCategoryLabel(String cat) {
        if (cat == null) return "General";
        switch (cat.toLowerCase()) {
            case "math": return "Mathematiques";
            case "physique": return "Physique";
            case "science": return "Sciences";
            case "informatique": return "Informatique";
            case "langue": return "Langues";
            default: return "Autre";
        }
    }

    private String getCategoryIcon(String cat) {
        if (cat == null) return "\uD83D\uDCDA";
        switch (cat.toLowerCase()) {
            case "math": return "\uD83D\uDCD0";
            case "physique": case "science": return "\uD83D\uDD2C";
            case "informatique": return "\uD83D\uDCBB";
            case "langue": return "\uD83D\uDCDD";
            default: return "\uD83D\uDCDA";
        }
    }

    // ---- Lire la suite : vue complète de l'article ----
    private void openArticleDetail(Blog blog) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(blog.getTitre());
        dialog.setMinWidth(660);
        dialog.setMinHeight(500);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #f0f4f8;");

        // Bannière
        StackPane banner = new StackPane();
        banner.setPrefHeight(160);
        banner.setStyle("-fx-background-color: " + getBannerColor(blog.getImage()) + ";");

        Label icon = new Label(getCategoryIcon(blog.getImage()));
        icon.setStyle("-fx-font-size: 64; -fx-opacity: 0.25;");
        StackPane.setAlignment(icon, Pos.CENTER_RIGHT);
        StackPane.setMargin(icon, new Insets(0, 30, 0, 0));

        Label badge = new Label(getCategoryLabel(blog.getImage()));
        badge.setStyle("-fx-background-color: rgba(0,0,0,0.25); -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 5 14; -fx-background-radius: 20; -fx-font-size: 12;");
        StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
        StackPane.setMargin(badge, new Insets(0, 0, 16, 20));

        VBox titleBox = new VBox(6);
        titleBox.setAlignment(Pos.BOTTOM_LEFT);
        StackPane.setMargin(titleBox, new Insets(0, 0, 40, 20));
        StackPane.setAlignment(titleBox, Pos.BOTTOM_LEFT);

        Label titleLabel = new Label(blog.getTitre());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(560);
        titleLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 6, 0, 0, 2);");
        titleBox.getChildren().add(titleLabel);

        banner.getChildren().addAll(icon, badge, titleBox);

        // Corps de l'article
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setFocusTraversable(false);

        VBox body = new VBox(18);
        body.setPadding(new Insets(24, 30, 24, 30));
        body.setStyle("-fx-background-color: white;");

        // Méta (auteur + date)
        HBox meta = new HBox(12);
        meta.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        avatar.setPrefSize(44, 44);
        avatar.setMinSize(44, 44);
        avatar.setStyle("-fx-background-radius: 22; -fx-background-color: " + getAvatarColor(blog.getImage()) + ";");
        String initial = (blog.getPublishedBy() != null && !blog.getPublishedBy().isEmpty())
                ? String.valueOf(blog.getPublishedBy().charAt(0)).toUpperCase() : "?";
        Label avatarLabel = new Label(initial);
        avatarLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18;");
        avatar.getChildren().add(avatarLabel);

        VBox authorBox = new VBox(2);
        Label authorName = new Label(blog.getPublishedBy() != null ? blog.getPublishedBy() : "Anonyme");
        authorName.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 14;");
        String dateStr = blog.getPublishedAt() != null
                ? blog.getPublishedAt().format(DATE_FORMATTER)
                : (blog.getCreatedAt() != null ? blog.getCreatedAt().format(DATE_FORMATTER) : "");
        Label dateLabel = new Label("\uD83D\uDCC5  " + dateStr);
        dateLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12;");
        authorBox.getChildren().addAll(authorName, dateLabel);

        meta.getChildren().addAll(avatar, authorBox);

        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.3;");

        // Contenu complet
        Label fullContent = new Label(blog.getContent());
        fullContent.setWrapText(true);
        fullContent.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 14; -fx-line-spacing: 7;");

        // Bouton fermer
        Button closeBtn = new Button("Fermer");
        closeBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 10; -fx-padding: 10 28; -fx-font-size: 13; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());
        HBox closebar = new HBox();
        closebar.setAlignment(Pos.CENTER_RIGHT);
        closebar.getChildren().add(closeBtn);

        body.getChildren().addAll(meta, sep, fullContent, closebar);
        scrollPane.setContent(body);

        root.getChildren().addAll(banner, scrollPane);

        Scene scene = new Scene(root, 680, 580);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ---- Modifier article ----
    private void openEditForm(Blog blog) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Modifier l'article");
        dialog.setMinWidth(560);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #f0f4f8;");

        // En-tête
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 24, 20, 24));
        header.setStyle("-fx-background-color: linear-gradient(to right, #8e44ad, #e91e8c);");
        Label headerTitle = new Label("\u270F\uFE0F  Modifier l'Article");
        headerTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: white;");
        header.getChildren().add(headerTitle);

        VBox form = new VBox(16);
        form.setPadding(new Insets(24));

        // Titre
        VBox titreGroup = new VBox(6);
        Label titreLabel = new Label("Titre de l'article *");
        titreLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        TextField titreField = new TextField(blog.getTitre());
        titreField.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 13;");
        titreGroup.getChildren().addAll(titreLabel, titreField);

        // Catégorie
        VBox catGroup = new VBox(6);
        Label catLabel = new Label("Catégorie *");
        catLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        ComboBox<String> catBox = new ComboBox<>();
        catBox.getItems().addAll("math", "physique", "science", "informatique", "langue", "autre");
        catBox.setValue(blog.getImage() != null ? blog.getImage() : "autre");
        catBox.setMaxWidth(Double.MAX_VALUE);
        catBox.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-size: 13;");
        catGroup.getChildren().addAll(catLabel, catBox);

        // Image
        final String[] selectedImagePath = {blog.getImage()};
        VBox imageGroup = new VBox(6);
        Label imageLabel = new Label("Image de l'article (optionnel)");
        imageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        HBox imageRow = new HBox(10);
        imageRow.setAlignment(Pos.CENTER_LEFT);
        String currentImg = blog.getImage() != null ? blog.getImage() : "Aucune image";
        Label imagePathLabel = new Label(currentImg);
        imagePathLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12;");
        imagePathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(imagePathLabel, Priority.ALWAYS);
        Button browseBtn = new Button("\uD83D\uDCC1  Parcourir...");
        browseBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 8 16; -fx-font-size: 12; -fx-cursor: hand;");
        browseBtn.setOnAction(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Choisir une image");
            fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            java.io.File file = fileChooser.showOpenDialog(dialog);
            if (file != null) {
                selectedImagePath[0] = file.toURI().toString();
                imagePathLabel.setText(file.getName());
            }
        });
        imageRow.getChildren().addAll(imagePathLabel, browseBtn);
        imageGroup.getChildren().addAll(imageLabel, imageRow);

        // Contenu
        VBox contentGroup = new VBox(6);
        Label contentLabel = new Label("Contenu de l'article *");
        contentLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        TextArea contentArea = new TextArea(blog.getContent());
        contentArea.setPrefRowCount(7);
        contentArea.setWrapText(true);
        contentArea.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 13;");
        contentGroup.getChildren().addAll(contentLabel, contentArea);

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Annuler");
        cancelBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 10; -fx-padding: 10 24; -fx-font-size: 13; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("\u2705  Enregistrer les modifications");
        saveBtn.setStyle("-fx-background-color: linear-gradient(to right, #8e44ad, #e91e8c); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; " +
                "-fx-padding: 10 24; -fx-font-size: 13; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            String titre = titreField.getText().trim();
            String content = contentArea.getText().trim();
            String cat = catBox.getValue();

            if (titre.isEmpty() || content.isEmpty() || cat == null) {
                errorLabel.setText("\u26A0\uFE0F  Veuillez remplir tous les champs obligatoires (*)");
                return;
            }

            blog.setTitre(titre);
            blog.setContent(content);
            blog.setImage(cat); // image field = catégorie pour le mapping BD
            blogService.updateBlog(blog);
            loadBlogs(blogService.getAllBlogs());
            dialog.close();
        });

        buttons.getChildren().addAll(cancelBtn, saveBtn);
        form.getChildren().addAll(titreGroup, catGroup, imageGroup, contentGroup, errorLabel, buttons);
        root.getChildren().addAll(header, form);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ---- Formulaire Création ----
    @FXML
    private void openCreateForm() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Créer un nouvel article");
        dialog.setMinWidth(560);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #f0f4f8;");

        // En-tête du formulaire
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 24, 20, 24));
        header.setStyle("-fx-background-color: linear-gradient(to right, #3a7bd5, #00d2ff);");
        Label headerTitle = new Label("✏️  Nouvel Article");
        headerTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: white;");
        header.getChildren().add(headerTitle);

        // Corps du formulaire
        VBox form = new VBox(16);
        form.setPadding(new Insets(24));

        // Titre
        VBox titreGroup = new VBox(6);
        Label titreLabel = new Label("Titre de l'article *");
        titreLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        TextField titreField = new TextField();
        titreField.setPromptText("Ex: Les bases de l'algèbre...");
        titreField.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 13;");
        titreGroup.getChildren().addAll(titreLabel, titreField);

        // Catégorie
        VBox catGroup = new VBox(6);
        Label catLabel = new Label("Catégorie *");
        catLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        ComboBox<String> catBox = new ComboBox<>();
        catBox.getItems().addAll("math", "physique", "science", "informatique", "langue", "autre");
        catBox.setPromptText("Choisir une catégorie");
        catBox.setMaxWidth(Double.MAX_VALUE);
        catBox.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-size: 13;");
        catGroup.getChildren().addAll(catLabel, catBox);

        // Image - Parcourir depuis PC
        final String[] selectedImagePath = {null};
        VBox imageGroup = new VBox(6);
        Label imageLabel = new Label("Image de l'article (optionnel)");
        imageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        HBox imageRow = new HBox(10);
        imageRow.setAlignment(Pos.CENTER_LEFT);
        Label imagePathLabel = new Label("Aucune image sélectionnée");
        imagePathLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12;");
        imagePathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(imagePathLabel, Priority.ALWAYS);
        Button browseBtn = new Button("📁  Parcourir...");
        browseBtn.setStyle("-fx-background-color: #3a7bd5; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 8 16; -fx-font-size: 12; -fx-cursor: hand;");
        browseBtn.setOnAction(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Choisir une image");
            fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            java.io.File file = fileChooser.showOpenDialog(dialog);
            if (file != null) {
                selectedImagePath[0] = file.toURI().toString();
                imagePathLabel.setText(file.getName());
                imagePathLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12;");
            }
        });
        imageRow.getChildren().addAll(imagePathLabel, browseBtn);
        imageGroup.getChildren().addAll(imageLabel, imageRow);

        // Contenu + compteur X/500
        final int MAX_CONTENT = 500;
        VBox contentGroup = new VBox(4);
        Label contentLabel = new Label("Contenu de l'article *  (min. 20 · max. 500 caractères)");
        contentLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 13;");
        TextArea contentArea = new TextArea();
        contentArea.setPromptText("Rédigez votre article ici...");
        contentArea.setPrefRowCount(7);
        contentArea.setWrapText(true);
        contentArea.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 13;");
        Label contentCounter = new Label("0/" + MAX_CONTENT);
        contentCounter.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        contentArea.textProperty().addListener((obs, o, n) -> {
            if (n != null && n.length() > MAX_CONTENT) {
                contentArea.setText(o); // bloquer au-delà de 500
                return;
            }
            int len = n == null ? 0 : n.trim().length();
            contentCounter.setText(len + "/" + MAX_CONTENT);
            String color = len < 20 ? "#e74c3c" : (len > 450 ? "#f59e0b" : "#10b981");
            contentCounter.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        });
        contentGroup.getChildren().addAll(contentLabel, contentArea, contentCounter);

        // Compteur titre
        Label titreCounter = new Label("0/100");
        titreCounter.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        titreField.textProperty().addListener((obs, o, n) -> {
            int len = n == null ? 0 : n.length();
            // Bloquer à 100 caractères
            if (len > 100) { titreField.setText(o); return; }
            titreCounter.setText(len + "/100");
            titreCounter.setStyle("-fx-font-size: 11; -fx-text-fill: " + (len < 5 ? "#e74c3c" : "#10b981") + ";");
        });
        titreGroup.getChildren().add(titreCounter);

        // Message d'erreur
        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12; -fx-font-weight: bold;");
        errorLabel.setWrapText(true);

        // Anti-spam : bloquer si 2 soumissions en moins de 30 secondes
        final long[] submitTimes = {0, 0}; // [0]=avant-dernière, [1]=dernière

        // Boutons
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Annuler");
        cancelBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 10; -fx-padding: 10 24; -fx-font-size: 13; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("✅  Publier l'article");
        saveBtn.setStyle("-fx-background-color: linear-gradient(to right, #3a7bd5, #00d2ff); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; " +
                "-fx-padding: 10 24; -fx-font-size: 13; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            String titre   = titreField.getText().trim();
            String content = contentArea.getText().trim();
            String cat     = catBox.getValue();

            // ── Contrôle 1 : Titre obligatoire (5–100 caractères) ──
            if (titre.length() < 5) {
                errorLabel.setText("⚠️  Le titre doit contenir au moins 5 caractères.");
                titreField.setStyle(titreField.getStyle().replace("#d0d9e8", "#e74c3c") +
                    "; -fx-border-color: #e74c3c;");
                return;
            }

            // ── Contrôle 2 : Contenu obligatoire (min. 20 caractères) ──
            if (content.length() < 20) {
                errorLabel.setText("⚠️  Le contenu doit contenir au moins 20 caractères.");
                contentArea.setStyle(contentArea.getStyle() + "; -fx-border-color: #e74c3c;");
                return;
            }

            // ── Contrôle 3 : Catégorie obligatoire ──
            if (cat == null || cat.isBlank()) {
                errorLabel.setText("⚠️  Veuillez sélectionner une catégorie.");
                catBox.setStyle(catBox.getStyle() + "; -fx-border-color: #e74c3c;");
                return;
            }

            // ── Contrôle 4 : Anti-spam (max 2 soumissions par 30 secondes) ──
            long now = System.currentTimeMillis();
            if (submitTimes[0] > 0 && (now - submitTimes[0]) < 30_000) {
                long restant = (30_000 - (now - submitTimes[0])) / 1000;
                errorLabel.setText("⏳  Trop de soumissions. Patientez encore " + restant + " seconde(s).");
                return;
            }

            // Réinitialiser les styles
            errorLabel.setText("");
            titreField.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 13;");
            contentArea.setStyle("-fx-background-color: white; -fx-border-color: #d0d9e8; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 13;");

            // Décaler les timestamps : [0]=avant-dernière, [1]=dernière
            submitTimes[0] = submitTimes[1];
            submitTimes[1] = now;

            String currentUser = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();
            Blog newBlog = new Blog(0, titre, content, cat,
                    LocalDateTime.now(), currentUser, LocalDateTime.now());
            int createdId = blogService.addBlog(newBlog);
            if (createdId < 0) {
                errorLabel.setText("⚠️ Erreur lors de la création de l'article.");
                return;
            }
            List<Blog> updated = blogService.getAllBlogsFromDB();
            loadBlogs(updated);
            dialog.close();
            showPendingPopup();
        });

        buttons.getChildren().addAll(cancelBtn, saveBtn);
        form.getChildren().addAll(titreGroup, catGroup, imageGroup, contentGroup, errorLabel, buttons);

        root.getChildren().addAll(header, form);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ---- Tri ----
    private List<Blog> applySorting(List<Blog> blogs) {
        if (sortComboBox == null || sortComboBox.getValue() == null) return blogs;
        List<Blog> sorted = new ArrayList<>(blogs);
        switch (sortComboBox.getValue()) {
            case "Les plus aimés":
                sorted.sort((a, b) -> {
                    long la = blogService.getInteractionsByBlogId(a.getId()).stream()
                            .filter(i -> "like".equalsIgnoreCase(i.getType())).count();
                    long lb = blogService.getInteractionsByBlogId(b.getId()).stream()
                            .filter(i -> "like".equalsIgnoreCase(i.getType())).count();
                    return Long.compare(lb, la);
                });
                break;
            case "Les plus commentés":
                sorted.sort((a, b) -> {
                    long ca = blogService.getInteractionsByBlogId(a.getId()).stream()
                            .filter(i -> i.getCommentaire() != null && !i.getCommentaire().isEmpty()).count();
                    long cb = blogService.getInteractionsByBlogId(b.getId()).stream()
                            .filter(i -> i.getCommentaire() != null && !i.getCommentaire().isEmpty()).count();
                    return Long.compare(cb, ca);
                });
                break;
            default: // Les plus récents
                sorted.sort((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
                break;
        }
        return sorted;
    }

    // ---- Filtres ----
    @FXML private void handleSearch() {
        String kw = searchField.getText().trim();
        List<Blog> result = kw.isEmpty()
                ? blogService.getAllBlogs()
                : blogService.searchBlogs(kw);
        loadBlogs(applySorting(result));
    }

    @FXML private void filterTous() {
        loadBlogs(applySorting(blogService.getAllBlogs()));
    }

    @FXML private void filterMath() {
        loadBlogs(applySorting(blogService.filterByCategory("math")));
    }

    @FXML private void filterScience() {
        loadBlogs(applySorting(blogService.filterByCategory("physique", "science")));
    }

    @FXML private void filterInfo() {
        loadBlogs(applySorting(blogService.filterByCategory("informatique")));
    }

    @FXML private void filterLangue() {
        loadBlogs(applySorting(blogService.filterByCategory("langue")));
    }

    @FXML private void filterAutre() {
        loadBlogs(applySorting(blogService.filterByCategory("autre", "study-tips")));
    }

    /** Popup informant le tuteur que son article est en attente de validation */
    /** Retourne l'id effectif du user connecté (résout le fallback mock id=0 via la BD) */
    private int resolveCurrentUserId() {
        tn.esprit.fahamni.Models.User u = tn.esprit.fahamni.utils.SessionManager.getCurrentUser();
        if (u == null) return 0;
        if (u.getId() > 0) return u.getId();
        // Fallback : chercher par email dans la BD
        try {
            java.sql.Connection c = tn.esprit.fahamni.utils.MyDataBase.getInstance().getCnx();
            if (c != null && u.getEmail() != null) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id FROM user WHERE email = ? LIMIT 1")) {
                    ps.setString(1, u.getEmail());
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()) return rs.getInt("id");
                }
            }
        } catch (Exception ignored) {}
        return 1; // fallback ultime
    }

    private void refreshBellBadge() {
        try {
            if (notifBellBadge == null) return;
            int uid = resolveCurrentUserId();
            if (uid <= 0) return;
            int count = notifService.countUnreadForUser(uid);
            if (count > 0) {
                notifBellBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                notifBellBadge.setVisible(true);
                notifBellBadge.setManaged(true);
            } else {
                notifBellBadge.setVisible(false);
                notifBellBadge.setManaged(false);
            }
        } catch (Exception e) {
            System.err.println("refreshBellBadge: " + e.getMessage());
        }
    }

    @FXML
    private void handleNotifBellClick(MouseEvent event) {
        if (notifPopup != null && notifPopup.isShowing()) {
            notifPopup.hide();
            return;
        }

        tn.esprit.fahamni.Models.User u = tn.esprit.fahamni.utils.SessionManager.getCurrentUser();
        int uid = resolveCurrentUserId();
        // Charger TOUTES les notifs (lues + non lues) pour ne rien manquer
        List<Notification> notifs = uid > 0
                ? notifService.getAllForUser(uid)
                : new ArrayList<>();
        long unreadCount = notifs.stream().filter(n -> !n.isRead()).count();

        VBox container = new VBox(0);
        container.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.18),18,0,0,4);" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 14; -fx-border-width: 1;");
        container.setPrefWidth(340);

        // En-tête
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 16));
        header.setStyle("-fx-background-color: linear-gradient(to right,#3a7bd5,#00d2ff);" +
                        "-fx-background-radius: 14 14 0 0;");
        Label titleLbl = new Label("🔔  Mes Notifications");
        titleLbl.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: white;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        String countText = unreadCount > 0 ? unreadCount + " non lu(s)" : (notifs.isEmpty() ? "Aucune" : notifs.size() + " au total");
        Label cntLbl = new Label(countText);
        cntLbl.setStyle("-fx-font-size: 11; -fx-text-fill: rgba(255,255,255,0.85);");
        header.getChildren().addAll(titleLbl, sp, cntLbl);
        container.getChildren().add(header);

        if (notifs.isEmpty()) {
            Label empty = new Label("Aucune notification pour le moment.");
            empty.setPadding(new Insets(24, 16, 24, 16));
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");
            container.getChildren().add(empty);
        } else {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(Math.min(notifs.size() * 76.0, 300));
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

            VBox list = new VBox(0);
            for (int i = 0; i < notifs.size(); i++) {
                Notification n = notifs.get(i);
                boolean approved = n.getMessage() != null &&
                        (n.getMessage().contains("approuve") || n.getMessage().contains("publie"));
                boolean refused = n.getMessage() != null && n.getMessage().contains("refuse");
                boolean isUnread = !n.isRead();

                VBox item = new VBox(4);
                item.setPadding(new Insets(10, 16, 10, 16));
                String bg = isUnread
                    ? (approved ? "#f0fdf4" : "#fff7ed")
                    : "#f8fafc"; // lu = gris clair
                item.setStyle("-fx-background-color: " + bg + ";");

                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label icon = new Label(approved ? "✅" : (refused ? "❌" : "📋"));
                icon.setStyle("-fx-font-size: 15;");
                Label msg = new Label(n.getMessage() != null ? n.getMessage() : "");
                msg.setWrapText(true);
                String msgColor = isUnread
                    ? (approved ? "#065f46" : "#92400e")
                    : "#64748b"; // lu = couleur atténuée
                msg.setStyle("-fx-font-size: 12; -fx-text-fill: " + msgColor + ";");
                msg.setMaxWidth(230);
                HBox.setHgrow(msg, Priority.ALWAYS);

                HBox rightBox = new HBox(4);
                rightBox.setAlignment(Pos.TOP_RIGHT);
                if (isUnread) {
                    Label badge = new Label("Nouveau");
                    badge.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;" +
                        "-fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6;" +
                        "-fx-padding: 1 5;");
                    rightBox.getChildren().add(badge);
                }
                row.getChildren().addAll(icon, msg, rightBox);

                Label date = new Label(n.getCreatedAt() != null ? n.getCreatedAt().format(DT_FMT) : "");
                date.setStyle("-fx-font-size: 10; -fx-text-fill: #94a3b8;");

                item.getChildren().addAll(row, date);
                list.getChildren().add(item);

                if (i < notifs.size() - 1) {
                    Separator sep = new Separator();
                    sep.setStyle("-fx-opacity: 0.3;");
                    list.getChildren().add(sep);
                }
            }
            scroll.setContent(list);
            container.getChildren().add(scroll);

            if (unreadCount > 0) {
                Button markRead = new Button("✓  Tout marquer comme lu");
                markRead.setMaxWidth(Double.MAX_VALUE);
                markRead.setStyle(
                    "-fx-background-color: #f8faff; -fx-text-fill: #3a7bd5;" +
                    "-fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 10;" +
                    "-fx-cursor: hand; -fx-border-color: #e2e8f0;" +
                    "-fx-border-width: 1 0 0 0;");
                markRead.setOnAction(e -> {
                    notifService.markAllReadForUser(uid);
                    notifPopup.hide();
                    refreshBellBadge();
                });
                container.getChildren().add(markRead);
            }
        }

        notifPopup = new Popup();
        notifPopup.getContent().add(container);
        notifPopup.setAutoHide(true);

        javafx.scene.Node source = (javafx.scene.Node) event.getSource();
        double x = source.localToScreen(source.getBoundsInLocal()).getMaxX() - 340;
        double y = source.localToScreen(source.getBoundsInLocal()).getMaxY() + 8;
        notifPopup.show(source.getScene().getWindow(), x, y);

        // Marquer comme lu après affichage
        if (unreadCount > 0) {
            notifService.markAllReadForUser(uid);
            refreshBellBadge();
        }
    }

    private void showPendingPopup() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Article soumis");
        popup.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 14;");
        root.setPrefWidth(400);

        Label icon = new Label("⏳");
        icon.setStyle("-fx-font-size: 40;");

        Label title = new Label("Article soumis avec succès !");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label msg = new Label(
            "Votre article a été soumis et est en attente de validation par l'administrateur.\n" +
            "Vous recevrez une notification dès qu'il sera approuvé et publié.");
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 13; -fx-text-fill: #64748b; -fx-text-alignment: center;");
        msg.setMaxWidth(340);

        Button ok = new Button("OK, compris !");
        ok.setStyle(
            "-fx-background-color: #3a7bd5; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-font-size: 13; " +
            "-fx-background-radius: 8; -fx-padding: 9 28; -fx-cursor: hand;");
        ok.setOnAction(e -> popup.close());

        root.getChildren().addAll(icon, title, msg, ok);
        popup.setScene(new Scene(root));
        popup.showAndWait();
    }
}
