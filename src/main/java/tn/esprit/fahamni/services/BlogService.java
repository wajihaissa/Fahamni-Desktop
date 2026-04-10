package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Blog;
import tn.esprit.fahamni.Models.Interaction;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.SessionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// NotificationService est instancié à la demande pour éviter la dépendance circulaire

public class BlogService {

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

    // ===================== BLOG =====================

    public List<Blog> getAllBlogs() {
        syncAllBlogCounts(); // synchronise likes_count/comments_count pour tous les articles
        List<Blog> blogs = getAllBlogsFromDB();
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
            System.out.println("syncAllBlogCounts: likes_count synchronisé.");
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
            System.out.println("syncAllBlogCounts: comments_count synchronisé.");
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
        String[] userNameCols = {"fullName", "full_name", "name", "username"};
        for (String col : userNameCols) {
            blogs.clear();
            String sql = "SELECT b.id, b.titre, b.content, b.images, b.category, b.status, b.publisher_id, " +
                         "b.created_at, b.published_at, u." + col + " AS publisher_name " +
                         "FROM blog b LEFT JOIN user u ON b.publisher_id = u.id " +
                         "WHERE (b.status = 'published' OR b.status IS NULL) " +
                         "ORDER BY b.created_at DESC";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) blogs.add(mapBlog(rs));
                System.out.println("getAllBlogs: " + blogs.size() + " articles (col: " + col + ")");
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
            System.out.println("getAllBlogs fallback: " + blogs.size() + " articles");
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
        Connection c = cnx();
        if (c == null) return -1;
        int publisherId = getCurrentUserId();
        String cat = toCategoryDB(blog.getImage());

        String[] sqls = {
            "INSERT INTO blog (titre, content, images, status, category, created_at, published_at, publisher_id, likes_count, comments_count) VALUES (?, ?, NULL, 'pending', ?, NOW(), NOW(), ?, 0, 0)",
            "INSERT INTO blog (titre, content, images, status, category, created_at, published_at, publisher_id) VALUES (?, ?, NULL, 'pending', ?, NOW(), NOW(), ?)",
            "INSERT INTO blog (titre, content, images, status, category, created_at, published_at, likes_count, comments_count) VALUES (?, ?, NULL, 'pending', ?, NOW(), NOW(), 0, 0)",
            "INSERT INTO blog (titre, content, images, status, category, created_at, published_at) VALUES (?, ?, NULL, 'pending', ?, NOW(), NOW())"
        };

        for (int i = 0; i < sqls.length; i++) {
            try (PreparedStatement ps = c.prepareStatement(sqls[i], Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, blog.getTitre());
                ps.setString(2, blog.getContent());
                ps.setString(3, cat);
                if (i < 2) {
                    if (publisherId > 0) ps.setInt(4, publisherId);
                    else ps.setNull(4, Types.INTEGER);
                }
                ps.executeUpdate();
                int blogId = -1;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) blogId = keys.getInt(1);
                }
                System.out.println("Article sauvegarde (requete " + (i+1) + ", id=" + blogId + ", category=" + cat + ")");
                // Notification admin
                try {
                    String tuteurName = SessionManager.getCurrentUserName();
                    new NotificationService().createForAdmin(
                        "Nouvel article en attente : \"" + blog.getTitre() + "\" soumis par " + tuteurName, blogId);
                } catch (Exception ne) {
                    System.err.println("addBlog: notif admin echouee: " + ne.getMessage());
                }
                return blogId;
            } catch (Exception e) {
                System.err.println("addBlog requete " + (i+1) + ": " + e.getMessage());
            }
        }
        return -1;
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
        String[] cols = {"fullName", "full_name", "name", "username"};
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
        String[] userCols = {"fullName", "full_name", "name", "username"};
        for (String col : userCols) {
            result.clear();
            String sql = "SELECT i.*, COALESCE(u." + col + ", CAST(i.innteractor_id AS CHAR)) AS creator_name " +
                         "FROM interaction i LEFT JOIN user u ON i.innteractor_id = u.id WHERE i.blog_id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, blogId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapInteraction(rs));
                return result;
            } catch (Exception e) { /* colonne user suivante */ }
        }
        // Fallback sans JOIN user
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT i.*, CAST(i.innteractor_id AS CHAR) AS creator_name FROM interaction i WHERE i.blog_id = ?")) {
            ps.setInt(1, blogId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapInteraction(rs));
        } catch (Exception e) { System.err.println("Erreur getInteractions: " + e.getMessage()); }
        return result;
    }

    /** @return null si succès, message d'erreur détaillé sinon */
    public String addInteraction(int blogId, String type, String commentaire, String createdByName) {
        if (blogId <= 0) return "blogId invalide: " + blogId;

        int userId = getCurrentUserId();
        boolean isLike = "like".equalsIgnoreCase(type);
        System.out.println("addInteraction: blogId=" + blogId + " type=" + type + " userId=" + userId);

        StringBuilder errors = new StringBuilder();

        // Utiliser une connexion FRAÎCHE pour éviter tout problème d'état
        Connection fresh = MyDataBase.getInstance().getFreshConnection();
        if (fresh == null) {
            return "Impossible d'ouvrir une connexion à MySQL. Vérifiez que XAMPP/MySQL est démarré.";
        }

        try {
            // Désactiver FK checks sur cette connexion fraîche
            try (Statement st = fresh.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");
            } catch (Exception ignored) {}

            // Tentative 1 : avec innteractor_id réel
            String sql1 = isLike
                ? "INSERT INTO interaction (reaction, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (1, NOW(), ?, ?, 0, 0, 0)"
                : "INSERT INTO interaction (comment, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (?, NOW(), ?, ?, 0, 0, 0)";
            try (PreparedStatement ps = fresh.prepareStatement(sql1)) {
                if (isLike) { ps.setInt(1, blogId); ps.setInt(2, userId); }
                else { ps.setString(1, commentaire); ps.setInt(2, blogId); ps.setInt(3, userId); }
                ps.executeUpdate();
                System.out.println("OK interaction enregistrée (userId=" + userId + ")");
                updateBlogCounts(blogId);
                return null; // succès
            } catch (Exception e1) {
                errors.append("[1] ").append(e1.getMessage()).append(" | ");
                System.err.println("Tentative 1 échouée: " + e1.getMessage());
            }

            // Tentative 2 : innteractor_id=1 forcé
            String sql2 = isLike
                ? "INSERT INTO interaction (reaction, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (1, NOW(), ?, 1, 0, 0, 0)"
                : "INSERT INTO interaction (comment, created_at, blog_id, innteractor_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (?, NOW(), ?, 1, 0, 0, 0)";
            try (PreparedStatement ps = fresh.prepareStatement(sql2)) {
                if (isLike) { ps.setInt(1, blogId); }
                else { ps.setString(1, commentaire); ps.setInt(2, blogId); }
                ps.executeUpdate();
                System.out.println("OK interaction enregistrée (innteractor_id=1)");
                updateBlogCounts(blogId);
                return null; // succès
            } catch (Exception e2) {
                errors.append("[2] ").append(e2.getMessage()).append(" | ");
                System.err.println("Tentative 2 échouée: " + e2.getMessage());
            }

            // Tentative 3 : sans innteractor_id
            String sql3 = isLike
                ? "INSERT INTO interaction (reaction, created_at, blog_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (1, NOW(), ?, 0, 0, 0)"
                : "INSERT INTO interaction (comment, created_at, blog_id, is_notif_read, is_flagged, is_deleted_by_admin) VALUES (?, NOW(), ?, 0, 0, 0)";
            try (PreparedStatement ps = fresh.prepareStatement(sql3)) {
                if (isLike) { ps.setInt(1, blogId); }
                else { ps.setString(1, commentaire); ps.setInt(2, blogId); }
                ps.executeUpdate();
                System.out.println("OK interaction enregistrée (sans innteractor_id)");
                updateBlogCounts(blogId);
                return null; // succès
            } catch (Exception e3) {
                errors.append("[3] ").append(e3.getMessage());
                System.err.println("Tentative 3 échouée: " + e3.getMessage());
            }

        } finally {
            try { fresh.close(); } catch (Exception ignored) {}
        }

        return "Echec INSERT (blogId=" + blogId + ", userId=" + userId + "): " + errors;
    }

    /** Retourne le nombre de likes d'un article directement depuis la BD */
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

    /** Retourne le nombre de commentaires d'un article directement depuis la BD */
    public long countComments(int blogId) {
        Connection c = cnx();
        if (c == null || blogId <= 0) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM interaction WHERE blog_id = ? AND comment IS NOT NULL AND comment <> ''")) {
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

    /** Vérifie si le user a déjà liké cet article */
    public boolean hasUserLiked(int blogId, int userId) {
        if (blogId <= 0) return false;
        if (userId <= 0) userId = 1; // fallback user mock
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
        Connection fresh = MyDataBase.getInstance().getFreshConnection();
        if (fresh == null) return;
        try {
            try (Statement st = fresh.createStatement()) { st.execute("SET FOREIGN_KEY_CHECKS=0"); } catch (Exception ignored) {}
            try (PreparedStatement ps = fresh.prepareStatement(
                    "DELETE FROM interaction WHERE blog_id = ? AND innteractor_id = ? AND reaction IS NOT NULL AND reaction <> 0")) {
                ps.setInt(1, blogId);
                ps.setInt(2, userId);
                ps.executeUpdate();
                System.out.println("Like retiré (blogId=" + blogId + ", userId=" + userId + ")");
            } catch (Exception e) { System.err.println("removeLike: " + e.getMessage()); }
        } finally {
            try { fresh.close(); } catch (Exception ignored) {}
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
        if (c == null || SessionManager.getCurrentUser() == null) return 1;
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM user WHERE email = ?")) {
            ps.setString(1, SessionManager.getCurrentUser().getEmail());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (Exception e) { System.err.println("getCurrentUserId: " + e.getMessage()); }
        return 1;
    }
}
