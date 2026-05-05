package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Resource;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ResourceService implements IServices<Resource> {

    private final Connection cnx;

    public ResourceService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Resource entity) throws SQLException {
        ajouter(entity);
    }

    @Override
    public List<Resource> getAll() throws SQLException {
        return afficherList();
    }

    @Override
    public void update(Resource entity) throws SQLException {
        modifier(entity);
    }

    @Override
    public void delete(Resource entity) throws SQLException {
        if (entity != null) {
            supprimer(entity.getId());
        }
    }
    public void ajouter(Resource resource) {
        String req = "INSERT INTO resource (titre, type, filepath, link, section_id) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, resource.getTitre());
            pst.setString(2, resource.getType());
            pst.setString(3, resource.getFilepath());
            pst.setString(4, resource.getLink());
            pst.setInt(5, resource.getSectionId());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    resource.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void modifier(Resource resource) {
        String req = "UPDATE resource SET titre = ?, type = ?, filepath = ?, link = ?, section_id = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, resource.getTitre());
            pst.setString(2, resource.getType());
            pst.setString(3, resource.getFilepath());
            pst.setString(4, resource.getLink());
            pst.setInt(5, resource.getSectionId());
            pst.setInt(6, resource.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void supprimer(int id) {
        String req = "DELETE FROM resource WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<Resource> afficherList() {
        List<Resource> resources = new ArrayList<>();
        String req = "SELECT * FROM resource";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                Resource resource = new Resource(
                    rs.getInt("id"),
                    rs.getString("titre"),
                    rs.getString("type"),
                    rs.getString("filepath"),
                    rs.getString("link"),
                    rs.getInt("section_id")
                );
                resources.add(resource);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return resources;
    }

    public Resource getOneById(int id) {
        String req = "SELECT * FROM resource WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new Resource(
                        rs.getInt("id"),
                        rs.getString("titre"),
                        rs.getString("type"),
                        rs.getString("filepath"),
                        rs.getString("link"),
                        rs.getInt("section_id")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
