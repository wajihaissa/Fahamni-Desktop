package tn.esprit.fahamni.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchemaUtils {

    private DatabaseSchemaUtils() {
    }

    public static boolean tableExists(Connection connection, String tableName) throws SQLException {
        if (connection == null || tableName == null || tableName.isBlank()) {
            return false;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(), null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toLowerCase(), null)) {
            return resultSet.next();
        }
    }

    public static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        if (connection == null || tableName == null || tableName.isBlank() || columnName == null || columnName.isBlank()) {
            return false;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName.toLowerCase(), columnName.toLowerCase())) {
            return resultSet.next();
        }
    }

    public static boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        if (connection == null || tableName == null || tableName.isBlank() || indexName == null || indexName.isBlank()) {
            return false;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidateTableName : new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()}) {
            try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, candidateTableName, false, false)) {
                while (resultSet.next()) {
                    String currentIndexName = resultSet.getString("INDEX_NAME");
                    if (indexName.equalsIgnoreCase(currentIndexName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static void executeDdl(Connection connection, String sql) throws SQLException {
        if (connection == null || sql == null || sql.isBlank()) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
