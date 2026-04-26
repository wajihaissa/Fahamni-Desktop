package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.CommentAdminItem;
import tn.esprit.fahamni.Models.Interaction;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.BlogSchemaSupport;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.SessionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// NotificationService est instancié à la demande pour éviter la dépendance circulaire

public class BlogService implements IServices<Blog> {

    /** Toujours obtenir la connexion dynamiquement (gère reconnexion) */
    private Connection cnx() {
        return MyDataBase.getInstance().getCnx();
    }

    // Mapping catégorie formulaire → BD
    public static String toCategoryDB(String formCat) {
        if (formCat == null) return "study-tips";
        switch (formCat.toLowerCase()) {
            case "math":          return "mathematics";
            case "physique":      return "science";
            case "science":       return "science";
            case "informatique":  return "computer-science";
            case "langue":        return "study-tips";
            default:              return "study-tips";
        }
    }

    // Mapping BD → catégorie interne (pour affichage)
    private static String fromCategoryDB(String dbCat) {
        if (dbCat == null) return "autre";
        switch (dbCat.toLowerCase()) {
            case "mathematics":      return "math";
            case "physics":
            case "physique":         return "physique";
            case "science":          return "science";
            case "computer-science": return "informatique";
            case "language":
            case "langue":           return "langue";
            default:                 return "autre";
        }
    }

    public BlogService() {
        BlogSchemaSupport.ensureSchema();
        ensureViewsColumn();
        ensureSharedFromColumn();
        ensureFavoriteColumn();
    }

    // ===================== BLOG =====================

    public List<Blog> getAllBlogs() {
        syncAllBlogCounts(); // synchronise likes_count/comments_count pour tous les articles
        List<Blog> blogs = getAllBlogsFromDB();
        if (blogs.isEmpty() && isBlogTableEmpty()) {
            ensureSampleBlogsPersisted();
            blogs = getAllBlogsFromDB();
        }
        if (blogs.isEmpty()) blogs.addAll(getSampleBlogs());
        return blogs;
    }

