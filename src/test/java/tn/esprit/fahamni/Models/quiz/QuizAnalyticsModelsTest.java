package tn.esprit.fahamni.Models.quiz;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizAnalyticsModelsTest {

    @Test
    void analyticsHelpersExposeMeaningfulState() {
        QuizQuestionPerformance performance = new QuizQuestionPerformance();
        performance.setAttempts(2);
        performance.setLastAnsweredAt(Instant.now());

        QuizLeaderboardEntry leaderboardEntry = new QuizLeaderboardEntry();
        leaderboardEntry.setUserName("Quiz Tester");

        QuizUserInsight insight = new QuizUserInsight();
        insight.setTotalAttempts(3);
        insight.getWeakestTopics().add("Java");
        insight.getStrongestTopics().add("SQL");

        assertTrue(performance.hasAttempts());
        assertTrue(performance.hasRecentAnswer());
        assertTrue(leaderboardEntry.hasIdentity());
        assertTrue(insight.hasAttempts());
        assertTrue(insight.hasWeakTopics());
        assertTrue(insight.hasStrongTopics());
    }

    @Test
    void analyticsHelpersReportEmptyStateWhenUnset() {
        QuizQuestionPerformance performance = new QuizQuestionPerformance();
        QuizLeaderboardEntry leaderboardEntry = new QuizLeaderboardEntry();
        QuizUserInsight insight = new QuizUserInsight();

        assertFalse(performance.hasAttempts());
        assertFalse(performance.hasRecentAnswer());
        assertFalse(leaderboardEntry.hasIdentity());
        assertFalse(insight.hasAttempts());
        assertFalse(insight.hasWeakTopics());
        assertFalse(insight.hasStrongTopics());
    }
}
