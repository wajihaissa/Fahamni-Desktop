package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class ActivityLog {

    private int id;
    private String adminName;
    private String action;       // "Approuve" ou "Refuse"
    private String articleTitle;
    private String articleAuthor;
    private LocalDateTime createdAt;

    public ActivityLog() {}

    public ActivityLog(String adminName, String action, String articleTitle, String articleAuthor) {
        this.adminName     = adminName;
        this.action        = action;
        this.articleTitle  = articleTitle;
        this.articleAuthor = articleAuthor;
        this.createdAt     = LocalDateTime.now();
    }

    public int getId()                    { return id; }
    public void setId(int id)             { this.id = id; }

    public String getAdminName()                       { return adminName; }
    public void setAdminName(String adminName)         { this.adminName = adminName; }

    public String getAction()                          { return action; }
    public void setAction(String action)               { this.action = action; }

    public String getArticleTitle()                    { return articleTitle; }
    public void setArticleTitle(String articleTitle)   { this.articleTitle = articleTitle; }

    public String getArticleAuthor()                   { return articleAuthor; }
    public void setArticleAuthor(String a)             { this.articleAuthor = a; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }
}
