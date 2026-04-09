package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.Quiz;
import tn.esprit.fahamni.services.QuizService;
import javafx.fxml.FXML;
import java.util.List;

public class QuizController {

    private final QuizService quizService = new QuizService();
    private List<Quiz> quizzes;

    @FXML
    private void initialize() {
        quizzes = quizService.getAllQuizzes();
    }

    public List<Quiz> getQuizzes() {
        return quizzes;
    }
}

