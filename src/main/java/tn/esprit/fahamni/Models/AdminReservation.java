package tn.esprit.fahamni.Models;

import java.time.LocalDate;

public class AdminReservation {

    private int id;
    private String studentName;
    private String sessionTitle;
    private String requestDate;
    private LocalDate requestLocalDate;
    private String status;
    private int statusCode;
    private boolean cancelled;

    public AdminReservation(String studentName, String sessionTitle, String requestDate, String status) {
        this.studentName = studentName;
        this.sessionTitle = sessionTitle;
        this.requestDate = requestDate;
        this.status = status;
    }

    public AdminReservation(int id, String studentName, String sessionTitle, String requestDate,
                            String status, int statusCode, boolean cancelled) {
        this(id, studentName, sessionTitle, requestDate, null, status, statusCode, cancelled);
    }

    public AdminReservation(int id, String studentName, String sessionTitle, String requestDate,
                            LocalDate requestLocalDate, String status, int statusCode, boolean cancelled) {
        this.id = id;
        this.studentName = studentName;
        this.sessionTitle = sessionTitle;
        this.requestDate = requestDate;
        this.requestLocalDate = requestLocalDate;
        this.status = status;
        this.statusCode = statusCode;
        this.cancelled = cancelled;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public LocalDate getRequestLocalDate() {
        return requestLocalDate;
    }

    public void setRequestLocalDate(LocalDate requestLocalDate) {
        this.requestLocalDate = requestLocalDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

