package com.resqroute.edgecases;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.database.DatabaseConfig;
import com.resqroute.database.DatabaseConnectionManager;
import com.resqroute.exception.*;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.service.*;
import com.resqroute.simulation.engine.CitySimulationEngine;
import com.resqroute.simulation.model.CityScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 22: Edge Cases, Concurrency Conflicts & System Reliability Tests")
class SystemEdgeCasesAndReliabilityTest {

    // ==========================================
    // 1. Empty Inputs & Invalid Identifiers
    // ==========================================

    @Test
    @DisplayName("Empty inputs and invalid identifiers reject cleanly with appropriate exceptions")
    void testEmptyInputsAndInvalidIdentifiersRejectCleanly() {
        RoadGraph graph = new RoadGraph();

        assertThrows(NullPointerException.class, () -> graph.addNode(null));
        assertThrows(NodeNotFoundException.class, () -> graph.getRequiredNode("MISSING"));

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        assertThrows(NodeNotFoundException.class, () -> dijkstra.findFastestRoute(graph, "MISSING_A", "MISSING_B"));

        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        assertThrows(NodeNotFoundException.class, () -> fleetService.registerAmbulance("AMB-1", "NON_EXISTENT_NODE"));

        HospitalManagementService hospitalService = new HospitalManagementService(graph);
        Hospital invalidHosp = Hospital.builder()
                .id("H1")
                .name("Missing Node Hosp")
                .locationNodeId("MISSING_NODE")
                .totalCapacity(5)
                .availableCapacity(5)
                .build();
        assertThrows(NodeNotFoundException.class, () -> hospitalService.registerHospital(invalidHosp));
    }

    // ==========================================
    // 2. Duplicate Operations
    // ==========================================

    @Test
    @DisplayName("Duplicate node, edge, ambulance, and emergency case registrations are rejected")
    void testDuplicateOperationsRejected() {
        RoadGraph graph = new RoadGraph();
        Node n1 = new Node("N1", 12.0, 77.0, "Node 1");
        Node n2 = new Node("N2", 12.1, 77.1, "Node 2");

        graph.addNode(n1);
        graph.addNode(n2);
        assertThrows(DuplicateNodeException.class, () -> graph.addNode(new Node("N1", 12.0, 77.0, "Duplicate Node 1")));

        graph.addEdge("N1", "N2", 5.0, 5.0, "Edge 1");
        assertThrows(DuplicateEdgeException.class, () -> graph.addEdge("N1", "N2", 5.0, 5.0, "Duplicate Edge 1"));

        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        fleetService.registerAmbulance("AMB-1", "N1");
        assertThrows(IllegalArgumentException.class, () -> fleetService.registerAmbulance("AMB-1", "N1"));

        EmergencyManagementService emergencyService = new EmergencyManagementService(graph, fleetService);
        EmergencyCase emergency = new EmergencyCase("CASE-1", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "N1", Instant.now(), "Incident");
        emergencyService.createCase(emergency);
        assertThrows(DuplicateCaseException.class, () -> emergencyService.createCase(emergency));
    }

    // ==========================================
    // 3. Concurrent Hospital Reservations
    // ==========================================

    @Test
    @DisplayName("Simultaneous concurrent reservations cannot overbook hospital beyond available capacity")
    void testConcurrentHospitalReservationsPreventOverbooking() throws InterruptedException {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("HOSP_NODE", 12.0, 77.0, "Hospital Node"));

