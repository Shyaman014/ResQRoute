package com.resqroute.model;

import java.time.Instant;
import java.util.*;

/**
 * Domain model representing an emergency incident lifecycle in ResQRoute.
 *
 * <p>Encapsulates incident classification, triage severity, pickup waypoint, lifecycle status,
 * transit assignments, clinical capability requirements, and chronological status history.
 * Designed according to privacy best practices: stores strictly operational dispatch metadata
 * with zero personally identifiable patient information (PII).</p>
 */
public final class EmergencyCase {

    private final String id;
    private final EmergencyType type;
    private final EmergencySeverity severity;
    private final String pickupLocationNodeId;
    private final Instant createdAt;
    private final EmergencyStatus status;
    private final String assignedAmbulanceId;
    private final String destinationHospitalId;
    private final String description;
    private final List<EmergencyStatusHistoryEntry> statusHistory;
    private final Set<AmbulanceCapability> requiredCapabilities;

    /**
     * Complete primary constructor with explicit required capabilities.
     */
    public EmergencyCase(String id,
                         EmergencyType type,
                         EmergencySeverity severity,
                         String pickupLocationNodeId,
                         Instant createdAt,
                         EmergencyStatus status,
                         String assignedAmbulanceId,
                         String destinationHospitalId,
                         String description,
                         List<EmergencyStatusHistoryEntry> statusHistory,
                         Set<AmbulanceCapability> requiredCapabilities) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Emergency case ID must not be null or blank");
        }
        if (pickupLocationNodeId == null || pickupLocationNodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Pickup location node ID must not be null or blank");
        }

        this.id = id.trim();
        this.type = Objects.requireNonNull(type, "Emergency type must not be null");
        this.severity = Objects.requireNonNull(severity, "Emergency severity must not be null");
        this.pickupLocationNodeId = pickupLocationNodeId.trim();
        this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp must not be null");
        this.status = Objects.requireNonNull(status, "Emergency status must not be null");

        this.assignedAmbulanceId = (assignedAmbulanceId == null || assignedAmbulanceId.trim().isEmpty())
                ? null
                : assignedAmbulanceId.trim();
        this.destinationHospitalId = (destinationHospitalId == null || destinationHospitalId.trim().isEmpty())
                ? null
                : destinationHospitalId.trim();
        this.description = (description == null || description.trim().isEmpty())
                ? type.displayName()
                : description.trim();

        if (statusHistory == null || statusHistory.isEmpty()) {
            this.statusHistory = List.of(new EmergencyStatusHistoryEntry(this.status, this.createdAt, "Initial report"));
        } else {
            this.statusHistory = List.copyOf(statusHistory);
        }

        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            this.requiredCapabilities = deriveDefaultCapabilities(this.type, this.severity);
        } else {
            this.requiredCapabilities = Collections.unmodifiableSet(EnumSet.copyOf(requiredCapabilities));
        }
    }

    /**
     * Primary constructor defaulting to derived required capabilities.
     */
    public EmergencyCase(String id,
                         EmergencyType type,
                         EmergencySeverity severity,
                         String pickupLocationNodeId,
                         Instant createdAt,
                         EmergencyStatus status,
                         String assignedAmbulanceId,
                         String destinationHospitalId,
                         String description,
                         List<EmergencyStatusHistoryEntry> statusHistory) {
        this(id, type, severity, pickupLocationNodeId, createdAt, status, assignedAmbulanceId, destinationHospitalId, description, statusHistory, null);
    }

    /**
     * Standard initial creation constructor (defaults to REPORTED status).
     */
    public EmergencyCase(String id,
                         EmergencyType type,
                         EmergencySeverity severity,
                         String pickupLocationNodeId,
                         Instant createdAt,
                         String description) {
        this(id, type, severity, pickupLocationNodeId, createdAt, EmergencyStatus.REPORTED, null, null, description, null, null);
    }

    private static Set<AmbulanceCapability> deriveDefaultCapabilities(EmergencyType type, EmergencySeverity severity) {
        EnumSet<AmbulanceCapability> set = EnumSet.of(AmbulanceCapability.BASIC_LIFE_SUPPORT);
        if (type == EmergencyType.CARDIAC) {
            set.add(AmbulanceCapability.ADVANCED_LIFE_SUPPORT);
            set.add(AmbulanceCapability.CARDIAC_CARE);
        } else if (type == EmergencyType.FIRE_RESCUE) {
            set.add(AmbulanceCapability.HEAVY_RESCUE);
        } else if (severity == EmergencySeverity.CRITICAL) {
            set.add(AmbulanceCapability.ADVANCED_LIFE_SUPPORT);
        } else if (severity == EmergencySeverity.HIGH && type == EmergencyType.TRAUMA) {
            set.add(AmbulanceCapability.ADVANCED_LIFE_SUPPORT);
        }
        return Collections.unmodifiableSet(set);
    }

    public String getId() {
        return id;
    }

    public EmergencyType getType() {
        return type;
    }

    public EmergencySeverity getSeverity() {
        return severity;
    }

    public String getPickupLocationNodeId() {
        return pickupLocationNodeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public EmergencyStatus getStatus() {
        return status;
    }

    public Optional<String> getAssignedAmbulanceId() {
        return Optional.ofNullable(assignedAmbulanceId);
    }

    public Optional<String> getDestinationHospitalId() {
        return Optional.ofNullable(destinationHospitalId);
    }

    public String getDescription() {
        return description;
    }

    public List<EmergencyStatusHistoryEntry> getStatusHistory() {
        return statusHistory;
    }

    public Set<AmbulanceCapability> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isCompleted() {
        return status == EmergencyStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return status == EmergencyStatus.CANCELLED;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean hasAssignedAmbulance() {
        return assignedAmbulanceId != null;
    }

    public boolean hasDestinationHospital() {
        return destinationHospitalId != null;
    }

    /**
     * Returns an updated copy with a new status and appended history entry.
     */
    public EmergencyCase withStatus(EmergencyStatus newStatus, Instant timestamp, String notes) {
        Objects.requireNonNull(newStatus, "New status must not be null");
        Objects.requireNonNull(timestamp, "Transition timestamp must not be null");

        List<EmergencyStatusHistoryEntry> updatedHistory = new ArrayList<>(this.statusHistory);
        updatedHistory.add(new EmergencyStatusHistoryEntry(newStatus, timestamp, notes));

        return new EmergencyCase(
                this.id,
                this.type,
                this.severity,
                this.pickupLocationNodeId,
                this.createdAt,
                newStatus,
                this.assignedAmbulanceId,
                this.destinationHospitalId,
                this.description,
                updatedHistory,
                this.requiredCapabilities
        );
    }

    /**
     * Returns an updated copy with a revised triage severity rating.
     */
    public EmergencyCase withSeverity(EmergencySeverity newSeverity) {
        Objects.requireNonNull(newSeverity, "Emergency severity must not be null");
        return new EmergencyCase(
                this.id,
                this.type,
                newSeverity,
                this.pickupLocationNodeId,
                this.createdAt,
                this.status,
                this.assignedAmbulanceId,
                this.destinationHospitalId,
                this.description,
                this.statusHistory,
                this.requiredCapabilities
        );
    }

    /**
     * Returns an updated copy with an assigned ambulance.
     */
    public EmergencyCase withAssignedAmbulance(String ambulanceId) {
        if (ambulanceId == null || ambulanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ambulance ID must not be null or blank");
        }
        return new EmergencyCase(
                this.id,
                this.type,
                this.severity,
                this.pickupLocationNodeId,
                this.createdAt,
                this.status,
                ambulanceId.trim(),
                this.destinationHospitalId,
                this.description,
                this.statusHistory,
                this.requiredCapabilities
        );
    }

    /**
     * Returns an updated copy with assigned ambulance cleared.
     */
    public EmergencyCase withUnassignedAmbulance() {
        return new EmergencyCase(
                this.id,
                this.type,
                this.severity,
                this.pickupLocationNodeId,
                this.createdAt,
                this.status,
                null,
                this.destinationHospitalId,
                this.description,
                this.statusHistory,
                this.requiredCapabilities
        );
    }

    /**
     * Returns an updated copy with destination hospital node ID set.
     */
    public EmergencyCase withDestinationHospital(String hospitalNodeId) {
        if (hospitalNodeId == null || hospitalNodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital node ID must not be null or blank");
        }
        return new EmergencyCase(
                this.id,
                this.type,
                this.severity,
                this.pickupLocationNodeId,
                this.createdAt,
                this.status,
                this.assignedAmbulanceId,
                hospitalNodeId.trim(),
                this.description,
                this.statusHistory,
                this.requiredCapabilities
        );
    }

    /**
     * Returns an updated copy with explicit required clinical capabilities.
     */
    public EmergencyCase withRequiredCapabilities(Set<AmbulanceCapability> newCapabilities) {
        return new EmergencyCase(
                this.id,
                this.type,
                this.severity,
                this.pickupLocationNodeId,
                this.createdAt,
                this.status,
                this.assignedAmbulanceId,
                this.destinationHospitalId,
                this.description,
                this.statusHistory,
                newCapabilities
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private EmergencyType type;
        private EmergencySeverity severity;
        private String pickupLocationNodeId;
        private Instant createdAt;
        private EmergencyStatus status = EmergencyStatus.REPORTED;
        private String assignedAmbulanceId;
        private String destinationHospitalId;
        private String description;
        private List<EmergencyStatusHistoryEntry> statusHistory;
        private Set<AmbulanceCapability> requiredCapabilities;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(EmergencyType type) {
            this.type = type;
            return this;
        }

        public Builder severity(EmergencySeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder pickupLocationNodeId(String pickupLocationNodeId) {
            this.pickupLocationNodeId = pickupLocationNodeId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder status(EmergencyStatus status) {
            this.status = status;
            return this;
        }

        public Builder assignedAmbulanceId(String assignedAmbulanceId) {
            this.assignedAmbulanceId = assignedAmbulanceId;
            return this;
        }

        public Builder destinationHospitalId(String destinationHospitalId) {
            this.destinationHospitalId = destinationHospitalId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder statusHistory(List<EmergencyStatusHistoryEntry> statusHistory) {
            this.statusHistory = statusHistory;
            return this;
        }

        public Builder requiredCapabilities(Set<AmbulanceCapability> requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities;
            return this;
        }

        public EmergencyCase build() {
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new EmergencyCase(id, type, severity, pickupLocationNodeId, createdAt,
                    status, assignedAmbulanceId, destinationHospitalId, description, statusHistory, requiredCapabilities);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmergencyCase that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("EmergencyCase[id='%s', type=%s, severity=%s, pickup='%s', status=%s, amb='%s', hosp='%s', reqCaps=%s]",
                id, type, severity, pickupLocationNodeId, status,
                assignedAmbulanceId != null ? assignedAmbulanceId : "none",
                destinationHospitalId != null ? destinationHospitalId : "none",
                requiredCapabilities);
    }
}
