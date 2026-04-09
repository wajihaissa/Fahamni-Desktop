package tn.esprit.fahamni.Models;

public class Quiz {

    private String title;
    private String subject;
    private int questionCount;
    private int durationMinutes;
    private String difficulty;
    private String description;
    private double rating;
    private Integer lastScore;

    public Quiz(String title, String subject, int questionCount, int durationMinutes,
                String difficulty, String description, double rating, Integer lastScore) {
        this.title = title;
        this.subject = subject;
        this.questionCount = questionCount;
        this.durationMinutes = durationMinutes;
        this.difficulty = difficulty;
        this.description = description;
        this.rating = rating;
        this.lastScore = lastScore;
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

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public Integer getLastScore() {
        return lastScore;
    }

    public void setLastScore(Integer lastScore) {
        this.lastScore = lastScore;
    }
}

