package tn.esprit.fahamni.Models;

import java.util.List;

public record SessionRoomCustomizationConfig(
    String disposition,
    Integer capacity,
    String tableStyle,
    String chairStyle,
    Boolean accessibilityRequired,
    List<SeatLayoutSlot> seatLayout
) {
    public SessionRoomCustomizationConfig {
        disposition = normalizeText(disposition);
        capacity = normalizeCapacity(capacity);
        tableStyle = normalizeText(tableStyle);
        chairStyle = normalizeText(chairStyle);
        seatLayout = seatLayout == null ? List.of() : List.copyOf(seatLayout);
    }

    public boolean hasSeatLayout() {
        return !seatLayout.isEmpty();
    }

    public int seatLayoutSize() {
        return seatLayout.size();
    }

    public boolean hasAccessibilityOverride() {
        return accessibilityRequired != null;
    }

    public boolean accessibilityRequiredOrDefault(boolean fallbackValue) {
        return accessibilityRequired == null ? fallbackValue : accessibilityRequired;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Integer normalizeCapacity(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    public record SeatLayoutSlot(int seatKey, int row, int column) {
        public SeatLayoutSlot {
            seatKey = Math.max(1, seatKey);
            row = Math.max(1, row);
            column = Math.max(1, column);
        }
    }
}
