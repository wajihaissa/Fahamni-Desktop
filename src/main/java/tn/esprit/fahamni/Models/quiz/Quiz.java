package tn.esprit.fahamni.Models.quiz;

import javafx.beans.property.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Quiz {
    public static final double DEFAULT_PASS_PERCENTAGE = 60.0;

    // JavaFX Properties
    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty titre = new SimpleStringProperty();
    private final StringProperty keyword = new SimpleStringProperty();
    private final ObjectProperty<Instant> createdAt = new SimpleObjectProperty<>();
    
    private final List<Question> questions;
    private final List<QuizResult> quizResults;

    public Quiz(Long id, String titre, String keyword, Instant createdAt, List<Question> questions, List<QuizResult> quizResults) {
        setId(id);
        setTitre(titre);
        setKeyword(keyword);
        setCreatedAt(createdAt);
        this.questions = questions != null ? questions : new ArrayList<>();
        this.quizResults = quizResults != null ? quizResults : new ArrayList<>();
    }

    public Quiz() {
        this.questions = new ArrayList<>();
        this.quizResults = new ArrayList<>();
        setCreatedAt(Instant.now());
    }

    // JavaFX Property getters (CRITICAL for TableView)
    public LongProperty idProperty() {
        return id;
    }

    public StringProperty titreProperty() {
        return titre;
    }

    public StringProperty keywordProperty() {
        return keyword;
    }

    public ObjectProperty<Instant> createdAtProperty() {
        return createdAt;
    }

    // Regular getters and setters
    public Long getId() {
        return id.get();
    }

    public void setId(Long id) {
        this.id.set(id != null ? id : 0L);
    }

    public String getTitre() {
        return titre.get();
    }

    public void setTitre(String titre) {
        this.titre.set(titre != null ? titre : "");
    }

    public String getKeyword() {
        return keyword.get();
    }

    public void setKeyword(String keyword) {
        this.keyword.set(keyword != null ? keyword : "");
    }

    public Instant getCreatedAt() {
        return createdAt.get();
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt.set(createdAt);
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