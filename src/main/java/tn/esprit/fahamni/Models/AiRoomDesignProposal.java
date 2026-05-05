package tn.esprit.fahamni.Models;

import java.util.LinkedHashMap;
import java.util.Map;

public record AiRoomDesignProposal(
    Salle salle,
    Map<Integer, Integer> suggestedFixedEquipements,
    String previewHeadline,
    String previewSummary,
    String previewLegend,
    String adminSummary,
    String sourceLabel,
    String fixedEquipmentSummary,
    int variantIndex
) {
    public AiRoomDesignProposal {
        if (salle == null) {
            throw new IllegalArgumentException("La salle proposee est obligatoire.");
        }

        suggestedFixedEquipements = suggestedFixedEquipements == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(suggestedFixedEquipements));
        previewHeadline = normalizeText(previewHeadline);
        previewSummary = normalizeText(previewSummary);
        previewLegend = normalizeText(previewLegend);
        adminSummary = normalizeText(adminSummary);
        sourceLabel = normalizeText(sourceLabel);
        fixedEquipmentSummary = normalizeText(fixedEquipmentSummary);
        variantIndex = Math.max(1, variantIndex);
    }

    public boolean hasSuggestedFixedEquipements() {
        return !suggestedFixedEquipements.isEmpty();
    }

    public String fixedEquipmentSummaryOrDefault() {
        return fixedEquipmentSummary == null ? "aucun" : fixedEquipmentSummary;
    }

    public String adminSummaryOrDefault() {
        return adminSummary == null ? "Proposition AI prete a etre appliquee." : adminSummary;
    }

    public String sourceLabelOrDefault() {
        return sourceLabel == null ? "AI" : sourceLabel;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
