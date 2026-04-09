package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.MockTutorDirectoryService;
import tn.esprit.fahamni.services.SeanceService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SeanceController {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter UPCOMING_FORMATTER = DateTimeFormatter.ofPattern("dd MMM - HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final SeanceService seanceService = new SeanceService();
    private final MockTutorDirectoryService tutorDirectoryService = new MockTutorDirectoryService();

    private YearMonth displayedMonth;
    private List<Seance> seances = List.of();

    @FXML
    private Label monthLabel;

    @FXML
    private GridPane calendarGrid;

    @FXML
    private VBox upcomingSessionsBox;

    @FXML
    private void initialize() {
        displayedMonth = YearMonth.from(LocalDate.now());
        configureGrid();
        reloadCalendarData();
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
            Label pill = new Label(buildDaySessionText(orderedSessions.get(i)));
            pill.getStyleClass().add("calendar-session-pill");
            pill.setWrapText(true);
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
