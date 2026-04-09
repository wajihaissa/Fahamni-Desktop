package tn.esprit.fahamni.services;

import java.util.List;

public class SeanceService {

    public List<String> getSubjects() {
        return List.of(
            "All Subjects",
            "Mathematics",
            "Physics",
            "Chemistry",
            "English",
            "French",
            "Biology"
        );
    }

    public String getDefaultSubject() {
        return "All Subjects";
    }

    public String buildSearchSummary(String searchText, String selectedSubject) {
        return "Searching for: " + searchText + " in " + selectedSubject;
    }
}

