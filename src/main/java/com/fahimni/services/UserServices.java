package com.fahimni.services;

import com.fahimni.interfaces.UserInt;
import com.fahimni.models.User;
import com.fahimni.utils.bd;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserServices implements UserInt<User> {
    private Connection cnx;

    public UserServices() {
        cnx = bd.getInstance().getConnection();
    }

    @Override
    public void add(User user) {
        String qry = "INSERT INTO `user` (`email`, `password`, `full_name`, `Status`, `created_at`) VALUES (?, ?, ?, ?, NOW())";
        try {
            PreparedStatement pst = cnx.prepareStatement(qry);
            pst.setString(1, user.getEmail());
            pst.setString(2, user.getPassword());
            pst.setString(3, user.getFullName());
            pst.setBoolean(4, user.isStatus());
            pst.executeUpdate();
            System.out.println("User added successfully!");
        } catch (SQLException e) {
            System.out.println("Error adding user: " + e.getMessage());
        }
    }

    @Override
    public void delete(User user) {
        String qry = "DELETE FROM `user` WHERE `id` = ?";
        try {
            PreparedStatement pst = cnx.prepareStatement(qry);
            pst.setInt(1, user.getId());
            pst.executeUpdate();
            System.out.println("User deleted successfully!");
        } catch (SQLException e) {
            System.out.println("Error deleting user: " + e.getMessage());
        }
    }

    @Override
    public void update(User user) {
        String qry = "UPDATE `user` SET `email` = ?, `password` = ?, `full_name` = ?, `Status` = ? WHERE `id` = ?";
        try {
            PreparedStatement pst = cnx.prepareStatement(qry);
            pst.setString(1, user.getEmail());
            pst.setString(2, user.getPassword());
            pst.setString(3, user.getFullName());
            pst.setBoolean(4, user.isStatus());
            pst.setInt(5, user.getId());
            pst.executeUpdate();
            System.out.println("User updated successfully!");
        } catch (SQLException e) {
            System.out.println("Error updating user: " + e.getMessage());
        }
    }

    @Override
    public List<User> getAll() {
        List<User> users = new ArrayList<>();
        String qry = "SELECT * FROM `user`";
        try {
            Statement st = cnx.createStatement();
            ResultSet rs = st.executeQuery(qry);
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setEmail(rs.getString("email"));
                user.setPassword(rs.getString("password"));
                user.setFullName(rs.getString("full_name"));
                user.setStatus(rs.getBoolean("Status"));
                user.setCreatedAt(rs.getString("created_at"));
                users.add(user);
            }
        } catch (SQLException e) {
            System.out.println("Error getting users: " + e.getMessage());
        }
        return users;
    }
}