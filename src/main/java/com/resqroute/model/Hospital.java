package com.resqroute.model;

import java.time.Instant;
import java.util.*;

/**
 * Domain model representing an emergency care hospital in the dispatch network.
 *
 * <p>Invariant rules enforced:
 * <ul>
 *   <li>{@code totalCapacity >= 0} and {@code 0 <= availableCapacity <= totalCapacity}.</li>
 *   <li>Status consistency:
 *     <ul>
 *       <li>{@link HospitalStatus#OPEN} strictly requires {@code availableCapacity > 0}.</li>
 *       <li>{@link HospitalStatus#BUSY} strictly requires {@code availableCapacity == 0}.</li>
 *       <li>{@link HospitalStatus#CLOSED} indicates administrative closure regardless of capacity.</li>
 *     </ul>
 *   </li>
 *   <li>Supported emergency types and capabilities are defensively copied as unmodifiable sets.</li>
 * </ul>
 * </p>
 */
public final class Hospital {

    private final String id;
    private final String name;
    private final String locationNodeId;
    private final Set<EmergencyType> supportedEmergencyTypes;
    private final Set<HospitalCapability> capabilities;
    private final int totalCapacity;
    private final int availableCapacity;
    private final HospitalStatus status;
    private final Instant lastUpdatedAt;

