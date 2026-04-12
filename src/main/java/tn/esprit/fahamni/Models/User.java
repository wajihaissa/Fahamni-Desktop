package tn.esprit.fahamni.Models;

public class User {

    private final Integer id;
    private final String fullName;
    private final String email;
    private final String password;
    private final UserRole role;

    public User(String fullName, String email, String password, UserRole role) {
        this((Integer) null, fullName, email, password, role);
    }

    public User(int id, String fullName, String email, String password, UserRole role) {
        this(Integer.valueOf(id), fullName, email, password, role);
    }

    public User(Long id, String fullName, String email, String password, UserRole role) {
        this(id == null ? null : id.intValue(), fullName, email, password, role);
    }

    public User(Integer id, String fullName, String email, String password, UserRole role) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public Integer getId() {
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
