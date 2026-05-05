package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MyDataBase {

    private static MyDataBase instance;

    private static final String DEFAULT_DB_HOST = "127.0.0.1";
    private static final int DEFAULT_DB_PORT = 3306;
    private static final String DEFAULT_DB_NAME = "fahamni";
    private static final String DEFAULT_DB_USER = "root";

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

    private static final String CREATE_MATIERE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS matiere (
            id INT AUTO_INCREMENT PRIMARY KEY,
            titre VARCHAR(255) NOT NULL,
            description TEXT NULL,
            structure LONGTEXT NULL,
            created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
            cover_image VARCHAR(500) NULL
        )
        """;

    private static final String CREATE_CATEGORY_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS category (
            id INT AUTO_INCREMENT PRIMARY KEY,
            name VARCHAR(190) NOT NULL,
            slug VARCHAR(190) NOT NULL,
            CONSTRAINT uk_category_slug UNIQUE (slug)
        )
        """;

    private static final String CREATE_MATIERE_CATEGORY_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS matiere_category (
            matiere_id INT NOT NULL,
            category_id INT NOT NULL,
            PRIMARY KEY (matiere_id, category_id),
            CONSTRAINT fk_matiere_category_matiere
                FOREIGN KEY (matiere_id) REFERENCES matiere(id)
                ON DELETE CASCADE,
            CONSTRAINT fk_matiere_category_category
                FOREIGN KEY (category_id) REFERENCES category(id)
                ON DELETE CASCADE
        )
        """;

    private static final String CREATE_CHAPTER_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS chapter (
            id INT AUTO_INCREMENT PRIMARY KEY,
            titre VARCHAR(255) NOT NULL,
            matiere_id INT NOT NULL,
            CONSTRAINT fk_chapter_matiere
                FOREIGN KEY (matiere_id) REFERENCES matiere(id)
                ON DELETE CASCADE
        )
        """;

    private static final String CREATE_SECTION_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS section (
            id INT AUTO_INCREMENT PRIMARY KEY,
            titre VARCHAR(255) NOT NULL,
            chapter_id INT NOT NULL,
            CONSTRAINT fk_section_chapter
                FOREIGN KEY (chapter_id) REFERENCES chapter(id)
                ON DELETE CASCADE
        )
        """;

    private static final String CREATE_RESOURCE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS resource (
            id INT AUTO_INCREMENT PRIMARY KEY,
            titre VARCHAR(255) NOT NULL,
            type VARCHAR(100) NOT NULL,
            filepath VARCHAR(1000) NULL,
            link VARCHAR(1000) NULL,
            section_id INT NOT NULL,
            CONSTRAINT fk_resource_section
                FOREIGN KEY (section_id) REFERENCES section(id)
                ON DELETE CASCADE
        )
        """;

    private static final String CREATE_FLASHCARD_ATTEMPT_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS flashcard_attempt (
            id INT AUTO_INCREMENT PRIMARY KEY,
            question TEXT NOT NULL,
            user_answer TEXT NULL,
            ai_feedback TEXT NULL,
            is_correct BOOLEAN NOT NULL DEFAULT FALSE,
            matiere_id INT NOT NULL,
            section_id INT NOT NULL,
            expected_answer TEXT NULL,
            CONSTRAINT fk_flashcard_attempt_matiere
                FOREIGN KEY (matiere_id) REFERENCES matiere(id)
                ON DELETE CASCADE,
            CONSTRAINT fk_flashcard_attempt_section
                FOREIGN KEY (section_id) REFERENCES section(id)
                ON DELETE CASCADE
        )
        """;

    private static final String CREATE_CALL_ROOMS_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS call_rooms (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            room_code VARCHAR(6) NOT NULL,
            host_ip VARCHAR(64) NOT NULL,
            host_video_port INT NOT NULL,
            guest_ip VARCHAR(64) NULL,
            guest_video_port INT NULL,
            status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """;

    private Connection cnx;

    private MyDataBase() {
        connect();
    }

    private void connect() {
        String url = getUrl();
        String username = getUsername();
        List<String> candidatePasswords = getCandidatePasswords();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            cnx = null;
            System.out.println("MySQL driver not found: " + e.getMessage());
            return;
        }

        SQLException lastException = null;
        for (String password : candidatePasswords) {
            try {
                cnx = DriverManager.getConnection(url, username, password);
                ensureManagedSchema(cnx);
                System.out.println("Connected to MySQL: " + sanitizeJdbcUrl(url));
                return;
            } catch (SQLException e) {
                cnx = null;
                lastException = e;
            }
        }

        if (lastException == null) {
            System.out.println("Database connection failed for " + sanitizeJdbcUrl(url));
            return;
        }

        System.out.println(
            "Database connection failed for " + sanitizeJdbcUrl(url)
                + " (SQLState=" + lastException.getSQLState()
                + ", ErrorCode=" + lastException.getErrorCode() + "): "
                + lastException.getMessage()
        );
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

    private static String getUrl() {
        String configuredUrl = firstConfigured("DB_URL", "FAHAMNI_DB_URL");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }

        String host = getString(DEFAULT_DB_HOST, "DB_HOST", "FAHAMNI_DB_HOST");
        int port = getInt(DEFAULT_DB_PORT, "DB_PORT", "FAHAMNI_DB_PORT");
        String dbName = getString(DEFAULT_DB_NAME, "DB_NAME", "FAHAMNI_DB_NAME");

        return "jdbc:mysql://" + host + ":" + port + "/" + dbName
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            + "&connectTimeout=5000&socketTimeout=10000";
    }

    private static String getUsername() {
        return getString(DEFAULT_DB_USER, "DB_USER", "FAHAMNI_DB_USERNAME");
    }

    private static List<String> getCandidatePasswords() {
        Set<String> passwords = new LinkedHashSet<>();
        String configuredPassword = firstConfigured("DB_PASSWORD", "FAHAMNI_DB_PASSWORD");
        if (configuredPassword != null) {
            passwords.add(configuredPassword);
        }
        passwords.add("");
        return new ArrayList<>(passwords);
    }

    private static String getString(String fallback, String... keys) {
        String value = firstConfigured(keys);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int getInt(int fallback, String... keys) {
        String value = firstConfigured(keys);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String firstConfigured(String... keys) {
        if (keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = LocalConfig.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void ensureManagedSchema(Connection connection) {
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
            ensureQuizColumn(connection, "keyword", "VARCHAR(190) NOT NULL DEFAULT '' AFTER titre");
            ensureQuizColumn(connection, "created_at", "DATETIME NULL AFTER keyword");
            ensureQuestionColumn(connection, "topic", "VARCHAR(190) NULL AFTER question");
            ensureQuestionColumn(connection, "difficulty", "VARCHAR(40) NOT NULL DEFAULT 'Medium' AFTER topic");
            ensureQuestionColumn(connection, "source_question_id", "BIGINT NULL AFTER difficulty");
            ensureQuestionColumn(connection, "hint", "TEXT NULL AFTER source_question_id");
            ensureQuestionColumn(connection, "explanation", "TEXT NULL AFTER hint");
            ensureQuizResultTable(connection);
            ensureQuizResultColumn(connection, "total_questions", "INT NULL AFTER score");
            ensureQuizResultColumn(connection, "percentage", "DOUBLE NULL AFTER total_questions");
            ensureQuizResultColumn(connection, "passed", "BOOLEAN NULL AFTER percentage");
            ensureQuizResultColumn(connection, "user_id", "INT NULL AFTER passed");
            ensureQuizResultColumn(connection, "user_email", "VARCHAR(190) NULL AFTER user_id");
            ensureQuizResultColumn(connection, "user_full_name", "VARCHAR(190) NULL AFTER user_email");
            ensureQuizAnswerAttemptTable(connection);
            ensureCourseTables(connection);
            ensureCallRoomTable(connection);
        } catch (SQLException exception) {
            System.out.println("Unable to align managed schema automatically: " + exception.getMessage());
        }
    }

    private void ensureCallRoomTable(Connection connection) throws SQLException {
        createTableIfMissing(connection, "call_rooms", CREATE_CALL_ROOMS_TABLE_SQL);
        ensureCallRoomColumn(connection, "room_code", "VARCHAR(6) NOT NULL");
        ensureCallRoomColumn(connection, "host_ip", "VARCHAR(64) NOT NULL");
        ensureCallRoomColumn(connection, "host_video_port", "INT NOT NULL");
        ensureCallRoomColumn(connection, "guest_ip", "VARCHAR(64) NULL AFTER host_video_port");
        ensureCallRoomColumn(connection, "guest_video_port", "INT NULL AFTER guest_ip");
        ensureCallRoomColumn(connection, "status", "VARCHAR(20) NOT NULL DEFAULT 'WAITING' AFTER guest_video_port");
        ensureCallRoomColumn(connection, "created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status");

        ensureIndex(
            connection,
            "call_rooms",
            "uk_call_rooms_room_code",
            "CREATE UNIQUE INDEX uk_call_rooms_room_code ON call_rooms(room_code)"
        );
        ensureIndex(
            connection,
            "call_rooms",
            "idx_call_rooms_status_created_at",
            "CREATE INDEX idx_call_rooms_status_created_at ON call_rooms(status, created_at)"
        );
    }

    private void ensureCourseTables(Connection connection) throws SQLException {
        createTableIfMissing(connection, "matiere", CREATE_MATIERE_TABLE_SQL);
        ensureCourseMatiereColumn(connection, "description", "TEXT NULL AFTER titre");
        ensureCourseMatiereColumn(connection, "structure", "LONGTEXT NULL AFTER description");
        ensureCourseMatiereColumn(connection, "created_at", "DATETIME NULL DEFAULT CURRENT_TIMESTAMP AFTER structure");
        ensureCourseMatiereColumn(connection, "cover_image", "VARCHAR(500) NULL AFTER created_at");

        createTableIfMissing(connection, "category", CREATE_CATEGORY_TABLE_SQL);
        ensureCategoryColumn(connection, "name", "VARCHAR(190) NOT NULL");
        ensureCategoryColumn(connection, "slug", "VARCHAR(190) NOT NULL");
        ensureCategoryIndexes(connection);

        createTableIfMissing(connection, "matiere_category", CREATE_MATIERE_CATEGORY_TABLE_SQL);
        createTableIfMissing(connection, "chapter", CREATE_CHAPTER_TABLE_SQL);
        createTableIfMissing(connection, "section", CREATE_SECTION_TABLE_SQL);
        createTableIfMissing(connection, "resource", CREATE_RESOURCE_TABLE_SQL);
        createTableIfMissing(connection, "flashcard_attempt", CREATE_FLASHCARD_ATTEMPT_TABLE_SQL);

        ensureIndex(
            connection,
            "matiere_category",
            "idx_matiere_category_category",
            "CREATE INDEX idx_matiere_category_category ON matiere_category(category_id)"
        );
        ensureIndex(
            connection,
            "chapter",
            "idx_chapter_matiere",
            "CREATE INDEX idx_chapter_matiere ON chapter(matiere_id)"
        );
        ensureIndex(
            connection,
            "section",
            "idx_section_chapter",
            "CREATE INDEX idx_section_chapter ON section(chapter_id)"
        );
        ensureIndex(
            connection,
            "resource",
            "idx_resource_section",
            "CREATE INDEX idx_resource_section ON resource(section_id)"
        );
        ensureIndex(
            connection,
            "flashcard_attempt",
            "idx_flashcard_attempt_matiere",
            "CREATE INDEX idx_flashcard_attempt_matiere ON flashcard_attempt(matiere_id)"
        );
        ensureIndex(
            connection,
            "flashcard_attempt",
            "idx_flashcard_attempt_section",
            "CREATE INDEX idx_flashcard_attempt_section ON flashcard_attempt(section_id)"
        );
    }

    private void createTableIfMissing(Connection connection, String tableName, String createSql) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, tableName)) {
            return;
        }
        DatabaseSchemaUtils.executeDdl(connection, createSql);
    }

    private void ensureSalleColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "salle", columnName, columnDefinition);
    }

    private void ensureSeanceColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "seance", columnName, columnDefinition);
    }

    private void ensureCourseMatiereColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "matiere", columnName, columnDefinition);
    }

    private void ensureCategoryColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "category", columnName, columnDefinition);
    }

    private void ensureQuizColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "quiz", columnName, columnDefinition);
    }

    private void ensureQuestionColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "question", columnName, columnDefinition);
    }

    private void ensureQuizResultColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "quiz_result", columnName, columnDefinition);
    }

    private void ensureCallRoomColumn(Connection connection, String columnName, String columnDefinition) throws SQLException {
        ensureColumn(connection, "call_rooms", columnName, columnDefinition);
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (!DatabaseSchemaUtils.tableExists(connection, tableName)
            || DatabaseSchemaUtils.columnExists(connection, tableName, columnName)) {
            return;
        }

        DatabaseSchemaUtils.executeDdl(
            connection,
            "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition
        );
    }

    private void ensureQuizResultTable(Connection connection) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "quiz_result")) {
            ensureQuizResultIndexes(connection);
            return;
        }

        if (!DatabaseSchemaUtils.tableExists(connection, "quiz")) {
            return;
        }

        String quizIdType = resolveColumnType(connection, "quiz", "id", "BIGINT");
        String userIdType = DatabaseSchemaUtils.tableExists(connection, "user")
            ? resolveColumnType(connection, "user", "id", "INT")
            : "INT";

        DatabaseSchemaUtils.executeDdl(connection, buildQuizResultCreateSql(quizIdType, userIdType));
        ensureQuizResultIndexes(connection);
    }

    private String buildQuizResultCreateSql(String quizIdType, String userIdType) {
        return """
            CREATE TABLE IF NOT EXISTS quiz_result (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                quiz_id %s NOT NULL,
                score INT NOT NULL,
                completed_at DATETIME NOT NULL,
                total_questions INT NULL,
                percentage DOUBLE NULL,
                passed BOOLEAN NULL,
                user_id %s NULL,
                user_email VARCHAR(190) NULL,
                user_full_name VARCHAR(190) NULL
            )
            """.formatted(quizIdType, userIdType);
    }

    private void ensureQuizResultIndexes(Connection connection) throws SQLException {
        ensureIndex(
            connection,
            "quiz_result",
            "idx_quiz_result_quiz",
            "CREATE INDEX idx_quiz_result_quiz ON quiz_result(quiz_id)"
        );
        ensureIndex(
            connection,
            "quiz_result",
            "idx_quiz_result_user",
            "CREATE INDEX idx_quiz_result_user ON quiz_result(user_id)"
        );
        ensureIndex(
            connection,
            "quiz_result",
            "idx_quiz_result_completed_at",
            "CREATE INDEX idx_quiz_result_completed_at ON quiz_result(completed_at)"
        );
    }

    private void ensureCategoryIndexes(Connection connection) throws SQLException {
        ensureIndex(
            connection,
            "category",
            "uk_category_slug",
            "CREATE UNIQUE INDEX uk_category_slug ON category(slug)"
        );
    }

    private void ensureQuizAnswerAttemptTable(Connection connection) throws SQLException {
        if (DatabaseSchemaUtils.tableExists(connection, "quiz_answer_attempt")) {
            ensureQuizAnswerAttemptIndexes(connection);
            return;
        }

        if (!DatabaseSchemaUtils.tableExists(connection, "quiz_result")
            || !DatabaseSchemaUtils.tableExists(connection, "question")
            || !DatabaseSchemaUtils.tableExists(connection, "choice")) {
            return;
        }

        String quizResultIdType = resolveColumnType(connection, "quiz_result", "id", "BIGINT");
        String questionIdType = resolveColumnType(connection, "question", "id", "BIGINT");
        String selectedChoiceIdType = resolveColumnType(connection, "choice", "id", "BIGINT");

        SQLException foreignKeyFailure = null;
        try {
            DatabaseSchemaUtils.executeDdl(
                connection,
                buildQuizAnswerAttemptCreateSql(quizResultIdType, questionIdType, selectedChoiceIdType, true)
            );
        } catch (SQLException exception) {
            foreignKeyFailure = exception;
        }

        if (!DatabaseSchemaUtils.tableExists(connection, "quiz_answer_attempt")) {
            DatabaseSchemaUtils.executeDdl(
                connection,
                buildQuizAnswerAttemptCreateSql(quizResultIdType, questionIdType, selectedChoiceIdType, false)
            );
            if (foreignKeyFailure != null) {
                System.out.println(
                    "quiz_answer_attempt created without foreign keys to stay compatible with the current quiz schema: "
                        + foreignKeyFailure.getMessage()
                );
            }
        }

        ensureQuizAnswerAttemptIndexes(connection);
    }

    private String buildQuizAnswerAttemptCreateSql(
        String quizResultIdType,
        String questionIdType,
        String selectedChoiceIdType,
        boolean withForeignKeys
    ) {
        StringBuilder sql = new StringBuilder("""
            CREATE TABLE IF NOT EXISTS quiz_answer_attempt (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                quiz_result_id %s NOT NULL,
                question_id %s NOT NULL,
                selected_choice_id %s NULL,
                is_correct BOOLEAN NOT NULL DEFAULT FALSE,
                answered_at DATETIME NOT NULL
            """.formatted(quizResultIdType, questionIdType, selectedChoiceIdType));

        if (withForeignKeys) {
            sql.append("""
                ,
                CONSTRAINT fk_quiz_answer_attempt_result
                    FOREIGN KEY (quiz_result_id) REFERENCES quiz_result(id)
                    ON DELETE CASCADE,
                CONSTRAINT fk_quiz_answer_attempt_question
                    FOREIGN KEY (question_id) REFERENCES question(id)
                    ON DELETE CASCADE,
                CONSTRAINT fk_quiz_answer_attempt_choice
                    FOREIGN KEY (selected_choice_id) REFERENCES choice(id)
                    ON DELETE SET NULL
                """);
        }

        sql.append("\n)");
        return sql.toString();
    }

    private void ensureQuizAnswerAttemptIndexes(Connection connection) throws SQLException {
        ensureIndex(
            connection,
            "quiz_answer_attempt",
            "idx_quiz_answer_attempt_result",
            "CREATE INDEX idx_quiz_answer_attempt_result ON quiz_answer_attempt(quiz_result_id)"
        );
        ensureIndex(
            connection,
            "quiz_answer_attempt",
            "idx_quiz_answer_attempt_question",
            "CREATE INDEX idx_quiz_answer_attempt_question ON quiz_answer_attempt(question_id)"
        );
        ensureIndex(
            connection,
            "quiz_answer_attempt",
            "idx_quiz_answer_attempt_choice",
            "CREATE INDEX idx_quiz_answer_attempt_choice ON quiz_answer_attempt(selected_choice_id)"
        );
    }

    private void ensureIndex(Connection connection, String tableName, String indexName, String createIndexSql) throws SQLException {
        if (!DatabaseSchemaUtils.tableExists(connection, tableName) || DatabaseSchemaUtils.indexExists(connection, tableName, indexName)) {
            return;
        }
        DatabaseSchemaUtils.executeDdl(connection, createIndexSql);
    }

    private String resolveColumnType(Connection connection, String tableName, String columnName, String fallbackType) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();

        for (String candidateTableName : new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()}) {
            for (String candidateColumnName : new String[]{columnName, columnName.toUpperCase(), columnName.toLowerCase()}) {
                try (ResultSet columns = metaData.getColumns(catalog, null, candidateTableName, candidateColumnName)) {
                    if (!columns.next()) {
                        continue;
                    }

                    String typeName = columns.getString("TYPE_NAME");
                    if (typeName != null && !typeName.isBlank()) {
                        return typeName;
                    }
                }
            }
        }

        return fallbackType;
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
            try {
                System.out.println("call_rooms table present: " + DatabaseSchemaUtils.tableExists(db.getCnx(), "call_rooms"));
            } catch (SQLException exception) {
                System.out.println("Impossible de verifier la table call_rooms: " + exception.getMessage());
            }
        } else {
            System.out.println("Connexion echouee");
        }
    }
}
