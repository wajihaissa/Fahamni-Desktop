package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminReservation;
import tn.esprit.fahamni.utils.MyDataBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AdminReservationService {

    private static final String STATUS_PENDING_LABEL = "En attente";
    private static final String STATUS_ACCEPTED_LABEL = "Acceptee";
    private static final String STATUS_REFUSED_LABEL = "Refusee";
    private static final String STATUS_CANCELLED_LABEL = "Annulee";
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ObservableList<AdminReservation> reservations = FXCollections.observableArrayList();
    private final Connection cnx = MyDataBase.getInstance().getCnx();

    public AdminReservationService() {
        reloadReservations();
    }

    public ObservableList<AdminReservation> getReservations() {
        return reservations;
    }

    public void reloadReservations() {
        reservations.setAll(loadReservations());
    }

    private List<AdminReservation> loadReservations() {
        List<AdminReservation> loadedReservations = new ArrayList<>();
        if (cnx == null) {
            return loadedReservations;
        }

        String sql = """
            SELECT
                r.id,
                r.status,
                r.reserved_at,
                r.cancell_at,
                r.seance_id,
                r.participant_id,
                u.full_name,
                u.email,
                s.matiere
            FROM reservation r
            LEFT JOIN user u ON u.id = r.participant_id
            LEFT JOIN seance s ON s.id = r.seance_id
            ORDER BY r.reserved_at DESC, r.id DESC
            """;

        try (PreparedStatement pst = cnx.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                Timestamp cancelledAt = rs.getTimestamp("cancell_at");
                int storedStatus = rs.getInt("status");
                int normalizedStatus = normalizeStoredStatus(storedStatus);
                boolean cancelled = cancelledAt != null;

                loadedReservations.add(new AdminReservation(
                    rs.getInt("id"),
                    resolveStudentLabel(rs),
                    resolveSessionLabel(rs),
                    formatDateTime(rs.getTimestamp("reserved_at")),
                    mapStatusCodeToLabel(normalizedStatus, cancelled),
                    normalizedStatus,
                    cancelled
                ));
            }
        } catch (SQLException e) {
            System.out.println("Erreur chargement reservations admin: " + e.getMessage());
        }
        return loadedReservations;
    }

    private String mapStatusCodeToLabel(int status, boolean cancelled) {
        if (cancelled) {
            return STATUS_CANCELLED_LABEL;
        }
        return switch (status) {
            case ReservationService.STATUS_ACCEPTED -> STATUS_ACCEPTED_LABEL;
            case ReservationService.STATUS_REFUSED -> STATUS_REFUSED_LABEL;
            default -> STATUS_PENDING_LABEL;
        };
    }

    private int normalizeStoredStatus(int status) {
        return status == 2 ? ReservationService.STATUS_ACCEPTED : status;
    }

    private String resolveStudentLabel(ResultSet rs) throws SQLException {
        String fullName = rs.getString("full_name");
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String email = rs.getString("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "Etudiant #" + rs.getInt("participant_id");
    }

    private String resolveSessionLabel(ResultSet rs) throws SQLException {
        String subject = rs.getString("matiere");
        if (subject != null && !subject.isBlank()) {
            return subject + " (#" + rs.getInt("seance_id") + ")";
        }
        return "Seance #" + rs.getInt("seance_id");
    }

    private String formatDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime().format(DISPLAY_FORMATTER) : "";
    }
}
