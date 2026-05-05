package tn.esprit.fahamni.utils;

import javafx.collections.ObservableList;
import javafx.scene.Node;

import java.util.prefs.Preferences;

public final class FrontOfficeThemePreference {

    public enum ThemeMode {
        DARK,
        LIGHT
    }

    private static final String DARK_CLASS = "theme-dark";
    private static final String LIGHT_CLASS = "theme-light";
    private static final String PREF_KEY = "frontoffice.theme.mode";
    private static final Preferences PREFS = Preferences.userNodeForPackage(FrontOfficeThemePreference.class);

    private static ThemeMode currentMode = ThemeMode.LIGHT;

    private FrontOfficeThemePreference() {
    }

    public static ThemeMode getCurrentMode() {
        return currentMode;
    }

    public static boolean isLightMode() {
        return currentMode == ThemeMode.LIGHT;
    }

    public static ThemeMode toggle() {
        return currentMode; // fixe — pas de toggle
    }

    public static void setCurrentMode(ThemeMode mode) {
        currentMode = ThemeMode.DARK; // navbar toujours dark
        PREFS.put(PREF_KEY, ThemeMode.DARK.name());
    }

    public static void apply(Node node) {
        if (node == null) {
            return;
        }

        ObservableList<String> styleClasses = node.getStyleClass();
        styleClasses.remove(DARK_CLASS);
        styleClasses.remove(LIGHT_CLASS);
        styleClasses.add(isLightMode() ? LIGHT_CLASS : DARK_CLASS);
    }

    private static ThemeMode loadThemeMode() {
        try {
            return ThemeMode.valueOf(PREFS.get(PREF_KEY, ThemeMode.LIGHT.name()));
        } catch (IllegalArgumentException ignored) {
            return ThemeMode.LIGHT;
        }
    }
}
