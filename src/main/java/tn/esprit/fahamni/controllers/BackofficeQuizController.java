package tn.esprit.fahamni.controllers;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
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
    @FXML private Label feedbackLabel;
    @FXML private Label questionsAddedLabel;
    @FXML private ListView<String> questionsListView;

    private final QuizService quizService = new QuizService();
    private final ObservableList<Quiz> quizItems = FXCollections.observableArrayList();
    private final List<Question> currentQuestions = new ArrayList<>();
    private Quiz selectedQuiz = null;
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
            if (createdAt == null) return new SimpleStringProperty("-");
            return new SimpleStringProperty(
                    createdAt.atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
            );
        });

        lastScoreColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(getLastScoreLabel(cell.getValue())));

        // ALIGNMENT
        idColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        titleColumn.setCellFactory(tc -> createCell(Pos.CENTER_LEFT));
        keywordColumn.setCellFactory(tc -> createCell(Pos.CENTER_LEFT));
        questionsColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        resultsColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        createdAtColumn.setCellFactory(tc -> createCell(Pos.CENTER));
        lastScoreColumn.setCellFactory(tc -> createCell(Pos.CENTER));

        correctChoiceComboBox.getItems().setAll("Choix A", "Choix B", "Choix C", "Choix D");
        correctChoiceComboBox.setValue("Choix A");

        quizzesTable.setItems(quizItems);

        quizzesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> populateForm(newVal));

        refreshQuizData();
    }
       @FXML
    public void handleCreateQuiz(ActionEvent event) {
        // This is now the "Save Quiz" button
        if (titleField.getText().trim().isEmpty() || keywordField.getText().trim().isEmpty()) {
            showFeedback("Veuillez remplir le titre et le mot-clé", true);
            return;
        }

        if (currentQuestions.isEmpty()) {
            showFeedback("Veuillez ajouter au moins une question", true);
            return;
        }

        try {
            if (selectedQuiz != null) {
                // Update existing quiz
                Quiz updatedQuiz = new Quiz();
                updatedQuiz.setTitre(titleField.getText());
                updatedQuiz.setKeyword(keywordField.getText());
                
                for (Question q : currentQuestions) {
                    updatedQuiz.addQuestion(q);
                }
                
                quizService.updateQuiz(selectedQuiz.getId(), updatedQuiz);
                showFeedback("Quiz mis à jour avec succès !", false);
            } else {
                // Create new quiz
                Quiz quiz = new Quiz();
                quiz.setTitre(titleField.getText());
                quiz.setKeyword(keywordField.getText());
                
                for (Question q : currentQuestions) {
                    quiz.addQuestion(q);
                }
                
                Quiz savedQuiz = quizService.createQuiz(quiz);
                if (savedQuiz != null && savedQuiz.getId() != null) {
                    showFeedback("Quiz créé avec " + currentQuestions.size() + " question(s) !", false);
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
        if (currentQuestions.size() >= MAX_QUESTIONS) {
            feedbackLabel.setText("Maximum de " + MAX_QUESTIONS + " questions atteint !");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
            return;
        }

        if (questionField.getText().trim().isEmpty()) {
            feedbackLabel.setText("Veuillez entrer une question");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
            return;
        }

        if (choice1Field.getText().trim().isEmpty() || choice2Field.getText().trim().isEmpty() ||
            choice3Field.getText().trim().isEmpty() || choice4Field.getText().trim().isEmpty()) {
            feedbackLabel.setText("Veuillez remplir tous les choix");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
            return;
        }

        try {
            Question question = new Question();
            question.setQuestion(questionField.getText());
            
            String[] choiceTexts = {choice1Field.getText(), choice2Field.getText(), 
                                   choice3Field.getText(), choice4Field.getText()};
            String correctChoice = correctChoiceComboBox.getValue();
            
            for (int i = 0; i < choiceTexts.length; i++) {
                Choice choice = new Choice();
                choice.setChoice(choiceTexts[i]);
                choice.setIsCorrect(correctChoice.equals("Choix " + (char)('A' + i)));
                question.addChoice(choice);
            }
            currentQuestions.add(question);
            
            updateQuestionsList();
            questionField.clear();
            choice1Field.clear();
            choice2Field.clear();
            choice3Field.clear();
            choice4Field.clear();
            correctChoiceComboBox.setValue("Choix A");
            
            feedbackLabel.setText("Question ajoutée ! (" + currentQuestions.size() + "/" + MAX_QUESTIONS + ")");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        } catch (Exception e) {
            feedbackLabel.setText("Erreur : " + e.getMessage());
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        }
    }

    @FXML
    public void handleRemoveLastQuestion(ActionEvent event) {
        if (!currentQuestions.isEmpty()) {
            currentQuestions.remove(currentQuestions.size() - 1);
            updateQuestionsList();
            feedbackLabel.setText("Question supprimée ! (" + currentQuestions.size() + "/" + MAX_QUESTIONS + ")");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        }
    }

    @FXML
    public void handleUpdateQuiz(ActionEvent event) {
        if (selectedQuiz == null) {
            showFeedback("Veuillez sélectionner un quiz à modifier", true);
            return;
        }

        if (titleField.getText().trim().isEmpty() || keywordField.getText().trim().isEmpty()) {
            showFeedback("Veuillez remplir le titre et le mot-clé", true);
            return;
        }

        if (currentQuestions.isEmpty()) {
            showFeedback("Veuillez ajouter au moins une question", true);
            return;
        }

        try {
            Quiz updatedQuiz = new Quiz();
            updatedQuiz.setTitre(titleField.getText());
            updatedQuiz.setKeyword(keywordField.getText());
            for (Question q : currentQuestions) {
                updatedQuiz.addQuestion(q);
            }

            Quiz result = quizService.updateQuiz(selectedQuiz.getId(), updatedQuiz);
            if (result != null) {
                showFeedback("Quiz mis à jour avec succès !", false);
                clearForm();
                selectedQuiz = null;
                refreshQuizData();
            } else {
                showFeedback("Impossible de mettre à jour le quiz", true);
            }
        } catch (Exception e) {
            showFeedback("Erreur lors de la mise à jour : " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleDeleteQuiz(ActionEvent event) {
        if (selectedQuiz == null) {
            showFeedback("Veuillez sélectionner un quiz à supprimer", true);
            return;
        }

        try {
            boolean deleted = quizService.deleteQuiz(selectedQuiz.getId());
            if (deleted) {
                showFeedback("Quiz supprimé avec succès !", false);
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
        showFeedback("Liste rafraîchie !", false);
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
        
        // Load questions from the selected quiz
        currentQuestions.clear();
        if (quiz.getQuestions() != null) {
            currentQuestions.addAll(quiz.getQuestions());
        }
        updateQuestionsList();
        
        // Clear the individual question fields
        questionField.clear();
        choice1Field.clear();
        choice2Field.clear();
        choice3Field.clear();
        choice4Field.clear();
        correctChoiceComboBox.setValue("Choix A");
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
        questionField.clear();
        choice1Field.clear();
        choice2Field.clear();
        choice3Field.clear();
        choice4Field.clear();
        correctChoiceComboBox.setValue("Choix A");
        currentQuestions.clear();
        updateQuestionsList();
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
                .filter(q -> !q.getQuizResults().isEmpty()).count();

        double avg = quizzes.stream()
                .flatMap(q -> q.getQuizResults().stream())
                .mapToDouble(r -> r.getPercentage() != null ? r.getPercentage() : 0.0)
                .average().orElse(0.0);

        totalQuizzesLabel.setText(String.valueOf(total));
        quizzesWithResultsLabel.setText(String.valueOf(withResults));
        averageScoreLabel.setText(
                NumberFormat.getPercentInstance(Locale.FRANCE).format(avg / 100.0)
        );
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