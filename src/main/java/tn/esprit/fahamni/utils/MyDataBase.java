package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String USERNAME = "root";
    private static final String PASSWORD = "";
    private static final String[] URLS = {
        "jdbc:mysql://127.0.0.1:3307/fahamni?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        "jdbc:mysql://localhost:3307/fahamni?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        "jdbc:mysql://127.0.0.1:3307/fahamni?useSSL=false",
        "jdbc:mysql://127.0.0.1:3306/fahamni?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        "jdbc:mysql://127.0.0.1:3306/fahamni?useSSL=false",
        "jdbc:mysql://127.0.0.1:3306/fahamni"
    };

    private Connection cnx;

    private MyDataBase() {
        connect();
    }

    public static MyDataBase getInstance() {
        if (instance == null) {
            instance = new MyDataBase();
        }
        return instance;
    }

    public Connection getCnx() {
        try {
            if (cnx == null || cnx.isClosed() || !cnx.isValid(2)) {
                System.err.println("Connexion perdue, tentative de reconnexion...");
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return cnx;
    }

    public Connection getFreshConnection() {
        for (String url : URLS) {
            try {
                Connection fresh = DriverManager.getConnection(url, USERNAME, PASSWORD);
                fresh.setAutoCommit(true);
                System.out.println("Connexion fraiche creee: " + url);
                return fresh;
            } catch (SQLException e) {
                System.err.println("getFreshConnection echoue (" + url + "): " + e.getMessage());
            }
        }
        return null;
    }

    private void connect() {
        cnx = null;
        for (String url : URLS) {
            try {
                cnx = DriverManager.getConnection(url, USERNAME, PASSWORD);
                System.out.println("Connecte a MySQL avec: " + url);
                return;
            } catch (SQLException e) {
                System.err.println("Tentative echouee (" + url + "): " + e.getMessage());
            }
        }
        System.err.println("ERREUR: Impossible de se connecter a MySQL. Verifiez que MySQL est demarre sur le port 3306 ou 3307.");
    }

    public static void main(String[] args) {
        MyDataBase db = MyDataBase.getInstance();
        if (db.getCnx() != null) {
            System.out.println("Connexion reussie");
        } else {
            System.out.println("Connexion echouee");
        }
    }
}
