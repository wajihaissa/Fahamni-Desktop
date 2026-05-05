package tn.esprit.fahamni.services;

import org.junit.jupiter.api.Test;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizAnswerAttempt;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizServiceIntegrationTest {

    private final QuizService service = new QuizService();

    @Test
    void createUpdateSubmitAndDeleteQuizRoundTripPersistsExpectedQuizState() {
        List<Long> quizIdsToCleanup = new ArrayList<>();
        Long quizResultId = null;

        try {
            Quiz createdQuiz = service.createQuiz(buildQuiz(
                    uniqueValue("Integration Quiz"),
                    uniqueValue("integration-keyword"),
                    "Java Basics",
                    "What does JVM stand for?",
                    "Java Virtual Machine",
                    "Java Visual Model",
                    "Collections",
                    "Which interface preserves insertion order?",
                    "List",
                    "Thread"
            ));

            assertNotNull(createdQuiz);
            quizIdsToCleanup.add(createdQuiz.getId());
            assertNotNull(createdQuiz.getId());
            assertEquals(2, createdQuiz.getQuestions().size());
            assertTrue(createdQuiz.getQuestions().stream().allMatch(question -> question.getId() != null));
            assertTrue(createdQuiz.getQuestions().stream().flatMap(question -> question.getChoices().stream()).allMatch(choice -> choice.getId() != null));

            Quiz fetchedQuiz = service.getQuizById(createdQuiz.getId());

            assertNotNull(fetchedQuiz);
            assertEquals(createdQuiz.getTitre(), fetchedQuiz.getTitre());
            assertEquals(createdQuiz.getKeyword(), fetchedQuiz.getKeyword());
            assertEquals(2, fetchedQuiz.getQuestions().size());
            assertEquals("Java Basics", fetchedQuiz.getQuestions().get(0).getTopic());
            assertEquals("Collections", fetchedQuiz.getQuestions().get(1).getTopic());

            Quiz updatedInput = buildQuiz(
                    uniqueValue("Updated Integration Quiz"),
                    uniqueValue("updated-integration-keyword"),
                    "Streams",
                    "Which stream operation transforms elements?",
                    "map",
                    "forEach",
                    "Concurrency",
                    "Which keyword enforces shared visibility?",
                    "volatile",
                    "abstract"
            );

            Quiz updatedQuiz = service.updateQuiz(createdQuiz.getId(), updatedInput);

            assertNotNull(updatedQuiz);
            assertEquals(updatedInput.getTitre(), updatedQuiz.getTitre());
            assertEquals(updatedInput.getKeyword(), updatedQuiz.getKeyword());
            assertEquals(2, updatedQuiz.getQuestions().size());
            assertEquals("Streams", updatedQuiz.getQuestions().get(0).getTopic());
            assertEquals("Concurrency", updatedQuiz.getQuestions().get(1).getTopic());

            Map<Long, Long> answers = new HashMap<>();
            answers.put(updatedQuiz.getQuestions().get(0).getId(), findCorrectChoiceId(updatedQuiz.getQuestions().get(0)));
            answers.put(updatedQuiz.getQuestions().get(1).getId(), updatedQuiz.getQuestions().get(1).getChoices().get(1).getId());

            User user = loadExistingUser();
            assertNotNull(user);
            QuizResult result = service.submitQuiz(updatedQuiz.getId(), answers, user);

            assertNotNull(result);
            quizResultId = result.getId();
            assertNotNull(result.getId());
            assertEquals(1, result.getScore());
            assertEquals(2, result.getTotalQuestions());
            assertEquals(50.0, result.getPercentage());
            assertFalse(result.isPassed());
            assertEquals(user.getEmail(), result.getUser().getEmail());

            List<QuizAnswerAttempt> answerAttempts = service.getAnswerAttemptsForResult(quizResultId);

            assertEquals(2, answerAttempts.size());
            assertTrue(answerAttempts.stream().anyMatch(attempt -> Boolean.TRUE.equals(attempt.getCorrect())));
            assertTrue(answerAttempts.stream().anyMatch(attempt -> Boolean.FALSE.equals(attempt.getCorrect())));
            assertTrue(answerAttempts.stream().allMatch(attempt -> attempt.getAnsweredAt() != null));

            List<Quiz> recentResults = service.getRecentResults();

            assertTrue(recentResults.stream().anyMatch(quiz -> createdQuiz.getId().equals(quiz.getId())));
            assertEquals(1, service.getLastScore(service.getQuizById(updatedQuiz.getId())));
        } finally {
            for (Long quizId : quizIdsToCleanup) {
                if (quizId != null) {
                    service.deleteQuiz(quizId);
                }
            }

            if (quizResultId != null) {
                assertTrue(service.getAnswerAttemptsForResult(quizResultId).isEmpty());
            }
        }
    }

    @Test
    void getSourceQuizTitlesForQuizReturnsSourceQuizTitleForCopiedQuestions() {
        List<Long> quizIdsToCleanup = new ArrayList<>();

        try {
            Quiz sourceQuiz = service.createQuiz(buildQuiz(
                    uniqueValue("Source Quiz"),
                    uniqueValue("source-keyword"),
                    "Java",
                    "What is polymorphism?",
                    "Using one interface for many implementations",
                    "Creating duplicate classes",
                    "SQL",
                    "What does JOIN do?",
                    "Combines rows from related tables",
                    "Deletes duplicate records"
            ));
            assertNotNull(sourceQuiz);
            quizIdsToCleanup.add(sourceQuiz.getId());

            Quiz adaptiveQuiz = new Quiz();
            adaptiveQuiz.setTitre(uniqueValue("Adaptive Copy Quiz"));
            adaptiveQuiz.setKeyword(uniqueValue("adaptive-copy"));
            adaptiveQuiz.addQuestion(service.copyQuestionForQuiz(sourceQuiz.getQuestions().get(0), adaptiveQuiz));

            Quiz createdAdaptiveQuiz = service.createQuiz(adaptiveQuiz);
            assertNotNull(createdAdaptiveQuiz);
            quizIdsToCleanup.add(createdAdaptiveQuiz.getId());

            List<String> sourceTitles = service.getSourceQuizTitlesForQuiz(createdAdaptiveQuiz.getId());

            assertEquals(1, sourceTitles.size());
            assertEquals(sourceQuiz.getTitre(), sourceTitles.get(0));
        } finally {
            for (Long quizId : quizIdsToCleanup) {
                if (quizId != null) {
                    service.deleteQuiz(quizId);
                }
            }
        }
    }

    private Quiz buildQuiz(
            String title,
            String keyword,
            String firstTopic,
            String firstPrompt,
            String firstCorrectChoice,
            String firstWrongChoice,
            String secondTopic,
            String secondPrompt,
            String secondCorrectChoice,
            String secondWrongChoice
    ) {
        Quiz quiz = new Quiz();
        quiz.setTitre(title);
        quiz.setKeyword(keyword);
        quiz.addQuestion(buildQuestion(firstTopic, firstPrompt, firstCorrectChoice, firstWrongChoice));
        quiz.addQuestion(buildQuestion(secondTopic, secondPrompt, secondCorrectChoice, secondWrongChoice));
        return quiz;
    }

    private Question buildQuestion(String topic, String prompt, String correctChoice, String wrongChoice) {
        Question question = new Question();
        question.setQuestion(prompt);
        question.setTopic(topic);
        question.setDifficulty("Medium");
        question.setHint("Read the concept carefully.");
        question.setExplanation("This question checks the core idea.");
        question.addChoice(makeChoice(correctChoice, true));
        question.addChoice(makeChoice(wrongChoice, false));
        return question;
    }

    private Choice makeChoice(String text, boolean isCorrect) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(isCorrect);
        return choice;
    }

    private Long findCorrectChoiceId(Question question) {
        return question.getChoices().stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .map(Choice::getId)
                .findFirst()
                .orElseThrow();
    }

    private String uniqueValue(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    private User loadExistingUser() {
        Connection connection = MyDataBase.getInstance().getCnx();
        if (connection == null) {
            return null;
        }

        String query = "SELECT id, full_name, email FROM `user` ORDER BY id ASC LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        "",
                        UserRole.USER
                );
            }
        } catch (SQLException exception) {
            return null;
        }

        return null;
    }
}
