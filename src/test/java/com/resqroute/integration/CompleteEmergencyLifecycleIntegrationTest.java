package com.resqroute.integration;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.database.DatabaseConfig;
import com.resqroute.database.DatabaseConnectionManager;
import com.resqroute.database.service.ResQRouteDatabaseService;
import com.resqroute.dispatch.DispatchResult;
import com.resqroute.graph.RoadGraph;
import com.resqroute.hospital.FastestHospitalRankingPolicy;
import com.resqroute.hospital.HospitalSelectionResult;
import com.resqroute.model.*;
import com.resqroute.scheduling.AgingDispatchPolicy;
import com.resqroute.service.*;
import com.resqroute.simulation.model.CityScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 22: Comprehensive End-to-End Emergency Lifecycle Integration Tests")
class CompleteEmergencyLifecycleIntegrationTest {

    private static class TestClock extends Clock {
        private Instant current;

        TestClock(Instant start) {
            this.current = start;
        }

        public void advanceMinutes(long mins) {
            this.current = this.current.plusSeconds(mins * 60);
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
            return current;
        }
    }

    @Test
    @DisplayName("End-to-End: Report -> Dispatch -> On Scene -> Hospital Selection -> Transport -> Delivery & Release")
    void testFullEmergencyLifecycleWorkflow() {
        TestClock clock = new TestClock(Instant.parse("2026-10-19T08:00:00Z"));
        RoadGraph graph = new RoadGraph();

        // Setup topology: Station -> Scene -> Hospital
        graph.addNode(new Node("STATION", 12.91, 77.51, "Ambulance Station"));
        graph.addNode(new Node("SCENE", 12.93, 77.53, "Emergency Scene"));
        graph.addNode(new Node("HOSPITAL_A", 12.95, 77.55, "Apex Medical Center"));
        graph.addNode(new Node("HOSPITAL_B", 12.90, 77.50, "County Clinic"));

        graph.addEdge("STATION", "SCENE", 4.0, 8.0, "Station Way");
        graph.addEdge("SCENE", "HOSPITAL_A", 3.0, 6.0, "Hospital Ave");
        graph.addEdge("SCENE", "HOSPITAL_B", 10.0, 20.0, "County Road");

        // Fleet, Management, Queue, Hospital services
        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        EmergencyManagementService emergencyService = new EmergencyManagementService(graph, fleetService, clock);
        EmergencyDispatchQueueService queueService = new EmergencyDispatchQueueService(emergencyService, new AgingDispatchPolicy(10.0), clock);
        HospitalManagementService hospitalService = new HospitalManagementService(graph, clock);

        Ambulance amb1 = new Ambulance("AMB-101", "STATION", EnumSet.allOf(AmbulanceCapability.class));
        fleetService.registerAmbulance(amb1);

        Hospital hospA = Hospital.builder()
                .id("HOSPITAL_A")
                .name("Apex Medical Center")
                .locationNodeId("HOSPITAL_A")
                .totalCapacity(5)
                .availableCapacity(5)
                .supportedEmergencyTypes(Set.of(EmergencyType.CARDIAC, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.ICU, HospitalCapability.TRAUMA_CENTER, HospitalCapability.CARDIAC_CATH_LAB))
                .build();
        hospitalService.registerHospital(hospA);

        AmbulanceDispatchService dispatchService = new AmbulanceDispatchService(graph, emergencyService, fleetService, queueService);
        HospitalSelectionService hospitalSelectionService = new HospitalSelectionService(
                graph, new DijkstraRoutingAlgorithm(), hospitalService, emergencyService,
                com.resqroute.hospital.DefaultHospitalSuitabilityMatcher.INSTANCE,
                FastestHospitalRankingPolicy.INSTANCE,
                clock
        );

        // 1. Intake: Report Cardiac Emergency & triage to queue
        EmergencyCase emergency = new EmergencyCase("CASE-500", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE", clock.instant(), "Sudden collapse");
        emergencyService.createCase(emergency);
        emergencyService.updateCaseStatus("CASE-500", EmergencyStatus.WAITING_FOR_DISPATCH);
        queueService.enqueue(emergencyService.getCaseOrThrow("CASE-500"));

        assertEquals(1, queueService.size());
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, emergencyService.getCaseOrThrow("CASE-500").getStatus());

        // 2. Dispatch
        DispatchResult dispatch = dispatchService.dispatchNext();
        assertTrue(dispatch.isSuccess(), "Dispatch should succeed: " + dispatch.getMessage());
        assertEquals("AMB-101", dispatch.getAssignedAmbulance().orElseThrow().getId());
        assertEquals(0, queueService.size());

        EmergencyCase dispatchedCase = emergencyService.getCaseOrThrow("CASE-500");
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, dispatchedCase.getStatus());
        assertEquals("AMB-101", dispatchedCase.getAssignedAmbulanceId().orElseThrow());
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-101").getStatus());

