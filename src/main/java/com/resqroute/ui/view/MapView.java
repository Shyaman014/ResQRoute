package com.resqroute.ui.view;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.ui.component.EmptyStateView;
import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.component.StatCard;
import com.resqroute.ui.controller.SimulationPresentationModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Road network topology view detailing graph intersections, street segments, distance weights,
 * and previewing spatial map foundations for Phase 17.
 */
public class MapView extends ScrollPane {

    private final SimulationPresentationModel model;

    private final StatCard nodesCard;
    private final StatCard edgesCard;
    private final StatCard hospitalsCard;
    private final StatCard stationsCard;

    private final TableView<com.resqroute.model.Node> nodesTable;
    private final TableView<EdgeDisplayItem> edgesTable;

    public record EdgeDisplayItem(String sourceId, String destId, String roadName, double distanceKm, double travelTimeMinutes, boolean available) {}

    public MapView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setFitToWidth(true);
        getStyleClass().add("map-scroll-pane");

        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(24));
        contentBox.getStyleClass().add("map-container");

        // 1. Header
        SectionHeader header = new SectionHeader(
                "City Road Network & Spatial Topology",
                "Graph intersections, directed road segments, speed limits, and routing infrastructure"
        );
        contentBox.getChildren().add(header);

        // 2. Interactive Map Phase 17 Notice Banner
        HBox previewBanner = new HBox(12);
        previewBanner.setAlignment(Pos.CENTER_LEFT);
        previewBanner.setPadding(new Insets(14, 18, 14, 18));
        previewBanner.getStyleClass().add("map-preview-banner");

        Label mapIcon = new Label("🗺️");
        mapIcon.getStyleClass().add("preview-banner-icon");

        VBox bannerText = new VBox(3);
        Label bannerTitle = new Label("Interactive Visual Map Canvas scheduled for Phase 17");
        bannerTitle.getStyleClass().add("preview-banner-title");

        Label bannerDesc = new Label("The underlying road network graph is active and powering real-time Dijkstra, A*, and Yen's K-Shortest Paths routing. Inspect topology below.");
        bannerDesc.getStyleClass().add("preview-banner-desc");

        bannerText.getChildren().addAll(bannerTitle, bannerDesc);
        previewBanner.getChildren().addAll(mapIcon, bannerText);
        contentBox.getChildren().add(previewBanner);

        // 3. Topology Stats Grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(16);
        statsGrid.setVgap(16);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(25);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(25);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(25);
        ColumnConstraints col4 = new ColumnConstraints();
        col4.setPercentWidth(25);
        statsGrid.getColumnConstraints().addAll(col1, col2, col3, col4);

        nodesCard = new StatCard("📍", "Intersections", "0 Nodes", "Graph vertices", "card-accent-indigo");
        edgesCard = new StatCard("🛣️", "Road Segments", "0 Edges", "Directed graph edges", "card-accent-emerald");
        hospitalsCard = new StatCard("🏥", "Hospital Hubs", "0 Facilities", "Emergency intake locations", "card-accent-rose");
        stationsCard = new StatCard("🚑", "Depot Stations", "0 Depots", "Ambulance base stations", "card-accent-amber");

        statsGrid.add(nodesCard, 0, 0);
        statsGrid.add(edgesCard, 1, 0);
        statsGrid.add(hospitalsCard, 2, 0);
        statsGrid.add(stationsCard, 3, 0);
        contentBox.getChildren().add(statsGrid);

        // 4. Tables Split: Nodes & Edges
        GridPane tablesGrid = new GridPane();
        tablesGrid.setHgap(16);
        tablesGrid.setVgap(16);

        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(40);
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(60);
        tablesGrid.getColumnConstraints().addAll(leftCol, rightCol);

        // Left: Nodes Table
        VBox nodesBox = new VBox(8);
        nodesBox.getStyleClass().add("dashboard-card-section");
        nodesBox.setPadding(new Insets(14));
        Label nodesTitle = new Label("Graph Nodes (Intersections & Hubs)");
        nodesTitle.getStyleClass().add("card-section-title");
        nodesTable = createNodesTable();
        nodesBox.getChildren().addAll(nodesTitle, nodesTable);
        tablesGrid.add(nodesBox, 0, 0);

        // Right: Edges Table
        VBox edgesBox = new VBox(8);
        edgesBox.getStyleClass().add("dashboard-card-section");
        edgesBox.setPadding(new Insets(14));
        Label edgesTitle = new Label("Directed Road Corridors & Speed Limits");
        edgesTitle.getStyleClass().add("card-section-title");
        edgesTable = createEdgesTable();
        edgesBox.getChildren().addAll(edgesTitle, edgesTable);
        tablesGrid.add(edgesBox, 1, 0);

        contentBox.getChildren().add(tablesGrid);

        setContent(contentBox);

        // Wire model
        model.addStateChangeListener(m -> refresh());
        refresh();
    }

    private TableView<com.resqroute.model.Node> createNodesTable() {
        TableView<com.resqroute.model.Node> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new EmptyStateView("📍", "No Graph Loaded", "Initialize a city scenario to inspect network nodes."));

        TableColumn<com.resqroute.model.Node, String> idCol = new TableColumn<>("Node ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));
        idCol.setPrefWidth(90);

        TableColumn<com.resqroute.model.Node, String> nameCol = new TableColumn<>("Intersection Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        nameCol.setPrefWidth(140);

        TableColumn<com.resqroute.model.Node, String> coordsCol = new TableColumn<>("Coordinates (Lat, Lon)");
        coordsCol.setCellValueFactory(d -> {
            com.resqroute.model.Node n = d.getValue();
            return new SimpleStringProperty(String.format("%.4f, %.4f",
                    n.getCoordinates().latitude(), n.getCoordinates().longitude()));
        });
        coordsCol.setPrefWidth(140);

        t.getColumns().addAll(idCol, nameCol, coordsCol);
        return t;
    }

    private TableView<EdgeDisplayItem> createEdgesTable() {
        TableView<EdgeDisplayItem> t = new TableView<>();
        t.setPrefHeight(260);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new EmptyStateView("🛣️", "No Road Segments", "Initialize a city scenario to inspect road corridors."));

        TableColumn<EdgeDisplayItem, String> nameCol = new TableColumn<>("Road Corridor");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().roadName()));
        nameCol.setPrefWidth(140);

        TableColumn<EdgeDisplayItem, String> fromCol = new TableColumn<>("From");
        fromCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sourceId()));
        fromCol.setPrefWidth(90);

        TableColumn<EdgeDisplayItem, String> toCol = new TableColumn<>("To");
        toCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().destId()));
        toCol.setPrefWidth(90);

        TableColumn<EdgeDisplayItem, String> distCol = new TableColumn<>("Distance");
        distCol.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f km", d.getValue().distanceKm())));
        distCol.setPrefWidth(85);

        TableColumn<EdgeDisplayItem, String> timeCol = new TableColumn<>("Base Time");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.1f min", d.getValue().travelTimeMinutes())));
        timeCol.setPrefWidth(85);

        TableColumn<EdgeDisplayItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().available() ? "Open" : "Closed"));
        statusCol.setPrefWidth(75);

        t.getColumns().addAll(nameCol, fromCol, toCol, distCol, timeCol, statusCol);
        return t;
    }

    public void refresh() {
        model.getScenario().ifPresentOrElse(scenario -> {
            RoadGraph graph = scenario.getRoadGraph();
            nodesCard.update(graph.getNodeCount() + " Nodes", "Active network vertices");
            edgesCard.update(graph.getEdgeCount() + " Edges", "Directed road connections");
            hospitalsCard.update(scenario.getInitialHospitals().size() + " Facilities", "Regional medical centres");
            stationsCard.update(scenario.getInitialAmbulances().size() + " Bases", "Ambulance station nodes");

            // Update Nodes
            List<com.resqroute.model.Node> nodes = new ArrayList<>(graph.getAllNodes());
            nodesTable.getItems().setAll(nodes);

            // Update Edges
            List<EdgeDisplayItem> edgeItems = new ArrayList<>();
            for (com.resqroute.model.Node n : nodes) {
                for (Edge e : graph.getOutgoingEdges(n.getId())) {
                    edgeItems.add(new EdgeDisplayItem(
                            n.getId(),
                            e.getDestination().getId(),
                            e.getRoadName(),
                            e.getDistanceKm(),
                            e.getBaseTravelTimeMinutes(),
                            e.isAvailable()
                    ));
                }
            }
            edgesTable.getItems().setAll(edgeItems);
        }, () -> {
            nodesCard.update("0 Nodes", "No scenario active");
            edgesCard.update("0 Edges", "No scenario active");
            hospitalsCard.update("0 Facilities", "No scenario active");
            stationsCard.update("0 Bases", "No scenario active");
            nodesTable.getItems().clear();
            edgesTable.getItems().clear();
        });
    }
}
