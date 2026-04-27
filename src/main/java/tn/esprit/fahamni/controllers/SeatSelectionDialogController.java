package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.services.ReservationService.SeatSelectionOption;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SeatSelectionDialogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int CLASSROOM_MAX_COLUMNS = 6;
    private static final int CONFERENCE_MAX_COLUMNS = 8;
    private static final int U_SHAPE_MAX_COLUMNS = 7;
    private static final SeatColumnLayout EMPTY_LAYOUT = new SeatColumnLayout(0, 0, -1, 0, new int[0]);

    @FXML
    private Label seatSelectionTitleLabel;

    @FXML
    private Label seatSelectionSubtitleLabel;

    @FXML
    private Label seatSelectionCapacityChipLabel;

    @FXML
    private Label seatSelectionAvailabilityChipLabel;

    @FXML
    private Label seatSelectionLayoutChipLabel;

    @FXML
    private Label selectedSeatLabel;

    @FXML
    private Label seatSelectionHintLabel;

    @FXML
    private GridPane seatMapGrid;

    @FXML
    private ScrollPane seatMapScroll;

    private Button confirmButton;
    private Button selectedSeatButton;
    private Integer selectedPlaceId;
    private SeatColumnLayout currentSeatLayout = EMPTY_LAYOUT;
    private SeatDisplayProfile currentDisplayProfile = SeatDisplayProfile.CLASSROOM;
    private String currentDispositionLabel = "cours";
    private final Map<Integer, SeatVisualPlacement> visualPlacements = new LinkedHashMap<>();

    public void configure(Seance seance, List<SeatSelectionOption> seatOptions, Button confirmButton, String typeDisposition) {
        this.confirmButton = confirmButton;
        this.selectedPlaceId = null;
        this.selectedSeatButton = null;
        this.currentDisplayProfile = resolveDisplayProfile(typeDisposition);
        this.currentDispositionLabel = resolveDispositionLabel(typeDisposition, currentDisplayProfile);

        if (this.confirmButton != null) {
            this.confirmButton.setDisable(true);
        }

        seatSelectionTitleLabel.setText(safeText(seance == null ? null : seance.getMatiere()));
        seatSelectionSubtitleLabel.setText(buildSubtitle(seance));

        int totalSeats = seatOptions == null ? 0 : seatOptions.size();
        long availableSeats = seatOptions == null ? 0 : seatOptions.stream().filter(SeatSelectionOption::selectable).count();
        seatSelectionCapacityChipLabel.setText(totalSeats + " place(s)");
        seatSelectionAvailabilityChipLabel.setText(availableSeats + " libre(s)");
        seatSelectionLayoutChipLabel.setText(buildLayoutChipText());
        selectedSeatLabel.setText("Aucune place selectionnee");
        seatSelectionHintLabel.setText("Cliquez sur un carre disponible pour afficher sa position.");

        renderSeatGrid(seatOptions == null ? List.of() : seatOptions);
    }

    public Integer getSelectedPlaceId() {
        return selectedPlaceId;
    }

    private void renderSeatGrid(List<SeatSelectionOption> seatOptions) {
        seatMapGrid.getChildren().clear();
        seatMapGrid.getColumnConstraints().clear();
        seatMapGrid.getRowConstraints().clear();
        currentSeatLayout = EMPTY_LAYOUT;
        visualPlacements.clear();

        if (seatOptions.isEmpty()) {
            Label emptyLabel = new Label("Aucune place configuree pour cette salle.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().addAll("reservation-section-copy", "seat-grid-empty-state");
            seatMapGrid.add(emptyLabel, 0, 0);
            return;
        }

        currentSeatLayout = buildSeatColumnLayout(seatOptions.size());
        int maxRow = buildVisualPlacements(seatOptions);
        seatSelectionLayoutChipLabel.setText(buildLayoutChipText());

        if (seatMapScroll != null) {
            double viewportHeight = 56.0 + (maxRow * 42.0);
            seatMapScroll.setPrefViewportHeight(Math.max(280.0, Math.min(560.0, viewportHeight)));
        }

        ColumnConstraints axisColumn = new ColumnConstraints();
        axisColumn.setMinWidth(24.0);
        axisColumn.setPrefWidth(26.0);
        axisColumn.setMaxWidth(26.0);
        seatMapGrid.getColumnConstraints().add(axisColumn);

        int logicalColumn = 1;
        for (int visualColumn = 1; visualColumn <= currentSeatLayout.visualColumns(); visualColumn++) {
            if (currentSeatLayout.isAisle(visualColumn)) {
                ColumnConstraints aisle = new ColumnConstraints();
                aisle.setMinWidth(34.0);
                aisle.setPrefWidth(40.0);
                aisle.setMaxWidth(44.0);
                seatMapGrid.getColumnConstraints().add(aisle);
                continue;
            }

            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setMinWidth(30.0);
            constraints.setPrefWidth(32.0);
            constraints.setMaxWidth(32.0);
            seatMapGrid.getColumnConstraints().add(constraints);

            Label columnLabel = new Label(String.valueOf(logicalColumn));
            columnLabel.getStyleClass().add("seat-grid-axis-label");
            seatMapGrid.add(columnLabel, visualColumn, 0);
            logicalColumn++;
        }

        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(22.0);
        headerRow.setPrefHeight(24.0);
        seatMapGrid.getRowConstraints().add(headerRow);

        for (int row = 1; row <= maxRow; row++) {
            RowConstraints constraints = new RowConstraints();
            constraints.setMinHeight(34.0);
            constraints.setPrefHeight(36.0);
            seatMapGrid.getRowConstraints().add(constraints);

            Label rowLabel = new Label(formatRowLabel(row));
            rowLabel.getStyleClass().add("seat-grid-axis-label");
            seatMapGrid.add(rowLabel, 0, row);
        }

        for (SeatSelectionOption option : sortSeatOptions(seatOptions)) {
            SeatVisualPlacement placement = resolvePlacement(option);
            if (placement == null) {
                continue;
            }

            Button seatButton = createSeatButton(option);
            seatMapGrid.add(
                seatButton,
                currentSeatLayout.visualColumnForLogical(placement.logicalColumn()),
                placement.rowIndex() + 1
            );
        }
    }

    private Button createSeatButton(SeatSelectionOption option) {
        Button seatButton = new Button("");
        seatButton.getStyleClass().addAll("seat-map-button", resolveSeatStateStyle(option));
        seatButton.setMinSize(26.0, 26.0);
        seatButton.setPrefSize(28.0, 28.0);
        seatButton.setMaxSize(28.0, 28.0);
        seatButton.setWrapText(false);
        seatButton.setFocusTraversable(false);
        seatButton.setUserData(option);
        seatButton.setTooltip(new Tooltip(option.buttonLabel() + " | " + buildSeatDisposition(option)));

        if (!option.selectable()) {
            seatButton.setDisable(true);
            return seatButton;
        }

        seatButton.setOnAction(event -> selectSeat(seatButton, option));
        return seatButton;
    }

    private void selectSeat(Button seatButton, SeatSelectionOption option) {
        if (selectedSeatButton != null && selectedSeatButton != seatButton) {
            selectedSeatButton.getStyleClass().remove("selected");
        }

        selectedSeatButton = seatButton;
        selectedPlaceId = option.placeId();

        if (!seatButton.getStyleClass().contains("selected")) {
            seatButton.getStyleClass().add("selected");
        }

        selectedSeatLabel.setText(buildSelectedSeatBadgeText(option));
        seatSelectionHintLabel.setText("Selection active. Confirmez pour finaliser la reservation.");

        if (confirmButton != null) {
            confirmButton.setDisable(false);
        }
    }

    private String resolveSeatStateStyle(SeatSelectionOption option) {
        if (option == null) {
            return "blocked";
        }
        if (option.reserved()) {
            return "occupied";
        }
        if (option.selectable()) {
            return "available";
        }
        return "blocked";
    }

    private String buildSubtitle(Seance seance) {
        if (seance == null) {
            return "Selectionnez une place libre pour confirmer votre reservation.";
        }

        String schedule = seance.getStartAt() == null
            ? "Horaire non renseigne"
            : seance.getStartAt().format(DATE_TIME_FORMATTER);
        return "Seance du " + schedule + ". Choisissez votre place puis confirmez votre reservation.";
    }

    private String safeText(String value) {
        if (value == null) {
            return "la seance";
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? "la seance" : normalized;
    }

    private SeatDisplayProfile resolveDisplayProfile(String typeDisposition) {
        String normalizedDisposition = normalizeDisposition(typeDisposition);
        return switch (normalizedDisposition) {
            case "u" -> SeatDisplayProfile.U_SHAPE;
            case "conference", "cinema", "reunion" -> SeatDisplayProfile.CONFERENCE;
            case "atelier", "informatique", "classe", "cours" -> SeatDisplayProfile.CLASSROOM;
            default -> SeatDisplayProfile.CLASSROOM;
        };
    }

    private String resolveDispositionLabel(String typeDisposition, SeatDisplayProfile profile) {
        String normalizedDisposition = normalizeDisposition(typeDisposition);
        return switch (normalizedDisposition) {
            case "classe", "cours" -> "cours";
            case "u" -> "en U";
            case "conference" -> "conference";
            case "cinema" -> "cinema";
            case "reunion" -> "reunion";
            case "atelier" -> "atelier";
            case "informatique" -> "informatique";
            default -> profile == SeatDisplayProfile.U_SHAPE ? "en U"
                : profile == SeatDisplayProfile.CONFERENCE ? "conference"
                : "cours";
        };
    }

    private String normalizeDisposition(String typeDisposition) {
        if (typeDisposition == null) {
            return "";
        }
        return typeDisposition.trim().toLowerCase(Locale.ROOT);
    }

    private SeatColumnLayout buildSeatColumnLayout(int seatCount) {
        if (seatCount <= 0) {
            return EMPTY_LAYOUT;
        }

        return switch (currentDisplayProfile) {
            case CONFERENCE -> buildCompactColumnLayout(determineConferenceColumns(seatCount));
            case U_SHAPE -> buildCompactColumnLayout(determineUShapeColumns(seatCount));
            case CLASSROOM -> buildClassroomColumnLayout(seatCount);
        };
    }

    private SeatColumnLayout buildClassroomColumnLayout(int seatCount) {
        int logicalColumns = Math.min(CLASSROOM_MAX_COLUMNS, Math.max(1, seatCount));
        if (logicalColumns <= 3) {
            return buildCompactColumnLayout(logicalColumns);
        }

        int leftGroup = (int) Math.ceil(logicalColumns / 2.0);
        int aisleColumn = leftGroup + 1;
        int[] mapping = new int[logicalColumns + 1];
        int visualColumn = 1;
        for (int column = 1; column <= logicalColumns; column++) {
            mapping[column] = visualColumn++;
            if (column == leftGroup) {
                visualColumn++;
            }
        }

        return new SeatColumnLayout(logicalColumns, logicalColumns + 1, aisleColumn, leftGroup, mapping);
    }

    private SeatColumnLayout buildCompactColumnLayout(int logicalColumns) {
        int[] mapping = new int[logicalColumns + 1];
        for (int column = 1; column <= logicalColumns; column++) {
            mapping[column] = column;
        }
        return new SeatColumnLayout(logicalColumns, logicalColumns, -1, logicalColumns, mapping);
    }

    private int determineConferenceColumns(int seatCount) {
        return Math.min(CONFERENCE_MAX_COLUMNS, Math.max(4, (int) Math.ceil(Math.sqrt(seatCount)) + 1));
    }

    private int determineUShapeColumns(int seatCount) {
        if (seatCount <= 4) {
            return Math.max(2, seatCount);
        }
        return Math.min(U_SHAPE_MAX_COLUMNS, Math.max(4, (int) Math.ceil(Math.sqrt(seatCount)) + 1));
    }

    private int buildVisualPlacements(List<SeatSelectionOption> seatOptions) {
        return switch (currentDisplayProfile) {
            case CONFERENCE -> buildConferencePlacements(seatOptions);
            case U_SHAPE -> buildUShapePlacements(seatOptions);
            case CLASSROOM -> buildClassroomPlacements(seatOptions);
        };
    }

    private int buildClassroomPlacements(List<SeatSelectionOption> seatOptions) {
        List<SeatSelectionOption> orderedSeats = sortSeatOptions(seatOptions);
        int columns = Math.max(1, currentSeatLayout.logicalColumns());

        for (int index = 0; index < orderedSeats.size(); index++) {
            SeatSelectionOption option = orderedSeats.get(index);
            int logicalColumn = (index % columns) + 1;
            int rowIndex = index / columns;
            visualPlacements.put(option.placeId(), new SeatVisualPlacement(logicalColumn, rowIndex, resolveClassroomZone(logicalColumn)));
        }

        return Math.max(1, (int) Math.ceil(orderedSeats.size() / (double) columns));
    }

    private int buildConferencePlacements(List<SeatSelectionOption> seatOptions) {
        List<SeatSelectionOption> orderedSeats = sortSeatOptions(seatOptions);
        int columns = Math.max(1, currentSeatLayout.logicalColumns());
        int totalRows = Math.max(1, (int) Math.ceil(orderedSeats.size() / (double) columns));

        for (int rowIndex = 0; rowIndex < totalRows; rowIndex++) {
            int rowStart = rowIndex * columns;
            int rowSeatCount = Math.min(columns, orderedSeats.size() - rowStart);
            int leftOffset = Math.max(0, (columns - rowSeatCount) / 2);

            for (int offset = 0; offset < rowSeatCount; offset++) {
                SeatSelectionOption option = orderedSeats.get(rowStart + offset);
                int logicalColumn = leftOffset + offset + 1;
                visualPlacements.put(option.placeId(), new SeatVisualPlacement(logicalColumn, rowIndex, "Bloc central"));
            }
        }

        return totalRows;
    }

    private int buildUShapePlacements(List<SeatSelectionOption> seatOptions) {
        List<SeatSelectionOption> orderedSeats = sortSeatOptions(seatOptions);
        int columns = Math.max(2, currentSeatLayout.logicalColumns());

        if (orderedSeats.size() <= columns) {
            int leftOffset = Math.max(0, (columns - orderedSeats.size()) / 2);
            for (int index = 0; index < orderedSeats.size(); index++) {
                SeatSelectionOption option = orderedSeats.get(index);
                visualPlacements.put(option.placeId(), new SeatVisualPlacement(leftOffset + index + 1, 0, "Base"));
            }
            return 1;
        }

        int rows = Math.max(3, (int) Math.ceil(Math.max(0, orderedSeats.size() - columns) / 2.0) + 1);
        List<SeatVisualPlacement> perimeterPlacements = new ArrayList<>();

        for (int column = 1; column <= columns; column++) {
            perimeterPlacements.add(new SeatVisualPlacement(column, rows - 1, "Base"));
        }

        for (int step = 1; step < rows; step++) {
            int rowIndex = rows - 1 - step;
            perimeterPlacements.add(new SeatVisualPlacement(1, rowIndex, "Aile gauche"));
            perimeterPlacements.add(new SeatVisualPlacement(columns, rowIndex, "Aile droite"));
        }

        for (int index = 0; index < orderedSeats.size() && index < perimeterPlacements.size(); index++) {
            visualPlacements.put(orderedSeats.get(index).placeId(), perimeterPlacements.get(index));
        }

        return rows;
    }

    private List<SeatSelectionOption> sortSeatOptions(List<SeatSelectionOption> seatOptions) {
        return seatOptions.stream()
            .sorted(Comparator.comparingInt(SeatSelectionOption::row)
                .thenComparingInt(SeatSelectionOption::column)
                .thenComparingInt(SeatSelectionOption::placeId))
            .toList();
    }

    private String buildSelectedSeatBadgeText(SeatSelectionOption option) {
        if (option == null) {
            return "Aucune place selectionnee";
        }

        SeatVisualPlacement placement = resolvePlacement(option);
        int logicalColumn = resolveDisplayColumn(option, placement);
        return option.buttonLabel()
            + " | "
            + resolveSeatZone(option, placement)
            + " | "
            + formatRowLabel(resolveDisplayRow(option, placement))
            + logicalColumn;
    }

    private String buildSeatDisposition(SeatSelectionOption option) {
        if (option == null) {
            return "Disposition indisponible";
        }

        SeatVisualPlacement placement = resolvePlacement(option);
        int logicalColumn = resolveDisplayColumn(option, placement);
        return resolveSeatZone(option, placement)
            + " | Rangee "
            + formatRowLabel(resolveDisplayRow(option, placement))
            + " | Colonne "
            + logicalColumn;
    }

    private SeatVisualPlacement resolvePlacement(SeatSelectionOption option) {
        if (option == null) {
            return null;
        }
        return visualPlacements.get(option.placeId());
    }

    private int resolveDisplayRow(SeatSelectionOption option, SeatVisualPlacement placement) {
        return placement == null ? option.row() : placement.rowIndex() + 1;
    }

    private int resolveDisplayColumn(SeatSelectionOption option, SeatVisualPlacement placement) {
        return placement == null ? option.column() : placement.logicalColumn();
    }

    private String resolveSeatZone(SeatSelectionOption option, SeatVisualPlacement placement) {
        if (placement != null && placement.zoneLabel() != null && !placement.zoneLabel().isBlank()) {
            return placement.zoneLabel();
        }
        return switch (currentDisplayProfile) {
            case CONFERENCE -> "Bloc central";
            case U_SHAPE -> "Base";
            case CLASSROOM -> resolveClassroomZone(option.column());
        };
    }

    private String resolveClassroomZone(int logicalColumn) {
        if (!currentSeatLayout.hasAisle()) {
            return "Zone centrale";
        }
        return logicalColumn <= currentSeatLayout.leftBlockColumns() ? "Bloc gauche" : "Bloc droit";
    }

    private String buildLayoutChipText() {
        return "Disposition " + currentDispositionLabel;
    }

    private String formatRowLabel(int row) {
        if (row <= 0) {
            return "?";
        }

        StringBuilder label = new StringBuilder();
        int value = row;
        while (value > 0) {
            value--;
            label.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return label.toString();
    }

    private enum SeatDisplayProfile {
        CLASSROOM,
        CONFERENCE,
        U_SHAPE
    }

    private record SeatColumnLayout(int logicalColumns, int visualColumns, int aisleColumn, int leftBlockColumns, int[] mapping) {
        private int visualColumnForLogical(int logicalColumn) {
            return logicalColumn >= 0 && logicalColumn < mapping.length ? mapping[logicalColumn] : logicalColumn;
        }

        private boolean hasAisle() {
            return aisleColumn > 0;
        }

        private boolean isAisle(int visualColumn) {
            return hasAisle() && aisleColumn == visualColumn;
        }
    }

    private record SeatVisualPlacement(int logicalColumn, int rowIndex, String zoneLabel) {
    }
}
