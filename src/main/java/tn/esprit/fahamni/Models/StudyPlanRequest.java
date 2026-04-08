package tn.esprit.fahamni.Models;

import java.time.LocalDate;

public class StudyPlanRequest {

    private final LocalDate examDate;
    private final String subject;
    private final String difficulty;

    public StudyPlanRequest(LocalDate examDate, String subject, String difficulty) {
        this.examDate = examDate;
        this.subject = subject;
        this.difficulty = difficulty;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public String getSubject() {
        return subject;
    }

    public String getDifficulty() {
        return difficulty;
    }
}

