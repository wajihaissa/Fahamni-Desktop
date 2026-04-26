package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AdminArticlesService {

    private Connection cnx() {
        return MyDataBase.getInstance().getCnx();
    }

    private boolean hasBlogCategoryColumn() {
        Connection c = cnx();
        if (c == null) {
            return false;
        }
        try (ResultSet columns = c.getMetaData().getColumns(c.getCatalog(), null, "blog", "category")) {
            return columns.next();
        } catch (Exception e) {
            return false;
        }
    }

    public List<Blog> getAllArticles() {
        return queryArticles(null);
    }

    public List<Blog> getPendingArticles() {
        return queryArticles("pending");
    }

    public List<Blog> getApprovedArticles() {
        return queryArticles("published");
    }

    public List<Blog> getDeletedArticles() {
        return queryArticles("deleted");
    }

    public int countByStatus(String status) {
        Connection c = cnx();
        if (c == null) {
            return 0;
        }

        String sql = status == null
            ? "SELECT COUNT(*) FROM blog"
            : "SELECT COUNT(*) FROM blog WHERE status = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (status != null) {
                ps.setString(1, status);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("countByStatus: " + e.getMessage());
        }
        return 0;
    }

    public void approveArticle(int blogId) {
        updateStatus(blogId, "published");
        notifyPublisher(blogId, "Votre article \"%s\" a ete approuve et publie sur Fahamni !");
        logAction(blogId, "Approuve");
    }

    public void deleteArticle(int blogId) {
        updateStatus(blogId, "deleted");
        notifyPublisher(blogId, "Votre article \"%s\" a ete refuse par l'administrateur.");
        logAction(blogId, "Refuse");
    }

    public void setPending(int blogId) {
        updateStatus(blogId, "pending");
    }

    private void logAction(int blogId, String action) {
        try {
            Connection c = cnx();
            if (c == null) {
                return;
            }

            String title = "";
            String author = "";
            String[] nameColumns = {"full_name", "fullName", "name", "username"};
            for (String column : nameColumns) {
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT b.titre, u." + column + " AS pub FROM blog b " +
                        "LEFT JOIN user u ON b.publisher_id = u.id WHERE b.id = ?")) {
                    ps.setInt(1, blogId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        title = rs.getString("titre");
                        author = rs.getString("pub");
                        if (author == null) {
                            author = "Anonyme";
                        }
                    }
                    break;
                } catch (Exception ignored) {
                }
            }

            String adminName = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();
            new ActivityLogService().log(adminName, action, title, author);
        } catch (Exception e) {
            System.err.println("logAction: " + e.getMessage());
        }
    }

    private void notifyPublisher(int blogId, String messageTemplate) {
        Connection c = cnx();
        if (c == null) {
            return;
        }

        try {
            String title;
            int publisherId;
            try (PreparedStatement ps = c.prepareStatement("SELECT titre, publisher_id FROM blog WHERE id = ?")) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    System.err.println("notifyPublisher: blog " + blogId + " introuvable");
                    return;
                }
                title = rs.getString("titre");
                publisherId = rs.getInt("publisher_id");
            }

            String message = String.format(messageTemplate, title);
            if (publisherId > 0) {
                new NotificationService().createForUser(publisherId, message, blogId);
                return;
            }

            try (PreparedStatement ps = c.prepareStatement(
                "SELECT message FROM notification WHERE blog_id = ? AND recipient_id = 0 ORDER BY id DESC LIMIT 1")) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String notificationMessage = rs.getString("message");
                    int markerIndex = notificationMessage.indexOf("soumis par ");
                    if (markerIndex >= 0) {
                        String authorName = notificationMessage.substring(markerIndex + "soumis par ".length()).trim();
                        int foundId = findUserIdByName(c, authorName);
                        if (foundId > 0) {
                            new NotificationService().createForUser(foundId, message, blogId);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("notifyPublisher fallback: " + e.getMessage());
            }

            System.err.println("notifyPublisher: publisher introuvable pour blogId=" + blogId);
        } catch (Exception e) {
            System.err.println("notifyPublisher: " + e.getMessage());
        }
    }

    private int findUserIdByName(Connection c, String name) {
        String[] nameColumns = {"full_name", "fullName", "name", "username"};
        for (String column : nameColumns) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM user WHERE " + column + " = ? LIMIT 1")) {
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private void updateStatus(int blogId, String status) {
        Connection c = cnx();
        if (c == null || blogId <= 0) {
            return;
        }

        try (PreparedStatement ps = c.prepareStatement("UPDATE blog SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, blogId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("updateStatus: " + e.getMessage());
        }
    }

    private List<Blog> queryArticles(String status) {
        Connection c = cnx();
        if (c == null) {
            return new ArrayList<>();
        }

        List<Blog> result = new ArrayList<>();
        String[] userColumns = {"full_name", "fullName", "name", "username"};
        String categorySelect = hasBlogCategoryColumn() ? "b.category" : "NULL AS category";
        for (String column : userColumns) {
            result.clear();
            String sql = status == null
                ? "SELECT b.id, b.titre, b.content, b.images, " + categorySelect + ", b.status, " +
                    "b.publisher_id, b.created_at, b.published_at, u." + column + " AS publisher_name " +
                    "FROM blog b LEFT JOIN user u ON b.publisher_id = u.id " +
                    "ORDER BY b.created_at DESC"
                : "SELECT b.id, b.titre, b.content, b.images, " + categorySelect + ", b.status, " +
                    "b.publisher_id, b.created_at, b.published_at, u." + column + " AS publisher_name " +
                    "FROM blog b LEFT JOIN user u ON b.publisher_id = u.id " +
                    "WHERE b.status = ? ORDER BY b.created_at DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (status != null) {
                    ps.setString(1, status);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(mapBlog(rs));
                }
                return result;
            } catch (Exception e) {
                System.err.println("queryArticles col=" + column + ": " + e.getMessage());
            }
        }
        return result;
    }

    private Blog mapBlog(ResultSet rs) throws java.sql.SQLException {
        LocalDateTime createdAt = rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now();
        LocalDateTime publishedAt = null;
        try {
            publishedAt = rs.getTimestamp("published_at") != null
                ? rs.getTimestamp("published_at").toLocalDateTime() : null;
        } catch (Exception ignored) {
        }
        String publisher = rs.getString("publisher_name");
        if (publisher == null || publisher.isBlank()) {
            publisher = "Anonyme";
        }
        String category = null;
        try {
            category = rs.getString("category");
        } catch (Exception ignored) {
        }
        String status = null;
        try {
            status = rs.getString("status");
        } catch (Exception ignored) {
        }

        Blog blog = new Blog(
            rs.getInt("id"),
            rs.getString("titre"),
            rs.getString("content"),
            category,
            createdAt,
            publisher,
            publishedAt
        );
        try {
            blog.setPublisherId(rs.getInt("publisher_id"));
        } catch (Exception ignored) {
        }
        blog.setStatus(status != null ? status : "pending");
        return blog;
    }
}
