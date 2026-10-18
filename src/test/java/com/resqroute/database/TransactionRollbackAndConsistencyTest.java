package com.resqroute.database;

import com.resqroute.database.service.ResQRouteDatabaseService;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.model.CityScenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 20: Transactional Rollback, Multi-Entity Consistency & Restart Persistence Tests")
class TransactionRollbackAndConsistencyTest {

    private DatabaseConnectionManager connectionManager;
    private ResQRouteDatabaseService databaseService;
    private Path tempDbFile;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
        databaseService = new ResQRouteDatabaseService(connectionManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (databaseService != null) {
            databaseService.close();
        }
        if (tempDbFile != null && Files.exists(tempDbFile)) {
            Files.deleteIfExists(tempDbFile);
        }
    }

    @Test
    @DisplayName("Transaction rollback restores original database state upon exception")
    void testTransactionRollbackOnFailure() throws SQLException {
        CityScenario scenario = CityScenario.createDefaultMetropolis(42L);
        databaseService.saveScenarioSnapshot("METROPOLIS-BASE", scenario);

        Ambulance initialAmb = connectionManager.executeTransactionResult(
                conn -> databaseService.getAmbulanceRepository().findById("AMB-NORTH", conn).orElseThrow());
        assertEquals("NORTH-STATION", initialAmb.getCurrentLocationNodeId());

        // Attempt a transaction where step 1 updates location, but step 2 fails intentionally
        assertThrows(SQLException.class, () -> {
            connectionManager.executeTransaction(conn -> {
                databaseService.getAmbulanceRepository().updateLocation("AMB-NORTH", "CENTRAL-PLAZA", conn);
                // Deliberately trigger failure (e.g. violate foreign key or throw exception)
                throw new RuntimeException("Forced simulated database hardware/network failure");
            });
        });

        // Verify state was rolled back completely
        Ambulance afterRollback = connectionManager.executeTransactionResult(
                conn -> databaseService.getAmbulanceRepository().findById("AMB-NORTH", conn).orElseThrow());
        assertEquals("NORTH-STATION", afterRollback.getCurrentLocationNodeId(),
                "Ambulance location must remain unchanged after transaction rollback");
    }

    @Test
    @DisplayName("Complete end-to-end mission lifecycle maintains atomic multi-entity consistency")
    void testEndToEndMissionConsistency() throws SQLException {
        CityScenario scenario = CityScenario.createDefaultMetropolis(42L);
        databaseService.saveScenarioSnapshot("METROPOLIS-01", scenario);

        Instant now = Instant.now();
        EmergencyCase emergency = new EmergencyCase("CASE-500", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "CENTRAL-PLAZA", now, "Heart emergency");
        databaseService.getEmergencyRepository().save(emergency, connectionManager.getConnection());

        // 1. Dispatch Assignment
        databaseService.recordDispatchAssignment("CASE-500", "AMB-NORTH", now.plusSeconds(30));

        try (Connection conn = connectionManager.getConnection()) {
            EmergencyCase c1 = databaseService.getEmergencyRepository().findById("CASE-500", conn).orElseThrow();
            Ambulance a1 = databaseService.getAmbulanceRepository().findById("AMB-NORTH", conn).orElseThrow();

            assertEquals(EmergencyStatus.AMBULANCE_EN_ROUTE, c1.getStatus());
            assertEquals("AMB-NORTH", c1.getAssignedAmbulanceId().orElse(null));
            assertEquals(AmbulanceStatus.EN_ROUTE, a1.getStatus());
            assertEquals("CASE-500", a1.getAssignedCaseId().orElse(null));
        }

        // 2. Hospital Reservation
        boolean reserved = databaseService.recordHospitalReservation("HOSP-APEX", "CASE-500", "AMB-NORTH", now.plusSeconds(600));
        assertTrue(reserved);

        try (Connection conn = connectionManager.getConnection()) {
            Hospital h = databaseService.getHospitalRepository().findById("HOSP-APEX", conn).orElseThrow();
            EmergencyCase c2 = databaseService.getEmergencyRepository().findById("CASE-500", conn).orElseThrow();
            Ambulance a2 = databaseService.getAmbulanceRepository().findById("AMB-NORTH", conn).orElseThrow();

            assertEquals(7, h.getAvailableCapacity()); // Initial was 8 in Metropolis
            assertEquals(EmergencyStatus.TRANSPORTING, c2.getStatus());
            assertEquals("HOSP-APEX", c2.getDestinationHospitalId().orElse(null));
            assertEquals(AmbulanceStatus.TRANSPORTING, a2.getStatus());
        }

        // 3. Hospital Delivery and Completion
        databaseService.recordHospitalDeliveryAndCompletion("HOSP-APEX", "CASE-500", "AMB-NORTH", now.plusSeconds(1200));

        try (Connection conn = connectionManager.getConnection()) {
            Hospital h = databaseService.getHospitalRepository().findById("HOSP-APEX", conn).orElseThrow();
            EmergencyCase c3 = databaseService.getEmergencyRepository().findById("CASE-500", conn).orElseThrow();
            Ambulance a3 = databaseService.getAmbulanceRepository().findById("AMB-NORTH", conn).orElseThrow();

            assertEquals(8, h.getAvailableCapacity()); // Capacity restored to 8
            assertEquals(EmergencyStatus.COMPLETED, c3.getStatus());
            assertEquals(AmbulanceStatus.AVAILABLE, a3.getStatus());
            assertTrue(a3.getAssignedCaseId().isEmpty());
        }
    }

