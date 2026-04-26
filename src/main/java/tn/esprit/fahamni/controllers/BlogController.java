package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.Interaction;
import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.services.BlogService;
import tn.esprit.fahamni.services.NotificationService;
import tn.esprit.fahamni.services.TextToSpeechService;
import tn.esprit.fahamni.services.TranslationService;
import tn.esprit.fahamni.services.ProfanityFilterService;
import tn.esprit.fahamni.services.SpellCheckService;
import tn.esprit.fahamni.services.ContentGeneratorService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.PauseTransition;
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
    private final TextToSpeechService ttsService = new TextToSpeechService();
    private final TranslationService translationService = new TranslationService();
    private final ProfanityFilterService profanityFilter = new ProfanityFilterService();
    private final SpellCheckService spellCheckService = new SpellCheckService();
    private final ContentGeneratorService contentGenerator = new ContentGeneratorService();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private Popup notifPopup;

    private static final int PAGE_SIZE = 6;
    private static final int BLOG_CARD_COLUMNS = 3;
    private static final double BLOG_CARD_COLLAPSED_HEIGHT = 244;
    private static final double BLOG_CARD_EXCERPT_HEIGHT = 34;
    private int currentPage = 1;
    private List<Blog> currentBlogList = new ArrayList<>();
    private boolean showRecommendations = true;

    @FXML private TextField searchField;
    @FXML private VBox blogsContainer;
    @FXML private HBox paginationBar;
    @FXML private Label notifBellBadge;
    @FXML private Button createArticleBtn;
    @FXML private Button btnTous;
    @FXML private Button btnMath;
    @FXML private Button btnScience;
    @FXML private Button btnInfo;
    @FXML private Button btnLangue;
    @FXML private Button btnAutre;
    @FXML private Button btnFavoris;
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
        // Bouton "Creer un article" : visible uniquement pour les tuteurs
        tn.esprit.fahamni.Models.User u = tn.esprit.fahamni.utils.SessionManager.getCurrentUser();
        boolean canCreate = u != null && u.getRole() == tn.esprit.fahamni.Models.UserRole.TUTOR;
        if (createArticleBtn != null) {
            createArticleBtn.setVisible(canCreate);
            createArticleBtn.setManaged(canCreate);
        }

        setActiveCategoryButton(btnTous);
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
            empty.getStyleClass().add("blog-empty-label");
            blogsContainer.getChildren().add(empty);
            buildPaginationBar(0);
            return;
        }

        int total = blogs.size();
        int fromIdx = (currentPage - 1) * PAGE_SIZE;
        int toIdx = Math.min(fromIdx + PAGE_SIZE, total);
        List<Blog> pageBlogs = blogs.subList(fromIdx, toIdx);
        GridPane grid = buildBlogGrid(pageBlogs);
        blogsContainer.getChildren().add(grid);
        buildPaginationBar(total);
        if (currentPage == 1 && showRecommendations) buildRecommendationSection();
        return;
