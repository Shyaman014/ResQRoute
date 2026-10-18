package com.resqroute.ui.view;

import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.metrics.SimulationMetrics;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.ui.component.EmptyStateView;
import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.component.StatCard;
import com.resqroute.ui.component.UiDialogs;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.model.UiSimulationState;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Dedicated simulation execution console providing fine-grained time-step advancement,
 * scenario metadata, real-time telemetry metrics, and the full event log audit trail.
 */
public class SimulationView extends ScrollPane {

    private final SimulationPresentationModel model;

    // Controls
    private final Button playPauseBtn;
    private final Button step30sBtn;
    private final Button step1mBtn;
    private final Button step5mBtn;
    private final Button resetBtn;
    private final Button initBtn;
    private final ComboBox<String> speedCombo;

    // Info Labels
    private final Label scenarioNameVal = new Label("None");
    private final Label durationVal = new Label("—");
    private final Label timeStepVal = new Label("—");
    private final Label seedVal = new Label("—");
    private final Label nodesVal = new Label("—");
    private final Label edgesVal = new Label("—");

    // Metrics Stat Cards
    private final StatCard casesMetricsCard;
    private final StatCard latencyCard;
    private final StatCard responseTimeCard;
    private final StatCard utilizationCard;

    // Event Log Table
    private final TableView<SimulationEvent> eventTable;

