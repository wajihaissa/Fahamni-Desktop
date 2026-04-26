package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.UserRole;
import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.QuizAnswerAttempt;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuizService {
    private static final double PASS_PERCENTAGE = Quiz.DEFAULT_PASS_PERCENTAGE;

    private final Connection cnx;

    public QuizService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public Quiz createQuiz(Quiz quiz) {
        String quizQuery = "INSERT INTO quiz (titre, keyword, created_at) VALUES (?, ?, ?)";
        String questionQuery = buildQuestionInsertQuery();
        String choiceQuery = "INSERT INTO choice (choice, is_correct, question_id) VALUES (?, ?, ?)";

        if (!isQuizStructureValid(quiz)) {
            return null;
        }

        try {
            cnx.setAutoCommit(false);
            Instant createdAt = Instant.now();

            try (PreparedStatement quizStmt = cnx.prepareStatement(quizQuery, Statement.RETURN_GENERATED_KEYS)) {
                createQuizRecord(quizStmt, quiz, createdAt);
            }

            if (quiz.getId() != null && quiz.getQuestions() != null) {
                try (PreparedStatement questionStmt = cnx.prepareStatement(questionQuery, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery, Statement.RETURN_GENERATED_KEYS)) {
                    for (Question question : quiz.getQuestions()) {
                        insertQuestionWithChoices(questionStmt, choiceStmt, question, quiz);
                    }
                }
            }

            cnx.commit();
            return quiz;
        } catch (SQLException e) {
            rollbackQuietly();
            System.err.println("Error creating quiz: " + e.getMessage());
            return null;
        } finally {
            resetAutoCommitQuietly();
        }
    }

    public List<Quiz> getAllQuizzes() {
        List<Quiz> quizzes = new ArrayList<>();
        String query = "SELECT * FROM quiz";

        try (Statement stmt = cnx.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                quizzes.add(loadQuizAggregate(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching quizzes: " + e.getMessage());
        }

        return quizzes;
    }

    public Quiz getQuizById(Long id) {
        String query = "SELECT * FROM quiz WHERE id = ?";

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return loadQuizAggregate(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching quiz: " + e.getMessage());
        }

        return null;
    }

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
            return null;
        } finally {
            resetAutoCommitQuietly();
        }
    }

    public boolean deleteQuiz(Long id) {
        String deleteAttemptsQuery = "DELETE qaa FROM quiz_answer_attempt qaa INNER JOIN quiz_result qr ON qaa.quiz_result_id = qr.id WHERE qr.quiz_id = ?";
        String deleteResultsQuery = "DELETE FROM quiz_result WHERE quiz_id = ?";
        String deleteQuizQuery = "DELETE FROM quiz WHERE id = ?";

        try {
            cnx.setAutoCommit(false);

            if (tableExists("quiz_answer_attempt")) {
                try (PreparedStatement deleteAttemptsStmt = cnx.prepareStatement(deleteAttemptsQuery)) {
                    deleteAttemptsStmt.setLong(1, id);
                    deleteAttemptsStmt.executeUpdate();
                }
            }

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

    public List<Quiz> getRecentResults() {
        List<Quiz> quizzes = new ArrayList<>();
        String query = "SELECT DISTINCT q.* FROM quiz q INNER JOIN quiz_result qr ON q.id = qr.quiz_id";

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

    public List<Question> getAllQuestionsFromBank() {
        List<Question> questions = new ArrayList<>();
        String questionQuery = buildQuestionBankQuery();
        String choiceQuery = "SELECT * FROM choice WHERE question_id = ?";

        try (Statement questionStmt = cnx.createStatement();
             ResultSet questionRs = questionStmt.executeQuery(questionQuery)) {
            while (questionRs.next()) {
                questions.add(loadQuestionBankEntry(questionRs, choiceQuery));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching question bank: " + e.getMessage());
        }

        return questions;
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

    public List<QuizAnswerAttempt> getAnswerAttemptsForResult(Long quizResultId) {
        if (quizResultId == null || !tableExists("quiz_answer_attempt")) {
            return List.of();
        }

        String query = "SELECT * FROM quiz_answer_attempt WHERE quiz_result_id = ? ORDER BY id ASC";
        List<QuizAnswerAttempt> answerAttempts = new ArrayList<>();

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setLong(1, quizResultId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    answerAttempts.add(mapResultSetToQuizAnswerAttempt(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading answer attempts: " + e.getMessage());
        }

        return answerAttempts;
    }

    public List<String> getSourceQuizTitlesForQuiz(Long quizId) {
        if (quizId == null || !hasQuestionColumn("source_question_id")) {
            return List.of();
        }
        Set<String> titles = new LinkedHashSet<>(loadSourceQuizTitles(quizId, true));
        if (titles.isEmpty()) {
            titles.addAll(loadSourceQuizTitles(quizId, false));
        }
        return new ArrayList<>(titles);
    }

    public QuizResult submitQuiz(Long quizId, Map<Long, Long> selectedChoiceIdsByQuestionId, User user) {
        Quiz quiz = getQuizById(quizId);
        if (quiz == null || !isQuizStructureValid(quiz)) {
            return null;
        }

        QuizResult result = evaluateQuiz(quiz, selectedChoiceIdsByQuestionId);
        result.setUser(user);

        try {
            cnx.setAutoCommit(false);
            QuizResult persistedResult = persistQuizResult(quiz, result, selectedChoiceIdsByQuestionId);
            if (persistedResult == null) {
                cnx.rollback();
                return null;
            }

            cnx.commit();
            quiz.addQuizResult(persistedResult);
            return persistedResult;
        } catch (SQLException e) {
            rollbackQuietly();
            System.err.println("Error submitting quiz: " + e.getMessage());
            return null;
        } finally {
            resetAutoCommitQuietly();
        }
    }

    public QuizResult evaluateQuiz(Quiz quiz, Map<Long, Long> selectedChoiceIdsByQuestionId) {
        if (quiz == null || !isQuizStructureValid(quiz)) {
            return null;
        }

        int totalQuestions = quiz.getQuestions().size();
        int score = calculateScore(quiz, selectedChoiceIdsByQuestionId);

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
        if (!hasQuizShell(quiz)) {
            return false;
        }

        for (Question question : quiz.getQuestions()) {
            if (!hasValidQuestionShell(question)) {
                return false;
            }

            if (!hasValidChoiceSet(question.getChoices())) {
                return false;
            }
        }

        return true;
    }

    public Question copyQuestionForQuiz(Question source, Quiz quizShell) {
        Question copy = new Question();
        copy.setSourceQuestionId(source.getSourceQuestionId() != null ? source.getSourceQuestionId() : source.getId());
        copy.setQuestion(source.getQuestion());
        copy.setTopic(source.getTopic());
        copy.setDifficulty(source.getDifficulty());
        copy.setHint(source.getHint());
        copy.setExplanation(source.getExplanation());
        copy.setQuiz(quizShell);

        for (Choice sourceChoice : source.getChoices()) {
            Choice choiceCopy = new Choice();
            choiceCopy.setChoice(sourceChoice.getChoice());
            choiceCopy.setIsCorrect(sourceChoice.getIsCorrect());
            copy.addChoice(choiceCopy);
        }

        return copy;
    }

    private Quiz mapResultSetToQuiz(ResultSet rs) throws SQLException {
        Quiz quiz = new Quiz();
        quiz.setId(rs.getLong("id"));
        quiz.setTitre(rs.getString("titre"));
        quiz.setKeyword(rs.getString("keyword"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        quiz.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        return quiz;
    }

    private Quiz loadQuizAggregate(ResultSet rs) throws SQLException {
        Quiz quiz = mapResultSetToQuiz(rs);
        loadQuizQuestions(quiz);
        loadQuizResults(quiz);
        return quiz;
    }

    private void loadQuizQuestions(Quiz quiz) {
        String questionQuery = "SELECT * FROM question WHERE quiz_id = ?";
        String choiceQuery = "SELECT * FROM choice WHERE question_id = ?";

        try (PreparedStatement questionStmt = cnx.prepareStatement(questionQuery)) {
            questionStmt.setLong(1, quiz.getId());
            try (ResultSet questionRs = questionStmt.executeQuery()) {
                while (questionRs.next()) {
                    quiz.addQuestion(loadQuestionWithChoices(questionRs, choiceQuery, quiz));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading questions: " + e.getMessage());
        }
    }

    private void loadQuizResults(Quiz quiz) {
        String query = "SELECT * FROM quiz_result WHERE quiz_id = ? ORDER BY completed_at DESC, id DESC";

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setLong(1, quiz.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    quiz.addQuizResult(mapResultSetToQuizResult(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading results: " + e.getMessage());
        }
    }

    private Question loadQuestionWithChoices(ResultSet questionRs, String choiceQuery, Quiz quiz) throws SQLException {
        Question question = new Question();
        question.setId(questionRs.getLong("id"));
        question.setSourceQuestionId(resolveSourceQuestionId(questionRs));
        question.setQuestion(questionRs.getString("question"));
        question.setTopic(resolveQuestionTopic(questionRs, quiz.getKeyword()));
        question.setDifficulty(resolveQuestionDifficulty(questionRs));
        question.setHint(resolveQuestionHint(questionRs));
        question.setExplanation(resolveQuestionExplanation(questionRs));
        question.setQuiz(quiz);

        try (PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery)) {
            choiceStmt.setLong(1, question.getId());
            try (ResultSet choiceRs = choiceStmt.executeQuery()) {
                while (choiceRs.next()) {
                    question.addChoice(mapResultSetToChoice(choiceRs));
                }
            }
        }

        return question;
    }

    private Question loadQuestionBankEntry(ResultSet questionRs, String choiceQuery) throws SQLException {
        Question question = new Question();
        question.setId(questionRs.getLong("id"));
        question.setSourceQuestionId(resolveSourceQuestionId(questionRs));
        question.setQuestion(questionRs.getString("question"));
        question.setTopic(resolveQuestionTopic(questionRs, questionRs.getString("quiz_keyword")));
        question.setDifficulty(resolveQuestionDifficulty(questionRs));
        question.setHint(resolveQuestionHint(questionRs));
        question.setExplanation(resolveQuestionExplanation(questionRs));

        try (PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery)) {
            choiceStmt.setLong(1, question.getId());
            try (ResultSet choiceRs = choiceStmt.executeQuery()) {
                while (choiceRs.next()) {
                    question.addChoice(mapResultSetToChoice(choiceRs));
                }
            }
        }

        return question;
    }

    private QuizResult mapResultSetToQuizResult(ResultSet rs) throws SQLException {
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

        return result;
    }

    private QuizAnswerAttempt mapResultSetToQuizAnswerAttempt(ResultSet rs) throws SQLException {
        QuizAnswerAttempt answerAttempt = new QuizAnswerAttempt();
        answerAttempt.setId(rs.getLong("id"));
        answerAttempt.setQuizResultId(getNullableLong(rs, "quiz_result_id"));
        answerAttempt.setQuestionId(getNullableLong(rs, "question_id"));
        answerAttempt.setSelectedChoiceId(getNullableLong(rs, "selected_choice_id"));
        answerAttempt.setCorrect(getNullableBoolean(rs, "is_correct"));

        Timestamp answeredAt = rs.getTimestamp("answered_at");
        answerAttempt.setAnsweredAt(answeredAt != null ? answeredAt.toInstant() : null);
        return answerAttempt;
    }

    private Choice mapResultSetToChoice(ResultSet rs) throws SQLException {
        Choice choice = new Choice();
        choice.setId(rs.getLong("id"));
        choice.setChoice(rs.getString("choice"));
        choice.setIsCorrect(rs.getBoolean("is_correct"));
        return choice;
    }

    private void replaceQuizQuestions(Long quizId, List<Question> questions) throws SQLException {
        deleteQuizQuestions(quizId);

        Quiz quizShell = new Quiz();
        quizShell.setId(quizId);

        String questionQuery = buildQuestionInsertQuery();
        String choiceQuery = "INSERT INTO choice (choice, is_correct, question_id) VALUES (?, ?, ?)";

        try (PreparedStatement questionStmt = cnx.prepareStatement(questionQuery, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement choiceStmt = cnx.prepareStatement(choiceQuery, Statement.RETURN_GENERATED_KEYS)) {
            for (Question question : questions) {
                insertQuestionWithChoices(questionStmt, choiceStmt, question, quizShell);
            }
        }
    }

    private void deleteQuizQuestions(Long quizId) throws SQLException {
        if (tableExists("quiz_answer_attempt")) {
            String deleteAttemptsQuery = "DELETE qaa FROM quiz_answer_attempt qaa INNER JOIN question q ON qaa.question_id = q.id WHERE q.quiz_id = ?";
            try (PreparedStatement deleteAttemptsStmt = cnx.prepareStatement(deleteAttemptsQuery)) {
                deleteAttemptsStmt.setLong(1, quizId);
                deleteAttemptsStmt.executeUpdate();
            }
        }

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

    private void createQuizRecord(PreparedStatement statement, Quiz quiz, Instant createdAt) throws SQLException {
        statement.setString(1, quiz.getTitre());
        statement.setString(2, quiz.getKeyword());
        statement.setTimestamp(3, Timestamp.from(createdAt));

        if (statement.executeUpdate() <= 0) {
            return;
        }

        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                quiz.setId(keys.getLong(1));
                quiz.setCreatedAt(createdAt);
            }
        }
    }

    private void insertQuestionWithChoices(
            PreparedStatement questionStatement,
            PreparedStatement choiceStatement,
            Question question,
            Quiz quiz
    ) throws SQLException {
        bindQuestionStatement(questionStatement, question, quiz);
        int questionRows = questionStatement.executeUpdate();
        if (questionRows <= 0) {
            return;
        }

        try (ResultSet questionKeys = questionStatement.getGeneratedKeys()) {
            if (questionKeys.next()) {
                question.setId(questionKeys.getLong(1));
                question.setQuiz(quiz);
            }
        }

        for (Choice choice : question.getChoices()) {
            insertChoice(choiceStatement, choice, question);
        }
    }

    private void insertChoice(PreparedStatement statement, Choice choice, Question question) throws SQLException {
        statement.setString(1, choice.getChoice());
        statement.setBoolean(2, Boolean.TRUE.equals(choice.getIsCorrect()));
        statement.setLong(3, question.getId());
        statement.executeUpdate();

        try (ResultSet choiceKeys = statement.getGeneratedKeys()) {
            if (choiceKeys.next()) {
                choice.setId(choiceKeys.getLong(1));
            }
        }

        choice.setQuestion(question);
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

    private QuizResult persistQuizResult(Quiz quiz, QuizResult result, Map<Long, Long> selectedChoiceIdsByQuestionId) throws SQLException {
        QuizResult persistedResult = saveQuizResult(quiz, result);
        if (persistedResult == null) {
            return null;
        }

        if (tableExists("quiz_answer_attempt")) {
            saveAnswerAttempts(persistedResult, quiz, selectedChoiceIdsByQuestionId);
        }

        return persistedResult;
    }

    private void saveAnswerAttempts(QuizResult result, Quiz quiz, Map<Long, Long> selectedChoiceIdsByQuestionId) throws SQLException {
        String query = """
                INSERT INTO quiz_answer_attempt (quiz_result_id, question_id, selected_choice_id, is_correct, answered_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            for (Question question : quiz.getQuestions()) {
                Choice correctChoice = getCorrectChoice(question);
                Long selectedChoiceId = selectedChoiceIdsByQuestionId != null
                        ? selectedChoiceIdsByQuestionId.get(question.getId())
                        : null;
                boolean isCorrect = correctChoice != null && selectedChoiceId != null && selectedChoiceId.equals(correctChoice.getId());

                stmt.setLong(1, result.getId());
                stmt.setLong(2, question.getId());
                if (selectedChoiceId != null) {
                    stmt.setLong(3, selectedChoiceId);
                } else {
                    stmt.setNull(3, Types.BIGINT);
                }
                stmt.setBoolean(4, isCorrect);
                stmt.setTimestamp(5, Timestamp.from(result.getCompletedAt() != null ? result.getCompletedAt() : Instant.now()));
                stmt.addBatch();
            }

            stmt.executeBatch();
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

    private String buildQuestionInsertQuery() {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        columns.add("question");
        placeholders.add("?");

        if (hasQuestionColumn("topic")) {
            columns.add("topic");
            placeholders.add("?");
        }

        if (hasQuestionColumn("difficulty")) {
            columns.add("difficulty");
            placeholders.add("?");
        }

        if (hasQuestionColumn("source_question_id")) {
            columns.add("source_question_id");
            placeholders.add("?");
        }

        if (hasQuestionColumn("hint")) {
            columns.add("hint");
            placeholders.add("?");
        }

        if (hasQuestionColumn("explanation")) {
            columns.add("explanation");
            placeholders.add("?");
        }

        columns.add("quiz_id");
        placeholders.add("?");

        return "INSERT INTO question (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", placeholders) + ")";
    }

    private void bindQuestionStatement(PreparedStatement statement, Question question, Quiz quiz) throws SQLException {
        int index = 1;
        statement.setString(index++, question.getQuestion());

        if (hasQuestionColumn("topic")) {
            String topic = !isBlank(question.getTopic()) ? question.getTopic() : quiz.getKeyword();
            statement.setString(index++, topic);
        }

        if (hasQuestionColumn("difficulty")) {
            String difficulty = !isBlank(question.getDifficulty()) ? question.getDifficulty() : "Medium";
            statement.setString(index++, difficulty);
        }

        if (hasQuestionColumn("source_question_id")) {
            if (question.getSourceQuestionId() != null) {
                statement.setLong(index++, question.getSourceQuestionId());
            } else {
                statement.setNull(index++, Types.BIGINT);
            }
        }

        if (hasQuestionColumn("hint")) {
            statement.setString(index++, question.getHint());
        }

        if (hasQuestionColumn("explanation")) {
            statement.setString(index++, question.getExplanation());
        }

        statement.setLong(index, quiz.getId());
    }

    private String buildQuestionBankQuery() {
        StringBuilder query = new StringBuilder("SELECT q.*, quiz.keyword AS quiz_keyword FROM question q INNER JOIN quiz ON quiz.id = q.quiz_id");
        if (hasQuestionColumn("source_question_id")) {
            query.append(" WHERE q.source_question_id IS NULL");
        } else {
            query.append(" WHERE quiz.keyword NOT LIKE 'adaptive-%'");
            query.append(" AND quiz.titre NOT LIKE 'Adaptive Quiz - %'");
        }
        return query.toString();
    }

    private List<String> loadSourceQuizTitles(Long quizId, boolean preferDirectSourceLink) {
        String query = preferDirectSourceLink
                ? """
                SELECT DISTINCT source_quiz.titre
                FROM question adaptive_question
                INNER JOIN question source_question ON source_question.id = adaptive_question.source_question_id
                INNER JOIN quiz source_quiz ON source_quiz.id = source_question.quiz_id
                WHERE adaptive_question.quiz_id = ?
                ORDER BY source_quiz.titre ASC
                """
                : """
                SELECT DISTINCT source_quiz.titre
                FROM question adaptive_question
                INNER JOIN question source_question
                    ON source_question.question = adaptive_question.question
                    AND source_question.id <> adaptive_question.id
                    AND source_question.source_question_id IS NULL
                INNER JOIN quiz source_quiz ON source_quiz.id = source_question.quiz_id
                WHERE adaptive_question.quiz_id = ?
                    AND adaptive_question.source_question_id IS NULL
                    AND source_quiz.keyword NOT LIKE 'adaptive-%'
                    AND source_quiz.titre NOT LIKE 'Adaptive Quiz - %'
                ORDER BY source_quiz.titre ASC
                """;

        Set<String> titles = new LinkedHashSet<>();

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setLong(1, quizId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String title = rs.getString("titre");
                    if (!isBlank(title)) {
                        titles.add(title.trim());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching source quiz titles: " + e.getMessage());
        }

        return new ArrayList<>(titles);
    }

    private Choice getCorrectChoice(Question question) {
        return question.getChoices()
                .stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .findFirst()
                .orElse(null);
    }

    private int calculateScore(Quiz quiz, Map<Long, Long> selectedChoiceIdsByQuestionId) {
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

        return score;
    }

    private boolean hasQuizShell(Quiz quiz) {
        return quiz != null
                && !isBlank(quiz.getTitre())
                && !isBlank(quiz.getKeyword())
                && quiz.getQuestions() != null
                && !quiz.getQuestions().isEmpty();
    }

    private boolean hasValidQuestionShell(Question question) {
        return question != null
                && !isBlank(question.getQuestion())
                && question.getChoices() != null
                && question.getChoices().size() >= 2;
    }

    private boolean hasValidChoiceSet(List<Choice> choices) {
        int correctChoices = 0;
        Set<String> normalizedChoices = new HashSet<>();

        for (Choice choice : choices) {
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

        return correctChoices == 1;
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
        return tableHasColumn("quiz_result", columnName);
    }

    private boolean hasQuestionColumn(String columnName) {
        return tableHasColumn("question", columnName);
    }

    private boolean tableHasColumn(String tableName, String columnName) {
        try {
            try (ResultSet columns = getDatabaseMetaData().getColumns(cnx.getCatalog(), null, tableName, columnName)) {
                return columns.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            try (ResultSet tables = getDatabaseMetaData().getTables(cnx.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private DatabaseMetaData getDatabaseMetaData() throws SQLException {
        return cnx.getMetaData();
    }

    private String resolveQuestionTopic(ResultSet rs, String fallback) throws SQLException {
        if (hasColumn(rs, "topic")) {
            String topic = rs.getString("topic");
            if (!isBlank(topic)) {
                return topic;
            }
        }
        return fallback;
    }

    private String resolveQuestionDifficulty(ResultSet rs) throws SQLException {
        if (hasColumn(rs, "difficulty")) {
            String difficulty = rs.getString("difficulty");
            if (!isBlank(difficulty)) {
                return difficulty;
            }
        }
        return "Medium";
    }

    private String resolveQuestionHint(ResultSet rs) throws SQLException {
        if (!hasColumn(rs, "hint")) {
            return "";
        }
        String hint = rs.getString("hint");
        return hint == null ? "" : hint;
    }

    private String resolveQuestionExplanation(ResultSet rs) throws SQLException {
        if (!hasColumn(rs, "explanation")) {
            return "";
        }
        String explanation = rs.getString("explanation");
        return explanation == null ? "" : explanation;
    }

    private Long resolveSourceQuestionId(ResultSet rs) throws SQLException {
        if (!hasColumn(rs, "source_question_id")) {
            return null;
        }
        long sourceQuestionId = rs.getLong("source_question_id");
        return rs.wasNull() ? null : sourceQuestionId;
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
