package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.optimization.*;
import com.resqroute.scheduling.AgingDispatchPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 14: Multi-Ambulance Assignment Optimization Tests")
class FleetAssignmentOptimizerTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;
    private EmergencyManagementService emergencyService;
    private EmergencyDispatchQueueService queueService;
    private FleetAssignmentOptimizer optimizer;
    private PriorityAwareCostModel costModel;
    private MutableTestClock testClock;

    private static final Instant T0 = Instant.parse("2026-10-18T16:00:00Z");

    static class MutableTestClock extends Clock {
        private Instant instant;

        MutableTestClock(Instant initial) {
            this.instant = initial;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        public void advanceMinutes(long mins) {
            this.instant = this.instant.plusSeconds(mins * 60);
        }
    }

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();

        // Nodes
        Node stNorth = new Node("ST-NORTH", 12.98, 77.59, "North Station");
        Node stSouth = new Node("ST-SOUTH", 12.94, 77.59, "South Station");
        Node stEast  = new Node("ST-EAST", 12.96, 77.63, "East Station");
        Node scA = new Node("SCENE-A", 12.97, 77.59, "Scene A");
        Node scB = new Node("SCENE-B", 12.95, 77.60, "Scene B");
        Node scC = new Node("SCENE-C", 12.96, 77.62, "Scene C");
        Node isolated = new Node("ISOLATED", 13.10, 77.70, "Isolated Scene");

        graph.addNode(stNorth);
        graph.addNode(stSouth);
        graph.addNode(stEast);
        graph.addNode(scA);
        graph.addNode(scB);
        graph.addNode(scC);
        graph.addNode(isolated);

        // Edges:
        // ST-NORTH -> SCENE-A: 2.0 km, 3.0 min
        graph.addEdge("ST-NORTH", "SCENE-A", 2.0, 3.0, "N-A");
        // ST-NORTH -> SCENE-B: 5.0 km, 8.0 min
        graph.addEdge("ST-NORTH", "SCENE-B", 5.0, 8.0, "N-B");
        // ST-NORTH -> SCENE-C: 6.0 km, 10.0 min
        graph.addEdge("ST-NORTH", "SCENE-C", 6.0, 10.0, "N-C");

        // ST-SOUTH -> SCENE-A: 6.0 km, 9.0 min
        graph.addEdge("ST-SOUTH", "SCENE-A", 6.0, 9.0, "S-A");
        // ST-SOUTH -> SCENE-B: 2.0 km, 3.0 min
        graph.addEdge("ST-SOUTH", "SCENE-B", 2.0, 3.0, "S-B");
        // ST-SOUTH -> SCENE-C: 5.0 km, 7.0 min
        graph.addEdge("ST-SOUTH", "SCENE-C", 5.0, 7.0, "S-C");

        // ST-EAST -> SCENE-A: 6.0 km, 8.0 min
        graph.addEdge("ST-EAST", "SCENE-A", 6.0, 8.0, "E-A");
        // ST-EAST -> SCENE-B: 5.0 km, 7.0 min
        graph.addEdge("ST-EAST", "SCENE-B", 5.0, 7.0, "E-B");
        // ST-EAST -> SCENE-C: 2.0 km, 2.5 min
        graph.addEdge("ST-EAST", "SCENE-C", 2.0, 2.5, "E-C");

        testClock = new MutableTestClock(T0);
        fleetService = new AmbulanceFleetService(graph);
        emergencyService = new EmergencyManagementService(graph, fleetService, testClock);
        queueService = new EmergencyDispatchQueueService(emergencyService, new AgingDispatchPolicy(10.0), testClock);
        costModel = new PriorityAwareCostModel();

        optimizer = new FleetAssignmentOptimizer(
                graph,
                new DijkstraRoutingAlgorithm(),
                fleetService,
                emergencyService,
                queueService,
                com.resqroute.dispatch.DefaultCapabilityMatcher.INSTANCE,
                costModel,
                testClock
        );
    }

    @Test
    @DisplayName("Multiple emergencies and ambulances: Global optimization minimizes total travel time and cost")
    void testMultipleEmergenciesAndAmbulancesGlobalMinimization() {
        // Ambulances:
        // AMB-N at ST-NORTH (closest to SCENE-A)
        // AMB-S at ST-SOUTH (closest to SCENE-B)
        // AMB-E at ST-EAST (closest to SCENE-C)
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");
        fleetService.registerAmbulance("AMB-E", "ST-EAST");

        // Emergencies:
        EmergencyCase caseA = emergencyService.createCase("CASE-A", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "Case A");
        EmergencyCase caseB = emergencyService.createCase("CASE-B", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-B", "Case B");
        EmergencyCase caseC = emergencyService.createCase("CASE-C", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-C", "Case C");

        FleetAssignmentPlan plan = optimizer.computeOptimalPlan(
                List.of(caseA, caseB, caseC),
                fleetService.getAllAmbulances()
        );

        assertEquals(3, plan.getAssignedCount());
        assertEquals(0, plan.getUnassignedCount());

        // Perfectly pairs each ambulance to its nearest scene:
        // AMB-N -> CASE-A (3.0 min)
        // AMB-S -> CASE-B (3.0 min)
        // AMB-E -> CASE-C (2.5 min)
        assertEquals("AMB-N", plan.getAssignmentForCase("CASE-A").orElseThrow().getAmbulanceId());
        assertEquals("AMB-S", plan.getAssignmentForCase("CASE-B").orElseThrow().getAmbulanceId());
        assertEquals("AMB-E", plan.getAssignmentForCase("CASE-C").orElseThrow().getAmbulanceId());

        assertEquals(3.0 + 3.0 + 2.5, plan.getTotalEstimatedTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Fewer ambulances than emergencies: Maximizes matching and prioritizes CRITICAL emergencies")
    void testFewerAmbulancesPrioritizesCriticalCases() {
        // Only 1 ambulance available at ST-NORTH
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");

        // Two emergencies at SCENE-A (same location, same distance to AMB-1):
        // CASE-CRIT is CRITICAL
        // CASE-LOW is LOW
        EmergencyCase caseLow = emergencyService.createCase("CASE-LOW", EmergencyType.MEDICAL, EmergencySeverity.LOW, "SCENE-A", "Minor");
        EmergencyCase caseCrit = emergencyService.createCase("CASE-CRIT", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE-A", "Arrest");

        FleetAssignmentPlan plan = optimizer.computeOptimalPlan(
                List.of(caseLow, caseCrit),
                fleetService.getAllAmbulances()
        );

        assertEquals(1, plan.getAssignedCount());
        assertEquals(1, plan.getUnassignedCount());

        // Must assign AMB-1 to CASE-CRIT because of high severity bonus
        assertEquals("CASE-CRIT", plan.getAssignments().get(0).getCaseId());
        assertEquals("AMB-1", plan.getAssignments().get(0).getAmbulanceId());

        // CASE-LOW remains unassigned with clear diagnostic reason
        UnassignedEmergency unassigned = plan.getUnassignedEmergencies().get(0);
        assertEquals("CASE-LOW", unassigned.getCaseId());
        assertTrue(unassigned.getReason().contains("Fleet capacity exhausted"));
    }

    @Test
    @DisplayName("More ambulances than emergencies: Assigns closest ambulances and leaves excess available")
    void testMoreAmbulancesThanEmergencies() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");
        fleetService.registerAmbulance("AMB-E", "ST-EAST");

        // Only 1 emergency at SCENE-A
        EmergencyCase caseA = emergencyService.createCase("CASE-A", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-A", "Case A");

        FleetAssignmentPlan plan = optimizer.computeOptimalPlan(
                List.of(caseA),
                fleetService.getAllAmbulances()
        );

        assertEquals(1, plan.getAssignedCount());
        assertEquals(0, plan.getUnassignedCount());

        // AMB-N at 3.0 min is chosen over South (9.0 min) and East (8.0 min)
        assertEquals("AMB-N", plan.getAssignments().get(0).getAmbulanceId());
        assertEquals(3.0, plan.getAssignments().get(0).getTravelTimeMinutes(), 1e-4);

        // AMB-S and AMB-E were unassigned in the plan
        assertFalse(plan.isAmbulanceAssigned("AMB-S"));
        assertFalse(plan.isAmbulanceAssigned("AMB-E"));
    }

    @Test
    @DisplayName("Incompatible and unreachable ambulance candidates are safely excluded from matching")
    void testIncompatibleAndUnreachableCandidatesExcluded() {
        // AMB-BLS is close at ST-NORTH (3.0 min), but only has BASIC_LIFE_SUPPORT
        Ambulance bls = new Ambulance("AMB-BLS", "ST-NORTH", Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT));
        fleetService.registerAmbulance(bls);

        // AMB-RESCUE is at ST-SOUTH (7.0 min to SCENE-C), has HEAVY_RESCUE
        Ambulance rescue = new Ambulance("AMB-RESCUE", "ST-SOUTH", Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT, AmbulanceCapability.HEAVY_RESCUE));
        fleetService.registerAmbulance(rescue);

        // Case 1: Trapped victim requiring HEAVY_RESCUE at SCENE-C
        EmergencyCase rescueCase = emergencyService.createCase("CASE-RESCUE", EmergencyType.FIRE_RESCUE, EmergencySeverity.HIGH, "SCENE-C", "Extrication");

        // Case 2: At ISOLATED node (unreachable by any road)
        EmergencyCase isolatedCase = emergencyService.createCase("CASE-ISOLATED", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "ISOLATED", "No road");

        FleetAssignmentPlan plan = optimizer.computeOptimalPlan(
                List.of(rescueCase, isolatedCase),
                fleetService.getAllAmbulances()
        );

        // Only CASE-RESCUE can be assigned (to AMB-RESCUE)
        assertEquals(1, plan.getAssignedCount());
        assertEquals("CASE-RESCUE", plan.getAssignments().get(0).getCaseId());
        assertEquals("AMB-RESCUE", plan.getAssignments().get(0).getAmbulanceId());

        // CASE-ISOLATED is unassigned due to reachability
        assertEquals(1, plan.getUnassignedCount());
        UnassignedEmergency unassignedIso = plan.getUnassignedEmergencies().stream()
                .filter(u -> u.getCaseId().equals("CASE-ISOLATED"))
                .findFirst().orElseThrow();
        assertTrue(unassignedIso.getReason().contains("unreachable"));
    }

    @Test
    @DisplayName("Empty candidate sets return clean plan with zero assignments")
    void testEmptyCandidateSets() {
        // No ambulances, no emergencies
        FleetAssignmentPlan p1 = optimizer.computeOptimalPlan(List.of(), List.of());
        assertEquals(0, p1.getAssignedCount());
        assertEquals(0, p1.getUnassignedCount());

        // Emergencies but 0 ambulances
        EmergencyCase c = emergencyService.createCase("C1", EmergencyType.MEDICAL, EmergencySeverity.LOW, "SCENE-A", "Fever");
        FleetAssignmentPlan p2 = optimizer.computeOptimalPlan(List.of(c), List.of());
        assertEquals(0, p2.getAssignedCount());
        assertEquals(1, p2.getUnassignedCount());
        assertTrue(p2.getUnassignedEmergencies().get(0).getReason().contains("No available ambulances"));
    }

    @Test
    @DisplayName("Equal-cost assignments produce deterministic results")
    void testEqualCostAssignmentsDeterministic() {
        // Two identical ambulances at ST-NORTH
        fleetService.registerAmbulance("AMB-A", "ST-NORTH");
        fleetService.registerAmbulance("AMB-B", "ST-NORTH");

        EmergencyCase c = emergencyService.createCase("CASE-1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-A", "Fever");

        FleetAssignmentPlan plan1 = optimizer.computeOptimalPlan(List.of(c), fleetService.getAllAmbulances());
        FleetAssignmentPlan plan2 = optimizer.computeOptimalPlan(List.of(c), fleetService.getAllAmbulances());

        assertEquals(plan1.getAssignments().get(0).getAmbulanceId(), plan2.getAssignments().get(0).getAmbulanceId());
    }

    @Test
    @DisplayName("Commit validation and atomic execution updates emergency and ambulance states")
    void testCommitPlanSuccessfulStateTransitions() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        emergencyService.createCase("CASE-1", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "C1");
        emergencyService.createCase("CASE-2", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-B", "C2");

        queueService.enqueue("CASE-1");
        queueService.enqueue("CASE-2");
        assertEquals(2, queueService.size());

        // Plan and dispatch via integrated optimizeAndDispatch
        FleetAssignmentResult result = optimizer.optimizeAndDispatch();

        assertTrue(result.isCommitted());
        assertEquals(2, result.getAssignedCount());

        // Verify state updates in live services
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-N").getStatus());
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-S").getStatus());

        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, emergencyService.getCaseOrThrow("CASE-1").getStatus());
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, emergencyService.getCaseOrThrow("CASE-2").getStatus());

        // Queue is drained
        assertTrue(queueService.isEmpty());
    }

    @Test
    @DisplayName("Failed commit: Stale availability or cancelled emergency aborts commit with zero partial state")
    void testFailedCommitZeroPartialState() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        EmergencyCase c1 = emergencyService.createCase("CASE-1", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "C1");
        EmergencyCase c2 = emergencyService.createCase("CASE-2", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-B", "C2");

        // Plan is computed when both are available
        FleetAssignmentPlan plan = optimizer.computeOptimalPlan(List.of(c1, c2), fleetService.getAllAmbulances());
        assertEquals(2, plan.getAssignedCount());

        // Before committing, CASE-2 is cancelled externally
        emergencyService.cancelCase("CASE-2", "Cancelled externally");

        // Attempting to commit the stale plan detects the conflict
        FleetAssignmentResult commitResult = optimizer.commitPlan(plan);

        assertFalse(commitResult.isCommitted());
        assertTrue(commitResult.getMessage().contains("Commit aborted"));

        // Zero partial state guarantee: AMB-N and AMB-S are NOT assigned; CASE-1 remains unassigned
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-N").getStatus());
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-S").getStatus());
        assertEquals(EmergencyStatus.REPORTED, emergencyService.getCaseOrThrow("CASE-1").getStatus());
        assertFalse(emergencyService.getCaseOrThrow("CASE-1").hasAssignedAmbulance());
    }

    @Test
    @DisplayName("Optimality validation: Compares MCMF against brute-force permutation search on candidate graph")
    void testCompareAgainstBruteForceEnumeration() {
        // Construct a non-trivial 3x3 assignment scenario
        fleetService.registerAmbulance("A1", "ST-NORTH"); // to SCENE-A (3.0), SCENE-B (8.0), SCENE-C (10.0)
        fleetService.registerAmbulance("A2", "ST-SOUTH"); // to SCENE-A (9.0), SCENE-B (3.0), SCENE-C (7.0)
        fleetService.registerAmbulance("A3", "ST-EAST");  // to SCENE-A (8.0), SCENE-B (7.0), SCENE-C (2.5)

        EmergencyCase c1 = emergencyService.createCase("E1", EmergencyType.TRAUMA, EmergencySeverity.CRITICAL, "SCENE-A", "E1");
        EmergencyCase c2 = emergencyService.createCase("E2", EmergencyType.CARDIAC, EmergencySeverity.HIGH, "SCENE-B", "E2");
        EmergencyCase c3 = emergencyService.createCase("E3", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-C", "E3");

        List<EmergencyCase> emergencies = List.of(c1, c2, c3);
        List<Ambulance> ambulances = fleetService.getAllAmbulances();

        // MCMF solution
        FleetAssignmentPlan mcmfPlan = optimizer.computeOptimalPlan(emergencies, ambulances);
        assertEquals(3, mcmfPlan.getAssignedCount());
        double mcmfCost = mcmfPlan.getTotalEstimatedCost();

        // Brute-force enumeration of all 3! = 6 bijective matchings
        double bruteForceMinCost = Double.POSITIVE_INFINITY;
        int[][] permutations = {
                {0, 1, 2},
                {0, 2, 1},
                {1, 0, 2},
                {1, 2, 0},
                {2, 0, 1},
                {2, 1, 0}
        };

        Instant now = testClock.instant();
        for (int[] p : permutations) {
            double cost = 0.0;
            // c1 assigned to ambulances[p[0]], c2 to ambulances[p[1]], c3 to ambulances[p[2]]
            for (int caseIdx = 0; caseIdx < 3; caseIdx++) {
                int ambIdx = p[caseIdx];
                Ambulance amb = ambulances.get(ambIdx);
                EmergencyCase em = emergencies.get(caseIdx);
                RouteResult r = optimizer.getRoutingAlgorithm().findFastestRoute(graph, amb.getCurrentLocationNodeId(), em.getPickupLocationNodeId());
                cost += costModel.calculateCost(amb, em, r.getTotalTravelTimeMinutes(), now);
            }
            if (cost < bruteForceMinCost) {
                bruteForceMinCost = cost;
            }
        }

        // MCMF MUST achieve the exact same minimum cost found by exhaustive brute force
        assertEquals(bruteForceMinCost, mcmfCost, 1e-4,
                "MCMF must find the global minimum cost matching verified by brute force enumeration");
    }
}
