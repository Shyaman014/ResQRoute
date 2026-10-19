package com.resqroute.ui.view;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.model.AmbulanceMovementTelemetry;
import com.resqroute.simulation.model.VehicleVisualState;
import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.component.StatusBadge;
import com.resqroute.ui.component.UiDialogs;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.map.MapCoordinateTransform;
import com.resqroute.ui.map.MapProvider;
import com.resqroute.ui.map.OsmTileService;
import com.resqroute.ui.map.RoadNetworkCanvas;
import com.resqroute.ui.model.UiMessage;
import com.resqroute.ui.model.UiSimulationState;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interactive map view providing full visualization of the city's road network,
 * real-time traffic congestion,
 * road closures, calculated emergency routes, live ambulance movement
 * animation, and integrated simulation controls.
 */
public class MapView extends BorderPane {

    private final SimulationPresentationModel model;

    // Map Canvas
    private final RoadNetworkCanvas canvas;
    private final Pane canvasWrapper;

    // Simulation Controls
    private final Label statusPillLabel;
    private final Label timeReadoutLabel;
    private final Button playPauseBtn;
    private final Button stepBtn;
    private final ComboBox<String> stepDurationCombo;
    private final ComboBox<String> speedCombo;
    private final Button resetBtn;

    // Routing Controls
    private final ComboBox<String> sourceCombo;
    private final ComboBox<String> destCombo;
    private final ComboBox<String> algoCombo;
    private final Button calculateRouteBtn;
    private final Button clearRouteBtn;

    // Route Result Labels
    private final Label routeStatusLabel;
    private final Label routeDistanceLabel;
    private final Label routeTimeLabel;
    private final Label routeWaypointsLabel;
    private final VBox alternativeRoutesBox;

    // Fleet & Missions Panel
    private final VBox fleetCardsContainer;
    private final Label fleetCountLabel;

    // Inspector Labels & Controls
    private final Label inspectorTitleLabel;
    private final Label inspectorDetailsLabel;
    private final Button setAsSourceBtn;
    private final Button setAsDestBtn;
    private final Button toggleRoadClosureBtn;
    private final Button sendToBaseBtn;

    // Active Incidents List
    private final ListView<String> incidentsListView;

    // Cartography Controls
    private final ComboBox<MapProvider> mapProviderCombo;
    private final Label tileStatusLabel;
    private final HBox fallbackBanner;

    // Active Selection State
    private Node currentlySelectedNode;
    private MapCoordinateTransform.EdgeMatch currentlySelectedEdge;
    private AmbulanceMovementTelemetry currentlySelectedAmbulance;
    private Hospital currentlySelectedHospital;

    public MapView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        getStyleClass().add("map-view-root");
        setPadding(new Insets(14, 18, 14, 18));

        // 1. Top Section: Header & Live Simulation Controls Toolbar
        VBox topContainer = new VBox(10);
        SectionHeader header = new SectionHeader(
                "Interactive City Map & Live Simulation Controls",
                "Real-time vehicle animation, dynamic rerouting, topological inspection, and time-step execution");

        // Control Toolbar
        statusPillLabel = new Label("IDLE");
        statusPillLabel.getStyleClass().addAll("sim-status-pill", "sim-status-idle");

        timeReadoutLabel = new Label("⏱ 00:00:00 / 00:30:00");
        timeReadoutLabel.getStyleClass().add("time-readout-badge");

        // Map Cartography Provider Selector
        mapProviderCombo = new ComboBox<>();
        mapProviderCombo.getItems().addAll(MapProvider.values());
        mapProviderCombo.setValue(MapProvider.CARTO_DARK);
        mapProviderCombo.getStyleClass().add("toolbar-combo");
        mapProviderCombo.setAccessibleText("Select Map Cartography Provider");
        mapProviderCombo.setTooltip(new Tooltip("Select OpenStreetMap Cartography Provider"));
        // NOTE: mapProviderCombo.setOnAction() is wired after canvas is initialized (below)

        // Tile Connectivity Status Pill
        tileStatusLabel = new Label("🟢 OSM Live");
        tileStatusLabel.getStyleClass().addAll("sim-status-pill", "sim-status-idle");
        tileStatusLabel.setTooltip(new Tooltip("OpenStreetMap Tile Provider Connectivity (Click to retry)"));
        // NOTE: tileStatusLabel.setOnMouseClicked() is wired after canvas is initialized (below)

        playPauseBtn = new Button("▶ Play");
        playPauseBtn.getStyleClass().addAll("action-btn", "btn-success");
        playPauseBtn.setAccessibleText("Toggle continuous simulation execution");
        playPauseBtn.setTooltip(new Tooltip("Start or pause live simulation movement animation"));
        playPauseBtn.setOnAction(e -> model.togglePlayPause());

        stepBtn = new Button("⏭ Step");
        stepBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        stepBtn.setAccessibleText("Step forward in simulation time");
        stepBtn.setTooltip(new Tooltip("Step simulation forward by selected duration"));
        stepBtn.setOnAction(e -> executeSelectedStep());

        stepDurationCombo = new ComboBox<>();
        stepDurationCombo.getItems().addAll("+10s", "+30s", "+1m", "+5m");
        stepDurationCombo.setValue("+30s");
        stepDurationCombo.getStyleClass().add("toolbar-combo");
        stepDurationCombo.setAccessibleText("Single-step time advancement quantum");
        stepDurationCombo.setTooltip(new Tooltip("Select duration for each step"));

        speedCombo = new ComboBox<>();
        speedCombo.getItems().addAll("0.25x (Slow)", "0.5x", "1.0x (Normal)", "2.0x (Fast)", "4.0x (Turbo)");
        speedCombo.setValue("1.0x (Normal)");
        speedCombo.getStyleClass().add("toolbar-combo");
        speedCombo.setAccessibleText("Animation playback speed multiplier");
        speedCombo.setTooltip(new Tooltip("Adjust vehicle animation playback speed"));
        speedCombo.setOnAction(e -> handleSpeedSelection());

