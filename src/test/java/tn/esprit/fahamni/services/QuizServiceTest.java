package tn.esprit.fahamni.services;

import org.junit.jupiter.api.Test;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizServiceTest {

    private final QuizService service = new QuizService();

    @Test
    void isQuizStructureValidAcceptsWellFormedQuiz() {
        Quiz quiz = buildQuiz("Java Basics", "java");

        assertTrue(service.isQuizStructureValid(quiz));
    }

    @Test
    void isQuizStructureValidRejectsDuplicateChoicesIgnoringCaseAndWhitespace() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Java Basics");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setQuestion("Which keyword creates an object?");
        question.addChoice(makeChoice("new", true));
        question.addChoice(makeChoice(" New ", false));

        quiz.addQuestion(question);

        assertFalse(service.isQuizStructureValid(quiz));
    }

    @Test
    void isQuizStructureValidRejectsMultipleCorrectChoices() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Java Basics");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setQuestion("Which options are Java keywords?");
        question.addChoice(makeChoice("class", true));
        question.addChoice(makeChoice("public", true));

        quiz.addQuestion(question);

        assertFalse(service.isQuizStructureValid(quiz));
    }

    @Test
    void evaluateQuizComputesScorePercentageAndPassStatus() {
        Quiz quiz = buildQuiz("Java Basics", "java");
        Question firstQuestion = quiz.getQuestions().get(0);
        Question secondQuestion = quiz.getQuestions().get(1);

        Map<Long, Long> answers = new HashMap<>();
        answers.put(firstQuestion.getId(), firstQuestion.getChoices().get(0).getId());
        answers.put(secondQuestion.getId(), secondQuestion.getChoices().get(1).getId());

        QuizResult result = service.evaluateQuiz(quiz, answers);

        assertNotNull(result);
        assertSame(quiz, result.getQuiz());
        assertEquals(1, result.getScore());
        assertEquals(2, result.getTotalQuestions());
        assertEquals(50.0, result.getPercentage());
        assertFalse(result.isPassed());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void evaluateQuizTreatsSixtyPercentAsPassing() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Java Basics");
        quiz.setKeyword("java");
        quiz.addQuestion(buildQuestion(1L, "Question 1", 11L, 12L, 11L));
        quiz.addQuestion(buildQuestion(2L, "Question 2", 21L, 22L, 21L));
        quiz.addQuestion(buildQuestion(3L, "Question 3", 31L, 32L, 31L));
        quiz.addQuestion(buildQuestion(4L, "Question 4", 41L, 42L, 42L));
        quiz.addQuestion(buildQuestion(5L, "Question 5", 51L, 52L, 52L));

        Map<Long, Long> answers = Map.of(
                1L, 11L,
                2L, 21L,
                3L, 31L
        );

        QuizResult result = service.evaluateQuiz(quiz, answers);

        assertNotNull(result);
        assertEquals(3, result.getScore());
        assertEquals(60.0, result.getPercentage());
        assertTrue(result.isPassed());
    }

    @Test
    void evaluateQuizAcceptsCorrectCodeAnswerAfterWhitespaceNormalization() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Syntax Drill");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setId(10L);
        question.setQuestion("Write the Java print statement.");
        question.setQuestionType(Question.TYPE_CODE);
        question.setExpectedAnswer("System.out.println(\"Hello\");");
        quiz.addQuestion(question);

        QuizResult result = service.evaluateQuiz(
                quiz,
                Map.of(),
                Map.of(10L, "  System.out.println(\"Hello\");   ")
        );

        assertNotNull(result);
        assertEquals(1, result.getScore());
        assertEquals(100.0, result.getPercentage());
        assertTrue(result.isPassed());
    }

    @Test
    void evaluateQuizUsesAiForLogicBasedCodeQuestions() {
        StubAiQuizAssistantService aiService = new StubAiQuizAssistantService();
        aiService.codeEvaluation = Optional.of(true);
        QuizService aiQuizService = new QuizService(aiService);

        Quiz quiz = new Quiz();
        quiz.setTitre("Logic Drill");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setId(20L);
        question.setQuestion("Write logic that returns true for even numbers.");
        question.setQuestionType(Question.TYPE_CODE);
        question.setCodeLanguage("Java");
        question.setExpectedAnswer("return number % 2 == 0;");
        question.setCodeEvaluationMode(Question.CODE_EVALUATION_AI);
        quiz.addQuestion(question);

        QuizResult result = aiQuizService.evaluateQuiz(
                quiz,
                Map.of(),
                Map.of(20L, "return (number & 1) == 0;")
        );

        assertNotNull(result);
        assertEquals(1, result.getScore());
        assertTrue(result.isPassed());
    }

    @Test
    void evaluateQuizAcceptsCorrectChoiceForCodeOutputQuestion() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Output Drill");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setId(30L);
        question.setQuestion("What is the output of this code?");
        question.setQuestionType(Question.TYPE_CODE_OUTPUT);
        question.setCodeLanguage("Java");
        question.setStarterCode("System.out.println(2 + 3);");

        Choice first = makeChoice("5", true);
        first.setId(301L);
        Choice second = makeChoice("\"23\"", false);
        second.setId(302L);

        question.addChoice(first);
        question.addChoice(second);
        quiz.addQuestion(question);

        QuizResult result = service.evaluateQuiz(quiz, Map.of(30L, 301L), Map.of());

        assertNotNull(result);
        assertEquals(1, result.getScore());
        assertEquals(100.0, result.getPercentage());
        assertTrue(result.isPassed());
    }

    @Test
    void isQuizStructureValidRejectsOutputPromptWithoutCodeSnippet() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Broken Output Quiz");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setQuestion("What is the output of the following code?");
        question.addChoice(makeChoice("1", true));
        question.addChoice(makeChoice("0", false));
        question.addChoice(makeChoice("Division by zero!", false));
        question.addChoice(makeChoice("Error: Division by zero", false));
        quiz.addQuestion(question);

        assertFalse(service.isQuizStructureValid(quiz));
    }

    @Test
    void isQuizStructureValidRejectsNaturalCodeOutputPromptWithoutSnippet() {
        StubAiQuizAssistantService aiService = new StubAiQuizAssistantService();
        aiService.codeSnippetRequirement = Optional.of(true);
        QuizService aiQuizService = new QuizService(aiService);

        Quiz quiz = new Quiz();
        quiz.setTitre("Broken Output Quiz 2");
        quiz.setKeyword("java");

        Question question = new Question();
        question.setQuestion("Pick the right answer.");
        question.addChoice(makeChoice("15", true));
        question.addChoice(makeChoice("10", false));
        question.addChoice(makeChoice("5", false));
        question.addChoice(makeChoice("0", false));
        quiz.addQuestion(question);

        assertFalse(aiQuizService.isQuizStructureValid(quiz));
    }

    @Test
    void getLastScoreReturnsNullForQuizWithoutResults() {
        assertNull(service.getLastScore(buildQuiz("Java Basics", "java")));
    }

    @Test
    void getLastScoreUsesMostRecentCompletedResult() {
        Quiz quiz = buildQuiz("Java Basics", "java");

        QuizResult oldest = new QuizResult();
        oldest.setScore(1);
        oldest.setCompletedAt(java.time.Instant.parse("2026-04-20T10:15:30Z"));

        QuizResult latest = new QuizResult();
        latest.setScore(2);
        latest.setCompletedAt(java.time.Instant.parse("2026-04-22T10:15:30Z"));

        QuizResult ignoredWithoutTimestamp = new QuizResult();
        ignoredWithoutTimestamp.setScore(99);

        quiz.addQuizResult(oldest);
        quiz.addQuizResult(latest);
        quiz.addQuizResult(ignoredWithoutTimestamp);

        assertEquals(2, service.getLastScore(quiz));
    }

    @Test
    void copyQuestionForQuizCreatesDetachedCloneWithMetadataAndChoices() {
        Quiz sourceQuiz = buildQuiz("Java Basics", "java");
        Question source = sourceQuiz.getQuestions().get(0);
        source.setTopic("Collections");
        source.setDifficulty("Hard");
        source.setHint("Think about ordering.");
        source.setExplanation("Lists preserve order.");
        source.setQuestionType(Question.TYPE_CODE);
        source.setCodeLanguage("Java");
        source.setStarterCode("System.out.println();");
        source.setExpectedAnswer("System.out.println(\"Hi\");");
        source.setCodeEvaluationMode(Question.CODE_EVALUATION_AI);

        Quiz targetQuiz = new Quiz();
        targetQuiz.setTitre("Adaptive Quiz");
        targetQuiz.setKeyword("adaptive-java");

        Question copy = service.copyQuestionForQuiz(source, targetQuiz);

        assertNotNull(copy);
        assertNotSame(source, copy);
        assertEquals(source.getId(), copy.getSourceQuestionId());
        assertEquals(source.getQuestion(), copy.getQuestion());
        assertEquals("Collections", copy.getTopic());
        assertEquals("Hard", copy.getDifficulty());
        assertEquals("Think about ordering.", copy.getHint());
        assertEquals("Lists preserve order.", copy.getExplanation());
        assertEquals(Question.TYPE_CODE, copy.getQuestionType());
        assertEquals("Java", copy.getCodeLanguage());
        assertEquals("System.out.println();", copy.getStarterCode());
        assertEquals("System.out.println(\"Hi\");", copy.getExpectedAnswer());
        assertEquals(Question.CODE_EVALUATION_AI, copy.getCodeEvaluationMode());
        assertSame(targetQuiz, copy.getQuiz());
        assertEquals(source.getChoices().size(), copy.getChoices().size());
        assertNotSame(source.getChoices().get(0), copy.getChoices().get(0));
        assertSame(copy, copy.getChoices().get(0).getQuestion());
    }

    @Test
    void submitQuizReturnsNullForInvalidQuizIdWithoutTouchingUserData() {
        User user = new User(77, "Quiz Tester", "quiz@test.local", "", UserRole.USER);

        QuizResult result = service.submitQuiz(-999_999L, Map.of(), user);

        assertNull(result);
    }

    @Test
    void submitQuizReturnsNullWhenUserIsMissing() {
        QuizResult result = service.submitQuiz(-999_999L, Map.of(), null);

        assertNull(result);
    }

    private Quiz buildQuiz(String title, String keyword) {
        Quiz quiz = new Quiz();
        quiz.setTitre(title);
        quiz.setKeyword(keyword);
        quiz.addQuestion(buildQuestion(1L, "What is the JVM?", 11L, 12L, 11L));
        quiz.addQuestion(buildQuestion(2L, "Which keyword creates an object?", 21L, 22L, 21L));
        return quiz;
    }

    private Question buildQuestion(Long questionId, String prompt, Long firstChoiceId, Long secondChoiceId, Long correctChoiceId) {
        Question question = new Question();
        question.setId(questionId);
        question.setQuestion(prompt);

        Choice first = makeChoice(firstChoiceId.equals(correctChoiceId) ? "Correct answer" : "Wrong answer", firstChoiceId.equals(correctChoiceId));
        first.setId(firstChoiceId);
        Choice second = makeChoice(secondChoiceId.equals(correctChoiceId) ? "Correct answer" : "Wrong answer", secondChoiceId.equals(correctChoiceId));
        second.setId(secondChoiceId);

        question.addChoice(first);
        question.addChoice(second);
        return question;
    }

    private Choice makeChoice(String text, boolean isCorrect) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(isCorrect);
        return choice;
    }

    private static class StubAiQuizAssistantService extends AiQuizAssistantService {
        private Optional<Boolean> codeEvaluation = Optional.empty();
        private Optional<Boolean> codeSnippetRequirement = Optional.empty();

        @Override
        protected Optional<Boolean> tryEvaluateCodeAnswerWithOpenAi(Question question, String submittedAnswer) {
            return codeEvaluation;
        }

        @Override
        protected Optional<Boolean> tryDetectQuestionRequiresCodeSnippetWithOpenAi(String questionText, java.util.List<String> choices) {
            return codeSnippetRequirement;
        }

        @Override
        protected boolean isOpenAiReady() {
            return true;
        }
    }
}
