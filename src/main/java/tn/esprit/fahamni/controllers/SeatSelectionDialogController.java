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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SeatSelectionDialogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int TARGET_SEAT_COLUMNS = 6;
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
    private final Map<Integer, SeatVisualPlacement> visualPlacements = new LinkedHashMap<>();

    public void configure(Seance seance, List<SeatSelectionOption> seatOptions, Button confirmButton) {
        this.confirmButton = confirmButton;
        this.selectedPlaceId = null;
        this.selectedSeatButton = null;

        if (this.confirmButton != null) {
            this.confirmButton.setDisable(true);
        }

        seatSelectionTitleLabel.setText(safeText(seance == null ? null : seance.getMatiere()));
        seatSelectionSubtitleLabel.setText(buildSubtitle(seance));

        int totalSeats = seatOptions == null ? 0 : seatOptions.size();
        long availableSeats = seatOptions == null ? 0 : seatOptions.stream().filter(SeatSelectionOption::selectable).count();
        seatSelectionCapacityChipLabel.setText(totalSeats + " place(s)");
        seatSelectionAvailabilityChipLabel.setText(availableSeats + " libre(s)");
        seatSelectionLayoutChipLabel.setText("Plan de salle");
        selectedSeatLabel.setText("Aucune place selectionnee");
        seatSelectionHintLabel.setText(
            "Cliquez sur un carre disponible pour afficher sa position."
        );

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
            Label emptyLabel = new Label("Aucune place configur\u00E9e pour cette salle.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().addAll("reservation-section-copy", "seat-grid-empty-state");
            seatMapGrid.add(emptyLabel, 0, 0);
            return;
        }

        currentSeatLayout = buildSeatColumnLayout(seatOptions.size());
        int maxRow = buildVisualPlacements(seatOptions);
        if (seatSelectionLayoutChipLabel != null) {
            seatSelectionLayoutChipLabel.setText(buildLayoutChipText(currentSeatLayout));
        }

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

    private SeatColumnLayout buildSeatColumnLayout(int seatCount) {
        if (seatCount <= 0) {
            return EMPTY_LAYOUT;
        }
        int logicalColumns = Math.min(TARGET_SEAT_COLUMNS, Math.max(1, seatCount));
        if (logicalColumns <= 3) {
            int[] mapping = new int[logicalColumns + 1];
            for (int column = 1; column <= logicalColumns; column++) {
                mapping[column] = column;
            }
            return new SeatColumnLayout(logicalColumns, logicalColumns, -1, logicalColumns, mapping);
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

    private String buildSelectedSeatBadgeText(SeatSelectionOption option) {
        if (option == null) {
            return "Aucune place selectionnee";
        }
        SeatVisualPlacement placement = resolvePlacement(option);
        int logicalColumn = placement == null ? option.column() : placement.logicalColumn();
        return option.buttonLabel()
            + " | "
            + resolveSeatBlock(logicalColumn)
            + " | "
            + formatRowLabel(resolveDisplayRow(option))
            + logicalColumn;
    }

    private String buildSeatDisposition(SeatSelectionOption option) {
        if (option == null) {
            return "Disposition indisponible";
        }
        SeatVisualPlacement placement = resolvePlacement(option);
        int logicalColumn = placement == null ? option.column() : placement.logicalColumn();
        return resolveSeatBlock(logicalColumn)
            + " | Rangee "
            + formatRowLabel(resolveDisplayRow(option))
            + " | Colonne "
            + logicalColumn;
    }

    private String resolveSeatBlock(int column) {
        if (!currentSeatLayout.hasAisle()) {
            return "Zone centrale";
        }
        return column <= currentSeatLayout.leftBlockColumns() ? "Bloc gauche" : "Bloc droit";
    }

    private String buildLayoutChipText(SeatColumnLayout layout) {
        if (layout == null || layout.logicalColumns() <= 0) {
            return "Plan de salle";
        }
        if (layout.logicalColumns() == TARGET_SEAT_COLUMNS) {
            return "Disposition 3 + couloir + 3";
        }
        return layout.logicalColumns() <= 1
            ? "1 colonne"
            : layout.logicalColumns() + " colonnes";
    }

    private int buildVisualPlacements(List<SeatSelectionOption> seatOptions) {
        List<SeatSelectionOption> orderedSeats = sortSeatOptions(seatOptions);
        int columns = Math.max(1, currentSeatLayout.logicalColumns());

        for (int index = 0; index < orderedSeats.size(); index++) {
            SeatSelectionOption option = orderedSeats.get(index);
            int logicalColumn = (index % columns) + 1;
            int rowIndex = index / columns;
            visualPlacements.put(option.placeId(), new SeatVisualPlacement(logicalColumn, rowIndex));
        }

        return Math.max(1, (int) Math.ceil(orderedSeats.size() / (double) columns));
    }

    private List<SeatSelectionOption> sortSeatOptions(List<SeatSelectionOption> seatOptions) {
        return seatOptions.stream()
            .sorted(Comparator.comparingInt(SeatSelectionOption::row)
                .thenComparingInt(SeatSelectionOption::column)
                .thenComparingInt(SeatSelectionOption::placeId))
            .toList();
    }

    private SeatVisualPlacement resolvePlacement(SeatSelectionOption option) {
        if (option == null) {
            return null;
        }
        return visualPlacements.get(option.placeId());
    }

    private int resolveDisplayRow(SeatSelectionOption option) {
        SeatVisualPlacement placement = resolvePlacement(option);
        return placement == null ? option.row() : placement.rowIndex() + 1;
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

    private record SeatVisualPlacement(int logicalColumn, int rowIndex) {
    }
}
