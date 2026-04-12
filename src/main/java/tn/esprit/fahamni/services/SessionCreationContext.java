package tn.esprit.fahamni.services;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SessionCreationContext {

    @FunctionalInterface
    public interface Navigator {
        void openSessionCreation();
    }

    public record PendingSelection(Integer salleId, Map<Integer, Integer> equipementQuantites) {
    }

    private static Navigator navigator;
    private static Integer pendingSalleId;
    private static final LinkedHashMap<Integer, Integer> pendingEquipementQuantites = new LinkedHashMap<>();

    private SessionCreationContext() {
    }

    public static void registerNavigator(Navigator nextNavigator) {
        navigator = nextNavigator;
    }

    public static void clearPendingSelection() {
        pendingSalleId = null;
        pendingEquipementQuantites.clear();
    }

    public static void prepareRoomSelection(Integer salleId) {
        pendingSalleId = salleId != null && salleId > 0 ? salleId : null;
        pendingEquipementQuantites.clear();
    }

    public static void prepareEquipmentSelection(Integer equipementId, int quantite) {
        pendingSalleId = null;
        pendingEquipementQuantites.clear();
        if (equipementId != null && equipementId > 0) {
            pendingEquipementQuantites.put(equipementId, Math.max(1, quantite));
        }
    }

    public static boolean requestSessionCreationOpen() {
        if (navigator == null) {
            return false;
        }
        navigator.openSessionCreation();
        return true;
    }

    public static PendingSelection consumePendingSelection() {
        if (pendingSalleId == null && pendingEquipementQuantites.isEmpty()) {
            return null;
        }

        PendingSelection selection = new PendingSelection(
            pendingSalleId,
            Map.copyOf(pendingEquipementQuantites)
        );
        clearPendingSelection();
        return selection;
    }
}
