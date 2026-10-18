package com.resqroute.ui.view;

import com.resqroute.model.*;
import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.ui.component.EmptyStateView;
import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.component.StatCard;
import com.resqroute.ui.component.StatusBadge;
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
 * Main operations dashboard displaying high-level fleet telemetry, active emergency queues,
 * hospital network availability, and recent simulation events.
 */
public class DashboardView extends ScrollPane {

    private final SimulationPresentationModel model;

    // Stat Cards
    private final StatCard fleetCard;
    private final StatCard emergencyCard;
    private final StatCard hospitalCard;
    private final StatCard simulationCard;

    // Quick action buttons
    private final Button initBtn;
    private final Button stepBtn;
    private final Button playPauseBtn;
    private final Button resetBtn;

    // Tables
    private final TableView<EmergencyCase> emergencyTable;
    private final TableView<Ambulance> ambulanceTable;
    private final TableView<SimulationEvent> eventTable;

    public DashboardView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setFitToWidth(true);
        getStyleClass().add("dashboard-scroll-pane");

        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(24));
        contentBox.getStyleClass().add("dashboard-container");

        // 1. Header with Quick Action Bar
        SectionHeader header = new SectionHeader("Operations Dashboard", "Real-time dispatch telemetry and mission status");

        initBtn = new Button("🚀 Init Metropolis");
        initBtn.getStyleClass().addAll("action-btn", "btn-primary");
        initBtn.setOnAction(e -> model.initializeDefaultScenario());

        stepBtn = new Button("⏭ Step (+1m)");
        stepBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        stepBtn.setOnAction(e -> model.step(Duration.ofMinutes(1)));

        playPauseBtn = new Button("▶ Play");
        playPauseBtn.getStyleClass().addAll("action-btn", "btn-success");
        playPauseBtn.setOnAction(e -> model.togglePlayPause());

        resetBtn = new Button("🔄 Reset");
        resetBtn.getStyleClass().addAll("action-btn", "btn-danger");
        resetBtn.setOnAction(e -> model.reset());

        header.setActions(initBtn, stepBtn, playPauseBtn, resetBtn);
        contentBox.getChildren().add(header);

        // 2. Metric Stat Cards
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(16);
        metricsGrid.setVgap(16);

        fleetCard = new StatCard("🚑", "Fleet Readiness", "0 / 0", "No fleet active", "card-accent-emerald");
        emergencyCard = new StatCard("🚨", "Emergency Queue", "0 Active", "0 Critical pending", "card-accent-rose");
        hospitalCard = new StatCard("🏥", "Hospital Beds", "0 / 0", "0 Open hospitals", "card-accent-indigo");
        simulationCard = new StatCard("⏱️", "Simulation Clock", "T + 00:00", "State: Uninitialized", "card-accent-amber");

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(25);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(25);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(25);
        ColumnConstraints col4 = new ColumnConstraints();
        col4.setPercentWidth(25);
        metricsGrid.getColumnConstraints().addAll(col1, col2, col3, col4);

        metricsGrid.add(fleetCard, 0, 0);
        metricsGrid.add(emergencyCard, 1, 0);
        metricsGrid.add(hospitalCard, 2, 0);
        metricsGrid.add(simulationCard, 3, 0);

        contentBox.getChildren().add(metricsGrid);

        // 3. Split: Active Emergencies & Fleet Readiness
        GridPane liveGrid = new GridPane();
        liveGrid.setHgap(16);
        liveGrid.setVgap(16);

        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(55);
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(45);
        liveGrid.getColumnConstraints().addAll(leftCol, rightCol);

        // Left: Active Emergencies Table
        VBox emergencyBox = createCardSection("Active Emergencies", "Urgent incidents currently awaiting dispatch or in transit");
        emergencyTable = createEmergencyTable();
        emergencyBox.getChildren().add(emergencyTable);
        liveGrid.add(emergencyBox, 0, 0);

        // Right: Ambulance Fleet Table
        VBox ambulanceBox = createCardSection("Fleet Status", "Unit availability and mission deployment");
        ambulanceTable = createAmbulanceTable();
        ambulanceBox.getChildren().add(ambulanceTable);
        liveGrid.add(ambulanceBox, 1, 0);

        contentBox.getChildren().add(liveGrid);

        // 4. Recent Simulation Events
        VBox eventBox = createCardSection("Recent Simulation Events", "Chronological audit trail of dispatch, transit, and incident events");
        eventTable = createEventTable();
        eventBox.getChildren().add(eventTable);
        contentBox.getChildren().add(eventBox);

        setContent(contentBox);

        // Wire model updates
        model.addStateChangeListener(this::refresh);
        refresh(model);
    }

    private VBox createCardSection(String title, String subtitle) {
        VBox box = new VBox(8);
        box.getStyleClass().add("dashboard-card-section");
        box.setPadding(new Insets(14));

        Label t = new Label(title);
        t.getStyleClass().add("card-section-title");

        Label s = new Label(subtitle);
        s.getStyleClass().add("card-section-subtitle");

        box.getChildren().addAll(t, s);
        return box;
    }

    private TableView<EmergencyCase> createEmergencyTable() {
        TableView<EmergencyCase> table = new TableView<>();
        table.setPrefHeight(220);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new EmptyStateView("🚨", "No Active Emergencies", "All reported cases are completed or no scenario is active."));

        TableColumn<EmergencyCase, String> idCol = new TableColumn<>("Case ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        idCol.setPrefWidth(90);

        TableColumn<EmergencyCase, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType().name()));
        typeCol.setPrefWidth(90);

        TableColumn<EmergencyCase, Void> sevCol = new TableColumn<>("Severity");
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    EmergencyCase c = getTableRow().getItem();
                    setGraphic(StatusBadge.ofSeverity(c.getSeverity()));
                }
            }
        });
        sevCol.setPrefWidth(85);

        TableColumn<EmergencyCase, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPickupLocationNodeId()));
        locCol.setPrefWidth(90);

        TableColumn<EmergencyCase, Void> statusCol = new TableColumn<>("Status");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    EmergencyCase c = getTableRow().getItem();
                    setGraphic(StatusBadge.ofEmergencyStatus(c.getStatus()));
                }
            }
        });
        statusCol.setPrefWidth(120);

        TableColumn<EmergencyCase, String> ambCol = new TableColumn<>("Assigned Unit");
        ambCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAssignedAmbulanceId().orElse("—")));
        ambCol.setPrefWidth(100);

        table.getColumns().addAll(idCol, typeCol, sevCol, locCol, statusCol, ambCol);
        return table;
    }

    private TableView<Ambulance> createAmbulanceTable() {
        TableView<Ambulance> table = new TableView<>();
        table.setPrefHeight(220);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new EmptyStateView("🚑", "No Ambulances Registered", "Initialize a city scenario to populate the fleet."));

        TableColumn<Ambulance, String> idCol = new TableColumn<>("Unit ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        idCol.setPrefWidth(85);

        TableColumn<Ambulance, Void> statusCol = new TableColumn<>("Status");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Ambulance a = getTableRow().getItem();
                    setGraphic(StatusBadge.ofAmbulanceStatus(a.getStatus()));
                }
            }
        });
        statusCol.setPrefWidth(110);

        TableColumn<Ambulance, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrentLocationNodeId()));
        locCol.setPrefWidth(85);

        TableColumn<Ambulance, String> caseCol = new TableColumn<>("Mission Case");
        caseCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAssignedCaseId().orElse("—")));
        caseCol.setPrefWidth(95);

        table.getColumns().addAll(idCol, statusCol, locCol, caseCol);
        return table;
    }

    private TableView<SimulationEvent> createEventTable() {
        TableView<SimulationEvent> table = new TableView<>();
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new EmptyStateView("📝", "No Simulation Events", "Step or run the scenario to generate simulation events."));

        TableColumn<SimulationEvent, String> seqCol = new TableColumn<>("#");
        seqCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().sequenceId())));
        seqCol.setPrefWidth(50);

        TableColumn<SimulationEvent, String> timeCol = new TableColumn<>("Sim Time");
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(SimulationPresentationModel.formatDuration(data.getValue().simulationTime())));
        timeCol.setPrefWidth(90);

        TableColumn<SimulationEvent, Void> typeCol = new TableColumn<>("Event Type");
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    SimulationEvent ev = getTableRow().getItem();
                    Label pill = new Label(ev.type().name().replace('_', ' '));
                    pill.getStyleClass().addAll("status-badge", "badge-neutral");
                    setGraphic(pill);
                }
            }
        });
        typeCol.setPrefWidth(160);

        TableColumn<SimulationEvent, String> targetCol = new TableColumn<>("Target");
        targetCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().entityId()));
        targetCol.setPrefWidth(110);

        TableColumn<SimulationEvent, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
        descCol.setPrefWidth(300);

        table.getColumns().addAll(seqCol, timeCol, typeCol, targetCol, descCol);
        return table;
    }

    public void refresh(SimulationPresentationModel model) {
        // 1. Update Play / Pause Button state
        if (model.getUiState() == UiSimulationState.RUNNING) {
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

        // 2. Update Fleet Stat Card
        List<Ambulance> ambulances = model.getAmbulances();
        long available = ambulances.stream().filter(Ambulance::isAvailable).count();
        long busy = ambulances.size() - available;
        fleetCard.update(
                String.format("%d / %d Available", available, ambulances.size()),
                String.format("%d units deployed on active missions", busy)
        );

        // 3. Update Emergency Stat Card
        List<EmergencyCase> activeCases = model.getActiveEmergencies();
        long criticalCount = activeCases.stream().filter(c -> c.getSeverity() == EmergencySeverity.CRITICAL).count();
        long waitingCount = activeCases.stream().filter(c -> c.getStatus() == EmergencyStatus.WAITING_FOR_DISPATCH).count();
        emergencyCard.update(
                String.format("%d Active", activeCases.size()),
                String.format("%d Critical • %d Waiting for dispatch", criticalCount, waitingCount)
        );

        // 4. Update Hospital Stat Card
        List<Hospital> hospitals = model.getHospitals();
        int totalCap = hospitals.stream().mapToInt(Hospital::getTotalCapacity).sum();
        int occupiedCap = hospitals.stream().mapToInt(Hospital::getOccupiedCapacity).sum();
        long openHospitals = hospitals.stream().filter(Hospital::isOpen).count();
        hospitalCard.update(
                String.format("%d / %d Beds", occupiedCap, totalCap),
                String.format("%d / %d Hospitals Open", openHospitals, hospitals.size())
        );

        // 5. Update Simulation Stat Card
        simulationCard.update(
                "T + " + model.getFormattedElapsedTime(),
                "State: " + model.getUiState().getDisplayName()
        );

        // 6. Update Tables
        emergencyTable.getItems().setAll(activeCases);
        ambulanceTable.getItems().setAll(ambulances);
        eventTable.getItems().setAll(model.getRecentEvents(15));
    }
}