/*

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
*/
    }

    private GridPane buildBlogGrid(List<Blog> pageBlogs) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setMaxWidth(Double.MAX_VALUE);

        for (int col = 0; col < BLOG_CARD_COLUMNS; col++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / BLOG_CARD_COLUMNS);
            constraints.setFillWidth(true);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }

        for (int index = 0; index < pageBlogs.size(); index++) {
            VBox card;
            try {
                card = createBlogCard(pageBlogs.get(index));
            } catch (Exception ex) {
                card = new VBox();
            }
            card.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
            grid.add(card, index % BLOG_CARD_COLUMNS, index / BLOG_CARD_COLUMNS);
        }

        return grid;
    }

    private void buildRecommendationSection() {
        List<tn.esprit.fahamni.Models.Blog> recs = blogService.getRecommendedBlogs(3);
        boolean isPersonalized = !recs.isEmpty();
        if (!isPersonalized) recs = blogService.getPopularBlogs(3);
        if (recs.isEmpty()) return;

        // Titre de section
        Label title = new Label(isPersonalized ? "🎯  Recommandé pour toi" : "🔥  Articles populaires");
        title.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #5068d1; -fx-padding: 18 0 8 0;");

        Label subtitle = new Label(isPersonalized
                ? "Basé sur tes articles aimés"
                : "Les articles les plus appréciés de la communauté");
        subtitle.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8; -fx-padding: 0 0 10 0;");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #e3e6ef;");

        VBox header = new VBox(2, sep, title, subtitle);
        header.setPadding(new Insets(8, 0, 4, 0));
        blogsContainer.getChildren().add(header);

        // Grille des cartes recommandées
        HBox row = new HBox(14);
        row.setFillHeight(true);
        row.setPadding(new Insets(0, 0, 20, 0));
        for (tn.esprit.fahamni.Models.Blog rec : recs) {
            try {
                VBox card = createBlogCard(rec);
                HBox.setHgrow(card, Priority.ALWAYS);
                row.getChildren().add(card);
            } catch (Exception ignored) {}
        }
        // Remplir les cases vides si moins de 3 résultats
        while (row.getChildren().size() < 3) {
            Region filler = new Region();
            HBox.setHgrow(filler, Priority.ALWAYS);
            row.getChildren().add(filler);
        }
        blogsContainer.getChildren().add(row);
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
        info.setStyle("-fx-font-size: 12; -fx-text-fill: #6f788b; -fx-font-weight: bold;");
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
                    dots.setStyle("-fx-font-size: 12; -fx-text-fill: #98a1b4; -fx-padding: 0 4;");
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
            btn.setStyle("-fx-background-color: linear-gradient(to right, #6b5dd3, #5068d1); -fx-text-fill: white; " +
                    "-fx-font-weight: bold; -fx-background-radius: 10; " +
                    "-fx-padding: 5 11; -fx-font-size: 12; -fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(95,73,191,0.16), 8, 0.14, 0, 2);");
        } else {
            btn.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff, #f6f4ff); -fx-text-fill: #4a5167; " +
                    "-fx-border-color: #ddd8f4; -fx-border-width: 1; -fx-border-radius: 10; " +
                    "-fx-background-radius: 10; -fx-padding: 5 11; -fx-font-size: 12; -fx-cursor: hand;");
        }
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    private VBox createBlogCard(Blog blog) {
        String accent = getAvatarColor(blog.getImage());

        // Carte — hauteur naturelle, toutes les cartes d'une même ligne s'égalisent via HBox
        VBox card = new VBox(0);
        card.getStyleClass().add("blog-card-shell");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        double cardHeight = blog.isShared() ? BLOG_CARD_COLLAPSED_HEIGHT + 22 : BLOG_CARD_COLLAPSED_HEIGHT;
        card.setMinHeight(cardHeight);
        card.setPrefHeight(cardHeight);
        card.setMaxHeight(cardHeight);
        card.setStyle("-fx-border-color: " + accent + " #e3e6ef #e3e6ef #e3e6ef; -fx-border-width: 3px 1px 1px 1px;");

        // ── Bande colorée fine en haut ──
        HBox topBar = new HBox(8);
        topBar.setPrefHeight(28);
        topBar.setMinHeight(28);
        topBar.setMaxHeight(28);
        topBar.setPadding(new Insets(10, 14, 0, 14));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("blog-card-meta-row");

        Label catIcon = new Label(getCategoryIcon(blog.getImage()));
        catIcon.getStyleClass().add("blog-card-banner-icon");
        catIcon.setTextFill(Color.web(accent));
        Label catBadge = new Label(getCategoryLabel(blog.getImage()));
        catBadge.getStyleClass().add("blog-card-banner-badge");
        catBadge.setStyle("-fx-background-color: " + withOpacity(accent, 0.10) + "; "
                + "-fx-border-color: " + withOpacity(accent, 0.22) + "; "
                + "-fx-text-fill: " + accent + ";");

        Region barSpacer = new Region(); HBox.setHgrow(barSpacer, Priority.ALWAYS);

        String dateStr2 = blog.getPublishedAt() != null
                ? blog.getPublishedAt().format(DATE_FORMATTER)
                : (blog.getCreatedAt() != null ? blog.getCreatedAt().format(DATE_FORMATTER) : "");
        Label dateSmall = new Label(dateStr2);
        dateSmall.getStyleClass().add("blog-card-banner-date");
        dateSmall.setTextFill(Color.web("#8b93a7"));

        // Badge "Nouveau" si publié il y a moins de 24h
        boolean isNew = false;
        java.time.LocalDateTime refDate = blog.getPublishedAt() != null ? blog.getPublishedAt() : blog.getCreatedAt();
        if (refDate != null && refDate.isAfter(java.time.LocalDateTime.now().minusHours(24))) {
            isNew = true;
            Label newBadge = new Label("Nouveau");
            newBadge.setStyle(
                "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; " +
                "-fx-font-size: 9; -fx-font-weight: bold; " +
                "-fx-background-radius: 20; -fx-padding: 2 7;");
            topBar.getChildren().addAll(catIcon, catBadge, newBadge, barSpacer, dateSmall);
        } else {
            topBar.getChildren().addAll(catIcon, catBadge, barSpacer, dateSmall);
        }
        card.getChildren().add(topBar);

        // ── Corps principal ──
        VBox body = new VBox(7);
        body.setPadding(new Insets(10, 14, 8, 14));

        // Titre — 1 ligne tronquée
        Label title = new Label(blog.getTitre());
        title.setMaxWidth(Double.MAX_VALUE);
        title.setWrapText(false);
        title.getStyleClass().add("blog-card-title");
        title.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        title.setMinHeight(22);
        title.setPrefHeight(22);
        title.setMaxHeight(22);

        // Auteur inline — pour un article partagé, on affiche l'auteur original
        String displayAuthor = blog.isShared() && blog.getOriginalAuthor() != null
                ? blog.getOriginalAuthor() : (blog.getPublishedBy() != null ? blog.getPublishedBy() : "Anonyme");
        HBox authorRow = new HBox(5);
        authorRow.setAlignment(Pos.CENTER_LEFT);
        StackPane avatar = new StackPane();
        avatar.setPrefSize(20, 20); avatar.setMinSize(20, 20); avatar.setMaxSize(20, 20);
        avatar.setStyle("-fx-background-radius: 999; -fx-background-color: " + accent + ";");
        String initial = !displayAuthor.isEmpty() ? String.valueOf(displayAuthor.charAt(0)).toUpperCase() : "?";
        Label avatarLabel = new Label(initial);
        avatarLabel.getStyleClass().add("blog-card-author-initial");
        avatar.getChildren().add(avatarLabel);
        Label authorName = new Label(displayAuthor);
        authorName.getStyleClass().add("blog-card-author-name");
        authorRow.getChildren().addAll(avatar, authorName);

        // Séparateur fin
        Separator sep1 = new Separator();
        sep1.getStyleClass().add("blog-card-separator");

        // Extrait — tronqué à 80 chars, 2 lignes max
        String rawContent = blog.getContent() != null ? blog.getContent() : "";
        String cleanContent = cleanMarkdown(rawContent);
        boolean isTruncated = cleanContent.length() > 80;
        String excerpt = isTruncated ? cleanContent.substring(0, 80) + "..." : cleanContent;

        Label contentLabel = new Label(excerpt);
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("blog-card-excerpt");
        contentLabel.setMinHeight(BLOG_CARD_EXCERPT_HEIGHT);
        contentLabel.setPrefHeight(BLOG_CARD_EXCERPT_HEIGHT);
        contentLabel.setMaxHeight(BLOG_CARD_EXCERPT_HEIGHT);
        final boolean[] expanded = {false};

        if (blog.isShared()) {
            String sharerName = blog.getPublishedBy() != null ? blog.getPublishedBy() : "Quelqu'un";
            HBox shareRow = new HBox(5);
            shareRow.setAlignment(Pos.CENTER_LEFT);
            Label shareIcon = new Label("🔁");
            shareIcon.setStyle("-fx-font-size: 11;");
            Label shareLabel = new Label(sharerName + " a partagé");
            shareLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #0369a1; -fx-font-style: italic; -fx-font-weight: bold;");
            shareRow.getChildren().addAll(shareIcon, shareLabel);
            body.getChildren().addAll(shareRow, title, authorRow, sep1, contentLabel);
        } else {
            body.getChildren().addAll(title, authorRow, sep1, contentLabel);
        }
        card.getChildren().add(body);

        // ── Barre d'actions ──
        List<Interaction> interactions = blogService.getInteractionsByBlogId(blog.getId());
        long likesCount = interactions.stream().filter(i -> "like".equalsIgnoreCase(i.getType())).count();
        long commentsCount = interactions.stream()
                .filter(i -> i.getCommentaire() != null && !i.getCommentaire().isEmpty()).count();

        // Identité du user connecté — toujours résoudre le vrai ID en BD
        tn.esprit.fahamni.Models.User currentUser = tn.esprit.fahamni.utils.SessionManager.getCurrentUser();
        int dbUserId = resolveCurrentUserId();
        String currentUserName = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();

        // isMyArticle : ID en priorité, nom uniquement si publisherId absent
        boolean isMyArticle = (blog.getPublisherId() > 0 && blog.getPublisherId() == dbUserId)
                || (blog.getPublisherId() <= 0
                    && blog.getPublishedBy() != null && currentUserName != null
                    && !currentUserName.equals("Utilisateur")
                    && blog.getPublishedBy().trim().equalsIgnoreCase(currentUserName.trim()));

        boolean alreadyLiked = blogService.hasUserLiked(blog.getId(), dbUserId);

        Separator sepAct = new Separator(); sepAct.getStyleClass().add("blog-card-separator");
        card.getChildren().add(sepAct);
        card.setMinWidth(0);

        // Ligne 1 : réactions + commentaires
        HBox actBar = new HBox(5);
        actBar.setPadding(new Insets(6, 10, 2, 10));
        actBar.setAlignment(Pos.CENTER_LEFT);
        actBar.setMinWidth(0);

        // ── Réactions multiples 👍 ❤️ 😮 ──
        int userReaction = blogService.getUserReaction(blog.getId(), dbUserId);
        long r1 = blogService.countReaction(blog.getId(), 1);
        long r2 = blogService.countReaction(blog.getId(), 2);
        long r3 = blogService.countReaction(blog.getId(), 3);

        Button btn1 = new Button("👍 " + r1);
        Button btn2 = new Button("❤️ " + r2);
        Button btn3 = new Button("😮 " + r3);
        Button likeBtn = btn1; // alias pour compatibilité animation

        styleReactionBtn(btn1, 1, userReaction == 1);
        styleReactionBtn(btn2, 2, userReaction == 2);
        styleReactionBtn(btn3, 3, userReaction == 3);

        final int[] currentReaction = {userReaction};

        java.util.function.BiConsumer<Button, Integer> onReact = (btn, type) -> {
            if (currentReaction[0] == type) {
                // Retirer la réaction
                blogService.removeReaction(blog.getId(), dbUserId);
                currentReaction[0] = 0;
                styleReactionBtn(btn1, 1, false);
                styleReactionBtn(btn2, 2, false);
                styleReactionBtn(btn3, 3, false);
            } else {
                // Nouvelle réaction
                blogService.addReaction(blog.getId(), type, currentUserName);
                currentReaction[0] = type;
                styleReactionBtn(btn1, 1, type == 1);
                styleReactionBtn(btn2, 2, type == 2);
                styleReactionBtn(btn3, 3, type == 3);
            }
            animateCounter(btn1, "👍 " + blogService.countReaction(blog.getId(), 1));
            animateCounter(btn2, "❤️ " + blogService.countReaction(blog.getId(), 2));
            animateCounter(btn3, "😮 " + blogService.countReaction(blog.getId(), 3));
        };

        btn1.setOnAction(e -> onReact.accept(btn1, 1));
        btn2.setOnAction(e -> onReact.accept(btn2, 2));
        btn3.setOnAction(e -> onReact.accept(btn3, 3));

        Button commentBtn = new Button("\uD83D\uDCAC " + commentsCount);
        styleCompactBtn(commentBtn, "#eff2ff", "#5068d1", "#d9def6");

        Region actSpacer = new Region(); HBox.setHgrow(actSpacer, Priority.ALWAYS);

        // Compteur de vues
        Label viewsLabel = new Label("\uD83D\uDC41 " + blog.getViews());
        viewsLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");

        // Bouton Voir plus / Voir moins -- expand in-place
        Button readBtn = new Button("Voir plus");
        readBtn.getStyleClass().add("blog-card-read-button");
        readBtn.setMinWidth(Region.USE_PREF_SIZE);
        if (!isTruncated) { readBtn.setVisible(false); readBtn.setManaged(false); }
        readBtn.setOnAction(e -> {
            if (!expanded[0]) {
                contentLabel.setText(cleanContent);
                contentLabel.setMinHeight(Region.USE_COMPUTED_SIZE);
                contentLabel.setPrefHeight(Region.USE_COMPUTED_SIZE);
                contentLabel.setMaxHeight(Double.MAX_VALUE);
                readBtn.setText("Voir moins");
                blogService.incrementViews(blog.getId());
                blog.setViews(blog.getViews() + 1);
                viewsLabel.setText("\uD83D\uDC41 " + blog.getViews());
                expanded[0] = true;
            } else {
                contentLabel.setText(excerpt);
                contentLabel.setMinHeight(BLOG_CARD_EXCERPT_HEIGHT);
                contentLabel.setPrefHeight(BLOG_CARD_EXCERPT_HEIGHT);
                contentLabel.setMaxHeight(BLOG_CARD_EXCERPT_HEIGHT);
                readBtn.setText("Voir plus");
                expanded[0] = false;
            }
        });


        // Bouton Favori
        boolean[] favState = {blogService.isFavorite(blog.getId())};
        Button favBtn = new Button(favState[0] ? "\u2605" : "\u2606");
        favBtn.setTooltip(new Tooltip(favState[0] ? "Retirer des favoris" : "Ajouter aux favoris"));
        styleCompactBtn(favBtn,
            favState[0] ? "#fffbeb" : "#f8fafc",
            favState[0] ? "#d97706" : "#94a3b8",
            favState[0] ? "#fde68a" : "#e2e8f0");
        favBtn.setOnAction(e -> {
            boolean success;
            if (favState[0]) {
                success = blogService.removeFavorite(blog.getId());
                if (success) favState[0] = false;
            } else {
                success = blogService.addFavorite(blog.getId());
                if (success) favState[0] = true;
            }
            favBtn.setText(favState[0] ? "\u2605" : "\u2606");
            styleCompactBtn(favBtn,
                favState[0] ? "#fffbeb" : "#f8fafc",
                favState[0] ? "#d97706" : "#94a3b8",
                favState[0] ? "#fde68a" : "#e2e8f0");
            favBtn.setTooltip(new Tooltip(favState[0] ? "Retirer des favoris" : "Ajouter aux favoris"));
        });

        // Bouton Partager
        Button shareBtn = new Button("\uD83D\uDD17");
        styleCompactBtn(shareBtn, "#f3f1ff", "#6656ca", "#ddd6fb");
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
        // Ligne 2 : partage + lire + modifier/supprimer
        HBox actBar2 = new HBox(5);
        actBar2.setPadding(new Insets(2, 10, 8, 10));
        actBar2.setAlignment(Pos.CENTER_LEFT);
        actBar2.setMinWidth(0);

        Region actSpacer2 = new Region(); HBox.setHgrow(actSpacer2, Priority.ALWAYS);

        // Bouton Traduire : petit popup flottant sous le bouton
        final String[] shownContent = {excerpt};

        Button translateBtn = new Button("\uD83C\uDF10");
        styleCompactBtn(translateBtn, "#fefce8", "#a16207", "#fde68a");
        translateBtn.setTooltip(new Tooltip("Traduire l'article"));
        translateBtn.setOnAction(e -> {
            // Popup flottant style "prompt"
            javafx.stage.Popup langPopup = new javafx.stage.Popup();
            langPopup.setAutoHide(true);

            VBox popBox = new VBox(0);
            popBox.setStyle(
                "-fx-background-color: white; "
                + "-fx-border-color: #e2e8f0; -fx-border-width: 1; "
                + "-fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 4);");
            popBox.setMinWidth(160);

            String[][] langs = {
                {"fr", "\uD83C\uDDEB\uD83C\uDDF7", "Fran\u00e7ais"},
                {"en", "\uD83C\uDDEC\uD83C\uDDE7", "English"},
                {"ar", "\uD83C\uDDF8\uD83C\uDDE6", "Arabe"}
            };

            for (int li = 0; li < langs.length; li++) {
                final String code  = langs[li][0];
                final String flag  = langs[li][1];
                final String name  = langs[li][2];
                final boolean last = (li == langs.length - 1);

                Button lb = new Button();
                lb.setMaxWidth(Double.MAX_VALUE);
                lb.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                String border = last ? "" : "-fx-border-color: #f1f5f9; -fx-border-width: 0 0 1 0;";
                lb.setStyle(
                    "-fx-background-color: white; -fx-text-fill: #1e293b; "
                    + "-fx-font-size: 13; -fx-padding: 9 16; -fx-cursor: hand; "
                    + "-fx-background-radius: 0; " + border);
                lb.setText(flag + "  " + name);
                lb.setOnMouseEntered(ev -> lb.setStyle(
                    "-fx-background-color: #f0f9ff; -fx-text-fill: #0369a1; "
                    + "-fx-font-size: 13; -fx-padding: 9 16; -fx-cursor: hand; "
                    + "-fx-background-radius: 0; " + border));
                lb.setOnMouseExited(ev -> lb.setStyle(
                    "-fx-background-color: white; -fx-text-fill: #1e293b; "
                    + "-fx-font-size: 13; -fx-padding: 9 16; -fx-cursor: hand; "
                    + "-fx-background-radius: 0; " + border));

                lb.setOnAction(ev -> {
                    langPopup.hide();
                    if (code.equals("fr")) {
                        contentLabel.setText(excerpt);
                        shownContent[0] = excerpt;
                    } else {
                        contentLabel.setText("\u23F3 Traduction en cours...");
                        new Thread(() -> {
                            String tr = translationService.translate(blog.getContent(), code);
                            javafx.application.Platform.runLater(() -> {
                                if (tr != null && !tr.isBlank()) {
                                    String trEx = tr.length() > 80 ? tr.substring(0, 80) + "..." : tr;
                                    contentLabel.setText(trEx);
                                    shownContent[0] = trEx;
                                } else {
                                    contentLabel.setText(excerpt);
                                }
                            });
                        }).start();
                    }
                });
                popBox.getChildren().add(lb);
            }

            langPopup.getContent().add(popBox);
            javafx.geometry.Bounds b2 = translateBtn.localToScreen(translateBtn.getBoundsInLocal());
            langPopup.show(translateBtn.getScene().getWindow(), b2.getMinX(), b2.getMaxY() + 4);
        });

        if (isMyArticle) {
            if (!blog.isShared()) {
                // Article original : Modifier + Supprimer
                Button editBtn = new Button("\u270F");
                styleCompactBtn(editBtn, "#f4f0ff", "#7a5cd8", "#ddd3fb");
                editBtn.setTooltip(new Tooltip("Modifier mon article"));
                editBtn.setOnAction(e -> openEditForm(blog));

                Button deleteArticleBtn = new Button("\uD83D\uDDD1");
                styleCompactBtn(deleteArticleBtn, "#fff1f2", "#d1435b", "#fecdd3");
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
                actBar.getChildren().addAll(btn1, btn2, btn3, actSpacer, viewsLabel, commentBtn);
                actBar2.getChildren().addAll(favBtn, shareBtn, buildShareBtn(blog), editBtn, deleteArticleBtn, actSpacer2, buildTtsBtn(blog), translateBtn, readBtn);
            } else {
                // Article partage : bouton Annuler le partage
                Button unshareBtn = new Button("\uD83D\uDD01\u2715");
                styleCompactBtn(unshareBtn, "#fff7ed", "#c2410c", "#fed7aa");
                unshareBtn.setTooltip(new Tooltip("Annuler ce partage"));
                unshareBtn.setOnAction(e -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Retirer ce partage de votre fil ?");
                    confirm.setHeaderText(null);
                    confirm.showAndWait().ifPresent(r -> {
                        if (r == javafx.scene.control.ButtonType.OK) {
                            blogService.unshareArticle(blog.getId());
                            loadBlogs(blogService.getAllBlogs());
                        }
                    });
                });
                actBar.getChildren().addAll(btn1, btn2, btn3, actSpacer, viewsLabel, commentBtn);
                actBar2.getChildren().addAll(favBtn, unshareBtn, actSpacer2, buildTtsBtn(blog), translateBtn, readBtn);
            }
        } else {
            actBar.getChildren().addAll(btn1, btn2, btn3, actSpacer, viewsLabel, commentBtn);
            actBar2.getChildren().addAll(favBtn, shareBtn, buildShareBtn(blog), actSpacer2, buildTtsBtn(blog), translateBtn, readBtn);
        }

        VBox actionsBox = new VBox(0, actBar, actBar2);
        actionsBox.setMinWidth(0);
        card.getChildren().add(actionsBox);

        // ── Section commentaires (cachée par défaut) ──

        VBox commentsSection = new VBox(6);
        commentsSection.setPadding(new Insets(0, 10, 10, 10));
        commentsSection.setManaged(false);
        commentsSection.setVisible(false);
        commentsSection.getStyleClass().add("blog-card-comments-shell");

        Separator sepCom = new Separator(); sepCom.getStyleClass().add("blog-card-separator");

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
            "-fx-background-color: transparent; -fx-text-fill: #5f49bf; " +
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
                // addInteraction gere le filtre profanite, la sauvegarde et la notif admin
                String err = blogService.addInteraction(blog.getId(), "commentaire", comment, currentUserName);

                if ("PROFANITY".equals(err)) {
                    // Commentaire sauvegarde avec is_flagged=1 et admin notifie
                    Alert warn = new Alert(Alert.AlertType.WARNING);
                    warn.setTitle("Commentaire signal\u00e9");
                    warn.setHeaderText("\u26a0\ufe0f  Commentaire signal\u00e9 \u00e0 l'administrateur");
                    warn.setContentText(
                        "Votre commentaire contient un mot inappropri\u00e9. "
                        + "Il a \u00e9t\u00e9 enregistr\u00e9 et signal\u00e9 \u00e0 l'administrateur pour mod\u00e9ration. "
                        + "Il ne sera pas visible par les autres utilisateurs.");
                    warn.showAndWait();
                    commentField.clear();
                    commentCounter.setText("0/" + MAX_COMMENT);
                    return;
                }

                if (err != null) {
                    new Alert(Alert.AlertType.ERROR, err).showAndWait();
                    return;
                }

                // Commentaire normal : afficher dans l'UI
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
            if (v) {
                card.setMinHeight(cardHeight);
                card.setPrefHeight(cardHeight);
                card.setMaxHeight(cardHeight);
            } else {
                card.setMinHeight(Region.USE_COMPUTED_SIZE);
                card.setPrefHeight(Region.USE_COMPUTED_SIZE);
                card.setMaxHeight(Double.MAX_VALUE);
            }
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

    private void setDialogFieldError(Control control, boolean invalid) {
        if (control == null) {
            return;
        }

        if (invalid) {
            if (!control.getStyleClass().contains("blog-dialog-field-error")) {
                control.getStyleClass().add("blog-dialog-field-error");
            }
            return;
        }

        control.getStyleClass().remove("blog-dialog-field-error");
    }

    private void styleCompactBtn(Button btn, String bg, String fg) {
        styleCompactBtn(btn, bg, fg, "transparent");
    }

    private void styleCompactBtn(Button btn, String bg, String fg, String border) {
        btn.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                "-fx-border-color: " + border + "; -fx-border-width: 1; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-padding: 4 10; -fx-font-size: 11; -fx-cursor: hand;");
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    private void styleReactionBtn(Button btn, int reactionType, boolean active) {
        String background;
        String text;
        String border;

        switch (reactionType) {
            case 1 -> {
                background = active ? "#eef2ff" : "#f8faff";
                text = active ? "#4f63d2" : "#6b7bb5";
                border = active ? "#cfd8ff" : "#e1e8f7";
            }
            case 2 -> {
                background = active ? "#fff1f5" : "#fff7f9";
                text = active ? "#d14378" : "#c16c90";
                border = active ? "#ffc9da" : "#f4dbe4";
            }
            case 3 -> {
                background = active ? "#fff6e9" : "#fffaf2";
                text = active ? "#d48a2f" : "#bf9358";
                border = active ? "#ffd9a8" : "#f2e4c9";
            }
            default -> {
                background = active ? "#f2efff" : "#f8f9fc";
                text = active ? "#5f49bf" : "#546074";
                border = active ? "#d7cff8" : "#e1e5ef";
            }
        }

        btn.setStyle("-fx-background-color: " + background + "; -fx-text-fill: " + text + "; " +
                "-fx-background-radius: 8; -fx-padding: 4 10; -fx-font-size: 12; -fx-cursor: hand; " +
                "-fx-border-color: " + border + "; -fx-border-radius: 8; -fx-border-width: " + (active ? "1.5" : "1") + ";");
        btn.setMinWidth(Region.USE_PREF_SIZE);
    }

    // ---- Construction d'une ligne de commentaire ----
    private HBox buildCommentRow(String author, String commentText, boolean isMine,
                                  VBox commentsBox, Button commentBtn) {
        return buildCommentRow(author, commentText, isMine, commentsBox, commentBtn, false);
    }

    private HBox buildCommentRow(String author, String commentText, boolean isMine,
                                  VBox commentsBox, Button commentBtn, boolean isReply) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        if (isReply) {
            row.setPadding(new Insets(0, 0, 0, 42)); // indentation réponse
        }

        // Avatar avec initiale de l'auteur
        StackPane avatar = new StackPane();
        avatar.setPrefSize(32, 32);
        avatar.setMinSize(32, 32);
        String color = isMine ? "#5d68d8" : "#7d5bd8";
        avatar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 16;");
        String initial = (author != null && !author.isEmpty())
                ? String.valueOf(author.charAt(0)).toUpperCase() : "?";
        Label initLabel = new Label(initial);
        initLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;");
        avatar.getChildren().add(initLabel);

        // Bulle avec nom + texte
        VBox bubble = new VBox(4);
        bubble.setStyle("-fx-background-color: " + (isMine ? "#eff2ff" : "#f7f5ff") + "; " +
                "-fx-padding: 8 12; -fx-background-radius: 12;");
        HBox.setHgrow(bubble, Priority.ALWAYS);

        Label nameLabel = new Label(author != null ? author : "Anonyme");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + "; -fx-font-size: 11;");

        Label textLabel = new Label(commentText);
        textLabel.setWrapText(true);
        textLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12;");

        bubble.getChildren().addAll(nameLabel, textLabel);

        // Bouton Répondre (visible pour tous sauf les réponses déjà imbriquées)
        if (!isReply) {
            Button replyBtn = new Button("↩ Répondre");
            replyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; " +
                "-fx-font-size: 10; -fx-cursor: hand; -fx-padding: 2 0; -fx-underline: false;");

            // Champ de réponse (caché par défaut)
            HBox replyInput = new HBox(6);
            replyInput.setAlignment(Pos.CENTER);
            replyInput.setManaged(false);
            replyInput.setVisible(false);
            replyInput.setPadding(new Insets(4, 0, 0, 0));

            TextField replyField = new TextField();
            replyField.setPromptText("Répondre à " + (author != null ? author : "Anonyme") + "...");
            replyField.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-border-radius: 12; -fx-border-color: #6b5dd3; -fx-padding: 5 10; -fx-font-size: 11;");
            HBox.setHgrow(replyField, Priority.ALWAYS);

            Button sendReply = new Button("→");
            sendReply.setDisable(true);
            sendReply.setStyle("-fx-background-color: #6b5dd3; -fx-text-fill: white; " +
                "-fx-background-radius: 12; -fx-padding: 5 10; -fx-font-size: 11; -fx-cursor: hand;");

            replyField.textProperty().addListener((obs, o, n) ->
                sendReply.setDisable(n == null || n.trim().isEmpty()));

            String currentUserName2 = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();
            sendReply.setOnAction(e -> {
                String replyText = replyField.getText().trim();
                if (replyText.isEmpty()) return;
                // Construire la ligne de réponse indentée
                HBox replyRow = buildCommentRow(currentUserName2,
                    "@" + (author != null ? author : "Anonyme") + " " + replyText,
                    true, commentsBox, commentBtn, true);
                // Insérer après ce commentaire
                int idx = commentsBox.getChildren().indexOf(row);
                commentsBox.getChildren().add(idx + 1 >= commentsBox.getChildren().size()
                    ? commentsBox.getChildren().size() : idx + 1, replyRow);
                replyField.clear();
                replyInput.setVisible(false);
                replyInput.setManaged(false);
                replyBtn.setText("↩ Répondre");
                long n2 = commentsBox.getChildren().stream().filter(c -> c instanceof HBox).count();
                commentBtn.setText("💬 " + n2);
            });

            replyInput.getChildren().addAll(replyField, sendReply);
            bubble.getChildren().addAll(replyBtn, replyInput);

            replyBtn.setOnAction(e -> {
                boolean showing = replyInput.isVisible();
                replyInput.setVisible(!showing);
                replyInput.setManaged(!showing);
                replyBtn.setText(showing ? "↩ Répondre" : "✕ Annuler");
                if (!showing) replyField.requestFocus();
            });
        }

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
        if (cat == null) return "linear-gradient(to right, #6870d8, #93a0f1)";
        switch (cat.toLowerCase()) {
            case "math": return "linear-gradient(to right, #5d68d8, #7f8df0)";
            case "physique": case "science": return "linear-gradient(to right, #6f61d4, #938cf0)";
            case "informatique": return "linear-gradient(to right, #7d5bd8, #a178ee)";
            case "langue": return "linear-gradient(to right, #5f7ed9, #8ea4f2)";
            default: return "linear-gradient(to right, #6d748f, #9ca5c8)";
        }
    }

    private String getAvatarColor(String cat) {
        if (cat == null) return "#6870d8";
        switch (cat.toLowerCase()) {
            case "math": return "#5d68d8";
            case "physique": case "science": return "#6f61d4";
            case "informatique": return "#7d5bd8";
            case "langue": return "#5f7ed9";
            default: return "#6d748f";
        }
    }

    private String withOpacity(String hexColor, double opacity) {
        Color color = Color.web(hexColor);
        return String.format("rgba(%d,%d,%d,%.2f)",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255),
                opacity);
    }

    private double computeArticleDialogHeight(String content) {
        String cleanContent = cleanMarkdown(content == null ? "" : content).trim();
        int estimatedLines = Math.max(1, (int) Math.ceil(cleanContent.length() / 65.0));
        return Math.max(430, Math.min(680, 332 + (estimatedLines * 18)));
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
        dialog.setMinHeight(420);

        VBox root = new VBox(0);
        root.getStyleClass().addAll("blog-dialog-root", "blog-view-root");

        // Bannière
        StackPane banner = new StackPane();
        banner.setPrefHeight(180);
        banner.getStyleClass().add("blog-view-banner");
        banner.setStyle("-fx-background-color: " + getBannerColor(blog.getImage()) + ";");

        Label icon = new Label(getCategoryIcon(blog.getImage()));
        icon.getStyleClass().add("blog-view-banner-icon");
        StackPane.setAlignment(icon, Pos.CENTER_RIGHT);
        StackPane.setMargin(icon, new Insets(0, 34, 0, 0));

        Label badge = new Label(getCategoryLabel(blog.getImage()));
        badge.getStyleClass().add("blog-view-badge");
        StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
        StackPane.setMargin(badge, new Insets(0, 0, 22, 24));

        VBox titleBox = new VBox(6);
        titleBox.setAlignment(Pos.BOTTOM_LEFT);
        StackPane.setMargin(titleBox, new Insets(0, 0, 52, 24));
        StackPane.setAlignment(titleBox, Pos.BOTTOM_LEFT);

        Label titleLabel = new Label(blog.getTitre());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(560);
        titleLabel.getStyleClass().add("blog-view-title");
        titleBox.getChildren().add(titleLabel);

        banner.getChildren().addAll(icon, badge, titleBox);

        // Corps de l'article
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("blog-view-scroll");
        scrollPane.setFocusTraversable(false);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox body = new VBox(18);
        body.setPadding(new Insets(26, 30, 28, 30));
        body.getStyleClass().add("blog-view-body");

        // Méta (auteur + date)
        HBox meta = new HBox(12);
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.getStyleClass().add("blog-view-meta");

        StackPane avatar = new StackPane();
        avatar.setPrefSize(44, 44);
        avatar.setMinSize(44, 44);
        avatar.getStyleClass().add("blog-view-avatar");
        avatar.setStyle("-fx-background-color: " + getAvatarColor(blog.getImage()) + ";");
        String initial = (blog.getPublishedBy() != null && !blog.getPublishedBy().isEmpty())
                ? String.valueOf(blog.getPublishedBy().charAt(0)).toUpperCase() : "?";
        Label avatarLabel = new Label(initial);
        avatarLabel.getStyleClass().add("blog-view-avatar-label");
        avatar.getChildren().add(avatarLabel);

        VBox authorBox = new VBox(2);
        Label authorName = new Label(blog.getPublishedBy() != null ? blog.getPublishedBy() : "Anonyme");
        authorName.getStyleClass().add("blog-view-author-name");
        String dateStr = blog.getPublishedAt() != null
                ? blog.getPublishedAt().format(DATE_FORMATTER)
                : (blog.getCreatedAt() != null ? blog.getCreatedAt().format(DATE_FORMATTER) : "");
        Label dateLabel = new Label("\uD83D\uDCC5  " + dateStr);
        dateLabel.getStyleClass().add("blog-view-date");
        authorBox.getChildren().addAll(authorName, dateLabel);

        meta.getChildren().addAll(avatar, authorBox);

        Separator sep = new Separator();
        sep.getStyleClass().add("blog-view-separator");

        // Contenu complet
        Label fullContent = new Label(blog.getContent());
        fullContent.setWrapText(true);
        fullContent.getStyleClass().add("blog-view-content");

        Region viewSpacer = new Region();
        VBox.setVgrow(viewSpacer, Priority.ALWAYS);

        // Bouton fermer
        Button closeBtn = new Button("Fermer");
        closeBtn.getStyleClass().add("blog-dialog-button-secondary");
        closeBtn.setOnAction(e -> dialog.close());
        HBox closebar = new HBox();
        closebar.setAlignment(Pos.CENTER_RIGHT);
        closebar.getStyleClass().add("blog-view-closebar");
        closebar.getChildren().add(closeBtn);

        body.getChildren().addAll(meta, sep, fullContent, viewSpacer, closebar);
        scrollPane.setContent(body);

        root.getChildren().addAll(banner, scrollPane);

        Scene scene = new Scene(root, 680, computeArticleDialogHeight(blog.getContent()));
        tn.esprit.fahamni.utils.FrontOfficeUiTheme.applyBlogDialog(scene);
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
        root.getStyleClass().addAll("blog-dialog-root", "blog-edit-dialog-root");

        // En-tête
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("blog-dialog-header");
        Label headerTitle = new Label("\u270F\uFE0F  Modifier l'Article");
        headerTitle.getStyleClass().add("blog-dialog-header-title");
        header.getChildren().add(headerTitle);

        VBox form = new VBox(16);
        form.getStyleClass().add("blog-dialog-form");

        // Titre
        VBox titreGroup = new VBox(6);
        Label titreLabel = new Label("Titre de l'article *");
        titreLabel.getStyleClass().add("blog-dialog-label");
        TextField titreField = new TextField(blog.getTitre());
        titreField.getStyleClass().add("blog-dialog-field");
        titreGroup.getChildren().addAll(titreLabel, titreField);

        // Catégorie
        VBox catGroup = new VBox(6);
        Label catLabel = new Label("Catégorie *");
        catLabel.getStyleClass().add("blog-dialog-label");
        ComboBox<String> catBox = new ComboBox<>();
        catBox.getItems().addAll("math", "physique", "science", "informatique", "langue", "autre");
        catBox.setValue(blog.getImage() != null ? blog.getImage() : "autre");
        catBox.setMaxWidth(Double.MAX_VALUE);
        catBox.getStyleClass().add("blog-dialog-field");
        catGroup.getChildren().addAll(catLabel, catBox);

        // Image
        final String[] selectedImagePath = {blog.getImage()};
        VBox imageGroup = new VBox(6);
        Label imageLabel = new Label("Image de l'article (optionnel)");
        imageLabel.getStyleClass().add("blog-dialog-label");
        HBox imageRow = new HBox(10);
        imageRow.getStyleClass().add("blog-dialog-file-row");
        String currentImg = blog.getImage() != null ? blog.getImage() : "Aucune image";
        Label imagePathLabel = new Label(currentImg);
        imagePathLabel.getStyleClass().add("blog-dialog-image-path");
        imagePathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(imagePathLabel, Priority.ALWAYS);
        Button browseBtn = new Button("\uD83D\uDCC1  Parcourir...");
        browseBtn.getStyleClass().add("blog-dialog-button-browse");
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
        contentGroup.getStyleClass().add("blog-dialog-group");
        Label contentLabel = new Label("Contenu de l'article *");
        contentLabel.getStyleClass().add("blog-dialog-label");
        TextArea contentArea = new TextArea(blog.getContent());
        contentArea.setPrefRowCount(7);
        contentArea.setWrapText(true);
        contentArea.getStyleClass().add("blog-dialog-field");
        contentGroup.getChildren().addAll(contentLabel, contentArea);

        Label errorLabel = new Label("");
        errorLabel.getStyleClass().add("blog-dialog-error-message");

        HBox buttons = new HBox(12);
        buttons.getStyleClass().add("blog-dialog-actions");

        Button cancelBtn = new Button("Annuler");
        cancelBtn.getStyleClass().add("blog-dialog-button-secondary");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("\u2705  Enregistrer les modifications");
        saveBtn.getStyleClass().add("blog-dialog-button-primary");
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
        tn.esprit.fahamni.utils.FrontOfficeUiTheme.applyBlogDialog(scene);
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
        root.getStyleClass().add("blog-dialog-root");

        // En-tête du formulaire
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("blog-dialog-header");
        Label headerTitle = new Label("✏️  Nouvel Article");
        headerTitle.getStyleClass().add("blog-dialog-header-title");
        header.getChildren().add(headerTitle);

        // Corps du formulaire
        VBox form = new VBox(16);
        form.getStyleClass().add("blog-dialog-form");

        // Titre
        VBox titreGroup = new VBox(6);
        titreGroup.getStyleClass().add("blog-dialog-group");
        titreGroup.getStyleClass().add("blog-dialog-group");
        titreGroup.getStyleClass().add("blog-dialog-group");
        Label titreLabel = new Label("Titre de l'article *");
        titreLabel.getStyleClass().add("blog-dialog-label");
        TextField titreField = new TextField();
        titreField.setPromptText("Ex: Les bases de l'algèbre...");
        titreField.getStyleClass().add("blog-dialog-field");
        titreGroup.getChildren().addAll(titreLabel, titreField);

        // Catégorie
        VBox catGroup = new VBox(6);
        catGroup.getStyleClass().add("blog-dialog-group");
        catGroup.getStyleClass().add("blog-dialog-group");
        catGroup.getStyleClass().add("blog-dialog-group");
        Label catLabel = new Label("Catégorie *");
        catLabel.getStyleClass().add("blog-dialog-label");
        ComboBox<String> catBox = new ComboBox<>();
        catBox.getItems().addAll("math", "physique", "science", "informatique", "langue", "autre");
        catBox.setPromptText("Choisir une catégorie");
        catBox.setMaxWidth(Double.MAX_VALUE);
        catBox.getStyleClass().add("blog-dialog-field");

        // Champ personnalisé visible uniquement si "autre" est sélectionné
        TextField customCatField = new TextField();
        customCatField.setPromptText("Nom de la matière (ex: Chimie, Philosophie...)");
        customCatField.setMaxWidth(Double.MAX_VALUE);
        customCatField.getStyleClass().add("blog-dialog-field");
        customCatField.setManaged(false);
        customCatField.setVisible(false);
        catBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isAutre = "autre".equals(newVal);
            customCatField.setManaged(isAutre);
            customCatField.setVisible(isAutre);
            if (!isAutre) customCatField.clear();
        });
        catGroup.getChildren().addAll(catLabel, catBox, customCatField);

        // Image - Parcourir depuis PC
        final String[] selectedImagePath = {null};
        VBox imageGroup = new VBox(6);
        imageGroup.getStyleClass().add("blog-dialog-group");
        imageGroup.getStyleClass().add("blog-dialog-group");
        imageGroup.getStyleClass().add("blog-dialog-group");
        Label imageLabel = new Label("Image de l'article (optionnel)");
        imageLabel.getStyleClass().add("blog-dialog-label");
        HBox imageRow = new HBox(10);
        imageRow.getStyleClass().add("blog-dialog-file-row");
        Label imagePathLabel = new Label("Aucune image sélectionnée");
        imagePathLabel.getStyleClass().add("blog-dialog-image-path");
        imagePathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(imagePathLabel, Priority.ALWAYS);
        Button browseBtn = new Button("📁  Parcourir...");
        browseBtn.getStyleClass().add("blog-dialog-button-browse");
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
                imagePathLabel.setStyle("-fx-text-fill: #243147; -fx-font-size: 12px; -fx-font-weight: bold;");
            }
        });
        imageRow.getChildren().addAll(imagePathLabel, browseBtn);
        imageGroup.getChildren().addAll(imageLabel, imageRow);

        // Contenu + compteur X/500
        final int MAX_CONTENT = 500;
        VBox contentGroup = new VBox(4);
        contentGroup.getStyleClass().add("blog-dialog-group");

        Label contentLbl = new Label("Contenu de l'article *  (min. 20 · max. 500 caractères)");
        contentLbl.getStyleClass().add("blog-dialog-label");
        Region contentHSpacer = new Region(); HBox.setHgrow(contentHSpacer, Priority.ALWAYS);

        // Declarations anticipees pour les lambdas
        TextArea contentArea = new TextArea();
        Label errorLabel = new Label("");

        // Bouton IA collé au label du champ contenu
        Button aiBtn = new Button("\uD83E\uDD16  G\u00e9n\u00e9rer avec l'IA");
        aiBtn.setStyle(
            "-fx-background-color: #f5f3ff; -fx-text-fill: #7c3aed; "
            + "-fx-font-size: 11; -fx-background-radius: 20; "
            + "-fx-padding: 4 12; -fx-cursor: hand; "
            + "-fx-border-color: #ddd6fe; -fx-border-width: 1; -fx-border-radius: 20;");
        aiBtn.setOnAction(e -> {
            String titreAi = titreField.getText().trim();
            String catAi = catBox.getValue();
            if (titreAi.length() < 5) {
                errorLabel.setText("\u26a0\ufe0f  Entrez d'abord un titre (min. 5 caract\u00e8res).");
                return;
            }
            aiBtn.setText("\u23F3  G\u00e9n\u00e9ration...");
            aiBtn.setDisable(true);
            new Thread(() -> {
                String generated = contentGenerator.generate(titreAi, catAi != null ? catAi : "autre");
                javafx.application.Platform.runLater(() -> {
                    if (generated != null && !generated.isBlank()) {
                        contentArea.setText(generated);
                    } else {
                        errorLabel.setText("\u26a0\ufe0f  G\u00e9n\u00e9ration impossible.");
                    }
                    aiBtn.setText("\uD83E\uDD16  G\u00e9n\u00e9rer avec l'IA");
                    aiBtn.setDisable(false);
                });
            }).start();
        });

        HBox contentHeaderRow = new HBox(8, contentLbl, contentHSpacer, aiBtn);
        contentHeaderRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        contentArea.setPromptText("Rédigez votre article ici...");
        contentArea.setPrefRowCount(7);
        contentArea.setWrapText(true);
        contentArea.getStyleClass().add("blog-dialog-field");
        Label contentCounter = new Label("0/" + MAX_CONTENT);
        contentCounter.getStyleClass().add("blog-dialog-counter");
        contentArea.textProperty().addListener((obs, o, n) -> {
            if (n != null && n.length() > MAX_CONTENT) {
                contentArea.setText(o);
                return;
            }
            int len = n == null ? 0 : n.trim().length();
            contentCounter.setText(len + "/" + MAX_CONTENT);
            String color = len < 20 ? "#d1435b" : (len > 450 ? "#d48a2f" : "#5f49bf");
            contentCounter.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        });
        contentGroup.getChildren().addAll(contentHeaderRow, contentArea, contentCounter);

        // Compteur titre
        Label titreCounter = new Label("0/100");
        titreCounter.getStyleClass().add("blog-dialog-counter");
        titreField.textProperty().addListener((obs, o, n) -> {
            int len = n == null ? 0 : n.length();
            // Bloquer à 100 caractères
            if (len > 100) { titreField.setText(o); return; }
            titreCounter.setText(len + "/100");
            titreCounter.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + (len < 5 ? "#d1435b" : "#5f49bf") + ";");
        });
        titreGroup.getChildren().add(titreCounter);

        // Message d'erreur
        errorLabel.getStyleClass().add("blog-dialog-error-message");
        errorLabel.setWrapText(true);

        // Anti-spam : bloquer si 2 soumissions en moins de 30 secondes
        final long[] submitTimes = {0, 0}; // [0]=avant-dernière, [1]=dernière

        // Panel de resultats orthographiques (masque par defaut)
        VBox spellResultBox = new VBox(6);
        spellResultBox.setPadding(new Insets(8, 10, 8, 10));
        spellResultBox.setStyle("-fx-background-color: #fffbeb; -fx-border-color: #fbbf24; "
            + "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");
        spellResultBox.setManaged(false);
        spellResultBox.setVisible(false);

        // Bouton Correcteur orthographique
        Button spellBtn = new Button("\u2713  V\u00e9rifier l'orthographe");
        spellBtn.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1d4ed8; "
            + "-fx-font-size: 12; -fx-background-radius: 8; -fx-padding: 6 14; -fx-cursor: hand;");
        spellBtn.setOnAction(e -> {
            spellResultBox.getChildren().clear();
            spellResultBox.setManaged(false);
            spellResultBox.setVisible(false);
            spellBtn.setDisable(true);
            spellBtn.setText("\u23F3  V\u00e9rification...");
            new Thread(() -> {
                SpellCheckService.CombinedResult res = spellCheckService.checkCombined(
                    titreField.getText(), contentArea.getText());
                javafx.application.Platform.runLater(() -> {
                    spellBtn.setText("\u2713  V\u00e9rifier l'orthographe");
                    spellBtn.setDisable(false);
                    if (res.titreErrors.isEmpty() && res.contentErrors.isEmpty()) {
                        Label ok = new Label("\u2705  Aucune faute d\u00e9tect\u00e9e !");
                        ok.setStyle("-fx-font-size: 12; -fx-text-fill: #16a34a; -fx-font-weight: bold;");
                        spellResultBox.getChildren().add(ok);
                    } else {
                        Label hdr = new Label("Fautes d\u00e9tect\u00e9es \u2014 cliquez Corriger pour appliquer :");
                        hdr.setStyle("-fx-font-size: 11; -fx-text-fill: #92400e; -fx-font-weight: bold;");
                        spellResultBox.getChildren().add(hdr);
                        for (SpellCheckService.SpellError se : res.titreErrors)
                            spellResultBox.getChildren().add(buildSpellRow(se, titreField, null));
                        for (SpellCheckService.SpellError se : res.contentErrors)
                            spellResultBox.getChildren().add(buildSpellRow(se, null, contentArea));
                    }
                    spellResultBox.setManaged(true);
                    spellResultBox.setVisible(true);
                });
            }).start();
        });


        // Auto-correction : declenche apres 800ms sans frappe
        PauseTransition spellDelay = new PauseTransition(javafx.util.Duration.millis(900));
        spellDelay.setOnFinished(ev -> {
            String t = titreField.getText().trim();
            String c = contentArea.getText().trim();
            if (t.length() < 3 && c.length() < 3) return;
            spellResultBox.getChildren().clear();
            new Thread(() -> {
                SpellCheckService.CombinedResult res = spellCheckService.checkCombined(t, c);
                javafx.application.Platform.runLater(() -> {
                    spellResultBox.getChildren().clear();
                    if (!res.titreErrors.isEmpty() || !res.contentErrors.isEmpty()) {
                        Label hdr = new Label("Fautes d\u00e9tect\u00e9es \u2014 cliquez Corriger pour appliquer :");
                        hdr.setStyle("-fx-font-size: 11; -fx-text-fill: #92400e; -fx-font-weight: bold;");
                        spellResultBox.getChildren().add(hdr);
                        for (SpellCheckService.SpellError se : res.titreErrors)
                            spellResultBox.getChildren().add(buildSpellRow(se, titreField, null));
                        for (SpellCheckService.SpellError se : res.contentErrors)
                            spellResultBox.getChildren().add(buildSpellRow(se, null, contentArea));
                        spellResultBox.setManaged(true);
                        spellResultBox.setVisible(true);
                    } else {
                        spellResultBox.setManaged(false);
                        spellResultBox.setVisible(false);
                    }
                });
            }).start();
        });
        titreField.textProperty().addListener((obs, o, n) -> spellDelay.playFromStart());
        contentArea.textProperty().addListener((obs, o, n) -> spellDelay.playFromStart());

        HBox aiRow = new HBox(10, spellBtn);
        aiRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        contentGroup.getChildren().addAll(aiRow, spellResultBox);

        HBox buttons = new HBox(12);
        buttons.getStyleClass().add("blog-dialog-actions");

        Button cancelBtn = new Button("Annuler");
        cancelBtn.getStyleClass().add("blog-dialog-button-secondary");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("✅  Publier l'article");
        saveBtn.getStyleClass().add("blog-dialog-button-primary");
        saveBtn.setOnAction(e -> {
            String titre   = titreField.getText().trim();
            String content = contentArea.getText().trim();
            String catSelected = catBox.getValue();
            String customCat   = customCatField.getText().trim();
            // Si "autre" et champ personnalisé rempli → utiliser le nom custom
            String cat = "autre".equals(catSelected) && !customCat.isBlank() ? customCat : catSelected;
            setDialogFieldError(titreField, false);
            setDialogFieldError(contentArea, false);
            setDialogFieldError(catBox, false);
            errorLabel.setText("");

            // ── Contrôle 1 : Titre obligatoire (5–100 caractères) ──
            if (titre.length() < 5) {
                errorLabel.setText("⚠️  Le titre doit contenir au moins 5 caractères.");
                setDialogFieldError(titreField, true);
                return;
            }

            // ── Contrôle 2 : Contenu obligatoire (min. 20 caractères) ──
            if (content.length() < 20) {
                errorLabel.setText("⚠️  Le contenu doit contenir au moins 20 caractères.");
                setDialogFieldError(contentArea, true);
                return;
            }

            // ── Contrôle 3 : Catégorie obligatoire ──
            if (cat == null || cat.isBlank()) {
                errorLabel.setText("⚠️  Veuillez sélectionner une catégorie.");
                setDialogFieldError(catBox, true);
                return;
            }

            // ── Contrôle 3b : Nom de matière requis si "autre" sélectionné ──
            if ("autre".equals(catSelected) && customCat.isBlank()) {
                errorLabel.setText("⚠️  Veuillez saisir le nom de la matière.");
                customCatField.setStyle("-fx-border-color: #d1435b;");
                return;
            }

            // ── Contrôle 4 : Anti-spam (max 2 soumissions par 30 secondes) ──
            long now = System.currentTimeMillis();
            if (submitTimes[0] > 0 && (now - submitTimes[0]) < 30_000) {
                long restant = (30_000 - (now - submitTimes[0])) / 1000;
                errorLabel.setText("⏳  Trop de soumissions. Patientez encore " + restant + " seconde(s).");
                return;
            }

            // Validation passée, publication autorisée

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
        tn.esprit.fahamni.utils.FrontOfficeUiTheme.applyBlogDialog(scene);
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
        showRecommendations = true;
        setActiveCategoryButton(btnTous);
        loadBlogs(applySorting(blogService.getAllBlogs()));
    }

    @FXML private void filterMath() {
        showRecommendations = false;
        setActiveCategoryButton(btnMath);
        loadBlogs(applySorting(blogService.filterByCategory("math")));
    }

    @FXML private void filterScience() {
        showRecommendations = false;
        setActiveCategoryButton(btnScience);
        loadBlogs(applySorting(blogService.filterByCategory("physique", "science")));
    }

    @FXML private void filterInfo() {
        showRecommendations = false;
        setActiveCategoryButton(btnInfo);
        loadBlogs(applySorting(blogService.filterByCategory("informatique")));
    }

    @FXML private void filterLangue() {
        showRecommendations = false;
        setActiveCategoryButton(btnLangue);
        loadBlogs(applySorting(blogService.filterByCategory("langue")));
    }

    @FXML private void filterAutre() {
        showRecommendations = false;
        setActiveCategoryButton(btnAutre);
        loadBlogs(applySorting(blogService.filterByCategory("autre", "study-tips")));
    }

    @FXML private void filterFavoris() {
        showRecommendations = false;
        setActiveCategoryButton(btnFavoris);
        List<Blog> favs = blogService.getFavoriteBlogs();
        if (favs.isEmpty()) {
            blogsContainer.getChildren().clear();
            Label empty = new Label("♥  Vous n'avez pas encore de favoris. Cliquez sur ★ sur un article pour l'ajouter.");
            empty.setWrapText(true);
            empty.setStyle("-fx-font-size: 13; -fx-text-fill: #94a3b8; -fx-padding: 40 20;");
            blogsContainer.getChildren().add(empty);
        } else {
            loadBlogs(applySorting(favs));
        }
    }

    private void setActiveCategoryButton(Button activeButton) {
        List<Button> filterButtons = List.of(btnTous, btnMath, btnScience, btnInfo, btnLangue, btnAutre, btnFavoris);
        for (Button button : filterButtons) {
            if (button == null) continue;
            button.getStyleClass().remove("active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
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
        return 0;
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
            "-fx-border-color: #e3e6ef; -fx-border-radius: 14; -fx-border-width: 1;");
        container.setPrefWidth(340);

        // En-tête
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 16));
        header.setStyle("-fx-background-color: linear-gradient(to right,#6b5dd3,#5068d1);" +
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
    private Button buildShareBtn(tn.esprit.fahamni.Models.Blog blog) {
        if (blog.isShared()) return new Button();
        int sharesCount = blogService.countShares(blog.getId());
        Button btn = new Button("\uD83D\uDD01 " + sharesCount);
        styleCompactBtn(btn, "#f0f9ff", "#0369a1", "#bae6fd");
        btn.setTooltip(new Tooltip("Republier cet article dans votre fil"));
        btn.setOnAction(e -> {
            boolean ok = blogService.shareArticle(blog.getId());
            if (ok) loadBlogs(blogService.getAllBlogs());
        });
        return btn;
    }

    private Button buildTtsBtn(tn.esprit.fahamni.Models.Blog blog) {
        Button btn = new Button("\uD83D\uDD0A");
        styleCompactBtn(btn, "#f0fdf4", "#16a34a", "#bbf7d0");
        btn.setTooltip(new Tooltip("\u00c9couter l'article"));
        btn.setOnAction(e -> {
            if (ttsService.isSpeaking()) {
                ttsService.stop();
                btn.setText("\uD83D\uDD0A");
            } else {
                btn.setText("\u23F9");
                String texte = blog.getTitre() + ". " + blog.getContent();
                new Thread(() -> {
                    ttsService.speak(texte);
                    javafx.application.Platform.runLater(() -> btn.setText("\uD83D\uDD0A"));
                }).start();
            }
        });
        return btn;
    }

    private Button buildTranslateBtn(tn.esprit.fahamni.Models.Blog blog) {
        Button btn = new Button("\uD83C\uDF10");
        styleCompactBtn(btn, "#fefce8", "#a16207", "#fde68a");
        btn.setTooltip(new Tooltip("Traduire l'article"));
        btn.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();
            MenuItem en = new MenuItem("English");
            MenuItem ar = new MenuItem("\uD83C\uDDF8\uD83C\uDDE6  Arabe");
            en.setOnAction(ev -> showTranslation(blog, "en"));
            ar.setOnAction(ev -> showTranslation(blog, "ar"));
            menu.getItems().addAll(en, ar);
            menu.show(btn, javafx.geometry.Side.BOTTOM, 0, 0);
        });
        return btn;
    }

    private void showTranslation(tn.esprit.fahamni.Models.Blog blog, String lang) {
        String langLabel = lang.equals("en") ? "\uD83C\uDDEC\uD83C\uDDE7  English" : "\uD83C\uDDF8\uD83C\uDDE6  \u0639\u0631\u0628\u064a";
        Stage popup = new Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setTitle("Traduction \u2014 " + langLabel);
        popup.setMinWidth(500);
        popup.setMinHeight(400);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #f8faff;");

        // En-t\u00eate
        HBox header = new HBox(10);
        header.setPadding(new Insets(14, 18, 14, 18));
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #3a7bd5; -fx-background-radius: 0;");
        Label flagLbl = new Label(langLabel);
        flagLbl.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: white;");
        Label origLbl = new Label("\u2014  " + blog.getTitre());
        origLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #c7d9f5; -fx-font-style: italic;");
        origLbl.setWrapText(true);
        Region hsp = new Region(); HBox.setHgrow(hsp, Priority.ALWAYS);
        header.getChildren().addAll(flagLbl, hsp, origLbl);

        // Zone de chargement
        VBox body = new VBox(14);
        body.setPadding(new Insets(18));

        ProgressIndicator spinner = new ProgressIndicator(-1);
        spinner.setPrefSize(40, 40);
        Label loadingLbl = new Label("Traduction en cours...");
        loadingLbl.setStyle("-fx-font-size: 13; -fx-text-fill: #64748b;");
        VBox loadBox = new VBox(10, spinner, loadingLbl);
        loadBox.setAlignment(javafx.geometry.Pos.CENTER);
        loadBox.setPrefHeight(220);
        body.getChildren().add(loadBox);

        root.getChildren().addAll(header, body);
        popup.setScene(new Scene(root));
        popup.show();

        new Thread(() -> {
            String input = blog.getTitre() + " " + blog.getContent();
            String translated = translationService.translate(input, lang);
            javafx.application.Platform.runLater(() -> {
                body.getChildren().clear();
                if (translated == null || translated.isBlank()) {
                    Label err = new Label("\u26a0\ufe0f  Traduction indisponible. V\u00e9rifiez votre connexion.");
                    err.setStyle("-fx-font-size: 13; -fx-text-fill: #dc2626; -fx-wrap-text: true;");
                    body.getChildren().add(err);
                } else {
                    // S\u00e9parer le titre traduit du contenu traduit
                    String[] parts = translated.split("\n", 2);
                    String translatedTitle   = parts[0].trim();
                    String translatedContent = parts.length > 1 ? parts[1].trim() : translated;

                    Label tLbl = new Label(translatedTitle);
                    tLbl.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-wrap-text: true;");
                    tLbl.setMaxWidth(460);

                    javafx.scene.control.Separator sep = new javafx.scene.control.Separator();

                    ScrollPane scroll = new ScrollPane();
                    Label content = new Label(translatedContent);
                    content.setStyle("-fx-font-size: 13; -fx-text-fill: #374151; -fx-wrap-text: true; -fx-line-spacing: 3;");
                    content.setMaxWidth(440);
                    content.setWrapText(true);
                    scroll.setContent(content);
                    scroll.setFitToWidth(true);
                    scroll.setPrefHeight(260);
                    scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

                    body.getChildren().addAll(tLbl, sep, scroll);
                }
                Button close = new Button("Fermer");
                close.setStyle("-fx-background-color: #3a7bd5; -fx-text-fill: white; "
                    + "-fx-font-weight: bold; -fx-font-size: 12; -fx-background-radius: 8; "
                    + "-fx-padding: 7 22; -fx-cursor: hand;");
                close.setOnAction(ev -> popup.close());
                HBox foot = new HBox(close);
                foot.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                foot.setPadding(new Insets(0, 4, 4, 4));
                body.getChildren().add(foot);
            });
        }).start();
    }


    private HBox buildSpellRow(SpellCheckService.SpellError se,
                               TextField titreField, TextArea contentArea) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));

        // Mot incorrect
        Label bad = new Label(se.badWord);
        bad.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; "
            + "-fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 2 6;");

        Label arrow = new Label("\u2192");
        arrow.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13;");

        // Suggestion
        String sug = (se.suggestion != null && !se.suggestion.isBlank()) ? se.suggestion : "(aucune suggestion)";
        Label good = new Label(sug);
        good.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; "
            + "-fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 2 6;");

        // Zone (Titre / Contenu)
        String zone = titreField != null ? "[Titre]" : "[Contenu]";
        Label zoneLbl = new Label(zone);
        zoneLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #9ca3af;");

        // Bouton Corriger
        Button fixBtn = new Button("Corriger");
        fixBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; "
            + "-fx-font-size: 11; -fx-background-radius: 6; -fx-padding: 3 10; -fx-cursor: hand;");

        if (se.suggestion != null && !se.suggestion.isBlank()) {
            fixBtn.setOnAction(ev -> {
                if (titreField != null) {
                    String t = titreField.getText();
                    if (se.offset + se.length <= t.length())
                        titreField.setText(t.substring(0, se.offset) + se.suggestion + t.substring(se.offset + se.length));
                } else if (contentArea != null) {
                    String c = contentArea.getText();
                    if (se.offset + se.length <= c.length())
                        contentArea.setText(c.substring(0, se.offset) + se.suggestion + c.substring(se.offset + se.length));
                }
                ((VBox) row.getParent()).getChildren().remove(row);
            });
        } else {
            fixBtn.setDisable(true);
        }

        row.getChildren().addAll(zoneLbl, bad, arrow, good, fixBtn);
        return row;
    }

}