package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizQuestionPerformance;
import tn.esprit.fahamni.Models.quiz.QuizUserInsight;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AdaptiveQuizService {
    private final QuizService quizService = new QuizService();
    private final QuizAnalyticsService analyticsService = new QuizAnalyticsService();

    public Quiz generateAdaptiveQuizForUser(User user, int desiredQuestionCount) {
        if (user == null || user.getId() == null || desiredQuestionCount <= 0) {
            return null;
        }

        List<Question> questionBank = quizService.getAllQuestionsFromBank();
        if (questionBank.size() < 2) {
            return null;
        }

        QuizUserInsight insight = analyticsService.getUserInsight(user.getId());
        List<QuizQuestionPerformance> performanceHistory = analyticsService.getQuestionPerformanceForUser(user.getId());
        Map<Long, QuizQuestionPerformance> performanceByQuestionId = performanceHistory.stream()
                .collect(Collectors.toMap(QuizQuestionPerformance::getQuestionId, performance -> performance, (left, right) -> left, HashMap::new));

        Map<String, Double> topicWeaknessScores = buildTopicWeaknessScores(questionBank, performanceHistory, insight);
        Map<String, Integer> targetDifficultyDistribution = buildDifficultyDistribution(
                insight != null ? insight.getRecommendedDifficulty() : "Medium",
                desiredQuestionCount
        );

        List<QuestionCandidate> rankedCandidates = new ArrayList<>();
        for (Question question : questionBank) {
            QuizQuestionPerformance performance = performanceByQuestionId.get(question.getId());
            rankedCandidates.add(new QuestionCandidate(
                    question,
                    computeCandidateScore(question, performance, topicWeaknessScores, targetDifficultyDistribution)
            ));
        }

        rankedCandidates.sort(Comparator
                .comparingDouble(QuestionCandidate::score).reversed()
                .thenComparing(candidate -> candidate.question().getQuestion(), String.CASE_INSENSITIVE_ORDER));

        List<Question> selectedQuestions = selectBalancedQuestions(rankedCandidates, desiredQuestionCount, targetDifficultyDistribution);
        if (selectedQuestions.size() < 2) {
            return null;
        }

        return persistAdaptiveQuiz(user, selectedQuestions, insight);
    }

    private Map<String, Double> buildTopicWeaknessScores(
            List<Question> questionBank,
            List<QuizQuestionPerformance> performanceHistory,
            QuizUserInsight insight
    ) {
        Map<String, Double> weaknessScores = new HashMap<>();

        for (Question question : questionBank) {
            weaknessScores.put(normalizeTopic(question.getTopic()), 45.0);
        }

        for (QuizQuestionPerformance performance : performanceHistory) {
            String topic = normalizeTopic(performance.getTopic());
            double errorRate = 100.0 - performance.getAccuracyRate();
            double repetitionBonus = Math.min(performance.getAttempts() * 8.0, 24.0);
            weaknessScores.put(topic, errorRate + repetitionBonus);
        }

        if (insight != null) {
            for (int i = 0; i < insight.getWeakestTopics().size(); i++) {
                String topic = normalizeTopic(insight.getWeakestTopics().get(i));
                weaknessScores.merge(topic, 25.0 - (i * 5.0), Double::sum);
            }
            for (int i = 0; i < insight.getStrongestTopics().size(); i++) {
                String topic = normalizeTopic(insight.getStrongestTopics().get(i));
                weaknessScores.merge(topic, -10.0 - (i * 3.0), Double::sum);
            }
        }

        return weaknessScores;
    }

    private Map<String, Integer> buildDifficultyDistribution(String recommendedDifficulty, int desiredQuestionCount) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        String normalized = normalizeDifficulty(recommendedDifficulty);

        if ("Hard".equals(normalized)) {
            distribution.put("Hard", Math.max(1, (int) Math.ceil(desiredQuestionCount * 0.5)));
            distribution.put("Medium", Math.max(1, desiredQuestionCount / 3));
            distribution.put("Easy", Math.max(0, desiredQuestionCount - distribution.get("Hard") - distribution.get("Medium")));
            return distribution;
        }

        if ("Easy".equals(normalized)) {
            distribution.put("Easy", Math.max(1, (int) Math.ceil(desiredQuestionCount * 0.5)));
            distribution.put("Medium", Math.max(1, desiredQuestionCount / 3));
            distribution.put("Hard", Math.max(0, desiredQuestionCount - distribution.get("Easy") - distribution.get("Medium")));
            return distribution;
        }

        distribution.put("Medium", Math.max(1, (int) Math.ceil(desiredQuestionCount * 0.5)));
        distribution.put("Easy", Math.max(1, desiredQuestionCount / 4));
        distribution.put("Hard", Math.max(0, desiredQuestionCount - distribution.get("Medium") - distribution.get("Easy")));
        return distribution;
    }

    private double computeCandidateScore(
            Question question,
            QuizQuestionPerformance performance,
            Map<String, Double> topicWeaknessScores,
            Map<String, Integer> targetDifficultyDistribution
    ) {
        String topic = normalizeTopic(question.getTopic());
        String difficulty = normalizeDifficulty(question.getDifficulty());
        double topicWeight = topicWeaknessScores.getOrDefault(topic, 45.0);

        double accuracyPenalty = computeAccuracyPenalty(performance);
        double retryBonus = computeRetryBonus(performance);
        double noveltyBonus = computeNoveltyBonus(performance);
        double freshnessPenalty = computeFreshnessPenalty(performance);
        double difficultyFit = computeDifficultyFit(difficulty, targetDifficultyDistribution);

        return topicWeight + accuracyPenalty + retryBonus + noveltyBonus + difficultyFit - freshnessPenalty;
    }

    private List<Question> selectBalancedQuestions(
            List<QuestionCandidate> rankedCandidates,
            int desiredQuestionCount,
            Map<String, Integer> targetDifficultyDistribution
    ) {
        List<Question> selected = new ArrayList<>();
        Set<Long> usedQuestionIds = new HashSet<>();
        Map<String, Integer> topicUsage = new HashMap<>();
        Map<String, Integer> difficultyUsage = new HashMap<>();
        int maxPerTopic = Math.max(2, desiredQuestionCount / 2);

        for (QuestionCandidate candidate : rankedCandidates) {
            if (selected.size() >= desiredQuestionCount) {
                break;
            }

            Question question = candidate.question();
            String topic = normalizeTopic(question.getTopic());
            String difficulty = normalizeDifficulty(question.getDifficulty());

            if (!usedQuestionIds.add(question.getId())) {
                continue;
            }

            int currentTopicUsage = topicUsage.getOrDefault(topic, 0);
            if (topicQuotaReached(currentTopicUsage, maxPerTopic)
                    && hasAlternativeTopic(rankedCandidates, usedQuestionIds, topic, targetDifficultyDistribution, difficultyUsage)) {
                usedQuestionIds.remove(question.getId());
                continue;
            }

            int difficultyTarget = targetDifficultyDistribution.getOrDefault(difficulty, 0);
            int currentDifficultyUsage = difficultyUsage.getOrDefault(difficulty, 0);
            if (difficultyQuotaReached(currentDifficultyUsage, difficultyTarget)
                    && hasAlternativeDifficulty(rankedCandidates, usedQuestionIds, difficulty, targetDifficultyDistribution, difficultyUsage)) {
                usedQuestionIds.remove(question.getId());
                continue;
            }

            selected.add(question);
            topicUsage.merge(topic, 1, Integer::sum);
            difficultyUsage.merge(difficulty, 1, Integer::sum);
        }

        if (selected.size() < desiredQuestionCount) {
            for (QuestionCandidate candidate : rankedCandidates) {
                if (selected.size() >= desiredQuestionCount) {
                    break;
                }
                if (usedQuestionIds.add(candidate.question().getId())) {
                    selected.add(candidate.question());
                }
            }
        }

        return selected;
    }

    private boolean hasAlternativeTopic(
            List<QuestionCandidate> candidates,
            Set<Long> usedQuestionIds,
            String blockedTopic,
            Map<String, Integer> targetDifficultyDistribution,
            Map<String, Integer> difficultyUsage
    ) {
        for (QuestionCandidate candidate : candidates) {
            if (usedQuestionIds.contains(candidate.question().getId())) {
                continue;
            }

            String candidateTopic = normalizeTopic(candidate.question().getTopic());
            if (blockedTopic.equals(candidateTopic)) {
                continue;
            }

            String candidateDifficulty = normalizeDifficulty(candidate.question().getDifficulty());
            int target = targetDifficultyDistribution.getOrDefault(candidateDifficulty, 0);
            if (target == 0 || difficultyUsage.getOrDefault(candidateDifficulty, 0) < target) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAlternativeDifficulty(
            List<QuestionCandidate> candidates,
            Set<Long> usedQuestionIds,
            String blockedDifficulty,
            Map<String, Integer> targetDifficultyDistribution,
            Map<String, Integer> difficultyUsage
    ) {
        for (QuestionCandidate candidate : candidates) {
            if (usedQuestionIds.contains(candidate.question().getId())) {
                continue;
            }

            String candidateDifficulty = normalizeDifficulty(candidate.question().getDifficulty());
            if (blockedDifficulty.equals(candidateDifficulty)) {
                continue;
            }

            int target = targetDifficultyDistribution.getOrDefault(candidateDifficulty, 0);
            if (target == 0 || difficultyUsage.getOrDefault(candidateDifficulty, 0) < target) {
                return true;
            }
        }
        return false;
    }

    private boolean topicQuotaReached(int currentTopicUsage, int maxPerTopic) {
        return currentTopicUsage >= maxPerTopic;
    }

    private boolean difficultyQuotaReached(int currentDifficultyUsage, int difficultyTarget) {
        return difficultyTarget > 0 && currentDifficultyUsage >= difficultyTarget;
    }

    private Quiz persistAdaptiveQuiz(User user, List<Question> selectedQuestions, QuizUserInsight insight) {
        Quiz quiz = new Quiz();
        String primaryTopic = selectedQuestions.stream()
                .map(Question::getTopic)
                .filter(topic -> topic != null && !topic.isBlank())
                .findFirst()
                .orElse("General");
        String focusTopic = primaryTopic;

        if (insight != null && !insight.getWeakestTopics().isEmpty()) {
            focusTopic = insight.getWeakestTopics().get(0);
        }

        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        quiz.setTitre("Adaptive Quiz - " + focusTopic + " - " + user.getFullName() + " - " + createdAt);
        quiz.setKeyword(buildUniqueKeyword(focusTopic, user, createdAt));

        for (Question source : selectedQuestions) {
            quiz.addQuestion(quizService.copyQuestionForQuiz(source, quiz));
        }

        return quizService.createQuiz(quiz);
    }

    private String buildUniqueKeyword(String focusTopic, User user, Instant createdAt) {
        String sanitizedTopic = focusTopic == null || focusTopic.isBlank()
                ? "general"
                : focusTopic.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (sanitizedTopic.isBlank()) {
            sanitizedTopic = "general";
        }

        String userToken = user.getId() != null ? String.valueOf(user.getId()) : "user";
        String timeToken = String.valueOf(createdAt.getEpochSecond());
        return "adaptive-" + sanitizedTopic + "-" + userToken + "-" + timeToken;
    }

    private double computeAccuracyPenalty(QuizQuestionPerformance performance) {
        return performance == null ? 18.0 : (100.0 - performance.getAccuracyRate()) * 0.65;
    }

    private double computeRetryBonus(QuizQuestionPerformance performance) {
        return performance == null ? 22.0 : Math.min(performance.getIncorrectAnswers() * 9.0, 30.0);
    }

    private double computeNoveltyBonus(QuizQuestionPerformance performance) {
        return performance == null ? 16.0 : Math.max(0.0, 12.0 - (performance.getAttempts() * 2.0));
    }

    private double computeFreshnessPenalty(QuizQuestionPerformance performance) {
        if (performance == null || performance.getLastAnsweredAt() == null) {
            return 0.0;
        }

        long daysSinceLastSeen = ChronoUnit.DAYS.between(performance.getLastAnsweredAt(), Instant.now());
        if (daysSinceLastSeen < 2) {
            return 14.0;
        }
        if (daysSinceLastSeen < 7) {
            return 6.0;
        }
        return 0.0;
    }

    private double computeDifficultyFit(String difficulty, Map<String, Integer> targetDifficultyDistribution) {
        return switch (difficulty) {
            case "Hard" -> targetDifficultyDistribution.getOrDefault("Hard", 0) > 0 ? 12.0 : -5.0;
            case "Easy" -> targetDifficultyDistribution.getOrDefault("Easy", 0) > 0 ? 10.0 : -4.0;
            default -> targetDifficultyDistribution.getOrDefault("Medium", 0) > 0 ? 14.0 : 0.0;
        };
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return "general";
        }
        return topic.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "Medium";
        }

        String normalized = difficulty.trim().toLowerCase(Locale.ROOT);
        if ("easy".equals(normalized)) {
            return "Easy";
        }
        if ("hard".equals(normalized)) {
            return "Hard";
        }
        return "Medium";
    }

    private record QuestionCandidate(Question question, double score) {
    }
}
