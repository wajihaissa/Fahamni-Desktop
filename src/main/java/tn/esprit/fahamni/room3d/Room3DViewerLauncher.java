package tn.esprit.fahamni.room3d;

import com.jme3.system.AppSettings;

import java.util.Objects;

public final class Room3DViewerLauncher {

    private static final Object LOCK = new Object();

    private static Room3DApplication activeApplication;

    private Room3DViewerLauncher() {
    }

    public static void showPreview(Room3DPreviewData previewData) {
        Objects.requireNonNull(previewData, "previewData");

        synchronized (LOCK) {
            if (activeApplication != null) {
                Room3DApplication application = activeApplication;
                activeApplication.enqueue(() -> {
                    application.updatePreview(previewData);
                    return null;
                });
                return;
            }

            Room3DApplication application = new Room3DApplication(previewData);
            activeApplication = application;

            Thread renderThread = new Thread(() -> startApplication(application, previewData), "fahamni-room-3d");
            renderThread.setDaemon(true);
            renderThread.setUncaughtExceptionHandler((thread, throwable) -> onApplicationClosed(application));
            renderThread.start();
        }
    }

    public static Integer getActiveSelectedSeatId() {
        synchronized (LOCK) {
            return activeApplication == null ? null : activeApplication.getSelectedSeatId();
        }
    }

    public static String getActiveSelectedSeatLabel() {
        synchronized (LOCK) {
            return activeApplication == null ? null : activeApplication.getSelectedSeatLabel();
        }
    }

    public static boolean isActiveSelectionMode() {
        synchronized (LOCK) {
            return activeApplication != null && activeApplication.isSelectionMode();
        }
    }

    public static void closeActiveViewer() {
        Room3DApplication application;
        synchronized (LOCK) {
            application = activeApplication;
        }

        if (application != null) {
            application.stop();
        }
    }

    static void onApplicationClosed(Room3DApplication application) {
        synchronized (LOCK) {
            if (activeApplication == application) {
                activeApplication = null;
            }
        }
    }

    private static void startApplication(Room3DApplication application, Room3DPreviewData previewData) {
        try {
            AppSettings settings = new AppSettings(true);
            settings.setTitle(buildWindowTitle(previewData));
            settings.setResizable(true);
            settings.setResolution(1280, 720);
            settings.setSamples(8);
            settings.setGammaCorrection(true);
            settings.setVSync(true);
            settings.setFrameRate(120);

            application.setShowSettings(false);
            application.setSettings(settings);
            application.start();
        } catch (RuntimeException exception) {
            onApplicationClosed(application);
            throw exception;
        }
    }

    private static String buildWindowTitle(Room3DPreviewData previewData) {
        if (previewData != null && previewData.supportsSeatSelection()) {
            return "Fahamni - Selection 3D de place";
        }
        return "Fahamni - Apercu 3D de salle";
    }
}
