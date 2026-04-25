package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.ReservationService;
import tn.esprit.fahamni.services.ReservationService.SessionEvaluationItem;
import tn.esprit.fahamni.services.ReservationService.ReservationStats;
import tn.esprit.fahamni.services.SeanceService;
import tn.esprit.fahamni.utils.UserSession;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SeanceController {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter UPCOMING_FORMATTER = DateTimeFormatter.ofPattern("dd MMM - HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DETAILS_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SESSION_EVALUATIONS_STYLESHEET = "/com/fahamni/styles/frontoffice-session-evaluations.css";
    private static final String CALENDAR_REVIEWS_STYLESHEET = "/com/fahamni/styles/frontoffice-calendar-reviews.css";

    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();
    private final ReservationService reservationService = new ReservationService();

    private YearMonth displayedMonth;
    private List<Seance> seances = List.of();
    private Seance selectedSeance;

    @FXML
    private Label monthLabel;

    @FXML
    private GridPane calendarGrid;

    @FXML
    private VBox upcomingSessionsBox;

    @FXML
    private VBox selectedSessionDetailsBox;

    @FXML
    private void initialize() {
        displayedMonth = YearMonth.from(LocalDate.now());
        configureGrid();
        reloadCalendarData();
        renderSelectedSessionDetails();
    }

    @FXML
    private void handlePreviousMonth() {
        displayedMonth = displayedMonth.minusMonths(1);
        renderCalendar();
    }

    @FXML
    private void handleNextMonth() {
        displayedMonth = displayedMonth.plusMonths(1);
        renderCalendar();
    }

    private void reloadCalendarData() {
        seances = seanceService.getAll();
        renderUpcomingSessions();
        renderCalendar();
    }

    private void renderUpcomingSessions() {
        upcomingSessionsBox.getChildren().clear();

        List<Seance> upcomingSessions = seances.stream()
            .filter(this::isUpcoming)
            .sorted(Comparator.comparing(Seance::getStartAt))
            .limit(3)
            .toList();

        if (upcomingSessions.isEmpty()) {
            VBox emptyCard = new VBox(8.0);
            emptyCard.getStyleClass().add("calendar-upcoming-card");

            Label chip = new Label("A VENIR");
            chip.getStyleClass().add("calendar-upcoming-chip");

            Label emptyText = new Label("Aucune seance planifiee.");
            emptyText.setWrapText(true);
            emptyText.getStyleClass().add("calendar-upcoming-empty");

            emptyCard.getChildren().addAll(chip, emptyText);
            upcomingSessionsBox.getChildren().add(emptyCard);
            return;
        }

        for (Seance seance : upcomingSessions) {
            upcomingSessionsBox.getChildren().add(buildUpcomingCard(seance));
        }
    }

    private VBox buildUpcomingCard(Seance seance) {
        VBox card = new VBox(6.0);
        card.getStyleClass().add("calendar-upcoming-card");
        configureCalendarSessionInteraction(card, seance);
        if (isSelected(seance)) {
            card.getStyleClass().add("selected");
        }

        Label subjectLabel = new Label(seance.getMatiere());
        subjectLabel.getStyleClass().add("calendar-upcoming-title");
        subjectLabel.setWrapText(true);

        Label tutorLabel = new Label("Tutor: " + tutorDirectoryService.getTutorDisplayName(seance.getTuteurId()));
        tutorLabel.getStyleClass().add("calendar-upcoming-meta");
        tutorLabel.setWrapText(true);

        Label timeLabel = new Label(formatUpcomingDate(seance.getStartAt()));
        timeLabel.getStyleClass().add("calendar-upcoming-meta");

        card.getChildren().addAll(subjectLabel, tutorLabel, timeLabel);
        return card;
    }

    private void renderCalendar() {
        monthLabel.setText(displayedMonth.format(MONTH_FORMATTER));
        calendarGrid.getChildren().clear();

        Map<LocalDate, List<Seance>> sessionsByDay = seances.stream()
            .filter(seance -> seance.getStartAt() != null)
            .collect(Collectors.groupingBy(seance -> seance.getStartAt().toLocalDate()));

        LocalDate firstDayOfMonth = displayedMonth.atDay(1);
        int shift = firstDayOfMonth.getDayOfWeek().getValue() % 7;
        LocalDate gridStartDate = firstDayOfMonth.minusDays(shift);

        for (int cellIndex = 0; cellIndex < 42; cellIndex++) {
            LocalDate cellDate = gridStartDate.plusDays(cellIndex);
            VBox dayCard = buildDayCard(cellDate, sessionsByDay.getOrDefault(cellDate, List.of()));
            calendarGrid.add(dayCard, cellIndex % 7, cellIndex / 7);
        }
    }

    private VBox buildDayCard(LocalDate cellDate, List<Seance> sessionsForDay) {
        VBox card = new VBox(6.0);
        card.getStyleClass().add("calendar-day-card");

        if (!YearMonth.from(cellDate).equals(displayedMonth)) {
            card.getStyleClass().add("outside-month");
        }
        if (cellDate.equals(LocalDate.now())) {
            card.getStyleClass().add("today");
        }

        Label dayNumber = new Label(String.valueOf(cellDate.getDayOfMonth()));
        dayNumber.getStyleClass().add("calendar-day-number");
        if (!YearMonth.from(cellDate).equals(displayedMonth)) {
            dayNumber.getStyleClass().add("muted");
        }
        if (cellDate.equals(LocalDate.now())) {
            dayNumber.getStyleClass().add("active");
        }

        VBox sessionsBox = new VBox(4.0);
        sessionsBox.getStyleClass().add("calendar-day-sessions");

        List<Seance> orderedSessions = sessionsForDay.stream()
            .sorted(Comparator.comparing(Seance::getStartAt))
            .toList();

        for (int i = 0; i < Math.min(2, orderedSessions.size()); i++) {
            Seance session = orderedSessions.get(i);
            Label pill = new Label(buildDaySessionText(session));
            pill.getStyleClass().add("calendar-session-pill");
            if (isSelected(session)) {
                pill.getStyleClass().add("selected");
            }
            pill.setWrapText(true);
            configureCalendarSessionInteraction(pill, session);
            sessionsBox.getChildren().add(pill);
        }

        if (orderedSessions.size() > 2) {
            Label moreLabel = new Label("+" + (orderedSessions.size() - 2) + " autres");
            moreLabel.getStyleClass().add("calendar-session-more");
            sessionsBox.getChildren().add(moreLabel);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(dayNumber, sessionsBox, spacer);
        return card;
    }

    private void selectSeance(Seance seance) {
        selectedSeance = seance;
        renderSelectedSessionDetails();
        renderUpcomingSessions();
        renderCalendar();
    }

    private void clearSelectedSeance() {
        selectedSeance = null;
        renderSelectedSessionDetails();
        renderUpcomingSessions();
        renderCalendar();
    }

    private void renderSelectedSessionDetails() {
        selectedSessionDetailsBox.getChildren().clear();

        if (selectedSeance == null) {
            VBox emptyCard = new VBox(8.0);
            emptyCard.getStyleClass().add("calendar-selected-empty-card");

            Label hintTitle = new Label("Clique sur une seance");
            hintTitle.getStyleClass().add("calendar-selected-empty-title");
            Label hintText = new Label("Les details de la seance choisie s'afficheront ici.");
            hintText.setWrapText(true);
            hintText.getStyleClass().add("calendar-upcoming-meta");

            emptyCard.getChildren().addAll(hintTitle, hintText);
            selectedSessionDetailsBox.getChildren().add(emptyCard);
            return;
        }

        ReservationStats stats = reservationService.getStatsBySeanceId(selectedSeance.getId());
        LocalDateTime endAt = selectedSeance.getStartAt() != null
            ? selectedSeance.getStartAt().plusMinutes(selectedSeance.getDurationMin())
            : null;

        VBox detailCard = new VBox(10.0);
        detailCard.getStyleClass().add("calendar-selected-card");

        HBox header = new HBox(8.0);
        header.getStyleClass().add("calendar-selected-header");
        Label title = new Label(safeText(selectedSeance.getMatiere()));
        title.setWrapText(true);
        title.getStyleClass().add("calendar-selected-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Label status = new Label(mapStatusLabel(selectedSeance.getStatus()));
        status.getStyleClass().addAll("reservation-status", mapStatusStyle(selectedSeance.getStatus()));
        header.getChildren().addAll(title, headerSpacer, status);

        VBox rows = new VBox(7.0);
        rows.getChildren().addAll(
            buildSelectedInfoRow("Tuteur", tutorDirectoryService.getTutorDisplayName(selectedSeance.getTuteurId())),
            buildSelectedInfoRow("Horaire", formatDateTime(selectedSeance.getStartAt()) + " -> " + formatTime(endAt)),
            buildSelectedInfoRow("Duree", selectedSeance.getDurationMin() + " min"),
            buildSelectedInfoRow("Capacite", selectedSeance.getMaxParticipants() + " participants"),
            buildSelectedInfoRow("Mode", mapModeLabel(selectedSeance.getMode())),
            buildSelectedInfoRow("Salle", formatSalleReference(selectedSeance)),
            buildSelectedInfoRow("Materiel", formatEquipementReferences(selectedSeance)),
            buildSelectedInfoRow("Reservations", formatReservationCount(stats.total()))
        );

        Label description = new Label(safeText(selectedSeance.getDescription()));
        description.setWrapText(true);
        description.getStyleClass().add("calendar-selected-description");

        Button clearButton = new Button("Effacer la selection");
        clearButton.getStyleClass().addAll("action-button", "secondary");
        clearButton.setOnAction(event -> clearSelectedSeance());

        configureCalendarSessionInteraction(detailCard, selectedSeance);
        detailCard.getChildren().addAll(header, rows, description, clearButton);
        selectedSessionDetailsBox.getChildren().add(detailCard);
    }

    private void configureCalendarSessionInteraction(Node node, Seance seance) {
        if (node == null || seance == null) {
            return;
        }
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event == null || event.getClickCount() <= 0) {
                return;
            }

            selectSeance(seance);
            if (event.getClickCount() >= 2 && !isInteractiveTarget(event.getTarget())) {
                openSessionReviewsDialog(seance);
            }
        });
    }

    private boolean isInteractiveTarget(Object target) {
        Node current = target instanceof Node node ? node : null;
        while (current != null) {
            if (current instanceof Button) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void openSessionReviewsDialog(Seance seance) {
        if (seance == null) {
            return;
        }

        List<SessionEvaluationItem> evaluations = reservationService.getSessionEvaluations(seance.getId());
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogPane dialogPane = dialog.getDialogPane();
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
        VBox content = buildSessionReviewsDialogContent(seance, evaluations);

        dialog.setTitle("Avis de la seance");
        dialogPane.getButtonTypes().setAll(closeButton);
        dialogPane.setPrefWidth(640);
        dialogPane.getStyleClass().add("calendar-review-dialog");
        dialogPane.setContent(content);
        applyDialogTheme(dialogPane);
        appendDialogStylesheet(dialogPane, SESSION_EVALUATIONS_STYLESHEET);
        appendDialogStylesheet(dialogPane, CALENDAR_REVIEWS_STYLESHEET);
        dialog.setOnShown(event -> animateReviewDialogEntrance(content));
        dialog.showAndWait();
    }

    private VBox buildSessionReviewsDialogContent(Seance seance, List<SessionEvaluationItem> evaluations) {
        VBox root = new VBox(14.0);
        root.getStyleClass().add("calendar-review-root");
        root.setPadding(new Insets(18.0));

        VBox shell = new VBox(14.0);
        shell.getStyleClass().add("calendar-review-shell");

        HBox header = new HBox(10.0);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBlock = new VBox(4.0);
        Label titleLabel = new Label("Avis de la seance");
        titleLabel.getStyleClass().add("calendar-review-title");
        Label subtitleLabel = new Label(
            safeText(seance.getMatiere())
                + " | "
                + formatDateTime(seance.getStartAt())
                + " | Double-clique depuis le calendrier"
        );
        subtitleLabel.setWrapText(true);
        subtitleLabel.getStyleClass().add("calendar-review-subtitle");
        titleBlock.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label summaryChip = new Label(buildReviewsSummary(evaluations));
        summaryChip.getStyleClass().add("calendar-review-summary-chip");
        header.getChildren().addAll(titleBlock, spacer, summaryChip);

        VBox cardsBox = new VBox(10.0);
        List<Node> animatedNodes = new java.util.ArrayList<>();
        if (evaluations == null || evaluations.isEmpty()) {
            Label emptyLabel = new Label("Aucun avis n'a encore ete laisse pour cette seance.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("calendar-review-empty");
            cardsBox.getChildren().add(emptyLabel);
            animatedNodes.add(emptyLabel);
        } else {
            evaluations.stream()
                .limit(5)
                .map(evaluation -> buildReviewCard(seance, evaluation))
                .forEach(card -> {
                    cardsBox.getChildren().add(card);
                    animatedNodes.add(card);
                });

            if (evaluations.size() > 5) {
                Label moreLabel = new Label(
                    (evaluations.size() - 5) + " autre(s) avis sont aussi disponibles pour cette seance."
                );
                moreLabel.setWrapText(true);
                moreLabel.getStyleClass().add("calendar-review-empty");
                cardsBox.getChildren().add(moreLabel);
                animatedNodes.add(moreLabel);
            }
        }

        ScrollPane scrollPane = new ScrollPane(cardsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(420.0);
        scrollPane.getStyleClass().add("calendar-review-scroll");

        shell.getChildren().addAll(header, scrollPane);
        root.getChildren().add(shell);
        root.getProperties().put("calendar-review-animated-nodes", animatedNodes);
        return root;
    }

    private VBox buildReviewCard(Seance seance, SessionEvaluationItem evaluation) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("session-detail-evaluation-card");

        HBox header = new HBox(10.0);
        header.getStyleClass().add("session-detail-evaluation-header");

        VBox identityBlock = new VBox(3.0);
        Label authorLabel = new Label(resolveEvaluationAuthor(seance, evaluation));
        authorLabel.getStyleClass().add("session-detail-evaluation-author");
        Label dateLabel = new Label(
            evaluation != null && evaluation.ratedAt() != null
                ? "Notee le " + formatDateTime(evaluation.ratedAt())
                : "Avis partage recemment"
        );
        dateLabel.getStyleClass().add("session-detail-evaluation-date");
        identityBlock.getChildren().addAll(authorLabel, dateLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label starsLabel = new Label(buildRatingStars(evaluation != null ? evaluation.rating() : 0));
        starsLabel.getStyleClass().add("session-detail-evaluation-stars");
        Label scoreLabel = new Label((evaluation != null ? evaluation.rating() : 0) + "/5");
        scoreLabel.getStyleClass().add("session-detail-evaluation-score");

        header.getChildren().addAll(identityBlock, spacer, starsLabel, scoreLabel);

        Label reviewLabel = new Label(
            evaluation != null && evaluation.hasReview()
                ? evaluation.review()
                : "Pas de commentaire detaille, mais une note a bien ete envoyee."
        );
        reviewLabel.setWrapText(true);
        reviewLabel.getStyleClass().add("session-detail-evaluation-review");

        card.getChildren().addAll(header, reviewLabel);
        return card;
    }

    private String resolveEvaluationAuthor(Seance seance, SessionEvaluationItem evaluation) {
        if (evaluation == null) {
            return "Avis etudiant";
        }
        if (canCurrentTutorSeeReviewAuthor(seance)) {
            String normalizedName = normalizeText(evaluation.participantName());
            return normalizedName != null ? normalizedName : "Etudiant Fahamni";
        }
        return "Avis etudiant";
    }

    private boolean canCurrentTutorSeeReviewAuthor(Seance seance) {
        return UserSession.isCurrentTutor()
            && seance != null
            && UserSession.getCurrentUserId() == seance.getTuteurId();
    }

    private String buildReviewsSummary(List<SessionEvaluationItem> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return "0 avis";
        }
        double average = evaluations.stream()
            .mapToInt(SessionEvaluationItem::rating)
            .average()
            .orElse(0.0);
        return String.format(Locale.US, "%.1f", average).replace('.', ',') + "/5  |  " + evaluations.size() + " avis";
    }

    private String buildRatingStars(int rating) {
        int normalizedRating = Math.max(0, Math.min(ReservationService.MAX_STUDENT_RATING, rating));
        return "★".repeat(normalizedRating)
            + "☆".repeat(Math.max(0, ReservationService.MAX_STUDENT_RATING - normalizedRating));
    }

    private void animateReviewDialogEntrance(VBox root) {
        if (root == null) {
            return;
        }

        animateReviewNode(root, 0.0);

        Object animatedNodes = root.getProperties().get("calendar-review-animated-nodes");
        if (!(animatedNodes instanceof List<?> nodes)) {
            return;
        }

        double delay = 90.0;
        for (Object candidate : nodes) {
            if (candidate instanceof Node node) {
                animateReviewNode(node, delay);
                delay += 75.0;
            }
        }
    }

    private void animateReviewNode(Node node, double delayMillis) {
        if (node == null) {
            return;
        }

        node.setOpacity(0.0);
        node.setTranslateY(16.0);
        node.setScaleX(0.985);
        node.setScaleY(0.985);

        Duration delay = Duration.millis(delayMillis);

        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setDelay(delay);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(250), node);
        slide.setDelay(delay);
        slide.setFromY(16.0);
        slide.setToY(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), node);
        scale.setDelay(delay);
        scale.setFromX(0.985);
        scale.setFromY(0.985);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide, scale).play();
    }

    private void applyDialogTheme(DialogPane dialogPane) {
        if (dialogPane == null || calendarGrid == null || calendarGrid.getScene() == null) {
            return;
        }
        dialogPane.getStylesheets().setAll(calendarGrid.getScene().getStylesheets());
    }

    private void appendDialogStylesheet(DialogPane dialogPane, String stylesheetPath) {
        if (dialogPane == null || stylesheetPath == null || stylesheetPath.isBlank()) {
            return;
        }

        java.net.URL resource = getClass().getResource(stylesheetPath);
        if (resource == null) {
            return;
        }

        String stylesheet = resource.toExternalForm();
        if (!dialogPane.getStylesheets().contains(stylesheet)) {
            dialogPane.getStylesheets().add(stylesheet);
        }
    }

    private HBox buildSelectedInfoRow(String label, String value) {
        HBox row = new HBox(8.0);
        row.getStyleClass().add("calendar-selected-row");

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("calendar-selected-label");

        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.getStyleClass().add("calendar-selected-value");

        row.getChildren().addAll(labelNode, valueNode);
        return row;
    }

    private void configureGrid() {
        if (calendarGrid.getColumnConstraints().isEmpty()) {
            for (int i = 0; i < 7; i++) {
                ColumnConstraints column = new ColumnConstraints();
                column.setPercentWidth(100.0 / 7.0);
                column.setHgrow(Priority.ALWAYS);
                column.setFillWidth(true);
                calendarGrid.getColumnConstraints().add(column);
            }
        }

        if (calendarGrid.getRowConstraints().isEmpty()) {
            for (int i = 0; i < 6; i++) {
                RowConstraints row = new RowConstraints();
                row.setMinHeight(92.0);
                row.setPrefHeight(102.0);
                row.setVgrow(Priority.ALWAYS);
                row.setFillHeight(true);
                calendarGrid.getRowConstraints().add(row);
            }
        }
    }

    private boolean isUpcoming(Seance seance) {
        return seance.getStartAt() != null && !seance.getStartAt().isBefore(LocalDateTime.now());
    }

    private String formatUpcomingDate(LocalDateTime value) {
        return value != null ? value.format(UPCOMING_FORMATTER) : "";
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DETAILS_FORMATTER) : "Non renseigne";
    }

    private String formatTime(LocalDateTime value) {
        return value != null ? value.format(DAY_TIME_FORMATTER) : "--:--";
    }

    private String formatReservationCount(int reservationCount) {
        if (reservationCount <= 0) {
            return "0 reservation";
        }
        return reservationCount == 1 ? "1 reservation" : reservationCount + " reservations";
    }

    private String mapStatusLabel(int status) {
        return switch (status) {
            case 1 -> "Publiee";
            case 2 -> "Archivee";
            default -> "Brouillon";
        };
    }

    private String mapStatusStyle(int status) {
        return switch (status) {
            case 1 -> "confirmed";
            case 2 -> "completed";
            default -> "pending";
        };
    }

    private String mapModeLabel(String mode) {
        return Seance.MODE_ONSITE.equals(mode) ? "Presentiel" : "En ligne";
    }

    private String formatSalleReference(Seance seance) {
        if (seance == null || !seance.isPresentiel() || seance.getSalleId() == null) {
            return "Non requis";
        }
        return "Salle #" + seance.getSalleId();
    }

    private String formatEquipementReferences(Seance seance) {
        if (seance == null || !seance.isPresentiel() || seance.getEquipementQuantites().isEmpty()) {
            return "Aucun materiel";
        }
        return seance.getEquipementQuantites().entrySet().stream()
            .map(entry -> "Materiel #" + entry.getKey() + " x" + Math.max(1, entry.getValue()))
            .collect(Collectors.joining(", "));
    }

    private boolean isSelected(Seance seance) {
        return selectedSeance != null && seance != null && selectedSeance.getId() == seance.getId();
    }

    private String safeText(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Non renseigne";
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private String buildDaySessionText(Seance seance) {
        String time = seance.getStartAt() != null ? seance.getStartAt().format(DAY_TIME_FORMATTER) : "--:--";
        return time + "  " + truncate(seance.getMatiere(), 18);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
