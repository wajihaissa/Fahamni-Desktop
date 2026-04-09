package tn.esprit.fahamni.Models;

public class Reservation {

    private String sessionTitle;
    private String tutorName;
    private String schedule;
    private String location;
    private double price;
    private String status;
    private boolean upcoming;
    private Integer rating;

    public Reservation(String sessionTitle, String tutorName, String schedule, String location,
                       double price, String status, boolean upcoming, Integer rating) {
        this.sessionTitle = sessionTitle;
        this.tutorName = tutorName;
        this.schedule = schedule;
        this.location = location;
        this.price = price;
        this.status = status;
        this.upcoming = upcoming;
        this.rating = rating;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

