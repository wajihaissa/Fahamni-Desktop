package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.ReservationService;
import tn.esprit.fahamni.services.ReservationService.ReservationStats;
import tn.esprit.fahamni.services.SeanceService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private static final String DEFAULT_CALENDAR_SORT = "Ajout recent";
    private static final List<String> CALENDAR_SORT_OPTIONS = List.of(
        DEFAULT_CALENDAR_SORT,
        "Date la plus proche",
        "Date la plus lointaine",
        "Matiere A-Z",
        "Tuteur A-Z",
        "Statut",
        "Capacite decroissante"
    );
    private static final List<DateTimeFormatter> DATE_FILTER_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();
    private final ReservationService reservationService = new ReservationService();

    private YearMonth displayedMonth;
    private List<Seance> allSeances = List.of();
    private List<Seance> visibleSeances = List.of();
    private Seance selectedSeance;
    private boolean initializingView;

    @FXML
    private Label monthLabel;

    @FXML
    private GridPane calendarGrid;

    @FXML
    private VBox upcomingSessionsBox;

    @FXML
    private VBox selectedSessionDetailsBox;

    @FXML
    private TextField calendarStartDateField;

    @FXML
    private TextField calendarEndDateField;

    @FXML
    private ComboBox<String> calendarSortComboBox;

    @FXML
    private Label calendarFilterFeedbackLabel;

    @FXML
    private void initialize() {
        initializingView = true;
        displayedMonth = YearMonth.from(LocalDate.now());
        calendarSortComboBox.getItems().setAll(CALENDAR_SORT_OPTIONS);
        calendarSortComboBox.setValue(DEFAULT_CALENDAR_SORT);
        configureGrid();
        hideCalendarFilterFeedback();
        initializingView = false;
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

    @FXML
    private void handleApplyCalendarFilters() {
        if (initializingView) {
            return;
        }
        applyCalendarFilters();
    }

    @FXML
    private void handleResetCalendarFilters() {
        initializingView = true;
        calendarStartDateField.clear();
        calendarEndDateField.clear();
        calendarSortComboBox.setValue(DEFAULT_CALENDAR_SORT);
        selectedSeance = null;
        initializingView = false;
        applyCalendarFilters();
    }

    private void reloadCalendarData() {
        allSeances = seanceService.getAll();
        applyCalendarFilters();
    }

    private void applyCalendarFilters() {
        hideCalendarFilterFeedback();

        try {
            visibleSeances = sortSeances(filterSeancesByDate(allSeances));
        } catch (IllegalArgumentException exception) {
            showCalendarFilterFeedback(exception.getMessage());
            return;
        }

        if (selectedSeance != null && visibleSeances.stream().noneMatch(this::isSelected)) {
            selectedSeance = null;
        }
        renderUpcomingSessions();
        renderCalendar();
        renderSelectedSessionDetails();
    }

    private List<Seance> filterSeancesByDate(List<Seance> source) {
        LocalDate startDate = parseOptionalFilterDate(calendarStartDateField.getText(), "date debut");
        LocalDate endDate = parseOptionalFilterDate(calendarEndDateField.getText(), "date fin");

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("La date debut doit etre avant la date fin.");
        }

        return source.stream()
            .filter(seance -> matchesDateRange(seance, startDate, endDate))
            .toList();
    }

    private boolean matchesDateRange(Seance seance, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }
        if (seance.getStartAt() == null) {
            return false;
        }

        LocalDate sessionDate = seance.getStartAt().toLocalDate();
        boolean afterStart = startDate == null || !sessionDate.isBefore(startDate);
        boolean beforeEnd = endDate == null || !sessionDate.isAfter(endDate);
        return afterStart && beforeEnd;
    }

    private LocalDate parseOptionalFilterDate(String value, String fieldName) {
        String candidate = normalizeText(value);
        if (candidate == null) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FILTER_FORMATTERS) {
            try {
                return LocalDate.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        throw new IllegalArgumentException("Format invalide pour " + fieldName + ". Utilisez jj/mm/aaaa.");
    }

    private List<Seance> sortSeances(List<Seance> source) {
        return source.stream()
            .sorted(buildCalendarComparator())
            .toList();
    }

    private Comparator<Seance> buildCalendarComparator() {
        String selectedSort = calendarSortComboBox != null ? calendarSortComboBox.getValue() : DEFAULT_CALENDAR_SORT;
        Comparator<Seance> dateAscending = Comparator.comparing(
            Seance::getStartAt,
            Comparator.nullsLast(Comparator.naturalOrder())
        );

        if ("Date la plus proche".equals(selectedSort)) {
            return dateAscending;
        }
        if ("Date la plus lointaine".equals(selectedSort)) {
            return dateAscending.reversed();
        }
        if ("Matiere A-Z".equals(selectedSort)) {
            return Comparator.comparing((Seance seance) -> safe(seance.getMatiere()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(dateAscending);
        }
        if ("Tuteur A-Z".equals(selectedSort)) {
            return Comparator.comparing(
                    (Seance seance) -> safe(tutorDirectoryService.getTutorDisplayName(seance.getTuteurId())),
                    String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(dateAscending);
        }
        if ("Statut".equals(selectedSort)) {
            return Comparator.comparingInt(Seance::getStatus)
                .thenComparing(dateAscending);
        }
        if ("Capacite decroissante".equals(selectedSort)) {
            return Comparator.comparingInt(Seance::getMaxParticipants)
                .reversed()
                .thenComparing(dateAscending);
        }
        return Comparator.comparingInt(Seance::getId).reversed();
    }

    private void renderUpcomingSessions() {
        upcomingSessionsBox.getChildren().clear();

        List<Seance> upcomingSessions = visibleSeances.stream()
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
        card.setOnMouseClicked(event -> selectSeance(seance));
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

        Map<LocalDate, List<Seance>> sessionsByDay = visibleSeances.stream()
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
            pill.setOnMouseClicked(event -> selectSeance(session));
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
            buildSelectedInfoRow("Reservations", formatReservationCount(stats.total()))
        );

        Label description = new Label(safeText(selectedSeance.getDescription()));
        description.setWrapText(true);
        description.getStyleClass().add("calendar-selected-description");

        Button clearButton = new Button("Effacer la selection");
        clearButton.getStyleClass().addAll("action-button", "secondary");
        clearButton.setOnAction(event -> clearSelectedSeance());

        detailCard.getChildren().addAll(header, rows, description, clearButton);
        selectedSessionDetailsBox.getChildren().add(detailCard);
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

    private boolean isSelected(Seance seance) {
        return selectedSeance != null && seance != null && selectedSeance.getId() == seance.getId();
    }

    private String safeText(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : "Non renseigne";
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private void showCalendarFilterFeedback(String message) {
        calendarFilterFeedbackLabel.setText(message);
        calendarFilterFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", "error");
        calendarFilterFeedbackLabel.setManaged(true);
        calendarFilterFeedbackLabel.setVisible(true);
    }

    private void hideCalendarFilterFeedback() {
        calendarFilterFeedbackLabel.setText("");
        calendarFilterFeedbackLabel.getStyleClass().setAll("frontoffice-feedback", "error");
        calendarFilterFeedbackLabel.setManaged(false);
        calendarFilterFeedbackLabel.setVisible(false);
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
