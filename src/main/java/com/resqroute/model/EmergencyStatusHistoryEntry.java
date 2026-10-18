package com.resqroute.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable timestamped record of an emergency case status transition.
 */
public final class EmergencyStatusHistoryEntry {

    private final EmergencyStatus status;
    private final Instant timestamp;
    private final String notes;

    public EmergencyStatusHistoryEntry(EmergencyStatus status, Instant timestamp, String notes) {
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        this.notes = (notes == null || notes.trim().isEmpty()) ? status.name() : notes.trim();
    }

    public EmergencyStatusHistoryEntry(EmergencyStatus status, Instant timestamp) {
        this(status, timestamp, status.name());
    }

    public EmergencyStatus getStatus() {
        return status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmergencyStatusHistoryEntry that)) return false;
        return status == that.status
                && timestamp.equals(that.timestamp)
                && notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, timestamp, notes);
    }

    @Override
    public String toString() {
        return String.format("[%s] Status: %s - %s", timestamp, status, notes);
    }
}
