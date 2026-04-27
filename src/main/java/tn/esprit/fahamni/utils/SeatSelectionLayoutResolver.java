package tn.esprit.fahamni.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SeatSelectionLayoutResolver {

    private static final int CLASSROOM_MAX_COLUMNS = 6;
    private static final int CONFERENCE_MAX_COLUMNS = 8;
    private static final int U_SHAPE_MAX_COLUMNS = 7;
    private static final SeatColumnLayout EMPTY_LAYOUT = new SeatColumnLayout(0, 0, -1, 0, new int[0]);
    private static final LayoutProjection EMPTY_PROJECTION = new LayoutProjection(
        SeatDisplayProfile.CLASSROOM,
        "cours",
        "classe",
        EMPTY_LAYOUT,
        Map.of(),
        0
    );

    private SeatSelectionLayoutResolver() {
    }

    public static LayoutProjection emptyProjection() {
        return EMPTY_PROJECTION;
    }

    public static LayoutProjection resolve(String typeDisposition, List<SeatLayoutInput> seats) {
        String normalizedDisposition = normalizeDisposition(typeDisposition);
        SeatDisplayProfile profile = resolveDisplayProfile(normalizedDisposition);
        String dispositionLabel = resolveDispositionLabel(normalizedDisposition, profile);
        String renderDisposition = resolveRenderDisposition(normalizedDisposition, profile);
        List<SeatLayoutInput> effectiveSeats = seats == null ? List.of() : List.copyOf(seats);
        SeatColumnLayout seatColumnLayout = buildSeatColumnLayout(effectiveSeats.size(), profile);
        PlacementResult placementResult = buildVisualPlacements(effectiveSeats, profile, seatColumnLayout);

        return new LayoutProjection(
            profile,
            dispositionLabel,
            renderDisposition,
            seatColumnLayout,
            placementResult.placements(),
            placementResult.totalRows()
        );
    }

    private static SeatDisplayProfile resolveDisplayProfile(String normalizedDisposition) {
        return switch (normalizedDisposition) {
            case "u" -> SeatDisplayProfile.U_SHAPE;
            case "reunion" -> SeatDisplayProfile.REUNION;
            case "conference", "cinema" -> SeatDisplayProfile.CONFERENCE;
            case "atelier", "informatique", "classe", "cours" -> SeatDisplayProfile.CLASSROOM;
            default -> SeatDisplayProfile.CLASSROOM;
        };
    }

    private static String resolveDispositionLabel(String normalizedDisposition, SeatDisplayProfile profile) {
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

    private static String resolveRenderDisposition(String normalizedDisposition, SeatDisplayProfile profile) {
        if ("reunion".equals(normalizedDisposition)) {
            return "reunion";
        }
        if (profile == SeatDisplayProfile.U_SHAPE) {
            return "u";
        }
        if (profile == SeatDisplayProfile.CONFERENCE) {
            return "conference";
        }
        return "informatique".equals(normalizedDisposition) ? "informatique" : "classe";
    }

    private static SeatColumnLayout buildSeatColumnLayout(int seatCount, SeatDisplayProfile profile) {
        if (seatCount <= 0) {
            return EMPTY_LAYOUT;
        }

        return switch (profile) {
            case CONFERENCE -> buildCompactColumnLayout(determineConferenceColumns(seatCount));
            case REUNION -> buildCompactColumnLayout(determineReunionColumns(seatCount));
            case U_SHAPE -> buildCompactColumnLayout(determineUShapeColumns(seatCount));
            case CLASSROOM -> buildClassroomColumnLayout(seatCount);
        };
    }

    private static SeatColumnLayout buildClassroomColumnLayout(int seatCount) {
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

    private static SeatColumnLayout buildCompactColumnLayout(int logicalColumns) {
        int[] mapping = new int[logicalColumns + 1];
        for (int column = 1; column <= logicalColumns; column++) {
            mapping[column] = column;
        }
        return new SeatColumnLayout(logicalColumns, logicalColumns, -1, logicalColumns, mapping);
    }

    private static int determineConferenceColumns(int seatCount) {
        return Math.min(CONFERENCE_MAX_COLUMNS, Math.max(4, (int) Math.ceil(Math.sqrt(seatCount)) + 1));
    }

    private static int determineUShapeColumns(int seatCount) {
        if (seatCount <= 4) {
            return Math.max(2, seatCount);
        }
        return Math.min(U_SHAPE_MAX_COLUMNS, Math.max(4, (int) Math.ceil(Math.sqrt(seatCount)) + 1));
    }

    private static int determineReunionColumns(int seatCount) {
        return computeReunionGridBounds(seatCount).columns();
    }

    private static PlacementResult buildVisualPlacements(
        List<SeatLayoutInput> seats,
        SeatDisplayProfile profile,
        SeatColumnLayout seatColumnLayout
    ) {
        if (seats.isEmpty()) {
            return new PlacementResult(Map.of(), 0);
        }

        List<SeatLayoutInput> orderedSeats = sortSeatInputs(seats);
        return switch (profile) {
            case CONFERENCE -> buildConferencePlacements(orderedSeats, seatColumnLayout);
            case REUNION -> buildReunionPlacements(orderedSeats, seatColumnLayout);
            case U_SHAPE -> buildUShapePlacements(orderedSeats, seatColumnLayout);
            case CLASSROOM -> buildClassroomPlacements(orderedSeats, seatColumnLayout);
        };
    }

    private static PlacementResult buildClassroomPlacements(List<SeatLayoutInput> orderedSeats, SeatColumnLayout seatColumnLayout) {
        Map<Integer, SeatVisualPlacement> placements = new LinkedHashMap<>();
        int columns = Math.max(1, seatColumnLayout.logicalColumns());

        for (int index = 0; index < orderedSeats.size(); index++) {
            SeatLayoutInput seat = orderedSeats.get(index);
            int logicalColumn = (index % columns) + 1;
            int rowIndex = index / columns;
            placements.put(seat.seatKey(), buildPlacement(logicalColumn, rowIndex, resolveClassroomZone(logicalColumn, seatColumnLayout), seatColumnLayout));
        }

        int totalRows = Math.max(1, (int) Math.ceil(orderedSeats.size() / (double) columns));
        return new PlacementResult(placements, totalRows);
    }

    private static PlacementResult buildConferencePlacements(List<SeatLayoutInput> orderedSeats, SeatColumnLayout seatColumnLayout) {
        Map<Integer, SeatVisualPlacement> placements = new LinkedHashMap<>();
        int columns = Math.max(1, seatColumnLayout.logicalColumns());
        int totalRows = Math.max(1, (int) Math.ceil(orderedSeats.size() / (double) columns));

        for (int rowIndex = 0; rowIndex < totalRows; rowIndex++) {
            int rowStart = rowIndex * columns;
            int rowSeatCount = Math.min(columns, orderedSeats.size() - rowStart);
            int leftOffset = Math.max(0, (columns - rowSeatCount) / 2);

            for (int offset = 0; offset < rowSeatCount; offset++) {
                SeatLayoutInput seat = orderedSeats.get(rowStart + offset);
                int logicalColumn = leftOffset + offset + 1;
                placements.put(seat.seatKey(), buildPlacement(logicalColumn, rowIndex, "Bloc central", seatColumnLayout));
            }
        }

        return new PlacementResult(placements, totalRows);
    }

    private static PlacementResult buildUShapePlacements(List<SeatLayoutInput> orderedSeats, SeatColumnLayout seatColumnLayout) {
        Map<Integer, SeatVisualPlacement> placements = new LinkedHashMap<>();
        int columns = Math.max(2, seatColumnLayout.logicalColumns());

        if (orderedSeats.size() <= columns) {
            int leftOffset = Math.max(0, (columns - orderedSeats.size()) / 2);
            for (int index = 0; index < orderedSeats.size(); index++) {
                SeatLayoutInput seat = orderedSeats.get(index);
                int logicalColumn = leftOffset + index + 1;
                placements.put(seat.seatKey(), buildPlacement(logicalColumn, 0, "Base", seatColumnLayout));
            }
            return new PlacementResult(placements, 1);
        }

        int rows = Math.max(3, (int) Math.ceil(Math.max(0, orderedSeats.size() - columns) / 2.0) + 1);
        List<SeatVisualPlacement> perimeterPlacements = new ArrayList<>();

        for (int column = 1; column <= columns; column++) {
            perimeterPlacements.add(buildPlacement(column, rows - 1, "Base", seatColumnLayout));
        }

        for (int step = 1; step < rows; step++) {
            int rowIndex = rows - 1 - step;
            perimeterPlacements.add(buildPlacement(1, rowIndex, "Aile gauche", seatColumnLayout));
            perimeterPlacements.add(buildPlacement(columns, rowIndex, "Aile droite", seatColumnLayout));
        }

        for (int index = 0; index < orderedSeats.size() && index < perimeterPlacements.size(); index++) {
            placements.put(orderedSeats.get(index).seatKey(), perimeterPlacements.get(index));
        }

        return new PlacementResult(placements, rows);
    }

    private static PlacementResult buildReunionPlacements(List<SeatLayoutInput> orderedSeats, SeatColumnLayout seatColumnLayout) {
        Map<Integer, SeatVisualPlacement> placements = new LinkedHashMap<>();
        ReunionGridBounds bounds = computeReunionGridBounds(orderedSeats.size());
        List<SeatVisualPlacement> perimeterPlacements = buildReunionPerimeterPlacements(bounds.rows(), bounds.columns(), seatColumnLayout);
        List<SeatVisualPlacement> effectivePlacements = selectBalancedReunionPlacements(perimeterPlacements, orderedSeats.size());

        for (int index = 0; index < orderedSeats.size() && index < effectivePlacements.size(); index++) {
            placements.put(orderedSeats.get(index).seatKey(), effectivePlacements.get(index));
        }

        return new PlacementResult(placements, bounds.rows());
    }

    private static SeatVisualPlacement buildPlacement(
        int logicalColumn,
        int rowIndex,
        String zoneLabel,
        SeatColumnLayout seatColumnLayout
    ) {
        return new SeatVisualPlacement(
            logicalColumn,
            seatColumnLayout.visualColumnForLogical(logicalColumn),
            rowIndex,
            zoneLabel
        );
    }

    private static String resolveClassroomZone(int logicalColumn, SeatColumnLayout seatColumnLayout) {
        if (!seatColumnLayout.hasAisle()) {
            return "Zone centrale";
        }
        return logicalColumn <= seatColumnLayout.leftBlockColumns() ? "Bloc gauche" : "Bloc droit";
    }

    private static ReunionGridBounds computeReunionGridBounds(int seatCount) {
        if (seatCount <= 0) {
            return new ReunionGridBounds(1, 1);
        }

        int rows = 4;
        int columns = 4;
        while (calculateReunionPerimeterSeatCount(rows, columns) < seatCount) {
            if (columns <= rows) {
                columns++;
            } else {
                rows++;
            }
        }
        return new ReunionGridBounds(rows, columns);
    }

    private static int calculateReunionPerimeterSeatCount(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return 0;
        }
        if (rows == 1) {
            return columns;
        }
        if (columns == 1) {
            return rows;
        }
        return (rows * 2) + (columns * 2) - 4;
    }

    private static List<SeatVisualPlacement> buildReunionPerimeterPlacements(int rows, int columns, SeatColumnLayout seatColumnLayout) {
        List<SeatVisualPlacement> perimeterPlacements = new ArrayList<>(calculateReunionPerimeterSeatCount(rows, columns));

        for (int column = 1; column <= columns; column++) {
            perimeterPlacements.add(buildPlacement(column, 0, "Devant table", seatColumnLayout));
        }
        for (int row = 2; row < rows; row++) {
            perimeterPlacements.add(buildPlacement(columns, row - 1, "Cote droit", seatColumnLayout));
        }
        for (int column = columns; column >= 1; column--) {
            perimeterPlacements.add(buildPlacement(column, rows - 1, "Fond table", seatColumnLayout));
        }
        for (int row = rows - 1; row >= 2; row--) {
            perimeterPlacements.add(buildPlacement(1, row - 1, "Cote gauche", seatColumnLayout));
        }

        return perimeterPlacements;
    }

    private static List<SeatVisualPlacement> selectBalancedReunionPlacements(List<SeatVisualPlacement> perimeterPlacements, int seatCount) {
        if (seatCount <= 0 || perimeterPlacements.isEmpty()) {
            return List.of();
        }
        if (seatCount >= perimeterPlacements.size()) {
            return perimeterPlacements;
        }

        List<SeatVisualPlacement> balancedPlacements = new ArrayList<>(seatCount);
        for (int index = 0; index < seatCount; index++) {
            int slotIndex = Math.min(
                perimeterPlacements.size() - 1,
                (int) Math.floor(((index + 0.5f) * perimeterPlacements.size()) / seatCount)
            );
            balancedPlacements.add(perimeterPlacements.get(slotIndex));
        }
        return balancedPlacements;
    }

    private static List<SeatLayoutInput> sortSeatInputs(List<SeatLayoutInput> seats) {
        return seats.stream()
            .sorted(Comparator.comparingInt(SeatLayoutInput::row)
                .thenComparingInt(SeatLayoutInput::column)
                .thenComparingInt(SeatLayoutInput::seatKey))
            .toList();
    }

    private static String normalizeDisposition(String typeDisposition) {
        if (typeDisposition == null) {
            return "";
        }
        return typeDisposition.trim().toLowerCase(Locale.ROOT);
    }

    public record SeatLayoutInput(int seatKey, int row, int column) {
        public SeatLayoutInput {
            seatKey = Math.max(1, seatKey);
            row = Math.max(1, row);
            column = Math.max(1, column);
        }
    }

    public record LayoutProjection(
        SeatDisplayProfile displayProfile,
        String dispositionLabel,
        String renderDisposition,
        SeatColumnLayout seatColumnLayout,
        Map<Integer, SeatVisualPlacement> placements,
        int totalRows
    ) {
        public LayoutProjection {
            displayProfile = displayProfile == null ? SeatDisplayProfile.CLASSROOM : displayProfile;
            dispositionLabel = dispositionLabel == null || dispositionLabel.isBlank() ? "cours" : dispositionLabel;
            renderDisposition = renderDisposition == null || renderDisposition.isBlank() ? "classe" : renderDisposition;
            seatColumnLayout = seatColumnLayout == null ? EMPTY_LAYOUT : seatColumnLayout;
            placements = placements == null ? Map.of() : Map.copyOf(placements);
            totalRows = Math.max(0, totalRows);
        }

        public SeatVisualPlacement placementFor(int seatKey) {
            return placements.get(seatKey);
        }
    }

    public record SeatColumnLayout(int logicalColumns, int visualColumns, int aisleColumn, int leftBlockColumns, int[] mapping) {
        public SeatColumnLayout {
            logicalColumns = Math.max(0, logicalColumns);
            visualColumns = Math.max(0, visualColumns);
            leftBlockColumns = Math.max(0, leftBlockColumns);
            mapping = mapping == null ? new int[0] : mapping.clone();
        }

        public int visualColumnForLogical(int logicalColumn) {
            return logicalColumn >= 0 && logicalColumn < mapping.length ? mapping[logicalColumn] : logicalColumn;
        }

        public boolean hasAisle() {
            return aisleColumn > 0;
        }

        public boolean isAisle(int visualColumn) {
            return hasAisle() && aisleColumn == visualColumn;
        }
    }

    public record SeatVisualPlacement(int logicalColumn, int visualColumn, int rowIndex, String zoneLabel) {
        public int displayRow() {
            return rowIndex + 1;
        }
    }

    public enum SeatDisplayProfile {
        CLASSROOM,
        CONFERENCE,
        REUNION,
        U_SHAPE
    }

    private record PlacementResult(Map<Integer, SeatVisualPlacement> placements, int totalRows) {
    }

    private record ReunionGridBounds(int rows, int columns) {
    }
}
