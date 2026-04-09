package tn.esprit.fahamni.Models;

public class AdminReservation {

    private String studentName;
    private String sessionTitle;
    private String requestDate;
    private String status;

    public AdminReservation(String studentName, String sessionTitle, String requestDate, String status) {
        this.studentName = studentName;
        this.sessionTitle = sessionTitle;
        this.requestDate = requestDate;
        this.status = status;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public String getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(String requestDate) {
        this.requestDate = requestDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

