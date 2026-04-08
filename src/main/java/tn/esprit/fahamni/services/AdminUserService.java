package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminUser;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

public class AdminUserService {

    private final ObservableList<AdminUser> users = FXCollections.observableArrayList(
        new AdminUser("Nour Ben Salem", "nour.bensalem@fahamni.tn", "Student", "Active"),
        new AdminUser("Youssef Ayari", "youssef.ayari@fahamni.tn", "Tutor", "Pending"),
        new AdminUser("Amira Trabelsi", "amira.trabelsi@fahamni.tn", "Student", "Active"),
        new AdminUser("Admin Principal", "admin@fahamni.tn", "Administrator", "Active")
    );

    public ObservableList<AdminUser> getUsers() {
        return users;
    }

    public List<String> getAvailableRoles() {
        return List.of("Student", "Tutor", "Administrator");
    }

    public List<String> getAvailableStatuses() {
        return List.of("Active", "Pending", "Suspended");
    }

    public OperationResult createUser(String fullName, String email, String role, String status) {
        if (isBlank(fullName) || isBlank(email)) {
            return OperationResult.failure("Veuillez renseigner le nom et l'email.");
        }

        users.add(new AdminUser(fullName.trim(), email.trim(), role, status));
        return OperationResult.success("Utilisateur ajoute dans la liste admin.");
    }

    public OperationResult updateUser(AdminUser user, String fullName, String email, String role, String status) {
        if (user == null) {
            return OperationResult.failure("Selectionnez un utilisateur a mettre a jour.");
        }

        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setRole(role);
        user.setStatus(status);
        return OperationResult.success("Utilisateur mis a jour.");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

