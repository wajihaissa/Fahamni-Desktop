package tn.esprit.fahamni.Models.quiz;

import java.util.ArrayList;
import java.util.List;

public class QuizUserInsight {
    private Integer userId;
    private String userName;
    private int totalAttempts;
    private double averagePercentage;
    private double passRate;
    private String recommendedDifficulty;
    private double recentAveragePercentage;
    private double previousAveragePercentage;
    private double improvementDelta;
    private int currentStreak;
    private final List<String> weakestTopics = new ArrayList<>();
    private final List<String> strongestTopics = new ArrayList<>();

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(int totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public double getAveragePercentage() {
        return averagePercentage;
    }

    public void setAveragePercentage(double averagePercentage) {
        this.averagePercentage = averagePercentage;
    }

    public double getPassRate() {
        return passRate;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public String getRecommendedDifficulty() {
        return recommendedDifficulty;
    }

    public void setRecommendedDifficulty(String recommendedDifficulty) {
        this.recommendedDifficulty = recommendedDifficulty;
    }

    public double getRecentAveragePercentage() {
        return recentAveragePercentage;
    }

    public void setRecentAveragePercentage(double recentAveragePercentage) {
        this.recentAveragePercentage = recentAveragePercentage;
    }

    public double getPreviousAveragePercentage() {
        return previousAveragePercentage;
    }

    public void setPreviousAveragePercentage(double previousAveragePercentage) {
        this.previousAveragePercentage = previousAveragePercentage;
    }

    public double getImprovementDelta() {
        return improvementDelta;
    }

    public void setImprovementDelta(double improvementDelta) {
        this.improvementDelta = improvementDelta;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public boolean hasAttempts() {
        return totalAttempts > 0;
    }

    public boolean hasWeakTopics() {
        return !weakestTopics.isEmpty();
    }

    public boolean hasStrongTopics() {
        return !strongestTopics.isEmpty();
    }

    public List<String> getWeakestTopics() {
        return weakestTopics;
    }

    public List<String> getStrongestTopics() {
        return strongestTopics;
    }
}
