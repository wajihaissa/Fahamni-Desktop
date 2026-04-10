package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Notification;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gère les notifications :
 *  - recipient_id = 0  → notification pour l'administrateur
 *  - recipient_id > 0  → notification pour un tuteur (son user_id)
 */
public class NotificationService {

    public NotificationService() {
        ensureTable();
    }

    private Connection cnx() {
        return MyDataBase.getInstance().getCnx();
    }

    /** Crée la table notification si elle n'existe pas */
    private void ensureTable() {
        Connection c = cnx();
        if (c == null) return;
        try (Statement st = c.createStatement()) {
            st.execute(
                "CREATE TABLE IF NOT EXISTS notification (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY, " +
                "  recipient_id INT NOT NULL DEFAULT 0 COMMENT '0=admin, >0=user_id', " +
                "  blog_id      INT DEFAULT NULL, " +
                "  message      VARCHAR(500) NOT NULL, " +
                "  is_read      TINYINT DEFAULT 0, " +
                "  created_at   DATETIME DEFAULT NOW()" +
                ")"
            );
        } catch (Exception e) {
            System.err.println("NotificationService.ensureTable: " + e.getMessage());
        }
    }

    // ─── Création ────────────────────────────────────────────────────────────

    /** Envoi d'une notification à l'administrateur */
    public void createForAdmin(String message, int blogId) {
        insert(0, blogId, message);
    }

    /** Envoi d'une notification à un tuteur */
    public void createForUser(int userId, String message, int blogId) {
        if (userId <= 0) {
            System.err.println("createForUser: userId invalide (" + userId + ")");
            return;
        }
        insert(userId, blogId, message);
    }

    private void insert(int recipientId, int blogId, String message) {
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO notification (recipient_id, blog_id, message, is_read, created_at) " +
                "VALUES (?, ?, ?, 0, NOW())")) {
            ps.setInt(1, recipientId);
            if (blogId > 0) ps.setInt(2, blogId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, message);
            ps.executeUpdate();
            System.out.println("Notification creee pour recipient_id=" + recipientId + " : " + message);
        } catch (Exception e) {
            System.err.println("NotificationService.insert: " + e.getMessage());
        }
    }

    // ─── Lecture ─────────────────────────────────────────────────────────────

    /** Notifications non lues de l'admin */
    public List<Notification> getUnreadForAdmin() {
        return getUnread(0);
    }

    /** Notifications non lues d'un tuteur */
    public List<Notification> getUnreadForUser(int userId) {
        return getUnread(userId);
    }

    /** Toutes les notifications d'un tuteur (lues + non lues), limitées aux 20 dernières */
    public List<Notification> getAllForUser(int userId) {
        return getAll(userId, 20);
    }

    public int countUnreadForAdmin() {
        return countUnread(0);
    }

    public int countUnreadForUser(int userId) {
        return countUnread(userId);
    }

    private List<Notification> getUnread(int recipientId) {
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<Notification> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM notification WHERE recipient_id = ? AND is_read = 0 " +
                "ORDER BY created_at DESC")) {
            ps.setInt(1, recipientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            System.err.println("NotificationService.getUnread: " + e.getMessage());
        }
        return result;
    }

    private List<Notification> getAll(int recipientId, int limit) {
        Connection c = cnx();
        if (c == null) return new ArrayList<>();
        List<Notification> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM notification WHERE recipient_id = ? " +
                "ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, recipientId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            System.err.println("NotificationService.getAll: " + e.getMessage());
        }
        return result;
    }

    private int countUnread(int recipientId) {
        Connection c = cnx();
        if (c == null) return 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM notification WHERE recipient_id = ? AND is_read = 0")) {
            ps.setInt(1, recipientId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            System.err.println("NotificationService.countUnread: " + e.getMessage());
        }
        return 0;
    }

    // ─── Marquer comme lu ────────────────────────────────────────────────────

    public void markAllReadForAdmin() {
        markAllRead(0);
    }

    public void markAllReadForUser(int userId) {
        markAllRead(userId);
    }

    private void markAllRead(int recipientId) {
        Connection c = cnx();
        if (c == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE notification SET is_read = 1 WHERE recipient_id = ? AND is_read = 0")) {
            ps.setInt(1, recipientId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("NotificationService.markAllRead: " + e.getMessage());
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getInt("id"));
        n.setRecipientId(rs.getInt("recipient_id"));
        n.setBlogId(rs.getInt("blog_id"));
        n.setMessage(rs.getString("message"));
        n.setRead(rs.getInt("is_read") == 1);
        n.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : LocalDateTime.now());
        return n;
    }
}
