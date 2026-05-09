package tn.esprit.fahamni.Models.quiz;

import java.util.ArrayList;
import java.util.List;

public class Question {
    public static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";
    public static final String TYPE_CODE = "code";
    public static final String TYPE_CODE_OUTPUT = "code_output";
    public static final String CODE_EVALUATION_STRICT = "strict";
    public static final String CODE_EVALUATION_AI = "ai";

    private Long id;
    private Long sourceQuestionId;
    private String question;
    private Quiz quiz;
    private String topic;
    private String difficulty;
    private String hint;
    private String explanation;
    private String questionType;
    private String codeLanguage;
    private String starterCode;
    private String expectedAnswer;
    private String codeEvaluationMode;
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

    public boolean hasQuestionText() {
        return question != null && !question.trim().isEmpty();
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

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getCodeLanguage() {
        return codeLanguage;
    }

    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public void setStarterCode(String starterCode) {
        this.starterCode = starterCode;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getCodeEvaluationMode() {
        return codeEvaluationMode;
    }

    public void setCodeEvaluationMode(String codeEvaluationMode) {
        this.codeEvaluationMode = codeEvaluationMode;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public boolean hasChoices() {
        return !choices.isEmpty();
    }

    public boolean hasStarterCode() {
        return starterCode != null && !starterCode.trim().isEmpty();
    }

    public boolean isCodeQuestion() {
        return TYPE_CODE.equalsIgnoreCase(getNormalizedQuestionType());
    }

    public boolean isCodeOutputQuestion() {
        return TYPE_CODE_OUTPUT.equalsIgnoreCase(getNormalizedQuestionType());
    }

    public boolean isMultipleChoiceQuestion() {
        return !isCodeQuestion();
    }

    public boolean usesCodeSnippetPrompt() {
        return isCodeQuestion() || isCodeOutputQuestion();
    }

    public boolean looksLikeCodeOutputPrompt() {
        if (question == null) {
            return false;
        }
        String normalized = question.trim().toLowerCase();
        return normalized.contains("output of the following code")
                || normalized.contains("output of this code")
                || normalized.contains("result of the following code")
                || normalized.contains("result of this code")
                || normalized.contains("what does the following code output")
                || normalized.contains("what does this code output")
                || normalized.contains("what will the following code output")
                || normalized.contains("what will this code output")
                || normalized.contains("code output when executed")
                || normalized.contains("output when executed")
                || normalized.contains("output when run")
                || normalized.contains("following code output")
                || normalized.contains("sortie du code")
                || normalized.contains("sortie de ce code")
                || normalized.contains("resultat du code")
                || normalized.contains("resultat de ce code");
    }

    public boolean requiresCodeSnippet() {
        return isCodeOutputQuestion() || looksLikeCodeOutputPrompt();
    }

    public String getNormalizedQuestionType() {
        if (questionType == null || questionType.isBlank()) {
            return TYPE_MULTIPLE_CHOICE;
        }
        String normalized = questionType.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        if (TYPE_CODE.equals(normalized)) {
            return TYPE_CODE;
        }
        if (TYPE_CODE_OUTPUT.equals(normalized) || "output".equals(normalized) || "codeoutput".equals(normalized)) {
            return TYPE_CODE_OUTPUT;
        }
        return TYPE_MULTIPLE_CHOICE;
    }

    public String getNormalizedCodeEvaluationMode() {
        if (codeEvaluationMode == null || codeEvaluationMode.isBlank()) {
            return CODE_EVALUATION_STRICT;
        }
        String normalized = codeEvaluationMode.trim().toLowerCase();
        return CODE_EVALUATION_AI.equals(normalized) ? CODE_EVALUATION_AI : CODE_EVALUATION_STRICT;
    }

    public boolean usesAiCodeEvaluation() {
        return isCodeQuestion() && CODE_EVALUATION_AI.equals(getNormalizedCodeEvaluationMode());
    }

    public long getCorrectChoiceCount() {
        return choices.stream()
                .filter(Choice::isMarkedCorrect)
                .count();
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
