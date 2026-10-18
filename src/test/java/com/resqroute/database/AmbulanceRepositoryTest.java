package com.resqroute.database;

import com.resqroute.database.repository.AmbulanceRepository;
import com.resqroute.database.repository.RoadNetworkRepository;
import com.resqroute.database.repository.SqliteAmbulanceRepository;
import com.resqroute.database.repository.SqliteRoadNetworkRepository;
import com.resqroute.model.Ambulance;
import com.resqroute.model.AmbulanceCapability;
import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 20: Ambulance Persistence & Constraints Tests")
class AmbulanceRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private RoadNetworkRepository roadRepository;
    private AmbulanceRepository ambulanceRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
        roadRepository = new SqliteRoadNetworkRepository();
        ambulanceRepository = new SqliteAmbulanceRepository();

        // Seed base station nodes
        try (Connection conn = connectionManager.getConnection()) {
            roadRepository.saveNode(new Node("STATION-NORTH", 12.98, 77.59, "North Station"), conn);
            roadRepository.saveNode(new Node("STATION-SOUTH", 12.94, 77.59, "South Station"), conn);
        }
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("CRUD operations for ambulance entity including multi-capability persistence")
    void testAmbulanceCrudOperations() throws SQLException {
        Ambulance amb1 = new Ambulance("AMB-01", "STATION-NORTH",
                Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT, AmbulanceCapability.CARDIAC_CARE));

        try (Connection conn = connectionManager.getConnection()) {
            ambulanceRepository.save(amb1, conn);

            Ambulance loaded = ambulanceRepository.findById("AMB-01", conn).orElseThrow();
            assertEquals("AMB-01", loaded.getId());
            assertEquals("STATION-NORTH", loaded.getCurrentLocationNodeId());
            assertEquals(AmbulanceStatus.AVAILABLE, loaded.getStatus());
            assertTrue(loaded.hasCapability(AmbulanceCapability.BASIC_LIFE_SUPPORT));
            assertTrue(loaded.hasCapability(AmbulanceCapability.CARDIAC_CARE));
            assertFalse(loaded.hasCapability(AmbulanceCapability.HEAVY_RESCUE));

            // Update status and location
            ambulanceRepository.updateLocation("AMB-01", "STATION-SOUTH", conn);
            ambulanceRepository.updateStatus("AMB-01", AmbulanceStatus.ON_SCENE, "CASE-99", conn);

            Ambulance updated = ambulanceRepository.findById("AMB-01", conn).orElseThrow();
            assertEquals("STATION-SOUTH", updated.getCurrentLocationNodeId());
            assertEquals(AmbulanceStatus.ON_SCENE, updated.getStatus());
            assertEquals("CASE-99", updated.getAssignedCaseId().orElse(null));

            // Delete
            ambulanceRepository.delete("AMB-01", conn);
            assertTrue(ambulanceRepository.findById("AMB-01", conn).isEmpty());
        }
    }

    @Test
    @DisplayName("Query available ambulances filters out busy or responding units")
    void testQueryAvailableAmbulances() throws SQLException {
        Ambulance a1 = new Ambulance("AMB-A", "STATION-NORTH", AmbulanceStatus.AVAILABLE, null);
        Ambulance a2 = new Ambulance("AMB-B", "STATION-SOUTH", AmbulanceStatus.EN_ROUTE, "CASE-01");
        Ambulance a3 = new Ambulance("AMB-C", "STATION-NORTH", AmbulanceStatus.AVAILABLE, null);

        try (Connection conn = connectionManager.getConnection()) {
            ambulanceRepository.save(a1, conn);
            ambulanceRepository.save(a2, conn);
            ambulanceRepository.save(a3, conn);

            List<Ambulance> available = ambulanceRepository.findByStatus(AmbulanceStatus.AVAILABLE, conn);
            assertEquals(2, available.size());
            assertTrue(available.stream().anyMatch(a -> a.getId().equals("AMB-A")));
            assertTrue(available.stream().anyMatch(a -> a.getId().equals("AMB-C")));
            assertFalse(available.stream().anyMatch(a -> a.getId().equals("AMB-B")));
        }
    }

    @Test
    @DisplayName("Foreign-key constraint: saving ambulance at non-existent location throws SQLException")
    void testAmbulanceForeignKeyConstraint() throws SQLException {
        Ambulance invalidAmb = new Ambulance("AMB-INVALID", "NON-EXISTENT-NODE");
        try (Connection conn = connectionManager.getConnection()) {
            assertThrows(SQLException.class, () -> ambulanceRepository.save(invalidAmb, conn));
        }
    }
}
