package com.resqroute.ui.view;

import com.resqroute.model.Ambulance;
import com.resqroute.ui.component.NotificationBanner;
import com.resqroute.ui.component.StatusBadge;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.model.UiMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Top application status bar displaying scenario metadata, simulation clock, live fleet indicators, and notifications.
 */
public class TopStatusBar extends VBox {

    private final SimulationPresentationModel model;

    private final Label scenarioLabel;
    private final StatusBadge statusBadge;
    private final Label timeLabel;
    private final Label fleetIndicator;
    private final Label emergencyIndicator;
    private final NotificationBanner notificationBanner;

    public TopStatusBar(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setSpacing(6);
        setPadding(new Insets(10, 18, 10, 18));
        getStyleClass().add("top-status-bar");

        // Main status row
        HBox statusRow = new HBox(16);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // Scenario title
        scenarioLabel = new Label("Scenario: No Scenario Loaded");
        scenarioLabel.getStyleClass().add("status-bar-scenario");

        // Sim status badge
        statusBadge = StatusBadge.ofSimulationState(model.getUiState());

        // Simulation Clock
        timeLabel = new Label("⏱ T + 00:00");
        timeLabel.getStyleClass().add("status-bar-time");

        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Live telemetry pills
        fleetIndicator = new Label("🚑 Fleet: 0/0 Avail");
        fleetIndicator.getStyleClass().add("status-bar-telemetry");

        emergencyIndicator = new Label("🚨 Active: 0");
        emergencyIndicator.getStyleClass().add("status-bar-telemetry");

        statusRow.getChildren().addAll(scenarioLabel, statusBadge, timeLabel, spacer, fleetIndicator, emergencyIndicator);

        // Notification banner
        notificationBanner = new NotificationBanner();

        getChildren().addAll(statusRow, notificationBanner);

        // Wire model listeners
        model.addStateChangeListener(this::updateFromModel);
        model.addMessageListener(notificationBanner::showMessage);

        updateFromModel(model);
    }

    private void updateFromModel(SimulationPresentationModel model) {
        // Update scenario name
        model.getScenario().ifPresentOrElse(
                scenario -> scenarioLabel.setText("Scenario: " + scenario.getName()),
                () -> scenarioLabel.setText("Scenario: No Scenario Loaded")
        );

        // Update status badge
        statusBadge.setText(model.getUiState().getDisplayName());
        statusBadge.getStyleClass().removeAll("badge-neutral", "badge-info", "badge-warning", "badge-success", "badge-danger");
        statusBadge.getStyleClass().add(model.getUiState().getBadgeStyleClass());

        // Update clock
        timeLabel.setText("⏱ T + " + model.getFormattedElapsedTime());

        // Update telemetry
        long availableAmbulances = model.getAmbulances().stream().filter(Ambulance::isAvailable).count();
        int totalAmbulances = model.getAmbulances().size();
        fleetIndicator.setText(String.format("🚑 Fleet: %d/%d Avail", availableAmbulances, totalAmbulances));

        int activeEmergencies = model.getActiveEmergencies().size();
        emergencyIndicator.setText(String.format("🚨 Active: %d", activeEmergencies));
    }
}
