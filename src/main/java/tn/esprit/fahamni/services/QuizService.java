package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuizService {
    private static final double PASS_PERCENTAGE = Quiz.DEFAULT_PASS_PERCENTAGE;

    private final Connection cnx;

    public QuizService() {
        // Use Singleton instance
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    // CREATE
    public Quiz createQuiz(Quiz quiz) {
        String quizQuery = "INSERT INTO quiz (titre, keyword, created_at) VALUES (?, ?, ?)";
        String questionQuery = "INSERT INTO question (question, quiz_id) VALUES (?, ?)";
        String choiceQuery = "INSERT INTO choice (choice, is_correct, question_id) VALUES (?, ?, ?)";

        if (!isQuizStructureValid(quiz)) {
            return null;
        }

        try {
            cnx.setAutoCommit(false); // Start transaction
            Instant createdAt = Instant.now();

            // 1. Insert Quiz
            try (PreparedStatement quizStmt = cnx.prepareStatement(quizQuery, Statement.RETURN_GENERATED_KEYS)) {
                quizStmt.setString(1, quiz.getTitre());
                quizStmt.setString(2, quiz.getKeyword());
                quizStmt.setTimestamp(3, Timestamp.from(createdAt));

                int rowsAffected = quizStmt.executeUpdate();

                if (rowsAffected > 0) {
                    ResultSet rs = quizStmt.getGeneratedKeys();
                    if (rs.next()) {
                        quiz.setId(rs.getLong(1));
                        quiz.setCreatedAt(createdAt);
                    }
                }
            }

            // 2. Insert Questions and their Choices
            if (quiz.getId() != null && quiz.getQuestions() != null) {
                try (PreparedStatement questionStmt = cnx.prepareStatement(questionQuery, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery, Statement.RETURN_GENERATED_KEYS)) {

                    for (Question question : quiz.getQuestions()) {
                        // Insert Question
                        questionStmt.setString(1, question.getQuestion());
                        questionStmt.setLong(2, quiz.getId());

                        int questionRows = questionStmt.executeUpdate();

                        if (questionRows > 0) {
                            ResultSet qRs = questionStmt.getGeneratedKeys();
                            if (qRs.next()) {
                                question.setId(qRs.getLong(1));
                                question.setQuiz(quiz);
                            }

                            // Insert Choices for this Question
                            if (question.getChoices() != null && !question.getChoices().isEmpty()) {
                                for (Choice choice : question.getChoices()) {
                                    choiceStmt.setString(1, choice.getChoice());
                                    choiceStmt.setBoolean(2, choice.getIsCorrect() != null ? choice.getIsCorrect() : false);
                                    choiceStmt.setLong(3, question.getId());
                                    choiceStmt.executeUpdate();

                                    try (ResultSet choiceRs = choiceStmt.getGeneratedKeys()) {
                                        if (choiceRs.next()) {
                                            choice.setId(choiceRs.getLong(1));
                                        }
                                    }

                                    choice.setQuestion(question);
                                }
                            }
                        }
                    }
                }
            }

            cnx.commit(); // Commit transaction
            return quiz;

        } catch (SQLException e) {
            try {
                cnx.rollback(); // Rollback on error
                System.err.println("Transaction rolled back");
            } catch (SQLException ex) {
                System.err.println("Error during rollback: " + ex.getMessage());
            }
            System.err.println("Error creating quiz: " + e.getMessage());
            return null;
        } finally {
            try {
                cnx.setAutoCommit(true); // Reset auto-commit to default
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    // READ - All
    public List<Quiz> getAllQuizzes() {
        List<Quiz> quizzes = new ArrayList<>();
        String query = "SELECT * FROM quiz";

        try (Statement stmt = cnx.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Quiz quiz = mapResultSetToQuiz(rs);
                loadQuizQuestions(quiz);
                loadQuizResults(quiz);
                quizzes.add(quiz);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching quizzes: " + e.getMessage());
        }

        return quizzes;
    }

    // READ - By ID
    public Quiz getQuizById(Long id) {
        String query = "SELECT * FROM quiz WHERE id = ?";

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Quiz quiz = mapResultSetToQuiz(rs);
                loadQuizQuestions(quiz);
                loadQuizResults(quiz);
                return quiz;
            }
        } catch (SQLException e) {
            System.err.println("Error fetching quiz: " + e.getMessage());
        }

        return null;
    }

    // UPDATE
    public Quiz updateQuiz(Long id, Quiz updatedQuiz) {
        String query = "UPDATE quiz SET titre = ?, keyword = ? WHERE id = ?";

        if (!isQuizStructureValid(updatedQuiz)) {
            return null;
        }

        try {
            cnx.setAutoCommit(false);

            try (PreparedStatement stmt = cnx.prepareStatement(query)) {
                stmt.setString(1, updatedQuiz.getTitre());
                stmt.setString(2, updatedQuiz.getKeyword());
                stmt.setLong(3, id);

                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected == 0) {
                    cnx.rollback();
                    return null;
                }
            }

            replaceQuizQuestions(id, updatedQuiz.getQuestions());
            cnx.commit();
            return getQuizById(id);
        } catch (SQLException e) {
            rollbackQuietly();
            System.err.println("Error updating quiz: " + e.getMessage());
        } finally {
            resetAutoCommitQuietly();
        }

        return null;
    }

    // DELETE
    public boolean deleteQuiz(Long id) {
        String deleteResultsQuery = "DELETE FROM quiz_result WHERE quiz_id = ?";
        String deleteQuizQuery = "DELETE FROM quiz WHERE id = ?";

        try {
            cnx.setAutoCommit(false);

            try (PreparedStatement deleteResultsStmt = cnx.prepareStatement(deleteResultsQuery);
                 PreparedStatement deleteQuizStmt = cnx.prepareStatement(deleteQuizQuery)) {
                deleteResultsStmt.setLong(1, id);
                deleteResultsStmt.executeUpdate();

                deleteQuizQuestions(id);

                deleteQuizStmt.setLong(1, id);
                int rowsAffected = deleteQuizStmt.executeUpdate();

                cnx.commit();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            rollbackQuietly();
            System.err.println("Error deleting quiz: " + e.getMessage());
            return false;
        } finally {
            resetAutoCommitQuietly();
        }
    }

    // Get quizzes with results
    public List<Quiz> getRecentResults() {
        List<Quiz> quizzes = new ArrayList<>();
        String query = "SELECT DISTINCT q.* FROM quiz q " +
                "INNER JOIN quiz_result qr ON q.id = qr.quiz_id";

        try (Statement stmt = cnx.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Quiz quiz = mapResultSetToQuiz(rs);
                loadQuizResults(quiz);
                quizzes.add(quiz);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching recent results: " + e.getMessage());
        }

        return quizzes;
    }

    // Helper to map ResultSet to Quiz
    private Quiz mapResultSetToQuiz(ResultSet rs) throws SQLException {
        Quiz quiz = new Quiz();
        quiz.setId(rs.getLong("id"));
        quiz.setTitre(rs.getString("titre"));
        quiz.setKeyword(rs.getString("keyword"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        quiz.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        return quiz;
    }

    // Load questions for a quiz
    private void loadQuizQuestions(Quiz quiz) {
        // Query for questions
        String questionQuery = "SELECT * FROM question WHERE quiz_id = ?";
        // Query for choices of a specific question
        String choiceQuery = "SELECT * FROM choice WHERE question_id = ?";

        try (PreparedStatement questionStmt = cnx.prepareStatement(questionQuery)) {
            questionStmt.setLong(1, quiz.getId());
            ResultSet questionRs = questionStmt.executeQuery();

            while (questionRs.next()) {
                Question q = new Question();
                q.setId(questionRs.getLong("id"));
                q.setQuestion(questionRs.getString("question"));
                q.setQuiz(quiz); // Set the bidirectional relationship

                // Load choices for this question
                try (PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery)) {
                    choiceStmt.setLong(1, q.getId());
                    ResultSet choiceRs = choiceStmt.executeQuery();

                    while (choiceRs.next()) {
                        Choice c = new Choice();
                        c.setId(choiceRs.getLong("id"));
                        c.setChoice(choiceRs.getString("choice"));
                        c.setIsCorrect(choiceRs.getBoolean("is_correct"));

                        // This automatically sets the bidirectional relationship
                        q.addChoice(c);
                    }
                }

                quiz.addQuestion(q);
            }
        } catch (SQLException e) {
            System.err.println("Error loading questions: " + e.getMessage());
        }
    }
    // Load results for a quiz
    private void loadQuizResults(Quiz quiz) {
        String query = "SELECT * FROM quiz_result WHERE quiz_id = ?";

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setLong(1, quiz.getId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                QuizResult result = new QuizResult();
                result.setId(rs.getLong("id"));
                result.setScore(rs.getInt("score"));
                result.setTotalQuestions(getNullableInt(rs, "total_questions"));
                result.setPercentage(getNullableDouble(rs, "percentage"));
                result.setPassed(getNullableBoolean(rs, "passed"));

                Timestamp completedAt = rs.getTimestamp("completed_at");
                result.setCompletedAt(completedAt != null ? completedAt.toInstant() : null);

                if (hasColumn(rs, "user_email")) {
                    String email = rs.getString("user_email");
                    if (email != null && !email.isBlank()) {
                        String fullName = hasColumn(rs, "user_full_name") ? rs.getString("user_full_name") : email;
                        Long userId = getNullableLong(rs, "user_id");
                        result.setUser(new User(userId, fullName, email, "", UserRole.USER));
                    }
                }

                quiz.addQuizResult(result);
            }
        } catch (SQLException e) {
            System.err.println("Error loading results: " + e.getMessage());
        }
    }

    public Integer getLastScore(Quiz quiz) {
        if (quiz == null || quiz.getQuizResults().isEmpty()) {
            return null;
        }
        return quiz.getQuizResults()
                .stream()
                .filter(result -> result.getCompletedAt() != null)
                .max((r1, r2) -> r1.getCompletedAt().compareTo(r2.getCompletedAt()))
                .map(QuizResult::getScore)
                .orElse(null);
    }

    public QuizResult submitQuiz(Long quizId, Map<Long, Long> selectedChoiceIdsByQuestionId, User user) {
        Quiz quiz = getQuizById(quizId);
        if (quiz == null || !isQuizStructureValid(quiz)) {
            return null;
        }

        QuizResult result = evaluateQuiz(quiz, selectedChoiceIdsByQuestionId);
        result.setUser(user);

        QuizResult persistedResult = saveQuizResult(quiz, result);
        if (persistedResult != null) {
            quiz.addQuizResult(persistedResult);
        }

        return persistedResult;
    }

    public QuizResult evaluateQuiz(Quiz quiz, Map<Long, Long> selectedChoiceIdsByQuestionId) {
        if (quiz == null || !isQuizStructureValid(quiz)) {
            return null;
        }

        int totalQuestions = quiz.getQuestions().size();
        int score = 0;

        for (Question question : quiz.getQuestions()) {
            Choice correctChoice = getCorrectChoice(question);
            if (correctChoice == null) {
                continue;
            }

            Long selectedChoiceId = selectedChoiceIdsByQuestionId != null
                    ? selectedChoiceIdsByQuestionId.get(question.getId())
                    : null;

            if (selectedChoiceId != null && selectedChoiceId.equals(correctChoice.getId())) {
                score++;
            }
        }

        QuizResult result = new QuizResult();
        result.setQuiz(quiz);
        result.setScore(score);
        result.setTotalQuestions(totalQuestions);

        double percentage = totalQuestions == 0 ? 0.0 : (score * 100.0) / totalQuestions;
        result.setPercentage(percentage);
        result.setPassed(percentage >= PASS_PERCENTAGE);
        result.setCompletedAt(Instant.now());
        return result;
    }

    public boolean isQuizStructureValid(Quiz quiz) {
        if (quiz == null || isBlank(quiz.getTitre()) || isBlank(quiz.getKeyword()) || quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            return false;
        }

        for (Question question : quiz.getQuestions()) {
            if (question == null || isBlank(question.getQuestion()) || question.getChoices() == null || question.getChoices().size() < 2) {
                return false;
            }

            int correctChoices = 0;
            Set<String> normalizedChoices = new HashSet<>();

            for (Choice choice : question.getChoices()) {
                if (choice == null || isBlank(choice.getChoice())) {
                    return false;
                }

                String normalizedChoice = choice.getChoice().trim().toLowerCase();
                if (!normalizedChoices.add(normalizedChoice)) {
                    return false;
                }

                if (Boolean.TRUE.equals(choice.getIsCorrect())) {
                    correctChoices++;
                }
            }

            if (correctChoices != 1) {
                return false;
            }
        }

        return true;
    }

    private void replaceQuizQuestions(Long quizId, List<Question> questions) throws SQLException {
        deleteQuizQuestions(quizId);

        Quiz quizShell = new Quiz();
        quizShell.setId(quizId);

        String questionQuery = "INSERT INTO question (question, quiz_id) VALUES (?, ?)";
        String choiceQuery = "INSERT INTO choice (choice, is_correct, question_id) VALUES (?, ?, ?)";

        try (PreparedStatement questionStmt = cnx.prepareStatement(questionQuery, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery, Statement.RETURN_GENERATED_KEYS)) {

            for (Question question : questions) {
                questionStmt.setString(1, question.getQuestion());
                questionStmt.setLong(2, quizId);
                questionStmt.executeUpdate();

                try (ResultSet questionKeys = questionStmt.getGeneratedKeys()) {
                    if (questionKeys.next()) {
                        question.setId(questionKeys.getLong(1));
                    }
                }

                question.setQuiz(quizShell);

                for (Choice choice : question.getChoices()) {
                    choiceStmt.setString(1, choice.getChoice());
                    choiceStmt.setBoolean(2, Boolean.TRUE.equals(choice.getIsCorrect()));
                    choiceStmt.setLong(3, question.getId());
                    choiceStmt.executeUpdate();

                    try (ResultSet choiceKeys = choiceStmt.getGeneratedKeys()) {
                        if (choiceKeys.next()) {
                            choice.setId(choiceKeys.getLong(1));
                        }
                    }

                    choice.setQuestion(question);
                }
            }
        }
    }

    private void deleteQuizQuestions(Long quizId) throws SQLException {
        String deleteChoicesQuery = "DELETE c FROM choice c INNER JOIN question q ON c.question_id = q.id WHERE q.quiz_id = ?";
        String deleteQuestionsQuery = "DELETE FROM question WHERE quiz_id = ?";

        try (PreparedStatement deleteChoicesStmt = cnx.prepareStatement(deleteChoicesQuery);
             PreparedStatement deleteQuestionsStmt = cnx.prepareStatement(deleteQuestionsQuery)) {
            deleteChoicesStmt.setLong(1, quizId);
            deleteChoicesStmt.executeUpdate();

            deleteQuestionsStmt.setLong(1, quizId);
            deleteQuestionsStmt.executeUpdate();
        }
    }

    private QuizResult saveQuizResult(Quiz quiz, QuizResult result) {
        String query = buildQuizResultInsertQuery();

        try (PreparedStatement stmt = cnx.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            stmt.setLong(index++, quiz.getId());
            stmt.setInt(index++, result.getScore());
            stmt.setTimestamp(index++, Timestamp.from(result.getCompletedAt()));

            if (hasQuizResultColumn("total_questions")) {
                stmt.setInt(index++, result.getTotalQuestions());
            }

            if (hasQuizResultColumn("percentage")) {
                stmt.setDouble(index++, result.getPercentage());
            }

            if (hasQuizResultColumn("passed")) {
                stmt.setBoolean(index++, Boolean.TRUE.equals(result.getPassed()));
            }

            if (hasQuizResultColumn("user_id")) {
                if (result.getUser() != null && result.getUser().getId() != null) {
                    stmt.setLong(index++, result.getUser().getId());
                } else {
                    stmt.setNull(index++, Types.BIGINT);
                }
            }

            if (hasQuizResultColumn("user_email")) {
                stmt.setString(index++, result.getUser() != null ? result.getUser().getEmail() : null);
            }

            if (hasQuizResultColumn("user_full_name")) {
                stmt.setString(index, result.getUser() != null ? result.getUser().getFullName() : null);
            }

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                return null;
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    result.setId(keys.getLong(1));
                }
            }

            return result;
        } catch (SQLException e) {
            System.err.println("Error saving quiz result: " + e.getMessage());
            return null;
        }
    }

    private String buildQuizResultInsertQuery() {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        columns.add("quiz_id");
        placeholders.add("?");
        columns.add("score");
        placeholders.add("?");
        columns.add("completed_at");
        placeholders.add("?");

        if (hasQuizResultColumn("total_questions")) {
            columns.add("total_questions");
            placeholders.add("?");
        }

        if (hasQuizResultColumn("percentage")) {
            columns.add("percentage");
            placeholders.add("?");
        }

        if (hasQuizResultColumn("passed")) {
            columns.add("passed");
            placeholders.add("?");
        }

        if (hasQuizResultColumn("user_id")) {
            columns.add("user_id");
            placeholders.add("?");
        }

        if (hasQuizResultColumn("user_email")) {
            columns.add("user_email");
            placeholders.add("?");
        }

        if (hasQuizResultColumn("user_full_name")) {
            columns.add("user_full_name");
            placeholders.add("?");
        }

        return "INSERT INTO quiz_result (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", placeholders) + ")";
    }

    private Choice getCorrectChoice(Question question) {
        return question.getChoices()
                .stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .findFirst()
                .orElse(null);
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) {
            return null;
        }

        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) {
            return null;
        }

        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) {
            return null;
        }

        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) {
            return null;
        }

        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (columnName.equalsIgnoreCase(metaData.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasQuizResultColumn(String columnName) {
        try {
            DatabaseMetaData metaData = cnx.getMetaData();
            try (ResultSet columns = metaData.getColumns(cnx.getCatalog(), null, "quiz_result", columnName)) {
                return columns.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void rollbackQuietly() {
        try {
            cnx.rollback();
        } catch (SQLException rollbackError) {
            System.err.println("Error during rollback: " + rollbackError.getMessage());
        }
    }

    private void resetAutoCommitQuietly() {
        try {
            cnx.setAutoCommit(true);
        } catch (SQLException autoCommitError) {
            System.err.println("Error resetting auto-commit: " + autoCommitError.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
