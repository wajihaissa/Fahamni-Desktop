package tn.esprit.fahamni.services;

import org.junit.jupiter.api.Test;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizAnswerAttempt;
import tn.esprit.fahamni.Models.quiz.QuizQuestionPerformance;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.Models.quiz.QuizUserInsight;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveQuizServiceTest {

    @Test
    void generateAdaptiveQuizForUserPrioritizesWeakTopicsAndCopiesQuestions() throws Exception {
        StubQuizService quizService = new StubQuizService();
        StubQuizAnalyticsService analyticsService = new StubQuizAnalyticsService();

        Question javaEasy = question(101L, "Java basics", "Java", "Easy");
        Question javaMedium = question(102L, "Java collections", "Java", "Medium");
        Question sqlHard = question(103L, "SQL joins", "SQL", "Hard");
        Question networkMedium = question(104L, "Routing", "Networks", "Medium");
        quizService.questionBank = List.of(javaEasy, javaMedium, sqlHard, networkMedium);

        QuizUserInsight insight = new QuizUserInsight();
        insight.setRecommendedDifficulty("Medium");
        insight.getWeakestTopics().add("Java");
        insight.getStrongestTopics().add("SQL");
        analyticsService.insight = insight;
        analyticsService.performance = List.of(
                performance(101L, "Java", "Easy", 5, 1, 4, 20.0, Instant.now().minusSeconds(10L * 24 * 60 * 60)),
                performance(102L, "Java", "Medium", 2, 1, 1, 50.0, Instant.now().minusSeconds(5L * 24 * 60 * 60)),
                performance(103L, "SQL", "Hard", 4, 4, 0, 100.0, Instant.now().minusSeconds(20L * 24 * 60 * 60))
        );

        AdaptiveQuizService service = createService(quizService, analyticsService);
        User user = new User(7, "Ada Student", "ada@example.com", "", UserRole.USER);

        Quiz adaptiveQuiz = service.generateAdaptiveQuizForUser(user, 3);

        assertNotNull(adaptiveQuiz);
        assertSame(adaptiveQuiz, quizService.createdQuiz);
        assertEquals(3, adaptiveQuiz.getQuestions().size());
        assertEquals("Adaptive Quiz - Java Foundation Set", adaptiveQuiz.getTitre());
        assertTrue(adaptiveQuiz.getKeyword().startsWith("adaptive-java-7-"));
        assertTrue(adaptiveQuiz.getQuestions().stream().anyMatch(question -> "Java".equals(question.getTopic())));
        assertTrue(adaptiveQuiz.getQuestions().stream().allMatch(question -> question.getQuiz() == adaptiveQuiz));
        assertTrue(adaptiveQuiz.getQuestions().stream().allMatch(question -> question.getSourceQuestionId() != null));
        assertTrue(adaptiveQuiz.getQuestions().stream().noneMatch(question -> quizService.questionBank.contains(question)));
    }

    @Test
    void generateAdaptiveQuizForUserReturnsNullWhenQuestionBankTooSmall() {
        StubQuizService quizService = new StubQuizService();
        quizService.questionBank = List.of(question(101L, "Solo", "Java", "Easy"));

        AdaptiveQuizService service = createServiceUnchecked(quizService, new StubQuizAnalyticsService());
        User user = new User(7, "Ada Student", "ada@example.com", "", UserRole.USER);

        assertNull(service.generateAdaptiveQuizForUser(user, 3));
    }

    @Test
    void generateMistakeRecoveryQuizFromResultUsesIncorrectAttemptQuestionsFirst() throws Exception {
        StubQuizService quizService = new StubQuizService();
        StubQuizAnalyticsService analyticsService = new StubQuizAnalyticsService();

        Question sourceJava = question(201L, "Java exceptions", "Java", "Medium");
        Question sourceSql = question(202L, "SQL joins", "SQL", "Hard");
        Question sourceNetworks = question(203L, "Routing", "Networks", "Easy");
        quizService.questionBank = List.of(sourceJava, sourceSql, sourceNetworks);

        Quiz completedQuiz = new Quiz();
        completedQuiz.setTitre("Adaptive Quiz");
        completedQuiz.setKeyword("adaptive-java");

        Question adaptiveJava = copiedQuestion(301L, 201L, sourceJava, completedQuiz);
        Question adaptiveSql = copiedQuestion(302L, 202L, sourceSql, completedQuiz);
        completedQuiz.addQuestion(adaptiveJava);
        completedQuiz.addQuestion(adaptiveSql);

        QuizResult sourceResult = new QuizResult();
        sourceResult.setQuiz(completedQuiz);
        sourceResult.addAnswerAttempt(attempt(301L, false));
        sourceResult.addAnswerAttempt(attempt(302L, true));

        QuizUserInsight insight = new QuizUserInsight();
        insight.getWeakestTopics().add("Java");
        analyticsService.insight = insight;

        AdaptiveQuizService service = createService(quizService, analyticsService);
        User user = new User(9, "Kai Student", "kai@example.com", "", UserRole.USER);

        Quiz retryQuiz = service.generateMistakeRecoveryQuizForUser(user, sourceResult, 2);

        assertNotNull(retryQuiz);
        assertEquals(1, retryQuiz.getQuestions().size());
        assertEquals(201L, retryQuiz.getQuestions().get(0).getSourceQuestionId());
        assertEquals("Mistake Recovery Quiz - Java Review Set", retryQuiz.getTitre());
        assertTrue(retryQuiz.getKeyword().startsWith("mistake-retry-java-9-"));
    }

    @Test
    void generateMistakeRecoveryQuizFallsBackToAnalyticsHistoryWhenResultHasNoIncorrectAttempts() throws Exception {
        StubQuizService quizService = new StubQuizService();
        StubQuizAnalyticsService analyticsService = new StubQuizAnalyticsService();

        Question sourceJava = question(201L, "Java exceptions", "Java", "Medium");
        Question sourceSql = question(202L, "SQL joins", "SQL", "Hard");
        Question sourceNetworks = question(203L, "Routing", "Networks", "Easy");
        quizService.questionBank = List.of(sourceJava, sourceSql, sourceNetworks);

        analyticsService.performance = List.of(
                performance(202L, "SQL", "Hard", 4, 1, 3, 25.0, Instant.now().minusSeconds(9L * 24 * 60 * 60)),
                performance(203L, "Networks", "Easy", 2, 1, 1, 50.0, Instant.now().minusSeconds(15L * 24 * 60 * 60))
        );

        QuizUserInsight insight = new QuizUserInsight();
        insight.getWeakestTopics().add("SQL");
        analyticsService.insight = insight;

        AdaptiveQuizService service = createService(quizService, analyticsService);
        User user = new User(9, "Kai Student", "kai@example.com", "", UserRole.USER);

        QuizResult sourceResult = new QuizResult();
        sourceResult.setQuiz(new Quiz());

        Quiz retryQuiz = service.generateMistakeRecoveryQuizForUser(user, sourceResult, 2);

        assertNotNull(retryQuiz);
        assertEquals(2, retryQuiz.getQuestions().size());
        assertEquals(202L, retryQuiz.getQuestions().get(0).getSourceQuestionId());
        assertEquals(203L, retryQuiz.getQuestions().get(1).getSourceQuestionId());
    }

    @Test
    void generateAdaptiveQuizReusesExistingUnattemptedQuizWithSameContent() throws Exception {
        StubQuizService quizService = new StubQuizService();
        StubQuizAnalyticsService analyticsService = new StubQuizAnalyticsService();

        Question javaEasy = question(101L, "Java basics", "Java", "Easy");
        Question javaMedium = question(102L, "Java collections", "Java", "Medium");
        Question networkMedium = question(104L, "Routing", "Networks", "Medium");
        quizService.questionBank = List.of(javaEasy, javaMedium, networkMedium);

        QuizUserInsight insight = new QuizUserInsight();
        insight.setRecommendedDifficulty("Medium");
        insight.getWeakestTopics().add("Java");
        analyticsService.insight = insight;
        analyticsService.performance = List.of(
                performance(101L, "Java", "Easy", 5, 1, 4, 20.0, Instant.now().minusSeconds(10L * 24 * 60 * 60)),
                performance(102L, "Java", "Medium", 2, 1, 1, 50.0, Instant.now().minusSeconds(5L * 24 * 60 * 60))
        );

        User user = new User(7, "Ada Student", "ada@example.com", "", UserRole.USER);
        Quiz existingQuiz = new Quiz();
        existingQuiz.setId(900L);
        existingQuiz.setTitre("Adaptive Quiz - Java Focus Set");
        existingQuiz.setKeyword("adaptive-java-7-1714123456");
        existingQuiz.setCreatedAt(Instant.now().minusSeconds(120));
        existingQuiz.addQuestion(copiedQuestion(901L, 101L, javaEasy, existingQuiz));
        existingQuiz.addQuestion(copiedQuestion(902L, 102L, javaMedium, existingQuiz));
        existingQuiz.addQuestion(copiedQuestion(903L, 104L, networkMedium, existingQuiz));
        quizService.allQuizzes = List.of(existingQuiz);

        AdaptiveQuizService service = createService(quizService, analyticsService);

        Quiz adaptiveQuiz = service.generateAdaptiveQuizForUser(user, 3);

        assertSame(existingQuiz, adaptiveQuiz);
        assertNull(quizService.createdQuiz);
    }

    private AdaptiveQuizService createService(StubQuizService quizService, StubQuizAnalyticsService analyticsService) throws Exception {
        AdaptiveQuizService service = new AdaptiveQuizService();
        setField(service, "quizService", quizService);
        setField(service, "analyticsService", analyticsService);
        return service;
    }

    private AdaptiveQuizService createServiceUnchecked(StubQuizService quizService, StubQuizAnalyticsService analyticsService) {
        try {
            return createService(quizService, analyticsService);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Question question(Long id, String text, String topic, String difficulty) {
        Question question = new Question();
        question.setId(id);
        question.setQuestion(text);
        question.setTopic(topic);
        question.setDifficulty(difficulty);
        question.addChoice(choice("Correct", true));
        question.addChoice(choice("Wrong", false));
        return question;
    }

    private static Question copiedQuestion(Long id, Long sourceQuestionId, Question source, Quiz quiz) {
        Question question = question(id, source.getQuestion(), source.getTopic(), source.getDifficulty());
        question.setSourceQuestionId(sourceQuestionId);
        question.setQuiz(quiz);
        return question;
    }

    private static Choice choice(String text, boolean isCorrect) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(isCorrect);
        return choice;
    }

    private static QuizQuestionPerformance performance(
            Long questionId,
            String topic,
            String difficulty,
            int attempts,
            int correctAnswers,
            int incorrectAnswers,
            double accuracyRate,
            Instant lastAnsweredAt
    ) {
        QuizQuestionPerformance performance = new QuizQuestionPerformance();
        performance.setQuestionId(questionId);
        performance.setTopic(topic);
        performance.setDifficulty(difficulty);
        performance.setAttempts(attempts);
        performance.setCorrectAnswers(correctAnswers);
        performance.setIncorrectAnswers(incorrectAnswers);
        performance.setAccuracyRate(accuracyRate);
        performance.setLastAnsweredAt(lastAnsweredAt);
        return performance;
    }

    private static QuizAnswerAttempt attempt(Long questionId, boolean correct) {
        QuizAnswerAttempt attempt = new QuizAnswerAttempt();
        attempt.setQuestionId(questionId);
        attempt.setCorrect(correct);
        return attempt;
    }

    private static class StubQuizService extends QuizService {
        private List<Question> questionBank = List.of();
        private List<Quiz> allQuizzes = List.of();
        private Quiz createdQuiz;

        @Override
        public List<Question> getAllQuestionsFromBank() {
            return questionBank;
        }

        @Override
        public List<Quiz> getAllQuizzes() {
            return allQuizzes;
        }

        @Override
        public Question copyQuestionForQuiz(Question source, Quiz quizShell) {
            Question copy = new Question();
            copy.setId(source.getId() == null ? null : source.getId() + 10_000);
            copy.setSourceQuestionId(source.getSourceQuestionId() != null ? source.getSourceQuestionId() : source.getId());
            copy.setQuestion(source.getQuestion());
            copy.setTopic(source.getTopic());
            copy.setDifficulty(source.getDifficulty());
            copy.setHint(source.getHint());
            copy.setExplanation(source.getExplanation());
            copy.setQuiz(quizShell);
            for (Choice sourceChoice : source.getChoices()) {
                copy.addChoice(choice(sourceChoice.getChoice(), Boolean.TRUE.equals(sourceChoice.getIsCorrect())));
            }
            return copy;
        }

        @Override
        public Quiz createQuiz(Quiz quiz) {
            this.createdQuiz = quiz;
            return quiz;
        }
    }

    private static class StubQuizAnalyticsService extends QuizAnalyticsService {
        private QuizUserInsight insight;
        private List<QuizQuestionPerformance> performance = new ArrayList<>();

        @Override
        public QuizUserInsight getUserInsight(Integer userId) {
            return insight;
        }

        @Override
        public List<QuizQuestionPerformance> getQuestionPerformanceForUser(Integer userId) {
            return performance;
        }
    }
}
