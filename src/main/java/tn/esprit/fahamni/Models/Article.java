package tn.esprit.fahamni.Models;

public class Article {

    private String title;
    private String category;
    private String author;
    private String publicationDate;
    private int views;
    private int likes;
    private String excerpt;

    public Article(String title, String category, String author, String publicationDate,
                   int views, int likes, String excerpt) {
        this.title = title;
        this.category = category;
        this.author = author;
        this.publicationDate = publicationDate;
        this.views = views;
        this.likes = likes;
        this.excerpt = excerpt;
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

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    public int getViews() {
        return views;
    }

    public void setViews(int views) {
        this.views = views;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }
}

