package tn.esprit.fahamni.Models;

public class AdminDashboardSummary {

    private final int usersCount;
    private final int sessionsCount;
    private final int reservationsCount;
    private final int contentCount;

    public AdminDashboardSummary(int usersCount, int sessionsCount, int reservationsCount, int contentCount) {
        this.usersCount = usersCount;
        this.sessionsCount = sessionsCount;
        this.reservationsCount = reservationsCount;
        this.contentCount = contentCount;
    }

    public int getUsersCount() {
        return usersCount;
    }

    public int getSessionsCount() {
        return sessionsCount;
    }

    public int getReservationsCount() {
        return reservationsCount;
    }

    public int getContentCount() {
        return contentCount;
    }
}

