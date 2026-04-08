package tn.esprit.fahamni.Models;

public class Seance {

    private String title;
    private String subject;
    private String tutorName;
    private String schedule;
    private int durationMinutes;
    private int availableSpots;
    private int totalSpots;
    private double price;
    private double rating;
    private String description;

    public Seance(String title, String subject, String tutorName, String schedule, int durationMinutes,
                  int availableSpots, int totalSpots, double price, double rating, String description) {
        this.title = title;
        this.subject = subject;
        this.tutorName = tutorName;
        this.schedule = schedule;
        this.durationMinutes = durationMinutes;
        this.availableSpots = availableSpots;
        this.totalSpots = totalSpots;
        this.price = price;
        this.rating = rating;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getAvailableSpots() {
        return availableSpots;
    }

    public void setAvailableSpots(int availableSpots) {
        this.availableSpots = availableSpots;
    }

    public int getTotalSpots() {
        return totalSpots;
    }

    public void setTotalSpots(int totalSpots) {
        this.totalSpots = totalSpots;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

