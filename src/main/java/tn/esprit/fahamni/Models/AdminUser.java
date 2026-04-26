package tn.esprit.fahamni.Models;

public class AdminUser {

    private int id;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private String reviewStatus;
    private String profileStatus;
    private String linkedProfileStatus;
    private String createdAt;
    private String reviewedAt;
    private String fraudRisk;
    private int fraudScore;
    private String fraudReason;
    private String fraudSignals;
    private String fraudRecommendation;
    private String fraudCheckedAt;
    private String reviewNote;
    private String adminInsight;

    public AdminUser(int id, String fullName, String email, String role, String status, String reviewStatus,
                     String profileStatus, String linkedProfileStatus, String createdAt, String reviewedAt,
                     String fraudRisk, int fraudScore, String fraudReason, String fraudSignals,
                     String fraudRecommendation, String fraudCheckedAt, String reviewNote, String adminInsight) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.profileStatus = profileStatus;
        this.linkedProfileStatus = linkedProfileStatus;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
        this.fraudRisk = fraudRisk;
        this.fraudScore = fraudScore;
        this.fraudReason = fraudReason;
        this.fraudSignals = fraudSignals;
        this.fraudRecommendation = fraudRecommendation;
        this.fraudCheckedAt = fraudCheckedAt;
        this.reviewNote = reviewNote;
        this.adminInsight = adminInsight;
    }

    public AdminUser(String fullName, String email, String role, String status) {
        this(
            0,
            fullName,
            email,
            role,
            status,
            "Approved",
            "Profile On",
            "Not needed",
            "Non disponible",
            "Non disponible",
            "LOW",
            12,
            "LOW risk (12/100). No significant risk signals found.",
            "No significant risk signals found.",
            "Safe to approve with the standard workflow.",
            "Non disponible",
            "",
            "No insights available."
        );
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getProfileStatus() {
        return profileStatus;
    }

    public void setProfileStatus(String profileStatus) {
        this.profileStatus = profileStatus;
    }

    public String getLinkedProfileStatus() {
        return linkedProfileStatus;
    }

    public void setLinkedProfileStatus(String linkedProfileStatus) {
        this.linkedProfileStatus = linkedProfileStatus;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(String reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getFraudRisk() {
        return fraudRisk;
    }

    public void setFraudRisk(String fraudRisk) {
        this.fraudRisk = fraudRisk;
    }

    public int getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(int fraudScore) {
        this.fraudScore = fraudScore;
    }

    public String getFraudReason() {
        return fraudReason;
    }

    public void setFraudReason(String fraudReason) {
        this.fraudReason = fraudReason;
    }

    public String getFraudSignals() {
        return fraudSignals;
    }

    public void setFraudSignals(String fraudSignals) {
        this.fraudSignals = fraudSignals;
    }

    public String getFraudRecommendation() {
        return fraudRecommendation;
    }

    public void setFraudRecommendation(String fraudRecommendation) {
        this.fraudRecommendation = fraudRecommendation;
    }

    public String getFraudCheckedAt() {
        return fraudCheckedAt;
    }

    public void setFraudCheckedAt(String fraudCheckedAt) {
        this.fraudCheckedAt = fraudCheckedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getAdminInsight() {
        return adminInsight;
    }

    public void setAdminInsight(String adminInsight) {
        this.adminInsight = adminInsight;
    }
}

