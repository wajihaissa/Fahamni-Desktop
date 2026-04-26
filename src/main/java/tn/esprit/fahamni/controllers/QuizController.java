package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.QuizAnswerAttempt;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizLeaderboardEntry;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.Models.quiz.QuizUserInsight;
import tn.esprit.fahamni.services.AdaptiveQuizService;
import tn.esprit.fahamni.services.AiQuizAssistantService;
import tn.esprit.fahamni.services.QuizCertificationMailer;
import tn.esprit.fahamni.services.QuizAnalyticsService;
import tn.esprit.fahamni.services.QuizService;
import tn.esprit.fahamni.utils.UserSession;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class QuizController {

    @FXML private FlowPane availableQuizzesBox;
    @FXML private VBox recentResultsBox;
    @FXML private Label quizzesTakenValue;
    @FXML private Label averageScoreValue;
    @FXML private Label subjectsMasteredValue;
    @FXML private Label quizResultsSummaryLabel;
    @FXML private Label quizPageStatusLabel;
    @FXML private CheckBox attemptedOnlyCheckBox;
    @FXML private ComboBox<String> quizSortCombo;
    @FXML private TextField quizSearchField;

    private final QuizService quizService = new QuizService();
    private final QuizAnalyticsService quizAnalyticsService = new QuizAnalyticsService();
    private final AdaptiveQuizService adaptiveQuizService = new AdaptiveQuizService();
    private final AiQuizAssistantService aiQuizAssistantService = new AiQuizAssistantService();
    private final QuizCertificationMailer quizCertificationMailer = new QuizCertificationMailer();
    private final Map<Long, List<String>> adaptiveQuizSourceCache = new HashMap<>();
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
        List<Quiz> visibleQuizzes = filterVisibleQuizzes(quizzes);
        renderFilteredQuizCards();
        renderRecentResults(visibleQuizzes);
        updateStats(visibleQuizzes);
    }

    private void renderFilteredQuizCards() {
        String query = quizSearchField != null ? quizSearchField.getText() : "";
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Quiz> filteredQuizzes = filterVisibleQuizzes(allQuizzes).stream()
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

        quizResultsSummaryLabel.setText(buildQuizResultsSummaryText(resultCount, showingAll));
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

        Label descriptionLabel = new Label(buildQuizDescription(quiz));
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
        resultCard.getStyleClass().add(Boolean.TRUE.equals(result.getPassed()) ? "result-card-passed" : "result-card-retry");
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
        gradeLabel.getStyleClass().add(Boolean.TRUE.equals(result.getPassed()) ? "result-grade-passed" : "result-grade-retry");
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
        dialog.setHeaderText(null);

        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.LEFT);
        ButtonType nextButtonType = new ButtonType("Next", ButtonBar.ButtonData.OTHER);
        ButtonType finishButtonType = new ButtonType("Finish Quiz", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(backButtonType, nextButtonType, finishButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().addAll("quiz-dialog-pane", "quiz-experience-dialog");
        dialog.getDialogPane().setPrefWidth(700);
        applyCurrentTheme(dialog.getDialogPane());

        VBox content = new VBox(18);
        content.getStyleClass().add("quiz-dialog-shell");
        content.setPadding(new Insets(18));
        content.setPrefWidth(620);

        VBox heroBox = new VBox(8);
        heroBox.getStyleClass().add("quiz-dialog-hero");

        Label keywordLabel = new Label(quiz.getKeyword().isBlank() ? "General quiz" : quiz.getKeyword());
        keywordLabel.getStyleClass().add("quiz-dialog-eyebrow");

        Label titleLabel = new Label(quiz.getTitre());
        titleLabel.getStyleClass().add("quiz-dialog-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(buildQuizStartSubtitle(quiz));
        subtitleLabel.getStyleClass().add("quiz-dialog-subtitle");
        subtitleLabel.setWrapText(true);

        heroBox.getChildren().addAll(keywordLabel, titleLabel, subtitleLabel);

        HBox progressHeader = new HBox(10);
        progressHeader.setAlignment(Pos.CENTER_LEFT);

        Label stepLabel = new Label();
        stepLabel.getStyleClass().add("quiz-progress-step");

        Label progressLabel = new Label();
        progressLabel.getStyleClass().add("quiz-progress-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        progressHeader.getChildren().addAll(stepLabel, spacer, progressLabel);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("quiz-progress-bar");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        VBox questionHost = new VBox();
        questionHost.getStyleClass().add("quiz-question-stage");

        content.getChildren().addAll(heroBox, progressHeader, progressBar, questionHost);

        ScrollPane contentScroll = new ScrollPane(content);
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScroll.setPrefViewportWidth(640);
        contentScroll.setPrefViewportHeight(620);
        contentScroll.getStyleClass().add("quiz-dialog-scroll");

        dialog.getDialogPane().setContent(contentScroll);

        Map<Long, ToggleGroup> answers = new HashMap<>();
        int totalQuestions = quiz.getQuestions().size();
        int[] currentIndex = {0};

        Button backButton = (Button) dialog.getDialogPane().lookupButton(backButtonType);
        Button nextButton = (Button) dialog.getDialogPane().lookupButton(nextButtonType);
        Button finishButton = (Button) dialog.getDialogPane().lookupButton(finishButtonType);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);

        backButton.getStyleClass().addAll("review-button", "quiz-nav-button", "quiz-nav-button-secondary");
        nextButton.getStyleClass().addAll("start-quiz-button", "quiz-nav-button", "quiz-nav-button-primary");
        finishButton.getStyleClass().addAll("start-quiz-button", "quiz-nav-button", "quiz-nav-button-primary");
        cancelButton.getStyleClass().addAll("review-button", "quiz-nav-button", "quiz-nav-button-secondary");

        Runnable refreshControls = () -> updateQuizStepState(
                quiz,
                answers,
                currentIndex[0],
                totalQuestions,
                stepLabel,
                progressLabel,
                progressBar,
                backButton,
                nextButton,
                finishButton
        );

        Runnable refreshStep = () -> {
            renderQuestionStep(quiz, answers, currentIndex[0], totalQuestions, questionHost, refreshControls);
            refreshControls.run();
        };

        backButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                refreshStep.run();
            }
        });

        nextButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (currentIndex[0] < totalQuestions - 1) {
                currentIndex[0]++;
                refreshStep.run();
            }
        });

        refreshStep.run();

        dialog.setResultConverter(button -> {
            if (button == finishButtonType) {
                Map<Long, Long> selectedAnswers = new HashMap<>();
                for (Question question : quiz.getQuestions()) {
                    ToggleGroup group = answers.get(question.getId());
                    if (group != null && group.getSelectedToggle() != null) {
                        selectedAnswers.put(question.getId(), (Long) group.getSelectedToggle().getUserData());
                    }
                }
                return quizService.submitQuiz(quiz.getId(), selectedAnswers, getQuizUser());
            }
            return null;
        });

        Optional<QuizResult> result = dialog.showAndWait();
        result.ifPresent(quizResult -> {
            if (quizResult.getId() != null) {
                showQuizCompletionDialog(quiz, quizResult);
                refreshQuizData();
                quizCertificationMailer.sendPassCertificateAsync(quizResult.getUser(), quiz, quizResult)
                        .thenAccept(mailDispatchResult -> Platform.runLater(() -> handleCertificateMailResult(mailDispatchResult)));
            } else {
                showAlert(Alert.AlertType.ERROR, "Save failed", "The quiz result could not be saved.");
            }
        });
    }

    private void renderQuestionStep(
            Quiz quiz,
            Map<Long, ToggleGroup> answers,
            int questionIndex,
            int totalQuestions,
            VBox questionHost,
            Runnable onAnswerChanged
    ) {
        Question question = quiz.getQuestions().get(questionIndex);
        questionHost.getChildren().setAll(buildQuestionCard(
                quiz,
                question,
                questionIndex,
                totalQuestions,
                answers,
                onAnswerChanged
        ));
    }

    private void updateQuizStepState(
            Quiz quiz,
            Map<Long, ToggleGroup> answers,
            int questionIndex,
            int totalQuestions,
            Label stepLabel,
            Label progressLabel,
            ProgressBar progressBar,
            Button backButton,
            Button nextButton,
            Button finishButton
    ) {
        long answeredCount = quiz.getQuestions().stream()
                .filter(currentQuestion -> {
                    ToggleGroup group = answers.get(currentQuestion.getId());
                    return group != null && group.getSelectedToggle() != null;
                })
                .count();

        ToggleGroup currentGroup = answers.get(quiz.getQuestions().get(questionIndex).getId());
        boolean answered = currentGroup != null && currentGroup.getSelectedToggle() != null;

        progressBar.setProgress(answeredCount / (double) totalQuestions);
        stepLabel.setText("Question " + (questionIndex + 1) + " of " + totalQuestions);
        progressLabel.setText((int) Math.round((answeredCount * 100.0) / totalQuestions) + "% complete");

        backButton.setDisable(questionIndex == 0);
        nextButton.setVisible(questionIndex < totalQuestions - 1);
        nextButton.setManaged(questionIndex < totalQuestions - 1);
        nextButton.setDisable(!answered);
        finishButton.setVisible(questionIndex == totalQuestions - 1);
        finishButton.setManaged(questionIndex == totalQuestions - 1);
        finishButton.setDisable(!answered);
    }

    private VBox buildQuestionCard(
            Quiz quiz,
            Question question,
            int questionIndex,
            int totalQuestions,
            Map<Long, ToggleGroup> answers,
            Runnable onAnswerChanged
    ) {
        VBox questionBox = new VBox(16);
        questionBox.getStyleClass().addAll("quiz-question-box", "quiz-question-stage-card");

        HBox questionMeta = new HBox(10);
        questionMeta.setAlignment(Pos.CENTER_LEFT);

        Label numberBadge = new Label((questionIndex + 1) + "/" + totalQuestions);
        numberBadge.getStyleClass().add("quiz-step-badge");

        Label questionPrompt = new Label("Choose the best answer");
        questionPrompt.getStyleClass().add("quiz-step-copy");

        questionMeta.getChildren().addAll(numberBadge, questionPrompt);

        Label questionLabel = new Label(question.getQuestion());
        questionLabel.getStyleClass().add("quiz-question-label");
        questionLabel.setWrapText(true);

        ToggleGroup group = answers.computeIfAbsent(question.getId(), ignored -> new ToggleGroup());

        VBox choicesBox = new VBox(12);
        for (Choice currentChoice : question.getChoices()) {
            RadioButton radioButton = new RadioButton(currentChoice.getChoice());
            radioButton.setToggleGroup(group);
            radioButton.setUserData(currentChoice.getId());
            radioButton.getStyleClass().add("quiz-choice-button");
            radioButton.setWrapText(true);
            radioButton.setMaxWidth(Double.MAX_VALUE);
            radioButton.setOnAction(event -> onAnswerChanged.run());
            choicesBox.getChildren().add(radioButton);
        }

        Label hintLabel = new Label();
        hintLabel.getStyleClass().add("quiz-hint-label");
        hintLabel.setWrapText(true);
        hintLabel.setVisible(false);
        hintLabel.setManaged(false);

        Button hintButton = new Button("Get AI Hint");
        hintButton.getStyleClass().addAll("quiz-hint-button", "review-button");
        hintButton.setOnAction(event -> {
            Toggle selectedToggle = group.getSelectedToggle();
            Long selectedChoiceId = selectedToggle != null ? (Long) selectedToggle.getUserData() : null;
            AiQuizAssistantService.GeneratedHint generatedHint =
                    aiQuizAssistantService.generateHint(quiz, question, selectedChoiceId);
            hintLabel.setText(generatedHint.success()
                    ? "Hint: " + generatedHint.text()
                    : generatedHint.message());
            hintLabel.setVisible(true);
            hintLabel.setManaged(true);
        });

        VBox utilityBox = new VBox(10, hintButton, hintLabel);
        utilityBox.getStyleClass().add("quiz-utility-box");

        questionBox.getChildren().addAll(questionMeta, questionLabel, choicesBox, utilityBox);
        return questionBox;
    }

    private void showQuizCompletionDialog(Quiz quiz, QuizResult result) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Quiz result");
        dialog.setHeaderText(null);
        ButtonType reviewButtonType = new ButtonType("Review Answers", ButtonBar.ButtonData.LEFT);
        ButtonType retryMistakesButtonType = new ButtonType("Retake Mistakes", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(reviewButtonType, retryMistakesButtonType, ButtonType.OK);
        dialog.getDialogPane().getStyleClass().add("quiz-dialog-pane");
        applyCurrentTheme(dialog.getDialogPane());

        VBox content = new VBox(18);
        content.getStyleClass().addAll("quiz-dialog-shell", "quiz-result-shell");
        content.setPadding(new Insets(18));
        content.setPrefWidth(560);

        Label statusBadge = new Label(Boolean.TRUE.equals(result.getPassed()) ? "Passed" : "Keep going");
        statusBadge.getStyleClass().addAll(
                "quiz-result-status",
                Boolean.TRUE.equals(result.getPassed()) ? "quiz-result-status-passed" : "quiz-result-status-retry"
        );

        Label titleLabel = new Label(quiz.getTitre());
        titleLabel.getStyleClass().add("quiz-dialog-title");
        titleLabel.setWrapText(true);

        Label summaryLabel = new Label(Boolean.TRUE.equals(result.getPassed())
                ? "Nice work. You finished the quiz with a strong result."
                : "You finished the quiz. Review the result and try again when you're ready.");
        summaryLabel.getStyleClass().add("quiz-dialog-subtitle");
        summaryLabel.setWrapText(true);

        List<String> sourceTitles = getAdaptiveSourceQuizTitles(quiz);
        Label sourceLabel = new Label(sourceTitles.isEmpty()
                ? ""
                : "Built from: " + joinSourceTitles(sourceTitles));
        sourceLabel.getStyleClass().add("quiz-dialog-subtitle");
        sourceLabel.setWrapText(true);
        sourceLabel.setManaged(!sourceTitles.isEmpty());
        sourceLabel.setVisible(!sourceTitles.isEmpty());

        VBox scoreHero = new VBox(4);
        scoreHero.getStyleClass().add("quiz-result-hero");

        Label scoreLabel = new Label(result.getScore() + "/" + result.getTotalQuestions());
        scoreLabel.getStyleClass().add("quiz-result-score");

        Label percentageLabel = new Label(formatPercentage(result.getPercentage() != null ? result.getPercentage() : 0.0));
        percentageLabel.getStyleClass().add("quiz-result-percentage");
        scoreHero.getChildren().addAll(scoreLabel, percentageLabel);

        HBox statsRow = new HBox(12);
        User activeUser = getQuizUser();
        QuizUserInsight insight = activeUser.getId() != null ? quizAnalyticsService.getUserInsight(activeUser.getId()) : null;
        String recommendation = insight != null
                ? insight.getRecommendedDifficulty() + " focus"
                : "Medium focus";
        String weakestTopic = insight != null && !insight.getWeakestTopics().isEmpty()
                ? insight.getWeakestTopics().get(0)
                : (quiz.getKeyword().isBlank() ? "General" : quiz.getKeyword());
        statsRow.getChildren().addAll(
                buildResultStat("Topic", quiz.getKeyword().isBlank() ? "General" : quiz.getKeyword()),
                buildResultStat("Questions", String.valueOf(result.getTotalQuestions())),
                buildResultStat("Progress", buildProgressDeltaLabel(insight)),
                buildResultStat("Next Focus", weakestTopic + " / " + recommendation)
        );

        HBox analyticsRow = new HBox(12);
        analyticsRow.getChildren().addAll(
                buildResultStat("Current Streak", insight != null ? insight.getCurrentStreak() + " passed" : "0 passed"),
                buildResultStat("Recent Avg", insight != null ? formatPercentage(insight.getRecentAveragePercentage()) : formatPercentage(0.0)),
                buildResultStat("Previous Avg", insight != null ? formatPercentage(insight.getPreviousAveragePercentage()) : formatPercentage(0.0))
        );

        content.getChildren().addAll(statusBadge, titleLabel, summaryLabel, sourceLabel, scoreHero, statsRow, analyticsRow);
        dialog.getDialogPane().setContent(content);

        Button reviewButton = (Button) dialog.getDialogPane().lookupButton(reviewButtonType);
        reviewButton.getStyleClass().addAll("review-button", "quiz-nav-button", "quiz-nav-button-secondary");

        Button retryMistakesButton = (Button) dialog.getDialogPane().lookupButton(retryMistakesButtonType);
        retryMistakesButton.getStyleClass().addAll("start-quiz-button", "quiz-nav-button", "quiz-nav-button-primary");

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().addAll("review-button", "quiz-nav-button", "quiz-nav-button-secondary");

        reviewButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            showResultDetails(result);
        });

        retryMistakesButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            handleGenerateMistakeRecoveryQuiz(result);
        });

        dialog.showAndWait();
    }

    private void applyCurrentTheme(javafx.scene.control.DialogPane dialogPane) {
        if (dialogPane == null || availableQuizzesBox == null || availableQuizzesBox.getScene() == null) {
            return;
        }
        dialogPane.getStylesheets().setAll(availableQuizzesBox.getScene().getStylesheets());
    }

    private VBox buildResultStat(String label, String value) {
        VBox statCard = new VBox(4);
        statCard.getStyleClass().add("quiz-result-stat-card");
        statCard.setPrefWidth(160);

        Label titleLabel = new Label(label);
        titleLabel.getStyleClass().add("quiz-result-stat-label");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("quiz-result-stat-value");

        statCard.getChildren().addAll(titleLabel, valueLabel);
        return statCard;
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

    private String buildQuizResultsSummaryText(int resultCount, boolean showingAll) {
        if (showingAll) {
            return resultCount + " quiz available";
        }
        return resultCount + " quiz match your search";
    }

    private String formatPercentage(double value) {
        NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.FRANCE);
        percentFormat.setMinimumFractionDigits(0);
        percentFormat.setMaximumFractionDigits(0);
        return percentFormat.format(value / 100.0);
    }

    private void showPageStatus(String message, String styleClass) {
        if (quizPageStatusLabel == null) {
            return;
        }

        quizPageStatusLabel.setText(message == null ? "" : message);
        quizPageStatusLabel.getStyleClass().removeAll(
                "quiz-page-status-success",
                "quiz-page-status-info",
                "quiz-page-status-error"
        );
        quizPageStatusLabel.getStyleClass().add(styleClass);
        quizPageStatusLabel.setVisible(message != null && !message.isBlank());
        quizPageStatusLabel.setManaged(message != null && !message.isBlank());
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
        List<QuizLeaderboardEntry> leaderboardEntries = quizAnalyticsService.getLeaderboardEntries(5);
        if (leaderboardEntries.isEmpty()) {
            showPageStatus("No leaderboard data yet. Complete a quiz first.", "quiz-page-status-info");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Leaderboard");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().getStyleClass().addAll("quiz-dialog-pane", "leaderboard-dialog-pane");
        applyCurrentTheme(dialog.getDialogPane());

        VBox content = new VBox(18);
        content.getStyleClass().addAll("quiz-dialog-shell", "leaderboard-shell");
        content.setPadding(new Insets(20));
        content.setPrefWidth(640);

        Label eyebrowLabel = new Label("Quiz Leaderboard");
        eyebrowLabel.getStyleClass().add("quiz-dialog-eyebrow");

        Label titleLabel = new Label("Top weighted performers");
        titleLabel.getStyleClass().add("quiz-dialog-title");

        Label subtitleLabel = new Label("A blended ranking based on average score, best score, pass rate, consistency, and improvement.");
        subtitleLabel.getStyleClass().add("quiz-dialog-subtitle");
        subtitleLabel.setWrapText(true);

        HBox leaderboardStats = new HBox(12,
                buildResultStat("Players", String.valueOf(leaderboardEntries.size())),
                buildResultStat("Top Score", String.valueOf(Math.round(leaderboardEntries.get(0).getWeightedScore()))),
                buildResultStat("Best Avg", formatPercentage(
                        leaderboardEntries.stream()
                                .mapToDouble(QuizLeaderboardEntry::getAveragePercentage)
                                .max()
                                .orElse(0.0)
                ))
        );
        leaderboardStats.getStyleClass().add("leaderboard-stats-row");

        VBox entryList = new VBox(12);
        entryList.getStyleClass().add("leaderboard-entry-list");
        leaderboardEntries.forEach(entry -> entryList.getChildren().add(buildLeaderboardCard(entry)));

        content.getChildren().addAll(eyebrowLabel, titleLabel, subtitleLabel, leaderboardStats, entryList);
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().addAll("start-quiz-button", "quiz-nav-button", "quiz-nav-button-primary");

        dialog.showAndWait();
    }

    private HBox buildLeaderboardCard(QuizLeaderboardEntry entry) {
        HBox card = new HBox(16);
        card.getStyleClass().add("leaderboard-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label rankBadge = new Label("#" + entry.getRank());
        rankBadge.getStyleClass().add("leaderboard-rank-badge");
        rankBadge.setMinWidth(64);
        rankBadge.setAlignment(Pos.CENTER);

        VBox body = new VBox(6);
        body.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(body, Priority.ALWAYS);

        Label nameLabel = new Label(entry.getUserName());
        nameLabel.getStyleClass().add("leaderboard-name");

        Label summaryLabel = new Label(
                "Weighted " + Math.round(entry.getWeightedScore())
                        + "  |  Avg " + formatPercentage(entry.getAveragePercentage())
                        + "  |  Best " + formatPercentage(entry.getBestPercentage())
        );
        summaryLabel.getStyleClass().add("leaderboard-summary");
        summaryLabel.setWrapText(true);

        Label metaLabel = new Label(
                "Pass " + formatPercentage(entry.getPassRate())
                        + "  |  Attempts " + entry.getAttempts()
                        + "  |  Consistency " + Math.round(entry.getConsistencyScore())
        );
        metaLabel.getStyleClass().add("leaderboard-meta");
        metaLabel.setWrapText(true);

        body.getChildren().addAll(nameLabel, summaryLabel, metaLabel);

        VBox scoreBox = new VBox(4);
        scoreBox.getStyleClass().add("leaderboard-score-box");
        scoreBox.setAlignment(Pos.CENTER_RIGHT);

        Label weightedValue = new Label(String.valueOf(Math.round(entry.getWeightedScore())));
        weightedValue.getStyleClass().add("leaderboard-score-value");

        Label weightedLabel = new Label("Weighted");
        weightedLabel.getStyleClass().add("leaderboard-score-label");

        scoreBox.getChildren().addAll(weightedValue, weightedLabel);

        card.getChildren().addAll(rankBadge, body, scoreBox);
        return card;
    }

    @FXML
    private void handleRefreshQuizzes() {
        refreshQuizData();
        showPageStatus("Quiz list refreshed successfully.", "quiz-page-status-success");
    }

    @FXML
    private void handleGenerateAdaptiveQuiz() {
        Quiz adaptiveQuiz = adaptiveQuizService.generateAdaptiveQuizForUser(getQuizUser(), 5);
        if (adaptiveQuiz == null) {
            showPageStatus(
                    "Complete a couple of quizzes first so adaptive mode can learn your weak topics.",
                    "quiz-page-status-info"
            );
            return;
        }

        refreshQuizData();
        adaptiveQuizSourceCache.remove(adaptiveQuiz.getId());
        showPageStatus(
                "Adaptive quiz ready: "
                        + (adaptiveQuiz.getKeyword().isBlank() ? "General" : adaptiveQuiz.getKeyword())
                        + " focus opened for you.",
                "quiz-page-status-success"
        );
        openGeneratedQuiz(adaptiveQuiz);
    }

    @FXML
    private void handleGenerateMistakeRecoveryQuiz() {
        handleGenerateMistakeRecoveryQuiz(null);
    }

    private void handleGenerateMistakeRecoveryQuiz(QuizResult sourceResult) {
        if (sourceResult != null && sourceResult.getAnswerAttempts().isEmpty() && sourceResult.getId() != null) {
            quizService.getAnswerAttemptsForResult(sourceResult.getId()).forEach(sourceResult::addAnswerAttempt);
        }

        Quiz mistakeRecoveryQuiz = adaptiveQuizService.generateMistakeRecoveryQuizForUser(getQuizUser(), sourceResult, 5);
        if (mistakeRecoveryQuiz == null) {
            showPageStatus(
                    "Retake-mistakes mode is available after at least one missed question.",
                    "quiz-page-status-info"
            );
            return;
        }

        refreshQuizData();
        adaptiveQuizSourceCache.remove(mistakeRecoveryQuiz.getId());
        showPageStatus(
                "Mistake recovery quiz ready and opened with your most-missed questions.",
                "quiz-page-status-success"
        );
        openGeneratedQuiz(mistakeRecoveryQuiz);
    }

    private String buildQuizDescription(Quiz quiz) {
        if (quiz == null) {
            return "A quick knowledge check.";
        }

        String topic = quiz.getKeyword().isBlank() ? "General" : quiz.getKeyword();
        List<String> sourceTitles = getAdaptiveSourceQuizTitles(quiz);
        if (!sourceTitles.isEmpty()) {
            if (quiz.getKeyword().startsWith("mistake-retry-")) {
                return "A recovery quiz on '" + topic + "' built from questions you previously missed in: " + joinSourceTitles(sourceTitles) + ".";
            }
            return "An adaptive quiz on '" + topic + "' built from: " + joinSourceTitles(sourceTitles) + ".";
        }
        return "A quick knowledge check about '" + topic + "'.";
    }

    private String buildQuizStartSubtitle(Quiz quiz) {
        List<String> sourceTitles = getAdaptiveSourceQuizTitles(quiz);
        if (!sourceTitles.isEmpty()) {
            if (quiz.getKeyword().startsWith("mistake-retry-")) {
                return "Answer one question at a time. This recovery set focuses on earlier misses from: " + joinSourceTitles(sourceTitles) + ".";
            }
            return "Answer one question at a time. This adaptive set pulls from: " + joinSourceTitles(sourceTitles) + ".";
        }
        return "Answer one question at a time and move through the quiz at your own pace.";
    }

    private List<String> getAdaptiveSourceQuizTitles(Quiz quiz) {
        if (quiz == null || quiz.getId() == null || !isGeneratedPracticeQuiz(quiz)) {
            return List.of();
        }
        return adaptiveQuizSourceCache.computeIfAbsent(quiz.getId(), quizService::getSourceQuizTitlesForQuiz);
    }

    private boolean isGeneratedPracticeQuiz(Quiz quiz) {
        if (quiz == null || quiz.getKeyword().isBlank()) {
            return false;
        }
        return quiz.getKeyword().startsWith("adaptive-") || quiz.getKeyword().startsWith("mistake-retry-");
    }

    private String joinSourceTitles(List<String> sourceTitles) {
        if (sourceTitles == null || sourceTitles.isEmpty()) {
            return "";
        }
        if (sourceTitles.size() == 1) {
            return sourceTitles.get(0);
        }
        if (sourceTitles.size() == 2) {
            return sourceTitles.get(0) + " and " + sourceTitles.get(1);
        }
        return String.join(", ", sourceTitles.subList(0, sourceTitles.size() - 1))
                + ", and "
                + sourceTitles.get(sourceTitles.size() - 1);
    }

    private String buildProgressDeltaLabel(QuizUserInsight insight) {
        if (insight == null) {
            return "No trend yet";
        }

        double delta = insight.getImprovementDelta();
        if (Math.abs(delta) < 0.5) {
            return "Stable";
        }

        String prefix = delta > 0 ? "+" : "";
        return prefix + Math.round(delta) + " pts";
    }

    private String buildReviewSubtitle(QuizResult result) {
        User activeUser = getQuizUser();
        QuizUserInsight insight = activeUser.getId() != null ? quizAnalyticsService.getUserInsight(activeUser.getId()) : null;
        String trend = insight == null
                ? "Keep reviewing your explanations to lock in the concepts."
                : "Recent avg " + formatPercentage(insight.getRecentAveragePercentage())
                + " | Previous avg " + formatPercentage(insight.getPreviousAveragePercentage())
                + " | Streak " + insight.getCurrentStreak();
        return "Review each answer, compare it to the correct choice, and use the explanation to tighten the weak spots. " + trend;
    }

    private User getQuizUser() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser != null && currentUser.getId() != null && currentUser.getId() > 0) {
            return currentUser;
        }
        return TEST_USER;
    }

    private List<Quiz> filterVisibleQuizzes(List<Quiz> quizzes) {
        if (quizzes == null || quizzes.isEmpty()) {
            return List.of();
        }

        return quizzes.stream()
                .filter(this::isQuizVisibleToCurrentUser)
                .toList();
    }

    private boolean isQuizVisibleToCurrentUser(Quiz quiz) {
        if (!isGeneratedPracticeQuiz(quiz)) {
            return true;
        }

        Integer ownerUserId = extractGeneratedQuizOwnerUserId(quiz);
        Integer currentUserId = getQuizUser().getId();
        return ownerUserId != null && currentUserId != null && ownerUserId.equals(currentUserId);
    }

    private Integer extractGeneratedQuizOwnerUserId(Quiz quiz) {
        if (quiz == null || quiz.getKeyword().isBlank()) {
            return null;
        }

        String[] tokens = quiz.getKeyword().split("-");
        if (tokens.length < 2) {
            return null;
        }

        String userToken = tokens[tokens.length - 2];
        try {
            return Integer.valueOf(userToken);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void openGeneratedQuiz(Quiz generatedQuiz) {
        if (generatedQuiz == null || generatedQuiz.getId() == null) {
            return;
        }

        Quiz refreshedQuiz = allQuizzes.stream()
                .filter(quiz -> generatedQuiz.getId().equals(quiz.getId()))
                .findFirst()
                .orElseGet(() -> quizService.getQuizById(generatedQuiz.getId()));

        if (refreshedQuiz != null && isQuizVisibleToCurrentUser(refreshedQuiz)) {
            handleStartQuiz(refreshedQuiz);
        }
    }

    private void handleCertificateMailResult(QuizCertificationMailer.MailDispatchResult result) {
        if (result == null) {
            return;
        }

        if (result.skipped()) {
            showPageStatus(result.message(), "quiz-page-status-info");
            return;
        }

        if (result.success()) {
            showPageStatus("Certificate email sent to " + safeText(result.recipient(), "your inbox") + ".", "quiz-page-status-success");
            return;
        }

        showPageStatus(result.message(), "quiz-page-status-error");
    }

    private List<Node> buildReviewCards(QuizResult result) {
        if (result == null || result.getQuiz() == null || result.getQuiz().getQuestions().isEmpty()) {
            return List.<Node>of(new Label("No review data is available for this attempt yet."));
        }

        if (result.getAnswerAttempts().isEmpty() && result.getId() != null) {
            quizService.getAnswerAttemptsForResult(result.getId()).forEach(result::addAnswerAttempt);
        }

        Map<Long, QuizAnswerAttempt> attemptsByQuestionId = result.getAnswerAttempts().stream()
                .filter(attempt -> attempt.getQuestionId() != null)
                .collect(Collectors.toMap(QuizAnswerAttempt::getQuestionId, attempt -> attempt, (left, right) -> left, HashMap::new));

        return result.getQuiz().getQuestions().stream()
                .map(question -> buildReviewCard(question, attemptsByQuestionId.get(question.getId())))
                .collect(Collectors.toList());
    }

    private VBox buildReviewCard(Question question, QuizAnswerAttempt attempt) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("quiz-question-stage-card", "quiz-result-stat-card");

        Choice correctChoice = findCorrectChoice(question);
        Choice selectedChoice = findChoiceById(question, attempt != null ? attempt.getSelectedChoiceId() : null);
        boolean correct = attempt != null && Boolean.TRUE.equals(attempt.getCorrect());

        Label promptLabel = new Label(question.getQuestion());
        promptLabel.getStyleClass().add("quiz-question-label");
        promptLabel.setWrapText(true);

        Label metaLabel = new Label("Topic: " + safeText(question.getTopic(), "General")
                + " | Difficulty: " + safeText(question.getDifficulty(), "Medium")
                + " | " + (correct ? "Correct" : "Needs review"));
        metaLabel.getStyleClass().add("quiz-dialog-subtitle");
        metaLabel.setWrapText(true);

        Label selectedLabel = new Label("Your answer: " + (selectedChoice != null ? selectedChoice.getChoice() : "No answer"));
        selectedLabel.getStyleClass().add("quiz-hint-label");
        selectedLabel.setWrapText(true);

        Label correctLabel = new Label("Correct answer: " + (correctChoice != null ? correctChoice.getChoice() : "Unavailable"));
        correctLabel.getStyleClass().add("quiz-hint-label");
        correctLabel.setWrapText(true);

        String explanationText = !safeText(question.getExplanation(), "").isBlank()
                ? question.getExplanation()
                : !safeText(question.getHint(), "").isBlank()
                ? question.getHint()
                : "Revisit the core concept behind this question and compare why the correct choice fits better than the distractors.";
        Label explanationLabel = new Label("Why: " + explanationText);
        explanationLabel.getStyleClass().add("quiz-hint-label");
        explanationLabel.setWrapText(true);

        card.getChildren().addAll(promptLabel, metaLabel, selectedLabel, correctLabel, explanationLabel);
        return card;
    }

    private Choice findCorrectChoice(Question question) {
        if (question == null) {
            return null;
        }
        return question.getChoices().stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .findFirst()
                .orElse(null);
    }

    private Choice findChoiceById(Question question, Long choiceId) {
        if (question == null || choiceId == null) {
            return null;
        }
        return question.getChoices().stream()
                .filter(choice -> choiceId.equals(choice.getId()))
                .findFirst()
                .orElse(null);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void showResultDetails(QuizResult result) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Result review");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().getStyleClass().add("quiz-dialog-pane");
        applyCurrentTheme(dialog.getDialogPane());

        VBox content = new VBox(18);
        content.getStyleClass().addAll("quiz-dialog-shell", "quiz-result-shell");
        content.setPadding(new Insets(18));
        content.setPrefWidth(700);

        Label titleLabel = new Label(result.getQuiz() != null ? result.getQuiz().getTitre() : "Quiz review");
        titleLabel.getStyleClass().add("quiz-dialog-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(buildReviewSubtitle(result));
        subtitleLabel.getStyleClass().add("quiz-dialog-subtitle");
        subtitleLabel.setWrapText(true);

        VBox summaryRow = new VBox(12);
        HBox statsRow = new HBox(12,
                buildResultStat("Score", result.getScore() + "/" + result.getTotalQuestions()),
                buildResultStat("Percentage", formatPercentage(result.getPercentage() != null ? result.getPercentage() : 0.0)),
                buildResultStat("Status", Boolean.TRUE.equals(result.getPassed()) ? "Passed" : "Retry"),
                buildResultStat("Player", result.getUser() != null ? result.getUser().getFullName() : "Unknown")
        );
        summaryRow.getChildren().add(statsRow);

        VBox reviewList = new VBox(12);
        reviewList.getChildren().addAll(buildReviewCards(result));

        ScrollPane scrollPane = new ScrollPane(reviewList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportHeight(520);
        scrollPane.getStyleClass().add("quiz-dialog-scroll");

        content.getChildren().addAll(titleLabel, subtitleLabel, summaryRow, scrollPane);
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().addAll("start-quiz-button", "quiz-nav-button", "quiz-nav-button-primary");

        dialog.showAndWait();
    }
}
