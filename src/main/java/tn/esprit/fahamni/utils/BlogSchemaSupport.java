package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class BlogSchemaSupport {

    private static volatile boolean initialized;

    private BlogSchemaSupport() {
    }

    public static synchronized void ensureSchema() {
        if (initialized) {
            return;
        }
        repairSchema();
    }

    public static synchronized List<String> repairSchema() {
        List<String> report = new ArrayList<>();
        Connection connection = MyDataBase.getInstance().getCnx();
        if (connection == null) {
            report.add("Connexion MySQL indisponible, impossible de reparer le schema blog.");
            return report;
        }

        try {
            ensureBlogTable(connection, report);
            ensureInteractionTable(connection, report);
            ensureNotificationTable(connection, report);
            ensureCommentLogTable(connection, report);
            ensureActivityLogTable(connection, report);

            ensureLegacyCompatibleColumns(connection, report);
            ensureBlogColumns(connection, report);
            ensureInteractionColumns(connection, report);

            backfillInteractionColumns(connection, report);
            backfillBlogColumns(connection, report);
            migrateFavoritesTable(connection, report);
            ensureIndexes(connection, report);
            rebuildBlogCounters(connection, report);

            initialized = true;
        } catch (SQLException exception) {
            report.add("Erreur de reparation du schema blog: " + exception.getMessage());
        }

        return report;
    }

    private static void ensureBlogTable(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "blog")) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(connection,
            "CREATE TABLE blog ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "titre VARCHAR(255) NOT NULL, "
                + "content LONGTEXT NOT NULL, "
                + "images VARCHAR(255) NULL, "
                + "status VARCHAR(30) NULL DEFAULT 'pending', "
                + "category VARCHAR(100) NULL, "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "published_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP, "
                + "publisher_id INT NULL, "
                + "likes_count INT NOT NULL DEFAULT 0, "
                + "comments_count INT NOT NULL DEFAULT 0, "
                + "is_status_notif_read TINYINT(1) NOT NULL DEFAULT 0, "
                + "shared_from_id INT NULL, "
                + "views INT NOT NULL DEFAULT 0"
                + ")");
        report.add("Table `blog` creee.");
    }

    private static void ensureInteractionTable(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "interaction")) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(connection,
            "CREATE TABLE interaction ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "reaction INT NULL, "
                + "comment TEXT NULL, "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "blog_id INT NOT NULL, "
                + "innteractor_id INT NULL, "
                + "parent_id INT NULL, "
                + "is_notif_read TINYINT(1) NOT NULL DEFAULT 0, "
                + "is_flagged TINYINT(1) NOT NULL DEFAULT 0, "
                + "is_deleted_by_admin TINYINT(1) NOT NULL DEFAULT 0, "
                + "is_favorite TINYINT(1) NOT NULL DEFAULT 0"
                + ")");
        report.add("Table `interaction` creee.");
    }

    private static void ensureNotificationTable(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "notification")) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(connection,
            "CREATE TABLE notification ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "recipient_id INT NOT NULL DEFAULT 0, "
                + "blog_id INT NULL, "
                + "message VARCHAR(500) NOT NULL, "
                + "is_read TINYINT(1) NOT NULL DEFAULT 0, "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
        report.add("Table `notification` creee.");
    }

    private static void ensureCommentLogTable(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "comment_log")) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(connection,
            "CREATE TABLE comment_log ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "admin_name VARCHAR(150) NOT NULL, "
                + "action VARCHAR(50) NOT NULL, "
                + "comment_preview VARCHAR(200) DEFAULT '', "
                + "auteur VARCHAR(150) DEFAULT '', "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
        report.add("Table `comment_log` creee.");
    }

    private static void ensureActivityLogTable(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "activity_log")) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(connection,
            "CREATE TABLE activity_log ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "admin_name VARCHAR(150) NOT NULL, "
                + "action VARCHAR(50) NOT NULL, "
                + "article_title VARCHAR(300) NOT NULL, "
                + "article_author VARCHAR(150) DEFAULT '', "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
        report.add("Table `activity_log` creee.");
    }

    private static void ensureLegacyCompatibleColumns(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "interaction")
            && !DatabaseSchemaUtils.columnExists(connection, "interaction", "innteractor_id")) {
            DatabaseSchemaUtils.executeDdl(connection, "ALTER TABLE interaction ADD COLUMN innteractor_id INT NULL");
            report.add("Colonne `interaction.innteractor_id` ajoutee.");
        }

        if (DatabaseSchemaUtils.tableExists(connection, "interaction")
            && !DatabaseSchemaUtils.columnExists(connection, "interaction", "comment")) {
            DatabaseSchemaUtils.executeDdl(connection, "ALTER TABLE interaction ADD COLUMN comment TEXT NULL");
            report.add("Colonne `interaction.comment` ajoutee.");
        }
    }

    private static void ensureBlogColumns(Connection connection, List<String> report) throws SQLException {
        ensureColumn(connection, "blog", "images", "ALTER TABLE blog ADD COLUMN images VARCHAR(255) NULL", report);

        boolean addedStatus = ensureColumn(
            connection,
            "blog",
            "status",
            "ALTER TABLE blog ADD COLUMN status VARCHAR(30) NULL DEFAULT 'pending'",
            report
        );
        ensureColumn(connection, "blog", "category", "ALTER TABLE blog ADD COLUMN category VARCHAR(100) NULL", report);
        ensureColumn(connection, "blog", "created_at", "ALTER TABLE blog ADD COLUMN created_at DATETIME NULL", report);
        ensureColumn(connection, "blog", "published_at", "ALTER TABLE blog ADD COLUMN published_at DATETIME NULL", report);
        ensureColumn(connection, "blog", "publisher_id", "ALTER TABLE blog ADD COLUMN publisher_id INT NULL", report);
        ensureColumn(connection, "blog", "likes_count", "ALTER TABLE blog ADD COLUMN likes_count INT NOT NULL DEFAULT 0", report);
        ensureColumn(connection, "blog", "comments_count", "ALTER TABLE blog ADD COLUMN comments_count INT NOT NULL DEFAULT 0", report);
        ensureColumn(
            connection,
            "blog",
            "is_status_notif_read",
            "ALTER TABLE blog ADD COLUMN is_status_notif_read TINYINT(1) NOT NULL DEFAULT 0",
            report
        );
        ensureColumn(connection, "blog", "shared_from_id", "ALTER TABLE blog ADD COLUMN shared_from_id INT NULL", report);
        ensureColumn(connection, "blog", "views", "ALTER TABLE blog ADD COLUMN views INT NOT NULL DEFAULT 0", report);

        if (addedStatus) {
            executeUpdate(connection, "UPDATE blog SET status = 'published' WHERE status IS NULL OR TRIM(status) = ''");
            report.add("Statut des anciens articles initialise a `published`.");
        }
    }

    private static void ensureInteractionColumns(Connection connection, List<String> report) throws SQLException {
        ensureColumn(connection, "interaction", "reaction", "ALTER TABLE interaction ADD COLUMN reaction INT NULL", report);
        ensureColumn(connection, "interaction", "created_at", "ALTER TABLE interaction ADD COLUMN created_at DATETIME NULL", report);
        ensureColumn(connection, "interaction", "blog_id", "ALTER TABLE interaction ADD COLUMN blog_id INT NULL", report);
        ensureColumn(connection, "interaction", "parent_id", "ALTER TABLE interaction ADD COLUMN parent_id INT NULL", report);
        ensureColumn(
            connection,
            "interaction",
            "is_notif_read",
            "ALTER TABLE interaction ADD COLUMN is_notif_read TINYINT(1) NOT NULL DEFAULT 0",
            report
        );
        ensureColumn(
            connection,
            "interaction",
            "is_flagged",
            "ALTER TABLE interaction ADD COLUMN is_flagged TINYINT(1) NOT NULL DEFAULT 0",
            report
        );
        ensureColumn(
            connection,
            "interaction",
            "is_deleted_by_admin",
            "ALTER TABLE interaction ADD COLUMN is_deleted_by_admin TINYINT(1) NOT NULL DEFAULT 0",
            report
        );
        ensureColumn(
            connection,
            "interaction",
            "is_favorite",
            "ALTER TABLE interaction ADD COLUMN is_favorite TINYINT(1) NOT NULL DEFAULT 0",
            report
        );
    }

    private static void backfillInteractionColumns(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.columnExists(connection, "interaction", "interactor_id")
            && DatabaseSchemaUtils.columnExists(connection, "interaction", "innteractor_id")) {
            int updated = executeUpdate(
                connection,
                "UPDATE interaction SET innteractor_id = interactor_id "
                    + "WHERE innteractor_id IS NULL AND interactor_id IS NOT NULL"
            );
            if (updated > 0) {
                report.add("Valeurs migrees de `interaction.interactor_id` vers `interaction.innteractor_id`: " + updated);
            }
        }

        if (DatabaseSchemaUtils.columnExists(connection, "interaction", "commentaire")
            && DatabaseSchemaUtils.columnExists(connection, "interaction", "comment")) {
            int updated = executeUpdate(
                connection,
                "UPDATE interaction SET comment = commentaire "
                    + "WHERE (comment IS NULL OR comment = '') AND commentaire IS NOT NULL AND commentaire <> ''"
            );
            if (updated > 0) {
                report.add("Valeurs migrees de `interaction.commentaire` vers `interaction.comment`: " + updated);
            }
        }

        if (DatabaseSchemaUtils.columnExists(connection, "interaction", "reaction_type")
            && DatabaseSchemaUtils.columnExists(connection, "interaction", "reaction")) {
            int updated = executeUpdate(
                connection,
                "UPDATE interaction SET reaction = reaction_type "
                    + "WHERE reaction IS NULL AND reaction_type IS NOT NULL"
            );
            if (updated > 0) {
                report.add("Valeurs migrees de `interaction.reaction_type` vers `interaction.reaction`: " + updated);
            }
        }

        executeUpdate(connection, "UPDATE interaction SET is_notif_read = 0 WHERE is_notif_read IS NULL");
        executeUpdate(connection, "UPDATE interaction SET is_flagged = 0 WHERE is_flagged IS NULL");
        executeUpdate(connection, "UPDATE interaction SET is_deleted_by_admin = 0 WHERE is_deleted_by_admin IS NULL");
        executeUpdate(connection, "UPDATE interaction SET is_favorite = 0 WHERE is_favorite IS NULL");
    }

    private static void backfillBlogColumns(Connection connection, List<String> report) throws SQLException {
        if (DatabaseSchemaUtils.columnExists(connection, "blog", "images")
            && DatabaseSchemaUtils.columnExists(connection, "blog", "category")) {
            int updated = executeUpdate(
                connection,
                "UPDATE blog SET category = images "
                    + "WHERE (category IS NULL OR category = '') "
                    + "AND images IN ('mathematics', 'science', 'computer-science', 'study-tips', "
                    + "'language', 'math', 'physique', 'informatique', 'langue', 'autre')"
            );
            if (updated > 0) {
                report.add("Categorie blog restauree depuis `images`: " + updated);
            }
        }

        executeUpdate(connection, "UPDATE blog SET likes_count = 0 WHERE likes_count IS NULL");
        executeUpdate(connection, "UPDATE blog SET comments_count = 0 WHERE comments_count IS NULL");
        executeUpdate(connection, "UPDATE blog SET views = 0 WHERE views IS NULL");
        executeUpdate(connection, "UPDATE blog SET is_status_notif_read = 0 WHERE is_status_notif_read IS NULL");

        if (DatabaseSchemaUtils.columnExists(connection, "blog", "created_at")) {
            executeUpdate(connection, "UPDATE blog SET created_at = NOW() WHERE created_at IS NULL");
        }
        if (DatabaseSchemaUtils.columnExists(connection, "blog", "published_at")
            && DatabaseSchemaUtils.columnExists(connection, "blog", "created_at")) {
            executeUpdate(connection, "UPDATE blog SET published_at = created_at WHERE published_at IS NULL");
        }
    }

    private static void migrateFavoritesTable(Connection connection, List<String> report) throws SQLException {
        if (!DatabaseSchemaUtils.tableExists(connection, "favorites")) {
            return;
        }

        int inserted = executeUpdate(
            connection,
            "INSERT INTO interaction (blog_id, innteractor_id, is_favorite, created_at, is_notif_read, is_flagged, is_deleted_by_admin) "
                + "SELECT f.blog_id, f.user_id, 1, COALESCE(f.created_at, NOW()), 0, 0, 0 "
                + "FROM favorites f "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM interaction i "
                + "WHERE i.blog_id = f.blog_id AND i.innteractor_id = f.user_id AND i.is_favorite = 1"
                + ")"
        );
        executeUpdate(connection, "DROP TABLE favorites");
        report.add("Migration de `favorites` vers `interaction.is_favorite` terminee: " + inserted);
    }

    private static void ensureIndexes(Connection connection, List<String> report) throws SQLException {
        ensureIndex(connection, "blog", "idx_blog_publisher_id", "CREATE INDEX idx_blog_publisher_id ON blog(publisher_id)", report);
        ensureIndex(connection, "blog", "idx_blog_status", "CREATE INDEX idx_blog_status ON blog(status)", report);
        ensureIndex(connection, "blog", "idx_blog_created_at", "CREATE INDEX idx_blog_created_at ON blog(created_at)", report);
        ensureIndex(connection, "blog", "idx_blog_shared_from_id", "CREATE INDEX idx_blog_shared_from_id ON blog(shared_from_id)", report);

        ensureIndex(connection, "interaction", "idx_interaction_blog_id", "CREATE INDEX idx_interaction_blog_id ON interaction(blog_id)", report);
        ensureIndex(connection, "interaction", "idx_interaction_innteractor_id", "CREATE INDEX idx_interaction_innteractor_id ON interaction(innteractor_id)", report);
        ensureIndex(connection, "interaction", "idx_interaction_parent_id", "CREATE INDEX idx_interaction_parent_id ON interaction(parent_id)", report);
        ensureIndex(connection, "interaction", "idx_interaction_favorite", "CREATE INDEX idx_interaction_favorite ON interaction(is_favorite)", report);
    }

    private static void rebuildBlogCounters(Connection connection, List<String> report) throws SQLException {
        if (!DatabaseSchemaUtils.tableExists(connection, "blog") || !DatabaseSchemaUtils.tableExists(connection, "interaction")) {
            return;
        }

        int likesUpdated = executeUpdate(
            connection,
            "UPDATE blog b "
                + "SET likes_count = ("
                + "SELECT COUNT(*) FROM interaction i "
                + "WHERE i.blog_id = b.id AND i.reaction IS NOT NULL AND i.reaction <> 0)"
        );
        int commentsUpdated = executeUpdate(
            connection,
            "UPDATE blog b "
                + "SET comments_count = ("
                + "SELECT COUNT(*) FROM interaction i "
                + "WHERE i.blog_id = b.id AND i.comment IS NOT NULL AND i.comment <> '')"
        );

        report.add("Compteurs likes recalcules pour " + likesUpdated + " article(s).");
        report.add("Compteurs commentaires recalcules pour " + commentsUpdated + " article(s).");
    }

    private static boolean ensureColumn(
        Connection connection,
        String tableName,
        String columnName,
        String sql,
        List<String> report
    ) throws SQLException {
        if (DatabaseSchemaUtils.columnExists(connection, tableName, columnName)) {
            return false;
        }

        DatabaseSchemaUtils.executeDdl(connection, sql);
        report.add("Colonne `" + tableName + "." + columnName + "` ajoutee.");
        return true;
    }

    private static void ensureIndex(
        Connection connection,
        String tableName,
        String indexName,
        String sql,
        List<String> report
    ) throws SQLException {
        if (indexExists(connection, tableName, indexName)) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(connection, sql);
        report.add("Index `" + indexName + "` cree.");
    }

    private static boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
            while (indexes.next()) {
                String currentName = indexes.getString("INDEX_NAME");
                if (currentName != null && currentName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }
}
