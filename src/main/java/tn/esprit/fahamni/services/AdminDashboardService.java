package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminDashboardSummary;
import tn.esprit.fahamni.Models.AdminFeedItem;
import java.util.List;

public class AdminDashboardService {

    public AdminDashboardSummary getSummary() {
        return new AdminDashboardSummary(
            safeUsersCount(),
            safeSessionsCount(),
            safeReservationsCount(),
            safeContentCount()
        );
    }

    public List<AdminFeedItem> getRecentActivities() {
        return List.of(
            new AdminFeedItem("Nouvel utilisateur inscrit", "Nour Ben Salem a cree un compte aujourd'hui."),
            new AdminFeedItem("Reservation en attente", "1 reservation requiert une validation admin."),
            new AdminFeedItem("Article en brouillon", "Un contenu est pret pour publication.")
        );
    }

    public List<String> getAlerts() {
        return List.of(
            "2 tuteurs attendent une validation finale.",
            "Verifier les seances en brouillon avant publication.",
            "Mettre a jour le contenu pedagogique de la semaine."
        );
    }

    private int safeUsersCount() {
        try {
            return new AdminUserService().getUsers().size();
        } catch (Exception exception) {
            System.out.println("Dashboard users count error: " + exception.getMessage());
            return 0;
        }
    }

    private int safeSessionsCount() {
        try {
            return new AdminSessionService().getSessions().size();
        } catch (Exception exception) {
            System.out.println("Dashboard sessions count error: " + exception.getMessage());
            return 0;
        }
    }

    private int safeReservationsCount() {
        try {
            return new AdminReservationService().getReservations().size();
        } catch (Exception exception) {
            System.out.println("Dashboard reservations count error: " + exception.getMessage());
            return 0;
        }
    }

    private int safeContentCount() {
        try {
            return new AdminContentService().getArticles().size();
        } catch (Exception exception) {
            System.out.println("Dashboard content count error: " + exception.getMessage());
            return 0;
        }
    }
}

