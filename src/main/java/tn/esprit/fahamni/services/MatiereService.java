package tn.esprit.fahamni.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.interfaces.IService;
import tn.esprit.fahamni.utils.MyDataBase;

public class MatiereService implements IService<Matiere> {

    private final Connection cnx;

    public MatiereService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Matiere m) {
        String req = "INSERT INTO matiere (titre, description, structure, created_at, cover_image) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, m.getTitre());
            pst.setString(2, m.getDescription());
            pst.setString(3, m.getStructure());

            if (m.getCreatedAt() != null) {
                pst.setTimestamp(4, Timestamp.valueOf(m.getCreatedAt()));
            } else {
                pst.setNull(4, Types.TIMESTAMP);
            }

            pst.setString(5, m.getCoverImage());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    m.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during add: " + e.getMessage());
            throw new RuntimeException("Erreur de base de données lors de l'insertion : " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Matiere m) {
        String req = "UPDATE matiere SET titre = ?, description = ?, structure = ?, cover_image = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, m.getTitre());
            pst.setString(2, m.getDescription());
            pst.setString(3, m.getStructure());
            pst.setString(4, m.getCoverImage());
            pst.setInt(5, m.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Database error during update: " + e.getMessage());
            throw new RuntimeException("Erreur de base de données lors de la mise à jour : " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Matiere m) {
        String req = "DELETE FROM matiere WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, m.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Database error during delete: " + e.getMessage());
            throw new RuntimeException("Erreur de base de données lors de la suppression : " + e.getMessage(), e);
        }
    }

    @Override
    public List<Matiere> findAll() {
        List<Matiere> matieres = new ArrayList<>();
        String req = "SELECT * FROM matiere";

        try (Statement st = cnx.createStatement();
             ResultSet res = st.executeQuery(req)) {

            while (res.next()) {
                Timestamp createdAtTimestamp = res.getTimestamp("created_at");
                LocalDateTime createdAt = createdAtTimestamp != null ? createdAtTimestamp.toLocalDateTime() : null;

                Matiere matiere = new Matiere(
                    res.getInt("id"),
                    res.getString("titre"),
                    res.getString("description"),
                    res.getString("structure"),
                    createdAt,
                    res.getString("cover_image")
                );
                matieres.add(matiere);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return matieres;
    }

    @Override
    public Matiere findById(int id) {
        String req = "SELECT * FROM matiere WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet res = pst.executeQuery()) {
                if (res.next()) {
                    Timestamp createdAtTimestamp = res.getTimestamp("created_at");
                    LocalDateTime createdAt = createdAtTimestamp != null ? createdAtTimestamp.toLocalDateTime() : null;

                    return new Matiere(
                        res.getInt("id"),
                        res.getString("titre"),
                        res.getString("description"),
                        res.getString("structure"),
                        createdAt,
                        res.getString("cover_image")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
