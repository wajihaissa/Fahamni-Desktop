package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    // Essayer port 3307 (XAMPP) puis 3306 (MySQL standard)
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
        for (String url : URLS) {
            try {
                cnx = DriverManager.getConnection(url, USERNAME, PASSWORD);
                System.out.println("Connecté à MySQL avec: " + url);
                return;
            } catch (SQLException e) {
                System.err.println("Tentative échouée (" + url + "): " + e.getMessage());
            }
        }
        System.err.println("ERREUR: Impossible de se connecter à MySQL. Vérifiez que MySQL est démarré sur le port 3306.");
    }

    public static MyDataBase getInstance() {
        if (instance == null) {
            instance = new MyDataBase();
        }
        return instance;
    }

    public Connection getCnx() {
        // Vérifier si la connexion est encore valide, reconnecter si nécessaire
        try {
            if (cnx == null || cnx.isClosed() || !cnx.isValid(2)) {
                System.err.println("Connexion perdue, tentative de reconnexion...");
                for (String url : URLS) {
                    try {
                        cnx = DriverManager.getConnection(url, USERNAME, PASSWORD);
                        System.out.println("Reconnexion réussie.");
                        return cnx;
                    } catch (SQLException e) { /* essayer suivant */ }
                }
            }
        } catch (SQLException e) { /* connexion invalide, essayer de reconnecter */ }
        return cnx;
    }

    /** Crée une connexion FRAÎCHE et indépendante (à fermer par l'appelant) */
    public Connection getFreshConnection() {
        for (String url : URLS) {
            try {
                Connection fresh = DriverManager.getConnection(url, USERNAME, PASSWORD);
                fresh.setAutoCommit(true);
                System.out.println("Connexion fraîche créée: " + url);
                return fresh;
            } catch (SQLException e) {
                System.err.println("getFreshConnection échoué (" + url + "): " + e.getMessage());
            }
        }
        return null;
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
