package tn.esprit.fahamni.Models.quiz;


public class Choice {
    private Long id;
    private String choice;
    private Boolean isCorrect;
    private Question question;

    public Choice() {
    }

    public Choice(String choice, Boolean isCorrect) {
        this.choice = choice;
        this.isCorrect = isCorrect;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChoice() {
        return choice;
    }

    public void setChoice(String choice) {
        this.choice = choice;
    }

    public boolean hasChoiceText() {
        return choice != null && !choice.trim().isEmpty();
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public boolean isMarkedCorrect() {
        return Boolean.TRUE.equals(isCorrect);
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }
}
