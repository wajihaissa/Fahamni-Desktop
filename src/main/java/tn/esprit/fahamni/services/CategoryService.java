package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CategoryService implements IServices<Category> {

    private final Connection cnx;

    public CategoryService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public void ajouter(Category category) {
        String req = "INSERT INTO category (nom, description, image) VALUES (?, ?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, category.getNom());
            pst.setString(2, category.getDescription());
            pst.setString(3, category.getImage());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    category.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void modifier(Category category) {
        String req = "UPDATE category SET nom = ?, description = ?, image = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, category.getNom());
            pst.setString(2, category.getDescription());
            pst.setString(3, category.getImage());
            pst.setInt(4, category.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void supprimer(int id) {
        String req = "DELETE FROM category WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<Category> afficherList() {
        List<Category> categories = new ArrayList<>();
        String req = "SELECT * FROM category";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                Category category = new Category(
                    rs.getInt("id"),
                    rs.getString("nom"),
                    rs.getString("description"),
                    rs.getString("image")
                );
                categories.add(category);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return categories;
    }

    public Category getOneById(int id) {
        String req = "SELECT * FROM category WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new Category(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("description"),
                        rs.getString("image")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
