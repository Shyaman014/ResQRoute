package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.dispatch.DispatchResult;
import com.resqroute.dispatch.DispatchStatus;
import com.resqroute.dispatch.FastestResponseRankingPolicy;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.scheduling.AgingDispatchPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 11: Intelligent Ambulance Dispatch Algorithm Tests")
class AmbulanceDispatchServiceTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;
    private MutableTestClock testClock;
    private EmergencyManagementService emergencyService;
    private EmergencyDispatchQueueService queueService;
    private TrafficSimulationEngine trafficEngine;
    private RoadIncidentService incidentService;
    private AmbulanceDispatchService dispatchService;

    private static final Instant T0 = Instant.parse("2026-10-18T10:00:00Z");

    static class MutableTestClock extends Clock {
        private Instant currentInstant;

        MutableTestClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        public void advanceSeconds(long seconds) {
            this.currentInstant = this.currentInstant.plusSeconds(seconds);
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
            return currentInstant;
        }
    }

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();

        // Construct realistic metropolitan emergency network
        Node stationNorth = new Node("STATION-NORTH", 12.9800, 77.5900, "North Ambulance Station");
        Node stationSouth = new Node("STATION-SOUTH", 12.9600, 77.5900, "South Ambulance Station");
        Node sceneA = new Node("SCENE-A", 12.9750, 77.6000, "Commercial Intersection A");
        Node sceneB = new Node("SCENE-B", 12.9650, 77.6050, "Expressway Junction B");
        Node hospital = new Node("HOSP-01", 12.9700, 77.6200, "Apex Trauma Hospital");
        Node isolated = new Node("ISOLATED-ISLAND", 12.9990, 77.6500, "Disconnected Post");

        graph.addNode(stationNorth);
        graph.addNode(stationSouth);
        graph.addNode(sceneA);
        graph.addNode(sceneB);
        graph.addNode(hospital);
        graph.addNode(isolated);

        // Network roadways:
        // STATION-NORTH to SCENE-A: 2.0 km, 3.0 min
        graph.addEdge("STATION-NORTH", "SCENE-A", 2.0, 3.0, "North Boulevard");

        // STATION-SOUTH to SCENE-A: 5.0 km, 8.0 min
        graph.addEdge("STATION-SOUTH", "SCENE-A", 5.0, 8.0, "Southern Ring Road");

        // STATION-SOUTH to SCENE-B: 2.0 km, 2.5 min
        graph.addEdge("STATION-SOUTH", "SCENE-B", 2.0, 2.5, "South Connector");

        // STATION-NORTH to SCENE-B: 6.0 km, 10.0 min
        graph.addEdge("STATION-NORTH", "SCENE-B", 6.0, 10.0, "Central Expressway");

        // SCENE-A to SCENE-B: 3.0 km, 4.0 min (bidirectional)
        graph.addEdge("SCENE-A", "SCENE-B", 3.0, 4.0, "Cross Avenue A->B");
        graph.addEdge("SCENE-B", "SCENE-A", 3.0, 4.0, "Cross Avenue B->A");

        testClock = new MutableTestClock(T0);
        fleetService = new AmbulanceFleetService(graph);
        emergencyService = new EmergencyManagementService(graph, fleetService, testClock);
        queueService = new EmergencyDispatchQueueService(emergencyService, new AgingDispatchPolicy(10.0), testClock);
        trafficEngine = new TrafficSimulationEngine(graph, 42L);
        incidentService = new RoadIncidentService(graph);

        dispatchService = new AmbulanceDispatchService(
                graph,
                new DijkstraRoutingAlgorithm(),
                emergencyService,
                fleetService,
                queueService
        );
    }

    @Test
    @DisplayName("Selects the fastest eligible ambulance to the emergency pickup location")
    void testSelectFastestAmbulance() {
        // Station North has AMB-NORTH (3.0 min to SCENE-A)
        fleetService.registerAmbulance("AMB-NORTH", "STATION-NORTH");
        // Station South has AMB-SOUTH (8.0 min to SCENE-A)
        fleetService.registerAmbulance("AMB-SOUTH", "STATION-SOUTH");

        EmergencyCase emergency = emergencyService.createCase(
                "CASE-1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Severe injury");
        queueService.enqueue(emergency);

        DispatchResult result = dispatchService.dispatchNext();

        assertTrue(result.isSuccess());
        assertEquals(DispatchStatus.SUCCESS, result.getStatus());
        assertEquals("AMB-NORTH", result.getAssignedAmbulance().orElseThrow().getId());
        assertEquals("CASE-1", result.getEmergencyCase().orElseThrow().getId());
        assertEquals(3.0, result.getEstimatedTravelTimeMinutes(), 1e-4);
        assertEquals(2.0, result.getEstimatedDistanceKm(), 1e-4);

        // Verification of state updates
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-NORTH").getStatus());
        assertEquals(Optional.of("CASE-1"), fleetService.getAmbulanceOrThrow("AMB-NORTH").getAssignedCaseId());
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, emergencyService.getCaseOrThrow("CASE-1").getStatus());
        assertEquals(Optional.of("AMB-NORTH"), emergencyService.getCaseOrThrow("CASE-1").getAssignedAmbulanceId());

        // Queue is now empty
        assertTrue(queueService.isEmpty());
    }

    @Test
    @DisplayName("Prioritizes emergencies in priority queue order (CRITICAL ahead of MEDIUM)")
    void testPrioritizeEmergenciesCorrectly() {
        fleetService.registerAmbulance("AMB-1", "STATION-NORTH");

        // Two emergencies: Medium created first, then Critical
        EmergencyCase medCase = emergencyService.createCase(
                "CASE-MED", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-A", "Abdominal pain");
        EmergencyCase critCase = emergencyService.createCase(
                "CASE-CRIT", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE-A", "Cardiac arrest");

        queueService.enqueue(medCase);
        queueService.enqueue(critCase);

        // Dispatch should assign the only ambulance to CASE-CRIT first
        DispatchResult result1 = dispatchService.dispatchNext();
        assertTrue(result1.isSuccess());
        assertEquals("CASE-CRIT", result1.getEmergencyCase().orElseThrow().getId());
        assertEquals("AMB-1", result1.getAssignedAmbulance().orElseThrow().getId());

        // Second dispatch call has no available ambulances left -> CASE-MED remains queued
        DispatchResult result2 = dispatchService.dispatchNext();
        assertFalse(result2.isSuccess());
        assertEquals(DispatchStatus.NO_AVAILABLE_AMBULANCES, result2.getStatus());
        assertEquals("CASE-MED", result2.getEmergencyCase().orElseThrow().getId());
        assertEquals(1, queueService.size());
    }

    @Test
    @DisplayName("Capability-based filtering: Dispatches compatible ALS ambulance for Cardiac case even if further")
    void testCapabilityBasedFiltering() {
        // AMB-BLS is close at STATION-NORTH (3.0 min), but ONLY has BASIC_LIFE_SUPPORT
        Ambulance bls = new Ambulance("AMB-BLS", "STATION-NORTH", Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT));
        fleetService.registerAmbulance(bls);

        // AMB-ALS is further at STATION-SOUTH (8.0 min), but has CARDIAC_CARE and ADVANCED_LIFE_SUPPORT
        Ambulance als = new Ambulance("AMB-ALS", "STATION-SOUTH", Set.of(
                AmbulanceCapability.BASIC_LIFE_SUPPORT,
                AmbulanceCapability.ADVANCED_LIFE_SUPPORT,
                AmbulanceCapability.CARDIAC_CARE));
        fleetService.registerAmbulance(als);

        // Cardiac emergency at SCENE-A requires CARDIAC_CARE
        EmergencyCase cardiacCase = emergencyService.createCase(
                "CASE-CARDIAC", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE-A", "Heart attack");
        queueService.enqueue(cardiacCase);

        DispatchResult result = dispatchService.dispatchNext();

        assertTrue(result.isSuccess());
        // Must select AMB-ALS despite AMB-BLS being closer
        assertEquals("AMB-ALS", result.getAssignedAmbulance().orElseThrow().getId());
        assertEquals(6.5, result.getEstimatedTravelTimeMinutes(), 1e-4);

        // AMB-BLS remains AVAILABLE
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-BLS").getStatus());
    }

    @Test
    @DisplayName("Failure handling: Returns NO_COMPATIBLE_AMBULANCES when no available ambulance has required capabilities")
    void testNoCompatibleAmbulanceFailure() {
        // Only BLS ambulance available
        Ambulance bls = new Ambulance("AMB-BLS", "STATION-NORTH", Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT));
        fleetService.registerAmbulance(bls);

        // Heavy rescue case requiring HEAVY_RESCUE
        EmergencyCase rescueCase = emergencyService.createCase(
                "CASE-RESCUE", EmergencyType.FIRE_RESCUE, EmergencySeverity.HIGH, "SCENE-A", "Trapped in vehicle");
        queueService.enqueue(rescueCase);

        DispatchResult result = dispatchService.dispatchNext();

        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.NO_COMPATIBLE_AMBULANCES, result.getStatus());
        assertEquals("CASE-RESCUE", result.getEmergencyCase().orElseThrow().getId());

        // Emergency remains safely in queue
        assertEquals(1, queueService.size());
        assertEquals(AmbulanceStatus.AVAILABLE, bls.getStatus());
    }

    @Test
    @DisplayName("Failure handling: Returns UNREACHABLE_PICKUP when pickup location is disconnected or blocked")
    void testUnreachablePickupLocation() {
        fleetService.registerAmbulance("AMB-1", "STATION-NORTH");

        // Emergency on ISOLATED-ISLAND with no connecting roads
        EmergencyCase isolatedCase = emergencyService.createCase(
                "CASE-ISOLATED", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "ISOLATED-ISLAND", "Isolated patient");
        queueService.enqueue(isolatedCase);

        DispatchResult result = dispatchService.dispatchNext();

        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.UNREACHABLE_PICKUP, result.getStatus());
        assertEquals(1, queueService.size());

        // Ambulance remains available
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-1").getStatus());
    }

    @Test
    @DisplayName("Failure handling: Returns NO_AVAILABLE_AMBULANCES when fleet is empty or all vehicles busy")
    void testNoAvailableAmbulances() {
        // Fleet is empty
        EmergencyCase c = emergencyService.createCase(
                "C1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-A", "Fever");
        queueService.enqueue(c);

        DispatchResult result = dispatchService.dispatchNext();
        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.NO_AVAILABLE_AMBULANCES, result.getStatus());
        assertEquals(1, queueService.size());
    }

    @Test
    @DisplayName("Prevents duplicate assignments: Busy ambulance is never re-assigned")
    void testDuplicateAssignmentPrevention() {
        fleetService.registerAmbulance("AMB-SINGLE", "STATION-NORTH");

        emergencyService.createCase("CASE-A", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Case A");
        emergencyService.createCase("CASE-B", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-A", "Case B");

        queueService.enqueue("CASE-A");
        queueService.enqueue("CASE-B");

        // First dispatch assigns AMB-SINGLE to CASE-A
        DispatchResult r1 = dispatchService.dispatchNext();
        assertTrue(r1.isSuccess());
        assertEquals("CASE-A", r1.getEmergencyCase().orElseThrow().getId());

        // Second dispatch cannot assign AMB-SINGLE to CASE-B
        DispatchResult r2 = dispatchService.dispatchNext();
        assertFalse(r2.isSuccess());
        assertEquals(DispatchStatus.NO_AVAILABLE_AMBULANCES, r2.getStatus());
        assertEquals("CASE-B", r2.getEmergencyCase().orElseThrow().getId());

        // Fleet state is consistent: only 1 assignment exists
        assertEquals(Optional.of("CASE-A"), fleetService.getAmbulanceOrThrow("AMB-SINGLE").getAssignedCaseId());
    }

    @Test
    @DisplayName("Dynamic traffic changes affect ambulance selection")
    void testTrafficChangesAffectAmbulanceSelection() {
        // AMB-NORTH at STATION-NORTH: baseline travel time to SCENE-A is 3.0 min
        fleetService.registerAmbulance("AMB-NORTH", "STATION-NORTH");
        // AMB-SOUTH at STATION-SOUTH: via SCENE-B (2.5 min) + SCENE-B->SCENE-A (4.0 min) = 6.5 min
        fleetService.registerAmbulance("AMB-SOUTH", "STATION-SOUTH");

        // Inject severe congestion on North Boulevard (STATION-NORTH -> SCENE-A)
        // Set travel time to 15.0 minutes
        trafficEngine.setTraffic("STATION-NORTH", "SCENE-A", TrafficLevel.HEAVY);
        // Base travel time was 3.0 min, with HEAVY multiplier (2.5x) it increases to 7.5 min
        // Let's set to SEVERE (4.0x -> 12.0 min)
        trafficEngine.setTraffic("STATION-NORTH", "SCENE-A", TrafficLevel.SEVERE);

        EmergencyCase emergency = emergencyService.createCase(
                "CASE-TRAFFIC", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Traffic trauma");
        queueService.enqueue(emergency);

        DispatchResult result = dispatchService.dispatchNext();

        assertTrue(result.isSuccess());
        // AMB-SOUTH route via SCENE-B takes 2.5 + 4.0 = 6.5 min, which is faster than 12.0 min!
        assertEquals("AMB-SOUTH", result.getAssignedAmbulance().orElseThrow().getId());
        assertEquals(6.5, result.getEstimatedTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Road closures affect route feasibility: Reroutes to alternative open paths or alternative ambulances")
    void testRoadClosureAffectsAmbulanceSelection() {
        fleetService.registerAmbulance("AMB-NORTH", "STATION-NORTH");
        fleetService.registerAmbulance("AMB-SOUTH", "STATION-SOUTH");

        // Road Incident: North Boulevard (STATION-NORTH -> SCENE-A) is completely CLOSED
        incidentService.reportIncident(RoadIncident.accident("INC-01", "STATION-NORTH", "SCENE-A", T0));
        assertTrue(graph.isRoadClosed("STATION-NORTH", "SCENE-A"));

        EmergencyCase emergency = emergencyService.createCase(
                "CASE-CLOSURE", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "Closed road case");
        queueService.enqueue(emergency);

        DispatchResult result = dispatchService.dispatchNext();

        assertTrue(result.isSuccess());
        // Because direct road from North is closed, AMB-SOUTH is dispatched
        assertEquals("AMB-SOUTH", result.getAssignedAmbulance().orElseThrow().getId());
    }

    @Test
    @DisplayName("Failed assignments leave no partial state changes")
    void testFailedAssignmentNoPartialState() {
        fleetService.registerAmbulance("AMB-1", "STATION-NORTH");

        // Case at isolated location
        EmergencyCase isolated = emergencyService.createCase(
                "CASE-FAIL", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "ISOLATED-ISLAND", "No path");
        queueService.enqueue(isolated);

        DispatchResult result = dispatchService.dispatchNext();
        assertFalse(result.isSuccess());

        // Verifications of clean rollback / zero partial state:
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-1").getStatus());
        assertEquals(Optional.empty(), fleetService.getAmbulanceOrThrow("AMB-1").getAssignedCaseId());
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, emergencyService.getCaseOrThrow("CASE-FAIL").getStatus());
        assertEquals(Optional.empty(), emergencyService.getCaseOrThrow("CASE-FAIL").getAssignedAmbulanceId());
        assertEquals(1, queueService.size());
    }

    @Test
    @DisplayName("Batch dispatch allocates all available ambulances to pending emergencies in priority order")
    void testDispatchBatch() {
        fleetService.registerAmbulance("AMB-NORTH", "STATION-NORTH");
        fleetService.registerAmbulance("AMB-SOUTH", "STATION-SOUTH");

        emergencyService.createCase("CASE-CRIT", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE-A", "Arrest");
        emergencyService.createCase("CASE-HIGH", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-B", "Fracture");
        emergencyService.createCase("CASE-LOW", EmergencyType.OTHER, EmergencySeverity.LOW, "SCENE-A", "Minor");

        queueService.enqueue("CASE-CRIT");
        queueService.enqueue("CASE-HIGH");
        queueService.enqueue("CASE-LOW");

        List<DispatchResult> results = dispatchService.dispatchAllPending();

        // 3 calls attempted: 2 succeed, 3rd fails due to no ambulances left
        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("CASE-CRIT", results.get(0).getEmergencyCase().orElseThrow().getId());

        assertTrue(results.get(1).isSuccess());
        assertEquals("CASE-HIGH", results.get(1).getEmergencyCase().orElseThrow().getId());

        assertFalse(results.get(2).isSuccess());
        assertEquals(DispatchStatus.NO_AVAILABLE_AMBULANCES, results.get(2).getStatus());
        assertEquals("CASE-LOW", results.get(2).getEmergencyCase().orElseThrow().getId());

        // Only CASE-LOW remains in queue
        assertEquals(1, queueService.size());
        assertEquals("CASE-LOW", queueService.peekNextEmergency().orElseThrow().getId());
    }

    @Test
    @DisplayName("Cancellation handling: Ignores and removes cancelled emergency cases during dispatch")
    void testCancellationDuringDispatch() {
        fleetService.registerAmbulance("AMB-1", "STATION-NORTH");
        EmergencyCase emergency = emergencyService.createCase(
                "CASE-CANCELLED", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "Patient cancelled");
        queueService.enqueue(emergency);

        // Emergency is cancelled externally before dispatch runs
        emergencyService.cancelCase("CASE-CANCELLED", "Patient no longer requires EMS");

        // dispatchNext skips the cancelled case (queue purges it) and finds no other pending cases
        DispatchResult result = dispatchService.dispatchNext();
        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.NO_PENDING_EMERGENCIES, result.getStatus());
        assertTrue(queueService.isEmpty());
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-1").getStatus());
    }

    @Test
    @DisplayName("Handles direct dispatch of an ineligible (already terminal or assigned) emergency")
    void testDirectDispatchOfIneligibleEmergency() {
        fleetService.registerAmbulance("AMB-1", "STATION-NORTH");
        EmergencyCase c = emergencyService.createCase(
                "CASE-TERM", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "Terminal case");
        emergencyService.cancelCase("CASE-TERM", "Cancelled immediately");

        DispatchResult result = dispatchService.dispatchEmergency(c);
        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.CASE_INELIGIBLE, result.getStatus());
    }

    @Test
    @DisplayName("Routing integration with A* Algorithm produces identical optimal assignments")
    void testRoutingIntegrationWithAStar() {
        fleetService.registerAmbulance("AMB-NORTH", "STATION-NORTH");
        fleetService.registerAmbulance("AMB-SOUTH", "STATION-SOUTH");

        AmbulanceDispatchService aStarDispatchService = new AmbulanceDispatchService(
                graph,
                new com.resqroute.algorithm.AStarRoutingAlgorithm(),
                emergencyService,
                fleetService,
                queueService
        );

        EmergencyCase emergency = emergencyService.createCase(
                "CASE-ASTAR", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Trauma scene");
        queueService.enqueue(emergency);

        DispatchResult result = aStarDispatchService.dispatchNext();
        assertTrue(result.isSuccess());
        assertEquals("AMB-NORTH", result.getAssignedAmbulance().orElseThrow().getId());
        assertEquals(3.0, result.getEstimatedTravelTimeMinutes(), 1e-4);
    }
}

