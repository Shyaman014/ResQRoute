package com.resqroute.model;

import com.resqroute.exception.InvalidCostException;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of real-time dynamic traffic conditions on a roadway segment.
 *
 * <p>Separates dynamic traffic state from static road physical attributes (e.g., road distance).
 * Maintains the current congestion tier, dynamic delay multiplier, update timestamp, and provenance data source.</p>
 */
public record TrafficCondition(
        TrafficLevel level,
        double delayMultiplier,
        Instant timestamp,
        String dataSource
) {

    public TrafficCondition {
        Objects.requireNonNull(level, "TrafficLevel must not be null");
        Objects.requireNonNull(timestamp, "Timestamp must not be null");

        if (Double.isNaN(delayMultiplier) || Double.isInfinite(delayMultiplier) || delayMultiplier < 1.0) {
            throw new InvalidCostException("Traffic delay multiplier must be a finite number >= 1.0, got: " + delayMultiplier);
        }

        if (dataSource == null || dataSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Data source must not be null or blank");
        }
        dataSource = dataSource.trim();
    }

    /**
     * Creates a free-flow traffic condition timestamped at the current instant.
     */
    public static TrafficCondition freeFlow(String dataSource) {
        return new TrafficCondition(TrafficLevel.FREE_FLOW, 1.0, Instant.now(), dataSource);
    }

    /**
     * Creates a standard free-flow condition with default telemetry source.
     */
    public static TrafficCondition freeFlow() {
        return freeFlow("BASE_ROAD_TELEMETRY");
    }

    /**
     * Creates a traffic condition with the standard default multiplier for the specified level.
     */
    public static TrafficCondition of(TrafficLevel level, String dataSource) {
        Objects.requireNonNull(level, "TrafficLevel must not be null");
        return new TrafficCondition(level, level.getDefaultDelayMultiplier(), Instant.now(), dataSource);
    }

    /**
     * Creates a custom traffic condition with an explicit incident multiplier and description.
     */
    public static TrafficCondition incident(double delayMultiplier, String incidentDescription) {
        return new TrafficCondition(TrafficLevel.SEVERE, delayMultiplier, Instant.now(), "INCIDENT: " + incidentDescription);
    }

    /**
     * Computes the effective travel time given the road's base free-flow travel time.
     *
     * @param baseTravelTimeMinutes Base travel time in minutes.
     * @return Effective travel time adjusted for current congestion.
     */
    public double calculateEffectiveTravelTime(double baseTravelTimeMinutes) {
        if (Double.isNaN(baseTravelTimeMinutes) || Double.isInfinite(baseTravelTimeMinutes) || baseTravelTimeMinutes < 0.0) {
            throw new InvalidCostException("Base travel time must be non-negative: " + baseTravelTimeMinutes);
        }
        return baseTravelTimeMinutes * delayMultiplier;
    }
}