        resetBtn = new Button("🔄 Reset");
        resetBtn.getStyleClass().addAll("action-btn", "btn-danger");
        resetBtn.setAccessibleText("Reset simulation scenario");
        resetBtn.setTooltip(new Tooltip("Reset simulation and vehicle movements back to origin"));
        resetBtn.setOnAction(e -> {
            boolean confirmed = UiDialogs.confirm(
                    getScene() != null ? getScene().getWindow() : null,
                    "Confirm Simulation Reset",
                    "Reset Simulation & Vehicle Dispatches?",
                    "This action will reset the simulation clock, cancel in-progress transports, and restore all emergency vehicles to available status."
            );
            if (confirmed) {
                model.reset();
            }
        });

        // 2. Center: Canvas inside resizable Pane with Legend Overlay & Fallback Banner
        canvas = new RoadNetworkCanvas();
        canvas.setMapProvider(MapProvider.CARTO_DARK);
        canvas.getTileService().setOnStatusChangedListener(status -> updateMapStatusDisplay());

        // Wire canvas-dependent action handlers now that canvas is initialized
        mapProviderCombo.setOnAction(e -> {
            MapProvider selected = mapProviderCombo.getValue();
            if (selected != null) {
                canvas.setMapProvider(selected);
                updateMapStatusDisplay();
            }
        });
        tileStatusLabel.setOnMouseClicked(e -> {
            if (canvas.getTileService().getStatus() == OsmTileService.Status.OFFLINE_FALLBACK) {
                canvas.getTileService().retryConnection();
            }
        });

        canvasWrapper = new Pane(canvas);
        canvasWrapper.getStyleClass().add("map-canvas-container");

        // Bind canvas dimensions to wrapper pane
        canvas.widthProperty().bind(canvasWrapper.widthProperty());
        canvas.heightProperty().bind(canvasWrapper.heightProperty());

        // Legend Overlay (Semi-transparent top-left)
        VBox legendBox = createMapLegend();
        StackPane.setAlignment(legendBox, Pos.TOP_LEFT);
        StackPane.setMargin(legendBox, new Insets(12));

        // Offline Fallback Alert Banner (Centered top)
        fallbackBanner = new HBox(10);
        fallbackBanner.setAlignment(Pos.CENTER);
        fallbackBanner.setPadding(new Insets(8, 14, 8, 14));
        fallbackBanner.setStyle("-fx-background-color: rgba(185, 28, 28, 0.92); -fx-background-radius: 6; -fx-border-color: #ef4444; -fx-border-radius: 6; -fx-border-width: 1.5;");
        fallbackBanner.setMaxWidth(520);
        Label fallbackMsg = new Label("⚠️ Map Tiles Offline: Local Vector Grid Fallback Active");
        fallbackMsg.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11.5px;");
        Button retryBtn = new Button("🔄 Retry Connection");
        retryBtn.getStyleClass().addAll("action-btn", "mini-action-btn", "btn-secondary");
        retryBtn.setOnAction(e -> canvas.getTileService().retryConnection());
        fallbackBanner.getChildren().addAll(fallbackMsg, retryBtn);
        fallbackBanner.setVisible(false);
        fallbackBanner.setManaged(false);
        StackPane.setAlignment(fallbackBanner, Pos.TOP_CENTER);
        StackPane.setMargin(fallbackBanner, new Insets(12));

        StackPane mapStack = new StackPane(canvasWrapper, legendBox, fallbackBanner);
        HBox.setHgrow(mapStack, Priority.ALWAYS);

        HBox toolbar = createLiveSimulationToolbar();
        topContainer.getChildren().addAll(header, toolbar);
        setTop(topContainer);

        // 3. Right Sidebar: Tabs for Route Planning, Fleet, Inspector, and Incidents
        TabPane inspectorTabs = new TabPane();
        inspectorTabs.setPrefWidth(370);
        inspectorTabs.setMinWidth(340);
        inspectorTabs.setMaxWidth(400);
        inspectorTabs.getStyleClass().add("map-inspector-tabs");

        // Tab 1: Route Planning
        VBox routePanel = new VBox(12);
        routePanel.setPadding(new Insets(12));
        routePanel.getStyleClass().add("dashboard-card-section");

        sourceCombo = new ComboBox<>();
        sourceCombo.setMaxWidth(Double.MAX_VALUE);
        sourceCombo.setPromptText("Select Origin Node");
        sourceCombo.getStyleClass().add("toolbar-combo");

        destCombo = new ComboBox<>();
        destCombo.setMaxWidth(Double.MAX_VALUE);
        destCombo.setPromptText("Select Destination Node");
        destCombo.getStyleClass().add("toolbar-combo");

        algoCombo = new ComboBox<>();
        algoCombo.getItems().addAll("Dijkstra (Fastest Path)", "A* Algorithm (Haversine)",
                "Yen's K-Shortest (3 Alternatives)");
        algoCombo.setValue("Dijkstra (Fastest Path)");
        algoCombo.setMaxWidth(Double.MAX_VALUE);
        algoCombo.getStyleClass().add("toolbar-combo");

        calculateRouteBtn = new Button("🧭 Calculate Route");
        calculateRouteBtn.setMaxWidth(Double.MAX_VALUE);
        calculateRouteBtn.getStyleClass().addAll("action-btn", "btn-primary");
        calculateRouteBtn.setOnAction(e -> executeRouteCalculation());

        clearRouteBtn = new Button("Clear Route");
        clearRouteBtn.setMaxWidth(Double.MAX_VALUE);
        clearRouteBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        clearRouteBtn.setOnAction(e -> clearCurrentRoute());

