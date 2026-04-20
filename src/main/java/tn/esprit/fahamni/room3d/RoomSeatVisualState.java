package tn.esprit.fahamni.room3d;

public enum RoomSeatVisualState {
    AVAILABLE("Disponible"),
    RESERVED("Reservee"),
    MAINTENANCE("En maintenance"),
    UNAVAILABLE("Indisponible");

    private final String displayLabel;

    RoomSeatVisualState(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public static RoomSeatVisualState fromPlaceStatus(String status) {
        if (status == null || status.isBlank()) {
            return UNAVAILABLE;
        }

        String normalizedStatus = status.trim().toLowerCase();
        if ("disponible".equals(normalizedStatus)) {
            return AVAILABLE;
        }
        if (normalizedStatus.contains("maintenance")) {
            return MAINTENANCE;
        }
        return UNAVAILABLE;
    }
}
