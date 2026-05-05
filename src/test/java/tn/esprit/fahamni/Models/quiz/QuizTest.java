package tn.esprit.fahamni.Models.quiz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuizTest {

    @Test
    void addAndRemoveQuestionAndQuizResult_shouldKeepBidirectionalReferencesInSync() {
        Quiz quiz = new Quiz();
        Question question = new Question();
        QuizResult result = new QuizResult();
        quiz.addQuestion(question);
        quiz.addQuestion(question);
        quiz.addQuizResult(result);
        quiz.addQuizResult(result);
        assertEquals(1, quiz.getQuestions().size());
        assertSame(quiz, question.getQuiz());
        assertEquals(1, quiz.getQuizResults().size());
        assertSame(quiz, result.getQuiz());

        quiz.removeQuestion(question);
        quiz.removeQuizResult(result);

        assertTrue(quiz.getQuestions().isEmpty());
        assertNull(question.getQuiz());
        assertTrue(quiz.getQuizResults().isEmpty());
        assertNull(result.getQuiz());
    }

    @Test
    void aggregateHelpersExposeTheCurrentQuizState() {
        Quiz quiz = new Quiz();
        quiz.setTitre("Java Basics");
        quiz.setKeyword("Java");

        Question question = new Question();
        QuizResult result = new QuizResult();
        result.setScore(4);
        result.setPercentage(80.0);
        result.setPassed(true);

        quiz.addQuestion(question);
        quiz.addQuizResult(result);

        assertTrue(quiz.hasTitle());
        assertTrue(quiz.hasKeyword());
        assertEquals(1, quiz.getQuestionCount());
        assertEquals(1, quiz.getResultCount());
        assertTrue(result.hasScore());
        assertTrue(result.hasPercentage());
        assertTrue(result.isPassed());
    }
}
