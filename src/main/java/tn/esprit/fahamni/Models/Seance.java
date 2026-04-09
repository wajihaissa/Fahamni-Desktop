package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Seance {

    private static final DateTimeFormatter SCHEDULE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private int id;
    private String matiere;
    private LocalDateTime startAt;
    private int durationMin;
    private int maxParticipants;
    private int status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int tuteurId;

    // Optional display helpers kept for current mock views.
    private String displayTitle;
    private String displayTutorName;
    private String displaySchedule;
    private Integer availableSpots;
    private Double price;
    private Double rating;

    public Seance() {
    }

    public Seance(int id, String matiere, LocalDateTime startAt, int durationMin,
                  int maxParticipants, int status, String description,
                  LocalDateTime createdAt, LocalDateTime updatedAt, int tuteurId) {
        this.id = id;
        this.matiere = matiere;
        this.startAt = startAt;
        this.durationMin = durationMin;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tuteurId = tuteurId;
    }

    public Seance(String title, String subject, String tutorName, String schedule, int durationMinutes,
                  int availableSpots, int totalSpots, double price, double rating, String description) {
        this.displayTitle = title;
        this.matiere = subject;
        this.displayTutorName = tutorName;
        this.displaySchedule = schedule;
        this.durationMin = durationMinutes;
        this.availableSpots = availableSpots;
        this.maxParticipants = totalSpots;
        this.price = price;
        this.rating = rating;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMatiere() {
        return matiere;
    }

    public void setMatiere(String matiere) {
        this.matiere = matiere;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(int durationMin) {
        this.durationMin = durationMin;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTuteurId() {
        return tuteurId;
    }

    public void setTuteurId(int tuteurId) {
        this.tuteurId = tuteurId;
    }

    public String getTitle() {
        return displayTitle != null ? displayTitle : matiere;
    }

    public void setTitle(String title) {
        this.displayTitle = title;
    }

    public String getSubject() {
        return matiere;
    }

    public void setSubject(String subject) {
        this.matiere = subject;
    }

    public String getTutorName() {
        if (displayTutorName != null && !displayTutorName.isBlank()) {
            return displayTutorName;
        }
        return tuteurId > 0 ? "Tuteur #" + tuteurId : "Tuteur non defini";
    }

    public void setTutorName(String tutorName) {
        this.displayTutorName = tutorName;
    }

    public String getSchedule() {
        if (displaySchedule != null && !displaySchedule.isBlank()) {
            return displaySchedule;
        }
        return startAt != null ? startAt.format(SCHEDULE_FORMATTER) : "";
    }

    public void setSchedule(String schedule) {
        this.displaySchedule = schedule;
    }

    public int getDurationMinutes() {
        return durationMin;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMin = durationMinutes;
    }

    public int getAvailableSpots() {
        return availableSpots != null ? availableSpots : maxParticipants;
    }

    public void setAvailableSpots(int availableSpots) {
        this.availableSpots = availableSpots;
    }

    public int getTotalSpots() {
        return maxParticipants;
    }

    public void setTotalSpots(int totalSpots) {
        this.maxParticipants = totalSpots;
    }

    public double getPrice() {
        return price != null ? price : 0.0;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getRating() {
        return rating != null ? rating : 0.0;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}
