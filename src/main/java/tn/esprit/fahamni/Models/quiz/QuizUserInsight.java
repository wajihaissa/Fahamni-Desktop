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

    public List<String> getWeakestTopics() {
        return weakestTopics;
    }

    public List<String> getStrongestTopics() {
        return strongestTopics;
    }
}
