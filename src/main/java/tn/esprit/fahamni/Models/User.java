package tn.esprit.fahamni.Models;

public class User {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String password;
    private final UserRole role;

    public User(String fullName, String email, String password, UserRole role) {
        this(null, fullName, email, password, role);
    }

    public User(Long id, String fullName, String email, String password, UserRole role) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }
}