    /** Met à jour likes_count et comments_count pour tous les articles (JOIN, compatible toutes versions MySQL) */
    private void syncAllBlogCounts() {
        Connection c = cnx();
        if (c == null) return;
        // Likes via LEFT JOIN
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                "UPDATE blog " +
                "LEFT JOIN (SELECT blog_id, COUNT(*) AS cnt FROM interaction " +
                "           WHERE reaction IS NOT NULL AND reaction <> 0 GROUP BY blog_id) t " +
                "ON blog.id = t.blog_id " +
                "SET blog.likes_count = COALESCE(t.cnt, 0)");
        } catch (Exception e) {
            System.err.println("syncAllBlogCounts (likes): " + e.getMessage());
        }
        // Commentaires via LEFT JOIN
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                "UPDATE blog " +
                "LEFT JOIN (SELECT blog_id, COUNT(*) AS cnt FROM interaction " +
                "           WHERE comment IS NOT NULL AND comment <> '' GROUP BY blog_id) t " +
                "ON blog.id = t.blog_id " +
                "SET blog.comments_count = COALESCE(t.cnt, 0)");
        } catch (Exception e) {
            System.err.println("syncAllBlogCounts (comments): " + e.getMessage());
        }
    }

    public List<Blog> getAllBlogsFromDB() {
        Connection c = cnx();
        if (c == null) {
            System.err.println("getAllBlogsFromDB: connexion nulle.");
            return new ArrayList<>();
        }
        List<Blog> blogs = new ArrayList<>();
        String[] userNameCols = {"full_name", "fullName", "name", "username"};
        for (String col : userNameCols) {
            blogs.clear();
            String sql =
                "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, b.publisher_id, " +
                "b.created_at, b.published_at, IFNULL(b.views,0) AS views, " +
                "IFNULL(b.shared_from_id,0) AS shared_from_id, " +
                "u." + col + " AS publisher_name " +
                "FROM blog b " +
                "LEFT JOIN user u ON b.publisher_id = u.id " +
                "WHERE (b.status = 'published' OR b.status IS NULL) " +
                "ORDER BY b.created_at DESC";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) blogs.add(mapBlog(rs));
                return blogs;
            } catch (Exception e) {
                System.err.println("getAllBlogs avec '" + col + "': " + e.getMessage());
            }
        }
        // Fallback sans JOIN
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT b.id, b.titre, b.content, b.images, b.category, b.created_at, b.published_at, " +
                "CAST(IFNULL(b.publisher_id,0) AS CHAR) AS publisher_name FROM blog b ORDER BY b.created_at DESC")) {
            while (rs.next()) blogs.add(mapBlog(rs));
        } catch (Exception e) {
            System.err.println("Erreur getAllBlogs fallback: " + e.getMessage());
            e.printStackTrace();
        }
        return blogs;
    }

    /**
     * Crée un article (status=pending) et envoie une notification à l'admin.
     * @return l'ID du blog créé (>0) si succès, -1 si échec
     */
    public int addBlog(Blog blog) {
        int publisherId = getCurrentUserId();
        String publisherName = SessionManager.getCurrentUserName();
        String cat = toCategoryDB(blog.getImage());

        Connection c = cnx();
        if (c == null) return -1;
        if (!canCurrentUserCreateBlog() || publisherId <= 0 || !userExists(publisherId)) {
            System.err.println("addBlog: seul un tuteur connecte peut creer un article.");
            return -1;
        }

        int blogId = -1;
        // Tentative 1 : avec publisher_id, likes_count, comments_count
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO blog (titre, content, images, status, category, created_at, published_at, publisher_id, likes_count, comments_count) " +
                "VALUES (?, ?, NULL, 'pending', ?, NOW(), NOW(), ?, 0, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, blog.getTitre());
            ps.setString(2, blog.getContent());
            ps.setString(3, cat);
            ps.setInt(4, publisherId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) blogId = keys.getInt(1); }
        } catch (Exception e1) {
            System.err.println("addBlog [1]: " + e1.getMessage());

            // Tentative 2 : avec publisher_id, sans likes/comments
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO blog (titre, content, images, status, category, created_at, published_at, publisher_id) " +
                    "VALUES (?, ?, NULL, 'pending', ?, NOW(), NOW(), ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, blog.getTitre());
                ps.setString(2, blog.getContent());
                ps.setString(3, cat);
                ps.setInt(4, publisherId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) blogId = keys.getInt(1); }
            } catch (Exception e2) {
                System.err.println("addBlog [2]: " + e2.getMessage());
            }
        }

        if (blogId > 0) {
            // Notification admin
            try {
                new NotificationService().createForAdmin(
                    "Nouvel article en attente : \"" + blog.getTitre() + "\" soumis par " + publisherName, blogId);
            } catch (Exception ne) {
                System.err.println("addBlog: notif admin echouee: " + ne.getMessage());
            }
        }
        return blogId;
    }

    public void updateBlog(Blog blog) {
        Connection c = cnx();
        if (c == null || blog.getId() <= 0) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE blog SET titre = ?, content = ?, category = ? WHERE id = ?")) {
            ps.setString(1, blog.getTitre());
            ps.setString(2, blog.getContent());
            ps.setString(3, toCategoryDB(blog.getImage()));
            ps.setInt(4, blog.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Erreur updateBlog: " + e.getMessage());
        }
    }

    public void deleteBlog(int id) {
        Connection c = cnx();
        if (c == null || id <= 0) return;
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM interaction WHERE blog_id = ?")) {
            ps.setInt(1, id); ps.executeUpdate();
        } catch (Exception ignored) {}
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM blog WHERE id = ?")) {
            ps.setInt(1, id); ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Erreur deleteBlog: " + e.getMessage());
        }
    }

    public List<Blog> searchBlogs(String keyword) {
        if (keyword == null || keyword.isEmpty()) return getAllBlogs();
        Connection c = cnx();
        if (c == null) return getSampleBlogs();
        List<Blog> result = new ArrayList<>();
        String kw = "%" + keyword + "%";
        String[] cols = {"full_name", "fullName", "name", "username"};
        for (String col : cols) {
            result.clear();
            String sql = "SELECT b.id, b.titre, b.content, b.images, b.category, b.created_at, b.published_at, " +
                         "u." + col + " AS publisher_name FROM blog b LEFT JOIN user u ON b.publisher_id = u.id " +
                         "WHERE b.titre LIKE ? OR b.content LIKE ? OR u." + col + " LIKE ? ORDER BY b.created_at DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, kw); ps.setString(2, kw); ps.setString(3, kw);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapBlog(rs));
                return result;
            } catch (Exception e) { /* colonne suivante */ }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT b.id, b.titre, b.content, b.images, b.category, b.created_at, b.published_at, " +
                "CAST(IFNULL(b.publisher_id,0) AS CHAR) AS publisher_name FROM blog b WHERE b.titre LIKE ? OR b.content LIKE ? ORDER BY b.created_at DESC")) {
            ps.setString(1, kw); ps.setString(2, kw);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapBlog(rs));
        } catch (Exception e) { System.err.println("Erreur searchBlogs: " + e.getMessage()); }
        return result;
    }

    public List<Blog> filterByCategory(String... keywords) {
        List<Blog> result = new ArrayList<>();
        for (Blog b : getAllBlogs()) {
            String cat = b.getImage() != null ? b.getImage().toLowerCase() : "";
            for (String kw : keywords) {
                if (cat.equals(kw)) { result.add(b); break; }
            }
        }
        return result;
    }

    // ===================== INTERACTION =====================

    public List<Interaction> getInteractionsByBlogId(int blogId) {
        Connection c = cnx();
        if (c == null || blogId <= 0) return new ArrayList<>();
        List<Interaction> result = new ArrayList<>();
        // Colonnes réelles: innteractor_id (double n), reaction, comment
        String[] userCols = {"full_name", "fullName", "name", "username"};
        for (String col : userCols) {
            result.clear();
            String sql = "SELECT i.*, COALESCE(u." + col + ", CAST(i.innteractor_id AS CHAR)) AS creator_name " +
                         "FROM interaction i LEFT JOIN user u ON i.innteractor_id = u.id " +
                         "WHERE i.blog_id = ? AND IFNULL(i.is_deleted_by_admin,0)=0 AND IFNULL(i.is_flagged,0)=0 " +
                         "AND i.parent_id IS NULL AND IFNULL(i.is_favorite,0)=0";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapInteraction(rs));
                return result;
            } catch (Exception e) { /* colonne user suivante */ }
        }
        // Fallback sans JOIN user
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT i.*, CAST(i.innteractor_id AS CHAR) AS creator_name FROM interaction i WHERE i.blog_id = ? AND IFNULL(i.is_deleted_by_admin,0)=0 AND IFNULL(i.is_flagged,0)=0 AND i.parent_id IS NULL AND IFNULL(i.is_favorite,0)=0")) {
            ps.setInt(1, blogId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapInteraction(rs));
        } catch (Exception e) { System.err.println("Erreur getInteractions: " + e.getMessage()); }
        return result;
    }

    /** @return null si succès, message d'erreur détaillé sinon */
    public String addInteraction(int blogId, String type, String commentaire, String createdByName) {
        if (blogId <= 0) return "blogId invalide: " + blogId;
        if (!blogExists(blogId)) return "Article introuvable en base (id=" + blogId + ").";

        int userId = getCurrentUserId();
        if (userId <= 0 || !userExists(userId)) {
            return "Utilisateur connecte introuvable. Veuillez vous reconnecter.";
        }
        boolean isLike = "like".equalsIgnoreCase(type);

        // Filtre anti-grossièretés (commentaires uniquement)
        boolean profanityBlocked = false;
        if (!isLike && commentaire != null) {
            ProfanityFilterService.FilterResult filter = new ProfanityFilterService().check(commentaire);
            if (filter.blocked) {
                profanityBlocked = true;
                String author = (createdByName != null && !createdByName.isBlank()) ? createdByName : "Utilisateur #" + userId;
                String preview = commentaire.length() > 60 ? commentaire.substring(0, 60) + "…" : commentaire;
                new NotificationService().createForAdmin(
                    "⚠️ Commentaire bloqué de " + author + " (mot interdit : \"" + filter.detectedWord + "\") : \"" + preview + "\"",
                    blogId
                );
            }
        }
        StringBuilder errors = new StringBuilder();

        Connection c = cnx();
        if (c == null) {
            return "Impossible d'ouvrir une connexion à MySQL. Vérifiez que XAMPP/MySQL est démarré.";
        }

        int flagged = profanityBlocked ? 1 : 0;

        // Tentative 1 : avec toutes les colonnes optionnelles
        String sql1 = isLike
            ? "INSERT INTO interaction (reaction, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (1, NOW(), ?, ?, 0, 0, 0)"
            : "INSERT INTO interaction (comment, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (?, NOW(), ?, ?, 0, ?, 0)";
        try (PreparedStatement ps = c.prepareStatement(sql1)) {
            if (isLike) { ps.setInt(1, blogId); ps.setInt(2, userId); }
            else { ps.setString(1, commentaire); ps.setInt(2, blogId); ps.setInt(3, userId); ps.setInt(4, flagged); }
            ps.executeUpdate();
            updateBlogCounts(blogId);
            return profanityBlocked ? "PROFANITY" : null;
        } catch (Exception e1) {
            errors.append("[1] ").append(e1.getMessage());
            System.err.println("addInteraction [1]: " + e1.getMessage());
        }

        // Tentative 2 : colonnes minimales uniquement (sans colonnes optionnelles)
        String sql2 = isLike
            ? "INSERT INTO interaction (reaction, created_at, blog_id, innteractor_id) VALUES (1, NOW(), ?, ?)"
            : "INSERT INTO interaction (comment, created_at, blog_id, innteractor_id) VALUES (?, NOW(), ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql2)) {
            if (isLike) { ps.setInt(1, blogId); ps.setInt(2, userId); }
            else { ps.setString(1, commentaire); ps.setInt(2, blogId); ps.setInt(3, userId); }
            ps.executeUpdate();
            updateBlogCounts(blogId);
            return profanityBlocked ? "PROFANITY" : null;
        } catch (Exception e2) {
            errors.append("[2] ").append(e2.getMessage());
            System.err.println("addInteraction [2]: " + e2.getMessage());
        }

        return "Echec INSERT (blogId=" + blogId + ", userId=" + userId + "): " + errors;
    }

    /** Retourne le nombre total de réactions d'un article */
    public long countLikes(int blogId) {
        Connection c = cnx();
        if (c == null || blogId <= 0) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND reaction IS NOT NULL AND reaction <> 0")) {
            ps.setInt(1, blogId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) { System.err.println("countLikes: " + e.getMessage()); }
        return 0;
    }

    /** Retourne le nombre de réactions d'un type précis (1=like, 2=love, 3=surprise) */
    public long countReaction(int blogId, int reactionType) {
        if (blogId <= 0) return 0;
        Connection c = cnx();
        if (c == null) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND reaction = ?")) {
            ps.setInt(1, blogId);
            ps.setInt(2, reactionType);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) { System.err.println("countReaction: " + e.getMessage()); }
        return 0;
    }

    /** Retourne la réaction actuelle du user sur cet article (0 = aucune) */
    public int getUserReaction(int blogId, int userId) {
        if (blogId <= 0 || userId <= 0) return 0;
        Connection c = cnx();
        if (c == null) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT reaction FROM interaction WHERE blog_id = ? AND innteractor_id = ? " +
                "AND reaction IS NOT NULL AND reaction <> 0 LIMIT 1")) {
            ps.setInt(1, blogId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("reaction");
        } catch (Exception e) { System.err.println("getUserReaction: " + e.getMessage()); }
        return 0;
    }

    /** Supprime toute réaction du user sur cet article */
    public void removeReaction(int blogId, int userId) {
        removeLike(blogId, userId);
    }

    /** Enregistre une réaction (1=like, 2=love, 3=surprise), remplace l'ancienne */
    public void addReaction(int blogId, int reactionType, String userName) {
        if (blogId <= 0) return;
        if (!blogExists(blogId)) return;
        int userId = getCurrentUserId();
        if (userId <= 0 || !userExists(userId)) return;

        Connection c = cnx();
        if (c == null) return;

        try (PreparedStatement delete = c.prepareStatement(
                "DELETE FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND reaction IS NOT NULL AND reaction <> 0");
             PreparedStatement insert = c.prepareStatement(
                "INSERT INTO interaction (reaction, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (?, NOW(), ?, ?, 0, 0, 0)")) {
            delete.setInt(1, blogId);
            delete.setInt(2, userId);
            delete.executeUpdate();

            insert.setInt(1, reactionType);
            insert.setInt(2, blogId);
            insert.setInt(3, userId);
            insert.executeUpdate();
            updateBlogCounts(blogId);
        } catch (Exception e) {
            System.err.println("addReaction: " + e.getMessage());
        }
    }

    /** Retourne le nombre de commentaires d'un article directement depuis la BD */
    public long countComments(int blogId) {
        Connection c = cnx();
        if (c == null || blogId <= 0) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND comment IS NOT NULL AND comment <> '' AND IFNULL(is_deleted_by_admin,0)=0 AND IFNULL(is_flagged,0)=0")) {
            ps.setInt(1, blogId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) { System.err.println("countComments: " + e.getMessage()); }
        return 0;
    }

    /** Recalcule et met à jour likes_count et comments_count dans la table blog */
    public void updateBlogCounts(int blogId) {
        Connection c = cnx();
        if (c == null || blogId <= 0) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE blog SET likes_count = (SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND reaction IS NOT NULL AND reaction <> 0) WHERE id = ?")) {
            ps.setInt(1, blogId); ps.setInt(2, blogId);
            ps.executeUpdate();
        } catch (Exception e) { System.err.println("Erreur updateBlogCounts likes: " + e.getMessage()); }

        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE blog SET comments_count = (SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND comment IS NOT NULL AND comment <> '') WHERE id = ?")) {
            ps.setInt(1, blogId); ps.setInt(2, blogId);
            ps.executeUpdate();
        } catch (Exception e) { System.err.println("Erreur updateBlogCounts comments: " + e.getMessage()); }
    }

    // ===================== REPLIES =====================

    private void ensureParentIdColumn() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE interaction ADD COLUMN parent_id INT DEFAULT NULL");
        } catch (Exception ignored) {}
    }

    public void addReply(int blogId, int parentId, String comment) {
        ensureParentIdColumn();
        if (!blogExists(blogId)) return;
        int userId = getCurrentUserId();
        if (userId <= 0 || comment == null || comment.isBlank()) return;
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO interaction (blog_id, innteractor_id, comment, parent_id, created_at) " +
                "VALUES (?, ?, ?, ?, NOW())")) {
            ps.setInt(1, blogId);
            ps.setInt(2, userId);
            ps.setString(3, comment.trim());
            ps.setInt(4, parentId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("addReply: " + e.getMessage());
        }
    }

    /**
     * Self-JOIN avancé : récupère les réponses d'un commentaire parent
     * avec le nom de l'auteur de la réponse ET le nom de l'auteur parent.
     */
    public List<Interaction> getReplies(int parentId) {
        ensureParentIdColumn();
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<Interaction> replies = new ArrayList<>();
        String[] cols = {"full_name", "fullName", "name", "username"};
        for (String col : cols) {
            replies.clear();
            String sql =
                "SELECT r.id, r.comment, r.created_at, r.parent_id, " +
                "       u." + col + " AS creator_name " +
                "FROM interaction r " +
                "LEFT JOIN user u ON r.innteractor_id = u.id " +
                "WHERE r.parent_id = ? " +
                "  AND IFNULL(r.is_deleted_by_admin, 0) = 0 " +
                "  AND IFNULL(r.is_flagged, 0) = 0 " +
                "ORDER BY r.created_at ASC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, parentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDateTime createdAt = rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now();
                        String name = rs.getString("creator_name");
                        if (name == null || name.isBlank()) name = "Anonyme";
                        Interaction reply = new Interaction(
                                rs.getInt("id"), "commentaire",
                                rs.getString("comment"), createdAt, name);
                        reply.setParentId(rs.getInt("parent_id"));
                        replies.add(reply);
                    }
                    return replies;
                }
            } catch (Exception e) {
                System.err.println("getReplies col=" + col + ": " + e.getMessage());
            }
        }
        return replies;
    }

    public String getPublisherNameOfBlog(int blogId) {
        Connection c = cnx();
        if (c == null) return null;
        String[] cols = {"full_name", "fullName", "name", "username"};
        for (String col : cols) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT u." + col + " FROM blog b LEFT JOIN user u ON b.publisher_id = u.id WHERE b.id = ?")) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank()) return name;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ===================== SHARE (Self-JOIN) =====================

    private void ensureSharedFromColumn() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE blog ADD COLUMN shared_from_id INT DEFAULT NULL");
        } catch (Exception ignored) {}
    }

    public boolean shareArticle(int originalBlogId) {
        int userId = getCurrentUserId();
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        // Récupérer l'article original
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT titre, content, images, category FROM blog WHERE id = ?")) {
            ps.setInt(1, originalBlogId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;
            // Insérer le partage : même contenu, shared_from_id pointe vers l'original
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO blog (titre, content, images, category, status, " +
                    "created_at, published_at, publisher_id, shared_from_id) " +
                    "VALUES (?, ?, ?, ?, 'published', NOW(), NOW(), ?, ?)")) {
                ins.setString(1, rs.getString("titre"));
                ins.setString(2, rs.getString("content"));
                ins.setString(3, rs.getString("images"));
                ins.setString(4, rs.getString("category"));
                ins.setInt(5, userId);
                ins.setInt(6, originalBlogId);
                ins.executeUpdate();
                return true;
            }
        } catch (Exception e) {
            System.err.println("shareArticle: " + e.getMessage());
            return false;
        }
    }

    public boolean unshareArticle(int sharedBlogId) {
        int userId = getCurrentUserId();
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        // Supprimer d'abord les interactions liées (likes, commentaires, favoris)
        // sinon la contrainte FK bloque le DELETE sur blog
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM interaction WHERE blog_id = ?")) {
            ps.setInt(1, sharedBlogId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("unshareArticle (interactions): " + e.getMessage());
        }
        // Supprimer le blog partagé
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM blog WHERE id = ? AND publisher_id = ? AND shared_from_id IS NOT NULL")) {
            ps.setInt(1, sharedBlogId);
            ps.setInt(2, userId);
            boolean deleted = ps.executeUpdate() > 0;
            if (!deleted) System.err.println("unshareArticle: aucune ligne supprimée (id=" + sharedBlogId + ", userId=" + userId + ")");
            return deleted;
        } catch (Exception e) {
            System.err.println("unshareArticle (blog): " + e.getMessage());
            return false;
        }
    }

    public int countShares(int blogId) {
        Connection c = cnx();
        if (c == null) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM blog WHERE shared_from_id = ?")) {
            ps.setInt(1, blogId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { System.err.println("countShares: " + e.getMessage()); }
        return 0;
    }

    public boolean hasShared(int blogId) {
        int userId = getCurrentUserId();
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM blog WHERE shared_from_id = ? AND publisher_id = ?")) {
            ps.setInt(1, blogId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) { return false; }
    }

    // ===================== RECOMMANDATIONS =====================

    /**
     * Filtrage collaboratif :
     * 1. Articles que l'utilisateur courant a aimés/commentés/favoris
     * 2. Utilisateurs ayant aimé les mêmes articles (utilisateurs similaires)
     * 3. Articles que ces utilisateurs ont aimés, que l'utilisateur courant n'a pas encore vus
     * Triés par score (nb d'utilisateurs similaires) puis vues DESC
     */
    public List<Blog> getRecommendedBlogs(int limit) {
        int userId = getCurrentUserId();
        if (userId <= 0) return new ArrayList<>();
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<Blog> result = new ArrayList<>();
        String[] cols = {"full_name", "fullName", "name", "username"};
        for (String col : cols) {
            result.clear();
            String sql =
                "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, b.publisher_id, " +
                "b.created_at, b.published_at, IFNULL(b.views,0) AS views, " +
                "IFNULL(b.shared_from_id,0) AS shared_from_id, " +
                "u." + col + " AS publisher_name " +
                "FROM blog b " +
                "JOIN ( " +
                "  SELECT i2.blog_id, COUNT(DISTINCT i2.innteractor_id) AS score " +
                "  FROM interaction i2 " +
                "  WHERE i2.innteractor_id IN ( " +
                "    SELECT DISTINCT i_sim.innteractor_id " +
                "    FROM interaction i_sim " +
                "    WHERE i_sim.blog_id IN ( " +
                "      SELECT DISTINCT blog_id FROM interaction " +
                "      WHERE innteractor_id = ? " +
                "      AND (reaction IS NOT NULL OR comment IS NOT NULL OR IFNULL(is_favorite,0)=1) " +
                "    ) " +
                "    AND i_sim.innteractor_id != ? " +
                "    AND (i_sim.reaction IS NOT NULL OR i_sim.comment IS NOT NULL OR IFNULL(i_sim.is_favorite,0)=1) " +
                "  ) " +
                "  AND i2.blog_id NOT IN ( " +
                "    SELECT DISTINCT blog_id FROM interaction WHERE innteractor_id = ? " +
                "  ) " +
                "  AND IFNULL(i2.is_deleted_by_admin,0) = 0 " +
                "  GROUP BY i2.blog_id " +
                ") scored ON b.id = scored.blog_id " +
                "LEFT JOIN user u ON b.publisher_id = u.id " +
                "WHERE (b.status = 'published' OR b.status IS NULL) " +
                "ORDER BY scored.score DESC, b.views DESC " +
                "LIMIT ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                ps.setInt(4, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapBlog(rs));
                return result;
            } catch (Exception e) {
                System.err.println("getRecommendedBlogs [" + col + "]: " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Fallback cold-start : top articles les plus aimés.
     * Utilise une table dérivée pour le tri afin d'éviter
     * tout problème d'alias dans ORDER BY.
     */
    public List<Blog> getPopularBlogs(int limit) {
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<Blog> result = new ArrayList<>();
        String[] cols = {"full_name", "fullName", "name", "username"};
        for (String col : cols) {
            result.clear();
            String sql =
                "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, b.publisher_id, " +
                "b.created_at, b.published_at, IFNULL(b.views,0) AS views, " +
                "IFNULL(b.shared_from_id,0) AS shared_from_id, " +
                "u." + col + " AS publisher_name, " +
                "IFNULL(lk.cnt, 0) AS like_score " +
                "FROM blog b " +
                "LEFT JOIN user u ON b.publisher_id = u.id " +
                "LEFT JOIN ( " +
                "  SELECT blog_id, COUNT(*) AS cnt " +
                "  FROM interaction " +
                "  WHERE reaction IS NOT NULL AND reaction > 0 " +
                "  GROUP BY blog_id " +
                ") lk ON b.id = lk.blog_id " +
                "WHERE (b.status = 'published' OR b.status IS NULL) " +
                "ORDER BY IFNULL(lk.cnt, 0) DESC " +
                "LIMIT ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Blog blog = mapBlog(rs);
                    System.out.println("Popular: [" + blog.getTitre() + "] like_score=" + rs.getInt("like_score"));
                    result.add(blog);
                }
                return result;
            } catch (Exception e) {
                System.err.println("getPopularBlogs [" + col + "]: " + e.getMessage());
            }
        }
        return result;
    }

    // ===================== VIEWS =====================

    private void ensureViewsColumn() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE blog ADD COLUMN views INT DEFAULT 0");
        } catch (Exception ignored) {
            // Colonne déjà existante — ignoré
        }
    }

    public void incrementViews(int blogId) {
        ensureViewsColumn();
        if (!blogExists(blogId)) return;
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE blog SET views = IFNULL(views, 0) + 1 WHERE id = ?")) {
            ps.setInt(1, blogId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("incrementViews: " + e.getMessage());
        }
    }

    // ===================== ADMIN COMMENTS =====================

    private void ensureCommentAdminColumns() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE interaction ADD COLUMN IF NOT EXISTS is_deleted_by_admin TINYINT DEFAULT 0");
        } catch (Exception ignored) {}
    }

    public List<CommentAdminItem> getAllCommentsForAdmin() {
        ensureCommentAdminColumns();
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<CommentAdminItem> result = new ArrayList<>();
        String[] userCols = {"full_name", "fullName", "name", "username"};
        for (String col : userCols) {
            result.clear();
            String sql = "SELECT i.id, i.comment, i.created_at, " +
                         "IFNULL(i.is_deleted_by_admin, 0) AS is_deleted_by_admin, " +
                         "IFNULL(i.is_flagged, 0) AS is_flagged, " +
                         "b.id AS blog_id, b.titre AS blog_titre, " +
                         "COALESCE(u." + col + ", CAST(i.innteractor_id AS CHAR)) AS auteur " +
                         "FROM interaction i " +
                         "JOIN blog b ON i.blog_id = b.id " +
                         "LEFT JOIN user u ON i.innteractor_id = u.id " +
                         "WHERE i.comment IS NOT NULL AND i.comment <> '' " +
                         "ORDER BY i.created_at DESC";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CommentAdminItem(
                        rs.getInt("id"),
                        rs.getString("comment"),
                        rs.getString("auteur"),
                        rs.getString("blog_titre"),
                        rs.getInt("blog_id"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                        rs.getInt("is_deleted_by_admin") == 1,
                        rs.getInt("is_flagged") == 1
                    ));
                }
                if (!result.isEmpty() || col.equals("username")) return result;
            } catch (Exception e) {
                System.err.println("getAllCommentsForAdmin [" + col + "]: " + e.getMessage());
            }
        }
        return result;
    }

    public long countCommentsForAdmin(String status) {
        ensureCommentAdminColumns();
        Connection c = cnx();
        if (c == null) return 0;
        String where = "total".equals(status)   ? "" :
                       "pending".equals(status) ? " AND IFNULL(is_flagged,0)=1 AND IFNULL(is_deleted_by_admin,0)=0" :
                       "visible".equals(status) ? " AND IFNULL(is_flagged,0)=0 AND IFNULL(is_deleted_by_admin,0)=0" :
                       " AND IFNULL(is_deleted_by_admin,0)=1";
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE comment IS NOT NULL AND comment <> ''" + where)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) { System.err.println("countCommentsForAdmin: " + e.getMessage()); }
        return 0;
    }

    public void deleteCommentByAdmin(int commentId) {
        ensureCommentAdminColumns();
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE interaction SET is_deleted_by_admin = 1 WHERE id = ?")) {
            ps.setInt(1, commentId);
            ps.executeUpdate();
            logCommentAction(commentId, "Supprime", c);
        } catch (Exception e) { System.err.println("deleteCommentByAdmin: " + e.getMessage()); }
    }

    public void approveComment(int commentId) {
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE interaction SET is_flagged = 0, is_deleted_by_admin = 0 WHERE id = ?")) {
            ps.setInt(1, commentId);
            ps.executeUpdate();
            logCommentAction(commentId, "Approuve", c);
        } catch (Exception e) { System.err.println("approveComment: " + e.getMessage()); }
    }

    private void ensureCommentLogTable() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS comment_log (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "admin_name VARCHAR(150) NOT NULL," +
                "action VARCHAR(50) NOT NULL," +
                "comment_preview VARCHAR(200) DEFAULT ''," +
                "auteur VARCHAR(150) DEFAULT ''," +
                "created_at DATETIME DEFAULT NOW())");
        } catch (Exception ignored) {}
    }

    private void logCommentAction(int commentId, String action, Connection c) {
        // Créer la table si elle n'existe pas (réutilise la même connexion singleton)
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS comment_log (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "admin_name VARCHAR(150) NOT NULL," +
                "action VARCHAR(50) NOT NULL," +
                "comment_preview VARCHAR(200) DEFAULT ''," +
                "auteur VARCHAR(150) DEFAULT ''," +
                "created_at DATETIME DEFAULT NOW())");
        } catch (Exception ignored) {}
        String adminName = tn.esprit.fahamni.utils.SessionManager.getCurrentUserName();
        if (adminName == null || adminName.isBlank()) adminName = "Administrateur";
        String preview = "", auteur = "";
        // Essayer plusieurs noms de colonnes pour récupérer le nom de l'auteur
        String[] nameCols = {"full_name", "fullName", "name", "username", "prenom", "nom"};
        for (String col : nameCols) {
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT i.comment, COALESCE(u." + col + ", CAST(i.innteractor_id AS CHAR)) AS auteur " +
                    "FROM interaction i LEFT JOIN user u ON i.innteractor_id = u.id WHERE i.id = ?")) {
                sel.setInt(1, commentId);
                ResultSet rs = sel.executeQuery();
                if (rs.next()) {
                    String txt = rs.getString("comment");
                    preview = txt != null && txt.length() > 80 ? txt.substring(0, 80) + "..." : (txt != null ? txt : "");
                    String a = rs.getString("auteur");
                    auteur  = a != null ? a : "";
                }
                break; // colonne trouvée
            } catch (Exception ignored) {}
        }
        if (preview.isEmpty() && auteur.isEmpty()) {
            // Fallback sans JOIN
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT comment, innteractor_id FROM interaction WHERE id = ?")) {
                sel.setInt(1, commentId);
                ResultSet rs = sel.executeQuery();
                if (rs.next()) {
                    String txt = rs.getString("comment");
                    preview = txt != null && txt.length() > 80 ? txt.substring(0, 80) + "..." : (txt != null ? txt : "");
                    auteur  = String.valueOf(rs.getInt("innteractor_id"));
                }
            } catch (Exception e) {
                System.err.println("logCommentAction (fallback): " + e.getMessage());
                return;
            }
        }
        // Utiliser la connexion passée en paramètre (singleton — ne pas la fermer)
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO comment_log (admin_name, action, comment_preview, auteur) VALUES (?, ?, ?, ?)")) {
            ins.setString(1, adminName.length() > 148 ? adminName.substring(0, 148) : adminName);
            ins.setString(2, action);
            ins.setString(3, preview.length() > 198 ? preview.substring(0, 198) : preview);
            ins.setString(4, auteur.length() > 148 ? auteur.substring(0, 148) : auteur);
            ins.executeUpdate();
            System.out.println("comment_log INSERT OK: " + action + " by " + adminName);
        } catch (Exception e) { System.err.println("logCommentAction (insert): " + e.getMessage()); }
    }

    public java.util.List<java.util.Map<String, String>> getRecentCommentLog(int limit) {
        ensureCommentLogTable();
        java.util.List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        Connection c = cnx();
        if (c == null) return result;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT admin_name, action, comment_preview, auteur, created_at " +
                "FROM comment_log ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
                row.put("date",    rs.getTimestamp("created_at") != null
                    ? rs.getTimestamp("created_at").toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "");
                row.put("admin",   rs.getString("admin_name"));
                row.put("action",  rs.getString("action"));
                row.put("comment", rs.getString("comment_preview"));
                row.put("auteur",  rs.getString("auteur"));
                result.add(row);
            }
        } catch (Exception e) { System.err.println("getRecentCommentLog: " + e.getMessage()); }
        return result;
    }

    public void restoreComment(int commentId) {
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE interaction SET is_deleted_by_admin = 0 WHERE id = ?")) {
            ps.setInt(1, commentId);
            ps.executeUpdate();
        } catch (Exception e) { System.err.println("restoreComment: " + e.getMessage()); }
    }

    // ===================== FAVORITES (stockés dans interaction.is_favorite) =====================

    private void ensureFavoriteColumn() {
        Connection c = cnx();
        if (c == null) return;
        // Ajouter la colonne si elle n'existe pas encore
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE interaction ADD COLUMN is_favorite TINYINT DEFAULT 0");
        } catch (Exception ignored) {}
        // Migrer les données de la table favorites vers interaction.is_favorite
        try (Statement st = c.createStatement()) {
            boolean favTableExists = false;
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = 'favorites'")) {
                if (rs.next()) favTableExists = rs.getInt(1) > 0;
            }
            if (favTableExists) {
                // Insérer les favoris existants dans interaction (évite les doublons)
                st.executeUpdate(
                    "INSERT IGNORE INTO interaction (blog_id, innteractor_id, is_favorite, created_at, is_notif_read, is_flagged, is_deleted_by_admin) " +
                    "SELECT blog_id, user_id, 1, created_at, 0, 0, 0 FROM favorites f " +
                    "WHERE NOT EXISTS (" +
                    "  SELECT 1 FROM interaction i WHERE i.blog_id = f.blog_id AND i.innteractor_id = f.user_id AND i.is_favorite = 1)");
                // Supprimer la table favorites
                st.executeUpdate("DROP TABLE favorites");
                System.out.println("Migration favorites → interaction.is_favorite effectuée.");
            }
        } catch (Exception e) {
            System.err.println("ensureFavoriteColumn migration: " + e.getMessage());
        }
    }

    public boolean addFavorite(int blogId) {
        ensureFavoriteColumn();
        if (!blogExists(blogId)) {
            System.err.println("addFavorite: blog introuvable en base (id=" + blogId + ")");
            return false;
        }
        int userId = getCurrentUserId();
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        // Vérifie si une ligne favorite existe déjà
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND is_favorite = 1 LIMIT 1")) {
            ps.setInt(1, blogId); ps.setInt(2, userId);
            if (ps.executeQuery().next()) return true;
        } catch (Exception e) { System.err.println("addFavorite check: " + e.getMessage()); }
        // Tentative 1 : avec toutes les colonnes
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO interaction (blog_id, innteractor_id, is_favorite, created_at, is_notif_read, is_flagged, is_deleted_by_admin) " +
                "VALUES (?, ?, 1, NOW(), 0, 0, 0)")) {
            ps.setInt(1, blogId); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("addFavorite [1]: " + e.getMessage());
        }
        // Tentative 2 : colonnes minimales uniquement
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO interaction (blog_id, innteractor_id, is_favorite, created_at) VALUES (?, ?, 1, NOW())")) {
            ps.setInt(1, blogId); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { System.err.println("addFavorite [2]: " + e.getMessage()); return false; }
    }

    public boolean removeFavorite(int blogId) {
        int userId = getCurrentUserId();
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND is_favorite = 1")) {
            ps.setInt(1, blogId); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { System.err.println("removeFavorite: " + e.getMessage()); return false; }
    }

    public boolean isFavorite(int blogId) {
        ensureFavoriteColumn();
        if (!blogExists(blogId)) return false;
        int userId = getCurrentUserId();
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND is_favorite = 1")) {
            ps.setInt(1, blogId); ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) { System.err.println("isFavorite: " + e.getMessage()); return false; }
    }

    public List<Blog> getFavoriteBlogs() {
        ensureFavoriteColumn();
        int userId = getCurrentUserId();
        if (userId <= 0) return new java.util.ArrayList<>();
        Connection c = cnx();
        if (c == null) return new java.util.ArrayList<>();
        List<Blog> result = new java.util.ArrayList<>();
        String[] userNameCols = {"full_name", "fullName", "name", "username"};
        for (String col : userNameCols) {
            result.clear();
            String sql =
                "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, b.publisher_id, " +
                "b.created_at, b.published_at, IFNULL(b.views,0) AS views, " +
                "IFNULL(b.shared_from_id,0) AS shared_from_id, " +
                "u." + col + " AS publisher_name " +
                "FROM blog b " +
                "JOIN interaction i ON b.id = i.blog_id " +
                "LEFT JOIN user u ON b.publisher_id = u.id " +
                "WHERE i.innteractor_id = ? AND i.is_favorite = 1 " +
                "AND (b.status = 'published' OR b.status IS NULL) " +
                "ORDER BY i.created_at DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapBlog(rs));
                return result;
            } catch (Exception e) {
                System.err.println("getFavoriteBlogs [" + col + "]: " + e.getMessage());
            }
        }
        return result;
    }

    // ===================== HELPERS =====================

    private Blog mapBlog(ResultSet rs) throws SQLException {
        LocalDateTime createdAt = rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now();
        LocalDateTime publishedAt = rs.getTimestamp("published_at") != null
                ? rs.getTimestamp("published_at").toLocalDateTime() : null;
        String publisher = rs.getString("publisher_name");
        if (publisher == null || publisher.isBlank()) publisher = "Anonyme";
        String category = null;
        try { category = rs.getString("category"); } catch (Exception ignored) {}
        Blog blog = new Blog(rs.getInt("id"), rs.getString("titre"), rs.getString("content"),
                fromCategoryDB(category), createdAt, publisher, publishedAt);
        try { blog.setPublisherId(rs.getInt("publisher_id")); } catch (Exception ignored) {}
        try { blog.setStatus(rs.getString("status")); } catch (Exception ignored) {}
        try { blog.setViews(rs.getInt("views")); } catch (Exception ignored) {}
        try {
            int sfId = rs.getInt("shared_from_id");
            blog.setSharedFromId(sfId);
            if (sfId > 0) {
                String origAuthor = getPublisherNameOfBlog(sfId);
                blog.setOriginalAuthor(origAuthor);
            }
        } catch (Exception ignored) {}
        return blog;
    }

    private Interaction mapInteraction(ResultSet rs) throws SQLException {
        LocalDateTime createdAt = rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now();

        // reaction = INTEGER (1=like, NULL=commentaire), comment = TEXT
        String reactionVal = getColumnSafe(rs, "reaction", "type", "reaction_type");
        String commentaire  = getColumnSafe(rs, "comment", "commentaire", "content", "text");

        // Convertir valeur entière → type logique
        String type = (reactionVal != null && !reactionVal.trim().equals("0") && !reactionVal.trim().isEmpty())
                ? "like" : "commentaire";

        // creator_name est un alias calculé
        String creatorName = null;
        try { creatorName = rs.getString("creator_name"); } catch (Exception ignored) {}

        return new Interaction(rs.getInt("id"), type, commentaire, createdAt, creatorName);
    }

    /** Essaie plusieurs noms de colonnes et retourne la première valeur trouvée */
    private String getColumnSafe(ResultSet rs, String... columnNames) {
        for (String col : columnNames) {
            try {
                String val = rs.getString(col);
                return val; // trouvé (même si null)
            } catch (SQLException ignored) { }
        }
        return null;
    }

    private List<Blog> getSampleBlogs() {
        List<Blog> s = new ArrayList<>();
        s.add(new Blog(0, "Introduction aux équations du second degré",
                "Une équation du second degré est de la forme ax² + bx + c = 0. " +
                "Pour la résoudre, on calcule le discriminant Δ = b² - 4ac. " +
                "Si Δ > 0, deux solutions. Si Δ = 0, une solution. Si Δ < 0, pas de solution réelle.",
                "math", LocalDateTime.now().minusDays(2), "Ahmed Ben Ali", LocalDateTime.now().minusDays(2)));
        s.add(new Blog(0, "Les lois de Newton expliquées simplement",
                "1ère loi: inertie. 2ème loi: F=ma. 3ème loi: action-réaction.",
                "science", LocalDateTime.now().minusDays(5), "Fatma Trabelsi", LocalDateTime.now().minusDays(5)));
        s.add(new Blog(0, "Apprendre Python en 10 étapes",
                "Python est le meilleur langage pour débuter. Variables, conditions, boucles, fonctions...",
                "informatique", LocalDateTime.now().minusDays(1), "Yassine Mansour", LocalDateTime.now().minusDays(1)));
        s.add(new Blog(0, "Améliorer son niveau en anglais rapidement",
                "Regardez des séries, écoutez des podcasts, pratiquez avec Anki, trouvez un partenaire.",
                "autre", LocalDateTime.now().minusDays(3), "Meriem Khelifi", LocalDateTime.now().minusDays(3)));
        return s;
    }

    private boolean isBlogTableEmpty() {
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM blog")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) == 0;
        } catch (Exception e) {
            System.err.println("isBlogTableEmpty: " + e.getMessage());
            return false;
        }
    }

    private void ensureSampleBlogsPersisted() {
        Connection c = cnx();
        if (c == null || !isBlogTableEmpty()) return;

        Integer samplePublisherId = resolveSamplePublisherId();
        if (samplePublisherId == null || samplePublisherId <= 0) {
            System.err.println("ensureSampleBlogsPersisted: aucun utilisateur disponible pour publisher_id.");
            return;
        }

        String sql =
            "INSERT INTO blog (" +
                "titre, content, images, status, category, created_at, published_at, " +
                "publisher_id, likes_count, comments_count, is_status_notif_read, shared_from_id, views" +
            ") VALUES (?, ?, NULL, 'published', ?, ?, ?, ?, 0, 0, 0, NULL, 0)";

        int inserted = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Blog sample : getSampleBlogs()) {
                ps.setString(1, sample.getTitre());
                ps.setString(2, sample.getContent());
                ps.setString(3, toCategoryDB(sample.getImage()));
                ps.setTimestamp(4, Timestamp.valueOf(sample.getCreatedAt()));
                ps.setTimestamp(5, Timestamp.valueOf(
                    sample.getPublishedAt() != null ? sample.getPublishedAt() : sample.getCreatedAt()
                ));
                ps.setInt(6, samplePublisherId);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            for (int result : results) {
                if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }
        } catch (Exception e) {
            System.err.println("ensureSampleBlogsPersisted: " + e.getMessage());
            return;
        }

        if (inserted > 0) {
            System.out.println("BlogService: " + inserted + " article(s) d'exemple ont ete enregistres en base.");
        }
    }

    private Integer resolveSamplePublisherId() {
        int currentUserId = getCurrentUserId();
        if (currentUserId > 0 && userExists(currentUserId)) {
            return currentUserId;
        }

        Connection c = cnx();
        if (c == null) return null;
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM user ORDER BY id ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (Exception e) {
            System.err.println("resolveSamplePublisherId: " + e.getMessage());
        }
        return null;
    }

    private boolean blogExists(int blogId) {
        if (blogId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM blog WHERE id = ? LIMIT 1")) {
            ps.setInt(1, blogId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("blogExists: " + e.getMessage());
            return false;
        }
    }

    /** Vérifie si le user a déjà liké cet article */
    public boolean hasUserLiked(int blogId, int userId) {
        if (blogId <= 0) return false;
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND reaction IS NOT NULL AND reaction <> 0")) {
            ps.setInt(1, blogId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (Exception e) { System.err.println("hasUserLiked: " + e.getMessage()); }
        return false;
    }

    /** Supprime le like du user sur un article (toggle) */
    public void removeLike(int blogId, int userId) {
        if (blogId <= 0) return;
        if (userId <= 0) userId = getCurrentUserId();
        if (userId <= 0 || !userExists(userId)) return;

        Connection c = cnx();
        if (c == null) return;
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND reaction IS NOT NULL AND reaction <> 0")) {
                ps.setInt(1, blogId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            } catch (Exception e) { System.err.println("removeLike: " + e.getMessage()); }
        } catch (Exception e) {
            System.err.println("removeLike: " + e.getMessage());
        }
        updateBlogCounts(blogId);
    }

    /** Retourne l'id du user connecté */
    private int getCurrentUserId() {
        // Utiliser l'id stocké dans la session (retourné par AuthService depuis la BD)
        if (SessionManager.getCurrentUser() != null && SessionManager.getCurrentUser().getId() > 0) {
            return SessionManager.getCurrentUser().getId();
        }
        // Fallback : chercher par email
        Connection c = cnx();
        if (c == null || SessionManager.getCurrentUser() == null) return 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM user WHERE email = ?")) {
            ps.setString(1, SessionManager.getCurrentUser().getEmail());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (Exception e) { System.err.println("getCurrentUserId: " + e.getMessage()); }
        return 0;
    }

    private boolean canCurrentUserCreateBlog() {
        User user = SessionManager.getCurrentUser();
        return user != null && user.getRole() == UserRole.TUTOR;
    }

    private boolean userExists(int userId) {
        if (userId <= 0) return false;
        Connection c = cnx();
        if (c == null) return false;
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM user WHERE id = ? LIMIT 1")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("userExists: " + e.getMessage());
            return false;
        }
    }

    // ─── IServices<Blog> ─────────────────────────────────────────────────────

    @Override
    public void add(Blog blog) throws SQLException {
        addBlog(blog);
    }

    @Override
    public List<Blog> getAll() throws SQLException {
        return getAllBlogs();
    }

    @Override
    public void update(Blog blog) throws SQLException {
        updateBlog(blog);
    }

    @Override
    public void delete(Blog blog) throws SQLException {
        deleteBlog(blog.getId());
    }
}
