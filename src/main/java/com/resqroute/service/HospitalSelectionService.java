package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.RoutingAlgorithm;
import com.resqroute.exception.DuplicateReservationException;
import com.resqroute.exception.HospitalCapacityExceededException;
import com.resqroute.exception.HospitalClosedException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.hospital.*;
import com.resqroute.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Intelligent hospital selection and capacity reservation engine recommending optimal clinical
 * destinations based on patient medical requirements, live road traffic, route feasibility,
 * and hospital operational status.
 *
 * <h3>Selection Algorithm &amp; Complexity Analysis:</h3>
 * <ol>
 *   <li><b>Mandatory Constraint Filtering:</b> Isolates hospitals that are operationally {@link HospitalStatus#OPEN},
 *       possess {@code availableCapacity > 0}, and satisfy all clinical requirements via
 *       {@link HospitalSuitabilityMatcher} ($\mathcal{O}(H)$ where $H$ is the total hospital count).</li>
 *   <li><b>Traffic-Aware Route Evaluation:</b> Calculates shortest travel times from patient pickup
 *       (or ambulance origin) to each candidate hospital using {@link RoutingAlgorithm} ($\mathcal{O}(K \cdot (E + V \log V))$
 *       where $K \le H$ is the number of compatible open hospitals).</li>
 *   <li><b>Reachability Pruning:</b> Excludes hospitals severed by road closures or graph disconnections.</li>
 *   <li><b>Policy-Driven Ranking:</b> Orders candidates using {@link HospitalRankingPolicy} ($\mathcal{O}(K \log K)$).</li>
 *   <li><b>Atomic Fallback Reservation:</b> Attempts atomic bed reservation starting from the top-ranked hospital.
 *       If an atomic reservation fails due to a concurrent race (capacity exhausted between evaluation and booking),
 *       the engine automatically falls back to the next ranked candidate until capacity is secured.</li>
 * </ol>
 */
public class HospitalSelectionService {

    private final RoadGraph roadGraph;
    private final RoutingAlgorithm routingAlgorithm;
    private final HospitalManagementService hospitalService;
    private final EmergencyManagementService emergencyService;
    private final HospitalSuitabilityMatcher suitabilityMatcher;
    private final HospitalRankingPolicy rankingPolicy;
    private final Clock clock;

    public HospitalSelectionService(RoadGraph roadGraph,
                                  RoutingAlgorithm routingAlgorithm,
                                  HospitalManagementService hospitalService,
                                  EmergencyManagementService emergencyService,
                                  HospitalSuitabilityMatcher suitabilityMatcher,
                                  HospitalRankingPolicy rankingPolicy,
                                  Clock clock) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.routingAlgorithm = Objects.requireNonNull(routingAlgorithm, "RoutingAlgorithm must not be null");
        this.hospitalService = Objects.requireNonNull(hospitalService, "HospitalManagementService must not be null");
        this.emergencyService = emergencyService; // May be null if running purely destination evaluation
        this.suitabilityMatcher = Objects.requireNonNull(suitabilityMatcher, "HospitalSuitabilityMatcher must not be null");
        this.rankingPolicy = Objects.requireNonNull(rankingPolicy, "HospitalRankingPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public HospitalSelectionService(RoadGraph roadGraph,
                                  RoutingAlgorithm routingAlgorithm,
                                  HospitalManagementService hospitalService,
                                  EmergencyManagementService emergencyService) {
        this(roadGraph, routingAlgorithm, hospitalService, emergencyService,
                DefaultHospitalSuitabilityMatcher.INSTANCE,
                FastestHospitalRankingPolicy.INSTANCE,
                emergencyService != null ? emergencyService.getClock() : Clock.systemUTC());
    }

    public HospitalSelectionService(RoadGraph roadGraph,
                                  HospitalManagementService hospitalService,
                                  EmergencyManagementService emergencyService) {
        this(roadGraph, new DijkstraRoutingAlgorithm(), hospitalService, emergencyService);
    }

    /**
     * Evaluates all candidate hospitals for an emergency case, returning an ordered list
     * of ranked recommendations with estimated travel times and rationale.
     *
     * @param emergencyCase The emergency case.
     * @param originNodeId  The road network origin node (typically patient pickup location).
     * @return Ordered list of feasible recommendations (1st = best candidate).
     */
    public List<HospitalRecommendation> evaluateHospitals(EmergencyCase emergencyCase, String originNodeId) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        Objects.requireNonNull(originNodeId, "Origin node ID must not be null");

        List<Hospital> allHospitals = hospitalService.getAllHospitals();
        List<HospitalRecommendation> candidates = new ArrayList<>();

        for (Hospital hospital : allHospitals) {
            // 1. Mandatory admission availability: must be OPEN with available capacity
            if (!hospital.isAvailableForAdmission()) {
                continue;
            }

            // 2. Mandatory clinical compatibility check
            if (!suitabilityMatcher.isMedicallyCompatible(hospital, emergencyCase)) {
                continue;
            }

            // 3. Routing calculation with dynamic traffic costs and road closures
            String destinationNodeId = hospital.getLocationNodeId();
            RouteResult route = routingAlgorithm.findFastestRoute(roadGraph, originNodeId, destinationNodeId);

            if (route == null || !route.isReachable()) {
                continue;
            }

            String rationale = buildRationale(hospital, emergencyCase, route);
            candidates.add(new HospitalRecommendation(
                    hospital,
                    route,
                    route.getTotalTravelTimeMinutes(),
                    route.getTotalDistanceKm(),
                    true,
                    0, // Assigned after sorting
                    rationale
            ));
        }

        // Rank candidates using injected policy
        candidates.sort((c1, c2) -> rankingPolicy.compare(c1, c2, emergencyCase));

        // Assign 1-based ranks
        List<HospitalRecommendation> ranked = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ranked.add(candidates.get(i).withRank(i + 1));
        }

        return Collections.unmodifiableList(ranked);
    }

    /**
     * Evaluates candidate hospitals using the emergency case's pickup location as origin.
     */
    public List<HospitalRecommendation> evaluateHospitals(EmergencyCase emergencyCase) {
        return evaluateHospitals(emergencyCase, emergencyCase.getPickupLocationNodeId());
    }

    /**
     * Selects the most suitable hospital and atomically reserves an admission unit.
     *
     * <p>Safe against concurrent capacity exhaustion: if a candidate's available capacity is
     * taken by another request between evaluation and booking, the algorithm automatically
     * falls back to the next ranked eligible hospital.</p>
     *
     * @param emergencyCase Emergency case instance.
     * @param originNodeId  Origin road node.
     * @return HospitalSelectionResult detailing the outcome and reserved hospital.
     */
    public synchronized HospitalSelectionResult selectAndReserveHospital(EmergencyCase emergencyCase, String originNodeId) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        Objects.requireNonNull(originNodeId, "Origin node ID must not be null");
        Instant now = clock.instant();

        // Refresh with latest state if managed
        EmergencyCase latestCase = (emergencyService != null)
                ? emergencyService.getCase(emergencyCase.getId()).orElse(emergencyCase)
                : emergencyCase;

        if (latestCase.isTerminal()) {
            return HospitalSelectionResult.failure(
                    HospitalSelectionStatus.CASE_INELIGIBLE,
                    latestCase,
                    "Emergency case is already terminal (" + latestCase.getStatus() + ")",
                    now
            );
        }

        // Prevent duplicate reservations: check if case already holds an active reservation at any hospital
        for (Hospital h : hospitalService.getAllHospitals()) {
            if (hospitalService.hasActiveReservation(h.getId(), latestCase.getId())) {
                return HospitalSelectionResult.failure(
                        HospitalSelectionStatus.RESERVATION_FAILED,
                        latestCase,
                        "Case '" + latestCase.getId() + "' already holds an active reservation at hospital '" + h.getId() + "'",
                        now
                );
            }
        }

        // Evaluate candidate hospitals
        List<HospitalRecommendation> rankedCandidates = evaluateHospitals(latestCase, originNodeId);

        if (rankedCandidates.isEmpty()) {
            HospitalSelectionStatus diagnosis = diagnoseFailureCause(latestCase, originNodeId);
            return HospitalSelectionResult.failure(diagnosis, latestCase, diagnosis.name(), now);
        }

        // Fallback Reservation Loop
        for (int i = 0; i < rankedCandidates.size(); i++) {
            HospitalRecommendation candidate = rankedCandidates.get(i);
            String hospitalId = candidate.getHospitalId();

            try {
                // Attempt atomic reservation in HospitalManagementService
                hospitalService.reserveCapacity(hospitalId, latestCase.getId());

                // Successfully reserved: designate destination in emergency service if present
                if (emergencyService != null) {
                    try {
                        emergencyService.setDestinationHospital(latestCase.getId(), candidate.getLocationNodeId());
                    } catch (Exception ignored) {
                        // Keep hospital reservation even if case destination update is decoupled
                    }
                }

                // Prepare alternative list (excluding the selected candidate)
                List<HospitalRecommendation> alternatives = new ArrayList<>(rankedCandidates);
                alternatives.remove(i);

                return HospitalSelectionResult.success(latestCase, candidate, alternatives, now);

            } catch (HospitalCapacityExceededException | HospitalClosedException ex) {
                // Capacity changed concurrently or hospital closed between evaluation and booking;
                // Fall back to next candidate in ranked order
                continue;
            } catch (DuplicateReservationException ex) {
                return HospitalSelectionResult.failure(
                        HospitalSelectionStatus.RESERVATION_FAILED,
                        latestCase,
                        "Duplicate reservation attempt: " + ex.getMessage(),
                        now
                );
            } catch (Exception ex) {
                return HospitalSelectionResult.failure(
                        HospitalSelectionStatus.RESERVATION_FAILED,
                        latestCase,
                        "Unexpected reservation failure: " + ex.getMessage(),
                        now
                );
            }
        }

        // All candidates failed reservation due to concurrent capacity changes
        return HospitalSelectionResult.failure(
                HospitalSelectionStatus.ALL_HOSPITALS_FULL_OR_BUSY,
                latestCase,
                rankedCandidates,
                "All candidate hospitals became saturated concurrently",
                now
        );
    }

    /**
     * Selects and reserves a hospital for a registered emergency case by ID.
     */
    public synchronized HospitalSelectionResult selectAndReserveHospital(String caseId) {
        if (emergencyService == null) {
            throw new IllegalStateException("EmergencyManagementService must be provided to lookup cases by ID");
        }
        EmergencyCase emergencyCase = emergencyService.getCaseOrThrow(caseId);
        return selectAndReserveHospital(emergencyCase, emergencyCase.getPickupLocationNodeId());
    }

    /**
     * Releases any active hospital reservation associated with an emergency case ID.
     *
     * @param caseId Unique emergency case identifier.
     * @return {@code true} if a reservation was found and released, {@code false} otherwise.
     */
    public synchronized boolean releaseHospitalReservation(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return false;
        }
        String cleanCaseId = caseId.trim();
        for (Hospital hospital : hospitalService.getAllHospitals()) {
            if (hospitalService.hasActiveReservation(hospital.getId(), cleanCaseId)) {
                hospitalService.releaseCapacity(hospital.getId(), cleanCaseId);
                return true;
            }
        }
        return false;
    }

    /**
     * Cancels an emergency case and safely releases any active hospital reservation and allocated ambulance.
     */
    public synchronized EmergencyCase cancelHospitalAssignment(String caseId, String reason) {
        releaseHospitalReservation(caseId);
        if (emergencyService != null) {
            return emergencyService.cancelCase(caseId, reason);
        }
        throw new IllegalStateException("EmergencyManagementService is required to cancel emergency case");
    }

    private HospitalSelectionStatus diagnoseFailureCause(EmergencyCase emergencyCase, String originNodeId) {
        List<Hospital> all = hospitalService.getAllHospitals();
        if (all.isEmpty()) {
            return HospitalSelectionStatus.NO_ELIGIBLE_HOSPITALS;
        }

        boolean anyCompatible = all.stream()
                .anyMatch(h -> suitabilityMatcher.isMedicallyCompatible(h, emergencyCase));
        if (!anyCompatible) {
            return HospitalSelectionStatus.NO_COMPATIBLE_HOSPITALS;
        }

        boolean anyOpenAndAvailable = all.stream()
                .filter(h -> suitabilityMatcher.isMedicallyCompatible(h, emergencyCase))
                .anyMatch(Hospital::isAvailableForAdmission);
        if (!anyOpenAndAvailable) {
            return HospitalSelectionStatus.ALL_HOSPITALS_FULL_OR_BUSY;
        }

        // Compatible and open hospitals exist, but are unreachable
        return HospitalSelectionStatus.ALL_HOSPITALS_UNREACHABLE;
    }

    private String buildRationale(Hospital hospital, EmergencyCase emergencyCase, RouteResult route) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Fastest feasible arrival: %.1f min (%.1f km) with %d bed(s) available. ",
                route.getTotalTravelTimeMinutes(), route.getTotalDistanceKm(), hospital.getAvailableCapacity()));

        if (emergencyCase.getType() == EmergencyType.TRAUMA) {
            sb.append("Provides certified Trauma Center care. ");
        } else if (emergencyCase.getType() == EmergencyType.CARDIAC) {
            sb.append("Equipped with acute Cardiac Catheterization Lab. ");
        }

        if (emergencyCase.getSeverity() == EmergencySeverity.CRITICAL) {
            sb.append("Operational ICU capability verified.");
        }

        return sb.toString().trim();
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public RoutingAlgorithm getRoutingAlgorithm() {
        return routingAlgorithm;
    }

    public HospitalManagementService getHospitalService() {
        return hospitalService;
    }

    public EmergencyManagementService getEmergencyService() {
        return emergencyService;
    }

    public HospitalSuitabilityMatcher getSuitabilityMatcher() {
        return suitabilityMatcher;
    }

    public HospitalRankingPolicy getRankingPolicy() {
        return rankingPolicy;
    }

    public Clock getClock() {
        return clock;
    }
}
