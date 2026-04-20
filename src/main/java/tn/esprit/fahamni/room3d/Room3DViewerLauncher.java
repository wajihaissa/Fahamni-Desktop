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

            Thread renderThread = new Thread(() -> startApplication(application), "fahamni-room-3d");
            renderThread.setDaemon(true);
            renderThread.setUncaughtExceptionHandler((thread, throwable) -> onApplicationClosed(application));
            renderThread.start();
        }
    }

    static void onApplicationClosed(Room3DApplication application) {
        synchronized (LOCK) {
            if (activeApplication == application) {
                activeApplication = null;
            }
        }
    }

    private static void startApplication(Room3DApplication application) {
        try {
            AppSettings settings = new AppSettings(true);
            settings.setTitle("Fahamni - Apercu 3D de salle");
            settings.setResizable(true);
            settings.setResolution(1280, 720);

            application.setShowSettings(false);
            application.setSettings(settings);
            application.start();
        } catch (RuntimeException exception) {
            onApplicationClosed(application);
            throw exception;
        }
    }
}
