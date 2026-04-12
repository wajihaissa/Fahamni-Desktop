package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String URL = "jdbc:mysql://127.0.0.1:3306/fahamni";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private Connection cnx;

    private MyDataBase() {
        try {
            cnx = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("Connected ...");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static MyDataBase getInstance() {
        if (instance == null) {
            instance = new MyDataBase();
        }
        return instance;
    }

    public Connection getCnx() {
        return cnx;
    }
    public static void main(String[] args) {
    MyDataBase db = MyDataBase.getInstance();
    if (db.getCnx() != null) {
        System.out.println("Connexion réussie ✅");
    } else {
        System.out.println("Connexion échouée ❌");
    }
}
}
