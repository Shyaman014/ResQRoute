package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.hospital.*;
import com.resqroute.model.*;
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

@DisplayName("Phase 13: Intelligent Hospital Selection Algorithm Tests")
class HospitalSelectionServiceTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;
    private EmergencyManagementService emergencyService;
    private HospitalManagementService hospitalService;
    private TrafficSimulationEngine trafficEngine;
    private RoadIncidentService incidentService;
    private HospitalSelectionService selectionService;
    private MutableTestClock testClock;

    private static final Instant T0 = Instant.parse("2026-10-18T14:00:00Z");

    static class MutableTestClock extends Clock {
        private Instant currentInstant;

        MutableTestClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
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

        // Nodes
        Node scene = new Node("SCENE", 12.970, 77.600, "Emergency Scene");
        Node hospNorthNode = new Node("HOSP-NORTH-NODE", 12.990, 77.600, "North Medical District");
        Node hospSouthNode = new Node("HOSP-SOUTH-NODE", 12.950, 77.600, "South Medical District");
        Node hospEastNode = new Node("HOSP-EAST-NODE", 12.970, 77.630, "East Medical District");
        Node isolated = new Node("ISOLATED-NODE", 13.100, 77.800, "Disconnected Node");

        graph.addNode(scene);
        graph.addNode(hospNorthNode);
        graph.addNode(hospSouthNode);
        graph.addNode(hospEastNode);
        graph.addNode(isolated);

        // Edges:
        // SCENE -> HOSP-NORTH: 3.0 km, 4.0 min
        graph.addEdge("SCENE", "HOSP-NORTH-NODE", 3.0, 4.0, "North Radial");
        // SCENE -> HOSP-SOUTH: 5.0 km, 7.0 min
        graph.addEdge("SCENE", "HOSP-SOUTH-NODE", 5.0, 7.0, "South Radial");
        // SCENE -> HOSP-EAST: 6.0 km, 10.0 min
        graph.addEdge("SCENE", "HOSP-EAST-NODE", 6.0, 10.0, "East Radial");

        testClock = new MutableTestClock(T0);
        fleetService = new AmbulanceFleetService(graph);
        emergencyService = new EmergencyManagementService(graph, fleetService, testClock);
        hospitalService = new HospitalManagementService(graph, testClock);
        trafficEngine = new TrafficSimulationEngine(graph, 42L);
        incidentService = new RoadIncidentService(graph);

        selectionService = new HospitalSelectionService(
                graph,
                new DijkstraRoutingAlgorithm(),
                hospitalService,
                emergencyService
        );
    }

    @Test
    @DisplayName("Selects the fastest eligible hospital and reserves capacity")
    void testFastestEligibleHospitalSelection() {
        Hospital northHosp = Hospital.builder()
                .id("HOSP-NORTH")
                .name("Apex North Hospital")
                .locationNodeId("HOSP-NORTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER))
                .totalCapacity(10)
                .availableCapacity(5)
                .build();

        Hospital southHosp = Hospital.builder()
                .id("HOSP-SOUTH")
                .name("South Community Hospital")
                .locationNodeId("HOSP-SOUTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER))
                .totalCapacity(10)
                .availableCapacity(8)
                .build();

        hospitalService.registerHospital(northHosp);
        hospitalService.registerHospital(southHosp);

        EmergencyCase traumaCase = emergencyService.createCase(
                "CASE-1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE", "Fracture trauma");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-1");

        assertTrue(result.isSuccess());
        assertEquals(HospitalSelectionStatus.SUCCESS, result.getStatus());

        HospitalRecommendation selected = result.getSelectedHospital().orElseThrow();
        assertEquals("HOSP-NORTH", selected.getHospitalId());
        assertEquals(4.0, selected.getEstimatedTravelTimeMinutes(), 1e-4);
        assertEquals(3.0, selected.getDistanceKm(), 1e-4);
        assertEquals(1, selected.getRank());

        // Capacity was reserved at HOSP-NORTH: available was 5, now 4
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());
        assertTrue(hospitalService.hasActiveReservation("HOSP-NORTH", "CASE-1"));

        // HOSP-SOUTH capacity untouched
        assertEquals(8, hospitalService.getHospitalOrThrow("HOSP-SOUTH").getAvailableCapacity());

        // Ranked alternatives list contains HOSP-SOUTH as rank 2
        assertEquals(1, result.getRankedAlternatives().size());
        assertEquals("HOSP-SOUTH", result.getRankedAlternatives().get(0).getHospitalId());

        // Emergency case destination node was designated
        EmergencyCase updatedCase = emergencyService.getCaseOrThrow("CASE-1");
        assertEquals(Optional.of("HOSP-NORTH-NODE"), updatedCase.getDestinationHospitalId());
    }

    @Test
    @DisplayName("Mandatory clinical capability constraints override low travel times")
    void testCapabilityMismatchOverridesProximity() {
        // HOSP-NORTH is close (4.0 min), but ONLY has EMERGENCY_ROOM (cannot handle severe trauma or cardiac)
        Hospital northBasic = Hospital.builder()
                .id("HOSP-NORTH")
                .name("North Basic Clinic")
                .locationNodeId("HOSP-NORTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.OTHER))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(10)
                .availableCapacity(5)
                .build();

        // HOSP-SOUTH is farther (7.0 min), but is a certified TRAUMA_CENTER with ICU
        Hospital southTrauma = Hospital.builder()
                .id("HOSP-SOUTH")
                .name("South Regional Trauma Center")
                .locationNodeId("HOSP-SOUTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER, HospitalCapability.ICU))
                .totalCapacity(10)
                .availableCapacity(5)
                .build();

        hospitalService.registerHospital(northBasic);
        hospitalService.registerHospital(southTrauma);

        // High severity trauma case
        EmergencyCase traumaCase = emergencyService.createCase(
                "CASE-TRAUMA", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE", "Major trauma");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-TRAUMA");

        assertTrue(result.isSuccess());
        // Must select HOSP-SOUTH because HOSP-NORTH lacks TRAUMA_CENTER capability
        assertEquals("HOSP-SOUTH", result.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(7.0, result.getSelectedHospital().orElseThrow().getEstimatedTravelTimeMinutes(), 1e-4);

        // HOSP-NORTH was completely excluded from alternatives
        assertTrue(result.getRankedAlternatives().isEmpty());
    }

    @Test
    @DisplayName("Returns NO_COMPATIBLE_HOSPITALS when no hospital satisfies medical requirements")
    void testNoCompatibleHospitals() {
        Hospital basicHosp = new Hospital("HOSP-BASIC", "Basic Clinic", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(basicHosp);

        EmergencyCase cardiacCase = emergencyService.createCase(
                "CASE-CARDIAC", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE", "Cardiac arrest");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-CARDIAC");

        assertFalse(result.isSuccess());
        assertEquals(HospitalSelectionStatus.NO_COMPATIBLE_HOSPITALS, result.getStatus());
        assertEquals(5, basicHosp.getAvailableCapacity());
    }

    @Test
    @DisplayName("Excludes full and administratively closed hospitals")
    void testFullAndClosedHospitalsExcluded() {
        // HOSP-NORTH is closest (4.0 min), but status is BUSY (0 available capacity)
        Hospital northFull = Hospital.builder()
                .id("HOSP-NORTH")
                .name("North Hospital Full")
                .locationNodeId("HOSP-NORTH-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(5)
                .availableCapacity(0)
                .status(HospitalStatus.BUSY)
                .build();

        // HOSP-SOUTH is farther (7.0 min), but status is CLOSED
        Hospital southClosed = Hospital.builder()
                .id("HOSP-SOUTH")
                .name("South Hospital Closed")
                .locationNodeId("HOSP-SOUTH-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(5)
                .availableCapacity(5)
                .status(HospitalStatus.CLOSED)
                .build();

        // HOSP-EAST is farthest (10.0 min), but OPEN with available beds
        Hospital eastOpen = Hospital.builder()
                .id("HOSP-EAST")
                .name("East Hospital Open")
                .locationNodeId("HOSP-EAST-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(5)
                .availableCapacity(3)
                .status(HospitalStatus.OPEN)
                .build();

        hospitalService.registerHospital(northFull);
        hospitalService.registerHospital(southClosed);
        hospitalService.registerHospital(eastOpen);

        EmergencyCase medicalCase = emergencyService.createCase(
                "CASE-MED", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Medical emergency");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-MED");

        assertTrue(result.isSuccess());
        // Selects HOSP-EAST because North is full and South is closed
        assertEquals("HOSP-EAST", result.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(10.0, result.getSelectedHospital().orElseThrow().getEstimatedTravelTimeMinutes(), 1e-4);
        assertEquals(2, hospitalService.getHospitalOrThrow("HOSP-EAST").getAvailableCapacity());
    }

    @Test
    @DisplayName("Returns ALL_HOSPITALS_FULL_OR_BUSY when all compatible hospitals have 0 capacity")
    void testAllHospitalsFullOrBusy() {
        Hospital fullHosp = Hospital.builder()
                .id("HOSP-FULL")
                .name("Full Hospital")
                .locationNodeId("HOSP-NORTH-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(2)
                .availableCapacity(0)
                .status(HospitalStatus.BUSY)
                .build();
        hospitalService.registerHospital(fullHosp);

        EmergencyCase c = emergencyService.createCase(
                "CASE-1", EmergencyType.MEDICAL, EmergencySeverity.LOW, "SCENE", "Minor");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-1");

        assertFalse(result.isSuccess());
        assertEquals(HospitalSelectionStatus.ALL_HOSPITALS_FULL_OR_BUSY, result.getStatus());
    }

    @Test
    @DisplayName("Returns ALL_HOSPITALS_UNREACHABLE when roads are blocked or destinations disconnected")
    void testUnreachableHospitals() {
        Hospital isolatedHosp = new Hospital("HOSP-ISOLATED", "Island Clinic", "ISOLATED-NODE", 5);
        hospitalService.registerHospital(isolatedHosp);

        EmergencyCase c = emergencyService.createCase(
                "CASE-ISOLATED", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Isolated patient");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-ISOLATED");

        assertFalse(result.isSuccess());
        assertEquals(HospitalSelectionStatus.ALL_HOSPITALS_UNREACHABLE, result.getStatus());
    }

    @Test
    @DisplayName("Traffic congestion shifts hospital ranking dynamically")
    void testTrafficChangesAffectRanking() {
        Hospital northHosp = new Hospital("HOSP-NORTH", "North Hospital", "HOSP-NORTH-NODE", 5);
        Hospital southHosp = new Hospital("HOSP-SOUTH", "South Hospital", "HOSP-SOUTH-NODE", 5);
        hospitalService.registerHospital(northHosp);
        hospitalService.registerHospital(southHosp);

        // Base travel times: North = 4.0 min, South = 7.0 min
        // Inject SEVERE congestion on North Radial (SCENE -> HOSP-NORTH-NODE): multiplier 4.0x -> 16.0 min!
        trafficEngine.setTraffic("SCENE", "HOSP-NORTH-NODE", TrafficLevel.SEVERE);

        EmergencyCase c = emergencyService.createCase(
                "CASE-CONGESTION", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Congestion case");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-CONGESTION");

        assertTrue(result.isSuccess());
        // South Hospital at 7.0 min is now much faster than congested North Hospital at 14.0 min!
        assertEquals("HOSP-SOUTH", result.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(7.0, result.getSelectedHospital().orElseThrow().getEstimatedTravelTimeMinutes(), 1e-4);

        // North Hospital is relegated to alternative
        assertEquals(1, result.getRankedAlternatives().size());
        assertEquals("HOSP-NORTH", result.getRankedAlternatives().get(0).getHospitalId());
        assertEquals(14.0, result.getRankedAlternatives().get(0).getEstimatedTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Road closures re-route to alternative open hospitals")
    void testRoadClosureAffectsFeasibility() {
        Hospital northHosp = new Hospital("HOSP-NORTH", "North Hospital", "HOSP-NORTH-NODE", 5);
        Hospital southHosp = new Hospital("HOSP-SOUTH", "South Hospital", "HOSP-SOUTH-NODE", 5);
        hospitalService.registerHospital(northHosp);
        hospitalService.registerHospital(southHosp);

        // Incident closes the road to North Hospital entirely
        incidentService.reportIncident(RoadIncident.accident("INC-1", "SCENE", "HOSP-NORTH-NODE", T0));
        assertTrue(graph.isRoadClosed("SCENE", "HOSP-NORTH-NODE"));

        EmergencyCase c = emergencyService.createCase(
                "CASE-CLOSURE", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Closure case");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-CLOSURE");

        assertTrue(result.isSuccess());
        // North Hospital is unreachable, so South Hospital is selected
        assertEquals("HOSP-SOUTH", result.getSelectedHospital().orElseThrow().getHospitalId());
    }

    @Test
    @DisplayName("Fallback selection: automatically secures capacity at next ranked hospital if top choice fills concurrently")
    void testFallbackSelectionOnConcurrentCapacityExhaustion() {
        // HOSP-NORTH has only 1 bed left
        Hospital northHosp = Hospital.builder()
                .id("HOSP-NORTH")
                .name("North Hospital")
                .locationNodeId("HOSP-NORTH-NODE")
                .totalCapacity(5)
                .availableCapacity(1)
                .build();

        // HOSP-SOUTH has 5 beds
        Hospital southHosp = Hospital.builder()
                .id("HOSP-SOUTH")
                .name("South Hospital")
                .locationNodeId("HOSP-SOUTH-NODE")
                .totalCapacity(5)
                .availableCapacity(5)
                .build();

        hospitalService.registerHospital(northHosp);
        hospitalService.registerHospital(southHosp);

        EmergencyCase case1 = emergencyService.createCase(
                "CASE-1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Case 1");
        EmergencyCase case2 = emergencyService.createCase(
                "CASE-2", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Case 2");

        // Case 1 takes the last bed at HOSP-NORTH
        HospitalSelectionResult res1 = selectionService.selectAndReserveHospital("CASE-1");
        assertTrue(res1.isSuccess());
        assertEquals("HOSP-NORTH", res1.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(0, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());

        // Case 2 tries to select. HOSP-NORTH is now full -> fallback secures HOSP-SOUTH!
        HospitalSelectionResult res2 = selectionService.selectAndReserveHospital("CASE-2");
        assertTrue(res2.isSuccess());
        assertEquals("HOSP-SOUTH", res2.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSP-SOUTH").getAvailableCapacity());
    }

    @Test
    @DisplayName("Prevents duplicate reservations for the same emergency case")
    void testDuplicateReservationPrevention() {
        Hospital hosp = new Hospital("HOSP-NORTH", "North Hospital", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(hosp);

        emergencyService.createCase("CASE-DUP", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Dup case");

        // First selection succeeds
        HospitalSelectionResult r1 = selectionService.selectAndReserveHospital("CASE-DUP");
        assertTrue(r1.isSuccess());
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());

        // Second selection attempt for same case detects existing reservation and rejects duplicate booking
        HospitalSelectionResult r2 = selectionService.selectAndReserveHospital("CASE-DUP");
        assertFalse(r2.isSuccess());
        assertEquals(HospitalSelectionStatus.RESERVATION_FAILED, r2.getStatus());
        // Capacity remained untouched at 4
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());
    }

    @Test
    @DisplayName("Cancellation correctly releases reserved hospital capacity")
    void testCancellationAndReservationRelease() {
        Hospital hosp = new Hospital("HOSP-NORTH", "North Hospital", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(hosp);

        emergencyService.createCase("CASE-CANCEL", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE", "Cancel case");

        // Reserve hospital
        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-CANCEL");
        assertTrue(result.isSuccess());
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());

        // Cancel hospital assignment
        EmergencyCase cancelled = selectionService.cancelHospitalAssignment("CASE-CANCEL", "Patient transferred to air ambulance");

        assertEquals(EmergencyStatus.CANCELLED, cancelled.getStatus());
        // Bed released back to hospital
        assertEquals(5, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());
        assertFalse(hospitalService.hasActiveReservation("HOSP-NORTH", "CASE-CANCEL"));
    }

    @Test
    @DisplayName("Rejects hospital selection for terminal emergency cases")
    void testTerminalCaseRejected() {
        Hospital hosp = new Hospital("HOSP-NORTH", "North Hospital", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(hosp);

        emergencyService.createCase("CASE-TERM", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE", "Terminal");
        emergencyService.cancelCase("CASE-TERM", "Cancelled before hospital selection");

        HospitalSelectionResult result = selectionService.selectAndReserveHospital("CASE-TERM");
        assertFalse(result.isSuccess());
        assertEquals(HospitalSelectionStatus.CASE_INELIGIBLE, result.getStatus());
        assertEquals(5, hospitalService.getHospitalOrThrow("HOSP-NORTH").getAvailableCapacity());
    }
}
