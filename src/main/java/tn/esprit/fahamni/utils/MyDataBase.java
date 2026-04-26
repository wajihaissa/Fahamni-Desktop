package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/fahamni";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final String CREATE_SALLE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS salle (
            idSalle INT AUTO_INCREMENT PRIMARY KEY,
            nom VARCHAR(150) NOT NULL,
            capacite INT NOT NULL,
            localisation VARCHAR(255) NOT NULL,
            typeSalle VARCHAR(100) NOT NULL,
            etat VARCHAR(50) NOT NULL,
            description TEXT,
            batiment VARCHAR(150),
            etage INT,
            typeDisposition VARCHAR(100),
            accesHandicape BOOLEAN NOT NULL DEFAULT FALSE,
            statutDetaille VARCHAR(150),
            dateDerniereMaintenance DATE
        )
        """;
    private static final String CREATE_EQUIPEMENT_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS equipement (
            idEquipement INT AUTO_INCREMENT PRIMARY KEY,
            nom VARCHAR(150) NOT NULL,
            typeEquipement VARCHAR(100) NOT NULL,
            quantiteDisponible INT NOT NULL,
            etat VARCHAR(50) NOT NULL,
            description TEXT
        )
        """;
    private static final String CREATE_PLACE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS place (
            idPlace INT AUTO_INCREMENT PRIMARY KEY,
            numero INT NOT NULL,
            rang INT NOT NULL,
            colonne INT NOT NULL,
            etat VARCHAR(50) NOT NULL,
            idSalle INT NOT NULL,
            CONSTRAINT fk_place_salle
                FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
                ON DELETE CASCADE,
            CONSTRAINT uk_place_salle_numero UNIQUE (idSalle, numero),
            CONSTRAINT uk_place_salle_position UNIQUE (idSalle, rang, colonne)
        )
        """;
    private static final String CREATE_SALLE_EQUIPEMENT_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS salle_equipement (
            idSalle INT NOT NULL,
            idEquipement INT NOT NULL,
            quantite INT NOT NULL,
            PRIMARY KEY (idSalle, idEquipement),
            CONSTRAINT fk_salle_equipement_salle
                FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
                ON DELETE CASCADE,
            CONSTRAINT fk_salle_equipement_equipement
                FOREIGN KEY (idEquipement) REFERENCES equipement(idEquipement)
                ON DELETE CASCADE
        )
        """;
    private static final String CREATE_SEANCE_EQUIPEMENT_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS seance_equipement (
            seance_id INT NOT NULL,
            idEquipement INT NOT NULL,
            quantite INT NOT NULL DEFAULT 1,
            PRIMARY KEY (seance_id, idEquipement),
            CONSTRAINT fk_seance_equipement_seance
                FOREIGN KEY (seance_id) REFERENCES seance(id)
                ON DELETE CASCADE,
            CONSTRAINT fk_seance_equipement_equipement
                FOREIGN KEY (idEquipement) REFERENCES equipement(idEquipement)
                ON DELETE CASCADE
        )
        """;
    private static final String CREATE_QUIZ_ANSWER_ATTEMPT_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS quiz_answer_attempt (
            id INT AUTO_INCREMENT PRIMARY KEY,
            quiz_result_id INT NOT NULL,
            question_id INT NOT NULL,
            selected_choice_id INT NULL,
            is_correct BOOLEAN NOT NULL DEFAULT FALSE,
            answered_at DATETIME NOT NULL,
            CONSTRAINT fk_quiz_answer_attempt_result
                FOREIGN KEY (quiz_result_id) REFERENCES quiz_result(id)
                ON DELETE CASCADE,
            CONSTRAINT fk_quiz_answer_attempt_question
                FOREIGN KEY (question_id) REFERENCES question(id)
                ON DELETE CASCADE,
            CONSTRAINT fk_quiz_answer_attempt_choice
                FOREIGN KEY (selected_choice_id) REFERENCES choice(id)
                ON DELETE SET NULL
        )
        """;

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
                ensureInfrastructureSchema(cnx);
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

    private void ensureInfrastructureSchema(Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            createTableIfMissing(connection, "salle", CREATE_SALLE_TABLE_SQL);
            createTableIfMissing(connection, "equipement", CREATE_EQUIPEMENT_TABLE_SQL);
            createTableIfMissing(connection, "place", CREATE_PLACE_TABLE_SQL);
            createTableIfMissing(connection, "salle_equipement", CREATE_SALLE_EQUIPEMENT_TABLE_SQL);
            ensureSalleColumn(connection, "batiment", "VARCHAR(150) NULL AFTER description");
            ensureSalleColumn(connection, "etage", "INT NULL AFTER batiment");
            ensureSalleColumn(connection, "typeDisposition", "VARCHAR(100) NULL AFTER etage");
            ensureSalleColumn(connection, "accesHandicape", "BOOLEAN NOT NULL DEFAULT FALSE AFTER typeDisposition");
            ensureSalleColumn(connection, "statutDetaille", "VARCHAR(150) NULL AFTER accesHandicape");
            ensureSalleColumn(connection, "dateDerniereMaintenance", "DATE NULL AFTER statutDetaille");
            ensureSeanceColumn(connection, "mode_seance", "VARCHAR(30) NOT NULL DEFAULT 'en_ligne'");
            ensureSeanceColumn(connection, "salle_id", "INT NULL");
            createTableIfMissing(connection, "seance_equipement", CREATE_SEANCE_EQUIPEMENT_TABLE_SQL);
            ensureQuestionColumn(connection, "topic", "VARCHAR(190) NULL AFTER question");
            ensureQuestionColumn(connection, "difficulty", "VARCHAR(40) NOT NULL DEFAULT 'Medium' AFTER topic");
            ensureQuestionColumn(connection, "source_question_id", "BIGINT NULL AFTER difficulty");
            ensureQuestionColumn(connection, "hint", "TEXT NULL AFTER source_question_id");
            ensureQuestionColumn(connection, "explanation", "TEXT NULL AFTER hint");
            ensureQuizResultColumn(connection, "total_questions", "INT NULL AFTER score");
            ensureQuizResultColumn(connection, "percentage", "DOUBLE NULL AFTER total_questions");
            ensureQuizResultColumn(connection, "passed", "BOOLEAN NULL AFTER percentage");
            ensureQuizResultColumn(connection, "user_id", "INT NULL AFTER passed");
            ensureQuizResultColumn(connection, "user_email", "VARCHAR(190) NULL AFTER user_id");
            ensureQuizResultColumn(connection, "user_full_name", "VARCHAR(190) NULL AFTER user_email");
            createTableIfMissing(connection, "quiz_answer_attempt", CREATE_QUIZ_ANSWER_ATTEMPT_TABLE_SQL);
        } catch (SQLException exception) {
            System.out.println("Unable to align infrastructure schema automatically: " + exception.getMessage());
        }
    }

    private void createTableIfMissing(Connection connection, String tableName, String createSql) throws SQLException {
        if (tableExists(connection, tableName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
        }
    }

    private void ensureSalleColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "salle", columnName, columnDefinition);
    }

    private void ensureSeanceColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "seance", columnName, columnDefinition);
    }

    private void ensureQuestionColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "question", columnName, columnDefinition);
    }

    private void ensureQuizResultColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "quiz_result", columnName, columnDefinition);
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (!tableExists(connection, tableName) || columnExists(connection, tableName, columnName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (var tables = metaData.getTables(connection.getCatalog(), null, tableName, new String[] { "TABLE" })) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (var columns = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        }
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
