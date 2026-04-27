package tn.esprit.fahamni.utils;

import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

/**
 * Centralized navigator for managing view transitions and maintaining references to shared UI elements.
 * This prevents null pointer issues when trying to access contentPane from deeply nested views.
 */
public class ViewNavigator {

    private static ViewNavigator instance;
    private AnchorPane contentPane;
    private Label pageTitle;

    private ViewNavigator() {
    }

    public static ViewNavigator getInstance() {
        if (instance == null) {
            instance = new ViewNavigator();
        }
        return instance;
    }

    /**
     * Set the main content pane and page title references from MainController.
     * This should be called during MainController initialization.
     */
    public void initialize(AnchorPane contentPane, Label pageTitle) {
        this.contentPane = contentPane;
        this.pageTitle = pageTitle;
        System.out.println("[ViewNavigator] Initialized with contentPane and pageTitle");
    }

    /**
     * Get the shared content pane where views are displayed.
     */
    public AnchorPane getContentPane() {
        if (contentPane == null) {
            System.err.println("[ViewNavigator] WARNING: contentPane is null - initialize ViewNavigator first!");
        }
        return contentPane;
    }

    /**
     * Get the page title label for updating the title when views change.
     */
    public Label getPageTitle() {
        if (pageTitle == null) {
            System.err.println("[ViewNavigator] WARNING: pageTitle is null - initialize ViewNavigator first!");
        }
        return pageTitle;
    }

    /**
     * Load a view and display it in the content pane.
     */
    public void loadView(String fxmlPath, String title) throws Exception {
        if (contentPane == null) {
            throw new RuntimeException("ViewNavigator not initialized - contentPane is null");
        }

        javafx.scene.Node view = SceneManager.loadView(
            tn.esprit.fahamni.test.Main.class,
            fxmlPath
        );

        contentPane.getChildren().clear();
        AnchorPane.setTopAnchor(view, 0.0);
        AnchorPane.setBottomAnchor(view, 0.0);
        AnchorPane.setLeftAnchor(view, 0.0);
        AnchorPane.setRightAnchor(view, 0.0);
        contentPane.getChildren().add(view);

        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    /**
     * Load a view into the content pane (with a title).
     */
    public void replaceView(javafx.scene.Node newView, String title) {
        if (contentPane == null) {
            System.err.println("[ViewNavigator] ERROR: Cannot replace view - contentPane is null");
            return;
        }

        contentPane.getChildren().clear();
        AnchorPane.setTopAnchor(newView, 0.0);
        AnchorPane.setBottomAnchor(newView, 0.0);
        AnchorPane.setLeftAnchor(newView, 0.0);
        AnchorPane.setRightAnchor(newView, 0.0);
        contentPane.getChildren().add(newView);

        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    /**
     * Update only the page title.
     */
    public void setPageTitle(String title) {
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }
}
