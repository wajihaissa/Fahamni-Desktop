package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public record SessionRoomCustomizationRequest(
    int idDemande,
    int seanceId,
    int baseSalleId,
    int tuteurId,
    String status,
    SessionRoomCustomizationConfig requestedConfig,
    SessionRoomCustomizationConfig approvedConfig,
    String commentTuteur,
    String commentAdmin,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt
) {
    public SessionRoomCustomizationRequest {
        idDemande = Math.max(0, idDemande);
        seanceId = Math.max(0, seanceId);
        baseSalleId = Math.max(0, baseSalleId);
        tuteurId = Math.max(0, tuteurId);
        status = normalizeStatus(status);
        commentTuteur = normalizeText(commentTuteur);
        commentAdmin = normalizeText(commentAdmin);
    }

    public boolean isApproved() {
        return "approved".equals(status);
    }

    public boolean isRejected() {
        return "rejected".equals(status);
    }

    public boolean isCancelled() {
        return "cancelled".equals(status);
    }

    public boolean isFinalDecision() {
        return isApproved() || isRejected();
    }

    public boolean isReadOnlyForAdmin() {
        return isFinalDecision() || isCancelled();
    }

    public boolean canBeReviewedByAdmin() {
        return !isReadOnlyForAdmin();
    }

    public boolean isPendingReview() {
        return "pending".equals(status) || "in_review".equals(status);
    }

    public SessionRoomCustomizationConfig effectiveApprovedConfig() {
        if (!isApproved()) {
            return null;
        }
        return approvedConfig != null ? approvedConfig : requestedConfig;
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return "pending";
        }
        return value.trim().toLowerCase();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
