package com.resqroute.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Domain model representing an emergency ambulance vehicle in the dispatch network.
 *
 * <p>Tracks vehicle identity, current road network location, operational status,
 * and active mission assignment.</p>
 */
public final class Ambulance {

    private final String id;
    private final String currentLocationNodeId;
    private final AmbulanceStatus status;
    private final String assignedCaseId;

    public Ambulance(String id, String currentLocationNodeId, AmbulanceStatus status, String assignedCaseId) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Ambulance ID must not be null or blank");
        }
        if (currentLocationNodeId == null || currentLocationNodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Current location node ID must not be null or blank");
        }

        this.id = id.trim();
        this.currentLocationNodeId = currentLocationNodeId.trim();
        this.status = Objects.requireNonNull(status, "Ambulance status must not be null");
        this.assignedCaseId = (assignedCaseId == null || assignedCaseId.trim().isEmpty())
                ? null
                : assignedCaseId.trim();
    }

    public Ambulance(String id, String currentLocationNodeId) {
        this(id, currentLocationNodeId, AmbulanceStatus.AVAILABLE, null);
    }

    public String getId() {
        return id;
    }

    public String getCurrentLocationNodeId() {
        return currentLocationNodeId;
    }

    public AmbulanceStatus getStatus() {
        return status;
    }

    public Optional<String> getAssignedCaseId() {
        return Optional.ofNullable(assignedCaseId);
    }

    public boolean isAvailable() {
        return status == AmbulanceStatus.AVAILABLE && assignedCaseId == null;
    }

    public Ambulance withStatus(AmbulanceStatus newStatus) {
        return new Ambulance(this.id, this.currentLocationNodeId, newStatus, this.assignedCaseId);
    }

    public Ambulance withAssignedCase(String caseId) {
        return new Ambulance(this.id, this.currentLocationNodeId, AmbulanceStatus.ASSIGNED, caseId);
    }

    public Ambulance withLocation(String newLocationNodeId) {
        return new Ambulance(this.id, newLocationNodeId, this.status, this.assignedCaseId);
    }

    public Ambulance withReleased() {
        return new Ambulance(this.id, this.currentLocationNodeId, AmbulanceStatus.AVAILABLE, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ambulance ambulance)) return false;
        return id.equals(ambulance.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Ambulance[id='%s', location='%s', status=%s, assignedCase='%s']",
                id, currentLocationNodeId, status, assignedCaseId != null ? assignedCaseId : "none");
    }
}
