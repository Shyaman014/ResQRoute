package com.resqroute.simulation.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Specification for a road incident scheduled to block a road segment during simulation.
 */
public record ScheduledIncidentEvent(
        Duration startTimeOffset,
        Duration duration,
        String incidentId,
        String sourceNodeId,
        String destinationNodeId,
        String description
) {
    public ScheduledIncidentEvent {
        Objects.requireNonNull(startTimeOffset, "Start time offset must not be null");
        Objects.requireNonNull(duration, "Duration must not be null");
        if (startTimeOffset.isNegative()) {
            throw new IllegalArgumentException("Start time offset cannot be negative: " + startTimeOffset);
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Incident duration must be positive: " + duration);
        }
        Objects.requireNonNull(incidentId, "Incident ID must not be null");
        Objects.requireNonNull(sourceNodeId, "Source node ID must not be null");
        Objects.requireNonNull(destinationNodeId, "Destination node ID must not be null");
    }

    public Duration getEndTimeOffset() {
        return startTimeOffset.plus(duration);
    }
}
