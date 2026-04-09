package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.StudyPlanRequest;

public class PlannerService {

    public String getDefaultSubject() {
        return "Mathematics";
    }

    public String getDefaultDifficulty() {
        return "Medium";
    }

    public String generatePlan(StudyPlanRequest request) {
        return "Generating plan for: " + request.getSubject()
            + " on " + request.getExamDate()
            + " difficulty: " + request.getDifficulty();
    }
}

