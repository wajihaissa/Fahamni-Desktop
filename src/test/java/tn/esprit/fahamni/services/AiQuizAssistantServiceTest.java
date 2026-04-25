package tn.esprit.fahamni.services;

import org.junit.jupiter.api.Test;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AiQuizAssistantServiceTest {

    private final AiQuizAssistantService service = new AiQuizAssistantService();

    @Test
    void generateQuizDraftReturnsUsableQuiz() {
        AiQuizAssistantService.GeneratedQuizDraft generatedDraft =
                service.generateQuizDraft("Java", "Java Foundations", 4, "Medium");

        Quiz quiz = generatedDraft.quiz();

        assertNotNull(quiz);
        assertEquals("Java Foundations", quiz.getTitre());
        assertEquals("Java", quiz.getKeyword());
        assertEquals(4, quiz.getQuestions().size());
        assertTrue(quiz.getQuestions().stream().allMatch(question -> question.getChoices().size() == 4));
        assertTrue(quiz.getQuestions().stream()
                .allMatch(question -> question.getChoices().stream().filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect())).count() == 1));
    }

    @Test
    void generateHintReturnsReadableText() {
        Question question = service.generateQuizDraft("Algorithms", "Algorithms Quiz", 3, "Easy")
                .quiz()
                .getQuestions()
                .get(0);

        String hint = service.generateHint(null, question, null);

        assertNotNull(hint);
        assertFalse(hint.isBlank());
        assertTrue(hint.length() > 10);
    }

    @Test
    void generateHintDoesNotRuleOutOptions() {
        Question question = service.generateQuizDraft("Life", "Life Smart Quiz", 3, "Easy")
                .quiz()
                .getQuestions()
                .get(0);

        String hint = service.generateHint(null, question, null).toLowerCase();

        assertFalse(hint.contains("rule out"));
        assertFalse(hint.contains("ruling out"));
        assertFalse(hint.contains("option"));
        assertFalse(hint.contains("choice"));
    }

    @Test
    void inferQuestionMetadataFallsBackToKeywordAndAllowedDifficulty() {
        AiQuizAssistantService.QuestionMetadata metadata = service.inferQuestionMetadata(
                "Networks",
                "Network Quiz",
                "Why is packet routing important in distributed systems?",
                java.util.List.of("Speed", "Reliability", "Color", "Temperature")
        );

        assertNotNull(metadata);
        assertNotNull(metadata.topic());
        assertNotNull(metadata.difficulty());
        assertTrue(java.util.Set.of("Easy", "Medium", "Hard").contains(metadata.difficulty()));
    }

    @Test
    void generateQuizDraftClampsRequestedQuestionCount() {
        Quiz quiz = service.generateQuizDraft("Java", "Java Foundations", 10, "Medium").quiz();

        assertNotNull(quiz);
        assertEquals(5, quiz.getQuestions().size());
    }
}