        HospitalManagementService hospitalService = new HospitalManagementService(graph);
        Hospital hospital = Hospital.builder()
                .id("HOSP_LIMITED")
                .name("Limited Capacity Center")
                .locationNodeId("HOSP_NODE")
                .totalCapacity(2)
                .availableCapacity(2)
                .supportedEmergencyTypes(Set.of(EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.TRAUMA_CENTER))
                .build();
        hospitalService.registerHospital(hospital);

        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final String caseId = "CASE_CONCURRENT_" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    hospitalService.reserveCapacity("HOSP_LIMITED", caseId);
                    successCount.incrementAndGet();
                } catch (HospitalCapacityExceededException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent reservations timed out");
        assertEquals(2, successCount.get(), "Exactly 2 reservations must succeed for capacity of 2");
        assertEquals(8, failureCount.get(), "Remaining 8 concurrent requests must be rejected");
        assertEquals(0, hospitalService.getHospitalOrThrow("HOSP_LIMITED").getAvailableCapacity());
    }

    // ==========================================
    // 4. Concurrent Ambulance Assignment Conflicts
    // ==========================================

    @Test
    @DisplayName("Simultaneous ambulance assignment attempts ensure exactly one case succeeds without double-booking")
    void testConcurrentAmbulanceAssignmentConflict() throws InterruptedException {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("STATION", 12.0, 77.0, "Station"));
        graph.addNode(new Node("P1", 12.1, 77.1, "Pickup 1"));
        graph.addNode(new Node("P2", 12.2, 77.2, "Pickup 2"));

        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        fleetService.registerAmbulance("AMB-SINGLE", "STATION");

        EmergencyManagementService emergencyService = new EmergencyManagementService(graph, fleetService);
        EmergencyCase caseA = new EmergencyCase("CASE-A", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "P1", Instant.now(), "Incident A");
        EmergencyCase caseB = new EmergencyCase("CASE-B", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "P2", Instant.now(), "Incident B");
        emergencyService.createCase(caseA);
        emergencyService.createCase(caseB);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startGate.await();
                emergencyService.assignAmbulance("CASE-A", "AMB-SINGLE");
                successCount.incrementAndGet();
            } catch (AmbulanceUnavailableException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                endGate.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startGate.await();
                emergencyService.assignAmbulance("CASE-B", "AMB-SINGLE");
                successCount.incrementAndGet();
            } catch (AmbulanceUnavailableException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                endGate.countDown();
            }
        });

        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(1, successCount.get(), "Exactly one case must successfully claim the sole ambulance");
        assertEquals(1, conflictCount.get(), "The competing case must be cleanly rejected with AmbulanceUnavailableException");

        Ambulance assignedAmb = fleetService.getAmbulanceOrThrow("AMB-SINGLE");
        assertEquals(AmbulanceStatus.ASSIGNED, assignedAmb.getStatus());
        assertTrue(assignedAmb.getAssignedCaseId().isPresent());
    }

    // ==========================================
    // 5. Database Transaction Rollback on Failure
    // ==========================================

    @Test
    @DisplayName("Database transaction atomically rolls back all preliminary changes when a mid-transaction error occurs")
    void testDatabaseTransactionRollbackOnMidOperationFailure() throws SQLException {
        DatabaseConnectionManager connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();

        assertThrows(SQLException.class, () -> {
            connectionManager.executeTransaction(conn -> {
                // Step 1: Insert valid node
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO road_nodes (id, name, latitude, longitude) VALUES ('TX_NODE_1', 'Test Node 1', 12.0, 77.0)")) {
                    ps.executeUpdate();
                }

                // Step 2: Intentionally trigger failure mid-transaction
                throw new SQLException("Simulated mid-transaction failure");
            });
        });

        // Verify Step 1 was completely rolled back
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM road_nodes WHERE id = 'TX_NODE_1'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Uncommitted node from aborted transaction must not exist in database");
        } finally {
            connectionManager.close();
        }
    }

    // ==========================================
    // 6. Simulation Reset
    // ==========================================

    @Test
    @DisplayName("CitySimulationEngine reset completely restores initial state, elapsed time, and ambulance positions")
    void testSimulationResetRestoresCompleteInitialState() {
        CityScenario scenario = CityScenario.createDefaultMetropolis(42L);
        CitySimulationEngine engine = new CitySimulationEngine(scenario);

        // Run simulation for 10 minutes
        engine.runFor(Duration.ofMinutes(10));
        assertTrue(engine.getElapsedSimulationTime().compareTo(Duration.ZERO) > 0);
        assertFalse(engine.getSimulationEvents().isEmpty());

        // Reset simulation
        engine.reset();

        // Verify state is clean and restored to T = 0
        assertEquals(Duration.ZERO, engine.getElapsedSimulationTime());
        assertTrue(engine.getSimulationEvents().isEmpty());

        // Initial ambulances are back at their station nodes with status AVAILABLE
        for (Ambulance initialAmb : scenario.getInitialAmbulances()) {
            Ambulance current = engine.getFleetService().getAmbulanceOrThrow(initialAmb.getId());
            assertEquals(AmbulanceStatus.AVAILABLE, current.getStatus());
            assertEquals(initialAmb.getCurrentLocationNodeId(), current.getCurrentLocationNodeId());
        }
    }

    // ==========================================
    // 7. Stale Route Updates
    // ==========================================

    @Test
    @DisplayName("AutomaticReroutingService ignores incidents on already traversed roads but reroutes on upcoming roads")
    void testStaleRouteUpdateIgnoringPassedIncident() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("N1", 12.0, 77.0, "Start"));
        graph.addNode(new Node("N2", 12.1, 77.1, "Mid"));
        graph.addNode(new Node("N3", 12.2, 77.2, "Next"));
        graph.addNode(new Node("N4", 12.3, 77.3, "End"));
        graph.addNode(new Node("N_ALT", 12.15, 77.25, "Alt"));

        graph.addEdge("N1", "N2", 2.0, 2.0, "N1-N2");
        graph.addEdge("N2", "N3", 2.0, 2.0, "N2-N3");
        graph.addEdge("N3", "N4", 2.0, 2.0, "N3-N4");
        graph.addEdge("N2", "N_ALT", 2.5, 2.5, "N2-ALT");
        graph.addEdge("N_ALT", "N4", 2.5, 2.5, "ALT-N4");

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        AutomaticReroutingService reroutingService = new AutomaticReroutingService(graph, dijkstra);

        RouteResult route = dijkstra.findFastestRoute(graph, "N1", "N4");
        TrackedRoute tracked = reroutingService.trackRoute("MISSION-EDGE", route);

        // Ambulance reaches N2 (it has passed N1 -> N2, waypoint index 1)
        reroutingService.trackRoute(tracked.advanceToWaypoint(1));

        // 1. Stale incident occurs behind the vehicle on N1 -> N2
        List<RerouteResponse> responsesPassed = reroutingService.onRoadClosure("N1", "N2");
        assertEquals(1, responsesPassed.size());
        assertFalse(responsesPassed.getFirst().isRerouted(), "Closure on already traversed road segment must NOT trigger reroute");
        assertEquals(RerouteTriggerReason.UNRELATED_ROAD, responsesPassed.getFirst().triggerReason());

        // 2. Incident occurs ahead of vehicle on N2 -> N3
        graph.setRoadAvailability("N2", "N3", false);
        List<RerouteResponse> responsesAhead = reroutingService.onRoadClosure("N2", "N3");
        assertEquals(1, responsesAhead.size());
        assertTrue(responsesAhead.getFirst().isRerouted(), "Closure on remaining road segment MUST trigger reroute");
        assertEquals(List.of("N1", "N2", "N_ALT", "N4"), responsesAhead.getFirst().newRoute().getNodeIds());
    }
}
