package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;

public final class TemporaryUserContext {

    // Temporary static actors until the user/authentication module is connected.
    private static final int STUDENT_ID = 5;
    private static final int TUTOR_ID = 9;

    private static final String STUDENT_NAME = "Ameni Rahmeni";
    private static final String TUTOR_NAME = "Saida Mejri";

    private static UserRole currentRole = UserRole.ETUDIANT;
    private static String currentUserName = STUDENT_NAME;
    private static int currentActorId = STUDENT_ID;

    private TemporaryUserContext() {
    }

    public static void signIn(User user) {
        if (user == null) {
            resetToStudent();
            return;
        }

        if (user.getRole() == UserRole.TUTEUR) {
            currentRole = UserRole.TUTEUR;
            currentUserName = TUTOR_NAME;
            currentActorId = TUTOR_ID;
            return;
        }

        if (user.getRole() == UserRole.ADMIN) {
            currentRole = UserRole.ADMIN;
            currentUserName = user.getFullName();
            currentActorId = 0;
            return;
        }

        resetToStudent();
    }

    public static void resetToStudent() {
        currentRole = UserRole.ETUDIANT;
        currentUserName = STUDENT_NAME;
        currentActorId = STUDENT_ID;
    }

    public static boolean isCurrentStudent() {
        return currentRole == UserRole.ETUDIANT || currentRole == UserRole.USER;
    }

    public static boolean isCurrentTutor() {
        return currentRole == UserRole.TUTEUR;
    }

    public static UserRole getCurrentRole() {
        return currentRole;
    }

    public static int getCurrentActorId() {
        return currentActorId;
    }

    public static int getCurrentStudentId() {
        return STUDENT_ID;
    }

    public static int getCurrentTutorId() {
        return TUTOR_ID;
    }

    public static String getCurrentStudentName() {
        return STUDENT_NAME;
    }

    public static String getCurrentTutorName() {
        return TUTOR_NAME;
    }

    public static String getCurrentUserName() {
        return currentUserName;
    }

    public static String getCurrentUserInitials() {
        String[] parts = currentUserName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "FS";
        }

        String firstInitial = parts[0].substring(0, 1);
        String secondInitial = parts.length > 1 ? parts[1].substring(0, 1) : "";
        return (firstInitial + secondInitial).toUpperCase();
    }

    public static String getCurrentRoleLabel() {
        if (currentRole == UserRole.TUTEUR) {
            return "Espace Tuteur";
        }
        if (currentRole == UserRole.ADMIN) {
            return "Espace Administrateur";
        }
        return "Espace Etudiant";
    }

    public static String getCurrentStudentLabel() {
        return STUDENT_NAME + " (etudiant temporaire)";
    }

    public static String getCurrentTutorLabel() {
        return TUTOR_NAME + " (tuteur temporaire)";
    }
}
