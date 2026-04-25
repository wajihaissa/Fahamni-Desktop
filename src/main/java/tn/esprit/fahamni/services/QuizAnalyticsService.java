package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.quiz.QuizLeaderboardEntry;
import tn.esprit.fahamni.Models.quiz.QuizQuestionPerformance;
import tn.esprit.fahamni.Models.quiz.QuizUserInsight;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class QuizAnalyticsService {
    private final Connection cnx;

    public QuizAnalyticsService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public List<QuizLeaderboardEntry> getLeaderboardEntries(int limit) {
        String query = """
                WITH ranked_attempts AS (
                    SELECT
                        qr.user_id,
                        COALESCE(NULLIF(u.full_name, ''), NULLIF(qr.user_full_name, ''), NULLIF(u.email, ''), NULLIF(qr.user_email, ''), CONCAT('User #', qr.user_id)) AS user_name,
                        COALESCE(NULLIF(u.email, ''), NULLIF(qr.user_email, '')) AS user_email,
                        qr.percentage,
                        qr.passed,
                        qr.completed_at,
                        ROW_NUMBER() OVER (PARTITION BY qr.user_id ORDER BY qr.completed_at) AS chronological_attempt,
                        FIRST_VALUE(qr.percentage) OVER (PARTITION BY qr.user_id ORDER BY qr.completed_at) AS first_percentage,
                        LAST_VALUE(qr.percentage) OVER (
                            PARTITION BY qr.user_id
                            ORDER BY qr.completed_at
                            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
                        ) AS latest_percentage
                    FROM quiz_result qr
                    LEFT JOIN `user` u ON u.id = qr.user_id
                    WHERE qr.user_id IS NOT NULL
                ),
                user_metrics AS (
                    SELECT
                        user_id,
                        MAX(user_name) AS user_name,
                        MAX(user_email) AS user_email,
                        COUNT(*) AS attempts,
                        AVG(percentage) AS average_percentage,
                        MAX(percentage) AS best_percentage,
                        AVG(CASE WHEN passed = 1 THEN 100 ELSE 0 END) AS pass_rate,
                        GREATEST(0, 100 - (STDDEV_POP(percentage) * 2)) AS consistency_score,
                        MAX(latest_percentage - first_percentage) AS improvement_score
                    FROM ranked_attempts
                    GROUP BY user_id
                ),
                scored AS (
                    SELECT
                        user_id,
                        user_name,
                        user_email,
                        attempts,
                        average_percentage,
                        best_percentage,
                        pass_rate,
                        consistency_score,
                        improvement_score,
                        (
                            average_percentage * 0.35 +
                            best_percentage * 0.20 +
                            pass_rate * 0.20 +
                            consistency_score * 0.15 +
                            LEAST(GREATEST(improvement_score, 0), 100) * 0.10
                        ) AS weighted_score
                    FROM user_metrics
                )
                SELECT
                    DENSE_RANK() OVER (ORDER BY weighted_score DESC, average_percentage DESC, attempts DESC) AS leaderboard_rank,
                    user_id,
                    user_name,
                    user_email,
                    attempts,
                    average_percentage,
                    best_percentage,
                    pass_rate,
                    consistency_score,
                    improvement_score,
                    weighted_score
                FROM scored
                ORDER BY weighted_score DESC, average_percentage DESC, attempts DESC
                LIMIT ?
                """;

        List<QuizLeaderboardEntry> entries = new ArrayList<>();

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapLeaderboardEntry(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading leaderboard entries: " + e.getMessage());
        }

        return entries;
    }

    public QuizUserInsight getUserInsight(Integer userId) {
        if (userId == null) {
            return null;
        }

        String summaryQuery = """
                SELECT
                    qr.user_id,
                    COALESCE(
                        MAX(NULLIF(u.full_name, '')),
                        MAX(NULLIF(qr.user_full_name, '')),
                        MAX(NULLIF(u.email, '')),
                        MAX(NULLIF(qr.user_email, '')),
                        CONCAT('User #', qr.user_id)
                    ) AS user_name,
                    COUNT(*) AS total_attempts,
                    AVG(qr.percentage) AS average_percentage,
                    AVG(CASE WHEN qr.passed = 1 THEN 100 ELSE 0 END) AS pass_rate
                FROM quiz_result qr
                LEFT JOIN `user` u ON u.id = qr.user_id
                WHERE qr.user_id = ?
                GROUP BY qr.user_id
                """;
        String topicQuery = """
                WITH topic_performance AS (
                    SELECT
                        COALESCE(NULLIF(q.topic, ''), NULLIF(quiz.keyword, ''), 'General') AS topic,
                        COUNT(*) AS attempts,
                        AVG(CASE WHEN qaa.is_correct = 1 THEN 100 ELSE 0 END) AS accuracy_rate
                    FROM quiz_answer_attempt qaa
                    INNER JOIN question q ON q.id = qaa.question_id
                    INNER JOIN quiz_result qr ON qr.id = qaa.quiz_result_id
                    INNER JOIN quiz ON quiz.id = qr.quiz_id
                    WHERE qr.user_id = ?
                    GROUP BY COALESCE(NULLIF(q.topic, ''), NULLIF(quiz.keyword, ''), 'General')
                )
                SELECT topic, attempts, accuracy_rate
                FROM topic_performance
                ORDER BY accuracy_rate ASC, attempts DESC, topic ASC
                """;

        QuizUserInsight insight = null;

        try (PreparedStatement summaryStmt = cnx.prepareStatement(summaryQuery)) {
            summaryStmt.setInt(1, userId);
            try (ResultSet rs = summaryStmt.executeQuery()) {
                if (rs.next()) {
                    insight = mapUserInsightSummary(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading quiz insight: " + e.getMessage());
        }

        if (insight == null) {
            return null;
        }

        try (PreparedStatement topicStmt = cnx.prepareStatement(topicQuery)) {
            topicStmt.setInt(1, userId);
            try (ResultSet rs = topicStmt.executeQuery()) {
                applyTopicRanking(insight, rs);
            }
        } catch (SQLException e) {
            System.err.println("Error loading topic insight: " + e.getMessage());
        }

        return insight;
    }

    public List<QuizQuestionPerformance> getQuestionPerformanceForUser(Integer userId) {
        if (userId == null) {
            return List.of();
        }

        String query = """
                SELECT
                    COALESCE(
                        q.source_question_id,
                        (
                            SELECT matched.id
                            FROM question matched
                            INNER JOIN quiz matched_quiz ON matched_quiz.id = matched.quiz_id
                            WHERE matched.question = q.question
                                AND matched.id <> q.id
                                AND matched.source_question_id IS NULL
                                AND matched_quiz.keyword NOT LIKE 'adaptive-%'
                                AND matched_quiz.titre NOT LIKE 'Adaptive Quiz - %'
                            ORDER BY matched.id
                            LIMIT 1
                        ),
                        q.id
                    ) AS question_id,
                    COALESCE(NULLIF(q.topic, ''), NULLIF(quiz.keyword, ''), 'General') AS topic,
                    COALESCE(NULLIF(q.difficulty, ''), 'Medium') AS difficulty,
                    COUNT(*) AS attempts,
                    SUM(CASE WHEN qaa.is_correct = 1 THEN 1 ELSE 0 END) AS correct_answers,
                    SUM(CASE WHEN qaa.is_correct = 0 THEN 1 ELSE 0 END) AS incorrect_answers,
                    AVG(CASE WHEN qaa.is_correct = 1 THEN 100 ELSE 0 END) AS accuracy_rate,
                    MAX(qaa.answered_at) AS last_answered_at
                FROM quiz_answer_attempt qaa
                INNER JOIN question q ON q.id = qaa.question_id
                INNER JOIN quiz_result qr ON qr.id = qaa.quiz_result_id
                INNER JOIN quiz ON quiz.id = qr.quiz_id
                WHERE qr.user_id = ?
                GROUP BY
                    COALESCE(
                        q.source_question_id,
                        (
                            SELECT matched.id
                            FROM question matched
                            INNER JOIN quiz matched_quiz ON matched_quiz.id = matched.quiz_id
                            WHERE matched.question = q.question
                                AND matched.id <> q.id
                                AND matched.source_question_id IS NULL
                                AND matched_quiz.keyword NOT LIKE 'adaptive-%'
                                AND matched_quiz.titre NOT LIKE 'Adaptive Quiz - %'
                            ORDER BY matched.id
                            LIMIT 1
                        ),
                        q.id
                    ),
                    COALESCE(NULLIF(q.topic, ''), NULLIF(quiz.keyword, ''), 'General'),
                    COALESCE(NULLIF(q.difficulty, ''), 'Medium')
                ORDER BY accuracy_rate ASC, attempts DESC, last_answered_at DESC
                """;

        List<QuizQuestionPerformance> performances = new ArrayList<>();

        try (PreparedStatement stmt = cnx.prepareStatement(query)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    performances.add(mapQuestionPerformance(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading question performance: " + e.getMessage());
        }

        return performances;
    }

    private String resolveRecommendedDifficulty(double averagePercentage, double passRate, int attempts) {
        if (attempts < 2) {
            return "Medium";
        }
        if (averagePercentage >= 80.0 && passRate >= 70.0) {
            return "Hard";
        }
        if (averagePercentage < 55.0 || passRate < 50.0) {
            return "Easy";
        }
        return "Medium";
    }

    private QuizLeaderboardEntry mapLeaderboardEntry(ResultSet rs) throws SQLException {
        QuizLeaderboardEntry entry = new QuizLeaderboardEntry();
        entry.setRank(rs.getInt("leaderboard_rank"));
        entry.setUserId(rs.getInt("user_id"));
        entry.setUserName(rs.getString("user_name"));
        entry.setUserEmail(rs.getString("user_email"));
        entry.setAttempts(rs.getInt("attempts"));
        entry.setAveragePercentage(rs.getDouble("average_percentage"));
        entry.setBestPercentage(rs.getDouble("best_percentage"));
        entry.setPassRate(rs.getDouble("pass_rate"));
        entry.setConsistencyScore(rs.getDouble("consistency_score"));
        entry.setImprovementScore(rs.getDouble("improvement_score"));
        entry.setWeightedScore(rs.getDouble("weighted_score"));
        return entry;
    }

    private QuizQuestionPerformance mapQuestionPerformance(ResultSet rs) throws SQLException {
        QuizQuestionPerformance performance = new QuizQuestionPerformance();
        performance.setQuestionId(rs.getLong("question_id"));
        performance.setTopic(rs.getString("topic"));
        performance.setDifficulty(rs.getString("difficulty"));
        performance.setAttempts(rs.getInt("attempts"));
        performance.setCorrectAnswers(rs.getInt("correct_answers"));
        performance.setIncorrectAnswers(rs.getInt("incorrect_answers"));
        performance.setAccuracyRate(rs.getDouble("accuracy_rate"));
        Timestamp lastAnsweredAt = rs.getTimestamp("last_answered_at");
        performance.setLastAnsweredAt(lastAnsweredAt != null ? lastAnsweredAt.toInstant() : null);
        return performance;
    }

    private QuizUserInsight mapUserInsightSummary(ResultSet rs) throws SQLException {
        QuizUserInsight insight = new QuizUserInsight();
        insight.setUserId(rs.getInt("user_id"));
        insight.setUserName(rs.getString("user_name"));
        insight.setTotalAttempts(rs.getInt("total_attempts"));
        insight.setAveragePercentage(rs.getDouble("average_percentage"));
        insight.setPassRate(rs.getDouble("pass_rate"));
        insight.setRecommendedDifficulty(resolveRecommendedDifficulty(
                insight.getAveragePercentage(),
                insight.getPassRate(),
                insight.getTotalAttempts()
        ));
        return insight;
    }

    private void applyTopicRanking(QuizUserInsight insight, ResultSet rs) throws SQLException {
        List<String> rankedTopics = new ArrayList<>();
        while (rs.next()) {
            rankedTopics.add(rs.getString("topic"));
        }

        for (int i = 0; i < rankedTopics.size() && i < 3; i++) {
            insight.getWeakestTopics().add(rankedTopics.get(i));
        }
        for (int i = rankedTopics.size() - 1; i >= 0 && insight.getStrongestTopics().size() < 3; i--) {
            insight.getStrongestTopics().add(rankedTopics.get(i));
        }
    }
}