    public Hospital(String id,
                    String name,
                    String locationNodeId,
                    Set<EmergencyType> supportedEmergencyTypes,
                    Set<HospitalCapability> capabilities,
                    int totalCapacity,
                    int availableCapacity,
                    HospitalStatus status,
                    Instant lastUpdatedAt) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital ID must not be null or blank");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital name must not be null or blank");
        }
        if (locationNodeId == null || locationNodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital location node ID must not be null or blank");
        }
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("Total capacity cannot be negative: " + totalCapacity);
        }
        if (availableCapacity < 0 || availableCapacity > totalCapacity) {
            throw new IllegalArgumentException(String.format(
                    "Available capacity (%d) must be between 0 and total capacity (%d)",
                    availableCapacity, totalCapacity));
        }

        this.id = id.trim();
        this.name = name.trim();
        this.locationNodeId = locationNodeId.trim();
        this.totalCapacity = totalCapacity;
        this.availableCapacity = availableCapacity;
        this.lastUpdatedAt = Objects.requireNonNull(lastUpdatedAt, "Last updated timestamp must not be null");

        // Status-capacity consistency check
        HospitalStatus evaluatedStatus = Objects.requireNonNull(status, "Hospital status must not be null");
        if (evaluatedStatus == HospitalStatus.OPEN && availableCapacity == 0) {
            throw new IllegalArgumentException("Hospital cannot be OPEN with 0 available capacity (must be BUSY)");
        }
        if (evaluatedStatus == HospitalStatus.BUSY && availableCapacity > 0) {
            throw new IllegalArgumentException("Hospital cannot be BUSY with available capacity > 0 (must be OPEN or CLOSED)");
        }
        this.status = evaluatedStatus;

        if (supportedEmergencyTypes == null || supportedEmergencyTypes.isEmpty()) {
            this.supportedEmergencyTypes = Collections.unmodifiableSet(EnumSet.allOf(EmergencyType.class));
        } else {
            this.supportedEmergencyTypes = Collections.unmodifiableSet(EnumSet.copyOf(supportedEmergencyTypes));
        }

        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities = Collections.unmodifiableSet(EnumSet.of(HospitalCapability.EMERGENCY_ROOM));
        } else {
            this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }
    }

    /**
     * Convenience constructor with default open status (or busy if capacity 0) and current timestamp.
     */
    public Hospital(String id,
                    String name,
                    String locationNodeId,
                    Set<EmergencyType> supportedEmergencyTypes,
                    Set<HospitalCapability> capabilities,
                    int totalCapacity,
                    int availableCapacity) {
        this(id, name, locationNodeId, supportedEmergencyTypes, capabilities, totalCapacity, availableCapacity,
                availableCapacity > 0 ? HospitalStatus.OPEN : HospitalStatus.BUSY, Instant.now());
    }

    /**
     * Basic constructor defaulting to full capacity, all emergency types, and standard ER capability.
     */
    public Hospital(String id, String name, String locationNodeId, int capacity) {
        this(id, name, locationNodeId, null, null, capacity, capacity);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocationNodeId() {
        return locationNodeId;
    }

    public Set<EmergencyType> getSupportedEmergencyTypes() {
        return supportedEmergencyTypes;
    }

    public Set<HospitalCapability> getCapabilities() {
        return capabilities;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public int getOccupiedCapacity() {
        return totalCapacity - availableCapacity;
    }

    public HospitalStatus getStatus() {
        return status;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public boolean isOpen() {
        return status == HospitalStatus.OPEN;
    }

    public boolean isBusy() {
        return status == HospitalStatus.BUSY;
    }

    public boolean isClosed() {
        return status == HospitalStatus.CLOSED;
    }

    /**
     * Checks if this hospital is currently eligible and able to accept new emergency admissions.
     */
    public boolean isAvailableForAdmission() {
        return status == HospitalStatus.OPEN && availableCapacity > 0;
    }

    public boolean supportsEmergencyType(EmergencyType type) {
        return type != null && supportedEmergencyTypes.contains(type);
    }

    public boolean hasCapability(HospitalCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public boolean hasAllCapabilities(Collection<HospitalCapability> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        return capabilities.containsAll(required);
    }

    /**
     * Returns an updated hospital copy with revised capacity and status auto-alignment.
     */
    public Hospital withAvailableCapacity(int newAvailableCapacity, Instant timestamp) {
        HospitalStatus newStatus;
        if (this.status == HospitalStatus.CLOSED) {
            newStatus = HospitalStatus.CLOSED;
        } else if (newAvailableCapacity == 0) {
            newStatus = HospitalStatus.BUSY;
        } else {
            newStatus = HospitalStatus.OPEN;
        }

        return new Hospital(
                this.id,
                this.name,
                this.locationNodeId,
                this.supportedEmergencyTypes,
                this.capabilities,
                this.totalCapacity,
                newAvailableCapacity,
                newStatus,
                timestamp
        );
    }

    /**
     * Returns an updated hospital copy with revised total and available capacity.
     */
    public Hospital withCapacity(int newTotalCapacity, int newAvailableCapacity, Instant timestamp) {
        HospitalStatus newStatus;
        if (this.status == HospitalStatus.CLOSED) {
            newStatus = HospitalStatus.CLOSED;
        } else if (newAvailableCapacity == 0) {
            newStatus = HospitalStatus.BUSY;
        } else {
            newStatus = HospitalStatus.OPEN;
        }

        return new Hospital(
                this.id,
                this.name,
                this.locationNodeId,
                this.supportedEmergencyTypes,
                this.capabilities,
                newTotalCapacity,
                newAvailableCapacity,
                newStatus,
                timestamp
        );
    }

    /**
     * Returns an updated hospital copy with a new operational status.
     */
    public Hospital withStatus(HospitalStatus newStatus, Instant timestamp) {
        Objects.requireNonNull(newStatus, "New status must not be null");
        return new Hospital(
                this.id,
                this.name,
                this.locationNodeId,
                this.supportedEmergencyTypes,
                this.capabilities,
                this.totalCapacity,
                this.availableCapacity,
                newStatus,
                timestamp
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private String locationNodeId;
        private Set<EmergencyType> supportedEmergencyTypes;
        private Set<HospitalCapability> capabilities;
        private int totalCapacity;
        private int availableCapacity = -1;
        private HospitalStatus status;
        private Instant lastUpdatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder locationNodeId(String locationNodeId) {
            this.locationNodeId = locationNodeId;
            return this;
        }

        public Builder supportedEmergencyTypes(Set<EmergencyType> types) {
            this.supportedEmergencyTypes = types;
            return this;
        }

        public Builder capabilities(Set<HospitalCapability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder totalCapacity(int totalCapacity) {
            this.totalCapacity = totalCapacity;
            return this;
        }

        public Builder availableCapacity(int availableCapacity) {
            this.availableCapacity = availableCapacity;
            return this;
        }

        public Builder status(HospitalStatus status) {
            this.status = status;
            return this;
        }

        public Builder lastUpdatedAt(Instant lastUpdatedAt) {
            this.lastUpdatedAt = lastUpdatedAt;
            return this;
        }

        public Hospital build() {
            int avail = (availableCapacity == -1) ? totalCapacity : availableCapacity;
            Instant ts = (lastUpdatedAt == null) ? Instant.now() : lastUpdatedAt;
            HospitalStatus st = status;
            if (st == null) {
                st = (avail > 0) ? HospitalStatus.OPEN : HospitalStatus.BUSY;
            }
            return new Hospital(id, name, locationNodeId, supportedEmergencyTypes, capabilities, totalCapacity, avail, st, ts);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hospital hospital)) return false;
        return id.equals(hospital.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Hospital[id='%s', name='%s', location='%s', cap=%d/%d, status=%s, capabilities=%s]",
                id, name, locationNodeId, availableCapacity, totalCapacity, status, capabilities);
    }
}
