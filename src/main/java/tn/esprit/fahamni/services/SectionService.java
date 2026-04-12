package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Section;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SectionService implements IServices<Section> {

    private final Connection cnx;

    public SectionService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public void ajouter(Section section) {
        String req = "INSERT INTO section (titre, chapter_id) VALUES (?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, section.getTitre());
            pst.setInt(2, section.getChapterId());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    section.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void modifier(Section section) {
        String req = "UPDATE section SET titre = ?, chapter_id = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, section.getTitre());
            pst.setInt(2, section.getChapterId());
            pst.setInt(3, section.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void supprimer(int id) {
        String req = "DELETE FROM section WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<Section> afficherList() {
        List<Section> sections = new ArrayList<>();
        String req = "SELECT * FROM section";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                Section section = new Section(
                    rs.getInt("id"),
                    rs.getString("titre"),
                    rs.getInt("chapter_id")
                );
                sections.add(section);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return sections;
    }

    public Section getOneById(int id) {
        String req = "SELECT * FROM section WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new Section(
                        rs.getInt("id"),
                        rs.getString("titre"),
                        rs.getInt("chapter_id")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
