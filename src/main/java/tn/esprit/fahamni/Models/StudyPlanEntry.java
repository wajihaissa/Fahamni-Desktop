package tn.esprit.fahamni.Models;

public class StudyPlanEntry {

    private int dayNumber;
    private String phaseTitle;
    private String taskTitle;
    private String description;
    private double durationHours;
    private String status;

    public StudyPlanEntry(int dayNumber, String phaseTitle, String taskTitle,
                          String description, double durationHours, String status) {
        this.dayNumber = dayNumber;
        this.phaseTitle = phaseTitle;
        this.taskTitle = taskTitle;
        this.description = description;
        this.durationHours = durationHours;
        this.status = status;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }

    public String getPhaseTitle() {
        return phaseTitle;
    }

    public void setPhaseTitle(String phaseTitle) {
        this.phaseTitle = phaseTitle;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(double durationHours) {
        this.durationHours = durationHours;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