        // Route details box
        VBox routeDetailsCard = new VBox(6);
        routeDetailsCard.setPadding(new Insets(10));
        routeDetailsCard.getStyleClass().add("route-details-card");

        routeStatusLabel = new Label("Route: No route active");
        routeStatusLabel.getStyleClass().add("card-section-title");

        routeDistanceLabel = new Label("Distance: —");
        routeDistanceLabel.getStyleClass().add("param-label");

        routeTimeLabel = new Label("Est. Travel Time: —");
        routeTimeLabel.getStyleClass().add("param-label");

        routeWaypointsLabel = new Label("Path: —");
        routeWaypointsLabel.getStyleClass().add("param-label");
        routeWaypointsLabel.setWrapText(true);

        alternativeRoutesBox = new VBox(4);

        routeDetailsCard.getChildren().addAll(routeStatusLabel, routeDistanceLabel, routeTimeLabel, routeWaypointsLabel,
                alternativeRoutesBox);

        routePanel.getChildren().addAll(
                new Label("Origin Intersection:"), sourceCombo,
                new Label("Destination Intersection:"), destCombo,
                new Label("Routing Strategy:"), algoCombo,
                calculateRouteBtn, clearRouteBtn,
                new Separator(),
                routeDetailsCard);
        Tab routeTab = new Tab("🧭 Route Planner", routePanel);
        routeTab.setClosable(false);

        // Tab 2: Live Fleet & Mission Telemetry
        VBox fleetPanel = new VBox(10);
        fleetPanel.setPadding(new Insets(12));
        fleetPanel.getStyleClass().add("dashboard-card-section");

        HBox fleetHeader = new HBox(8);
        fleetHeader.setAlignment(Pos.CENTER_LEFT);
        Label fleetTitle = new Label("Ambulance Fleet Status");
        fleetTitle.getStyleClass().add("card-section-title");
        fleetCountLabel = new Label("(0 Active)");
        fleetCountLabel.getStyleClass().add("card-section-subtitle");
        fleetHeader.getChildren().addAll(fleetTitle, fleetCountLabel);

        fleetCardsContainer = new VBox(8);
        ScrollPane fleetScroll = new ScrollPane(fleetCardsContainer);
        fleetScroll.setFitToWidth(true);
        fleetScroll.setPrefHeight(450);
        VBox.setVgrow(fleetScroll, Priority.ALWAYS);

        fleetPanel.getChildren().addAll(fleetHeader, fleetScroll);
        Tab fleetTab = new Tab("🚑 Fleet & Missions", fleetPanel);
        fleetTab.setClosable(false);

        // Tab 3: Entity & Road Inspector
        VBox inspectorPanel = new VBox(12);
        inspectorPanel.setPadding(new Insets(12));
        inspectorPanel.getStyleClass().add("dashboard-card-section");

        inspectorTitleLabel = new Label("Select Node, Road, or Ambulance");
        inspectorTitleLabel.getStyleClass().add("card-section-title");

        inspectorDetailsLabel = new Label(
                "Click any vehicle, intersection, or road corridor on the map to inspect live telemetry, operational state, and dynamic route data.");
        inspectorDetailsLabel.getStyleClass().add("param-label");
        inspectorDetailsLabel.setWrapText(true);

        setAsSourceBtn = new Button("Set as Origin (A)");
        setAsSourceBtn.setMaxWidth(Double.MAX_VALUE);
        setAsSourceBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        setAsSourceBtn.setDisable(true);
        setAsSourceBtn.setOnAction(e -> {
            if (currentlySelectedNode != null) {
                sourceCombo.setValue(currentlySelectedNode.getId());
                updateWaypointsOnCanvas();
            }
        });

        setAsDestBtn = new Button("Set as Destination (B)");
        setAsDestBtn.setMaxWidth(Double.MAX_VALUE);
        setAsDestBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        setAsDestBtn.setDisable(true);
        setAsDestBtn.setOnAction(e -> {
            if (currentlySelectedNode != null) {
                destCombo.setValue(currentlySelectedNode.getId());
                updateWaypointsOnCanvas();
            }
        });

        toggleRoadClosureBtn = new Button("⛔ Toggle Road Closure");
        toggleRoadClosureBtn.setMaxWidth(Double.MAX_VALUE);
        toggleRoadClosureBtn.getStyleClass().addAll("action-btn", "btn-danger");
        toggleRoadClosureBtn.setDisable(true);
        toggleRoadClosureBtn.setOnAction(e -> toggleSelectedRoadClosure());

        sendToBaseBtn = new Button("🏠 Order Return to Base");
        sendToBaseBtn.setMaxWidth(Double.MAX_VALUE);
        sendToBaseBtn.getStyleClass().addAll("action-btn", "btn-primary");
        sendToBaseBtn.setDisable(true);
        sendToBaseBtn.setOnAction(e -> {
            if (currentlySelectedAmbulance != null) {
                model.returnAmbulanceToBase(currentlySelectedAmbulance.ambulanceId());
            }
        });

        inspectorPanel.getChildren().addAll(
                inspectorTitleLabel, inspectorDetailsLabel,
                new Separator(),
                setAsSourceBtn, setAsDestBtn,
                new Separator(),
                toggleRoadClosureBtn,
                sendToBaseBtn);
        Tab inspectorTab = new Tab("🔍 Inspector", inspectorPanel);
        inspectorTab.setClosable(false);

        // Tab 4: Active Incidents
        VBox incidentsPanel = new VBox(10);
        incidentsPanel.setPadding(new Insets(12));
        incidentsPanel.getStyleClass().add("dashboard-card-section");

        Label incTitle = new Label("Active Road Closures");
        incTitle.getStyleClass().add("card-section-title");

