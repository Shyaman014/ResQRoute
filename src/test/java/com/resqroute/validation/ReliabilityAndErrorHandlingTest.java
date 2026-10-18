package com.resqroute.validation;

import com.resqroute.database.DatabaseConfig;
import com.resqroute.database.DatabaseConnectionManager;
import com.resqroute.database.repository.HospitalRepository;
import com.resqroute.database.repository.RoadNetworkRepository;
import com.resqroute.database.repository.SqliteHospitalRepository;
import com.resqroute.database.repository.SqliteRoadNetworkRepository;
import com.resqroute.database.service.ResQRouteDatabaseService;
import com.resqroute.exception.AmbulanceUnavailableException;
import com.resqroute.exception.CorruptedDataException;
import com.resqroute.exception.InvalidRouteException;
import com.resqroute.exception.PersistenceException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.service.AmbulanceFleetService;
import com.resqroute.service.EmergencyManagementService;
import com.resqroute.ui.model.UiMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 21: Reliability, Centralized Error Handling & State Consistency Tests")
class ReliabilityAndErrorHandlingTest {

    private DatabaseConnectionManager connectionManager;
    private ResQRouteDatabaseService databaseService;
    private RoadNetworkRepository roadRepository;
    private HospitalRepository hospitalRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
        roadRepository = new SqliteRoadNetworkRepository();
        hospitalRepository = new SqliteHospitalRepository();
        databaseService = new ResQRouteDatabaseService(connectionManager);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    // ==========================================
    // 1. UI Message Sanitization Tests
    // ==========================================

    @Test
    @DisplayName("UiMessage.fromException sanitizes domain exceptions into human-readable messages")
    void testUiMessageSanitizesDomainExceptions() {
        InvalidRouteException domainEx = new InvalidRouteException("NODE_A", "NODE_A", "Endpoints cannot be identical");
        UiMessage msg1 = UiMessage.fromException(domainEx);
        assertEquals(UiMessage.MessageType.ERROR, msg1.type());
        assertTrue(msg1.text().contains("Endpoints cannot be identical"));

        // Wrapped in generic runtime exception
        RuntimeException wrapped = new RuntimeException("Outer failure", domainEx);
        UiMessage msg2 = UiMessage.fromException(wrapped);
        assertEquals(UiMessage.MessageType.ERROR, msg2.type());
        assertTrue(msg2.text().contains("Endpoints cannot be identical"));
    }

    @Test
    @DisplayName("UiMessage.fromException sanitizes SQL constraint errors and prevents DB leaking")
    void testUiMessageSanitizesSqlExceptions() {
        SQLException fkEx = new SQLException("[SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed");
        UiMessage fkMsg = UiMessage.fromException(fkEx);
        assertEquals("Database operation failed: Referenced network entity does not exist.", fkMsg.text());

        SQLException uniqueEx = new SQLException("[SQLITE_CONSTRAINT_UNIQUE] UNIQUE constraint failed: road_nodes.id");
        UiMessage uniqueMsg = UiMessage.fromException(uniqueEx);
        assertEquals("Database operation failed: A record with this identifier already exists.", uniqueMsg.text());

        SQLException checkEx = new SQLException("[SQLITE_CONSTRAINT_CHECK] CHECK constraint failed");
        UiMessage checkMsg = UiMessage.fromException(checkEx);
        assertEquals("Database operation failed: Data values violated domain validity constraints.", checkMsg.text());

        SQLException genericSql = new SQLException("disk I/O error 500");
        UiMessage genericSqlMsg = UiMessage.fromException(genericSql);
        assertEquals("Database operation failed. Please check inputs and retry.", genericSqlMsg.text());
    }

    @Test
    @DisplayName("UiMessage.fromException suppresses stack traces and raw class names")
    void testUiMessageSuppressesRawStackTraces() {
        Exception rawEx = new NullPointerException("NullPointer at com.resqroute.database.service.Foo.bar(Foo.java:42)");
        UiMessage msg = UiMessage.fromException(rawEx);
        assertEquals("An unexpected system error occurred. Please verify your inputs and try again.", msg.text());

        UiMessage nullMsg = UiMessage.fromException(null);
        assertEquals("An unknown error occurred.", nullMsg.text());
    }

    // ==========================================
    // 2. Database Integrity Verification
    // ==========================================

    @Test
    @DisplayName("ResQRouteDatabaseService confirms database integrity on clean schema")
    void testDatabaseIntegrityVerification() throws SQLException {
        assertTrue(databaseService.validateDatabaseIntegrity());
    }

    // ==========================================
    // 3. Corrupted Data Detection in Persistence Layer
    // ==========================================

