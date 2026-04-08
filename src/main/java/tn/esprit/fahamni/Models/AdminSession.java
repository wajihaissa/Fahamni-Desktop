package tn.esprit.fahamni.Models;

public class AdminSession {

    private String subject;
    private String tutor;
    private String schedule;
    private int capacity;
    private String status;

    public AdminSession(String subject, String tutor, String schedule, int capacity, String status) {
        this.subject = subject;
        this.tutor = tutor;
        this.schedule = schedule;
        this.capacity = capacity;
        this.status = status;
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
}

