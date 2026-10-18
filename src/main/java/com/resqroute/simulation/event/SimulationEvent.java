package com.resqroute.simulation.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable log entry representing an event that occurred at a deterministic simulation instant.
 */
public record SimulationEvent(
        long sequenceId,
        Instant timestamp,
        Duration simulationTime,
        SimulationEventType type,
        String entityId,
        String message
) implements Comparable<SimulationEvent> {

    public SimulationEvent {
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
        Objects.requireNonNull(simulationTime, "Simulation time must not be null");
        Objects.requireNonNull(type, "Event type must not be null");
        entityId = (entityId == null) ? "" : entityId.trim();
        message = (message == null) ? "" : message.trim();
    }

    @Override
    public int compareTo(SimulationEvent other) {
        int timeCmp = this.timestamp.compareTo(other.timestamp);
        if (timeCmp != 0) {
            return timeCmp;
        }
        return Long.compare(this.sequenceId, other.sequenceId);
    }
}
