package tn.esprit.fahamni.utils;

import tn.esprit.fahamni.Models.User;

public class SessionManager {

    private static User currentUser;

    private SessionManager() {}

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static String getCurrentUserName() {
        if (currentUser != null && currentUser.getFullName() != null) {
            return currentUser.getFullName();
        }
        return "Utilisateur";
    }

    public static void clear() {
        currentUser = null;
    }
}
