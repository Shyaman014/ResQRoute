package com.resqroute.ui.view;

import com.resqroute.ui.controller.NavigationController;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.model.NavSection;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Root container layout coordinating top status telemetry, navigation sidebar, and main view switching.
 */
public class MainView extends BorderPane {

    private final NavigationController navigationController;
    private final SimulationPresentationModel presentationModel;

    private final StackPane contentArea;
    private final Map<NavSection, Node> viewCache = new EnumMap<>(NavSection.class);

    public MainView(NavigationController navigationController, SimulationPresentationModel presentationModel) {
        this.navigationController = Objects.requireNonNull(navigationController, "NavigationController must not be null");
        this.presentationModel = Objects.requireNonNull(presentationModel, "SimulationPresentationModel must not be null");

        getStyleClass().add("main-view-root");

        // 1. Sidebar
        SidebarNav sidebar = new SidebarNav(navigationController);
        setLeft(sidebar);

        // 2. Top Status Bar
        TopStatusBar statusBar = new TopStatusBar(presentationModel);
        setTop(statusBar);

        // 3. Central Content Area
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        setCenter(contentArea);

        // Pre-initialize views for instant, fluid tab switching
        viewCache.put(NavSection.DASHBOARD, new DashboardView(presentationModel));
        viewCache.put(NavSection.MAP, new MapView(presentationModel));
        viewCache.put(NavSection.EMERGENCIES, new EmergenciesView(presentationModel));
        viewCache.put(NavSection.AMBULANCES, new AmbulancesView(presentationModel));
        viewCache.put(NavSection.HOSPITALS, new HospitalsView(presentationModel));
        viewCache.put(NavSection.SIMULATION, new SimulationView(presentationModel));
        viewCache.put(NavSection.SETTINGS, new SettingsView(presentationModel));

        // Connect navigation listener
        navigationController.addListener(this::switchSection);
        switchSection(navigationController.getCurrentSection());
    }

    private void switchSection(NavSection section) {
        Node view = viewCache.get(section);
        if (view != null) {
            contentArea.getChildren().setAll(view);
        }
    }

    public Node getView(NavSection section) {
        return viewCache.get(section);
    }
}
