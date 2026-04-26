package tn.esprit.fahamni.services.conference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;
import tn.esprit.fahamni.utils.MyDataBase;

public class CallRoomService {

    private static final int MAX_CODE_RETRIES = 20;
    private final Connection cnx;
    private final Random random = new Random();

    public CallRoomService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public String createRoom(String hostIp, int hostPort) {
        if (cnx == null) {
            throw new RuntimeException("Database connection is not available");
        }

        String insert = "INSERT INTO call_rooms (room_code, host_ip, host_video_port, status) VALUES (?, ?, ?, 'WAITING')";

        for (int i = 0; i < MAX_CODE_RETRIES; i++) {
            String roomCode = generateCode();
            try (PreparedStatement pst = cnx.prepareStatement(insert)) {
                pst.setString(1, roomCode);
                pst.setString(2, hostIp);
                pst.setInt(3, hostPort);
                pst.executeUpdate();
                return roomCode;
            } catch (SQLException e) {
                // 1062 = duplicate key (room code collision). Retry with a new code.
                if (e.getErrorCode() == 1062) {
                    continue;
                }
                throw new RuntimeException("Failed to create room: " + e.getMessage(), e);
            }
        }

        throw new RuntimeException("Could not generate a unique room code. Please retry.");
    }

    public PeerEndpoint joinRoom(String roomCode, String guestIp, int guestPort) {
        if (cnx == null) {
            throw new RuntimeException("Database connection is not available");
        }

        String select = "SELECT host_ip, host_video_port, status FROM call_rooms WHERE room_code = ?";
        String update = "UPDATE call_rooms SET guest_ip = ?, guest_video_port = ?, status = 'ACTIVE' WHERE room_code = ? AND status = 'WAITING'";

        try (PreparedStatement selectPst = cnx.prepareStatement(select)) {
            selectPst.setString(1, roomCode);

            try (ResultSet rs = selectPst.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String status = rs.getString("status");
                if (!"WAITING".equalsIgnoreCase(status)) {
                    return null;
                }

                String hostIp = rs.getString("host_ip");
                int hostPort = rs.getInt("host_video_port");

                try (PreparedStatement updatePst = cnx.prepareStatement(update)) {
                    updatePst.setString(1, guestIp);
                    updatePst.setInt(2, guestPort);
                    updatePst.setString(3, roomCode);
                    int updatedRows = updatePst.executeUpdate();
                    if (updatedRows == 0) {
                        return null;
                    }
                }

                return new PeerEndpoint(hostIp, hostPort);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to join room: " + e.getMessage(), e);
        }
    }

    public PeerEndpoint pollForGuest(String roomCode) {
        if (cnx == null) {
            throw new RuntimeException("Database connection is not available");
        }

        String query = "SELECT guest_ip, guest_video_port, status FROM call_rooms WHERE room_code = ?";

        try (PreparedStatement pst = cnx.prepareStatement(query)) {
            pst.setString(1, roomCode);

            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String status = rs.getString("status");
                String guestIp = rs.getString("guest_ip");
                int guestPort = rs.getInt("guest_video_port");

                if (!"ACTIVE".equalsIgnoreCase(status) || guestIp == null || guestIp.isBlank() || guestPort <= 0) {
                    return null;
                }

                return new PeerEndpoint(guestIp, guestPort);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed while polling room: " + e.getMessage(), e);
        }
    }

    private String generateCode() {
        int value = random.nextInt(900000) + 100000;
        return String.valueOf(value);
    }

    public static class PeerEndpoint {
        private final String ip;
        private final int videoPort;

        public PeerEndpoint(String ip, int videoPort) {
            this.ip = ip;
            this.videoPort = videoPort;
        }

        public String getIp() {
            return ip;
        }

        public int getVideoPort() {
            return videoPort;
        }
    }
}
