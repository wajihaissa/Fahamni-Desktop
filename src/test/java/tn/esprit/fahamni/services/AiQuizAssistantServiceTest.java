package tn.esprit.fahamni.services;

import org.junit.jupiter.api.Test;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
