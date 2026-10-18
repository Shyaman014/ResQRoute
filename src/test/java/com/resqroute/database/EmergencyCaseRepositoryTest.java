package com.resqroute.database;

import com.resqroute.database.model.EmergencyStatusRecord;
import com.resqroute.database.repository.EmergencyCaseRepository;
import com.resqroute.database.repository.RoadNetworkRepository;
import com.resqroute.database.repository.SqliteEmergencyCaseRepository;
import com.resqroute.database.repository.SqliteRoadNetworkRepository;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.model.EmergencyType;
import com.resqroute.model.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 20: Emergency Case Persistence & Audit Trail Tests")
class EmergencyCaseRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private RoadNetworkRepository roadRepository;
    private EmergencyCaseRepository emergencyRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
        roadRepository = new SqliteRoadNetworkRepository();
        emergencyRepository = new SqliteEmergencyCaseRepository();

        // Seed node
        try (Connection conn = connectionManager.getConnection()) {
            roadRepository.saveNode(new Node("CENTRAL-PLAZA", 12.96, 77.60, "Central Plaza"), conn);
        }
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("Save and retrieve emergency case with chronological status history trail")
    void testSaveAndRetrieveEmergencyWithStatusHistory() throws SQLException {
        Instant now = Instant.parse("2026-10-18T08:00:00Z");
        EmergencyCase emergency = new EmergencyCase(
                "CASE-101",
                EmergencyType.CARDIAC,
                EmergencySeverity.CRITICAL,
                "CENTRAL-PLAZA",
                now,
                "Elderly patient with chest pain"
        );

        try (Connection conn = connectionManager.getConnection()) {
            emergencyRepository.save(emergency, conn);

            // Verify retrieval
            EmergencyCase loaded = emergencyRepository.findById("CASE-101", conn).orElseThrow();
            assertEquals("CASE-101", loaded.getId());
            assertEquals(EmergencyType.CARDIAC, loaded.getType());
            assertEquals(EmergencySeverity.CRITICAL, loaded.getSeverity());
            assertEquals("CENTRAL-PLAZA", loaded.getPickupLocationNodeId());
            assertEquals(EmergencyStatus.REPORTED, loaded.getStatus());

            // Transition status through lifecycle
            Instant t1 = now.plusSeconds(60);
            emergencyRepository.updateStatus("CASE-101", EmergencyStatus.AMBULANCE_EN_ROUTE, "Dispatched AMB-01", t1, conn);

            Instant t2 = now.plusSeconds(300);
            emergencyRepository.updateStatus("CASE-101", EmergencyStatus.PATIENT_PICKED_UP, "Patient secured on scene", t2, conn);

            Instant t3 = now.plusSeconds(900);
            emergencyRepository.updateStatus("CASE-101", EmergencyStatus.COMPLETED, "Delivered to emergency trauma ward", t3, conn);

            // Audit history
            List<EmergencyStatusRecord> history = emergencyRepository.getStatusHistory("CASE-101", conn);
            assertTrue(history.size() >= 4, "Status history must capture all lifecycle transitions");

            assertEquals("REPORTED", history.get(0).status());
            assertEquals("AMBULANCE_EN_ROUTE", history.get(1).status());
            assertEquals("PATIENT_PICKED_UP", history.get(2).status());
            assertEquals("COMPLETED", history.get(3).status());
        }
    }

    @Test
    @DisplayName("Query active emergencies excludes COMPLETED and CANCELLED cases")
    void testQueryActiveEmergencies() throws SQLException {
        Instant now = Instant.now();
        EmergencyCase active1 = new EmergencyCase("C-1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "CENTRAL-PLAZA", now, "Accident");
        EmergencyCase active2 = new EmergencyCase("C-2", EmergencyType.FIRE_RESCUE, EmergencySeverity.MEDIUM, "CENTRAL-PLAZA", now, "Fire rescue");
        EmergencyCase done = new EmergencyCase("C-3", EmergencyType.MEDICAL, EmergencySeverity.LOW, "CENTRAL-PLAZA", now, "Fever");

        try (Connection conn = connectionManager.getConnection()) {
            emergencyRepository.save(active1, conn);
            emergencyRepository.save(active2, conn);
            emergencyRepository.save(done, conn);

            emergencyRepository.updateStatus("C-3", EmergencyStatus.COMPLETED, "Done", now, conn);

            List<EmergencyCase> activeList = emergencyRepository.findActive(conn);
            assertEquals(2, activeList.size());
            assertTrue(activeList.stream().anyMatch(c -> c.getId().equals("C-1")));
            assertTrue(activeList.stream().anyMatch(c -> c.getId().equals("C-2")));
            assertFalse(activeList.stream().anyMatch(c -> c.getId().equals("C-3")));
        }
    }

    @Test
    @DisplayName("Foreign-key constraint: creating case at non-existent pickup node throws SQLException")
    void testEmergencyForeignKeyConstraint() throws SQLException {
        Instant now = Instant.now();
        EmergencyCase invalid = new EmergencyCase("C-INVALID", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "NON-EXISTENT-WAYPOINT", now, "Crash");

        try (Connection conn = connectionManager.getConnection()) {
            assertThrows(SQLException.class, () -> emergencyRepository.save(invalid, conn));
        }
    }
}
