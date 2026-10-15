package com.resqroute.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable domain model representing a physical incident affecting an individual roadway segment.
 *
 * <p>Tracks incident classification (accident, closure, construction, blockage), severity rating,
 * affected directed roadway, creation timestamp, and optional expiration time.</p>
 */
public final class RoadIncident {

    private final String id;
    private final EdgeKey affectedRoad;
    private final IncidentType incidentType;
    private final IncidentSeverity severity;
    private final Instant createdAt;
    private final Instant expiryTime;
    private final String description;
    private final boolean blocksRoad;

    /**
     * Primary constructor with complete incident parameters.
     *
     * @param id           Unique incident identifier; must not be null or blank.
     * @param affectedRoad Key identifying the directed road segment; must not be null.
     * @param incidentType Classification of the incident; must not be null.
     * @param severity     Severity rating; must not be null.
     * @param createdAt    Creation timestamp; must not be null.
     * @param expiryTime   Optional expiration timestamp; if present, must not be before createdAt.
     * @param description  Descriptive summary of the incident; defaults to incident type name if blank.
     * @param blocksRoad   Whether this incident renders the road impassable to emergency vehicles.
     */
    public RoadIncident(String id,
                        EdgeKey affectedRoad,
                        IncidentType incidentType,
                        IncidentSeverity severity,
                        Instant createdAt,
                        Instant expiryTime,
                        String description,
                        boolean blocksRoad) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Incident ID must not be null or blank");
        }
        this.id = id.trim();
        this.affectedRoad = Objects.requireNonNull(affectedRoad, "Affected road must not be null");
        this.incidentType = Objects.requireNonNull(incidentType, "Incident type must not be null");
        this.severity = Objects.requireNonNull(severity, "Incident severity must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp must not be null");

        if (expiryTime != null && expiryTime.isBefore(createdAt)) {
            throw new IllegalArgumentException("Expiry time cannot be before creation time: expiry="
                    + expiryTime + ", created=" + createdAt);
        }
        this.expiryTime = expiryTime;
        this.description = (description == null || description.trim().isEmpty())
                ? incidentType.name().replace('_', ' ')
                : description.trim();
        this.blocksRoad = blocksRoad;
    }

    /**
     * Constructs a road-blocking incident without explicit expiry.
     */
    public RoadIncident(String id,
                        EdgeKey affectedRoad,
                        IncidentType incidentType,
                        IncidentSeverity severity,
                        Instant createdAt,
                        String description) {
        this(id, affectedRoad, incidentType, severity, createdAt, null, description, true);
    }

    public static RoadIncident of(String id,
                                  String sourceId,
                                  String destinationId,
                                  IncidentType type,
                                  IncidentSeverity severity,
                                  Instant createdAt,
                                  Instant expiryTime,
                                  String description) {
        return new RoadIncident(id, EdgeKey.of(sourceId, destinationId), type, severity, createdAt, expiryTime, description, true);
    }

    public static RoadIncident of(String id,
                                  String sourceId,
                                  String destinationId,
                                  IncidentType type,
                                  IncidentSeverity severity,
                                  Instant createdAt,
                                  String description) {
        return new RoadIncident(id, EdgeKey.of(sourceId, destinationId), type, severity, createdAt, null, description, true);
    }

    public static RoadIncident accident(String id, String sourceId, String destinationId, Instant createdAt) {
        return new RoadIncident(id, EdgeKey.of(sourceId, destinationId), IncidentType.ACCIDENT,
                IncidentSeverity.HIGH, createdAt, null, "Traffic collision blocking lane", true);
    }

    public static RoadIncident roadClosure(String id, String sourceId, String destinationId, Instant createdAt) {
        return new RoadIncident(id, EdgeKey.of(sourceId, destinationId), IncidentType.ROAD_CLOSURE,
                IncidentSeverity.CRITICAL, createdAt, null, "Full road closure", true);
    }

    public static RoadIncident construction(String id, String sourceId, String destinationId, Instant createdAt, Instant expiryTime) {
        return new RoadIncident(id, EdgeKey.of(sourceId, destinationId), IncidentType.CONSTRUCTION,
                IncidentSeverity.MEDIUM, createdAt, expiryTime, "Active road construction work", true);
    }

    public static RoadIncident temporaryBlockage(String id, String sourceId, String destinationId, Instant createdAt, Instant expiryTime) {
        return new RoadIncident(id, EdgeKey.of(sourceId, destinationId), IncidentType.TEMPORARY_BLOCKAGE,
                IncidentSeverity.HIGH, createdAt, expiryTime, "Temporary roadway blockage", true);
    }

    public String getId() {
        return id;
    }

    public EdgeKey getAffectedRoad() {
        return affectedRoad;
    }

    public String getSourceNodeId() {
        return affectedRoad.sourceId();
    }

    public String getDestinationNodeId() {
        return affectedRoad.destinationId();
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Optional<Instant> getExpiryTime() {
        return Optional.ofNullable(expiryTime);
    }

    public String getDescription() {
        return description;
    }

    public boolean isBlocksRoad() {
        return blocksRoad;
    }

    /**
     * Checks if this incident has expired relative to the provided timestamp.
     *
     * @param currentTime Current simulation or clock time; must not be null.
     * @return true if an expiry time was specified and currentTime is at or after expiryTime.
     */
    public boolean isExpired(Instant currentTime) {
        Objects.requireNonNull(currentTime, "Current time must not be null");
        return expiryTime != null && !currentTime.isBefore(expiryTime);
    }

    /**
     * Checks if this incident remains active at the specified timestamp.
     */
    public boolean isActive(Instant currentTime) {
        return !isExpired(currentTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoadIncident that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("RoadIncident{id='%s', type=%s, severity=%s, road='%s'->'%s', blocks=%s, created=%s, expires=%s, desc='%s'}",
                id, incidentType, severity, affectedRoad.sourceId(), affectedRoad.destinationId(),
                blocksRoad, createdAt, expiryTime != null ? expiryTime : "NONE", description);
    }
}
