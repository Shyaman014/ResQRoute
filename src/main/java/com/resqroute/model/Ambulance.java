package com.resqroute.model;

import java.util.*;

/**
 * Domain model representing an emergency ambulance vehicle in the dispatch network.
 *
 * <p>Tracks vehicle identity, current road network location, operational status,
 * active mission assignment, and clinical capabilities (e.g. BLS, ALS, Cardiac, Rescue).</p>
 */
public final class Ambulance {

    private final String id;
    private final String currentLocationNodeId;
    private final AmbulanceStatus status;
    private final String assignedCaseId;
    private final Set<AmbulanceCapability> capabilities;

    public Ambulance(String id,
                     String currentLocationNodeId,
                     AmbulanceStatus status,
                     String assignedCaseId,
                     Set<AmbulanceCapability> capabilities) {
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

        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities = Set.of(
                    AmbulanceCapability.BASIC_LIFE_SUPPORT,
                    AmbulanceCapability.ADVANCED_LIFE_SUPPORT,
                    AmbulanceCapability.CARDIAC_CARE
            );
        } else {
            this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }
    }

    public Ambulance(String id, String currentLocationNodeId, AmbulanceStatus status, String assignedCaseId) {
        this(id, currentLocationNodeId, status, assignedCaseId, null);
    }

    public Ambulance(String id, String currentLocationNodeId, Set<AmbulanceCapability> capabilities) {
        this(id, currentLocationNodeId, AmbulanceStatus.AVAILABLE, null, capabilities);
    }

    public Ambulance(String id, String currentLocationNodeId) {
        this(id, currentLocationNodeId, AmbulanceStatus.AVAILABLE, null, null);
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

    public Set<AmbulanceCapability> getCapabilities() {
        return capabilities;
    }

    public boolean hasCapability(AmbulanceCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public boolean hasAllCapabilities(Collection<AmbulanceCapability> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        return capabilities.containsAll(required);
    }

    public boolean isAvailable() {
        return status == AmbulanceStatus.AVAILABLE && assignedCaseId == null;
    }

    public Ambulance withStatus(AmbulanceStatus newStatus) {
        return new Ambulance(this.id, this.currentLocationNodeId, newStatus, this.assignedCaseId, this.capabilities);
    }

    public Ambulance withAssignedCase(String caseId) {
        return new Ambulance(this.id, this.currentLocationNodeId, AmbulanceStatus.ASSIGNED, caseId, this.capabilities);
    }

    public Ambulance withLocation(String newLocationNodeId) {
        return new Ambulance(this.id, newLocationNodeId, this.status, this.assignedCaseId, this.capabilities);
    }

    public Ambulance withCapabilities(Set<AmbulanceCapability> newCapabilities) {
        return new Ambulance(this.id, this.currentLocationNodeId, this.status, this.assignedCaseId, newCapabilities);
    }

    public Ambulance withReleased() {
        return new Ambulance(this.id, this.currentLocationNodeId, AmbulanceStatus.AVAILABLE, null, this.capabilities);
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
        return String.format("Ambulance[id='%s', location='%s', status=%s, assignedCase='%s', capabilities=%s]",
                id, currentLocationNodeId, status, assignedCaseId != null ? assignedCaseId : "none", capabilities);
    }
}
