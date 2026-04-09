package tn.esprit.fahamni.Models.quiz;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Quiz {
    public static final double DEFAULT_PASS_PERCENTAGE = 60.0;

    private Long id;
    private String titre;
    private String keyword;
    private Instant createdAt;
    private final List<Question> questions;
    private final List<QuizResult> quizResults;

    public Quiz(Long id, String titre, String keyword, Instant createdAt, List<Question> questions, List<QuizResult> quizResults) {
        this.id = id;
        this.titre = titre;
        this.keyword = keyword;
        this.createdAt = createdAt;
        this.questions = questions != null ? questions : new ArrayList<>();
        this.quizResults = quizResults != null ? quizResults : new ArrayList<>();
    }

    public Quiz() {
        this.questions = new ArrayList<>();
        this.quizResults = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void addQuestion(Question question) {
        if (question != null && !questions.contains(question)) {
            questions.add(question);
            question.setQuiz(this);
        }
    }

    public void removeQuestion(Question question) {
        if (questions.remove(question) && question != null && question.getQuiz() == this) {
            question.setQuiz(null);
        }
    }

    public List<QuizResult> getQuizResults() {
        return quizResults;
    }

    public void addQuizResult(QuizResult quizResult) {
        if (quizResult != null && !quizResults.contains(quizResult)) {
            quizResults.add(quizResult);
            quizResult.setQuiz(this);
        }
    }

    public void removeQuizResult(QuizResult quizResult) {
        if (quizResults.remove(quizResult) && quizResult != null && quizResult.getQuiz() == this) {
            quizResult.setQuiz(null);
        }
    }
}
