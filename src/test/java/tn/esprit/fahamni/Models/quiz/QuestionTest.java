package tn.esprit.fahamni.Models.quiz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTest {

    @Test
    void questionHelpersReflectCurrentChoiceState() {
        Question question = new Question();
        question.setQuestion("What does JVM stand for?");

        Choice first = new Choice("Java Virtual Machine", true);
        Choice second = new Choice("Java Visual Model", false);

        question.addChoice(first);
        question.addChoice(second);

        assertTrue(question.hasQuestionText());
        assertTrue(question.hasChoices());
        assertEquals(1, question.getCorrectChoiceCount());
        assertSame(question, first.getQuestion());
        assertSame(question, second.getQuestion());
        assertTrue(first.hasChoiceText());
        assertTrue(first.isMarkedCorrect());
        assertFalse(second.isMarkedCorrect());
    }
}
