package com.fahimni.models;

public class User {
    private int id;
    private String email;
    private String password;
    private String full_name;
    private boolean status;
    private String created_at;

    // Constructors
    public User() {}

    public User(String email, String password, String fullName, boolean status) {
        this.email = email;
        this.password = password;
        this.full_name = fullName;
        this.status = status;
    }

    public User(int id, String email, String password, String full_name, boolean status, String created_at) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.full_name = full_name;
        this.status = status;
        this.created_at = created_at;
    }

    // Getters
    public int getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullName() { return full_name; }
    public boolean isStatus() { return status; }
    public String getCreatedAt() { return created_at; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setFullName(String fullName) { this.full_name = fullName; }
    public void setStatus(boolean status) { this.status = status; }
    public void setCreatedAt(String createdAt) { this.created_at = createdAt; }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", fullName='" + full_name + '\'' +
                ", status=" + status +
                ", createdAt='" + created_at + '\'' +
                '}' + '\n';
    }
}