package com.resqroute.optimization;

import com.resqroute.model.EmergencyCase;

import java.util.Objects;

/**
 * Representation of an emergency case that could not be assigned an ambulance during optimization.
 */
public final class UnassignedEmergency {

    private final EmergencyCase emergencyCase;
    private final String reason;

    public UnassignedEmergency(EmergencyCase emergencyCase, String reason) {
        this.emergencyCase = Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        this.reason = (reason == null || reason.trim().isEmpty())
                ? "Fleet capacity or capability constraints"
                : reason.trim();
    }

    public EmergencyCase getEmergencyCase() {
        return emergencyCase;
    }

    public String getCaseId() {
        return emergencyCase.getId();
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnassignedEmergency that)) return false;
        return emergencyCase.equals(that.emergencyCase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emergencyCase);
    }

    @Override
    public String toString() {
        return String.format("UnassignedEmergency[case='%s' (%s), reason='%s']",
                emergencyCase.getId(), emergencyCase.getSeverity(), reason);
    }
}