        incidentsListView = new ListView<>();
        incidentsListView.setPrefHeight(250);
        incidentsListView.getStyleClass().add("table-view");

        Button reopenSelectedBtn = new Button("🔓 Reopen Selected Road");
        reopenSelectedBtn.setMaxWidth(Double.MAX_VALUE);
        reopenSelectedBtn.getStyleClass().addAll("action-btn", "btn-success");
        reopenSelectedBtn.setOnAction(e -> resolveSelectedIncident());

        incidentsPanel.getChildren().addAll(incTitle, incidentsListView, reopenSelectedBtn);
        Tab incidentsTab = new Tab("⚠️ Incidents", incidentsPanel);
        incidentsTab.setClosable(false);

        inspectorTabs.getTabs().addAll(routeTab, fleetTab, inspectorTab, incidentsTab);

        // Assemble main split layout
        HBox mainLayout = new HBox(14, mapStack, inspectorTabs);
        VBox.setVgrow(mainLayout, Priority.ALWAYS);
        setCenter(mainLayout);

        // Wire canvas selection callbacks
        canvas.setOnNodeSelected(this::onNodeClicked);
        canvas.setOnEdgeSelected(this::onEdgeClicked);
        canvas.setOnAmbulanceSelected(this::onAmbulanceClicked);
        canvas.setOnHospitalSelected(this::onHospitalClicked);

