package tn.esprit.fahamni.Models;

public class AdminUser {

    private int id;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private String createdAt;
    private String fraudRisk;
    private String fraudReason;

    public AdminUser(int id, String fullName, String email, String role, String status, String createdAt, String fraudRisk, String fraudReason) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.fraudRisk = fraudRisk;
        this.fraudReason = fraudReason;
    }

    public AdminUser(String fullName, String email, String role, String status) {
        this(0, fullName, email, role, status, "Non disponible", "LOW", "No review notes yet.");
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFraudRisk() {
        return fraudRisk;
    }

    public void setFraudRisk(String fraudRisk) {
        this.fraudRisk = fraudRisk;
    }

    public String getFraudReason() {
        return fraudReason;
    }

    public void setFraudReason(String fraudReason) {
        this.fraudReason = fraudReason;
    }
}

