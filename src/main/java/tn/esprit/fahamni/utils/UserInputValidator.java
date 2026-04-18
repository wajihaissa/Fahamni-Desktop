package tn.esprit.fahamni.utils;

import java.util.Locale;

public final class UserInputValidator {

    private UserInputValidator() {
    }

    public static String validateFullName(String fullName) {
        String normalized = normalizeFullName(fullName);
        if (normalized == null) {
            return "Le nom complet est obligatoire.";
        }
        if (normalized.length() < 3) {
            return "Le nom complet est trop court.";
        }
        return null;
    }

    public static String validateEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return "L'adresse email est obligatoire.";
        }

        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex != normalized.lastIndexOf('@') || atIndex >= normalized.length() - 1) {
            return "Veuillez saisir une adresse email valide.";
        }

        if (!normalized.substring(atIndex + 1).contains(".")) {
            return "Veuillez saisir une adresse email valide.";
        }

        return null;
    }

    public static String validatePassword(String password, String confirmPassword, boolean required) {
        boolean passwordBlank = isBlank(password);
        boolean confirmBlank = isBlank(confirmPassword);

        if (!required && passwordBlank && confirmBlank) {
            return null;
        }

        if (passwordBlank || confirmBlank) {
            return "Veuillez remplir les deux champs mot de passe.";
        }

        if (password.trim().length() < 4) {
            return "Le mot de passe doit contenir au moins 4 caracteres.";
        }

        if (!password.equals(confirmPassword)) {
            return "Les mots de passe ne correspondent pas.";
        }

        return null;
    }

    public static String normalizeFullName(String fullName) {
        return trimToNull(fullName);
    }

    public static String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
