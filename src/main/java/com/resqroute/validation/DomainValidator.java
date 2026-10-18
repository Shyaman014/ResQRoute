package com.resqroute.validation;

import com.resqroute.exception.InvalidCostException;
import com.resqroute.exception.InvalidRouteException;
import com.resqroute.exception.ValidationException;
import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyType;

import java.time.Duration;
import java.util.Objects;

/**
 * Centralized domain validation utility enforcing strict invariants across service boundaries
 * and preventing invalid data from mutating application state.
 */
public final class DomainValidator {

    private DomainValidator() {
    }

    /**
     * Validates a node identifier string.
     */
    public static String validateNodeId(String nodeId, String fieldName) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new ValidationException(fieldName + " must not be null or blank");
        }
        return nodeId.trim();
    }

    /**
     * Validates latitude and longitude coordinates.
     */
    public static void validateCoordinates(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new ValidationException("Latitude must be a valid finite number between -90.0 and 90.0: " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new ValidationException("Longitude must be a valid finite number between -180.0 and 180.0: " + longitude);
        }
    }

    /**
     * Validates physical road edge costs (distance and travel time).
     */
    public static void validateRoadWeights(double distanceKm, double travelTimeMinutes) {
        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) || distanceKm < 0.0) {
            throw new InvalidCostException("Distance in km must be a non-negative finite number: " + distanceKm);
        }
        if (Double.isNaN(travelTimeMinutes) || Double.isInfinite(travelTimeMinutes) || travelTimeMinutes < 0.0) {
            throw new InvalidCostException("Travel time in minutes must be a non-negative finite number: " + travelTimeMinutes);
        }
    }

    /**
     * Validates route origin and destination endpoints.
     */
    public static void validateRouteEndpoints(String originNodeId, String destinationNodeId) {
        String orig = validateNodeId(originNodeId, "Origin node ID");
        String dest = validateNodeId(destinationNodeId, "Destination node ID");
        if (orig.equals(dest)) {
            throw new InvalidRouteException(orig, dest, "Origin and destination nodes cannot be identical for routing");
        }
    }

    /**
     * Validates hospital capacity parameters.
     */
    public static void validateHospitalCapacity(int totalCapacity, int availableCapacity) {
        if (totalCapacity < 0) {
            throw new ValidationException("Hospital total capacity cannot be negative: " + totalCapacity);
        }
        if (availableCapacity < 0 || availableCapacity > totalCapacity) {
            throw new ValidationException(String.format(
                    "Hospital available capacity (%d) must be between 0 and total capacity (%d)",
                    availableCapacity, totalCapacity));
        }
    }

    /**
     * Validates emergency case inputs.
     */
    public static void validateEmergencyInputs(String caseId, EmergencyType type, EmergencySeverity severity, String pickupNodeId) {
        validateNodeId(caseId, "Emergency case ID");
        Objects.requireNonNull(type, "Emergency type must not be null");
        Objects.requireNonNull(severity, "Emergency severity must not be null");
        validateNodeId(pickupNodeId, "Pickup location node ID");
    }

    /**
     * Validates ambulance inputs.
     */
    public static void validateAmbulanceInputs(String ambulanceId, String locationNodeId, AmbulanceStatus status) {
        validateNodeId(ambulanceId, "Ambulance ID");
        validateNodeId(locationNodeId, "Ambulance location node ID");
        Objects.requireNonNull(status, "Ambulance status must not be null");
    }

    /**
     * Validates simulation scenario settings.
     */
    public static void validateSimulationSettings(Duration duration, Duration timeStep, long seed) {
        Objects.requireNonNull(duration, "Simulation duration must not be null");
        Objects.requireNonNull(timeStep, "Simulation time step must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new ValidationException("Simulation duration must be strictly positive: " + duration);
        }
        if (timeStep.isZero() || timeStep.isNegative()) {
            throw new ValidationException("Simulation time step must be strictly positive: " + timeStep);
        }
        if (timeStep.compareTo(duration) > 0) {
            throw new ValidationException(String.format(
                    "Simulation time step (%s) cannot exceed total simulation duration (%s)", timeStep, duration));
        }
    }
}
