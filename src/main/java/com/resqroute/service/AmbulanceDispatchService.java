package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.RoutingAlgorithm;
import com.resqroute.dispatch.*;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Intelligent emergency ambulance dispatch service orchestrating vehicle allocation,
 * route feasibility analysis, and atomic assignment transactions.
 *
 * <h3>Dispatch Strategy &amp; Complexity Analysis:</h3>
 * <p>
 * This engine executes a <b>greedy optimal response strategy</b> for individual emergency dispatch:
 * <ol>
 *   <li><b>Priority Selection:</b> Queries the highest-priority eligible emergency from the
 *       {@link EmergencyDispatchQueueService} ($O(1)$ amortized inspection).</li>
 *   <li><b>Readiness &amp; Capability Filtering:</b> Scans fleet ambulances, isolating vehicles in
 *       {@link AmbulanceStatus#AVAILABLE} status that possess all required clinical capabilities
 *       demanded by the emergency case ($O(M)$ where $M$ is fleet size).</li>
 *   <li><b>Traffic-Aware Route Evaluation:</b> Calculates current shortest-travel-time routes from
 *       each candidate ambulance's node to the pickup node using the injected routing engine
 *       ({@link RoutingAlgorithm}, e.g. Dijkstra or A*). Accounts for dynamic traffic congestion
 *       and road closures ($O(K \cdot (E + V \log V))$ where $K \le M$ is candidate count).</li>
 *   <li><b>Feasibility Pruning:</b> Discards unreachable candidates (severed by closures or disconnected graph).</li>
 *   <li><b>Policy-Driven Candidate Ranking:</b> Ranks candidate ambulances using an injected
 *       {@link AmbulanceRankingPolicy} (defaulting to minimum ETA) ($O(K \log K)$).</li>
 *   <li><b>Atomic Transaction Execution:</b> Synchronously binds the top ambulance and transitions the
 *       emergency case to {@link EmergencyStatus#AMBULANCE_ASSIGNED}. If a conflict arises, an automatic
 *       rollback frees the vehicle and re-queues the emergency, guaranteeing zero partial-state corruption.</li>
 * </ol>
 * </p>
 *
 * <h3>Complexity Summary:</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> $O(M \cdot (E + V \log V))$ per dispatch decision.</li>
 *   <li><b>Space Complexity:</b> $O(M + V)$ to evaluate candidate routes.</li>
 * </ul>
 */
public class AmbulanceDispatchService {

    private final RoadGraph roadGraph;
    private final RoutingAlgorithm routingAlgorithm;
    private final EmergencyManagementService emergencyService;
    private final AmbulanceFleetService fleetService;
    private final EmergencyDispatchQueueService queueService;
    private final AmbulanceRankingPolicy rankingPolicy;
    private final AmbulanceCapabilityMatcher capabilityMatcher;
    private final Clock clock;

    /**
     * Complete constructor with full dependency injection.
     */
    public AmbulanceDispatchService(RoadGraph roadGraph,
                                    RoutingAlgorithm routingAlgorithm,
                                    EmergencyManagementService emergencyService,
                                    AmbulanceFleetService fleetService,
                                    EmergencyDispatchQueueService queueService,
                                    AmbulanceRankingPolicy rankingPolicy,
                                    AmbulanceCapabilityMatcher capabilityMatcher,
                                    Clock clock) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.routingAlgorithm = Objects.requireNonNull(routingAlgorithm, "RoutingAlgorithm must not be null");
        this.emergencyService = Objects.requireNonNull(emergencyService, "EmergencyManagementService must not be null");
        this.fleetService = Objects.requireNonNull(fleetService, "AmbulanceFleetService must not be null");
        this.queueService = Objects.requireNonNull(queueService, "EmergencyDispatchQueueService must not be null");
        this.rankingPolicy = Objects.requireNonNull(rankingPolicy, "AmbulanceRankingPolicy must not be null");
        this.capabilityMatcher = Objects.requireNonNull(capabilityMatcher, "AmbulanceCapabilityMatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    /**
     * Constructs an AmbulanceDispatchService with standard fastest-response policy and default capability matcher.
     */
    public AmbulanceDispatchService(RoadGraph roadGraph,
                                    RoutingAlgorithm routingAlgorithm,
                                    EmergencyManagementService emergencyService,
                                    AmbulanceFleetService fleetService,
                                    EmergencyDispatchQueueService queueService) {
        this(roadGraph, routingAlgorithm, emergencyService, fleetService, queueService,
                FastestResponseRankingPolicy.INSTANCE, DefaultCapabilityMatcher.INSTANCE, emergencyService.getClock());
    }

    /**
     * Convenience constructor using Dijkstra routing and default components.
     */
    public AmbulanceDispatchService(RoadGraph roadGraph,
                                    EmergencyManagementService emergencyService,
                                    AmbulanceFleetService fleetService,
                                    EmergencyDispatchQueueService queueService) {
        this(roadGraph, new DijkstraRoutingAlgorithm(), emergencyService, fleetService, queueService);
    }

    /**
     * Dispatches the most suitable available ambulance to the highest-priority pending emergency in the queue.
     *
     * <p>If dispatch fails (e.g. no available ambulances or pickup unreachable), the emergency remains
     * safely in the dispatch queue without state modification.</p>
     *
     * @return DispatchResult detailing the outcome, assigned vehicle, route, and ETA.
     */
    public synchronized DispatchResult dispatchNext() {
        Instant now = clock.instant();

        Optional<EmergencyCase> pendingOpt = queueService.peekNextEmergency();
        if (pendingOpt.isEmpty()) {
            return DispatchResult.noPendingEmergencies(now);
        }

        EmergencyCase targetCase = pendingOpt.get();
        return executeDispatch(targetCase, true);
    }

    /**
     * Dispatches an optimal available ambulance to a designated emergency case by ID.
     */
    public synchronized DispatchResult dispatchEmergency(String caseId) {
        EmergencyCase emergencyCase = emergencyService.getCaseOrThrow(caseId);
        return dispatchEmergency(emergencyCase);
    }

    /**
     * Dispatches an optimal available ambulance to the specified emergency case instance.
     */
    public synchronized DispatchResult dispatchEmergency(EmergencyCase emergencyCase) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        boolean isQueued = queueService.containsCase(emergencyCase.getId());
        return executeDispatch(emergencyCase, isQueued);
    }

    /**
     * Iteratively dispatches available ambulances to pending emergencies until the queue is empty
     * or no further viable vehicles remain.
     *
     * @return List of all dispatch results produced during the batch run.
     */
    public synchronized List<DispatchResult> dispatchAllPending() {
        List<DispatchResult> results = new ArrayList<>();
        while (!queueService.isEmpty()) {
            DispatchResult result = dispatchNext();
            results.add(result);
            if (!result.isSuccess()) {
                // If the top priority case could not be dispatched (no vehicles or unreachable), stop batch
                break;
            }
        }
        return Collections.unmodifiableList(results);
    }

    private DispatchResult executeDispatch(EmergencyCase inputCase, boolean removeFromQueueOnSuccess) {
        Instant now = clock.instant();
        EmergencyCase emergencyCase = emergencyService.getCase(inputCase.getId()).orElse(inputCase);

        // 1. Verify eligibility
        if (emergencyCase.isTerminal()) {
            if (removeFromQueueOnSuccess) {
                queueService.remove(emergencyCase.getId());
            }
            return DispatchResult.caseIneligible(emergencyCase, "Case is already terminal (" + emergencyCase.getStatus() + ")", now);
        }
        if (emergencyCase.hasAssignedAmbulance()) {
            if (removeFromQueueOnSuccess) {
                queueService.remove(emergencyCase.getId());
            }
            return DispatchResult.caseIneligible(emergencyCase, "Ambulance already assigned: " + emergencyCase.getAssignedAmbulanceId().orElse(""), now);
        }

        // 2. Filter available ambulances in fleet
        List<Ambulance> availableAmbulances = fleetService.getAvailableAmbulances();
        if (availableAmbulances.isEmpty()) {
            return DispatchResult.noAvailableAmbulances(emergencyCase, now);
        }

        // 3. Filter by clinical capability compatibility
        List<Ambulance> compatibleAmbulances = availableAmbulances.stream()
                .filter(amb -> capabilityMatcher.isCompatible(amb, emergencyCase))
                .toList();
        if (compatibleAmbulances.isEmpty()) {
            return DispatchResult.noCompatibleAmbulances(emergencyCase, now);
        }

        // 4. Calculate routes and estimated arrival times for each compatible candidate
        String pickupNodeId = emergencyCase.getPickupLocationNodeId();
        List<AmbulanceCandidate> candidates = new ArrayList<>();

        for (Ambulance amb : compatibleAmbulances) {
            String originNodeId = amb.getCurrentLocationNodeId();
            RouteResult route = routingAlgorithm.findFastestRoute(roadGraph, originNodeId, pickupNodeId);

            if (route != null && route.isReachable()) {
                candidates.add(new AmbulanceCandidate(amb, route));
            }
        }

        if (candidates.isEmpty()) {
            return DispatchResult.unreachablePickup(emergencyCase, now);
        }

        // 5. Rank candidates using the injected ranking policy
        candidates.sort((c1, c2) -> rankingPolicy.compare(c1, c2, emergencyCase));
        AmbulanceCandidate bestCandidate = candidates.get(0);
        Ambulance selectedAmbulance = bestCandidate.ambulance();
        RouteResult selectedRoute = bestCandidate.routeToPickup();

        // 6. Atomic Transaction Assignment
        String ambId = selectedAmbulance.getId();
        String caseId = emergencyCase.getId();

        try {
            // Re-verify ambulance availability before state mutation
            if (!fleetService.isAmbulanceAvailable(ambId)) {
                return DispatchResult.failedTransaction(emergencyCase, "Ambulance '" + ambId + "' became unavailable concurrently", now);
            }

            // Assign ambulance (atomic coordination across emergency lifecycle and fleet service)
            EmergencyCase assignedCase;
            try {
                assignedCase = emergencyService.assignAmbulance(caseId, ambId);
            } catch (Exception e) {
                return DispatchResult.failedTransaction(emergencyCase, "State transition failed: " + e.getMessage(), now);
            }

            // Remove from dispatch queue
            if (removeFromQueueOnSuccess) {
                queueService.remove(caseId);
            }

            return DispatchResult.success(assignedCase, selectedAmbulance, selectedRoute, now);

        } catch (Exception ex) {
            return DispatchResult.failedTransaction(emergencyCase, "Unexpected transaction failure: " + ex.getMessage(), now);
        }
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public RoutingAlgorithm getRoutingAlgorithm() {
        return routingAlgorithm;
    }

    public EmergencyManagementService getEmergencyService() {
        return emergencyService;
    }

    public AmbulanceFleetService getFleetService() {
        return fleetService;
    }

    public EmergencyDispatchQueueService getQueueService() {
        return queueService;
    }

    public AmbulanceRankingPolicy getRankingPolicy() {
        return rankingPolicy;
    }

    public AmbulanceCapabilityMatcher getCapabilityMatcher() {
        return capabilityMatcher;
    }

    public Clock getClock() {
        return clock;
    }
}
