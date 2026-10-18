package com.resqroute.simulation.model;

import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyType;

import java.time.Duration;
import java.util.Objects;

/**
 * Specification for an emergency case scheduled to occur at a specific simulation offset.
 */
public record ScheduledEmergencyEvent(
        Duration timeOffset,
        String caseId,
        EmergencyType type,
        EmergencySeverity severity,
        String pickupNodeId,
        String description
) {
    public ScheduledEmergencyEvent {
        Objects.requireNonNull(timeOffset, "Time offset must not be null");
        if (timeOffset.isNegative()) {
            throw new IllegalArgumentException("Time offset cannot be negative: " + timeOffset);
        }
        Objects.requireNonNull(caseId, "Case ID must not be null");
        Objects.requireNonNull(type, "Emergency type must not be null");
        Objects.requireNonNull(severity, "Emergency severity must not be null");
        Objects.requireNonNull(pickupNodeId, "Pickup node ID must not be null");
    }
}
