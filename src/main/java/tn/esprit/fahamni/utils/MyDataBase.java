package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String DEFAULT_DB_HOST = "127.0.0.1";
    private static final int DEFAULT_DB_PORT = 3306;
    private static final String DEFAULT_DB_NAME = "fahamni";
    private static final String DEFAULT_DB_USER = "root";

    private Connection cnx;

    private MyDataBase() {
        connect();
    }

    private void connect() {
        String url = getUrl();
        String username = getUsername();
        String password = getPassword();
        try {
            cnx = DriverManager.getConnection(url, username, password);
            System.out.println("Connected to MySQL: " + sanitizeJdbcUrl(url));
        } catch (SQLException e) {
            cnx = null;
            System.out.println(
                "Database connection failed for " + sanitizeJdbcUrl(url)
                    + " (SQLState=" + e.getSQLState()
                    + ", ErrorCode=" + e.getErrorCode() + "): "
                    + e.getMessage()
            );
        }
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

    private static String getUrl() {
        String configuredUrl = LocalConfig.get("DB_URL");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }

        String host = getString("DB_HOST", DEFAULT_DB_HOST);
        int port = getInt("DB_PORT", DEFAULT_DB_PORT);
        String dbName = getString("DB_NAME", DEFAULT_DB_NAME);

        return "jdbc:mysql://" + host + ":" + port + "/" + dbName
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            + "&connectTimeout=5000&socketTimeout=10000";
    }

    private static String getUsername() {
        return getString("DB_USER", DEFAULT_DB_USER);
    }

    private static String getPassword() {
        String configuredPassword = LocalConfig.get("DB_PASSWORD");
        return configuredPassword == null ? "" : configuredPassword;
    }

    private static String getString(String key, String fallback) {
        String value = LocalConfig.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int getInt(String key, int fallback) {
        String value = LocalConfig.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String sanitizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "<empty>";
        }
        return url.replaceAll("(?i)(password=)[^&]+", "$1***");
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
