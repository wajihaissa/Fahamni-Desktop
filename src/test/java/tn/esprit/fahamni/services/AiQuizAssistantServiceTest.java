package tn.esprit.fahamni.services;

import org.junit.jupiter.api.Test;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiQuizAssistantServiceTest {

    @Test
    void generateQuizDraftReturnsUsableQuizWhenOpenAiResponds() {
        StubAiQuizAssistantService service = new StubAiQuizAssistantService();
        service.openAiReady = true;
        service.quizDraft = Optional.of(buildQuiz("Java", "Java Foundations", 4));

        AiQuizAssistantService.GeneratedQuizDraft generatedDraft =
                service.generateQuizDraft("Java", "Java Foundations", 4, "Medium");

        Quiz quiz = generatedDraft.quiz();

        assertTrue(generatedDraft.success());
        assertEquals("OpenAI", generatedDraft.provider());
        assertNotNull(quiz);
        assertEquals("Java Foundations", quiz.getTitre());
        assertEquals("Java", quiz.getKeyword());
        assertEquals(4, quiz.getQuestions().size());
        assertTrue(quiz.getQuestions().stream().allMatch(question -> question.getChoices().size() == 4));
        assertTrue(quiz.getQuestions().stream()
                .allMatch(question -> question.getChoices().stream().filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect())).count() == 1));
    }

    @Test
    void generateQuizDraftReturnsFailureWhenAiIsUnavailable() {
        StubAiQuizAssistantService service = new StubAiQuizAssistantService();
        service.openAiReady = false;
        service.unavailableMessage = "OpenAI is not configured.";

        AiQuizAssistantService.GeneratedQuizDraft generatedDraft =
                service.generateQuizDraft("Java", "Java Foundations", 4, "Medium");

        assertFalse(generatedDraft.success());
        assertNull(generatedDraft.quiz());
        assertEquals("Unavailable", generatedDraft.provider());
        assertEquals("OpenAI is not configured.", generatedDraft.message());
    }

    @Test
    void generateHintReturnsReadableTextWhenOpenAiResponds() {
        StubAiQuizAssistantService service = new StubAiQuizAssistantService();
        service.openAiReady = true;
        service.hint = Optional.of("Think about the security goal that reduces exposure before damage spreads.");

        Question question = buildQuiz("Cybersecurity", "Cybersecurity Drill", 1).getQuestions().get(0);
        AiQuizAssistantService.GeneratedHint generatedHint = service.generateHint(null, question, null);

        assertTrue(generatedHint.success());
        assertEquals("OpenAI", generatedHint.provider());
        assertNotNull(generatedHint.text());
        assertFalse(generatedHint.text().isBlank());
        assertTrue(generatedHint.text().length() > 10);
    }

    @Test
    void generateHintDoesNotRuleOutOptionsWhenAiResponds() {
        StubAiQuizAssistantService service = new StubAiQuizAssistantService();
        service.openAiReady = true;
        service.hint = Optional.of("Focus on the core idea the question is testing rather than the most dramatic wording.");

        Question question = buildQuiz("Life", "Life Smart Quiz", 1).getQuestions().get(0);
        String hint = service.generateHint(null, question, null).text().toLowerCase();

        assertFalse(hint.contains("rule out"));
        assertFalse(hint.contains("ruling out"));
        assertFalse(hint.contains("option"));
        assertFalse(hint.contains("choice"));
    }

    @Test
    void generateHintReturnsFailureWhenAiIsUnavailable() {
        StubAiQuizAssistantService service = new StubAiQuizAssistantService();
        service.openAiReady = false;
        service.unavailableMessage = "OpenAI hint generation is disabled.";

        Question question = buildQuiz("Networks", "Networks Quiz", 1).getQuestions().get(0);
        AiQuizAssistantService.GeneratedHint generatedHint = service.generateHint(null, question, null);

        assertFalse(generatedHint.success());
        assertNull(generatedHint.text());
        assertEquals("Unavailable", generatedHint.provider());
        assertEquals("OpenAI hint generation is disabled.", generatedHint.message());
    }

    @Test
    void inferQuestionMetadataFallsBackToKeywordAndAllowedDifficulty() {
        AiQuizAssistantService service = new AiQuizAssistantService();

        AiQuizAssistantService.QuestionMetadata metadata = service.inferQuestionMetadata(
                "Networks",
                "Network Quiz",
                "Why is packet routing important in distributed systems?",
                List.of("Speed", "Reliability", "Color", "Temperature")
        );

        assertNotNull(metadata);
        assertNotNull(metadata.topic());
        assertNotNull(metadata.difficulty());
        assertTrue(java.util.Set.of("Easy", "Medium", "Hard").contains(metadata.difficulty()));
    }

    private static Quiz buildQuiz(String keyword, String title, int questionCount) {
        Quiz quiz = new Quiz();
        quiz.setKeyword(keyword);
        quiz.setTitre(title);

        for (int index = 0; index < questionCount; index++) {
            Question question = new Question();
            question.setQuestion("Question " + (index + 1) + " about " + keyword + "?");
            question.addChoice(choice("Correct " + (index + 1), true));
            question.addChoice(choice("Wrong A " + (index + 1), false));
            question.addChoice(choice("Wrong B " + (index + 1), false));
            question.addChoice(choice("Wrong C " + (index + 1), false));
            quiz.addQuestion(question);
        }

        return quiz;
    }

    private static Choice choice(String text, boolean isCorrect) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(isCorrect);
        return choice;
    }

    private static class StubAiQuizAssistantService extends AiQuizAssistantService {
        private boolean openAiReady;
        private String unavailableMessage = "OpenAI is unavailable.";
        private Optional<Quiz> quizDraft = Optional.empty();
        private Optional<String> hint = Optional.empty();

        @Override
        protected boolean isOpenAiReady() {
            return openAiReady;
        }

        @Override
        protected String buildAiUnavailableMessage() {
            return unavailableMessage;
        }

        @Override
        protected String resolveAiProviderLabel() {
            return "OpenAI";
        }

        @Override
        protected Optional<Quiz> tryGenerateQuizDraftWithOpenAi(String topic, String title, int questionCount, String difficulty) {
            return quizDraft;
        }

        @Override
        protected Optional<String> tryGenerateHintWithOpenAi(Quiz quiz, Question question, Long selectedChoiceId) {
            return hint;
        }
    }
}
