package tn.esprit.fahamni.Models;

public record AiRoomDesignRequest(
    String brief,
    String preferredName,
    String preferredBuilding,
    Integer preferredFloor,
    String preferredLocation,
    int preferredCapacity,
    String preferredType,
    String preferredDisposition,
    boolean accessibilityRequired,
    String equipmentHints,
    int variantIndex
) {
    public AiRoomDesignRequest {
        brief = normalizeText(brief);
        preferredName = normalizeText(preferredName);
        preferredBuilding = normalizeText(preferredBuilding);
        preferredFloor = normalizeFloor(preferredFloor);
        preferredLocation = normalizeText(preferredLocation);
        preferredCapacity = Math.max(1, preferredCapacity);
        preferredType = normalizeText(preferredType);
        preferredDisposition = normalizeText(preferredDisposition);
        equipmentHints = normalizeText(equipmentHints);
        variantIndex = Math.max(1, variantIndex);
    }

    public AiRoomDesignRequest withVariantIndex(int nextVariantIndex) {
        return new AiRoomDesignRequest(
            brief,
            preferredName,
            preferredBuilding,
            preferredFloor,
            preferredLocation,
            preferredCapacity,
            preferredType,
            preferredDisposition,
            accessibilityRequired,
            equipmentHints,
            nextVariantIndex
        );
    }

    public String combinedHints() {
        if (brief == null) {
            return equipmentHints == null ? "" : equipmentHints;
        }
        if (equipmentHints == null) {
            return brief;
        }
        return brief + "\n" + equipmentHints;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Integer normalizeFloor(Integer value) {
        return value == null || value < 0 ? null : value;
    }
}
