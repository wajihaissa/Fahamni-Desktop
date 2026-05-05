package tn.esprit.fahamni.Models;

public class FlashcardAttempt {

    private int id;
    private String question;
    private String userAnswer;
    private String aiFeedback;
    private boolean isCorrect;
    private int subjectId;
    private int sectionId;
    private String expectedAnswer;

    public FlashcardAttempt() {
    }

    public FlashcardAttempt(String question, String userAnswer, String aiFeedback, boolean isCorrect, int subjectId, int sectionId, String expectedAnswer) {
        this.question = question;
        this.userAnswer = userAnswer;
        this.aiFeedback = aiFeedback;
        this.isCorrect = isCorrect;
        this.subjectId = subjectId;
        this.sectionId = sectionId;
        this.expectedAnswer = expectedAnswer;
    }

    public FlashcardAttempt(int id, String question, String userAnswer, String aiFeedback, boolean isCorrect, int subjectId, int sectionId, String expectedAnswer) {
        this.id = id;
        this.question = question;
        this.userAnswer = userAnswer;
        this.aiFeedback = aiFeedback;
        this.isCorrect = isCorrect;
        this.subjectId = subjectId;
        this.sectionId = sectionId;
        this.expectedAnswer = expectedAnswer;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public void setAiFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    public boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public int getSectionId() {
        return sectionId;
    }

    public void setSectionId(int sectionId) {
        this.sectionId = sectionId;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }
}
