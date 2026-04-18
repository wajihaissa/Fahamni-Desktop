package tn.esprit.fahamni.utils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class UserInputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile("^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' -]{3,79}$");
    private static final Pattern PASSWORD_LETTER_PATTERN = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern PASSWORD_DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Set<String> ALLOWED_FRONT_ROLES = Set.of("Etudiant", "Tuteur");
    private static final Set<String> ALLOWED_BACKOFFICE_ROLES = Set.of("Student", "Tutor", "Administrator");
    private static final Set<String> ALLOWED_STATUSES = Set.of("Active", "Suspended");

    private UserInputValidator() {
    }

    public static String validateFullName(String fullName) {
        String normalized = normalizeFullName(fullName);
        if (normalized == null) {
            return "Le nom complet est obligatoire.";
        }
        if (!FULL_NAME_PATTERN.matcher(normalized).matches()) {
            return "Le nom complet doit contenir entre 4 et 80 caracteres valides.";
        }
        if (normalized.split(" ").length < 2) {
            return "Veuillez saisir au moins un prenom et un nom.";
        }
        return null;
    }

    public static String validateEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return "L'adresse email est obligatoire.";
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return "Veuillez saisir une adresse email valide.";
        }
        return null;
    }

    public static String validatePassword(String password, boolean required) {
        if (!required && isBlank(password)) {
            return null;
        }
        if (isBlank(password)) {
            return "Le mot de passe est obligatoire.";
        }
        if (password.length() < 6) {
            return "Le mot de passe doit contenir au moins 6 caracteres.";
        }
        if (!PASSWORD_LETTER_PATTERN.matcher(password).matches() || !PASSWORD_DIGIT_PATTERN.matcher(password).matches()) {
            return "Le mot de passe doit contenir au moins une lettre et un chiffre.";
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

        String passwordError = validatePassword(password, true);
        if (passwordError != null) {
            return passwordError;
        }

        if (!password.equals(confirmPassword)) {
            return "Les mots de passe ne correspondent pas.";
        }
        return null;
    }

    public static String validateFrontRole(String role) {
        if (isBlank(role) || !ALLOWED_FRONT_ROLES.contains(role.trim())) {
            return "Veuillez choisir un role valide.";
        }
        return null;
    }

    public static String validateBackofficeRole(String role) {
        if (isBlank(role) || !ALLOWED_BACKOFFICE_ROLES.contains(role.trim())) {
            return "Veuillez choisir un role valide.";
        }
        return null;
    }

    public static String validateStatus(String status) {
        if (isBlank(status) || !ALLOWED_STATUSES.contains(status.trim())) {
            return "Veuillez choisir un statut valide.";
        }
        return null;
    }

    public static String normalizeFullName(String fullName) {
        String normalized = trimToNull(fullName);
        return normalized == null ? null : normalized.replaceAll("\\s+", " ");
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
