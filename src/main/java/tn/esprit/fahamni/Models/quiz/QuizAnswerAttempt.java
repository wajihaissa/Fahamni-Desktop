package tn.esprit.fahamni.Models.quiz;

import java.time.Instant;

public class QuizAnswerAttempt {
    private Long id;
    private Long quizResultId;
    private Long questionId;
    private Long selectedChoiceId;
    private Boolean correct;
    private Instant answeredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQuizResultId() {
        return quizResultId;
    }

    public void setQuizResultId(Long quizResultId) {
        this.quizResultId = quizResultId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getSelectedChoiceId() {
        return selectedChoiceId;
    }

    public void setSelectedChoiceId(Long selectedChoiceId) {
        this.selectedChoiceId = selectedChoiceId;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }
}