        // 3. Transit to Scene
        clock.advanceMinutes(5);
        emergencyService.updateCaseStatus("CASE-500", EmergencyStatus.AMBULANCE_EN_ROUTE);
        assertEquals(AmbulanceStatus.EN_ROUTE, fleetService.getAmbulanceOrThrow("AMB-101").getStatus());

        // 4. Arrive at Scene
        clock.advanceMinutes(3);
        emergencyService.updateCaseStatus("CASE-500", EmergencyStatus.PATIENT_PICKED_UP);
        assertEquals(AmbulanceStatus.ON_SCENE, fleetService.getAmbulanceOrThrow("AMB-101").getStatus());
        assertEquals("SCENE", fleetService.getAmbulanceOrThrow("AMB-101").getCurrentLocationNodeId());

        // 5. Hospital Selection & Bed Reservation
        HospitalSelectionResult hospResult = hospitalSelectionService.selectAndReserveHospital(dispatchedCase, "SCENE");
        assertTrue(hospResult.isSuccess(), "Hospital reservation should succeed: " + hospResult.getMessage());
        assertEquals("HOSPITAL_A", hospResult.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSPITAL_A").getAvailableCapacity());

        emergencyService.setDestinationHospital("CASE-500", "HOSPITAL_A");

        // 6. Transporting to Hospital
        emergencyService.updateCaseStatus("CASE-500", EmergencyStatus.TRANSPORTING);
        assertEquals(AmbulanceStatus.TRANSPORTING, fleetService.getAmbulanceOrThrow("AMB-101").getStatus());

        // 7. Arrival, Handover, and Completion
        clock.advanceMinutes(6);
        hospitalService.releaseCapacity("HOSPITAL_A", "CASE-500");
        emergencyService.updateCaseStatus("CASE-500", EmergencyStatus.COMPLETED);

        // Verify terminal clean state
        EmergencyCase finalCase = emergencyService.getCaseOrThrow("CASE-500");
        assertTrue(finalCase.isCompleted());
        Ambulance freedAmb = fleetService.getAmbulanceOrThrow("AMB-101");
        assertEquals(AmbulanceStatus.AVAILABLE, freedAmb.getStatus());
        assertTrue(freedAmb.getAssignedCaseId().isEmpty());
        assertEquals("HOSPITAL_A", freedAmb.getCurrentLocationNodeId());
        assertEquals(5, hospitalService.getHospitalOrThrow("HOSPITAL_A").getAvailableCapacity());
    }

