package tn.esprit.fahamni.Models.quiz;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import tn.esprit.fahamni.Models.User;

public class QuizResult {
    private Long id;
    private User user;
    private Quiz quiz;
    private Integer score;
    private Integer totalQuestions;
    private Double percentage;
    private Boolean passed;
    private Instant completedAt;
    private final List<QuizAnswerAttempt> answerAttempts = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public boolean hasScore() {
        return score != null;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }

    public boolean hasPercentage() {
        return percentage != null;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public boolean isPassed() {
        return Boolean.TRUE.equals(passed);
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<QuizAnswerAttempt> getAnswerAttempts() {
        return answerAttempts;
    }

    public void addAnswerAttempt(QuizAnswerAttempt answerAttempt) {
        if (answerAttempt != null && !answerAttempts.contains(answerAttempt)) {
            answerAttempts.add(answerAttempt);
        }
    }
}
