package tn.esprit.fahamni.Models.quiz;

import java.util.ArrayList;
import java.util.List;

public class Question {
    private Long id;
    private Long sourceQuestionId;
    private String question;
    private Quiz quiz;
    private String topic;
    private String difficulty;
    private String hint;
    private String explanation;
    private final List<Choice> choices;

    public Question() {
        this.choices = new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceQuestionId() {
        return sourceQuestionId;
    }

    public void setSourceQuestionId(Long sourceQuestionId) {
        this.sourceQuestionId = sourceQuestionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
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

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void addChoice(Choice choice) {
        if (choice != null && !choices.contains(choice)) {
            choices.add(choice);
            choice.setQuestion(this);
        }
    }

    public void removeChoice(Choice choice) {
        if (choices.remove(choice) && choice != null && choice.getQuestion() == this) {
            choice.setQuestion(null);
        }
    }
}