    public SimulationView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setFitToWidth(true);
        getStyleClass().add("simulation-scroll-pane");

        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(24));
        contentBox.getStyleClass().add("simulation-container");

        // 1. Header
        SectionHeader header = new SectionHeader(
                "City Scenario Simulator",
                "Deterministic discrete-time execution engine and comprehensive telemetry analytics"
        );
        contentBox.getChildren().add(header);

        // 2. Control Toolbar Panel
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14));
        toolbar.getStyleClass().add("control-toolbar-panel");

        initBtn = new Button("🚀 Init Metropolis");
        initBtn.getStyleClass().addAll("action-btn", "btn-primary");
        initBtn.setAccessibleText("Initialize Default Metropolis Scenario");
        initBtn.setTooltip(new Tooltip("Initialize Metropolis scenario with complete fleet and road graph"));
        initBtn.setOnAction(e -> model.initializeDefaultScenario());

        playPauseBtn = new Button("▶ Play");
        playPauseBtn.getStyleClass().addAll("action-btn", "btn-success");
        playPauseBtn.setAccessibleText("Toggle continuous simulation execution");
        playPauseBtn.setTooltip(new Tooltip("Start or pause automatic simulation runner"));
        playPauseBtn.setOnAction(e -> model.togglePlayPause());

        step30sBtn = new Button("+30s");
        step30sBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        step30sBtn.setAccessibleText("Step forward 30 seconds");
        step30sBtn.setTooltip(new Tooltip("Advance simulation discrete clock by 30 seconds"));
        step30sBtn.setOnAction(e -> model.step(Duration.ofSeconds(30)));

        step1mBtn = new Button("+1m");
        step1mBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        step1mBtn.setAccessibleText("Step forward 1 minute");
        step1mBtn.setTooltip(new Tooltip("Advance simulation discrete clock by 1 minute"));
        step1mBtn.setOnAction(e -> model.step(Duration.ofMinutes(1)));

        step5mBtn = new Button("+5m");
        step5mBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        step5mBtn.setAccessibleText("Step forward 5 minutes");
        step5mBtn.setTooltip(new Tooltip("Advance simulation discrete clock by 5 minutes"));
        step5mBtn.setOnAction(e -> model.step(Duration.ofMinutes(5)));

        resetBtn = new Button("🔄 Reset");
        resetBtn.getStyleClass().addAll("action-btn", "btn-danger");
        resetBtn.setAccessibleText("Reset simulation scenario");
        resetBtn.setTooltip(new Tooltip("Reset simulation and fleet back to scenario origin"));
        resetBtn.setOnAction(e -> {
            boolean confirmed = UiDialogs.confirm(
                    getScene() != null ? getScene().getWindow() : null,
                    "Confirm Simulation Reset",
                    "Reset Simulation & Fleet Back to Origin?",
                    "This action will reset the simulation clock, cancel in-progress transports, and restore all emergency vehicles to available status."
            );
            if (confirmed) {
                model.reset();
            }
        });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label speedLabel = new Label("Auto Speed:");
        speedLabel.getStyleClass().add("toolbar-label");

        speedCombo = new ComboBox<>();
        speedCombo.getItems().addAll("Slow (1.2s)", "Normal (0.8s)", "Fast (0.3s)", "Turbo (0.1s)");
        speedCombo.setValue("Normal (0.8s)");
        speedCombo.getStyleClass().add("toolbar-combo");
        speedCombo.setAccessibleText("Simulation playback tick rate");
        speedCombo.setTooltip(new Tooltip("Adjust background simulation tick interval"));
        speedCombo.setOnAction(e -> {
            String val = speedCombo.getValue();
            if (val != null) {
                if (val.startsWith("Slow")) model.setAutoRunIntervalMs(1200);
                else if (val.startsWith("Normal")) model.setAutoRunIntervalMs(800);
                else if (val.startsWith("Fast")) model.setAutoRunIntervalMs(300);
                else if (val.startsWith("Turbo")) model.setAutoRunIntervalMs(100);
            }
        });

        toolbar.getChildren().addAll(initBtn, playPauseBtn, step30sBtn, step1mBtn, step5mBtn, resetBtn, spacer, speedLabel, speedCombo);
        contentBox.getChildren().add(toolbar);

        // 3. Grid: Scenario Info & Metrics
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        ColumnConstraints colLeft = new ColumnConstraints();
        colLeft.setPercentWidth(35);
        ColumnConstraints colRight = new ColumnConstraints();
        colRight.setPercentWidth(65);
        grid.getColumnConstraints().addAll(colLeft, colRight);

        // Left: Scenario Information
        VBox scenarioInfoCard = new VBox(10);
        scenarioInfoCard.getStyleClass().add("dashboard-card-section");
        scenarioInfoCard.setPadding(new Insets(16));

        Label infoTitle = new Label("Scenario Parameters");
        infoTitle.getStyleClass().add("card-section-title");

        GridPane paramGrid = new GridPane();
        paramGrid.setHgap(12);
        paramGrid.setVgap(8);

        paramGrid.add(createParamLabel("Scenario:"), 0, 0);
        paramGrid.add(scenarioNameVal, 1, 0);
        paramGrid.add(createParamLabel("Max Duration:"), 0, 1);
        paramGrid.add(durationVal, 1, 1);
        paramGrid.add(createParamLabel("Time Step:"), 0, 2);
        paramGrid.add(timeStepVal, 1, 2);
        paramGrid.add(createParamLabel("Random Seed:"), 0, 3);
        paramGrid.add(seedVal, 1, 3);
        paramGrid.add(createParamLabel("Road Nodes:"), 0, 4);
        paramGrid.add(nodesVal, 1, 4);
        paramGrid.add(createParamLabel("Road Edges:"), 0, 5);
        paramGrid.add(edgesVal, 1, 5);

        scenarioInfoCard.getChildren().addAll(infoTitle, paramGrid);
        grid.add(scenarioInfoCard, 0, 0);

        // Right: Telemetry Metrics Cards
        GridPane metricsCardsGrid = new GridPane();
        metricsCardsGrid.setHgap(12);
        metricsCardsGrid.setVgap(12);

        ColumnConstraints m1 = new ColumnConstraints();
        m1.setPercentWidth(50);
        ColumnConstraints m2 = new ColumnConstraints();
        m2.setPercentWidth(50);
        metricsCardsGrid.getColumnConstraints().addAll(m1, m2);

        casesMetricsCard = new StatCard("📊", "Cases Lifecycle", "0 Completed", "0 Reported • 0 Dispatched", "card-accent-emerald");
        latencyCard = new StatCard("⏱️", "Dispatch Latency", "0.0 min", "Average call to assignment", "card-accent-indigo");
        responseTimeCard = new StatCard("🎯", "Response Time", "0.0 min", "Average arrival at scene", "card-accent-rose");
        utilizationCard = new StatCard("⚡", "Fleet Utilization", "0.0%", "Mission deployment active time", "card-accent-amber");

        metricsCardsGrid.add(casesMetricsCard, 0, 0);
        metricsCardsGrid.add(latencyCard, 1, 0);
        metricsCardsGrid.add(responseTimeCard, 0, 1);
        metricsCardsGrid.add(utilizationCard, 1, 1);

        grid.add(metricsCardsGrid, 1, 0);
        contentBox.getChildren().add(grid);

        // 4. Chronological Event Log
        VBox eventBox = new VBox(8);
        eventBox.getStyleClass().add("dashboard-card-section");
        eventBox.setPadding(new Insets(16));

        Label eventTitle = new Label("Full Simulation Event Log");
        eventTitle.getStyleClass().add("card-section-title");

        eventTable = createEventLogTable();
        eventBox.getChildren().addAll(eventTitle, eventTable);
        contentBox.getChildren().add(eventBox);

        setContent(contentBox);

        // Wire model
        model.addStateChangeListener(m -> refresh());
        refresh();
    }

    private Label createParamLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("param-label");
        return l;
    }

    private TableView<SimulationEvent> createEventLogTable() {
        TableView<SimulationEvent> t = new TableView<>();
        t.setPrefHeight(320);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new EmptyStateView("📝", "No Events Logged", "Advance the simulation to generate structured lifecycle events."));

        TableColumn<SimulationEvent, String> seqCol = new TableColumn<>("Seq #");
        seqCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().sequenceId())));
        seqCol.setPrefWidth(60);

        TableColumn<SimulationEvent, String> timeCol = new TableColumn<>("Sim Time");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(SimulationPresentationModel.formatDuration(d.getValue().simulationTime())));
        timeCol.setPrefWidth(90);

        TableColumn<SimulationEvent, String> typeCol = new TableColumn<>("Event Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().type().name().replace('_', ' ')));
        typeCol.setPrefWidth(160);

        TableColumn<SimulationEvent, String> targetCol = new TableColumn<>("Target Entity");
        targetCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().entityId()));
        targetCol.setPrefWidth(120);

        TableColumn<SimulationEvent, String> descCol = new TableColumn<>("Event Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().message()));
        descCol.setPrefWidth(360);

        t.setRowFactory(tv -> {
            TableRow<SimulationEvent> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    SimulationEvent ev = row.getItem();
                    UiDialogs.info(getScene().getWindow(),
                            "Simulation Event Details",
                            "Event #" + ev.sequenceId() + " (" + ev.type().name() + ")",
                            "Simulation Timestamp: " + SimulationPresentationModel.formatDuration(ev.simulationTime()) +
                                    "\nTarget Entity: " + ev.entityId() +
                                    "\nEvent Message: " + ev.message());
                }
            });
            return row;
        });

        t.getColumns().addAll(seqCol, timeCol, typeCol, targetCol, descCol);
        return t;
    }

    public void refresh() {
        boolean isRunning = model.getUiState() == UiSimulationState.RUNNING;
        step30sBtn.setDisable(isRunning);
        step1mBtn.setDisable(isRunning);
        step5mBtn.setDisable(isRunning);
        initBtn.setDisable(isRunning);

        // Play / pause button styling
        if (isRunning) {
            playPauseBtn.setText("⏸ Pause");
            playPauseBtn.getStyleClass().removeAll("btn-success");
            if (!playPauseBtn.getStyleClass().contains("btn-warning")) {
                playPauseBtn.getStyleClass().add("btn-warning");
            }
        } else {
            playPauseBtn.setText("▶ Play");
            playPauseBtn.getStyleClass().removeAll("btn-warning");
            if (!playPauseBtn.getStyleClass().contains("btn-success")) {
                playPauseBtn.getStyleClass().add("btn-success");
            }
        }

        // Scenario Metadata
        model.getScenario().ifPresentOrElse(scenario -> {
            scenarioNameVal.setText(scenario.getName());
            durationVal.setText(SimulationPresentationModel.formatDuration(scenario.getSimulationDuration()));
            timeStepVal.setText(scenario.getTimeStep().toSeconds() + " sec");
            seedVal.setText(String.valueOf(scenario.getRandomSeed()));
            nodesVal.setText(String.valueOf(scenario.getRoadGraph().getNodeCount()));
            edgesVal.setText(String.valueOf(scenario.getRoadGraph().getEdgeCount()));
        }, () -> {
            scenarioNameVal.setText("None");
            durationVal.setText("—");
            timeStepVal.setText("—");
            seedVal.setText("—");
            nodesVal.setText("—");
            edgesVal.setText("—");
        });

        // Metrics
        SimulationMetrics metrics = model.getMetrics();
        casesMetricsCard.update(
                String.format("%d Completed", metrics.totalCompletedCases()),
                String.format("%d Reported • %d Dispatched • %d Unserved",
                        metrics.totalReportedCases(), metrics.totalDispatchedCases(), metrics.totalUnservedCases())
        );

        latencyCard.update(
                String.format("%.2f min", metrics.averageDispatchLatencyMinutes()),
                "Average time from call to assignment"
        );

        responseTimeCard.update(
                String.format("%.2f min", metrics.averageResponseTimeMinutes()),
                String.format("Delivery avg: %.2f min", metrics.averageHospitalDeliveryTimeMinutes())
        );

        utilizationCard.update(
                String.format("%.1f%%", metrics.overallAmbulanceUtilization() * 100.0),
                "Fleet operational duty cycle"
        );

        // Event Log
        eventTable.getItems().setAll(model.getRecentEvents(100));
    }
}
