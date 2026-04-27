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
import tn.esprit.fahamni.utils.SeatSelectionLayoutResolver;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class SeatSelectionDialogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
    private SeatSelectionLayoutResolver.LayoutProjection currentLayoutProjection = SeatSelectionLayoutResolver.emptyProjection();

    public void configure(Seance seance, List<SeatSelectionOption> seatOptions, Button confirmButton, String typeDisposition) {
        this.confirmButton = confirmButton;
        this.selectedPlaceId = null;
        this.selectedSeatButton = null;
        this.currentLayoutProjection = SeatSelectionLayoutResolver.resolve(
            typeDisposition,
            buildSeatLayoutInputs(seatOptions == null ? List.of() : seatOptions)
        );

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

        if (seatOptions.isEmpty()) {
            Label emptyLabel = new Label("Aucune place configuree pour cette salle.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().addAll("reservation-section-copy", "seat-grid-empty-state");
            seatMapGrid.add(emptyLabel, 0, 0);
            return;
        }

        SeatSelectionLayoutResolver.SeatColumnLayout currentSeatLayout = currentLayoutProjection.seatColumnLayout();
        int maxRow = currentLayoutProjection.totalRows();
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
            SeatSelectionLayoutResolver.SeatVisualPlacement placement = resolvePlacement(option);
            if (placement == null) {
                continue;
            }

            Button seatButton = createSeatButton(option);
            seatMapGrid.add(
                seatButton,
                placement.visualColumn(),
                placement.displayRow()
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

        SeatSelectionLayoutResolver.SeatVisualPlacement placement = resolvePlacement(option);
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

        SeatSelectionLayoutResolver.SeatVisualPlacement placement = resolvePlacement(option);
        int logicalColumn = resolveDisplayColumn(option, placement);
        return resolveSeatZone(option, placement)
            + " | Rangee "
            + formatRowLabel(resolveDisplayRow(option, placement))
            + " | Colonne "
            + logicalColumn;
    }

    private SeatSelectionLayoutResolver.SeatVisualPlacement resolvePlacement(SeatSelectionOption option) {
        if (option == null) {
            return null;
        }
        return currentLayoutProjection.placementFor(option.placeId());
    }

    private int resolveDisplayRow(SeatSelectionOption option, SeatSelectionLayoutResolver.SeatVisualPlacement placement) {
        return placement == null ? option.row() : placement.displayRow();
    }

    private int resolveDisplayColumn(SeatSelectionOption option, SeatSelectionLayoutResolver.SeatVisualPlacement placement) {
        return placement == null ? option.column() : placement.logicalColumn();
    }

    private String resolveSeatZone(SeatSelectionOption option, SeatSelectionLayoutResolver.SeatVisualPlacement placement) {
        if (placement != null && placement.zoneLabel() != null && !placement.zoneLabel().isBlank()) {
            return placement.zoneLabel();
        }
        return switch (currentLayoutProjection.displayProfile()) {
            case CONFERENCE -> "Bloc central";
            case REUNION -> "Table reunion";
            case U_SHAPE -> "Base";
            case CLASSROOM -> resolveFallbackClassroomZone(option.column());
        };
    }

    private String resolveFallbackClassroomZone(int logicalColumn) {
        SeatSelectionLayoutResolver.SeatColumnLayout layout = currentLayoutProjection.seatColumnLayout();
        if (!layout.hasAisle()) {
            return "Zone centrale";
        }
        return logicalColumn <= layout.leftBlockColumns() ? "Bloc gauche" : "Bloc droit";
    }

    private String buildLayoutChipText() {
        return "Disposition " + currentLayoutProjection.dispositionLabel();
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

    private List<SeatSelectionLayoutResolver.SeatLayoutInput> buildSeatLayoutInputs(List<SeatSelectionOption> seatOptions) {
        return seatOptions.stream()
            .map(option -> new SeatSelectionLayoutResolver.SeatLayoutInput(
                option.placeId(),
                option.row(),
                option.column()
            ))
            .toList();
    }
}
