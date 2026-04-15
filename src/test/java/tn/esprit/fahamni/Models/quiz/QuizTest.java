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
}
