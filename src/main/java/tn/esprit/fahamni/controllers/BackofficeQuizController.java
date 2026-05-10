package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.services.AiQuizAssistantService;
import tn.esprit.fahamni.services.QuizService;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BackofficeQuizController {
    private static final String QUESTION_MODE_MULTIPLE_CHOICE = "Multiple Choice";
    private static final String QUESTION_MODE_CODE_SNIPPET = "Code Snippet + MCQ";
    private static final String QUESTION_MODE_CODE_EDITOR = "Code Editor";

    @FXML private TableView<Quiz> quizzesTable;
    @FXML private TableColumn<Quiz, String> titleColumn;
    @FXML private TableColumn<Quiz, String> keywordColumn;
    @FXML private TableColumn<Quiz, Integer> questionsColumn;
    @FXML private TableColumn<Quiz, Integer> resultsColumn;
    @FXML private TableColumn<Quiz, String> createdAtColumn;
    @FXML private TableColumn<Quiz, String> lastScoreColumn;

    @FXML private Label totalQuizzesLabel;
    @FXML private Label quizzesWithResultsLabel;
    @FXML private Label averageScoreLabel;

    @FXML private TextField titleField;
    @FXML private TextField keywordField;
    @FXML private TextField questionField;
    @FXML private TextField choice1Field;
    @FXML private TextField choice2Field;
    @FXML private TextField choice3Field;
    @FXML private TextField choice4Field;
    @FXML private TextField codeLanguageField;
    @FXML private TextField codeOutputLanguageField;
    @FXML private TextArea starterCodeArea;
    @FXML private TextArea expectedAnswerArea;
    @FXML private TextArea codeOutputArea;
    @FXML private VBox multipleChoiceEditorBox;
    @FXML private VBox codeOutputEditorBox;
    @FXML private VBox codeEditorBox;

    @FXML private ComboBox<String> questionTypeComboBox;
    @FXML private ComboBox<String> codeEvaluationModeComboBox;
    @FXML private ComboBox<String> correctChoiceComboBox;
    @FXML private ComboBox<Integer> aiQuestionCountComboBox;
    @FXML private ComboBox<String> aiDifficultyComboBox;
    @FXML private Label feedbackLabel;
    @FXML private Label questionsAddedLabel;
    @FXML private ListView<String> questionsListView;

    private final QuizService quizService = new QuizService();
    private final AiQuizAssistantService aiQuizAssistantService = new AiQuizAssistantService();
    private final ObservableList<Quiz> quizItems = FXCollections.observableArrayList();
    private final List<Question> currentQuestions = new ArrayList<>();
    private Quiz selectedQuiz = null;
    private int selectedQuestionIndex = -1;
    private static final int MAX_QUESTIONS = 6;
    private static final int MIN_TITLE_LENGTH = 3;
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MIN_QUESTION_LENGTH = 10;

    @FXML
    private void initialize() {
        quizzesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        quizzesTable.setFixedCellSize(40);

        titleColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTitre()));

        keywordColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getKeyword()));

        questionsColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getQuestions().size()).asObject());

        resultsColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getQuizResults().size()).asObject());

        createdAtColumn.setCellValueFactory(cell -> {
            Instant createdAt = cell.getValue().getCreatedAt();
            if (createdAt == null) {
                return new SimpleStringProperty("-");
            }
            return new SimpleStringProperty(
                    createdAt.atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
            );
        });

        lastScoreColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(getLastScoreLabel(cell.getValue())));

        titleColumn.setCellFactory(tc -> createCell(Pos.CENTER_LEFT));
        keywordColumn.setCellFactory(tc -> createCell(Pos.CENTER_LEFT));
        questionsColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        resultsColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        createdAtColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        lastScoreColumn.setCellFactory(tc -> createCell(Pos.CENTER));

        questionTypeComboBox.getItems().setAll(
                QUESTION_MODE_MULTIPLE_CHOICE,
                QUESTION_MODE_CODE_SNIPPET,
                QUESTION_MODE_CODE_EDITOR
        );
        questionTypeComboBox.setValue(QUESTION_MODE_MULTIPLE_CHOICE);
        questionTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateQuestionEditorMode());
        codeEvaluationModeComboBox.getItems().setAll("Strict Syntax", "AI Logic Check");
        codeEvaluationModeComboBox.setValue("AI Logic Check");

        correctChoiceComboBox.getItems().setAll("Choix A", "Choix B", "Choix C", "Choix D");
        correctChoiceComboBox.setValue("Choix A");
        if (aiQuestionCountComboBox != null) {
            aiQuestionCountComboBox.getItems().setAll(3, 4, 5, 6);
            aiQuestionCountComboBox.setValue(6);
        }
        if (aiDifficultyComboBox != null) {
            aiDifficultyComboBox.getItems().setAll("Easy", "Medium", "Hard");
            aiDifficultyComboBox.setValue("Medium");
        }

        quizzesTable.setItems(quizItems);
        quizzesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> populateForm(newVal));
        questionsListView.getSelectionModel().selectedIndexProperty()
                .addListener((obs, oldVal, newVal) -> populateQuestionEditor(newVal == null ? -1 : newVal.intValue()));

        refreshQuizData();
        updateQuestionEditorMode();
    }

    @FXML
    public void handleCreateQuiz(ActionEvent event) {
        String quizValidationError = validateQuizForm();
        if (quizValidationError != null) {
            showFeedback(quizValidationError, true);
            return;
        }

        try {
            if (selectedQuiz != null) {
                Quiz updatedQuiz = buildQuizFromForm();
                quizService.updateQuiz(selectedQuiz.getId(), updatedQuiz);
                showFeedback("Quiz mis a jour avec succes !", false);
            } else {
                Quiz quiz = buildQuizFromForm();
                Quiz savedQuiz = quizService.createQuiz(quiz);
                if (savedQuiz != null && savedQuiz.getId() != null) {
                    showFeedback("Quiz cree avec " + currentQuestions.size() + " question(s) !", false);
                } else {
                    showFeedback("Erreur lors de la sauvegarde du quiz", true);
                    return;
                }
            }

            clearForm();
            refreshQuizData();
        } catch (Exception e) {
            showFeedback("Erreur : " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    @FXML
    public void handleAddQuestion(ActionEvent event) {
        boolean isEditing = selectedQuestionIndex >= 0;

        if (!isEditing && currentQuestions.size() >= MAX_QUESTIONS) {
            showFeedback("Maximum de " + MAX_QUESTIONS + " questions atteint !", true);
            return;
        }

        String questionValidationError = validateQuestionEditor(isEditing);
        if (questionValidationError != null) {
            showFeedback(questionValidationError, true);
            return;
        }

        try {
            Question question = buildQuestionFromFields();
            if (isEditing) {
                currentQuestions.set(selectedQuestionIndex, question);
            } else {
                currentQuestions.add(question);
            }

            updateQuestionsList();
            clearQuestionEditor();
            showFeedback(
                    isEditing
                            ? "Question modifiee ! (" + currentQuestions.size() + "/" + MAX_QUESTIONS + ")"
                            : "Question ajoutee ! (" + currentQuestions.size() + "/" + MAX_QUESTIONS + ")",
                    false
            );
        } catch (Exception e) {
            showFeedback("Erreur : " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleDeleteSelectedQuestion(ActionEvent event) {
        if (selectedQuestionIndex < 0 || selectedQuestionIndex >= currentQuestions.size()) {
            showFeedback("Selectionnez une question dans la liste pour la supprimer", true);
            return;
        }

        currentQuestions.remove(selectedQuestionIndex);
        updateQuestionsList();
        clearQuestionEditor();
        showFeedback("Question supprimee ! (" + currentQuestions.size() + "/" + MAX_QUESTIONS + ")", false);
    }

    @FXML
    public void handleUpdateQuiz(ActionEvent event) {
        if (selectedQuiz == null) {
            showFeedback("Veuillez selectionner un quiz a modifier", true);
            return;
        }

        String quizValidationError = validateQuizForm();
        if (quizValidationError != null) {
            showFeedback(quizValidationError, true);
            return;
        }

        try {
            Quiz updatedQuiz = buildQuizFromForm();
            Quiz result = quizService.updateQuiz(selectedQuiz.getId(), updatedQuiz);
            if (result != null) {
                showFeedback("Quiz mis a jour avec succes !", false);
                clearForm();
                selectedQuiz = null;
                refreshQuizData();
            } else {
                showFeedback("Impossible de mettre a jour le quiz", true);
            }
        } catch (Exception e) {
            showFeedback("Erreur lors de la mise a jour : " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleDeleteQuiz(ActionEvent event) {
        if (selectedQuiz == null) {
            showFeedback("Veuillez selectionner un quiz a supprimer", true);
            return;
        }

        try {
            boolean deleted = quizService.deleteQuiz(selectedQuiz.getId());
            if (deleted) {
                showFeedback("Quiz supprime avec succes !", false);
                clearForm();
                selectedQuiz = null;
                refreshQuizData();
            } else {
                showFeedback("Impossible de supprimer le quiz", true);
            }
        } catch (Exception e) {
            showFeedback("Erreur lors de la suppression : " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        selectedQuiz = null;
        clearForm();
        refreshQuizData();
        showFeedback("Liste rafraichie !", false);
    }

    @FXML
    public void handleGenerateQuizWithAi(ActionEvent event) {
        String title = normalizeText(titleField.getText());
        String keyword = normalizeText(keywordField.getText());
        String topic = keyword != null ? keyword : title;

        if (topic == null || topic.isBlank()) {
            showFeedback("Ajoutez un titre ou un mot-cle avant la generation IA", true);
            return;
        }

        int questionCount = aiQuestionCountComboBox != null && aiQuestionCountComboBox.getValue() != null
                ? aiQuestionCountComboBox.getValue()
                : MAX_QUESTIONS;
        String difficulty = aiDifficultyComboBox != null && aiDifficultyComboBox.getValue() != null
                ? aiDifficultyComboBox.getValue()
                : "Medium";

        AiQuizAssistantService.GeneratedQuizDraft generatedDraft =
                aiQuizAssistantService.generateQuizDraft(topic, title, questionCount, difficulty);
        if (!generatedDraft.success()) {
            showFeedback(generatedDraft.message(), true);
            return;
        }

        Quiz generatedQuiz = generatedDraft.quiz();

        if (generatedQuiz == null || generatedQuiz.getQuestions().isEmpty()) {
            showFeedback("La generation IA a echoue, veuillez reessayer", true);
            return;
        }

        titleField.setText(generatedQuiz.getTitre());
        keywordField.setText(generatedQuiz.getKeyword());
        currentQuestions.clear();
        currentQuestions.addAll(generatedQuiz.getQuestions());
        String generatedQuizError = validateAndNormalizeCurrentQuestions();
        if (generatedQuizError != null) {
            currentQuestions.clear();
            showFeedback("Le quiz genere doit etre corrige avant usage : " + generatedQuizError, true);
            return;
        }
        clearQuestionEditor();
        updateQuestionsList();
        showFeedback(generatedDraft.message(), false);
    }

    private Quiz buildQuizFromForm() {
        Quiz quiz = new Quiz();
        String normalizedTitle = normalizeText(titleField.getText());
        String normalizedKeyword = normalizeText(keywordField.getText());
        quiz.setTitre(normalizedTitle);
        quiz.setKeyword(normalizedKeyword);

        for (Question question : currentQuestions) {
            enrichQuestionMetadata(question, normalizedTitle, normalizedKeyword);
            quiz.addQuestion(question);
        }

        return quiz;
    }

    private Question buildQuestionFromFields() {
        Question question = new Question();
        question.setQuestion(normalizeText(questionField.getText()));
        question.setQuestionType(resolveSelectedQuestionType());

        if (question.isCodeQuestion()) {
            question.setCodeLanguage(normalizeText(codeLanguageField.getText()));
            question.setStarterCode(normalizeMultilineText(starterCodeArea.getText()));
            question.setExpectedAnswer(normalizeMultilineText(expectedAnswerArea.getText()));
            question.setCodeEvaluationMode(resolveSelectedCodeEvaluationMode());
            return question;
        }

        if (question.isCodeOutputQuestion()) {
            question.setCodeLanguage(normalizeText(codeOutputLanguageField.getText()));
            question.setStarterCode(normalizeMultilineText(codeOutputArea.getText()));
        }

        String[] choiceTexts = {
                normalizeText(choice1Field.getText()),
                normalizeText(choice2Field.getText()),
                normalizeText(choice3Field.getText()),
                normalizeText(choice4Field.getText())
        };
        String correctChoice = correctChoiceComboBox.getValue();

        for (int i = 0; i < choiceTexts.length; i++) {
            Choice choice = new Choice();
            choice.setChoice(choiceTexts[i]);
            choice.setIsCorrect(correctChoice.equals("Choix " + (char) ('A' + i)));
            question.addChoice(choice);
        }

        return question;
    }

    private String validateQuizForm() {
        String normalizedTitle = normalizeText(titleField.getText());
        String normalizedKeyword = normalizeText(keywordField.getText());

        titleField.setText(normalizedTitle == null ? "" : normalizedTitle);
        keywordField.setText(normalizedKeyword == null ? "" : normalizedKeyword);

        if (normalizedTitle == null || normalizedKeyword == null) {
            return "Veuillez remplir le titre et le mot-cle.";
        }
        if (normalizedTitle.length() < MIN_TITLE_LENGTH) {
            return "Le titre doit contenir au moins " + MIN_TITLE_LENGTH + " caracteres.";
        }
        if (normalizedKeyword.length() < MIN_KEYWORD_LENGTH) {
            return "Le mot-cle doit contenir au moins " + MIN_KEYWORD_LENGTH + " caracteres.";
        }
        if (!containsLettersOrDigits(normalizedKeyword)) {
            return "Le mot-cle doit contenir des lettres ou des chiffres utiles.";
        }
        if (isDuplicateQuizTitle(normalizedTitle)) {
            return "Un autre quiz utilise deja ce titre.";
        }
        if (currentQuestions.isEmpty()) {
            return "Veuillez ajouter au moins une question.";
        }

        return validateAndNormalizeCurrentQuestions();
    }

    private String validateQuestionEditor(boolean isEditing) {
        String normalizedQuestion = normalizeText(questionField.getText());
        String normalizedChoice1 = normalizeText(choice1Field.getText());
        String normalizedChoice2 = normalizeText(choice2Field.getText());
        String normalizedChoice3 = normalizeText(choice3Field.getText());
        String normalizedChoice4 = normalizeText(choice4Field.getText());

        applyNormalizedQuestionEditorValues(
                normalizedQuestion,
                normalizedChoice1,
                normalizedChoice2,
                normalizedChoice3,
                normalizedChoice4
        );

        if (normalizedQuestion == null) {
            return "Veuillez entrer une question.";
        }
        if (normalizedQuestion.length() < MIN_QUESTION_LENGTH) {
            return "La question doit etre plus precise (au moins " + MIN_QUESTION_LENGTH + " caracteres).";
        }
        if (looksLikePlaceholder(normalizedQuestion)) {
            return "La question semble etre un texte de test. Remplacez-la par un vrai enonce.";
        }

        if (isCodeEditorSelected()) {
            String normalizedCodeLanguage = normalizeText(codeLanguageField.getText());
            String normalizedStarterCode = normalizeMultilineText(starterCodeArea.getText());
            String normalizedExpectedAnswer = normalizeMultilineText(expectedAnswerArea.getText());

            applyNormalizedCodeEditorValues(normalizedCodeLanguage, normalizedStarterCode, normalizedExpectedAnswer);

            if (normalizedExpectedAnswer == null) {
                return "Ajoutez la reponse de reference pour corriger la question de code.";
            }
            if (normalizedExpectedAnswer.length() < 2) {
                return "La reponse de reference doit contenir du vrai code.";
            }
            if (Question.CODE_EVALUATION_AI.equals(resolveSelectedCodeEvaluationMode())
                    && (normalizedCodeLanguage == null || normalizedCodeLanguage.length() < 2)) {
                return "Precisez le langage pour permettre la correction IA.";
            }
            if (hasDuplicateQuestion(normalizedQuestion, isEditing ? selectedQuestionIndex : -1)) {
                return "Cette question existe deja dans ce quiz.";
            }
            return null;
        }

        if (isCodeOutputSelected()) {
            String normalizedCodeLanguage = normalizeText(codeOutputLanguageField.getText());
            String normalizedCodeOutput = normalizeMultilineText(codeOutputArea.getText());
            applyNormalizedCodeOutputValues(normalizedCodeLanguage, normalizedCodeOutput);

            if (normalizedCodeOutput == null) {
                return "Ajoutez le code dont l'etudiant doit predire la sortie.";
            }
            if (normalizedCodeOutput.length() < 2) {
                return "Le code affiche doit contenir une vraie instruction.";
            }
        }

        List<String> normalizedChoices = List.of(normalizedChoice1, normalizedChoice2, normalizedChoice3, normalizedChoice4);
        boolean requiresCodeSnippet = aiQuizAssistantService.requiresCodeSnippet(normalizedQuestion, normalizedChoices);
        if (requiresCodeSnippet && !isCodeOutputSelected()) {
            return "Cette question parle d'un code a analyser. Utilisez le mode Code Snippet + MCQ et ajoutez le code a afficher.";
        }

        if (normalizedChoices.stream().anyMatch(choice -> choice == null || choice.isBlank())) {
            return "Veuillez remplir tous les choix.";
        }
        if (normalizedChoices.stream().anyMatch(this::looksLikePlaceholderChoice)) {
            return "Remplacez les choix generiques par de vraies reponses.";
        }

        Set<String> uniqueChoices = new HashSet<>();
        for (String choice : normalizedChoices) {
            if (!uniqueChoices.add(choice.toLowerCase(Locale.ROOT))) {
                return "Les choix d'une meme question doivent etre differents.";
            }
        }

        return hasDuplicateQuestion(normalizedQuestion, isEditing ? selectedQuestionIndex : -1)
                ? "Cette question existe deja dans ce quiz."
                : null;
    }

    private String validateAndNormalizeCurrentQuestions() {
        Set<String> normalizedQuestions = new HashSet<>();

        for (Question question : currentQuestions) {
            if (question == null) {
                return "Une question est invalide.";
            }

            String normalizedQuestion = normalizeText(question.getQuestion());
            question.setQuestion(normalizedQuestion);
            if (normalizedQuestion == null || normalizedQuestion.length() < MIN_QUESTION_LENGTH) {
                return "Chaque question doit contenir au moins " + MIN_QUESTION_LENGTH + " caracteres.";
            }
            if (looksLikePlaceholder(normalizedQuestion)) {
                return "Une question contient encore un texte trop generique.";
            }
            if (!normalizedQuestions.add(normalizedQuestion.toLowerCase(Locale.ROOT))) {
                return "Deux questions du quiz sont identiques.";
            }

            if (question.isCodeQuestion()) {
                question.setQuestionType(Question.TYPE_CODE);
                question.setCodeLanguage(normalizeText(question.getCodeLanguage()));
                question.setStarterCode(normalizeMultilineText(question.getStarterCode()));
                question.setExpectedAnswer(normalizeMultilineText(question.getExpectedAnswer()));
                question.setCodeEvaluationMode(normalizeCodeEvaluationMode(question.getCodeEvaluationMode()));
                if (question.getExpectedAnswer() == null || question.getExpectedAnswer().length() < 2) {
                    return "Chaque question de code doit definir une reponse de reference.";
                }
                continue;
            }

            if (question.isCodeOutputQuestion()) {
                question.setQuestionType(Question.TYPE_CODE_OUTPUT);
                question.setCodeLanguage(normalizeText(question.getCodeLanguage()));
                question.setStarterCode(normalizeMultilineText(question.getStarterCode()));
                if (question.getStarterCode() == null || question.getStarterCode().length() < 2) {
                    return "Chaque question de sortie de code doit afficher un extrait de code.";
                }
            }

            if (aiQuizAssistantService.requiresCodeSnippet(
                    question.getQuestion(),
                    question.getChoices().stream().map(Choice::getChoice).toList()
            ) && !question.hasStarterCode()) {
                return "Une question demande le resultat d'un code mais aucun code n'est fourni.";
            }

            List<Choice> choices = question.getChoices();
            if (choices == null || choices.size() < 2) {
                return "Chaque question doit contenir au moins deux choix.";
            }

            int correctAnswers = 0;
            Set<String> normalizedChoices = new HashSet<>();
            for (Choice choice : choices) {
                if (choice == null) {
                    return "Un choix de reponse est invalide.";
                }

                String normalizedChoice = normalizeText(choice.getChoice());
                choice.setChoice(normalizedChoice);
                if (normalizedChoice == null) {
                    return "Tous les choix doivent etre renseignes.";
                }
                if (looksLikePlaceholderChoice(normalizedChoice)) {
                    return "Un choix contient encore un texte generique.";
                }
                if (!normalizedChoices.add(normalizedChoice.toLowerCase(Locale.ROOT))) {
                    return "Les choix d'une meme question doivent etre differents.";
                }
                if (Boolean.TRUE.equals(choice.getIsCorrect())) {
                    correctAnswers++;
                }
            }

            if (correctAnswers != 1) {
                return "Chaque question doit avoir exactement une bonne reponse.";
            }
        }

        return null;
    }

    private boolean isDuplicateQuizTitle(String normalizedTitle) {
        return quizItems.stream()
                .filter(quiz -> quiz != null && quiz.getTitre() != null)
                .anyMatch(quiz -> {
                    if (selectedQuiz != null && selectedQuiz.getId() != null && selectedQuiz.getId().equals(quiz.getId())) {
                        return false;
                    }
                    return normalizedTitle.equalsIgnoreCase(normalizeText(quiz.getTitre()));
                });
    }

    private boolean hasDuplicateQuestion(String normalizedQuestion, int ignoredIndex) {
        for (int i = 0; i < currentQuestions.size(); i++) {
            if (i == ignoredIndex) {
                continue;
            }
            Question current = currentQuestions.get(i);
            if (current != null && normalizedQuestion.equalsIgnoreCase(normalizeText(current.getQuestion()))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeMultilineText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean containsLettersOrDigits(String value) {
        return value != null && value.matches(".*[\\p{L}\\p{N}].*");
    }

    private boolean looksLikePlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("question")
                || normalized.equals("test")
                || normalized.equals("question test")
                || normalized.equals("sample question");
    }

    private boolean looksLikePlaceholderChoice(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.matches("^(choix|option|choice)\\s*[a-d1-4]$")
                || normalized.equals("reponse")
                || normalized.equals("answer")
                || normalized.equals("test");
    }

    private void enrichQuestionMetadata(Question question, String quizTitle, String quizKeyword) {
        if (question == null) {
            return;
        }

        boolean missingTopic = normalizeText(question.getTopic()) == null;
        boolean missingDifficulty = normalizeText(question.getDifficulty()) == null;
        if (!missingTopic && !missingDifficulty) {
            return;
        }

        List<String> choiceTexts = question.getChoices().stream()
                .map(Choice::getChoice)
                .filter(choice -> choice != null && !choice.isBlank())
                .toList();

        AiQuizAssistantService.QuestionMetadata metadata = aiQuizAssistantService.inferQuestionMetadata(
                quizKeyword,
                quizTitle,
                question.getQuestion(),
                choiceTexts
        );

        if (missingTopic) {
            question.setTopic(normalizeText(metadata.topic()));
        }
        if (missingDifficulty) {
            question.setDifficulty(normalizeText(metadata.difficulty()));
        }
    }

    private void populateForm(Quiz quiz) {
        if (quiz == null) {
            selectedQuiz = null;
            clearForm();
            return;
        }

        selectedQuiz = quiz;
        titleField.setText(quiz.getTitre());
        keywordField.setText(quiz.getKeyword());

        currentQuestions.clear();
        if (quiz.getQuestions() != null) {
            currentQuestions.addAll(quiz.getQuestions());
        }

        updateQuestionsList();
        clearQuestionEditor();
    }

    private void applyNormalizedQuestionEditorValues(
            String normalizedQuestion,
            String normalizedChoice1,
            String normalizedChoice2,
            String normalizedChoice3,
            String normalizedChoice4
    ) {
        questionField.setText(normalizedQuestion == null ? "" : normalizedQuestion);
        choice1Field.setText(normalizedChoice1 == null ? "" : normalizedChoice1);
        choice2Field.setText(normalizedChoice2 == null ? "" : normalizedChoice2);
        choice3Field.setText(normalizedChoice3 == null ? "" : normalizedChoice3);
        choice4Field.setText(normalizedChoice4 == null ? "" : normalizedChoice4);
    }

    private void applyNormalizedCodeEditorValues(
            String normalizedCodeLanguage,
            String normalizedStarterCode,
            String normalizedExpectedAnswer
    ) {
        codeLanguageField.setText(normalizedCodeLanguage == null ? "" : normalizedCodeLanguage);
        starterCodeArea.setText(normalizedStarterCode == null ? "" : normalizedStarterCode);
        expectedAnswerArea.setText(normalizedExpectedAnswer == null ? "" : normalizedExpectedAnswer);
    }

    private void applyNormalizedCodeOutputValues(String normalizedCodeLanguage, String normalizedCodeOutput) {
        codeOutputLanguageField.setText(normalizedCodeLanguage == null ? "" : normalizedCodeLanguage);
        codeOutputArea.setText(normalizedCodeOutput == null ? "" : normalizedCodeOutput);
    }

    private void populateQuestionEditor(int index) {
        if (index < 0 || index >= currentQuestions.size()) {
            clearQuestionEditor();
            return;
        }

        selectedQuestionIndex = index;
        Question question = currentQuestions.get(index);
        questionField.setText(question.getQuestion());

        questionTypeComboBox.setValue(resolveQuestionTypeLabel(question));
        updateQuestionEditorMode();

        if (question.isCodeQuestion()) {
            codeLanguageField.setText(question.getCodeLanguage() != null ? question.getCodeLanguage() : "");
            starterCodeArea.setText(question.getStarterCode() != null ? question.getStarterCode() : "");
            expectedAnswerArea.setText(question.getExpectedAnswer() != null ? question.getExpectedAnswer() : "");
            codeEvaluationModeComboBox.setValue(question.usesAiCodeEvaluation() ? "AI Logic Check" : "Strict Syntax");
            clearChoiceFields();
            clearCodeOutputFields();
            return;
        }

        if (question.isCodeOutputQuestion()) {
            codeOutputLanguageField.setText(question.getCodeLanguage() != null ? question.getCodeLanguage() : "");
            codeOutputArea.setText(question.getStarterCode() != null ? question.getStarterCode() : "");
            clearCodeEditorFields();
        }

        List<Choice> choices = question.getChoices();
        choice1Field.setText(getChoiceText(choices, 0));
        choice2Field.setText(getChoiceText(choices, 1));
        choice3Field.setText(getChoiceText(choices, 2));
        choice4Field.setText(getChoiceText(choices, 3));
        correctChoiceComboBox.setValue(resolveCorrectChoiceLabel(choices));
        clearCodeEditorFields();
    }

    private String getChoiceText(List<Choice> choices, int index) {
        if (choices == null || index >= choices.size() || choices.get(index) == null) {
            return "";
        }
        String text = choices.get(index).getChoice();
        return text != null ? text : "";
    }

    private String resolveCorrectChoiceLabel(List<Choice> choices) {
        if (choices != null) {
            for (int i = 0; i < choices.size(); i++) {
                Choice choice = choices.get(i);
                if (choice != null && Boolean.TRUE.equals(choice.getIsCorrect())) {
                    return "Choix " + (char) ('A' + i);
                }
            }
        }
        return "Choix A";
    }

    private void showFeedback(String message, boolean isError) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(isError ? "-fx-text-fill: #ff6b6b;" : "-fx-text-fill: #51cf66;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void updateQuestionsList() {
        ObservableList<String> questionsList = FXCollections.observableArrayList();
        for (int i = 0; i < currentQuestions.size(); i++) {
            Question question = currentQuestions.get(i);
            String typeLabel = question.isCodeQuestion()
                    ? (question.usesAiCodeEvaluation() ? "[Code AI] " : "[Code Strict] ")
                    : question.isCodeOutputQuestion()
                    ? "[Code Snippet] "
                    : "[MCQ] ";
            questionsList.add((i + 1) + ". " + typeLabel + question.getQuestion());
        }
        if (questionsListView != null) {
            questionsListView.setItems(questionsList);
        }
        if (questionsAddedLabel != null) {
            questionsAddedLabel.setText(currentQuestions.size() + "/" + MAX_QUESTIONS + " questions");
        }
    }

    private void clearForm() {
        titleField.clear();
        keywordField.clear();
        currentQuestions.clear();
        clearQuestionEditor();
        updateQuestionsList();
    }

    private void clearQuestionEditor() {
        questionField.clear();
        questionTypeComboBox.setValue(QUESTION_MODE_MULTIPLE_CHOICE);
        clearChoiceFields();
        clearCodeOutputFields();
        clearCodeEditorFields();
        correctChoiceComboBox.setValue("Choix A");
        codeEvaluationModeComboBox.setValue("Strict Syntax");
        selectedQuestionIndex = -1;
        if (questionsListView != null) {
            questionsListView.getSelectionModel().clearSelection();
        }
        updateQuestionEditorMode();
    }

    private <T> TableCell<Quiz, T> createCell(Pos alignment) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setAlignment(alignment);
            }
        };
    }

    private boolean isCodeEditorSelected() {
        return QUESTION_MODE_CODE_EDITOR.equals(questionTypeComboBox.getValue());
    }

    private boolean isCodeOutputSelected() {
        return QUESTION_MODE_CODE_SNIPPET.equals(questionTypeComboBox.getValue());
    }

    private String resolveSelectedQuestionType() {
        if (isCodeEditorSelected()) {
            return Question.TYPE_CODE;
        }
        if (isCodeOutputSelected()) {
            return Question.TYPE_CODE_OUTPUT;
        }
        return Question.TYPE_MULTIPLE_CHOICE;
    }

    private String resolveQuestionTypeLabel(Question question) {
        if (question != null) {
            if (question.isCodeQuestion()) {
                return QUESTION_MODE_CODE_EDITOR;
            }
            if (question.isCodeOutputQuestion()) {
                return QUESTION_MODE_CODE_SNIPPET;
            }
        }
        return QUESTION_MODE_MULTIPLE_CHOICE;
    }

    private void updateQuestionEditorMode() {
        boolean codeMode = isCodeEditorSelected();
        boolean codeOutputMode = isCodeOutputSelected();

        setNodeVisibility(multipleChoiceEditorBox, !codeMode);
        setNodeVisibility(codeOutputEditorBox, codeOutputMode);
        setNodeVisibility(codeEditorBox, codeMode);
    }

    private void setNodeVisibility(javafx.scene.Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void clearChoiceFields() {
        choice1Field.clear();
        choice2Field.clear();
        choice3Field.clear();
        choice4Field.clear();
    }

    private void clearCodeEditorFields() {
        codeLanguageField.clear();
        starterCodeArea.clear();
        expectedAnswerArea.clear();
    }

    private void clearCodeOutputFields() {
        if (codeOutputLanguageField != null) {
            codeOutputLanguageField.clear();
        }
        if (codeOutputArea != null) {
            codeOutputArea.clear();
        }
    }

    private String resolveSelectedCodeEvaluationMode() {
        return "AI Logic Check".equals(codeEvaluationModeComboBox.getValue())
                ? Question.CODE_EVALUATION_AI
                : Question.CODE_EVALUATION_STRICT;
    }

    private String normalizeCodeEvaluationMode(String value) {
        if (Question.CODE_EVALUATION_AI.equalsIgnoreCase(value)) {
            return Question.CODE_EVALUATION_AI;
        }
        return Question.CODE_EVALUATION_STRICT;
    }

    private void refreshQuizData() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        quizItems.setAll(quizzes);
        quizzesTable.refresh();
        updateStats(quizzes);
    }

    private void updateStats(List<Quiz> quizzes) {
        int total = quizzes.size();
        int withResults = (int) quizzes.stream()
                .filter(q -> !q.getQuizResults().isEmpty())
                .count();

        double avg = quizzes.stream()
                .flatMap(q -> q.getQuizResults().stream())
                .mapToDouble(r -> r.getPercentage() != null ? r.getPercentage() : 0.0)
                .average()
                .orElse(0.0);

        totalQuizzesLabel.setText(String.valueOf(total));
        quizzesWithResultsLabel.setText(String.valueOf(withResults));
        averageScoreLabel.setText(NumberFormat.getPercentInstance(Locale.FRANCE).format(avg / 100.0));
    }

    private String getLastScoreLabel(Quiz quiz) {
        return quiz.getQuizResults().stream()
                .filter(result -> result.getPercentage() != null)
                .max((left, right) -> {
                    Instant leftCompletedAt = left.getCompletedAt();
                    Instant rightCompletedAt = right.getCompletedAt();
                    if (leftCompletedAt == null && rightCompletedAt == null) {
                        return 0;
                    }
                    if (leftCompletedAt == null) {
                        return -1;
                    }
                    if (rightCompletedAt == null) {
                        return 1;
                    }
                    return leftCompletedAt.compareTo(rightCompletedAt);
                })
                .map(result -> NumberFormat.getPercentInstance(Locale.FRANCE).format(result.getPercentage() / 100.0))
                .orElse("-");
    }
}
