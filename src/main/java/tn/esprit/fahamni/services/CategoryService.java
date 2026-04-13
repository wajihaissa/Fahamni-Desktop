package tn.esprit.fahamni.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

public class CategoryService implements IServices<Category> {

    private final Connection cnx;

    public CategoryService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public void ajouter(Category category) {
        String req = "INSERT INTO category (name, slug) VALUES (?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, category.getName());
            pst.setString(2, category.getSlug());
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
        String req = "UPDATE category SET name = ?, slug = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, category.getName());
            pst.setString(2, category.getSlug());
            pst.setInt(3, category.getId());
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
                    rs.getString("name"),
                    rs.getString("slug")
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
                        rs.getString("name"),
                        rs.getString("slug")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    public List<Category> findByMatiereId(int matiereId) {
        List<Category> categories = new ArrayList<>();
        
        // Check if connection is available
        if (cnx == null) {
            System.err.println("Database connection is null - cannot fetch categories");
            return categories;
        }
        
        String req = "SELECT c.id, c.name, c.slug "
            + "FROM category c "
            + "INNER JOIN matiere_category mc ON mc.category_id = c.id "
            + "WHERE mc.matiere_id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, matiereId);

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    categories.add(new Category(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("slug")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return categories;
    }

    public void replaceMatiereCategories(int matiereId, List<Integer> categoryIds) {
        String deleteReq = "DELETE FROM matiere_category WHERE matiere_id = ?";
        String insertReq = "INSERT INTO matiere_category (matiere_id, category_id) VALUES (?, ?)";
        boolean initialAutoCommit = true;

        try {
            initialAutoCommit = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return;
        }

        try (PreparedStatement deletePst = cnx.prepareStatement(deleteReq);
             PreparedStatement insertPst = cnx.prepareStatement(insertReq)) {

            deletePst.setInt(1, matiereId);
            deletePst.executeUpdate();

            Set<Integer> uniqueIds = new LinkedHashSet<>();
            if (categoryIds != null) {
                for (Integer categoryId : categoryIds) {
                    if (categoryId != null && categoryId > 0) {
                        uniqueIds.add(categoryId);
                    }
                }
            }

            for (Integer categoryId : uniqueIds) {
                insertPst.setInt(1, matiereId);
                insertPst.setInt(2, categoryId);
                insertPst.addBatch();
            }
            insertPst.executeBatch();

            cnx.commit();
        } catch (SQLException e) {
            try {
                cnx.rollback();
            } catch (SQLException rollbackException) {
                System.out.println(rollbackException.getMessage());
            }
            System.out.println(e.getMessage());
        } finally {
            try {
                cnx.setAutoCommit(initialAutoCommit);
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
