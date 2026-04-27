package tn.esprit.fahamni.utils;

import tn.esprit.fahamni.entities.Matiere;

/**
 * Application-wide state holder for shared context.
 * Uses singleton pattern to manage globally accessible application state.
 * Currently tracks the currently selected course (Matiere).
 */
public class ApplicationState {

    private static ApplicationState instance;
    private Matiere currentMatiere;
    private String currentView = "Dashboard";

    private ApplicationState() {
    }

    public static ApplicationState getInstance() {
        if (instance == null) {
            instance = new ApplicationState();
        }
        return instance;
    }

    public Matiere getCurrentMatiere() {
        return currentMatiere;
    }

    public void setCurrentMatiere(Matiere matiere) {
        this.currentMatiere = matiere;
    }

    public void clearCurrentMatiere() {
        this.currentMatiere = null;
    }

    public String getCurrentView() {
        return currentView;
    }

    public void setCurrentView(String currentView) {
        if (currentView == null || currentView.isBlank()) {
            return;
        }
        this.currentView = currentView;
    }
}
