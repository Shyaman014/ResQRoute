package com.resqroute.service;

import com.resqroute.exception.AmbulanceUnavailableException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Ambulance;
import com.resqroute.model.AmbulanceStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing ambulance fleet state, operational readiness, and vehicle-case bindings.
 *
 * <p>Enforces fleet-management integrity rules:
 * <ul>
 *   <li>Ambulances must be situated at valid road network nodes.</li>
 *   <li>Only ambulances in {@link AmbulanceStatus#AVAILABLE} status may be assigned to emergency cases.</li>
 *   <li>Assigning an ambulance transitions its state to {@link AmbulanceStatus#ASSIGNED} and binds its case ID.</li>
 *   <li>Releasing an ambulance clears the mission link and restores readiness to {@link AmbulanceStatus#AVAILABLE}.</li>
 * </ul>
 * </p>
 */
public class AmbulanceFleetService {

    private final RoadGraph roadGraph;
    private final Map<String, Ambulance> fleetById;

    public AmbulanceFleetService(RoadGraph roadGraph) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.fleetById = new ConcurrentHashMap<>();
    }

    /**
     * Registers a new ambulance into the fleet.
     *
     * @param ambulance The ambulance to register; must not be null.
     * @return The registered ambulance.
     */
    public Ambulance registerAmbulance(Ambulance ambulance) {
        Objects.requireNonNull(ambulance, "Ambulance must not be null");
        String id = ambulance.getId();

        if (fleetById.containsKey(id)) {
            throw new IllegalArgumentException("Ambulance already registered with ID: '" + id + "'");
        }

        String location = ambulance.getCurrentLocationNodeId();
        if (!roadGraph.containsNode(location)) {
            throw new NodeNotFoundException(location);
        }

        fleetById.put(id, ambulance);
        return ambulance;
    }

    /**
     * Convenience registration helper creating an available ambulance at a designated station node.
     */
    public Ambulance registerAmbulance(String id, String initialLocationNodeId) {
        return registerAmbulance(new Ambulance(id, initialLocationNodeId));
    }

    /**
     * Retrieves an ambulance by ID if registered.
     */
    public Optional<Ambulance> getAmbulance(String ambulanceId) {
        if (ambulanceId == null || ambulanceId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fleetById.get(ambulanceId.trim()));
    }

    /**
     * Retrieves an ambulance by ID or throws AmbulanceUnavailableException if not found.
     */
    public Ambulance getAmbulanceOrThrow(String ambulanceId) {
        return getAmbulance(ambulanceId).orElseThrow(() ->
                new AmbulanceUnavailableException(ambulanceId, "Ambulance is not registered in the fleet"));
    }

    /**
     * Lists all registered ambulances in the fleet.
     */
    public List<Ambulance> getAllAmbulances() {
        return List.copyOf(fleetById.values());
    }

    /**
     * Lists all ambulances currently available for dispatch.
     */
    public List<Ambulance> getAvailableAmbulances() {
        return fleetById.values().stream()
                .filter(Ambulance::isAvailable)
                .toList();
    }

    /**
     * Checks if a specified ambulance is currently available for dispatch.
     */
    public boolean isAmbulanceAvailable(String ambulanceId) {
        return getAmbulance(ambulanceId).map(Ambulance::isAvailable).orElse(false);
    }

    /**
     * Assigns an available ambulance to a designated emergency case.
     *
     * @param ambulanceId Unique ambulance identifier.
     * @param caseId      Unique emergency case identifier.
     * @return The updated assigned Ambulance instance.
     * @throws AmbulanceUnavailableException if the ambulance is missing, not available, or already assigned.
     */
    public synchronized Ambulance assignAmbulanceToCase(String ambulanceId, String caseId) {
        Ambulance ambulance = getAmbulanceOrThrow(ambulanceId);

        if (!ambulance.isAvailable()) {
            throw new AmbulanceUnavailableException(ambulanceId,
                    String.format("Current status is %s (assignedCase: %s)",
                            ambulance.getStatus(), ambulance.getAssignedCaseId().orElse("none")));
        }

        Ambulance assigned = ambulance.withAssignedCase(caseId);
        fleetById.put(ambulanceId, assigned);
        return assigned;
    }

    /**
     * Updates an ambulance's operational status.
     */
    public synchronized Ambulance updateAmbulanceStatus(String ambulanceId, AmbulanceStatus newStatus) {
        Ambulance ambulance = getAmbulanceOrThrow(ambulanceId);
        Ambulance updated = ambulance.withStatus(newStatus);
        fleetById.put(ambulanceId, updated);
        return updated;
    }

    /**
     * Updates an ambulance's physical road network location node.
     */
    public synchronized Ambulance updateLocation(String ambulanceId, String nodeLocationId) {
        Ambulance ambulance = getAmbulanceOrThrow(ambulanceId);
        if (nodeLocationId == null || !roadGraph.containsNode(nodeLocationId.trim())) {
            throw new NodeNotFoundException(nodeLocationId);
        }
        Ambulance updated = ambulance.withLocation(nodeLocationId.trim());
        fleetById.put(ambulanceId, updated);
        return updated;
    }

    /**
     * Releases an assigned ambulance back to available status, clearing active case bindings.
     */
    public synchronized Ambulance releaseAmbulance(String ambulanceId) {
        Ambulance ambulance = getAmbulanceOrThrow(ambulanceId);
        Ambulance released = ambulance.withReleased();
        fleetById.put(ambulanceId, released);
        return released;
    }

    /**
     * Retrieves the ambulance currently assigned to a given case ID, if any.
     */
    public Optional<Ambulance> getAmbulanceAssignedToCase(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanCaseId = caseId.trim();
        return fleetById.values().stream()
                .filter(amb -> amb.getAssignedCaseId().map(id -> id.equals(cleanCaseId)).orElse(false))
                .findFirst();
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public int getFleetSize() {
        return fleetById.size();
    }
}
