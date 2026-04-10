package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AdminArticlesService {

    private Connection cnx() {
        return MyDataBase.getInstance().getCnx();
    }

    /** Tous les articles (toutes statuts) */
    public List<Blog> getAllArticles() {
        return queryArticles(null);
    }

    /** Articles en attente d'approbation */
    public List<Blog> getPendingArticles() {
        return queryArticles("pending");
    }

    /** Articles approuvés */
    public List<Blog> getApprovedArticles() {
        return queryArticles("published");
    }

    /** Articles supprimés */
    public List<Blog> getDeletedArticles() {
        return queryArticles("deleted");
    }

    public int countByStatus(String status) {
        Connection c = cnx();
        if (c == null) return 0;
        String sql = status == null
                ? "SELECT COUNT(*) FROM blog"
                : "SELECT COUNT(*) FROM blog WHERE status = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (status != null) ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            System.err.println("countByStatus: " + e.getMessage());
        }
        return 0;
    }

    /** Approuver un article : status → published + notification + journal */
    public void approveArticle(int blogId) {
        updateStatus(blogId, "published");
        notifyPublisher(blogId,
            "Votre article \"%s\" a ete approuve et publie sur Fahamni !");
        logAction(blogId, "Approuve");
    }

    /** Supprimer logiquement un article : status → deleted + notification + journal */
    public void deleteArticle(int blogId) {
        updateStatus(blogId, "deleted");
        notifyPublisher(blogId,
            "Votre article \"%s\" a ete refuse par l'administrateur.");
        logAction(blogId, "Refuse");
    }

    private void logAction(int blogId, String action) {
        try {
            Connection c = cnx();
            if (c == null) return;
            String titre = "", auteur = "";
            String[] nameCols = {"fullName", "full_name", "name", "username"};
            for (String col : nameCols) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT b.titre, u." + col + " AS pub FROM blog b " +
                        "LEFT JOIN user u ON b.publisher_id = u.id WHERE b.id = ?")) {
                    ps.setInt(1, blogId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        titre  = rs.getString("titre");
                        auteur = rs.getString("pub");
                        if (auteur == null) auteur = "Anonyme";
                    }
                    break;
                } catch (Exception ignored) {}
            }
            String adminName = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();
            new ActivityLogService().log(adminName, action, titre, auteur);
        } catch (Exception e) {
            System.err.println("logAction: " + e.getMessage());
        }
    }

    /** Remettre un article en attente */
    public void setPending(int blogId) {
        updateStatus(blogId, "pending");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /**
     * Envoie une notification au tuteur auteur du blog.
     * Si publisher_id est NULL dans blog, tente de retrouver l'utilisateur
     * par son nom via la table user.
     */
    private void notifyPublisher(int blogId, String messageTemplate) {
        Connection c = cnx();
        if (c == null) return;
        try {
            // Étape 1 : lire titre + publisher_id depuis blog
            String titre = null;
            int publisherId = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT titre, publisher_id FROM blog WHERE id = ?")) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) { System.err.println("notifyPublisher: blog " + blogId + " introuvable"); return; }
                titre = rs.getString("titre");
                publisherId = rs.getInt("publisher_id"); // 0 si NULL
            }

            String message = String.format(messageTemplate, titre);

            // Étape 2 : si publisher_id connu, envoyer directement
            if (publisherId > 0) {
                new NotificationService().createForUser(publisherId, message, blogId);
                System.out.println("Notification envoyee (userId=" + publisherId + "): " + message);
                return;
            }

            // Étape 3 : publisher_id NULL → chercher par nom via user table (JOIN sur publisher_name)
            String[] nameCols = {"fullName", "full_name", "name", "username"};
            for (String col : nameCols) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT u.id FROM blog b JOIN user u ON u." + col + " = " +
                        "(SELECT publisher_name FROM (SELECT " +
                        "  COALESCE((SELECT u2." + col + " FROM user u2 WHERE u2.id = b2.publisher_id LIMIT 1), " +
                        "           b2.titre) AS publisher_name FROM blog b2 WHERE b2.id = ?) sub) " +
                        "WHERE b.id = ? LIMIT 1")) {
                    // Requête trop complexe, utiliser approche simple
                } catch (Exception ignored) {}
            }

            // Étape 3 simplifiée : chercher dans notification la notification admin liée à ce blog
            // Le message contient "soumis par NomDuTuteur"
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT message FROM notification WHERE blog_id = ? AND recipient_id = 0 ORDER BY id DESC LIMIT 1")) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String notifMsg = rs.getString("message");
                    // Format : "Nouvel article en attente : \"titre\" soumis par NomDuTuteur"
                    int idx = notifMsg.indexOf("soumis par ");
                    if (idx >= 0) {
                        String authorName = notifMsg.substring(idx + "soumis par ".length()).trim();
                        int foundId = findUserIdByName(c, authorName);
                        if (foundId > 0) {
                            new NotificationService().createForUser(foundId, message, blogId);
                            System.out.println("Notification envoyee via nom '" + authorName + "' (userId=" + foundId + "): " + message);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("notifyPublisher fallback: " + e.getMessage());
            }

            System.err.println("notifyPublisher: publisher introuvable pour blogId=" + blogId + " (publisher_id=" + publisherId + ")");
        } catch (Exception e) {
            System.err.println("notifyPublisher: " + e.getMessage());
        }
    }

    /** Cherche l'id d'un utilisateur par son nom complet */
    private int findUserIdByName(Connection c, String name) {
        String[] nameCols = {"fullName", "full_name", "name", "username"};
        for (String col : nameCols) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM user WHERE " + col + " = ? LIMIT 1")) {
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("id");
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private void updateStatus(int blogId, String status) {
        Connection c = cnx();
        if (c == null || blogId <= 0) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE blog SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, blogId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("updateStatus: " + e.getMessage());
        }
    }

    private List<Blog> queryArticles(String status) {
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<Blog> result = new ArrayList<>();
        String[] userCols = {"fullName", "full_name", "name", "username"};
        for (String col : userCols) {
            result.clear();
            String sql = status == null
                ? "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, " +
                  "b.publisher_id, b.created_at, b.published_at, u." + col + " AS publisher_name " +
                  "FROM blog b LEFT JOIN user u ON b.publisher_id = u.id " +
                  "ORDER BY b.created_at DESC"
                : "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, " +
                  "b.publisher_id, b.created_at, b.published_at, u." + col + " AS publisher_name " +
                  "FROM blog b LEFT JOIN user u ON b.publisher_id = u.id " +
                  "WHERE b.status = ? ORDER BY b.created_at DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (status != null) ps.setString(1, status);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapBlog(rs));
                return result;
            } catch (Exception e) {
                System.err.println("queryArticles col=" + col + ": " + e.getMessage());
            }
        }
        return result;
    }

    private Blog mapBlog(ResultSet rs) throws SQLException {
        LocalDateTime createdAt = rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now();
        LocalDateTime publishedAt = null;
        try { publishedAt = rs.getTimestamp("published_at") != null
                ? rs.getTimestamp("published_at").toLocalDateTime() : null; } catch (Exception ignored) {}
        String publisher = rs.getString("publisher_name");
        if (publisher == null || publisher.isBlank()) publisher = "Anonyme";
        String category = null;
        try { category = rs.getString("category"); } catch (Exception ignored) {}
        String status = null;
        try { status = rs.getString("status"); } catch (Exception ignored) {}

        Blog blog = new Blog(rs.getInt("id"), rs.getString("titre"), rs.getString("content"),
                category, createdAt, publisher, publishedAt);
        try { blog.setPublisherId(rs.getInt("publisher_id")); } catch (Exception ignored) {}
        blog.setStatus(status != null ? status : "pending");
        return blog;
    }
}
