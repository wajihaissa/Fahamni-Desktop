package tn.esprit.fahamni.Models;

public class AdminSession {

    private int id;
    private String subject;
    private String tutor;
    private String schedule;
    private int capacity;
    private String status;
    private int tutorId;
    private int durationMinutes;
    private String description;

    public AdminSession(int id, String subject, String tutor, String schedule, int capacity, String status,
                        int tutorId, int durationMinutes, String description) {
        this.id = id;
        this.subject = subject;
        this.tutor = tutor;
        this.schedule = schedule;
        this.capacity = capacity;
        this.status = status;
        this.tutorId = tutorId;
        this.durationMinutes = durationMinutes;
        this.description = description;
    }

    public AdminSession(String subject, String tutor, String schedule, int capacity, String status) {
        this(0, subject, tutor, schedule, capacity, status, 0, 60, "");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTutor() {
        return tutor;
    }

    public void setTutor(String tutor) {
        this.tutor = tutor;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTutorId() {
        return tutorId;
    }

    public void setTutorId(int tutorId) {
        this.tutorId = tutorId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
