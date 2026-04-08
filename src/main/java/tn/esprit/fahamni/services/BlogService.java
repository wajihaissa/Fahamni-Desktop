package tn.esprit.fahamni.services;

import java.util.List;

public class BlogService {

    public List<String> getCategories() {
        return List.of(
            "All Categories",
            "Mathematics",
            "Physics",
            "Chemistry",
            "Biology",
            "English",
            "French",
            "Study Tips"
        );
    }

    public String getDefaultCategory() {
        return "All Categories";
    }
}