    @Test
    @DisplayName("SqliteRoadNetworkRepository throws CorruptedDataException on illegal coordinates")
    void testCorruptedNodeCoordinatesThrowsException() throws SQLException {
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA foreign_keys = OFF;");
            stmt.executeUpdate("DROP TABLE IF EXISTS roads;");
            stmt.executeUpdate("DROP TABLE IF EXISTS road_nodes;");
            stmt.executeUpdate("CREATE TABLE road_nodes (id TEXT PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL);");
            stmt.executeUpdate("INSERT INTO road_nodes (id, name, latitude, longitude) VALUES ('CORRUPT_N1', 'Corrupt Node', 999.0, 77.0);");
        }

        try (Connection conn = connectionManager.getConnection()) {
            assertThrows(CorruptedDataException.class, () -> roadRepository.findAllNodes(conn));
        }

        SQLException sqlEx = assertThrows(SQLException.class, () -> databaseService.loadRoadGraph());
        assertInstanceOf(CorruptedDataException.class, sqlEx.getCause());
    }

    @Test
    @DisplayName("SqliteHospitalRepository throws CorruptedDataException on capacity invariants violation")
    void testCorruptedHospitalCapacityThrowsException() throws SQLException {
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA foreign_keys = OFF;");
            stmt.executeUpdate("DROP TABLE IF EXISTS hospital_emergency_types;");
            stmt.executeUpdate("DROP TABLE IF EXISTS hospital_capabilities;");
            stmt.executeUpdate("DROP TABLE IF EXISTS hospitals;");
            stmt.executeUpdate("""
                CREATE TABLE hospitals (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    location_node_id TEXT NOT NULL,
                    total_capacity INTEGER NOT NULL,
                    available_capacity INTEGER NOT NULL,
                    operational_status TEXT NOT NULL,
                    last_updated TIMESTAMP NOT NULL
                );
            """);
            stmt.executeUpdate("""
                INSERT INTO hospitals (id, name, location_node_id, total_capacity, available_capacity, operational_status, last_updated)
                VALUES ('H_CORRUPT', 'Corrupt Hosp', 'HN1', 10, 50, 'OPEN', '2026-10-19T00:00:00Z');
            """);
        }

        try (Connection conn = connectionManager.getConnection()) {
            assertThrows(CorruptedDataException.class, () -> hospitalRepository.findById("H_CORRUPT", conn));
        }
    }

    // ==========================================
    // 4. State Consistency & Atomic Dispatch
    // ==========================================

    @Test
    @DisplayName("EmergencyManagementService preserves previous assignment when new allocation fails")
    void testAssignmentAtomicityOnFailedDispatch() {
        RoadGraph graph = new RoadGraph();
        Node n1 = new Node("N1", 12.0, 77.0, "Node 1");
        Node n2 = new Node("N2", 12.1, 77.1, "Node 2");
        graph.addNode(n1);
        graph.addNode(n2);
        graph.addEdge("N1", "N2", 5.0, 10.0, "Main St");

        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        EmergencyManagementService emergencyService = new EmergencyManagementService(graph, fleetService);

        Ambulance amb1 = fleetService.registerAmbulance("AMB-1", "N1");
        Ambulance amb2 = fleetService.registerAmbulance("AMB-2", "N2");
        fleetService.updateAmbulanceStatus("AMB-2", AmbulanceStatus.OUT_OF_SERVICE);

        EmergencyCase emergency = new EmergencyCase("CASE-100", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "N1", Instant.now(), "Chest pain");
        emergencyService.createCase(emergency);

        // Initial assignment succeeds
        emergencyService.assignAmbulance("CASE-100", "AMB-1");
        EmergencyCase afterFirst = emergencyService.getCase("CASE-100").orElseThrow();
        assertEquals("AMB-1", afterFirst.getAssignedAmbulanceId().orElseThrow());
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulance("AMB-1").orElseThrow().getStatus());

        // Attempt second assignment to unavailable ambulance (AMB-2 is OUT_OF_SERVICE)
        assertThrows(AmbulanceUnavailableException.class, () -> emergencyService.assignAmbulance("CASE-100", "AMB-2"));

        // Verify previous assignment was preserved intact and NOT released
        EmergencyCase afterFailed = emergencyService.getCase("CASE-100").orElseThrow();
        assertEquals("AMB-1", afterFailed.getAssignedAmbulanceId().orElseThrow());
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulance("AMB-1").orElseThrow().getStatus());
    }

    // ==========================================
    // 5. Asynchronous Persistence Exception Wrapping
    // ==========================================

    @Test
    @DisplayName("ResQRouteDatabaseService wraps async database errors in PersistenceException")
    void testAsyncDatabaseExceptionWrapping() {
        var future = databaseService.supplyAsync(() -> {
            throw new SQLException("Simulated async failure");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(PersistenceException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Simulated async failure"));
    }
}
