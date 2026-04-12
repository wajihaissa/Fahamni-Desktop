package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class Reservation {

    private int id;
    private int status;
    private LocalDateTime reservedAt;
    // The current SQL column is named `cancell_at`.
    private LocalDateTime cancelledAt;
    private String notes;
    private int seanceId;
    private int participantId;
    private LocalDateTime confirmationEmailSentAt;
    private LocalDateTime acceptanceEmailSentAt;
    private LocalDateTime reminderEmailSentAt;

    // Optional display helpers kept for the current mock UI flow.
    private String sessionTitle;
    private String tutorName;
    private String schedule;
    private String location;
    private double price;
    private String statusLabel;
    private boolean upcoming;
    private Integer rating;

    public Reservation() {
    }

    public Reservation(int id, int status, LocalDateTime reservedAt, LocalDateTime cancelledAt,
                       String notes, int seanceId, int participantId,
                       LocalDateTime confirmationEmailSentAt,
                       LocalDateTime acceptanceEmailSentAt,
                       LocalDateTime reminderEmailSentAt) {
        this.id = id;
        this.status = status;
        this.reservedAt = reservedAt;
        this.cancelledAt = cancelledAt;
        this.notes = notes;
        this.seanceId = seanceId;
        this.participantId = participantId;
        this.confirmationEmailSentAt = confirmationEmailSentAt;
        this.acceptanceEmailSentAt = acceptanceEmailSentAt;
        this.reminderEmailSentAt = reminderEmailSentAt;
    }

    public Reservation(String sessionTitle, String tutorName, String schedule, String location,
                       double price, String statusLabel, boolean upcoming, Integer rating) {
        this.sessionTitle = sessionTitle;
        this.tutorName = tutorName;
        this.schedule = schedule;
        this.location = location;
        this.price = price;
        this.statusLabel = statusLabel;
        this.upcoming = upcoming;
        this.rating = rating;
        this.reservedAt = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(LocalDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(int seanceId) {
        this.seanceId = seanceId;
    }

    public int getParticipantId() {
        return participantId;
    }

    public void setParticipantId(int participantId) {
        this.participantId = participantId;
    }

    public LocalDateTime getConfirmationEmailSentAt() {
        return confirmationEmailSentAt;
    }

    public void setConfirmationEmailSentAt(LocalDateTime confirmationEmailSentAt) {
        this.confirmationEmailSentAt = confirmationEmailSentAt;
    }

    public LocalDateTime getAcceptanceEmailSentAt() {
        return acceptanceEmailSentAt;
    }

    public void setAcceptanceEmailSentAt(LocalDateTime acceptanceEmailSentAt) {
        this.acceptanceEmailSentAt = acceptanceEmailSentAt;
    }

    public LocalDateTime getReminderEmailSentAt() {
        return reminderEmailSentAt;
    }

    public void setReminderEmailSentAt(LocalDateTime reminderEmailSentAt) {
        this.reminderEmailSentAt = reminderEmailSentAt;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public String getTutorName() {
        return tutorName;
    }

    public void setTutorName(String tutorName) {
        this.tutorName = tutorName;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public boolean isUpcoming() {
        return upcoming;
    }

    public void setUpcoming(boolean upcoming) {
        this.upcoming = upcoming;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
