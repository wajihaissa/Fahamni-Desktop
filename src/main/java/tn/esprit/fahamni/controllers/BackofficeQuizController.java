package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
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
import javafx.scene.control.TextField;
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
import java.util.List;
import java.util.Locale;

public class BackofficeQuizController {

    @FXML private TableView<Quiz> quizzesTable;
    @FXML private TableColumn<Quiz, Long> idColumn;
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
    private static final int MAX_QUESTIONS = 5;

    @FXML
    private void initialize() {
        quizzesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        quizzesTable.setFixedCellSize(40);

        idColumn.setCellValueFactory(cell ->
                new SimpleLongProperty(cell.getValue().getId()).asObject());

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

        idColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        titleColumn.setCellFactory(tc -> createCell(Pos.CENTER_LEFT));
        keywordColumn.setCellFactory(tc -> createCell(Pos.CENTER_LEFT));
        questionsColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        resultsColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        createdAtColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        lastScoreColumn.setCellFactory(tc -> createCell(Pos.CENTER));

        correctChoiceComboBox.getItems().setAll("Choix A", "Choix B", "Choix C", "Choix D");
        correctChoiceComboBox.setValue("Choix A");
        if (aiQuestionCountComboBox != null) {
            aiQuestionCountComboBox.getItems().setAll(3, 4, 5);
            aiQuestionCountComboBox.setValue(5);
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
    }

    @FXML
    public void handleCreateQuiz(ActionEvent event) {
        if (titleField.getText().trim().isEmpty() || keywordField.getText().trim().isEmpty()) {
            showFeedback("Veuillez remplir le titre et le mot-cle", true);
            return;
        }

        if (currentQuestions.isEmpty()) {
            showFeedback("Veuillez ajouter au moins une question", true);
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

        if (questionField.getText().trim().isEmpty()) {
            showFeedback("Veuillez entrer une question", true);
            return;
        }

        if (choice1Field.getText().trim().isEmpty() || choice2Field.getText().trim().isEmpty()
                || choice3Field.getText().trim().isEmpty() || choice4Field.getText().trim().isEmpty()) {
            showFeedback("Veuillez remplir tous les choix", true);
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

        if (titleField.getText().trim().isEmpty() || keywordField.getText().trim().isEmpty()) {
            showFeedback("Veuillez remplir le titre et le mot-cle", true);
            return;
        }

        if (currentQuestions.isEmpty()) {
            showFeedback("Veuillez ajouter au moins une question", true);
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
        String title = titleField.getText() != null ? titleField.getText().trim() : "";
        String topic = keywordField.getText() != null && !keywordField.getText().trim().isEmpty()
                ? keywordField.getText().trim()
                : title;

        if (topic.isEmpty()) {
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
        Quiz generatedQuiz = generatedDraft.quiz();

        if (generatedQuiz == null || generatedQuiz.getQuestions().isEmpty()) {
            showFeedback("La generation IA a echoue, veuillez reessayer", true);
            return;
        }

        titleField.setText(generatedQuiz.getTitre());
        keywordField.setText(generatedQuiz.getKeyword());
        currentQuestions.clear();
        currentQuestions.addAll(generatedQuiz.getQuestions());
        clearQuestionEditor();
        updateQuestionsList();
        showFeedback("Quiz genere avec " + generatedDraft.provider() + ". Verifiez puis enregistrez.", false);
    }

    private Quiz buildQuizFromForm() {
        Quiz quiz = new Quiz();
        quiz.setTitre(titleField.getText().trim());
        quiz.setKeyword(keywordField.getText().trim());

        for (Question question : currentQuestions) {
            quiz.addQuestion(question);
        }

        return quiz;
    }

    private Question buildQuestionFromFields() {
        Question question = new Question();
        question.setQuestion(questionField.getText().trim());

        String[] choiceTexts = {
                choice1Field.getText().trim(),
                choice2Field.getText().trim(),
                choice3Field.getText().trim(),
                choice4Field.getText().trim()
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

    private void populateQuestionEditor(int index) {
        if (index < 0 || index >= currentQuestions.size()) {
            clearQuestionEditor();
            return;
        }

        selectedQuestionIndex = index;
        Question question = currentQuestions.get(index);
        questionField.setText(question.getQuestion());

        List<Choice> choices = question.getChoices();
        choice1Field.setText(getChoiceText(choices, 0));
        choice2Field.setText(getChoiceText(choices, 1));
        choice3Field.setText(getChoiceText(choices, 2));
        choice4Field.setText(getChoiceText(choices, 3));
        correctChoiceComboBox.setValue(resolveCorrectChoiceLabel(choices));
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
            questionsList.add((i + 1) + ". " + currentQuestions.get(i).getQuestion());
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
        choice1Field.clear();
        choice2Field.clear();
        choice3Field.clear();
        choice4Field.clear();
        correctChoiceComboBox.setValue("Choix A");
        selectedQuestionIndex = -1;
        if (questionsListView != null) {
            questionsListView.getSelectionModel().clearSelection();
        }
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
                .map(r -> r.getPercentage())
                .filter(p -> p != null)
                .map(p -> NumberFormat.getPercentInstance(Locale.FRANCE).format(p / 100.0))
                .findFirst()
                .orElse("-");
    }
}
