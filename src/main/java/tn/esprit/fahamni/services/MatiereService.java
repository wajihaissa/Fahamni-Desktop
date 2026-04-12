package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Matiere;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class MatiereService implements IServices<Matiere> {

    private final Connection cnx;

    public MatiereService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public void ajouter(Matiere matiere) {
        String req = "INSERT INTO matiere (titre, description, structure, created_at, cover_image) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, matiere.getTitre());
            pst.setString(2, matiere.getDescription());
            pst.setString(3, matiere.getStructure());

            if (matiere.getCreatedAt() != null) {
                pst.setTimestamp(4, matiere.getCreatedAt());
            } else {
                pst.setNull(4, Types.TIMESTAMP);
            }

            pst.setString(5, matiere.getCoverImage());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    matiere.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void modifier(Matiere matiere) {
        String req = "UPDATE matiere SET titre = ?, description = ?, structure = ?, created_at = ?, cover_image = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, matiere.getTitre());
            pst.setString(2, matiere.getDescription());
            pst.setString(3, matiere.getStructure());

            if (matiere.getCreatedAt() != null) {
                pst.setTimestamp(4, matiere.getCreatedAt());
            } else {
                pst.setNull(4, Types.TIMESTAMP);
            }

            pst.setString(5, matiere.getCoverImage());
            pst.setInt(6, matiere.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void supprimer(int id) {
        String req = "DELETE FROM matiere WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<Matiere> afficherList() {
        List<Matiere> matieres = new ArrayList<>();
        String req = "SELECT * FROM matiere";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                Matiere matiere = new Matiere(
                    rs.getInt("id"),
                    rs.getString("titre"),
                    rs.getString("description"),
                    rs.getString("structure"),
                    rs.getTimestamp("created_at"),
                    rs.getString("cover_image")
                );
                matieres.add(matiere);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return matieres;
    }

    public Matiere getOneById(int id) {
        String req = "SELECT * FROM matiere WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new Matiere(
                        rs.getInt("id"),
                        rs.getString("titre"),
                        rs.getString("description"),
                        rs.getString("structure"),
                        rs.getTimestamp("created_at"),
                        rs.getString("cover_image")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
