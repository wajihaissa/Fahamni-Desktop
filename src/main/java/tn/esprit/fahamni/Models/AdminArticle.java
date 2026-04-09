package tn.esprit.fahamni.Models;

public class AdminArticle {

    private String title;
    private String category;
    private String author;
    private String status;

    public AdminArticle(String title, String category, String author, String status) {
        this.title = title;
        this.category = category;
        this.author = author;
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

