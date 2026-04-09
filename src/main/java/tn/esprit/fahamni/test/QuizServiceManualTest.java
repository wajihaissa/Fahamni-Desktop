package tn.esprit.fahamni.test;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.services.QuizService;

import java.util.HashMap;
import java.util.Map;

public class QuizServiceManualTest {

    public static void main(String[] args) {
        QuizService service = new QuizService();

        System.out.println("=== QUIZ SERVICE MANUAL TEST ===");

        Quiz createdQuiz = runCreateTest(service);
        if (createdQuiz == null) {
            System.out.println("Create test failed. Stopping the scenario.");
            return;
        }

        Quiz fetchedQuiz = runReadTests(service, createdQuiz.getId());
        if (fetchedQuiz == null) {
            System.out.println("Read test failed. Stopping the scenario.");
            return;
        }

        Quiz updatedQuiz = runUpdateTest(service, fetchedQuiz);
        if (updatedQuiz == null) {
            System.out.println("Update test failed. Stopping the scenario.");
            return;
        }

        runSubmitTest(service, updatedQuiz);
        runDeleteTest(service, updatedQuiz.getId());
    }

    private static Quiz runCreateTest(QuizService service) {
        System.out.println("\n[CREATE]");

        Quiz quiz = new Quiz();
        quiz.setTitre("running the streets ");
        quiz.setKeyword("TS");

        Question q1 = new Question();
        q1.setQuestion("Java is mainly a?");
        q1.addChoice(makeChoice("Programming language", true));
        q1.addChoice(makeChoice("Database engine", false));

        Question q2 = new Question();
        q2.setQuestion("JVM stands for?");
        q2.addChoice(makeChoice("Java Virtual Machine", true));
        q2.addChoice(makeChoice("Java Visual Model", false));

        quiz.addQuestion(q1);
        quiz.addQuestion(q2);

        Quiz created = service.createQuiz(quiz);
        if (created == null) {
            System.out.println("Quiz creation returned null.");
            return null;
        }

        System.out.println("Created quiz id: " + created.getId());
        System.out.println("Created questions: " + created.getQuestions().size());
        return created;
    }

    private static Quiz runReadTests(QuizService service, Long quizId) {
        System.out.println("\n[READ]");

        Quiz fetched = service.getQuizById(quizId);
        if (fetched == null) {
            System.out.println("getQuizById returned null for id " + quizId);
            return null;
        }

        System.out.println("Fetched title: " + fetched.getTitre());
        System.out.println("Fetched keyword: " + fetched.getKeyword());
        System.out.println("Fetched questions count: " + fetched.getQuestions().size());

        System.out.println("Listing fetched questions and choices:");
        for (Question question : fetched.getQuestions()) {
            System.out.println("- Q#" + question.getId() + ": " + question.getQuestion());
            for (Choice choice : question.getChoices()) {
                System.out.println("  * Choice#" + choice.getId() + ": " + choice.getChoice() + " | correct=" + choice.getIsCorrect());
            }
        }

        System.out.println("Total quizzes in DB: " + service.getAllQuizzes().size());
        return fetched;
    }

    private static Quiz runUpdateTest(QuizService service, Quiz quizToUpdate) {
        System.out.println("\n[UPDATE]");

        quizToUpdate.setTitre("all bout ma bread");
        quizToUpdate.setKeyword("YN");

        Question replacementQuestion = new Question();
        replacementQuestion.setQuestion("Which company originally developed Java?");
        replacementQuestion.addChoice(makeChoice("Sun Microsystems", true));
        replacementQuestion.addChoice(makeChoice("Microsoft", false));

        Question secondReplacementQuestion = new Question();
        secondReplacementQuestion.setQuestion("Which keyword creates an object?");
        secondReplacementQuestion.addChoice(makeChoice("new", true));
        secondReplacementQuestion.addChoice(makeChoice("class", false));

        quizToUpdate.getQuestions().clear();
        quizToUpdate.addQuestion(replacementQuestion);
        quizToUpdate.addQuestion(secondReplacementQuestion);

        Quiz updated = service.updateQuiz(quizToUpdate.getId(), quizToUpdate);
        if (updated == null) {
            System.out.println("updateQuiz returned null.");
            return null;
        }

        System.out.println("Updated title: " + updated.getTitre());
        System.out.println("Updated keyword: " + updated.getKeyword());
        System.out.println("Updated questions count: " + updated.getQuestions().size());
        return updated;
    }

    private static void runSubmitTest(QuizService service, Quiz quiz) {
        System.out.println("\n[SUBMIT RESULT]");

        Map<Long, Long> selectedChoicesByQuestion = new HashMap<>();
        for (Question question : quiz.getQuestions()) {
            Long correctChoiceId = question.getChoices().stream()
                    .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                    .findFirst()
                    .map(Choice::getId)
                    .orElse(null);

            selectedChoicesByQuestion.put(question.getId(), correctChoiceId);
        }

        User user = new User(1L, "Quiz Tester", "quiz.tester@fahamni.tn", "", UserRole.USER);
        QuizResult result = service.submitQuiz(quiz.getId(), selectedChoicesByQuestion, user);

        if (result == null) {
            System.out.println("submitQuiz returned null. Check the quiz_result table structure.");
            return;
        }

        System.out.println("Saved result id: " + result.getId());
        System.out.println("Score: " + result.getScore() + "/" + result.getTotalQuestions());
        System.out.println("Percentage: " + result.getPercentage());
        System.out.println("Passed: " + result.getPassed());
    }

    private static void runDeleteTest(QuizService service, Long quizId) {
        System.out.println("\n[DELETE]");

        boolean deleted = service.deleteQuiz(quizId);
        System.out.println("Deleted: " + deleted);

        Quiz afterDelete = service.getQuizById(quizId);
        System.out.println("Fetch after delete: " + (afterDelete == null ? "null (ok)" : "still exists"));
    }

    private static Choice makeChoice(String text, boolean isCorrect) {
        Choice choice = new Choice();
        choice.setChoice(text);
        choice.setIsCorrect(isCorrect);
        return choice;
    }
}
