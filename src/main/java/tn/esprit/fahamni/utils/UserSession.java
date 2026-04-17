package tn.esprit.fahamni.utils;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;

public final class UserSession {

    private static User currentUser;
    private static String currentJwtToken;

    private UserSession() {
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static void start(User user, String jwtToken) {
        currentUser = user;
        currentJwtToken = jwtToken;
    }

    public static String getCurrentJwtToken() {
        return currentJwtToken;
    }

    public static boolean hasValidJwtToken() {
        return JwtService.isTokenValid(currentJwtToken);
    }

    public static JwtService.JwtClaims getCurrentJwtClaims() {
        return JwtService.extractClaims(currentJwtToken);
    }

    public static void clear() {
        currentUser = null;
        currentJwtToken = null;
    }

    public static boolean hasCurrentUser() {
        return currentUser != null;
    }

    public static String getDisplayName() {
        return currentUser == null ? "Etudiant Fahamni" : currentUser.getFullName();
    }

    public static String getInitials() {
        if (currentUser == null || currentUser.getFullName() == null || currentUser.getFullName().isBlank()) {
            return "FS";
        }

        String[] parts = currentUser.getFullName().trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        return initials.length() == 0 ? "FS" : initials.toString();
    }

    public static String getRoleLabel() {
        if (currentUser == null) {
            return "Espace Etudiant";
        }

        if (currentUser.getRole() == UserRole.ADMIN) {
            return "Espace Administrateur";
        }

        if (currentUser.getRole() == UserRole.TUTOR) {
            return "Espace Tuteur";
        }

        return "Espace Etudiant";
    }
}
