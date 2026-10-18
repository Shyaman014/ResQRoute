package com.resqroute.service;

import com.resqroute.exception.DuplicateCaseException;
import com.resqroute.exception.EmergencyCaseNotFoundException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.lifecycle.EmergencyLifecycleRules;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * High-level orchestration service for managing the complete lifecycle of emergency cases.
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li><b>Separation of Concerns:</b> Separates domain state, lifecycle transition rules,
 *       and fleet management bindings into discrete architectural components.</li>
 *   <li><b>Integrity Protection:</b> Strictly prevents duplicate case IDs and rejects pickup/hospital
 *       locations not present in the road network graph.</li>
 *   <li><b>Lifecycle Discipline:</b> Rejects invalid status transitions via {@link EmergencyLifecycleRules}
 *       while preserving a fully chronological, timestamped transition history.</li>
 *   <li><b>Safe Cancellation:</b> Permits safe revocation only for eligible pre-transport cases and
 *       automatically frees assigned ambulances back to available status.</li>
 *   <li><b>Fleet Consistency:</b> Synchronizes emergency case progression with ambulance vehicle statuses
 *       across dispatch, transit, scene arrival, and handover.</li>
 *   <li><b>Deterministic Clocks:</b> Accepts an injected {@link Clock} for reproducible time-based testing.</li>
 * </ul>
 * </p>
 */
public class EmergencyManagementService {

    private final RoadGraph roadGraph;
    private final AmbulanceFleetService fleetService;
    private final Clock clock;
    private final Map<String, EmergencyCase> casesById;

    /**
     * Constructs an EmergencyManagementService with complete dependency injection.
     *
     * @param roadGraph    Road network graph for spatial waypoint validation; must not be null.
     * @param fleetService Fleet management service for ambulance coordination; must not be null.
     * @param clock        Clock for generating deterministic timestamps; must not be null.
     */
    public EmergencyManagementService(RoadGraph roadGraph, AmbulanceFleetService fleetService, Clock clock) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.fleetService = Objects.requireNonNull(fleetService, "AmbulanceFleetService must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.casesById = new ConcurrentHashMap<>();
    }

    /**
     * Constructs an EmergencyManagementService with road graph and fleet service, defaulting to UTC system clock.
     */
    public EmergencyManagementService(RoadGraph roadGraph, AmbulanceFleetService fleetService) {
        this(roadGraph, fleetService, Clock.systemUTC());
    }

    /**
     * Constructs an EmergencyManagementService with road graph and clock, creating a default fleet service.
     */
    public EmergencyManagementService(RoadGraph roadGraph, Clock clock) {
        this(roadGraph, new AmbulanceFleetService(roadGraph), clock);
    }

    /**
     * Constructs an EmergencyManagementService with default fleet service and UTC system clock.
     */
    public EmergencyManagementService(RoadGraph roadGraph) {
        this(roadGraph, new AmbulanceFleetService(roadGraph), Clock.systemUTC());
    }

    /**
     * Creates and registers a new emergency case in the system.
     *
     * @param emergencyCase The emergency case to record; must not be null.
     * @return The registered emergency case.
     * @throws DuplicateCaseException if an emergency case with the same ID is already registered.
     * @throws NodeNotFoundException  if pickup location or destination hospital does not exist in road network.
     */
    public synchronized EmergencyCase createCase(EmergencyCase emergencyCase) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");

        String id = emergencyCase.getId();
        if (casesById.containsKey(id)) {
            throw new DuplicateCaseException(id);
        }

        String pickupLocation = emergencyCase.getPickupLocationNodeId();
        if (!roadGraph.containsNode(pickupLocation)) {
            throw new NodeNotFoundException(pickupLocation);
        }

        if (emergencyCase.hasDestinationHospital()) {
            String hospitalId = emergencyCase.getDestinationHospitalId().orElseThrow();
            if (!roadGraph.containsNode(hospitalId)) {
                throw new NodeNotFoundException(hospitalId);
            }
        }

        // If an ambulance is already assigned upon creation, validate and assign in fleet
        if (emergencyCase.hasAssignedAmbulance()) {
            String ambId = emergencyCase.getAssignedAmbulanceId().orElseThrow();
            fleetService.assignAmbulanceToCase(ambId, id);
        }

