package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
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

    private final QuizService quizService = new QuizService();
    // Static test user used while the user session system is being integrated.
    private static final User TEST_USER = new User(1L, "Quiz Tester", "quiz.tester@fahamni.tn", "", UserRole.USER);

    @FXML
    private void initialize() {
        refreshQuizData();
    }

    private void refreshQuizData() {
        List<Quiz> quizzes = quizService.getAllQuizzes();
        if (quizzes.isEmpty()) {
            seedTestQuizzes();
            quizzes = quizService.getAllQuizzes();
        }
        renderQuizCards(quizzes);
        renderRecentResults(quizzes);
        updateStats(quizzes);
    }

    private void seedTestQuizzes() {
        try {
            Quiz quiz = new Quiz();
            quiz.setTitre("Valorant Certification Quiz");
            quiz.setKeyword("Valorant");

            Question q1 = new Question();
            q1.setQuestion("Quel est le rôle du Spike dans Valorant ?");
            q1.addChoice(buildChoice("Désamorcer les objets", false));
            q1.addChoice(buildChoice("Activer une arme spéciale", false));
            q1.addChoice(buildChoice("Poser une bombe pour gagner une manche", true));
            q1.addChoice(buildChoice("Soigner une équipe", false));
            quiz.addQuestion(q1);

            Question q2 = new Question();
            q2.setQuestion("Quelle capacité permet de voir les ennemis à travers les murs ?");
            q2.addChoice(buildChoice("Cypher - Fil de caméra", false));
            q2.addChoice(buildChoice("Sova - Flèche éclairante", false));
            q2.addChoice(buildChoice("Sage - Barrière de lumière", false));
            q2.addChoice(buildChoice("Skye - Suivi des traces", true));
            quiz.addQuestion(q2);

            quizService.createQuiz(quiz);
        } catch (Exception ignored) {
            // Ignore seed failures in environments with no DB access
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
            Label emptyLabel = new Label("Aucun quiz disponible pour le moment. Contactez l'administrateur.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("quiz-empty-message");
            availableQuizzesBox.getChildren().add(emptyLabel);
            return;
        }

        for (Quiz quiz : quizzes) {
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
            Label infoLabel = new Label(quiz.getQuestions().size() + " questions • " + (quiz.getQuizResults().size() > 0 ? "Résultats disponibles" : "Pas encore joué"));
            infoLabel.getStyleClass().add("quiz-info");
            Label descriptionLabel = new Label("Un quiz rapide sur le thème '" + quiz.getKeyword() + "'.");
            descriptionLabel.setWrapText(true);
            descriptionLabel.getStyleClass().add("quiz-description");
            cardBody.getChildren().addAll(titleLabel, infoLabel, descriptionLabel);
            HBox.setHgrow(cardBody, Priority.ALWAYS);

            VBox actionBox = new VBox(10);
            actionBox.setAlignment(Pos.CENTER);
            Label ratingLabel = new Label("★ " + buildRatingLabel(quiz));
            ratingLabel.getStyleClass().add("quiz-rating");
            Button startButton = new Button("Start Quiz");
            startButton.getStyleClass().add("start-quiz-button");
            startButton.setOnAction(event -> handleStartQuiz(quiz));
            actionBox.getChildren().addAll(ratingLabel, startButton);

            quizCard.getChildren().addAll(cardBody, actionBox);
            availableQuizzesBox.getChildren().add(quizCard);
        }
    }

    private void renderRecentResults(List<Quiz> quizzes) {
        recentResultsBox.getChildren().clear();
        if (quizzes == null || quizzes.isEmpty()) {
            Label emptyLabel = new Label("Aucun résultat récent disponible.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("result-empty-message");
            recentResultsBox.getChildren().add(emptyLabel);
            return;
        }

        quizzes.stream()
                .flatMap(q -> q.getQuizResults().stream())
                .sorted((r1, r2) -> r2.getCompletedAt().compareTo(r1.getCompletedAt()))
                .limit(5)
                .forEach(result -> {
                    HBox resultCard = new HBox(15);
                    resultCard.getStyleClass().add("result-card");
                    resultCard.setAlignment(Pos.CENTER_LEFT);
                    resultCard.setPadding(new Insets(14));

                    VBox resultBody = new VBox(4);
                    resultBody.setAlignment(Pos.CENTER_LEFT);
                    Label resultTitle = new Label(result.getQuiz() != null ? result.getQuiz().getTitre() : "Quiz inconnu");
                    resultTitle.getStyleClass().add("result-title");
                    Label resultDate = new Label("Terminé le " + (result.getCompletedAt() != null ? result.getCompletedAt().toString().substring(0, 10) : "--"));
                    resultDate.getStyleClass().add("result-date");
                    resultBody.getChildren().addAll(resultTitle, resultDate);
                    HBox.setHgrow(resultBody, Priority.ALWAYS);

                    VBox resultScore = new VBox(2);
                    resultScore.setAlignment(Pos.CENTER);
                    Label scoreLabel = new Label(result.getScore() + "/" + result.getTotalQuestions() + " (" + formatPercentage(result.getPercentage()) + ")");
                    scoreLabel.getStyleClass().add("result-score");
                    Label gradeLabel = new Label(result.getPassed() != null && result.getPassed() ? "Passed" : "Try again");
                    gradeLabel.getStyleClass().add("result-grade");
                    resultScore.getChildren().addAll(scoreLabel, gradeLabel);

                    Button reviewButton = new Button("Review");
                    reviewButton.getStyleClass().add("review-button");
                    reviewButton.setOnAction(event -> showResultDetails(result));

                    resultCard.getChildren().addAll(resultBody, resultScore, reviewButton);
                    recentResultsBox.getChildren().add(resultCard);
                });

        if (recentResultsBox.getChildren().isEmpty()) {
            Label emptyLabel = new Label("Aucun résultat récent enregistré.");
            emptyLabel.setWrapText(true);
            emptyLabel.getStyleClass().add("result-empty-message");
            recentResultsBox.getChildren().add(emptyLabel);
        }
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
            showAlert(Alert.AlertType.INFORMATION, "Quiz non disponible", "Ce quiz n'est pas prêt ou ne contient aucune question.");
            return;
        }

        Dialog<QuizResult> dialog = new Dialog<>();
        dialog.setTitle("Démarrer le quiz");
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
        answers.values().forEach(group -> group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            submitButton.setDisable(answers.values().stream().anyMatch(g -> g.getSelectedToggle() == null));
        }));

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
                showAlert(Alert.AlertType.INFORMATION,
                        "Quiz terminé",
                        "Score : " + quizResult.getScore() + "/" + quizResult.getTotalQuestions() + " (" + formatPercentage(quizResult.getPercentage()) + ")", 
                        "Résultat enregistré pour l'utilisateur de test.");
                refreshQuizData();
            } else {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de sauvegarder le résultat du quiz.");
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
            showAlert(Alert.AlertType.INFORMATION, "Leaderboard", "Aucun résultat disponible pour le moment.", null);
            return;
        }

        StringBuilder content = new StringBuilder();
        for (int index = 0; index < topResults.size(); index++) {
            QuizResult result = topResults.get(index);
            content.append(index + 1)
                    .append(". ")
                    .append(result.getQuiz() != null ? result.getQuiz().getTitre() : "Quiz inconnu")
                    .append(" - ")
                    .append(result.getScore())
                    .append("/")
                    .append(result.getTotalQuestions())
                    .append(" (")
                    .append(formatPercentage(result.getPercentage()))
                    .append(")");
            if (result.getUser() != null) {
                content.append(" - ").append(result.getUser().getFullName());
            }
            content.append("\n");
        }

        showAlert(Alert.AlertType.INFORMATION,
                "Leaderboard",
                "Top 5 des meilleurs scores :",
                content.toString());
    }

    private void showResultDetails(QuizResult result) {
        String userInfo = result.getUser() != null ? result.getUser().getFullName() + " (" + result.getUser().getEmail() + ")" : "Utilisateur inconnu";
        showAlert(Alert.AlertType.INFORMATION,
                "Détails du résultat",
                "Quiz : " + (result.getQuiz() != null ? result.getQuiz().getTitre() : "-"),
                "Score : " + result.getScore() + "/" + result.getTotalQuestions() + "\n" +
                        "Pourcentage : " + formatPercentage(result.getPercentage()) + "\n" +
                        "Statut : " + (Boolean.TRUE.equals(result.getPassed()) ? "Validé" : "Non validé") + "\n" +
                        "Joué par : " + userInfo
        );
    }
}