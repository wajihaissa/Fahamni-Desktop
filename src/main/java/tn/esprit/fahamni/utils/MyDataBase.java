package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/fahamni";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "";

    private Connection cnx;

    private MyDataBase() {
        connect();
    }

    private void connect() {
        String url = resolveValue("FAHAMNI_DB_URL", DEFAULT_URL);
        String username = resolveValue("FAHAMNI_DB_USERNAME", DEFAULT_USERNAME);
        List<String> candidatePasswords = buildCandidatePasswords();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            cnx = null;
            System.out.println("MySQL driver not found: " + e.getMessage());
            return;
        }

        for (String password : candidatePasswords) {
            try {
                cnx = DriverManager.getConnection(url, username, password);
                System.out.println("Connected to database successfully.");
                return;
            } catch (SQLException e) {
                cnx = null;
            }
        }

        System.out.println("Unable to connect to database using configured credentials.");
    }

    public static MyDataBase getInstance() {
        if (instance == null) {
            instance = new MyDataBase();
        }
        return instance;
    }

    public Connection getCnx() {
        try {
            if (cnx == null || cnx.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            cnx = null;
            connect();
        }
        return cnx;
    }

    public Connection getConnection() {
        return getCnx();
    }

    private static String resolveValue(String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        return envValue == null || envValue.isBlank() ? defaultValue : envValue.trim();
    }

    private static List<String> buildCandidatePasswords() {
        String configuredPassword = System.getenv("FAHAMNI_DB_PASSWORD");
        if (configuredPassword != null) {
            return List.of(configuredPassword);
        }

        return Arrays.asList(DEFAULT_PASSWORD, "96703053");
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
