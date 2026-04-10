package tn.esprit.fahamni.Models;

public class User {

    private final int id;
    private final String fullName;
    private final String email;
    private final String password;
    private final UserRole role;

    public User(int id, String fullName, String email, String password, UserRole role) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /** Constructeur sans id (compatibilité) */
    public User(String fullName, String email, String password, UserRole role) {
        this(0, fullName, email, password, role);
    }

    public int getId() { return id; }

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

