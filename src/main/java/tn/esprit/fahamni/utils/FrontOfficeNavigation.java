package tn.esprit.fahamni.utils;

public final class FrontOfficeNavigation {

    @FunctionalInterface
    public interface Navigator {
        void open(Destination destination);
    }

    public enum Destination {
        DASHBOARD,
        RESERVATIONS,
        CALENDAR,
        INFRASTRUCTURE,
        PLANNER,
        QUIZ,
        BLOG,
        ABOUT,
        PROFILE,
        SETTINGS
    }

    private static Navigator navigator;

    private FrontOfficeNavigation() {
    }

    public static void registerNavigator(Navigator nextNavigator) {
        navigator = nextNavigator;
    }

    public static boolean open(Destination destination) {
        if (navigator == null || destination == null) {
            return false;
        }

        navigator.open(destination);
        return true;
    }
}
