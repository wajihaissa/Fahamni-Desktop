package tn.esprit.fahamni.room3d;

import com.jme3.system.AppSettings;

import java.util.Objects;

public final class Room3DViewerLauncher {

    private static final Object LOCK = new Object();

    private static Room3DApplication activeApplication;
    private static boolean activeViewerReady;
    private static Integer selectedSeatIdSnapshot;
    private static String selectedSeatLabelSnapshot;

    private Room3DViewerLauncher() {
    }

    public static void showPreview(Room3DPreviewData previewData) {
        Objects.requireNonNull(previewData, "previewData");

        synchronized (LOCK) {
            clearSelectedSeatSnapshotLocked();
            activeViewerReady = false;

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
            refreshSelectedSeatSnapshotLocked();
            return selectedSeatIdSnapshot;
        }
    }

    public static String getActiveSelectedSeatLabel() {
        synchronized (LOCK) {
            refreshSelectedSeatSnapshotLocked();
            return selectedSeatLabelSnapshot;
        }
    }

    public static boolean isActiveSelectionMode() {
        synchronized (LOCK) {
            return activeApplication != null && activeApplication.isSelectionMode();
        }
    }

    public static boolean isActiveViewerReady() {
        synchronized (LOCK) {
            return activeApplication != null && activeViewerReady;
        }
    }

    public static boolean waitForActiveViewerReady(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        while (System.currentTimeMillis() < deadline) {
            synchronized (LOCK) {
                if (activeApplication == null) {
                    return false;
                }
                if (activeViewerReady) {
                    return true;
                }
            }

            try {
                Thread.sleep(40L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        synchronized (LOCK) {
            return activeApplication != null && activeViewerReady;
        }
    }

    public static void clearSelectedSeatSnapshot() {
        synchronized (LOCK) {
            clearSelectedSeatSnapshotLocked();
        }
    }

    public static void clearActiveSeatSelection() {
        Room3DApplication application;
        synchronized (LOCK) {
            clearSelectedSeatSnapshotLocked();
            application = activeApplication;
        }

        if (application != null) {
            application.enqueue(() -> {
                application.clearSeatSelection();
                return null;
            });
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
                activeViewerReady = false;
            }
        }
    }

    static void markApplicationReady(Room3DApplication application) {
        synchronized (LOCK) {
            if (activeApplication == application) {
                activeViewerReady = true;
            }
        }
    }

    static void updateSelectedSeatSnapshot(Integer seatId, String seatLabel) {
        synchronized (LOCK) {
            selectedSeatIdSnapshot = seatId;
            selectedSeatLabelSnapshot = seatId == null || seatLabel == null || seatLabel.isBlank()
                ? null
                : seatLabel;
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

    private static void refreshSelectedSeatSnapshotLocked() {
        if (activeApplication == null) {
            return;
        }

        updateSelectedSeatSnapshot(
            activeApplication.getSelectedSeatId(),
            activeApplication.getSelectedSeatLabel()
        );
    }

    private static void clearSelectedSeatSnapshotLocked() {
        selectedSeatIdSnapshot = null;
        selectedSeatLabelSnapshot = null;
    }

    private static String buildWindowTitle(Room3DPreviewData previewData) {
        if (previewData != null && previewData.supportsSeatSelection()) {
            return "Fahamni - Selection 3D de place";
        }
        if (previewData != null && previewData.isDesignReview()) {
            return "Fahamni - Conception 3D de salle";
        }
        return "Fahamni - Apercu 3D de salle";
    }
}
