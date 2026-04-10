package tn.esprit.fahamni.controllers;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.services.QuizService;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BackofficeQuizController {

    @FXML
    private TableView<Quiz> quizzesTable;

    @FXML
    private TableColumn<Quiz, Long> idColumn;

    @FXML
    private TableColumn<Quiz, String> titleColumn;

    @FXML
    private TableColumn<Quiz, String> keywordColumn;

    @FXML
    private TableColumn<Quiz, Integer> questionsColumn;

    @FXML
    private TableColumn<Quiz, Integer> resultsColumn;

    @FXML
    private TableColumn<Quiz, String> createdAtColumn;

    @FXML
    private TableColumn<Quiz, String> lastScoreColumn;

    @FXML
    private Label totalQuizzesLabel;

    @FXML
    private Label quizzesWithResultsLabel;

    @FXML
    private Label averageScoreLabel;

    @FXML
    private TextField titleField;

    @FXML
    private TextField keywordField;

    @FXML
    private TextField questionField;

    @FXML
    private TextField choice1Field;

    @FXML
    private TextField choice2Field;

    @FXML
    private TextField choice3Field;

    @FXML
    private TextField choice4Field;

    @FXML
    private ComboBox<String> correctChoiceComboBox;

    @FXML
    private Label feedbackLabel;

    private final QuizService quizService = new QuizService();
    private ObservableList<Quiz> quizItems = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @FXML
private void initialize() {
    System.out.println("Initializing BackofficeQuizController");
      quizzesTable.setStyle("-fx-text-fill: black; -fx-background-color: white;");
    
    // Make column headers visible
    for (TableColumn<?, ?> column : quizzesTable.getColumns()) {
        column.setStyle("-fx-text-fill: black; -fx-background-color: #f0f0f0; -fx-font-weight: bold;");
    }
    idColumn.setCellValueFactory(cellData -> {
        Quiz quiz = cellData.getValue();
        Long id = quiz.getId();
        return id != null ? new SimpleLongProperty(id).asObject() : null;
    });
    
    titleColumn.setCellValueFactory(cellData -> 
        new SimpleStringProperty(cellData.getValue().getTitre()));
    
    keywordColumn.setCellValueFactory(cellData -> 
        new SimpleStringProperty(cellData.getValue().getKeyword()));
    
    questionsColumn.setCellValueFactory(cellData -> {
        Quiz quiz = cellData.getValue();
        return new SimpleIntegerProperty(quiz.getQuestions().size()).asObject();
    });
    
    resultsColumn.setCellValueFactory(cellData -> {
        Quiz quiz = cellData.getValue();
        return new SimpleIntegerProperty(quiz.getQuizResults().size()).asObject();
    });
    
    // FIXED: Proper Instant formatting
    createdAtColumn.setCellValueFactory(cellData -> {
        Instant createdAt = cellData.getValue().getCreatedAt();
        if (createdAt == null) {
            return new SimpleStringProperty("-");
        }
        String formatted = createdAt.atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        return new SimpleStringProperty(formatted);
    });
    lastScoreColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(getLastScoreLabel(cellData.getValue())));

    idColumn.setStyle("-fx-alignment: CENTER;");
    titleColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    keywordColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    questionsColumn.setStyle("-fx-alignment: CENTER;");
    resultsColumn.setStyle("-fx-alignment: CENTER;");
    createdAtColumn.setStyle("-fx-alignment: CENTER;");
    lastScoreColumn.setStyle("-fx-alignment: CENTER;");

    applyBlackLabelCellFactory(idColumn, Pos.CENTER);
    applyBlackLabelCellFactory(titleColumn, Pos.CENTER_LEFT);
    applyBlackLabelCellFactory(keywordColumn, Pos.CENTER_LEFT);
    applyBlackLabelCellFactory(questionsColumn, Pos.CENTER);
    applyBlackLabelCellFactory(resultsColumn, Pos.CENTER);
    applyBlackLabelCellFactory(createdAtColumn, Pos.CENTER);
    applyBlackLabelCellFactory(lastScoreColumn, Pos.CENTER);

    correctChoiceComboBox.getItems().setAll("Choix A", "Choix B", "Choix C", "Choix D");
    correctChoiceComboBox.setValue("Choix A");

    quizzesTable.setPlaceholder(new Label("Chargement des quiz..."));
    quizzesTable.setItems(quizItems);
    quizzesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));

    refreshQuizData();
    hideFeedback();
}

    private <T> void applyBlackLabelCellFactory(TableColumn<Quiz, T> column, Pos alignment) {
        column.setCellFactory(col -> new TableCell<Quiz, T>() {
            private final Label content = new Label();
            {
                content.setTextFill(Color.BLACK);
                content.setWrapText(true);
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    content.setText(item.toString());
                    setText(null);
                    setGraphic(content);
                    setAlignment(alignment);
                }
            }
        });
    }

    @FXML
    private void handleCreateQuiz() {
        hideFeedback();

        Quiz quiz = new Quiz();
        quiz.setTitre(titleField.getText());
        quiz.setKeyword(keywordField.getText());

        Question question = new Question();
        question.setQuestion(questionField.getText());

        Choice choiceA = createChoice(choice1Field.getText(), "Choix A".equals(correctChoiceComboBox.getValue()));
        Choice choiceB = createChoice(choice2Field.getText(), "Choix B".equals(correctChoiceComboBox.getValue()));
        Choice choiceC = createChoice(choice3Field.getText(), "Choix C".equals(correctChoiceComboBox.getValue()));
        Choice choiceD = createChoice(choice4Field.getText(), "Choix D".equals(correctChoiceComboBox.getValue()));

        question.addChoice(choiceA);
        question.addChoice(choiceB);
        question.addChoice(choiceC);
        question.addChoice(choiceD);
        quiz.addQuestion(question);

        if (quizService.createQuiz(quiz) == null) {
            showFeedback("Impossible de créer le quiz. Vérifiez que tous les champs sont remplis et qu'une seule réponse est correcte.", false);
            return;
        }

        clearForm();
        refreshQuizData();
        showFeedback("Quiz ajouté avec succès.", true);
    }

    @FXML
    private void handleDeleteQuiz() {
        hideFeedback();
        Quiz selectedQuiz = quizzesTable.getSelectionModel().getSelectedItem();
        if (selectedQuiz == null) {
            showFeedback("Sélectionnez un quiz à supprimer.", false);
            return;
        }

        boolean deleted = quizService.deleteQuiz(selectedQuiz.getId());
        if (deleted) {
            refreshQuizData();
            showFeedback("Quiz supprimé avec succès.", true);
        } else {
            showFeedback("Impossible de supprimer le quiz sélectionné.", false);
        }
    }

    @FXML
    private void handleRefresh() {
        hideFeedback();
        refreshQuizData();
        showFeedback("Données mises à jour.", true);
    }

    @FXML
    private void handleUpdateQuiz() {
        hideFeedback();
        Quiz selectedQuiz = quizzesTable.getSelectionModel().getSelectedItem();
        if (selectedQuiz == null) {
            showFeedback("Sélectionnez un quiz à mettre à jour.", false);
            return;
        }

        Quiz quiz = new Quiz();
        quiz.setTitre(titleField.getText());
        quiz.setKeyword(keywordField.getText());

        Question question = new Question();
        question.setQuestion(questionField.getText());

        Choice choiceA = createChoice(choice1Field.getText(), "Choix A".equals(correctChoiceComboBox.getValue()));
        Choice choiceB = createChoice(choice2Field.getText(), "Choix B".equals(correctChoiceComboBox.getValue()));
        Choice choiceC = createChoice(choice3Field.getText(), "Choix C".equals(correctChoiceComboBox.getValue()));
        Choice choiceD = createChoice(choice4Field.getText(), "Choix D".equals(correctChoiceComboBox.getValue()));

        question.addChoice(choiceA);
        question.addChoice(choiceB);
        question.addChoice(choiceC);
        question.addChoice(choiceD);
        quiz.addQuestion(question);

        Quiz updatedQuiz = quizService.updateQuiz(selectedQuiz.getId(), quiz);
        if (updatedQuiz == null) {
            showFeedback("Impossible de mettre à jour le quiz. Vérifiez les champs.", false);
            return;
        }

        refreshQuizData();
        showFeedback("Quiz mis à jour avec succès.", true);
    }

    private void refreshQuizData() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        System.out.println("Loaded " + quizzes.size() + " quizzes");
        for (Quiz q : quizzes) {
            System.out.println("  - Quiz ID: " + q.getId() + ", Title: " + q.getTitre() + ", CreatedAt: " + q.getCreatedAt() + ", LastScore: " + getLastScoreLabel(q) + ", Questions: " + q.getQuestions().size() + ", Results: " + q.getQuizResults().size());
        }
        quizItems.setAll(quizzes);
        quizzesTable.refresh();
        quizzesTable.setMinHeight(240);
        updateStats(quizzes);
    }

    private void updateStats(List<Quiz> quizzes) {
        int total = quizzes.size();
        int withResults = (int) quizzes.stream().filter(q -> !q.getQuizResults().isEmpty()).count();
        double averageScore = quizzes.stream()
                .flatMap(q -> q.getQuizResults().stream())
                .mapToDouble(result -> result.getPercentage() != null ? result.getPercentage() : 0.0)
                .average()
                .orElse(0.0);

        totalQuizzesLabel.setText(String.valueOf(total));
        quizzesWithResultsLabel.setText(String.valueOf(withResults));
        averageScoreLabel.setText(NumberFormat.getPercentInstance(Locale.FRANCE).format(averageScore / 100.0));
    }

    private void populateForm(Quiz quiz) {
        if (quiz == null) {
            clearForm();
            return;
        }
        titleField.setText(quiz.getTitre());
        keywordField.setText(quiz.getKeyword());
        if (!quiz.getQuestions().isEmpty()) {
            Question question = quiz.getQuestions().get(0);
            questionField.setText(question.getQuestion());
            List<Choice> choices = question.getChoices();
            if (choices.size() >= 4) {
                choice1Field.setText(choices.get(0).getChoice());
                choice2Field.setText(choices.get(1).getChoice());
                choice3Field.setText(choices.get(2).getChoice());
                choice4Field.setText(choices.get(3).getChoice());
                correctChoiceComboBox.setValue(getCorrectChoiceLabel(choices));
            }
        }
    }

    private Choice createChoice(String text, boolean correct) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(correct);
        return choice;
    }

    private String getCorrectChoiceLabel(List<Choice> choices) {
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            if (Boolean.TRUE.equals(choice.getIsCorrect())) {
                return "Choix " + (char) ('A' + i);
            }
        }
        return "Choix A";
    }

    private String getLastScoreLabel(Quiz quiz) {
        return quiz.getQuizResults().stream()
                .filter(result -> result.getCompletedAt() != null)
                .max((r1, r2) -> r1.getCompletedAt().compareTo(r2.getCompletedAt()))
                .map(result -> {
                    Double percentage = result.getPercentage();
                    if (percentage != null) {
                        return NumberFormat.getPercentInstance(Locale.FRANCE).format(percentage / 100.0);
                    }
                    Integer score = result.getScore();
                    Integer total = result.getTotalQuestions();
                    if (score != null && total != null && total > 0) {
                        return NumberFormat.getPercentInstance(Locale.FRANCE).format((double) score / total);
                    }
                    if (score != null) {
                        return total != null ? score + "/" + total : score.toString();
                    }
                    return "-";
                })
                .orElse("-");
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
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.getStyleClass().setAll("backoffice-feedback");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}
