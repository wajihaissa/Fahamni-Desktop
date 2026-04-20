package tn.esprit.fahamni.room3d;

import java.util.Locale;

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

        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        if ("disponible".equals(normalizedStatus) || normalizedStatus.contains("libre")) {
            return AVAILABLE;
        }
        if (normalizedStatus.contains("reserv")) {
            return RESERVED;
        }
        if (normalizedStatus.contains("maintenance")) {
            return MAINTENANCE;
        }
        if (normalizedStatus.contains("indispon")) {
            return UNAVAILABLE;
        }
        return UNAVAILABLE;
    }
}
