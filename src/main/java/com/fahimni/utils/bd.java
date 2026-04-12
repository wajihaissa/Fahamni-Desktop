package com.fahimni.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class bd {
    private static bd instance;
    private final String URL = "jdbc:mysql://127.0.0.1:3306/fahamni";
    private final String USER = "root";
    private final String PASS = "";
    private Connection cnx;

    private bd() {
        try {
            // Load MySQL driver explicitly
            Class.forName("com.mysql.cj.jdbc.Driver");

            cnx = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("Connected to database successfully!");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    public static bd getInstance() {
        if (instance == null) {
            instance = new bd();
        }
        return instance;
    }

    public Connection getConnection() {
        return cnx;
    }
}