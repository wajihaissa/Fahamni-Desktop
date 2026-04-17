package tn.esprit.fahamni.utils;

import javafx.scene.Parent;
import javafx.scene.Scene;

public final class FrontOfficeUiTheme {

    private static final String STYLESHEET = "/com/fahamni/styles/frontoffice-theme.css";
    private static final String DASHBOARD_STYLESHEET = "/com/fahamni/styles/frontoffice-dashboard.css";
    private static final String RESERVATION_STYLESHEET = "/com/fahamni/styles/frontoffice-reservation.css";
    private static final String CALENDAR_STYLESHEET = "/com/fahamni/styles/frontoffice-calendar.css";
    private static final String INFRASTRUCTURE_STYLESHEET = "/com/fahamni/styles/frontoffice-infrastructure.css";
    private static final String BLOG_STYLESHEET = "/com/fahamni/styles/frontoffice-blog.css";

    private FrontOfficeUiTheme() {
    }

    public static void apply(Scene scene) {
        UiTheme.apply(scene, STYLESHEET);
    }

    public static void applyBlogDialog(Scene scene) {
        UiTheme.apply(scene, STYLESHEET, BLOG_STYLESHEET);
    }

    public static void applyViewTheme(Parent root, String fxmlPath) {
        String stylesheet = resolveViewStylesheet(fxmlPath);
        if (stylesheet == null) {
            return;
        }

        UiTheme.apply(root, stylesheet);
    }

    private static String resolveViewStylesheet(String fxmlPath) {
        if (fxmlPath == null || fxmlPath.isBlank()) {
            return null;
        }

        String fileName = fxmlPath.substring(fxmlPath.lastIndexOf('/') + 1);
        return switch (fileName) {
            case "DashboardView.fxml" -> DASHBOARD_STYLESHEET;
            case "ReservationView.fxml" -> RESERVATION_STYLESHEET;
            case "SeanceListView.fxml" -> CALENDAR_STYLESHEET;
            case "SallesEquipementsView.fxml" -> INFRASTRUCTURE_STYLESHEET;
            case "BlogView.fxml" -> BLOG_STYLESHEET;
            default -> null;
        };
    }
}
