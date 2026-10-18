package com.resqroute.database;

import com.resqroute.database.model.HospitalReservationRecord;
import com.resqroute.database.repository.*;
import com.resqroute.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 20: Hospital Capacity & Reservation Persistence Tests")
class HospitalRepositoryAndReservationTest {

    private DatabaseConnectionManager connectionManager;
    private RoadNetworkRepository roadRepository;
    private HospitalRepository hospitalRepository;
    private EmergencyCaseRepository emergencyRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
        roadRepository = new SqliteRoadNetworkRepository();
        hospitalRepository = new SqliteHospitalRepository();
        emergencyRepository = new SqliteEmergencyCaseRepository();

        // Seed node
        try (Connection conn = connectionManager.getConnection()) {
            roadRepository.saveNode(new Node("HOSP-NODE-1", 12.97, 77.61, "Hospital Node 1"), conn);
            roadRepository.saveNode(new Node("HOSP-NODE-2", 12.95, 77.58, "Hospital Node 2"), conn);
        }
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("Hospital persistence with capabilities, emergency types, and eligibility queries")
    void testHospitalCrudAndEligibility() throws SQLException {
        Hospital apex = Hospital.builder()
                .id("HOSP-APEX")
                .name("Apex Trauma & Cardiac Center")
                .locationNodeId("HOSP-NODE-1")
                .supportedEmergencyTypes(Set.of(EmergencyType.CARDIAC, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.CARDIAC_CATH_LAB, HospitalCapability.ICU))
                .totalCapacity(5)
                .availableCapacity(3)
                .build();

        Hospital metro = Hospital.builder()
                .id("HOSP-METRO")
                .name("Metro Community Clinic")
                .locationNodeId("HOSP-NODE-2")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(4)
                .availableCapacity(2)
                .build();

        try (Connection conn = connectionManager.getConnection()) {
            hospitalRepository.save(apex, conn);
            hospitalRepository.save(metro, conn);

            Hospital loadedApex = hospitalRepository.findById("HOSP-APEX", conn).orElseThrow();
            assertEquals("HOSP-APEX", loadedApex.getId());
            assertEquals(5, loadedApex.getTotalCapacity());
            assertEquals(3, loadedApex.getAvailableCapacity());
            assertEquals(HospitalStatus.OPEN, loadedApex.getStatus());
            assertTrue(loadedApex.supportsEmergencyType(EmergencyType.CARDIAC));
            assertTrue(loadedApex.hasCapability(HospitalCapability.CARDIAC_CATH_LAB));

            // Eligible for Cardiac
            List<Hospital> cardiacEligible = hospitalRepository.findEligible(
                    EmergencyType.CARDIAC, Set.of(HospitalCapability.CARDIAC_CATH_LAB), conn);
            assertEquals(1, cardiacEligible.size());
            assertEquals("HOSP-APEX", cardiacEligible.get(0).getId());

            // Eligible for General Medical
            List<Hospital> medicalEligible = hospitalRepository.findEligible(
                    EmergencyType.MEDICAL, Set.of(HospitalCapability.EMERGENCY_ROOM), conn);
            assertEquals(1, medicalEligible.size());
            assertEquals("HOSP-METRO", medicalEligible.get(0).getId());
        }
    }

    @Test
    @DisplayName("Capacity reservation decrements capacity and switches status to BUSY when full")
    void testCapacityReservationAndReleaseLifecycle() throws SQLException {
        Instant now = Instant.now();
        Hospital hosp = Hospital.builder()
                .id("HOSP-SMALL")
                .name("Small Clinic")
                .locationNodeId("HOSP-NODE-1")
                .supportedEmergencyTypes(Set.of(EmergencyType.CARDIAC))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(2)
                .availableCapacity(1)
                .build();

        EmergencyCase case1 = new EmergencyCase("CASE-A", EmergencyType.CARDIAC, EmergencySeverity.HIGH, "HOSP-NODE-1", now, "Emergency A");
        EmergencyCase case2 = new EmergencyCase("CASE-B", EmergencyType.CARDIAC, EmergencySeverity.HIGH, "HOSP-NODE-1", now, "Emergency B");

        try (Connection conn = connectionManager.getConnection()) {
            hospitalRepository.save(hosp, conn);
            emergencyRepository.save(case1, conn);
            emergencyRepository.save(case2, conn);

            // Reserve first bed (1 -> 0, becomes BUSY)
            boolean ok1 = hospitalRepository.reserveCapacity("HOSP-SMALL", "CASE-A", null, now, conn);
            assertTrue(ok1);

            Hospital h1 = hospitalRepository.findById("HOSP-SMALL", conn).orElseThrow();
            assertEquals(0, h1.getAvailableCapacity());
            assertEquals(HospitalStatus.BUSY, h1.getStatus());

            // Try to reserve second bed when full (should fail)
            boolean ok2 = hospitalRepository.reserveCapacity("HOSP-SMALL", "CASE-B", null, now, conn);
            assertFalse(ok2, "Cannot reserve capacity when hospital is BUSY with 0 available capacity");

            // Release reservation for CASE-A (0 -> 1, switches back to OPEN)
            boolean released = hospitalRepository.releaseCapacity("HOSP-SMALL", "CASE-A", now.plusSeconds(300), conn);
            assertTrue(released);

            Hospital h2 = hospitalRepository.findById("HOSP-SMALL", conn).orElseThrow();
            assertEquals(1, h2.getAvailableCapacity());
            assertEquals(HospitalStatus.OPEN, h2.getStatus());

            // Audit records
            List<HospitalReservationRecord> reservations = hospitalRepository.getReservations("HOSP-SMALL", conn);
            assertEquals(1, reservations.size());
            assertEquals("FULFILLED", reservations.get(0).status());
            assertNotNull(reservations.get(0).releasedAt());
        }
    }

    @Test
    @DisplayName("Database check constraint: capacity cannot be negative or exceed total")
    void testCapacityCheckConstraints() throws SQLException {
        try (Connection conn = connectionManager.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            // Negative capacity fails check
            assertThrows(SQLException.class, () -> stmt.execute("""
                INSERT INTO hospitals (id, name, location_node_id, total_capacity, available_capacity, operational_status, last_updated)
                VALUES ('H-INVALID', 'Bad Hosp', 'HOSP-NODE-1', 5, -1, 'OPEN', CURRENT_TIMESTAMP);
                """));

            // Available > total fails check
            assertThrows(SQLException.class, () -> stmt.execute("""
                INSERT INTO hospitals (id, name, location_node_id, total_capacity, available_capacity, operational_status, last_updated)
                VALUES ('H-INVALID2', 'Bad Hosp 2', 'HOSP-NODE-1', 5, 6, 'OPEN', CURRENT_TIMESTAMP);
                """));
        }
    }
}