    @Test
    @DisplayName("Dynamic road closure triggers automatic detour rerouting during transit")
    void testDynamicRoadClosureTriggersReroutingDuringTransit() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("N1", 12.0, 77.0, "Start"));
        graph.addNode(new Node("N2", 12.1, 77.1, "Waypoint 1"));
        graph.addNode(new Node("N3", 12.2, 77.2, "Blocked Waypoint"));
        graph.addNode(new Node("N4", 12.3, 77.3, "Destination"));
        graph.addNode(new Node("N_DETOUR", 12.15, 77.25, "Detour Point"));

        // Primary route: N1 -> N2 -> N3 -> N4 (travel time 2 + 2 + 2 = 6 mins)
        graph.addEdge("N1", "N2", 2.0, 2.0, "Primary 1");
        graph.addEdge("N2", "N3", 2.0, 2.0, "Primary 2 (to be closed)");
        graph.addEdge("N3", "N4", 2.0, 2.0, "Primary 3");

        // Detour: N2 -> N_DETOUR -> N4 (travel time 3 + 3 = 6 mins)
        graph.addEdge("N2", "N_DETOUR", 3.0, 3.0, "Detour In");
        graph.addEdge("N_DETOUR", "N4", 3.0, 3.0, "Detour Out");

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        AutomaticReroutingService reroutingService = new AutomaticReroutingService(graph, dijkstra);

        RouteResult initialRoute = dijkstra.findFastestRoute(graph, "N1", "N4");
        assertTrue(initialRoute.isReachable());
        assertEquals(List.of("N1", "N2", "N3", "N4"), initialRoute.getNodeIds());

        // Track route and simulate ambulance reaching N2 (index 1)
        TrackedRoute tracked = reroutingService.trackRoute("MISSION-1", initialRoute);
        reroutingService.trackRoute(tracked.advanceToWaypoint(1));

        // Close N2 -> N3
        graph.setRoadAvailability("N2", "N3", false);
        List<RerouteResponse> responses = reroutingService.onRoadClosure("N2", "N3");

        assertEquals(1, responses.size());
        RerouteResponse response = responses.getFirst();
        assertTrue(response.isRerouted());
        assertEquals(List.of("N1", "N2", "N_DETOUR", "N4"), response.newRoute().getNodeIds());
    }

    @Test
    @DisplayName("Traffic congestion shift dynamically redirects routing algorithm to faster path")
    void testTrafficCongestionUpdatesRoutePreference() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("ORIGIN", 12.0, 77.0, "Origin"));
        graph.addNode(new Node("NORTH", 12.1, 77.0, "North Highway"));
        graph.addNode(new Node("SOUTH", 11.9, 77.0, "South Bypass"));
        graph.addNode(new Node("DEST", 12.0, 77.2, "Destination"));

        // North: 5.0 mins base (2.5 + 2.5)
        graph.addEdge("ORIGIN", "NORTH", 5.0, 2.5, "North Entry");
        graph.addEdge("NORTH", "DEST", 5.0, 2.5, "North Exit");

        // South: 8.0 mins base (4.0 + 4.0)
        graph.addEdge("ORIGIN", "SOUTH", 6.0, 4.0, "South Entry");
        graph.addEdge("SOUTH", "DEST", 6.0, 4.0, "South Exit");

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();

        // Baseline: North route is faster (5.0 mins vs 8.0 mins)
        RouteResult initial = dijkstra.findFastestRoute(graph, "ORIGIN", "DEST");
        assertEquals(List.of("ORIGIN", "NORTH", "DEST"), initial.getNodeIds());
        assertEquals(5.0, initial.getTotalTravelTimeMinutes(), 1e-6);

        // Apply severe congestion on North route
        graph.updateTraffic("ORIGIN", "NORTH", TrafficLevel.SEVERE);
        graph.updateTraffic("NORTH", "DEST", TrafficLevel.SEVERE);

        // Re-evaluate: South route is now faster
        RouteResult afterCongestion = dijkstra.findFastestRoute(graph, "ORIGIN", "DEST");
        assertEquals(List.of("ORIGIN", "SOUTH", "DEST"), afterCongestion.getNodeIds());
        assertEquals(8.0, afterCongestion.getTotalTravelTimeMinutes(), 1e-6);
    }

    @Test
    @DisplayName("Hospital capacity saturation automatically diverts to the next best eligible hospital")
    void testHospitalCapacitySaturationSelectsAlternativeHospital() {
        TestClock clock = new TestClock(Instant.now());
        RoadGraph graph = new RoadGraph();

        graph.addNode(new Node("SCENE", 12.0, 77.0, "Pickup Scene"));
        graph.addNode(new Node("HOSP_CLOSE", 12.01, 77.01, "Close Hospital"));
        graph.addNode(new Node("HOSP_FAR", 12.05, 77.05, "Far Hospital"));

        graph.addEdge("SCENE", "HOSP_CLOSE", 2.0, 3.0, "Close Way");
        graph.addEdge("SCENE", "HOSP_FAR", 8.0, 10.0, "Far Highway");

        HospitalManagementService hospitalService = new HospitalManagementService(graph, clock);
        HospitalSelectionService selectionService = new HospitalSelectionService(
                graph, new DijkstraRoutingAlgorithm(), hospitalService, null,
                com.resqroute.hospital.DefaultHospitalSuitabilityMatcher.INSTANCE,
                FastestHospitalRankingPolicy.INSTANCE,
                clock
        );

        Hospital closeHosp = Hospital.builder()
                .id("HOSP_CLOSE")
                .name("Close Hospital")
                .locationNodeId("HOSP_CLOSE")
                .totalCapacity(1)
                .availableCapacity(1)
                .supportedEmergencyTypes(Set.of(EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER))
                .build();

        Hospital farHosp = Hospital.builder()
                .id("HOSP_FAR")
                .name("Far Hospital")
                .locationNodeId("HOSP_FAR")
                .totalCapacity(10)
                .availableCapacity(10)
                .supportedEmergencyTypes(Set.of(EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER))
                .build();

        hospitalService.registerHospital(closeHosp);
        hospitalService.registerHospital(farHosp);

        EmergencyCase case1 = new EmergencyCase("CASE-1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE", clock.instant(), "Injury 1");
        EmergencyCase case2 = new EmergencyCase("CASE-2", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE", clock.instant(), "Injury 2");

        // Case 1 takes the only bed in HOSP_CLOSE
        HospitalSelectionResult res1 = selectionService.selectAndReserveHospital(case1, "SCENE");
        assertTrue(res1.isSuccess(), "Case 1 hospital selection should succeed: " + res1.getMessage());
        assertEquals("HOSP_CLOSE", res1.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(0, hospitalService.getHospitalOrThrow("HOSP_CLOSE").getAvailableCapacity());
        assertEquals(HospitalStatus.BUSY, hospitalService.getHospitalOrThrow("HOSP_CLOSE").getStatus());

        // Case 2 evaluates and should automatically fall back to HOSP_FAR
        HospitalSelectionResult res2 = selectionService.selectAndReserveHospital(case2, "SCENE");
        assertTrue(res2.isSuccess(), "Case 2 hospital selection should succeed: " + res2.getMessage());
        assertEquals("HOSP_FAR", res2.getSelectedHospital().orElseThrow().getHospitalId());
        assertEquals(9, hospitalService.getHospitalOrThrow("HOSP_FAR").getAvailableCapacity());
    }

    @Test
    @DisplayName("Application restart: Database recovers saved network, fleet, and hospital state")
    void testDatabaseRestartAndStateRecovery() throws SQLException, IOException {
        Path tempDb = Files.createTempFile("resqroute_restart_test_", ".db");
        tempDb.toFile().deleteOnExit();

        DatabaseConnectionManager connectionManager1 = new DatabaseConnectionManager(DatabaseConfig.ofFile(tempDb));
        connectionManager1.initialize();

        CityScenario scenario = CityScenario.createDefaultMetropolis(101L);

        try (ResQRouteDatabaseService dbService = new ResQRouteDatabaseService(connectionManager1)) {
            dbService.saveScenarioSnapshot("METROPOLIS-SAVED", scenario);
            assertTrue(dbService.validateDatabaseIntegrity());
        }

        // Simulate application restart: reconnect with fresh connection manager to same database file
        DatabaseConnectionManager connectionManager2 = new DatabaseConnectionManager(DatabaseConfig.ofFile(tempDb));
        connectionManager2.initialize();

        try (ResQRouteDatabaseService restartedDbService = new ResQRouteDatabaseService(connectionManager2)) {
            RoadGraph loadedGraph = restartedDbService.loadRoadGraph();
            assertEquals(scenario.getRoadGraph().getAllNodes().size(), loadedGraph.getAllNodes().size());
            assertEquals(scenario.getRoadGraph().getAllEdges().size(), loadedGraph.getAllEdges().size());

            List<Ambulance> ambulances = restartedDbService.getAvailableAmbulances();
            assertFalse(ambulances.isEmpty());
            assertEquals(scenario.getInitialAmbulances().size(), ambulances.size());

            List<Hospital> hospitals = restartedDbService.getEligibleHospitals(EmergencyType.CARDIAC, Set.of());
            assertFalse(hospitals.isEmpty());
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    @Test
    @DisplayName("Emergency cancellation frees dispatched ambulance and cleans up reservations")
    void testEmergencyCancellationReleasesAmbulanceAndReservations() {
        TestClock clock = new TestClock(Instant.now());
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("BASE", 12.0, 77.0, "Base"));
        graph.addNode(new Node("PICKUP", 12.1, 77.1, "Pickup"));
        graph.addEdge("BASE", "PICKUP", 5.0, 5.0, "Way");

        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        EmergencyManagementService emergencyService = new EmergencyManagementService(graph, fleetService, clock);

        fleetService.registerAmbulance("AMB-99", "BASE");
        EmergencyCase emergency = new EmergencyCase("CASE-CANCEL-ME", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "PICKUP", clock.instant(), "False alert");
        emergencyService.createCase(emergency);

        // Assign ambulance
        emergencyService.assignAmbulance("CASE-CANCEL-ME", "AMB-99");
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-99").getStatus());
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, emergencyService.getCaseOrThrow("CASE-CANCEL-ME").getStatus());

        // Cancel emergency
        EmergencyCase cancelled = emergencyService.cancelCase("CASE-CANCEL-ME", "Patient condition stabilized");
        assertEquals(EmergencyStatus.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.isCancelled());

        // Verify fleet ambulance is instantly freed and available
        Ambulance amb = fleetService.getAmbulanceOrThrow("AMB-99");
        assertEquals(AmbulanceStatus.AVAILABLE, amb.getStatus());
        assertTrue(amb.getAssignedCaseId().isEmpty());
    }
}
