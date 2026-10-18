package com.resqroute.optimization;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.RouteResult;

import java.util.Objects;

/**
 * Immutable representation of an optimized ambulance-to-emergency assignment pairing.
 */
public final class AssignmentPair {

    private final Ambulance ambulance;
    private final EmergencyCase emergencyCase;
    private final RouteResult routeToPickup;
    private final double travelTimeMinutes;
    private final double distanceKm;
    private final double cost;
    private final String rationale;

    public AssignmentPair(Ambulance ambulance,
                          EmergencyCase emergencyCase,
                          RouteResult routeToPickup,
                          double travelTimeMinutes,
                          double distanceKm,
                          double cost,
                          String rationale) {
        this.ambulance = Objects.requireNonNull(ambulance, "Ambulance must not be null");
        this.emergencyCase = Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        this.routeToPickup = Objects.requireNonNull(routeToPickup, "RouteResult must not be null");
        this.travelTimeMinutes = travelTimeMinutes;
        this.distanceKm = distanceKm;
        this.cost = cost;
        this.rationale = (rationale == null) ? "" : rationale.trim();
    }

    public Ambulance getAmbulance() {
        return ambulance;
    }

    public String getAmbulanceId() {
        return ambulance.getId();
    }

    public EmergencyCase getEmergencyCase() {
        return emergencyCase;
    }

    public String getCaseId() {
        return emergencyCase.getId();
    }

    public RouteResult getRouteToPickup() {
        return routeToPickup;
    }

    public double getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getCost() {
        return cost;
    }

    public String getRationale() {
        return rationale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssignmentPair that)) return false;
        return ambulance.equals(that.ambulance) && emergencyCase.equals(that.emergencyCase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ambulance, emergencyCase);
    }

    @Override
    public String toString() {
        return String.format("AssignmentPair[ambulance='%s', case='%s' (%s), ETA=%.1f min, cost=%.1f, rationale='%s']",
                ambulance.getId(), emergencyCase.getId(), emergencyCase.getSeverity(),
                travelTimeMinutes, cost, rationale);
    }
}
