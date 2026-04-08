package tn.esprit.fahamni.Models;

public class AdminUser {

    private String fullName;
    private String email;
    private String role;
    private String status;

    public AdminUser(String fullName, String email, String role, String status) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = status;
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
}