        // Wire model updates
        model.addStateChangeListener(m -> refresh());
        refresh();
        updateMapStatusDisplay();
    }

    private HBox createLiveSimulationToolbar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.getStyleClass().add("control-toolbar-panel");

        // Simulation status and time
        bar.getChildren().addAll(statusPillLabel, timeReadoutLabel);

        // Play/Pause, Step, Speed, Reset
        bar.getChildren().addAll(playPauseBtn, stepBtn, stepDurationCombo, speedCombo, resetBtn);

        Separator sep = new Separator();
        sep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        bar.getChildren().add(sep);

        // Canvas View Navigation
        Button zoomInBtn = new Button("➕");
        zoomInBtn.setTooltip(new Tooltip("Zoom In"));
        zoomInBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        zoomInBtn.setOnAction(e -> {
            canvas.getTransform().zoomAt(1.25, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
            canvas.redraw();
        });

        Button zoomOutBtn = new Button("➖");
        zoomOutBtn.setTooltip(new Tooltip("Zoom Out"));
        zoomOutBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        zoomOutBtn.setOnAction(e -> {
            canvas.getTransform().zoomAt(0.8, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
            canvas.redraw();
        });

        Button fitBtn = new Button("⛶ Fit");
        fitBtn.setTooltip(new Tooltip("Fit Network to Screen"));
        fitBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        fitBtn.setOnAction(e -> canvas.fitToNetwork());

        Button resetViewBtn = new Button("🔄 Center");
        resetViewBtn.setTooltip(new Tooltip("Reset Pan & Zoom"));
        resetViewBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        resetViewBtn.setOnAction(e -> {
            canvas.getTransform().resetView();
            canvas.redraw();
        });

        bar.getChildren().addAll(zoomInBtn, zoomOutBtn, fitBtn, resetViewBtn);

        Separator sepMap = new Separator();
        sepMap.setOrientation(javafx.geometry.Orientation.VERTICAL);
        Label mapLabel = new Label("🗺️ Layer:");
        mapLabel.getStyleClass().add("toolbar-label");
        bar.getChildren().addAll(sepMap, mapLabel, mapProviderCombo, tileStatusLabel);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().add(spacer);

        Label hintLabel = new Label("💡 Drag to Pan • Scroll to Zoom • Double-click to Fit");
        hintLabel.getStyleClass().add("toolbar-label");
        bar.getChildren().add(hintLabel);

        return bar;
    }

    private void executeSelectedStep() {
        String val = stepDurationCombo.getValue();
        Duration step = switch (val) {
            case "+10s" -> Duration.ofSeconds(10);
            case "+30s" -> Duration.ofSeconds(30);
            case "+1m" -> Duration.ofMinutes(1);
            case "+5m" -> Duration.ofMinutes(5);
            default -> Duration.ofSeconds(30);
        };
        model.step(step);
    }

    private void handleSpeedSelection() {
        String val = speedCombo.getValue();
        if (val != null) {
            if (val.startsWith("0.25x"))
                model.setAutoRunIntervalMs(1200);
            else if (val.startsWith("0.5x"))
                model.setAutoRunIntervalMs(800);
            else if (val.startsWith("1.0x"))
                model.setAutoRunIntervalMs(400);
            else if (val.startsWith("2.0x"))
                model.setAutoRunIntervalMs(200);
            else if (val.startsWith("4.0x"))
                model.setAutoRunIntervalMs(100);
        }
    }

    private VBox createMapLegend() {
        VBox legend = new VBox(4);
        legend.setPadding(new Insets(8, 12, 8, 12));
        legend.getStyleClass().add("map-legend-overlay");
        legend.setMaxWidth(190);

        Label title = new Label("🗺️ Map & Unit Legend");
        title.getStyleClass().add("legend-title");

        legend.getChildren().addAll(
                title,
                createLegendItem("🚑", "#10b981", "Available Unit"),
                createLegendItem("🚑", "#00b4d8", "Responding to Scene"),
                createLegendItem("🚑", "#a855f7", "On Scene (Treating)"),
                createLegendItem("🚑", "#f59e0b", "Transporting to Hosp"),
                createLegendItem("🚑", "#06b6d4", "Returning to Base"),
                createLegendItem("- -", "#ef4444", "Road Closed (⛔)"),
                createLegendItem("══", "#38bdf8", "Active Transit Path"),
                createLegendItem("══", "#10b981", "Optimal Route (A*)"),
                createLegendItem("═ ═", "#a855f7", "Alternative Route (Yen)"),
                createLegendItem("🏥", "#10b981", "Hospital Facility"),
                createLegendItem("🚨", "#ef4444", "Active Emergency"));
        return legend;
    }

    private HBox createLegendItem(String symbol, String colorHex, String label) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        Label sym = new Label(symbol);
        sym.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");

        Label text = new Label(label);
        text.setStyle("-fx-font-size: 10px; -fx-text-fill: #cbd5e1;");

        row.getChildren().addAll(sym, text);
        return row;
    }

    private void onHospitalClicked(Hospital hosp) {
        this.currentlySelectedHospital = hosp;
        this.currentlySelectedNode = null;
        this.currentlySelectedEdge = null;
        this.currentlySelectedAmbulance = null;

        if (hosp != null) {
            inspectorTitleLabel.setText("🏥 " + hosp.getName());
            setAsSourceBtn.setDisable(true);
            setAsDestBtn.setDisable(false);
            setAsDestBtn.setText("Set as Destination (B)");
            setAsDestBtn.setOnAction(e -> {
                destCombo.setValue(hosp.getLocationNodeId());
                updateWaypointsOnCanvas();
            });
            toggleRoadClosureBtn.setDisable(true);
            sendToBaseBtn.setDisable(true);

            StringBuilder sb = new StringBuilder();
            sb.append("Facility ID: ").append(hosp.getId()).append("\n");
            sb.append("Location Node: ").append(hosp.getLocationNodeId()).append("\n");
            double util = hosp.getTotalCapacity() > 0
                    ? ((double) hosp.getOccupiedCapacity() / hosp.getTotalCapacity() * 100.0)
                    : 0.0;
            sb.append(String.format("Bed Capacity: %d / %d occupied (%d available)\n",
                    hosp.getOccupiedCapacity(), hosp.getTotalCapacity(), hosp.getAvailableCapacity()));
            sb.append(String.format("Capacity Utilization: %.1f%%\n\n", util));

            sb.append("Supported Emergency Types:\n");
            for (EmergencyType type : hosp.getSupportedEmergencyTypes()) {
                sb.append(" • ").append(type.displayName()).append("\n");
            }

            if (hosp.getCapabilities() != null && !hosp.getCapabilities().isEmpty()) {
                sb.append("\nSpecialized Clinical Capabilities:\n");
                for (HospitalCapability cap : hosp.getCapabilities()) {
                    sb.append(" • ").append(cap.name().replace('_', ' ')).append("\n");
                }
            }

            inspectorDetailsLabel.setText(sb.toString());
        } else {
            resetInspector();
        }
    }

    private void onNodeClicked(Node node) {
        this.currentlySelectedNode = node;
        this.currentlySelectedHospital = null;
        this.currentlySelectedEdge = null;
        this.currentlySelectedAmbulance = null;

        if (node != null) {
            inspectorTitleLabel.setText("📍 Intersection: " + node.getId());
            setAsSourceBtn.setDisable(false);
            setAsSourceBtn.setText("Set as Origin (A)");
            setAsSourceBtn.setOnAction(e -> {
                if (currentlySelectedNode != null) {
                    sourceCombo.setValue(currentlySelectedNode.getId());
                    updateWaypointsOnCanvas();
                }
            });
            setAsDestBtn.setDisable(false);
            setAsDestBtn.setText("Set as Destination (B)");
            setAsDestBtn.setOnAction(e -> {
                if (currentlySelectedNode != null) {
                    destCombo.setValue(currentlySelectedNode.getId());
                    updateWaypointsOnCanvas();
                }
            });
            toggleRoadClosureBtn.setDisable(true);
            sendToBaseBtn.setDisable(true);

            StringBuilder sb = new StringBuilder();
            sb.append("Name: ").append(node.getName()).append("\n");
            sb.append(String.format("Coordinates: %.5f, %.5f\n\n", node.getCoordinates().latitude(),
                    node.getCoordinates().longitude()));

            Optional<RoadGraph> graphOpt = model.getLiveGraph();
            if (graphOpt.isPresent()) {
                RoadGraph g = graphOpt.get();
                List<Edge> edges = g.getOutgoingEdges(node.getId());
                sb.append("Outgoing Roads (").append(edges.size()).append("):\n");
                for (Edge e : edges) {
                    sb.append(" • ").append(e.getRoadName()).append(" -> ").append(e.getDestination().getId())
                            .append(e.isAvailable() ? " [Open]" : " [CLOSED]")
                            .append("\n");
                }
            }

            long ambCount = model.getAmbulances().stream()
                    .filter(a -> a.getCurrentLocationNodeId().equals(node.getId())).count();
            if (ambCount > 0) {
                sb.append("\nAmbulances Stationed: ").append(ambCount).append(" units\n");
            }

            inspectorDetailsLabel.setText(sb.toString());
        } else {
            resetInspector();
        }
    }

    private void updateMapStatusDisplay() {
        if (canvas == null) return;
        if (canvas.getMapProvider() == MapProvider.OFFLINE_VECTOR) {
            tileStatusLabel.setText("📐 Vector Mode");
            tileStatusLabel.setStyle("-fx-text-fill: #94a3b8;");
            fallbackBanner.setVisible(false);
            fallbackBanner.setManaged(false);
            return;
        }

        OsmTileService.Status status = canvas.getTileService().getStatus();
        switch (status) {
            case ONLINE -> {
                tileStatusLabel.setText("🟢 OSM Live");
                tileStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-cursor: default;");
                fallbackBanner.setVisible(false);
                fallbackBanner.setManaged(false);
            }
            case LOADING -> {
                tileStatusLabel.setText("🔄 Loading...");
                tileStatusLabel.setStyle("-fx-text-fill: #38bdf8; -fx-cursor: default;");
            }
            case OFFLINE_FALLBACK -> {
                tileStatusLabel.setText("⚠️ Tiles Offline");
                tileStatusLabel.setStyle("-fx-text-fill: #ef4444; -fx-cursor: hand;");
                fallbackBanner.setVisible(true);
                fallbackBanner.setManaged(true);
            }
            case DISABLED -> {
                tileStatusLabel.setText("📐 Vector Mode");
                tileStatusLabel.setStyle("-fx-text-fill: #94a3b8;");
                fallbackBanner.setVisible(false);
                fallbackBanner.setManaged(false);
            }
        }
    }

    private void onEdgeClicked(MapCoordinateTransform.EdgeMatch edgeMatch) {
        this.currentlySelectedEdge = edgeMatch;
        this.currentlySelectedNode = null;
        this.currentlySelectedAmbulance = null;

        if (edgeMatch != null) {
            Edge edge = edgeMatch.edge();
            String src = edgeMatch.sourceNodeId();
            String dst = edge.getDestination().getId();

            inspectorTitleLabel.setText("🛣️ Road: " + edge.getRoadName());
            setAsSourceBtn.setDisable(true);
            setAsDestBtn.setDisable(true);
            toggleRoadClosureBtn.setDisable(false);
            sendToBaseBtn.setDisable(true);

            boolean isAvailable = edge.isAvailable();
            toggleRoadClosureBtn.setText(isAvailable ? "⛔ Close Road Corridor" : "🔓 Reopen Road Corridor");

            StringBuilder sb = new StringBuilder();
            sb.append("Direction: ").append(src).append(" ➔ ").append(dst).append("\n");
            sb.append(String.format("Distance: %.2f km\n", edge.getDistanceKm()));
            sb.append(String.format("Base Travel Time: %.2f min\n", edge.getBaseTravelTimeMinutes()));
            sb.append(String.format("Current Travel Time: %.2f min\n", edge.getTravelTimeMinutes()));
            sb.append("Traffic State: ")
                    .append(edge.getTrafficCondition() != null ? edge.getTrafficCondition().level() : "FREE_FLOW")
                    .append("\n");
            sb.append("Status: ").append(isAvailable ? "🟢 OPEN / PASSABLE" : "🔴 CLOSED (INCIDENT)").append("\n");

            inspectorDetailsLabel.setText(sb.toString());
        } else {
            resetInspector();
        }
    }

    private void onAmbulanceClicked(AmbulanceMovementTelemetry amb) {
        this.currentlySelectedAmbulance = amb;
        this.currentlySelectedNode = null;
        this.currentlySelectedEdge = null;

        if (amb != null) {
            inspectorTitleLabel.setText("🚑 Ambulance: " + amb.ambulanceId());
            setAsSourceBtn.setDisable(true);
            setAsDestBtn.setDisable(true);
            toggleRoadClosureBtn.setDisable(true);
            sendToBaseBtn.setDisable(!amb.ambulanceStatus().isAvailable());

            StringBuilder sb = new StringBuilder();
            sb.append("Operational Status: ").append(amb.ambulanceStatus()).append("\n");
            sb.append("Visual Presentation: ").append(amb.visualState()).append("\n");
            sb.append("Current Location: ").append(amb.sourceNodeId()).append("\n");
            sb.append("Next Waypoint: ").append(amb.targetNodeId()).append("\n");
            sb.append("Final Destination: ").append(amb.destinationName()).append("\n");
            sb.append(String.format("Coordinates: %s\n", amb.getFormattedCoordinates()));
            sb.append(String.format("Progress: %d%% (%s)\n", amb.getProgressPercentage(),
                    amb.isMoving() ? "In Transit" : "Stationary"));
            sb.append(String.format("Live ETA: %s\n", amb.getFormattedEta()));
            sb.append(String.format("Heading: %.1f°\n", amb.headingDegrees()));
            sb.append("Blocked Route: ").append(amb.isBlocked() ? "⚠️ YES (Detour Blocked)" : "NO (Clear)")
                    .append("\n");

            if (amb.caseId() != null) {
                sb.append("Active Emergency Case: ").append(amb.caseId()).append("\n");
            }

            inspectorDetailsLabel.setText(sb.toString());
        } else {
            resetInspector();
        }
    }

    private void resetInspector() {
        inspectorTitleLabel.setText("Select Node, Road, or Ambulance");
        inspectorDetailsLabel.setText(
                "Click any vehicle, intersection, or road corridor on the map to inspect live telemetry, operational state, and dynamic route data.");
        setAsSourceBtn.setDisable(true);
        setAsDestBtn.setDisable(true);
        toggleRoadClosureBtn.setDisable(true);
        sendToBaseBtn.setDisable(true);
    }

    private void toggleSelectedRoadClosure() {
        if (currentlySelectedEdge == null)
            return;
        Edge edge = currentlySelectedEdge.edge();
        String src = currentlySelectedEdge.sourceNodeId();
        String dst = edge.getDestination().getId();

        if (edge.isAvailable()) {
            boolean confirmed = UiDialogs.confirm(
                    getScene() != null ? getScene().getWindow() : null,
                    "Confirm Road Corridor Closure",
                    "Close Road Segment: " + src + " ➔ " + dst + "?",
                    "Closing this road corridor will block traffic, update travel times to infinity, and trigger dynamic rerouting for all active emergency vehicles navigating this path."
            );
            if (!confirmed) return;
            model.reportRoadClosure(src, dst, "Manual dispatcher closure");
        } else {
            // Reopen road incident
            model.getActiveIncidents().stream()
                    .filter(inc -> inc.getSourceNodeId().equals(src) && inc.getDestinationNodeId().equals(dst))
                    .findFirst()
                    .ifPresent(inc -> model.resolveRoadIncident(inc.getId()));
        }
        refresh();
    }

    private void resolveSelectedIncident() {
        String selected = incidentsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            model.publishMessage(UiMessage.warning("Select an incident to reopen."));
            return;
        }
        String incidentId = selected.split("\\|")[0].trim();
        model.resolveRoadIncident(incidentId);
        refresh();
    }

    private void updateWaypointsOnCanvas() {
        canvas.setRouteWaypoints(sourceCombo.getValue(), destCombo.getValue());
    }

    private void executeRouteCalculation() {
        String src = sourceCombo.getValue();
        String dst = destCombo.getValue();
        String algo = algoCombo.getValue();

        if (src == null || dst == null) {
            model.publishMessage(UiMessage.warning("Please select both origin and destination nodes."));
            return;
        }

        if (src.equals(dst)) {
            model.publishMessage(UiMessage.warning("Origin and destination must be different nodes."));
            return;
        }

        calculateRouteBtn.setDisable(true);
        calculateRouteBtn.setText("⏳ Calculating...");

        CompletableFuture.supplyAsync(() -> {
            if (algo != null && algo.startsWith("Yen")) {
                return model.calculateAlternativeRoutes(src, dst, 3);
            } else {
                String algoCode = (algo != null && algo.startsWith("A*")) ? "A_STAR" : "DIJKSTRA";
                RouteResult r = model.calculateRoute(src, dst, algoCode);
                return List.of(r);
            }
        }).thenAcceptAsync(routes -> {
            calculateRouteBtn.setDisable(false);
            calculateRouteBtn.setText("🧭 Calculate Route");

            if (routes.isEmpty() || !routes.get(0).isReachable()) {
                displayUnreachableRoute(src, dst);
                return;
            }

            RouteResult primary = routes.get(0);
            canvas.setPrimaryRoute(primary);

            if (routes.size() > 1) {
                canvas.setAlternativeRoutes(routes.subList(1, routes.size()));
                displayAlternativesSummary(routes);
            } else {
                canvas.setAlternativeRoutes(List.of());
                alternativeRoutesBox.getChildren().clear();
            }

            displayRouteDetails(primary);
            model.publishMessage(UiMessage.success("Optimal route computed successfully: " + src + " -> " + dst));
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                calculateRouteBtn.setDisable(false);
                calculateRouteBtn.setText("🧭 Calculate Route");
                displayUnreachableRoute(src, dst);
                model.publishMessage(UiMessage.error("Route calculation error: " + ex.getMessage()));
            });
            return null;
        });
    }

    private void displayRouteDetails(RouteResult route) {
        routeStatusLabel.setText("✅ Route Found (Optimal)");
        routeStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");

        routeDistanceLabel.setText(String.format("Distance: %.2f km", route.getTotalDistanceKm()));
        routeTimeLabel.setText(String.format("Est. Travel Time: %.2f min", route.getTotalTravelTimeMinutes()));

        StringBuilder pathBuilder = new StringBuilder("Path: ");
        List<Node> nodes = route.getPath();
        for (int i = 0; i < nodes.size(); i++) {
            pathBuilder.append(nodes.get(i).getId());
            if (i < nodes.size() - 1)
                pathBuilder.append(" ➔ ");
        }
        routeWaypointsLabel.setText(pathBuilder.toString());
    }

    private void displayAlternativesSummary(List<RouteResult> routes) {
        alternativeRoutesBox.getChildren().clear();
        Label altTitle = new Label("Alternative Routes (Yen's K):");
        altTitle.setStyle("-fx-text-fill: #a855f7; -fx-font-weight: bold; -fx-font-size: 11px;");
        alternativeRoutesBox.getChildren().add(altTitle);

        for (int i = 1; i < routes.size(); i++) {
            RouteResult alt = routes.get(i);
            Label l = new Label(String.format(" • Alt #%d: %.2f min (%.2f km)", i, alt.getTotalTravelTimeMinutes(),
                    alt.getTotalDistanceKm()));
            l.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
            alternativeRoutesBox.getChildren().add(l);
        }
    }

    private void displayUnreachableRoute(String src, String dst) {
        canvas.clearRoutes();
        routeStatusLabel.setText("❌ No Reachable Route");
        routeStatusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        routeDistanceLabel.setText("Distance: Unreachable");
        routeTimeLabel.setText("Est. Travel Time: Infinity");
        routeWaypointsLabel.setText("No path exists between " + src + " and " + dst + " (check road closures).");
        alternativeRoutesBox.getChildren().clear();
    }

    private void clearCurrentRoute() {
        canvas.clearRoutes();
        routeStatusLabel.setText("Route: Cleared");
        routeStatusLabel.setStyle("-fx-text-fill: #94a3b8;");
        routeDistanceLabel.setText("Distance: —");
        routeTimeLabel.setText("Est. Travel Time: —");
        routeWaypointsLabel.setText("Path: —");
        alternativeRoutesBox.getChildren().clear();
    }

    public void refresh() {
        // 1. Update Simulation Status Pill & Time Badge
        UiSimulationState uiState = model.getUiState();
        statusPillLabel.setText(uiState.name());
        statusPillLabel.getStyleClass().removeAll("sim-status-running", "sim-status-paused", "sim-status-completed",
                "sim-status-idle");

        boolean isRunning = (uiState == UiSimulationState.RUNNING);
        stepBtn.setDisable(isRunning);
        stepDurationCombo.setDisable(isRunning);

        switch (uiState) {
            case RUNNING -> {
                statusPillLabel.getStyleClass().add("sim-status-running");
                playPauseBtn.setText("⏸ Pause");
                playPauseBtn.getStyleClass().removeAll("btn-success");
                playPauseBtn.getStyleClass().add("btn-warning");
            }
            case PAUSED -> {
                statusPillLabel.getStyleClass().add("sim-status-paused");
                playPauseBtn.setText("▶ Resume");
                playPauseBtn.getStyleClass().removeAll("btn-warning");
                playPauseBtn.getStyleClass().add("btn-success");
            }
            case COMPLETED -> {
                statusPillLabel.getStyleClass().add("sim-status-completed");
                playPauseBtn.setText("▶ Play");
                playPauseBtn.getStyleClass().removeAll("btn-warning");
                playPauseBtn.getStyleClass().add("btn-success");
            }
            default -> {
                statusPillLabel.getStyleClass().add("sim-status-idle");
                playPauseBtn.setText("▶ Play");
                playPauseBtn.getStyleClass().removeAll("btn-warning");
                playPauseBtn.getStyleClass().add("btn-success");
            }
        }

        String maxDuration = model.getScenario()
                .map(sc -> SimulationPresentationModel.formatDuration(sc.getSimulationDuration()))
                .orElse("00:30:00");
        timeReadoutLabel.setText(String.format("⏱ %s / %s", model.getFormattedElapsedTime(), maxDuration));

        // 2. Update Canvas Data
        Optional<RoadGraph> graphOpt = model.getLiveGraph();
        if (graphOpt.isPresent()) {
            RoadGraph graph = graphOpt.get();

            List<String> nodeIds = graph.getAllNodes().stream().map(Node::getId).sorted().toList();
            if (!nodeIds.equals(sourceCombo.getItems())) {
                String prevSrc = sourceCombo.getValue();
                String prevDst = destCombo.getValue();
                sourceCombo.getItems().setAll(nodeIds);
                destCombo.getItems().setAll(nodeIds);
                if (nodeIds.contains(prevSrc))
                    sourceCombo.setValue(prevSrc);
                if (nodeIds.contains(prevDst))
                    destCombo.setValue(prevDst);
            }

            canvas.setNetworkData(graph, model.getAmbulances(), model.getHospitals(), model.getActiveEmergencies());
            canvas.setTelemetryData(model.getAmbulanceTelemetry(), model.getActiveMissionRoutes());

            // 3. Update Fleet Cards
            updateFleetCards();

            // 4. Update Incidents List
            List<RoadIncident> activeIncidents = model.getActiveIncidents();
            List<String> incStrings = activeIncidents.stream()
                    .map(inc -> inc.getId() + " | " + inc.getSourceNodeId() + " ➔ " + inc.getDestinationNodeId())
                    .toList();
            incidentsListView.getItems().setAll(incStrings);

            // 5. Keep currently selected ambulance updated in Inspector
            if (currentlySelectedAmbulance != null) {
                model.getAmbulanceTelemetry().stream()
                        .filter(t -> t.ambulanceId().equals(currentlySelectedAmbulance.ambulanceId()))
                        .findFirst()
                        .ifPresent(this::onAmbulanceClicked);
            }
        } else {
            canvas.setNetworkData(null, List.of(), List.of(), List.of());
            canvas.setTelemetryData(List.of(), List.of());
            fleetCardsContainer.getChildren().clear();
            incidentsListView.getItems().clear();
        }
    }

    private void updateFleetCards() {
        fleetCardsContainer.getChildren().clear();
        List<AmbulanceMovementTelemetry> telemetryList = model.getAmbulanceTelemetry();

        long activeCount = telemetryList.stream().filter(AmbulanceMovementTelemetry::isMoving).count();
        fleetCountLabel.setText(String.format("(%d en route / %d total)", activeCount, telemetryList.size()));

        for (AmbulanceMovementTelemetry amb : telemetryList) {
            VBox card = new VBox(6);
            card.getStyleClass().add("fleet-card");

            HBox cardTop = new HBox(8);
            cardTop.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label("🚑 " + amb.ambulanceId());
            title.getStyleClass().add("fleet-card-title");

            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            StatusBadge badge = StatusBadge.ofAmbulanceStatus(amb.ambulanceStatus());
            cardTop.getChildren().addAll(title, spacer, badge);

            Label destLabel = new Label("🎯 " + amb.destinationName());
            destLabel.getStyleClass().add("fleet-card-sub");

            Label etaLabel = new Label(
                    "⏱ " + (amb.isMoving() ? "ETA: " + amb.getFormattedEta() : "Stationary at " + amb.sourceNodeId()));
            etaLabel.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #38bdf8;");

            // Progress bar
            ProgressBar progressBar = new ProgressBar(amb.routeProgress());
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.getStyleClass().add("capacity-bar");

            HBox actions = new HBox(6);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button trackBtn = new Button("🎯 Track");
            trackBtn.getStyleClass().addAll("action-btn", "mini-action-btn", "btn-secondary");
            trackBtn.setOnAction(e -> {
                canvas.setSelectedAmbulanceId(amb.ambulanceId());
                canvas.getTransform().centerAt(amb.currentCoordinates());
                canvas.redraw();
                onAmbulanceClicked(amb);
            });

            Button returnBtn = new Button("🏠 Base");
            returnBtn.getStyleClass().addAll("action-btn", "mini-action-btn", "btn-primary");
            returnBtn.setDisable(!amb.ambulanceStatus().isAvailable());
            returnBtn.setOnAction(e -> model.returnAmbulanceToBase(amb.ambulanceId()));

            actions.getChildren().addAll(trackBtn, returnBtn);

            card.getChildren().addAll(cardTop, destLabel, etaLabel, progressBar, actions);
            fleetCardsContainer.getChildren().add(card);
        }
    }

    public RoadNetworkCanvas getCanvas() {
        return canvas;
    }

    public ComboBox<MapProvider> getMapProviderCombo() {
        return mapProviderCombo;
    }

    public Label getTileStatusLabel() {
        return tileStatusLabel;
    }

    public HBox getFallbackBanner() {
        return fallbackBanner;
    }
}

