package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.ActivityLog;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogService {

    public ActivityLogService() {
        ensureTable();
    }

    private Connection cnx() {
        return MyDataBase.getInstance().getCnx();
    }

    private void ensureTable() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute(
                "CREATE TABLE IF NOT EXISTS activity_log (" +
                "  id             INT AUTO_INCREMENT PRIMARY KEY," +
                "  admin_name     VARCHAR(150) NOT NULL," +
                "  action         VARCHAR(50)  NOT NULL," +
                "  article_title  VARCHAR(300) NOT NULL," +
                "  article_author VARCHAR(150) DEFAULT ''," +
                "  created_at     DATETIME     DEFAULT NOW()" +
                ")"
            );
        } catch (Exception e) {
            System.err.println("ActivityLogService.ensureTable: " + e.getMessage());
        }
    }

    /** Enregistre une action admin */
    public void log(String adminName, String action, String articleTitle, String articleAuthor) {
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO activity_log (admin_name, action, article_title, article_author, created_at) " +
                "VALUES (?, ?, ?, ?, NOW())")) {
            ps.setString(1, adminName != null ? adminName : "Admin");
            ps.setString(2, action);
            ps.setString(3, articleTitle != null ? articleTitle : "—");
            ps.setString(4, articleAuthor != null ? articleAuthor : "—");
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ActivityLogService.log: " + e.getMessage());
        }
    }

    /** Retourne les N dernières entrées du journal */
    public List<ActivityLog> getRecent(int limit) {
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<ActivityLog> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM activity_log ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ActivityLog log = new ActivityLog();
                log.setId(rs.getInt("id"));
                log.setAdminName(rs.getString("admin_name"));
                log.setAction(rs.getString("action"));
                log.setArticleTitle(rs.getString("article_title"));
                log.setArticleAuthor(rs.getString("article_author"));
                log.setCreatedAt(rs.getTimestamp("created_at") != null
                    ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now());
                result.add(log);
            }
        } catch (Exception e) {
            System.err.println("ActivityLogService.getRecent: " + e.getMessage());
        }
        return result;
    }
}
