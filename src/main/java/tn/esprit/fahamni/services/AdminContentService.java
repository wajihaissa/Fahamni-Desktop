package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AdminArticle;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

public class AdminContentService {

    private final ObservableList<AdminArticle> articles = FXCollections.observableArrayList(
        new AdminArticle("Mastering Algebra", "Mathematics", "Ahmed Ben Ali", "Published"),
        new AdminArticle("Study Plan for Bac", "Study Tips", "Editorial Team", "Draft"),
        new AdminArticle("Mechanics Revision Sheet", "Physics", "Sarah Mansour", "Published")
    );

    public ObservableList<AdminArticle> getArticles() {
        return articles;
    }

    public List<String> getAvailableCategories() {
        return List.of("Mathematics", "Physics", "Chemistry", "Study Tips");
    }

    public List<String> getAvailableStatuses() {
        return List.of("Draft", "Published", "Archived");
    }

    public OperationResult createDraft(String title, String category, String author) {
        if (isBlank(title) || isBlank(author)) {
            return OperationResult.failure("Renseignez le titre et l'auteur.");
        }

        articles.add(new AdminArticle(title.trim(), category, author.trim(), "Draft"));
        return OperationResult.success("Brouillon ajoute a la bibliotheque.");
    }

    public OperationResult updateArticle(AdminArticle article, String title, String category, String author, String status) {
        if (article == null) {
            return OperationResult.failure("Selectionnez un contenu a publier.");
        }

        article.setTitle(title.trim());
        article.setAuthor(author.trim());
        article.setCategory(category);
        article.setStatus(status);
        return OperationResult.success("Contenu mis a jour.");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

