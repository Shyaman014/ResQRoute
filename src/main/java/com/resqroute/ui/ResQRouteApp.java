package com.resqroute.ui;

import com.resqroute.ui.controller.NavigationController;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.view.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main JavaFX Application entry point for ResQRoute.
 *
 * <p>Initializes the presentation controllers, root window layouts, and global stylesheet.</p>
 */
public class ResQRouteApp extends Application {

    private SimulationPresentationModel presentationModel;

    @Override
    public void start(Stage primaryStage) {
        // Global uncaught exception handler to prevent unhandled exceptions from terminating UI
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            com.resqroute.logging.AppLogger.error("Uncaught exception on thread " + thread.getName(), throwable);
        });

        // UI thread safe presentation model
        presentationModel = new SimulationPresentationModel(Platform::runLater);
        NavigationController navigationController = new NavigationController();

        // Create main layout view
        MainView mainView = new MainView(navigationController, presentationModel);

        Scene scene = new Scene(mainView, 1240, 820);

        // Attach stylesheet
        URL cssResource = getClass().getResource("/com/resqroute/ui/css/resqroute.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        // Global keyboard navigation shortcuts (Alt+1 through Alt+7, Ctrl+1 through Ctrl+7)
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isAltDown() || event.isControlDown()) {
                switch (event.getCode()) {
                    case DIGIT1 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.DASHBOARD);
                    case DIGIT2 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.MAP);
                    case DIGIT3 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.EMERGENCIES);
                    case DIGIT4 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.AMBULANCES);
                    case DIGIT5 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.HOSPITALS);
                    case DIGIT6 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.SIMULATION);
                    case DIGIT7 -> navigationController.navigateTo(com.resqroute.ui.model.NavSection.SETTINGS);
                    default -> {}
                }
            }
        });

        primaryStage.setTitle("ResQRoute — Emergency Ambulance Dispatch & City Simulator");
        primaryStage.setMinWidth(1120);
        primaryStage.setMinHeight(750);
        primaryStage.setScene(scene);

        // Safe cleanup on window close
        primaryStage.setOnCloseRequest(e -> {
            if (presentationModel != null) {
                presentationModel.shutdown();
            }
        });

        // Automatically initialize default scenario on start for immediate responsiveness
        presentationModel.initializeDefaultScenario();

        primaryStage.show();
    }

    @Override
    public void stop() {
        if (presentationModel != null) {
            presentationModel.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
