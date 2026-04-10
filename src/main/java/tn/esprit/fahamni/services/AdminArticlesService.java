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

    /** Approuver un article : status → published + notification au tuteur */
    public void approveArticle(int blogId) {
        updateStatus(blogId, "published");
        // Récupérer titre et publisher_id pour notifier le tuteur
        try {
            Connection c = cnx();
            if (c == null) return;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT titre, publisher_id FROM blog WHERE id = ?")) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String titre = rs.getString("titre");
                    int publisherId = rs.getInt("publisher_id");
                    new NotificationService().createForUser(publisherId,
                        "Votre article \"" + titre + "\" a ete approuve et publie sur Fahamni !", blogId);
                    System.out.println("Notification envoyee au tuteur (userId=" + publisherId + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("approveArticle: notif tuteur echouee: " + e.getMessage());
        }
    }

    /** Supprimer logiquement un article : status → deleted */
    public void deleteArticle(int blogId) {
        updateStatus(blogId, "deleted");
    }

    /** Remettre un article en attente */
    public void setPending(int blogId) {
        updateStatus(blogId, "pending");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

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
