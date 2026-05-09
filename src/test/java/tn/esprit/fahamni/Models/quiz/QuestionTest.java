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

    @Test
    void codeQuestionHelpersDefaultAndTypeFlagsStayConsistent() {
        Question question = new Question();

        assertTrue(question.isMultipleChoiceQuestion());
        assertEquals(Question.TYPE_MULTIPLE_CHOICE, question.getNormalizedQuestionType());

        question.setQuestionType(Question.TYPE_CODE);
        question.setExpectedAnswer("System.out.println(\"Hi\");");
        question.setCodeEvaluationMode(Question.CODE_EVALUATION_AI);

        assertTrue(question.isCodeQuestion());
        assertEquals(Question.TYPE_CODE, question.getNormalizedQuestionType());
        assertEquals(Question.CODE_EVALUATION_AI, question.getNormalizedCodeEvaluationMode());
        assertTrue(question.usesAiCodeEvaluation());
    }

    @Test
    void codeOutputQuestionHelpersStayConsistent() {
        Question question = new Question();
        question.setQuestionType("code output");
        question.setStarterCode("print(1 + 1)");

        assertTrue(question.isCodeOutputQuestion());
        assertTrue(question.isMultipleChoiceQuestion());
        assertEquals(Question.TYPE_CODE_OUTPUT, question.getNormalizedQuestionType());
        assertTrue(question.usesCodeSnippetPrompt());
    }

    @Test
    void outputPromptDetectionCatchesMoreNaturalWording() {
        Question question = new Question();
        question.setQuestion("What does the following code output when executed?");

        assertTrue(question.looksLikeCodeOutputPrompt());
        assertTrue(question.requiresCodeSnippet());
    }
}
