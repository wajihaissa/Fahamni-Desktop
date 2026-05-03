package tn.esprit.fahamni.tools;

import tn.esprit.fahamni.services.BlogService;
import tn.esprit.fahamni.utils.BlogSchemaSupport;
import tn.esprit.fahamni.utils.DatabaseSchemaUtils;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class BlogSchemaRepairRunner {

    private BlogSchemaRepairRunner() {
    }

    public static void main(String[] args) {
        Connection connection = MyDataBase.getInstance().getCnx();
        if (connection == null) {
            System.err.println("Connexion MySQL indisponible. Verifiez DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD.");
            System.exit(1);
            return;
        }

        List<String> report = BlogSchemaSupport.repairSchema();
        System.out.println("=== Blog Schema Repair ===");
        if (report.isEmpty()) {
            System.out.println("Aucune operation necessaire.");
        } else {
            for (String line : report) {
                System.out.println("- " + line);
            }
        }

        try {
            int visibleBlogs = new BlogService().getAllBlogs().size();
            System.out.println("visible_blog_count: " + visibleBlogs);
            printTableSummary(connection, "blog");
            printTableSummary(connection, "interaction");
            printTableSummary(connection, "notification");
            printTableSummary(connection, "comment_log");
            printTableSummary(connection, "activity_log");
        } catch (SQLException exception) {
            System.err.println("Erreur lors du resume du schema: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void printTableSummary(Connection connection, String tableName) throws SQLException {
        if (!DatabaseSchemaUtils.tableExists(connection, tableName)) {
            System.out.println(tableName + ": missing");
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(tableName).append(": ");
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, null)) {
            boolean first = true;
            while (columns.next()) {
                if (!first) {
                    builder.append(", ");
                }
                builder.append(columns.getString("COLUMN_NAME"));
                first = false;
            }
        }
        System.out.println(builder);
    }
}
