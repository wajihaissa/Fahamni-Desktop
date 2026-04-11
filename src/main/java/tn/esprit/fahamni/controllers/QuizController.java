package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.services.QuizService;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class QuizController {

    @FXML private FlowPane availableQuizzesBox;
    @FXML private VBox recentResultsBox;
    @FXML private Label quizzesTakenValue;
    @FXML private Label averageScoreValue;
    @FXML private Label subjectsMasteredValue;
    @FXML private Label quizResultsSummaryLabel;
    @FXML private CheckBox attemptedOnlyCheckBox;
    @FXML private ComboBox<String> quizSortCombo;
    @FXML private TextField quizSearchField;

    private final QuizService quizService = new QuizService();
    // Static test user used while the user session system is being integrated.
    private static final User TEST_USER = new User(1L, "Quiz Tester", "quiz.tester@fahamni.tn", "", UserRole.USER);
    private List<Quiz> allQuizzes = List.of();

    @FXML
    private void initialize() {
        quizSearchField.textProperty().addListener((obs, oldValue, newValue) -> renderFilteredQuizCards());
        quizSortCombo.getItems().addAll("Most Recent", "Title A-Z", "Most Played");
        quizSortCombo.setValue("Most Recent");
        quizSortCombo.valueProperty().addListener((obs, oldValue, newValue) -> renderFilteredQuizCards());
        attemptedOnlyCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> renderFilteredQuizCards());
        refreshQuizData();
    }

    private void refreshQuizData() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        if (quizzes.isEmpty()) {
            seedTestQuizzes();
            quizzes = quizService.getAllQuizzes();
        }
        allQuizzes = quizzes;
        renderFilteredQuizCards();
        renderRecentResults(quizzes);
        updateStats(quizzes);
    }

    private void renderFilteredQuizCards() {
        String query = quizSearchField != null ? quizSearchField.getText() : "";
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Quiz> filteredQuizzes = allQuizzes.stream()
                .filter(quiz -> normalizedQuery.isBlank()
                        || quiz.getTitre().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || quiz.getKeyword().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(quiz -> attemptedOnlyCheckBox == null
                        || !attemptedOnlyCheckBox.isSelected()
                        || !quiz.getQuizResults().isEmpty())
                .sorted(resolveQuizComparator())
                .toList();

        updateQuizResultsSummary(filteredQuizzes.size(), normalizedQuery.isBlank());
        renderQuizCards(filteredQuizzes);
    }

    private void updateQuizResultsSummary(int resultCount, boolean showingAll) {
        if (quizResultsSummaryLabel == null) {
            return;
        }

        if (showingAll) {
            quizResultsSummaryLabel.setText(resultCount + " quiz available");
            return;
        }

        quizResultsSummaryLabel.setText(resultCount + " quiz match your search");
    }

    private Comparator<Quiz> resolveQuizComparator() {
        String selectedSort = quizSortCombo != null ? quizSortCombo.getValue() : "Most Recent";
        if ("Title A-Z".equals(selectedSort)) {
            return Comparator.comparing(Quiz::getTitre, String.CASE_INSENSITIVE_ORDER);
        }
        if ("Most Played".equals(selectedSort)) {
            return Comparator.comparingInt((Quiz quiz) -> quiz.getQuizResults().size()).reversed()
                    .thenComparing(Quiz::getTitre, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparing(Quiz::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Quiz::getTitre, String.CASE_INSENSITIVE_ORDER);
    }

    private void seedTestQuizzes() {
        try {
            Quiz quiz = new Quiz();
            quiz.setTitre("Valorant Certification Quiz");
            quiz.setKeyword("Valorant");

            Question q1 = new Question();
            q1.setQuestion("What is the role of the Spike in Valorant?");
            q1.addChoice(buildChoice("Disarm enemy items", false));
            q1.addChoice(buildChoice("Activate a special weapon", false));
            q1.addChoice(buildChoice("Plant the bomb to win the round", true));
            q1.addChoice(buildChoice("Heal your team", false));
            quiz.addQuestion(q1);

            Question q2 = new Question();
            q2.setQuestion("Which ability helps reveal enemies through walls?");
            q2.addChoice(buildChoice("Cypher - Camera Wire", false));
            q2.addChoice(buildChoice("Sova - Recon Bolt", true));
            q2.addChoice(buildChoice("Sage - Barrier Orb", false));
            q2.addChoice(buildChoice("Skye - Trailblazer", false));
            quiz.addQuestion(q2);

            quizService.createQuiz(quiz);
        } catch (Exception ignored) {
            // Ignore seed failures in environments with no DB access.
        }
    }

    private Choice buildChoice(String text, boolean isCorrect) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(isCorrect);
        return choice;
    }

    private void renderQuizCards(List<Quiz> quizzes) {
        availableQuizzesBox.getChildren().clear();
        if (quizzes == null || quizzes.isEmpty()) {
            Label emptyLabel = new Label("No quizzes are available right now. Please check back later.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("quiz-empty-message");
            availableQuizzesBox.getChildren().add(emptyLabel);
            return;
        }

        for (Quiz quiz : quizzes) {
            availableQuizzesBox.getChildren().add(buildQuizCard(quiz));
        }
    }

    private HBox buildQuizCard(Quiz quiz) {
        HBox quizCard = new HBox(15);
        quizCard.getStyleClass().add("quiz-card");
        quizCard.setAlignment(Pos.CENTER_LEFT);
        quizCard.setPadding(new Insets(14));
        quizCard.setPrefWidth(320);
        quizCard.setMaxWidth(320);

        VBox cardBody = new VBox(6);
        cardBody.setAlignment(Pos.CENTER_LEFT);
        cardBody.setPrefWidth(220);

        Label titleLabel = new Label(quiz.getTitre());
        titleLabel.getStyleClass().add("quiz-title");

        Label keywordBadge = new Label(quiz.getKeyword().isBlank() ? "General" : quiz.getKeyword());
        keywordBadge.getStyleClass().add("quiz-keyword-badge");

        String resultStatus = quiz.getQuizResults().isEmpty() ? "Not attempted yet" : "Results available";
        Label infoLabel = new Label(quiz.getQuestions().size() + " questions | " + resultStatus);
        infoLabel.getStyleClass().add("quiz-info");

        Label descriptionLabel = new Label("A quick knowledge check about '" + quiz.getKeyword() + "'.");
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("quiz-description");

        String bestScoreText = buildBestScoreLabel(quiz);
        Label bestScoreLabel = new Label(bestScoreText);
        bestScoreLabel.getStyleClass().add("quiz-info");

        Label attemptsLabel = new Label(buildAttemptCountLabel(quiz));
        attemptsLabel.getStyleClass().add("quiz-info");

        cardBody.getChildren().addAll(titleLabel, keywordBadge, infoLabel, descriptionLabel, bestScoreLabel, attemptsLabel);
        HBox.setHgrow(cardBody, Priority.ALWAYS);

        VBox actionBox = new VBox(10);
        actionBox.setAlignment(Pos.CENTER);

        Label ratingLabel = new Label("* " + buildRatingLabel(quiz));
        ratingLabel.getStyleClass().add("quiz-rating");

        Button startButton = new Button("Start Quiz");
        startButton.getStyleClass().add("start-quiz-button");
        startButton.setOnAction(event -> handleStartQuiz(quiz));
        actionBox.getChildren().addAll(ratingLabel, startButton);

        quizCard.getChildren().addAll(cardBody, actionBox);
        return quizCard;
    }

    private void renderRecentResults(List<Quiz> quizzes) {
        recentResultsBox.getChildren().clear();
        if (quizzes == null || quizzes.isEmpty()) {
            Label emptyLabel = new Label("No recent quiz results are available.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("result-empty-message");
            recentResultsBox.getChildren().add(emptyLabel);
            return;
        }

        quizzes.stream()
                .flatMap(quiz -> quiz.getQuizResults().stream())
                .filter(result -> result.getCompletedAt() != null)
                .sorted((left, right) -> right.getCompletedAt().compareTo(left.getCompletedAt()))
                .limit(5)
                .forEach(result -> recentResultsBox.getChildren().add(buildResultCard(result)));

        if (recentResultsBox.getChildren().isEmpty()) {
            Label emptyLabel = new Label("No recent quiz results have been recorded yet.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("result-empty-message");
            recentResultsBox.getChildren().add(emptyLabel);
        }
    }

    private HBox buildResultCard(QuizResult result) {
        HBox resultCard = new HBox(15);
        resultCard.getStyleClass().add("result-card");
        resultCard.setAlignment(Pos.CENTER_LEFT);
        resultCard.setPadding(new Insets(14));

        VBox resultBody = new VBox(4);
        resultBody.setAlignment(Pos.CENTER_LEFT);

        Label resultTitle = new Label(result.getQuiz() != null ? result.getQuiz().getTitre() : "Unknown quiz");
        resultTitle.getStyleClass().add("result-title");

        String completedOn = result.getCompletedAt() != null
                ? result.getCompletedAt().toString().substring(0, 10)
                : "--";
        Label resultDate = new Label("Completed on " + completedOn);
        resultDate.getStyleClass().add("result-date");
        resultBody.getChildren().addAll(resultTitle, resultDate);
        HBox.setHgrow(resultBody, Priority.ALWAYS);

        VBox resultScore = new VBox(2);
        resultScore.setAlignment(Pos.CENTER);

        String percentageLabel = formatPercentage(result.getPercentage() != null ? result.getPercentage() : 0.0);
        Label scoreLabel = new Label(result.getScore() + "/" + result.getTotalQuestions() + " (" + percentageLabel + ")");
        scoreLabel.getStyleClass().add("result-score");

        Label gradeLabel = new Label(Boolean.TRUE.equals(result.getPassed()) ? "Passed" : "Try again");
        gradeLabel.getStyleClass().add("result-grade");
        resultScore.getChildren().addAll(scoreLabel, gradeLabel);

        Button reviewButton = new Button("Review");
        reviewButton.getStyleClass().add("review-button");
        reviewButton.setOnAction(event -> showResultDetails(result));

        resultCard.getChildren().addAll(resultBody, resultScore, reviewButton);
        return resultCard;
    }

    private void updateStats(List<Quiz> quizzes) {
        int quizzesTaken = (int) quizzes.stream()
                .filter(quiz -> !quiz.getQuizResults().isEmpty())
                .count();

        double averageScore = quizzes.stream()
                .flatMap(quiz -> quiz.getQuizResults().stream())
                .mapToDouble(result -> result.getPercentage() != null ? result.getPercentage() : 0.0)
                .average()
                .orElse(0.0);

        int masteredSubjects = (int) quizzes.stream()
                .filter(quiz -> quiz.getQuizResults().stream().anyMatch(result -> Boolean.TRUE.equals(result.getPassed())))
                .count();

        quizzesTakenValue.setText(String.valueOf(quizzesTaken));
        averageScoreValue.setText(formatPercentage(averageScore));
        subjectsMasteredValue.setText(String.valueOf(masteredSubjects));
    }

    private void handleStartQuiz(Quiz quiz) {
        if (quiz == null || quiz.getQuestions().isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Quiz unavailable", "This quiz is not ready or does not have any questions yet.");
            return;
        }

        Dialog<QuizResult> dialog = new Dialog<>();
        dialog.setTitle("Start quiz");
        dialog.setHeaderText(quiz.getTitre());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(18);
        content.setPadding(new Insets(12));
        content.setPrefWidth(560);

        Map<Long, ToggleGroup> answers = new HashMap<>();
        for (int index = 0; index < quiz.getQuestions().size(); index++) {
            Question question = quiz.getQuestions().get(index);

            VBox questionBox = new VBox(8);
            questionBox.getStyleClass().add("quiz-question-box");

            Label questionLabel = new Label((index + 1) + ". " + question.getQuestion());
            questionLabel.getStyleClass().add("quiz-question-label");
            questionLabel.setWrapText(true);
            questionBox.getChildren().add(questionLabel);

            ToggleGroup group = new ToggleGroup();
            answers.put(question.getId(), group);

            for (Choice currentChoice : question.getChoices()) {
                RadioButton radioButton = new RadioButton(currentChoice.getChoice());
                radioButton.setToggleGroup(group);
                radioButton.setUserData(currentChoice.getId());
                radioButton.getStyleClass().add("quiz-choice-button");
                questionBox.getChildren().add(radioButton);
            }

            content.getChildren().add(questionBox);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(420);
        dialog.getDialogPane().setContent(scrollPane);

        Node submitButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        submitButton.setDisable(true);
        answers.values().forEach(group -> group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
                submitButton.setDisable(answers.values().stream().anyMatch(currentGroup -> currentGroup.getSelectedToggle() == null))
        ));

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                Map<Long, Long> selectedAnswers = new HashMap<>();
                for (Question question : quiz.getQuestions()) {
                    ToggleGroup group = answers.get(question.getId());
                    if (group != null && group.getSelectedToggle() != null) {
                        selectedAnswers.put(question.getId(), (Long) group.getSelectedToggle().getUserData());
                    }
                }
                return quizService.submitQuiz(quiz.getId(), selectedAnswers, TEST_USER);
            }
            return null;
        });

        Optional<QuizResult> result = dialog.showAndWait();
        result.ifPresent(quizResult -> {
            if (quizResult.getId() != null) {
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "Quiz completed",
                        "Score: " + quizResult.getScore() + "/" + quizResult.getTotalQuestions() + " (" + formatPercentage(quizResult.getPercentage()) + ")",
                        "The result was saved for the current placeholder test user."
                );
                refreshQuizData();
            } else {
                showAlert(Alert.AlertType.ERROR, "Save failed", "The quiz result could not be saved.");
            }
        });
    }

    private String buildRatingLabel(Quiz quiz) {
        if (quiz == null || quiz.getQuizResults().isEmpty()) {
            return "N/A";
        }

        double average = quiz.getQuizResults().stream()
                .mapToDouble(result -> result.getPercentage() != null ? result.getPercentage() : 0.0)
                .average()
                .orElse(0.0);
        return formatPercentage(average);
    }

    private String buildBestScoreLabel(Quiz quiz) {
        return quiz.getQuizResults().stream()
                .map(QuizResult::getPercentage)
                .filter(percentage -> percentage != null)
                .max(Double::compareTo)
                .map(score -> "Best score: " + formatPercentage(score))
                .orElse("Best score: No attempts yet");
    }

    private String buildAttemptCountLabel(Quiz quiz) {
        int attemptCount = quiz.getQuizResults().size();
        return attemptCount == 1 ? "1 recorded attempt" : attemptCount + " recorded attempts";
    }

    private String formatPercentage(double value) {
        NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.FRANCE);
        percentFormat.setMinimumFractionDigits(0);
        percentFormat.setMaximumFractionDigits(0);
        return percentFormat.format(value / 100.0);
    }

    private void showAlert(Alert.AlertType type, String title, String header) {
        showAlert(type, title, header, null);
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleShowLeaderboard() {
        List<QuizResult> topResults = quizService.getAllQuizzes().stream()
                .flatMap(quiz -> quiz.getQuizResults().stream())
                .sorted(Comparator.comparingInt((QuizResult result) -> result.getScore() != null ? result.getScore() : 0).reversed())
                .limit(5)
                .toList();

        if (topResults.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Leaderboard", "No quiz results are available yet.", null);
            return;
        }

        StringBuilder content = new StringBuilder();
        for (int index = 0; index < topResults.size(); index++) {
            QuizResult result = topResults.get(index);
            content.append(index + 1)
                    .append(". ")
                    .append(result.getQuiz() != null ? result.getQuiz().getTitre() : "Unknown quiz")
                    .append(" - ")
                    .append(result.getScore())
                    .append("/")
                    .append(result.getTotalQuestions())
                    .append(" (")
                    .append(formatPercentage(result.getPercentage() != null ? result.getPercentage() : 0.0))
                    .append(")");
            if (result.getUser() != null) {
                content.append(" - ").append(result.getUser().getFullName());
            }
            content.append("\n");
        }

        showAlert(Alert.AlertType.INFORMATION, "Leaderboard", "Top 5 scores", content.toString());
    }

    @FXML
    private void handleRefreshQuizzes() {
        refreshQuizData();
        showAlert(Alert.AlertType.INFORMATION, "Refresh complete", "The quiz list has been refreshed.", null);
    }

    private void showResultDetails(QuizResult result) {
        String userInfo = result.getUser() != null
                ? result.getUser().getFullName() + " (" + result.getUser().getEmail() + ")"
                : "Unknown user";

        showAlert(
                Alert.AlertType.INFORMATION,
                "Result details",
                "Quiz: " + (result.getQuiz() != null ? result.getQuiz().getTitre() : "-"),
                "Score: " + result.getScore() + "/" + result.getTotalQuestions() + "\n"
                        + "Percentage: " + formatPercentage(result.getPercentage() != null ? result.getPercentage() : 0.0) + "\n"
                        + "Status: " + (Boolean.TRUE.equals(result.getPassed()) ? "Passed" : "Not passed") + "\n"
                        + "Played by: " + userInfo
        );
    }
}
