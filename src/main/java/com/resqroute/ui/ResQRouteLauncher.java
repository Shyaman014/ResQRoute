package com.resqroute.ui;

import javafx.application.Application;

/**
 * Standard standalone entry point launcher to avoid JavaFX module-path restrictions when launching via standard classpath.
 */
public class ResQRouteLauncher {

    public static void main(String[] args) {
        Application.launch(ResQRouteApp.class, args);
    }
}
