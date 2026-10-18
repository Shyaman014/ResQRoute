package com.resqroute.dispatch;

import com.resqroute.model.Ambulance;
import com.resqroute.model.RouteResult;

import java.util.Objects;

/**
 * Evaluated feasible ambulance candidate with computed route to emergency pickup location.
 */
public record AmbulanceCandidate(Ambulance ambulance,
                                 RouteResult routeToPickup,
                                 double estimatedTravelTimeMinutes,
                                 double estimatedDistanceKm) {

    public AmbulanceCandidate {
        Objects.requireNonNull(ambulance, "Ambulance must not be null");
        Objects.requireNonNull(routeToPickup, "RouteResult must not be null");
        if (!routeToPickup.isReachable()) {
            throw new IllegalArgumentException("Cannot create candidate with unreachable route");
        }
    }

    public AmbulanceCandidate(Ambulance ambulance, RouteResult routeToPickup) {
        this(ambulance, routeToPickup, routeToPickup.getTotalTravelTimeMinutes(), routeToPickup.getTotalDistanceKm());
    }

    public String getAmbulanceId() {
        return ambulance.getId();
    }
}
