package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Chapter;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ChapterService implements IServices<Chapter> {

    private final Connection cnx;

    public ChapterService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Chapter entity) throws SQLException {
        ajouter(entity);
    }

    @Override
    public List<Chapter> getAll() throws SQLException {
        return afficherList();
    }

    @Override
    public void update(Chapter entity) throws SQLException {
        modifier(entity);
    }

    @Override
    public void delete(Chapter entity) throws SQLException {
        if (entity != null) {
            supprimer(entity.getId());
        }
    }
    public void ajouter(Chapter chapter) {
        String req = "INSERT INTO chapter (titre, matiere_id) VALUES (?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, chapter.getTitre());
            pst.setInt(2, chapter.getMatiereId());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    chapter.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void modifier(Chapter chapter) {
        String req = "UPDATE chapter SET titre = ?, matiere_id = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, chapter.getTitre());
            pst.setInt(2, chapter.getMatiereId());
            pst.setInt(3, chapter.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void supprimer(int id) {
        String req = "DELETE FROM chapter WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<Chapter> afficherList() {
        List<Chapter> chapters = new ArrayList<>();
        String req = "SELECT * FROM chapter";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                Chapter chapter = new Chapter(
                    rs.getInt("id"),
                    rs.getString("titre"),
                    rs.getInt("matiere_id")
                );
                chapters.add(chapter);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return chapters;
    }

    public Chapter getOneById(int id) {
        String req = "SELECT * FROM chapter WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new Chapter(
                        rs.getInt("id"),
                        rs.getString("titre"),
                        rs.getInt("matiere_id")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
