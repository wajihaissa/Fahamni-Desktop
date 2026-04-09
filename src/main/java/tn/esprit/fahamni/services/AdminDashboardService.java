package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminDashboardSummary;
import tn.esprit.fahamni.Models.AdminFeedItem;
import java.util.List;

public class AdminDashboardService {

    private final AdminUserService userService = new AdminUserService();
    private final AdminSessionService sessionService = new AdminSessionService();
    private final AdminReservationService reservationService = new AdminReservationService();
    private final AdminContentService contentService = new AdminContentService();

    public AdminDashboardSummary getSummary() {
        return new AdminDashboardSummary(
            userService.getUsers().size(),
            sessionService.getSessions().size(),
            reservationService.getReservations().size(),
            contentService.getArticles().size()
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
}

