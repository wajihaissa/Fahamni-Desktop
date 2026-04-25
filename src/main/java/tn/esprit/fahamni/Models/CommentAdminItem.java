package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class CommentAdminItem {
    private int id;
    private String commentaire;
    private String auteur;
    private String articleTitre;
    private int blogId;
    private LocalDateTime createdAt;
    private boolean deletedByAdmin;
    private boolean flagged;

    public CommentAdminItem(int id, String commentaire, String auteur,
                             String articleTitre, int blogId,
                             LocalDateTime createdAt,
                             boolean deletedByAdmin, boolean flagged) {
        this.id = id;
        this.commentaire = commentaire;
        this.auteur = auteur;
        this.articleTitre = articleTitre;
        this.blogId = blogId;
        this.createdAt = createdAt;
        this.deletedByAdmin = deletedByAdmin;
        this.flagged = flagged;
    }

    public int getId()                { return id; }
    public String getCommentaire()    { return commentaire; }
    public String getAuteur()         { return auteur; }
    public String getArticleTitre()   { return articleTitre; }
    public int getBlogId()            { return blogId; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public boolean isDeletedByAdmin() { return deletedByAdmin; }
    public boolean isFlagged()        { return flagged; }
    public void setDeletedByAdmin(boolean v) { this.deletedByAdmin = v; }
    public void setFlagged(boolean v)        { this.flagged = v; }
}
