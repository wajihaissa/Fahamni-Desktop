package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Quiz;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuizService {

    private final List<Quiz> quizzes = new ArrayList<>(List.of(
        new Quiz("Mathematics - Algebra Basics", "Mathematics", 15, 20,
            "Medium", "Test your understanding of fundamental algebraic concepts", 4.5, 87),
        new Quiz("Physics - Mechanics", "Physics", 20, 25,
            "Hard", "Challenge yourself with advanced mechanics problems", 4.7, 80),
        new Quiz("Chemistry - Organic Compounds", "Chemistry", 18, 22,
            "Medium", "Explore the world of organic chemistry", 4.3, null)
    ));

    public List<Quiz> getAllQuizzes() {
        return new ArrayList<>(quizzes);
    }

    public List<Quiz> getRecentResults() {
        return quizzes.stream()
            .filter(quiz -> quiz.getLastScore() != null)
            .collect(Collectors.toList());
    }

    public Quiz createQuiz(Quiz quiz) {
        quizzes.add(quiz);
        return quiz;
    }
}