        casesById.put(id, emergencyCase);
        return emergencyCase;
    }

    /**
     * Convenience method to register a new emergency case from core parameters.
     */
    public EmergencyCase createCase(String id,
                                  EmergencyType type,
                                  EmergencySeverity severity,
                                  String pickupLocationNodeId,
                                  String description) {
        Instant now = clock.instant();
        EmergencyCase emergencyCase = new EmergencyCase(id, type, severity, pickupLocationNodeId, now, description);
        return createCase(emergencyCase);
    }

    /**
     * Retrieves an emergency case by ID if registered.
     */
    public Optional<EmergencyCase> getCase(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(casesById.get(caseId.trim()));
    }

    /**
     * Retrieves an emergency case by ID, throwing EmergencyCaseNotFoundException if not found.
     */
    public EmergencyCase getCaseOrThrow(String caseId) {
        return getCase(caseId).orElseThrow(() -> new EmergencyCaseNotFoundException(caseId));
    }

    /**
     * Lists all registered emergency cases.
     */
    public List<EmergencyCase> getAllCases() {
        return List.copyOf(casesById.values());
    }

    /**
     * Lists all active emergency cases (excluding terminal COMPLETED and CANCELLED cases).
     */
    public List<EmergencyCase> getActiveCases() {
        return casesById.values().stream()
                .filter(EmergencyCase::isActive)
                .toList();
    }

    /**
     * Lists all completed emergency cases.
     */
    public List<EmergencyCase> getCompletedCases() {
        return casesById.values().stream()
                .filter(EmergencyCase::isCompleted)
                .toList();
    }

    /**
     * Lists all cancelled emergency cases.
     */
    public List<EmergencyCase> getCancelledCases() {
        return casesById.values().stream()
                .filter(EmergencyCase::isCancelled)
                .toList();
    }

    /**
     * Filters cases by criteria. Any null argument acts as a wildcard (matching all).
     */
    public List<EmergencyCase> filterCases(EmergencySeverity severity, EmergencyType type, EmergencyStatus status) {
        return casesById.values().stream()
                .filter(c -> severity == null || c.getSeverity() == severity)
                .filter(c -> type == null || c.getType() == type)
                .filter(c -> status == null || c.getStatus() == status)
                .toList();
    }

    /**
     * Filters cases by triage severity.
     */
    public List<EmergencyCase> getCasesBySeverity(EmergencySeverity severity) {
        Objects.requireNonNull(severity, "Severity must not be null");
        return filterCases(severity, null, null);
    }

    /**
     * Filters cases by emergency classification type.
     */
    public List<EmergencyCase> getCasesByType(EmergencyType type) {
        Objects.requireNonNull(type, "Type must not be null");
        return filterCases(null, type, null);
    }

    /**
     * Filters cases by current lifecycle status.
     */
    public List<EmergencyCase> getCasesByStatus(EmergencyStatus status) {
        Objects.requireNonNull(status, "Status must not be null");
        return filterCases(null, null, status);
    }

    /**
     * Generic predicate filter over all registered emergency cases.
     */
    public List<EmergencyCase> filter(Predicate<EmergencyCase> predicate) {
        Objects.requireNonNull(predicate, "Predicate must not be null");
        return casesById.values().stream()
                .filter(predicate)
                .toList();
    }

    /**
     * Retrieves all active emergency cases ranked according to triage priority (highest urgency first).
     */
    public List<EmergencyCase> getActiveCasesRankedByPriority() {
        List<EmergencyCase> active = new ArrayList<>(getActiveCases());
        active.sort(EmergencyPriorityComparator.INSTANCE);
        return Collections.unmodifiableList(active);
    }

    /**
     * Returns the singleton priority comparator for external queue and dispatch implementations.
     */
    public Comparator<EmergencyCase> getPriorityComparator() {
        return EmergencyPriorityComparator.INSTANCE;
    }

    /**
     * Updates an emergency case's triage severity rating.
     *
     * @param caseId      Unique emergency case identifier.
     * @param newSeverity The new severity rating; must not be null.
     * @return The updated EmergencyCase instance.
     */
    public synchronized EmergencyCase updateCaseSeverity(String caseId, EmergencySeverity newSeverity) {
        Objects.requireNonNull(newSeverity, "Severity must not be null");
        EmergencyCase current = getCaseOrThrow(caseId);
        EmergencyCase updated = current.withSeverity(newSeverity);
        casesById.put(caseId, updated);
        return updated;
    }

    /**
     * Updates an emergency case's lifecycle status through validated state transitions.
     *
     * @param caseId    Unique emergency case identifier.
     * @param newStatus Proposed target status.
     * @param notes     Optional transition note or rationale.
     * @return The updated EmergencyCase instance.
     */
    public synchronized EmergencyCase updateCaseStatus(String caseId, EmergencyStatus newStatus, String notes) {
        EmergencyCase current = getCaseOrThrow(caseId);
        EmergencyLifecycleRules.validateTransition(current, newStatus);

        Instant transitionTime = clock.instant();
        EmergencyCase updated = current.withStatus(newStatus, transitionTime, notes);

        // Synchronize ambulance fleet status if ambulance is assigned
        if (current.hasAssignedAmbulance()) {
            String ambId = current.getAssignedAmbulanceId().orElseThrow();
            switch (newStatus) {
                case AMBULANCE_EN_ROUTE -> fleetService.updateAmbulanceStatus(ambId, AmbulanceStatus.EN_ROUTE);
                case PATIENT_PICKED_UP -> {
                    fleetService.updateAmbulanceStatus(ambId, AmbulanceStatus.ON_SCENE);
                    fleetService.updateLocation(ambId, current.getPickupLocationNodeId());
                }
                case TRANSPORTING -> fleetService.updateAmbulanceStatus(ambId, AmbulanceStatus.TRANSPORTING);
                case COMPLETED -> {
                    current.getDestinationHospitalId().ifPresent(hosp -> fleetService.updateLocation(ambId, hosp));
                    fleetService.releaseAmbulance(ambId);
                }
                case CANCELLED -> fleetService.releaseAmbulance(ambId);
                case WAITING_FOR_DISPATCH -> {
                    fleetService.releaseAmbulance(ambId);
                    updated = updated.withUnassignedAmbulance();
                }
                default -> {
                    // Other states maintain existing fleet binding
                }
            }
        }

        casesById.put(caseId, updated);
        return updated;
    }

    /**
     * Updates status using default transition note.
     */
    public EmergencyCase updateCaseStatus(String caseId, EmergencyStatus newStatus) {
        return updateCaseStatus(caseId, newStatus, "Status transitioned to " + newStatus);
    }

    /**
     * Assigns an available ambulance to an emergency case, transitioning its status to AMBULANCE_ASSIGNED.
     *
     * @param caseId      Target case identifier.
     * @param ambulanceId Ambulance to allocate.
     * @return Updated EmergencyCase instance.
     */
    public synchronized EmergencyCase assignAmbulance(String caseId, String ambulanceId) {
        EmergencyCase current = getCaseOrThrow(caseId);

        if (current.isTerminal()) {
            throw new IllegalStateException("Cannot assign ambulance to terminal case in status " + current.getStatus());
        }

        // If an ambulance was previously assigned, release it first
        if (current.hasAssignedAmbulance()) {
            String prevAmbId = current.getAssignedAmbulanceId().orElseThrow();
            if (!prevAmbId.equals(ambulanceId)) {
                fleetService.releaseAmbulance(prevAmbId);
            }
        }

        // Allocate ambulance in fleet service
        fleetService.assignAmbulanceToCase(ambulanceId, caseId);

        // Update case with assigned ambulance
        EmergencyCase withAmb = current.withAssignedAmbulance(ambulanceId);

        // Transition status if currently pre-assignment
        if (withAmb.getStatus() == EmergencyStatus.REPORTED || withAmb.getStatus() == EmergencyStatus.WAITING_FOR_DISPATCH) {
            Instant now = clock.instant();
            withAmb = withAmb.withStatus(EmergencyStatus.AMBULANCE_ASSIGNED, now, "Ambulance '" + ambulanceId + "' dispatched to case");
        }

        casesById.put(caseId, withAmb);
        return withAmb;
    }

    /**
     * Designates a destination hospital node for this emergency case.
     */
    public synchronized EmergencyCase setDestinationHospital(String caseId, String hospitalNodeId) {
        EmergencyCase current = getCaseOrThrow(caseId);
        if (hospitalNodeId == null || !roadGraph.containsNode(hospitalNodeId.trim())) {
            throw new NodeNotFoundException(hospitalNodeId);
        }
        EmergencyCase updated = current.withDestinationHospital(hospitalNodeId.trim());
        casesById.put(caseId, updated);
        return updated;
    }

    /**
     * Safely cancels an eligible emergency case, freeing any allocated ambulance back to fleet readiness.
     *
     * @param caseId Unique emergency case identifier.
     * @param reason Reason for cancellation.
     * @return The updated cancelled EmergencyCase instance.
     */
    public synchronized EmergencyCase cancelCase(String caseId, String reason) {
        EmergencyCase current = getCaseOrThrow(caseId);
        EmergencyLifecycleRules.validateCancellation(current);

        // Free assigned ambulance if present
        if (current.hasAssignedAmbulance()) {
            String ambId = current.getAssignedAmbulanceId().orElseThrow();
            fleetService.releaseAmbulance(ambId);
        }

        Instant cancellationTime = clock.instant();
        String cancellationNotes = (reason == null || reason.trim().isEmpty())
                ? "Emergency case safely cancelled"
                : "Cancelled: " + reason.trim();

        EmergencyCase cancelled = current.withStatus(EmergencyStatus.CANCELLED, cancellationTime, cancellationNotes);
        casesById.put(caseId, cancelled);
        return cancelled;
    }

    /**
     * Cancels case with default explanation note.
     */
    public EmergencyCase cancelCase(String caseId) {
        return cancelCase(caseId, "Cancelled by dispatch controller");
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public AmbulanceFleetService getFleetService() {
        return fleetService;
    }

    public Clock getClock() {
        return clock;
    }

    public int getActiveCaseCount() {
        return getActiveCases().size();
    }

    public int getTotalCaseCount() {
        return casesById.size();
    }
}
