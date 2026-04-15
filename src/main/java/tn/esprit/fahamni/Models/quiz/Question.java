package tn.esprit.fahamni.Models.quiz;

import java.util.ArrayList;
import java.util.List;

public class Question {
    private Long id;
    private String question;
    private Quiz quiz;
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
