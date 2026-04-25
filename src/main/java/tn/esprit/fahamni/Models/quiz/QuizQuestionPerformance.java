package tn.esprit.fahamni.Models.quiz;

import java.time.Instant;

public class QuizQuestionPerformance {
    private Long questionId;
    private String topic;
    private String difficulty;
    private int attempts;
    private int correctAnswers;
    private int incorrectAnswers;
    private double accuracyRate;
    private Instant lastAnsweredAt;

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(int correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public int getIncorrectAnswers() {
        return incorrectAnswers;
    }

    public void setIncorrectAnswers(int incorrectAnswers) {
        this.incorrectAnswers = incorrectAnswers;
    }

    public double getAccuracyRate() {
        return accuracyRate;
    }

    public void setAccuracyRate(double accuracyRate) {
        this.accuracyRate = accuracyRate;
    }

    public Instant getLastAnsweredAt() {
        return lastAnsweredAt;
    }

    public void setLastAnsweredAt(Instant lastAnsweredAt) {
        this.lastAnsweredAt = lastAnsweredAt;
    }
}
