package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.utils.OperationResult;
import java.util.List;

public class AuthService {

    private final List<User> mockUsers = List.of(
        new User("Administrateur Fahamni", "admin@fahamni.tn", "admin123", UserRole.ADMIN),
        new User("Utilisateur Fahamni", "user@fahamni.tn", "user123", UserRole.USER)
    );

    public User authenticate(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            return null;
        }

        for (User user : mockUsers) {
            if (user.getEmail().equalsIgnoreCase(email.trim()) && user.getPassword().equals(password)) {
                return user;
            }
        }

        return null;
    }

    public OperationResult register(String fullName, String email, String password, String confirmPassword) {
        if (isBlank(fullName) || isBlank(email) || isBlank(password) || isBlank(confirmPassword)) {
            return OperationResult.failure("Veuillez remplir tous les champs.");
        }

        if (!email.contains("@")) {
            return OperationResult.failure("Veuillez saisir une adresse email valide.");
        }

        if (password.length() < 4) {
            return OperationResult.failure("Le mot de passe doit contenir au moins 4 caracteres.");
        }

        if (!password.equals(confirmPassword)) {
            return OperationResult.failure("Les mots de passe ne correspondent pas.");
        }

        return OperationResult.success("Compte cree avec succes. Vous pouvez maintenant vous connecter.");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

