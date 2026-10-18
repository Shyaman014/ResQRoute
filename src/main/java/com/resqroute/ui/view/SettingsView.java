package com.resqroute.ui.view;

import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.model.UiMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings and system overview view allowing configuration of simulation step sizes,
 * random seeds, and displaying underlying architectural specifications.
 */
public class SettingsView extends ScrollPane {

    private final SimulationPresentationModel model;

    private final TextField seedField;
    private final ComboBox<Integer> stepSizeCombo;
    private final ComboBox<Long> tickIntervalCombo;

    public SettingsView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setFitToWidth(true);
        getStyleClass().add("settings-scroll-pane");

        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(24));
        contentBox.getStyleClass().add("settings-container");

        // 1. Header
        SectionHeader header = new SectionHeader(
                "System Settings & Architecture",
                "Runtime configuration, simulation parameter calibration, and core engine specifications"
        );
        contentBox.getChildren().add(header);

        // 2. Settings Grid (Left: Config, Right: About & Architecture)
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        // Config Card
        VBox configCard = new VBox(14);
        configCard.getStyleClass().add("dashboard-card-section");
        configCard.setPadding(new Insets(20));

        Label configTitle = new Label("⚙️ Simulation Calibration");
        configTitle.getStyleClass().add("card-section-title");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(12);
        formGrid.setVgap(14);

        Label seedLabel = new Label("Deterministic Random Seed:");
        seedLabel.getStyleClass().add("param-label");
        seedField = new TextField(String.valueOf(model.getRandomSeed()));
        seedField.getStyleClass().add("settings-field");
        seedField.setAccessibleText("Deterministic pseudo-random number generator seed");
        seedField.setTooltip(new Tooltip("64-bit integer seed for reproducible traffic incidents and scenario generation"));

        Label stepLabel = new Label("Default Step Duration (Seconds):");
        stepLabel.getStyleClass().add("param-label");
        stepSizeCombo = new ComboBox<>();
        stepSizeCombo.getItems().addAll(15, 30, 60, 120, 300);
        stepSizeCombo.setValue((int) model.getDefaultStepDuration().toSeconds());
        stepSizeCombo.getStyleClass().add("settings-field");
        stepSizeCombo.setAccessibleText("Default simulation step duration in seconds");
        stepSizeCombo.setTooltip(new Tooltip("Discrete quantum of logical time advanced per step"));

        Label intervalLabel = new Label("Auto-Runner Tick Delay (ms):");
        intervalLabel.getStyleClass().add("param-label");
        tickIntervalCombo = new ComboBox<>();
        tickIntervalCombo.getItems().addAll(100L, 300L, 500L, 800L, 1200L, 2000L);
        tickIntervalCombo.setValue(model.getAutoRunIntervalMs());
        tickIntervalCombo.getStyleClass().add("settings-field");
        tickIntervalCombo.setAccessibleText("Background execution runner delay in milliseconds");
        tickIntervalCombo.setTooltip(new Tooltip("Real-world interval between consecutive simulation ticks"));

        formGrid.add(seedLabel, 0, 0);
        formGrid.add(seedField, 1, 0);
        formGrid.add(stepLabel, 0, 1);
        formGrid.add(stepSizeCombo, 1, 1);
        formGrid.add(intervalLabel, 0, 2);
        formGrid.add(tickIntervalCombo, 1, 2);

        Button saveBtn = new Button("💾 Save & Apply Settings");
        saveBtn.getStyleClass().addAll("action-btn", "btn-primary");
        saveBtn.setAccessibleText("Save and apply new simulation settings");
        saveBtn.setTooltip(new Tooltip("Persist configuration changes to live engine"));
        saveBtn.setOnAction(e -> applySettings());

        configCard.getChildren().addAll(configTitle, formGrid, saveBtn);
        grid.add(configCard, 0, 0);

        // System Architecture Card
        VBox sysCard = new VBox(12);
        sysCard.getStyleClass().add("dashboard-card-section");
        sysCard.setPadding(new Insets(20));

        Label sysTitle = new Label("🏛 Architecture & Technology Stack");
        sysTitle.getStyleClass().add("card-section-title");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(12);
        infoGrid.setVgap(10);

        infoGrid.add(createParamLabel("Application:"), 0, 0);
        infoGrid.add(new Label("ResQRoute Core Platform v1.0"), 1, 0);

        infoGrid.add(createParamLabel("Java Platform:"), 0, 1);
        infoGrid.add(new Label(System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"), 1, 1);

        infoGrid.add(createParamLabel("Routing Algorithms:"), 0, 2);
        infoGrid.add(new Label("Dijkstra, A* (Haversine), Yen's K-Shortest Paths"), 1, 2);

        infoGrid.add(createParamLabel("Dispatch Engine:"), 0, 3);
        infoGrid.add(new Label("Aging Priority Queue + Min-Cost Max-Flow Matching"), 1, 3);

        infoGrid.add(createParamLabel("Hospital Selection:"), 0, 4);
        infoGrid.add(new Label("Capability Matching + Bed Reservation Protocol"), 1, 4);

        infoGrid.add(createParamLabel("Traffic Engine:"), 0, 5);
        infoGrid.add(new Label("Dynamic Incident Injection & Real-Time Rerouting"), 1, 5);

        sysCard.getChildren().addAll(sysTitle, infoGrid);
        grid.add(sysCard, 1, 0);

        contentBox.getChildren().add(grid);
        setContent(contentBox);
    }

    private Label createParamLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("param-label");
        return l;
    }

    private void applySettings() {
        try {
            long seed = Long.parseLong(seedField.getText().trim());
            model.setRandomSeed(seed);

            Integer stepSec = stepSizeCombo.getValue();
            if (stepSec != null) {
                model.setDefaultStepDuration(Duration.ofSeconds(stepSec));
            }

            Long interval = tickIntervalCombo.getValue();
            if (interval != null) {
                model.setAutoRunIntervalMs(interval);
            }

            model.publishMessage(UiMessage.success("Simulation settings saved. New seed: " + seed));
        } catch (NumberFormatException ex) {
            model.publishMessage(UiMessage.error("Invalid seed value. Must be a valid 64-bit integer."));
        }
    }
}
