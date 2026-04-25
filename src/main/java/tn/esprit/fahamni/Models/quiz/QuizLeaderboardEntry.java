package tn.esprit.fahamni.Models.quiz;

public class QuizLeaderboardEntry {
    private int rank;
    private Integer userId;
    private String userName;
    private String userEmail;
    private int attempts;
    private double averagePercentage;
    private double bestPercentage;
    private double passRate;
    private double consistencyScore;
    private double improvementScore;
    private double weightedScore;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

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

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public double getAveragePercentage() {
        return averagePercentage;
    }

    public void setAveragePercentage(double averagePercentage) {
        this.averagePercentage = averagePercentage;
    }

    public double getBestPercentage() {
        return bestPercentage;
    }

    public void setBestPercentage(double bestPercentage) {
        this.bestPercentage = bestPercentage;
    }

    public double getPassRate() {
        return passRate;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public double getConsistencyScore() {
        return consistencyScore;
    }

    public void setConsistencyScore(double consistencyScore) {
        this.consistencyScore = consistencyScore;
    }

    public double getImprovementScore() {
        return improvementScore;
    }

    public void setImprovementScore(double improvementScore) {
        this.improvementScore = improvementScore;
    }

    public double getWeightedScore() {
        return weightedScore;
    }

    public void setWeightedScore(double weightedScore) {
        this.weightedScore = weightedScore;
    }
}
