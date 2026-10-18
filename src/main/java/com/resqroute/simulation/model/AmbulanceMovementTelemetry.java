package com.resqroute.simulation.model;

import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.GeoCoordinate;
import com.resqroute.model.RouteResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Real-time telemetry snapshot of an ambulance vehicle during simulation.
 *
 * <p>Captures precise interpolated geospatial coordinates, current active route progress,
 * next target waypoint, final destination, visual operational state, estimated time of arrival,
 * and heading angle for accurate map rendering and dashboard synchronization.</p>
 */
public record AmbulanceMovementTelemetry(
        String ambulanceId,
        AmbulanceStatus ambulanceStatus,
        VehicleVisualState visualState,
        String caseId,
        String sourceNodeId,
        String targetNodeId,
        String destinationNodeId,
        String destinationName,
        GeoCoordinate currentCoordinates,
        double routeProgress,
        double estimatedSecondsRemaining,
        RouteResult activeRoute,
        int currentEdgeIndex,
        double headingDegrees,
        boolean isBlocked
) {

    public AmbulanceMovementTelemetry {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(ambulanceStatus, "ambulanceStatus must not be null");
        Objects.requireNonNull(visualState, "visualState must not be null");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
        Objects.requireNonNull(destinationNodeId, "destinationNodeId must not be null");
        destinationName = (destinationName == null || destinationName.isBlank()) ? destinationNodeId : destinationName;
        currentCoordinates = (currentCoordinates == null) ? new GeoCoordinate(0.0, 0.0) : currentCoordinates;
        routeProgress = Math.min(1.0, Math.max(0.0, routeProgress));
        estimatedSecondsRemaining = Math.max(0.0, estimatedSecondsRemaining);
    }

    /**
     * Returns true if vehicle is actively traversing a road corridor.
     */
    public boolean isMoving() {
        return visualState.isMoving() && !isBlocked && routeProgress < 1.0;
    }

    /**
     * Returns active emergency case ID if en-route, on-scene, or transporting.
     */
    public Optional<String> getCaseId() {
        return Optional.ofNullable(caseId);
    }

    /**
     * Returns the active route if vehicle has an assigned travel path.
     */
    public Optional<RouteResult> getActiveRoute() {
        return Optional.ofNullable(activeRoute);
    }

    /**
     * Formats remaining travel time into a human-readable string (e.g. "2m 15s" or "45s").
     */
    public String getFormattedEta() {
        if (estimatedSecondsRemaining <= 0 || !isMoving()) {
            return "0s";
        }
        long totalSec = Math.round(estimatedSecondsRemaining);
        long mins = totalSec / 60;
        long secs = totalSec % 60;
        if (mins > 0) {
            return String.format("%dm %02ds", mins, secs);
        }
        return secs + "s";
    }

    /**
     * Formatted percentage of route completion (0 - 100).
     */
    public int getProgressPercentage() {
        return (int) Math.round(routeProgress * 100.0);
    }

    /**
     * Formatted string of current coordinates (e.g. "40.7128° N, -74.0060° E").
     */
    public String getFormattedCoordinates() {
        return String.format("%.5f, %.5f", currentCoordinates.latitude(), currentCoordinates.longitude());
    }
}