    @Test
    @DisplayName("Road incident persistence updates road availability atomically")
    void testRoadIncidentAndClosureConsistency() throws SQLException {
        CityScenario scenario = CityScenario.createDefaultMetropolis(42L);
        databaseService.saveScenarioSnapshot("METROPOLIS-INC", scenario);

        Instant now = Instant.now();
        RoadIncident incident = RoadIncident.roadClosure("INC-TEST-1", "NORTH-STATION", "CENTRAL-PLAZA", now);

        // Record incident
        databaseService.recordRoadIncident(incident);

        try (Connection conn = connectionManager.getConnection()) {
            assertFalse(databaseService.getRoadRepository().isRoadAvailable("NORTH-STATION", "CENTRAL-PLAZA", conn));
            RoadIncident loaded = databaseService.getIncidentRepository().findById("INC-TEST-1", conn).orElseThrow();
            assertEquals("INC-TEST-1", loaded.getId());
        }

        // Resolve incident
        databaseService.resolveRoadIncident("INC-TEST-1", "NORTH-STATION", "CENTRAL-PLAZA", now.plusSeconds(600));

        try (Connection conn = connectionManager.getConnection()) {
            assertTrue(databaseService.getRoadRepository().isRoadAvailable("NORTH-STATION", "CENTRAL-PLAZA", conn));
            assertTrue(databaseService.getActiveIncidents().stream().noneMatch(i -> i.getId().equals("INC-TEST-1")));
        }
    }

    @Test
    @DisplayName("Persistence across application restart preserves full database state")
    void testPersistenceAcrossApplicationRestart() throws Exception {
        tempDbFile = Files.createTempFile("resqroute_restart_test_", ".db");
        DatabaseConfig fileConfig = DatabaseConfig.ofFile(tempDbFile);

        // 1. First run: Initialize schema and write data
        try (DatabaseConnectionManager mgr1 = new DatabaseConnectionManager(fileConfig);
             ResQRouteDatabaseService svc1 = new ResQRouteDatabaseService(mgr1)) {
            mgr1.initialize();

            RoadGraph graph = new RoadGraph();
            graph.addNode(new Node("P1", 10.0, 20.0, "Node 1"));
            graph.addNode(new Node("P2", 10.5, 20.5, "Node 2"));
            graph.addEdge("P1", "P2", 7.5, 9.0, "Expressway");

            Ambulance amb = new Ambulance("AMB-PERSIST", "P1", Set.of(AmbulanceCapability.ADVANCED_LIFE_SUPPORT));

            try (Connection conn = mgr1.getConnection()) {
                svc1.getRoadRepository().saveGraph(graph, conn);
                svc1.getAmbulanceRepository().save(amb, conn);
            }
        } // Closed and shutdown cleanly

        // 2. Second run: Re-open the database file
        try (DatabaseConnectionManager mgr2 = new DatabaseConnectionManager(fileConfig);
             ResQRouteDatabaseService svc2 = new ResQRouteDatabaseService(mgr2)) {
            mgr2.initialize(); // Automatically checks migrations; already applied

            try (Connection conn = mgr2.getConnection()) {
                RoadGraph reloadedGraph = svc2.getRoadRepository().loadGraph(conn);
                assertEquals(2, reloadedGraph.getNodeCount());
                assertEquals(1, reloadedGraph.getEdgeCount());
                assertTrue(reloadedGraph.containsNode("P1"));
                assertTrue(reloadedGraph.containsNode("P2"));

                Ambulance reloadedAmb = svc2.getAmbulanceRepository().findById("AMB-PERSIST", conn).orElseThrow();
                assertEquals("AMB-PERSIST", reloadedAmb.getId());
                assertEquals("P1", reloadedAmb.getCurrentLocationNodeId());
                assertTrue(reloadedAmb.hasCapability(AmbulanceCapability.ADVANCED_LIFE_SUPPORT));
            }
        }
    }
}
