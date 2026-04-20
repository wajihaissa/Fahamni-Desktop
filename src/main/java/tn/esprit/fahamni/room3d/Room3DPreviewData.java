package tn.esprit.fahamni.room3d;

import java.util.List;

public record Room3DPreviewData(
    String roomName,
    String building,
    String location,
    String roomType,
    String disposition,
    String roomStatus,
    int capacity,
    boolean accessible,
    List<SeatPreview> seats
) {
    public Room3DPreviewData {
        roomName = normalizeText(roomName, "Salle");
        building = normalizeText(building, "Batiment non renseigne");
        location = normalizeText(location, "Localisation non renseignee");
        roomType = normalizeText(roomType, "Type non renseigne");
        disposition = normalizeText(disposition, "classe");
        roomStatus = normalizeText(roomStatus, "inconnu");
        capacity = Math.max(0, capacity);
        seats = seats == null ? List.of() : List.copyOf(seats);
    }

    public int seatCount() {
        return seats.size();
    }

    public int maxRow() {
        return seats.stream()
            .mapToInt(SeatPreview::row)
            .max()
            .orElse(1);
    }

    public int maxColumn() {
        return seats.stream()
            .mapToInt(SeatPreview::column)
            .max()
            .orElse(1);
    }

    public String summaryLine() {
        return building
            + " | "
            + location
            + " | "
            + roomType
            + " | "
            + seatCount()
            + "/"
            + capacity
            + " places";
    }

    private static String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public record SeatPreview(int number, int row, int column, RoomSeatVisualState state) {
        public SeatPreview {
            number = Math.max(1, number);
            row = Math.max(1, row);
            column = Math.max(1, column);
            state = state == null ? RoomSeatVisualState.AVAILABLE : state;
        }

        public String label() {
            return "Place " + number + " (rang " + row + ", colonne " + column + ")";
        }
    }
}
