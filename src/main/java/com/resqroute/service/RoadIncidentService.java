package com.resqroute.service;

import com.resqroute.exception.DuplicateIncidentException;
import com.resqroute.exception.InvalidEdgeException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Service orchestrating the complete lifecycle of road incidents and dynamic road availability.
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li><b>Separation of Concerns:</b> Manages incident tracking, severity, and expiration cleanly
 *       apart from core graph algorithms.</li>
 *   <li><b>Multiple Incidents per Road:</b> Supports multiple concurrent incidents on the same road segment;
 *       a road is reopened only when <i>all</i> blocking incidents have been resolved or expired.</li>
 *   <li><b>Directional Precision:</b> Accurately isolates directed and bidirectional closures without
 *       affecting unrelated adjacent roads.</li>
 *   <li><b>Topology Preservation:</b> Roads are disabled via availability flags; distances and baseline
 *       travel times are never mutated or deleted.</li>
 * </ul>
 * </p>
 */
public class RoadIncidentService {

    private final RoadGraph graph;
    private final Map<String, RoadIncident> activeIncidentsById;
    private final Map<EdgeKey, Set<RoadIncident>> activeIncidentsByRoad;

    /**
     * Constructs a RoadIncidentService managing incidents across the provided graph.
     *
     * @param graph Road network graph; must not be null.
     */
    public RoadIncidentService(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "RoadGraph must not be null");
        this.activeIncidentsById = new LinkedHashMap<>();
        this.activeIncidentsByRoad = new HashMap<>();
    }

    /**
     * Reports and registers an active road incident, updating road availability accordingly.
     *
     * @param incident The incident to record; must not be null.
     * @return The registered incident.
     * @throws DuplicateIncidentException if an incident with the same ID is already active.
     * @throws NodeNotFoundException      if either endpoint node does not exist in the graph.
     * @throws InvalidEdgeException       if the specified road segment does not exist.
     */
    public RoadIncident reportIncident(RoadIncident incident) {
        Objects.requireNonNull(incident, "Incident must not be null");

        String id = incident.getId();
        if (activeIncidentsById.containsKey(id)) {
            throw new DuplicateIncidentException(id);
        }

        String src = incident.getSourceNodeId();
        String dst = incident.getDestinationNodeId();

        if (!graph.containsNode(src)) {
            throw new NodeNotFoundException(src);
        }
        if (!graph.containsNode(dst)) {
            throw new NodeNotFoundException(dst);
        }
        if (!graph.containsEdge(src, dst)) {
            throw new InvalidEdgeException(String.format("Cannot register incident: road from '%s' to '%s' does not exist in network.", src, dst));
        }

        activeIncidentsById.put(id, incident);
        activeIncidentsByRoad.computeIfAbsent(incident.getAffectedRoad(), k -> new LinkedHashSet<>()).add(incident);

        // If the incident is marked as blocking, update road availability
        if (incident.isBlocksRoad()) {
            graph.setRoadAvailability(src, dst, false);
        }

        return incident;
    }

    /**
     * Convenience method to report a bidirectional incident affecting both traffic directions.
     *
     * @param baseId      Base incident identifier prefix.
     * @param type        Classification of the incident.
     * @param nodeA       First node identifier.
     * @param nodeB       Second node identifier.
     * @param severity    Severity rating.
     * @param createdAt   Creation timestamp.
     * @param expiryTime  Optional expiration timestamp.
     * @param description Descriptive summary.
     * @return List of the two registered directed incidents (forward and reverse).
     */
    public List<RoadIncident> reportBidirectionalIncident(String baseId,
                                                          IncidentType type,
                                                          String nodeA,
                                                          String nodeB,
                                                          IncidentSeverity severity,
                                                          Instant createdAt,
                                                          Instant expiryTime,
                                                          String description) {
        Objects.requireNonNull(baseId, "Base ID must not be null");
        RoadIncident forward = new RoadIncident(baseId + "-FORWARD", EdgeKey.of(nodeA, nodeB), type, severity, createdAt, expiryTime, description, true);
        RoadIncident reverse = new RoadIncident(baseId + "-REVERSE", EdgeKey.of(nodeB, nodeA), type, severity, createdAt, expiryTime, description, true);

        reportIncident(forward);
        reportIncident(reverse);

        return List.of(forward, reverse);
    }

    /**
     * Convenience method to report a bidirectional incident without explicit expiry.
     */
    public List<RoadIncident> reportBidirectionalIncident(String baseId,
                                                          IncidentType type,
                                                          String nodeA,
                                                          String nodeB,
                                                          IncidentSeverity severity,
                                                          Instant createdAt,
                                                          String description) {
        return reportBidirectionalIncident(baseId, type, nodeA, nodeB, severity, createdAt, null, description);
    }

    /**
     * Resolves and clears an active incident by its identifier.
     * Restores road availability only if no other active blocking incidents remain on the affected road.
     *
     * @param incidentId Unique ID of the incident to resolve.
     * @return Optional containing the resolved incident, or empty if not found.
     */
    public Optional<RoadIncident> resolveIncident(String incidentId) {
        if (incidentId == null || incidentId.trim().isEmpty()) {
            return Optional.empty();
        }

        RoadIncident incident = activeIncidentsById.remove(incidentId.trim());
        if (incident == null) {
            return Optional.empty();
        }

        EdgeKey road = incident.getAffectedRoad();
        Set<RoadIncident> incidentsOnRoad = activeIncidentsByRoad.get(road);

        if (incidentsOnRoad != null) {
            incidentsOnRoad.remove(incident);
            // Check if any other blocking incidents remain on this road
            boolean stillBlocked = incidentsOnRoad.stream().anyMatch(RoadIncident::isBlocksRoad);
            if (!stillBlocked) {
                graph.setRoadAvailability(road.sourceId(), road.destinationId(), true);
            }
            if (incidentsOnRoad.isEmpty()) {
                activeIncidentsByRoad.remove(road);
            }
        }

        return Optional.of(incident);
    }

    /**
     * Resolves a paired bidirectional incident by its base identifier.
     */
    public List<RoadIncident> resolveBidirectionalIncident(String baseId) {
        if (baseId == null || baseId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanBase = baseId.trim();
        List<RoadIncident> resolved = new ArrayList<>();
        resolveIncident(cleanBase + "-FORWARD").ifPresent(resolved::add);
        resolveIncident(cleanBase + "-REVERSE").ifPresent(resolved::add);
        return Collections.unmodifiableList(resolved);
    }

    /**
     * Clears all incidents currently affecting a specific directed road segment and reopens it.
     *
     * @param sourceId      Source node identifier.
     * @param destinationId Destination node identifier.
     * @return List of cleared incidents.
     */
    public List<RoadIncident> clearIncidentsOnRoad(String sourceId, String destinationId) {
        EdgeKey key = EdgeKey.of(sourceId, destinationId);
        Set<RoadIncident> incidents = activeIncidentsByRoad.remove(key);
        if (incidents == null || incidents.isEmpty()) {
            return Collections.emptyList();
        }

        List<RoadIncident> removed = new ArrayList<>();
        for (RoadIncident inc : incidents) {
            activeIncidentsById.remove(inc.getId());
            removed.add(inc);
        }

        graph.setRoadAvailability(sourceId, destinationId, true);
        return Collections.unmodifiableList(removed);
    }

    /**
     * Clears all incidents on a bidirectional roadway in both directions and reopens both.
     */
    public List<RoadIncident> clearIncidentsOnBidirectionalRoad(String nodeAId, String nodeBId) {
        List<RoadIncident> removed = new ArrayList<>();
        removed.addAll(clearIncidentsOnRoad(nodeAId, nodeBId));
        removed.addAll(clearIncidentsOnRoad(nodeBId, nodeAId));
        return Collections.unmodifiableList(removed);
    }

    /**
     * Scans for and resolves all incidents whose expiry timestamp has passed relative to the given time.
     * Restores roads whose blocking incidents have all expired.
     *
     * @param currentTime Current simulation or clock timestamp; must not be null.
     * @return List of expired and resolved incidents.
     */
    public List<RoadIncident> expireIncidents(Instant currentTime) {
        Objects.requireNonNull(currentTime, "Current time must not be null");

        List<String> expiredIds = new ArrayList<>();
        for (RoadIncident incident : activeIncidentsById.values()) {
            if (incident.isExpired(currentTime)) {
                expiredIds.add(incident.getId());
            }
        }

        List<RoadIncident> resolved = new ArrayList<>();
        for (String id : expiredIds) {
            resolveIncident(id).ifPresent(resolved::add);
        }

        return Collections.unmodifiableList(resolved);
    }

    /**
     * Checks if a directed road segment is currently blocked by one or more active incidents.
     */
    public boolean isRoadBlocked(String sourceId, String destinationId) {
        if (!graph.containsEdge(sourceId, destinationId)) {
            return true;
        }
        if (graph.isRoadClosed(sourceId, destinationId)) {
            return true;
        }
        EdgeKey key = EdgeKey.of(sourceId, destinationId);
        Set<RoadIncident> incidents = activeIncidentsByRoad.get(key);
        return incidents != null && incidents.stream().anyMatch(RoadIncident::isBlocksRoad);
    }

    /**
     * Retrieves an active incident by its unique ID.
     */
    public Optional<RoadIncident> getIncident(String incidentId) {
        if (incidentId == null || incidentId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeIncidentsById.get(incidentId.trim()));
    }

    /**
     * Retrieves all active incidents currently recorded on a directed road segment.
     */
    public List<RoadIncident> getIncidentsOnRoad(String sourceId, String destinationId) {
        EdgeKey key = EdgeKey.of(sourceId, destinationId);
        Set<RoadIncident> incidents = activeIncidentsByRoad.get(key);
        if (incidents == null || incidents.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(incidents);
    }

    /**
     * Retrieves an unmodifiable view of all active incidents across the network.
     */
    public Collection<RoadIncident> getActiveIncidents() {
        return Collections.unmodifiableCollection(activeIncidentsById.values());
    }

    /**
     * Returns total count of currently active incidents.
     */
    public int getActiveIncidentCount() {
        return activeIncidentsById.size();
    }

    /**
     * Clears all incidents across the entire network and restores road availability on all roads.
     */
    public void clearAllIncidents() {
        for (EdgeKey road : activeIncidentsByRoad.keySet()) {
            if (graph.containsEdge(road.sourceId(), road.destinationId())) {
                graph.setRoadAvailability(road.sourceId(), road.destinationId(), true);
            }
        }
        activeIncidentsById.clear();
        activeIncidentsByRoad.clear();
    }

    public RoadGraph getGraph() {
        return graph;
    }
}
