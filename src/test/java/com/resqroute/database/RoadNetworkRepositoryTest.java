package com.resqroute.database;

import com.resqroute.database.repository.RoadNetworkRepository;
import com.resqroute.database.repository.SqliteRoadNetworkRepository;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 20: Road Network Persistence Tests")
class RoadNetworkRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private RoadNetworkRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
        repository = new SqliteRoadNetworkRepository();
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("Save and reload complete road graph topology with nodes, edges, and availability")
    void testSaveAndLoadRoadGraph() throws SQLException {
        RoadGraph original = new RoadGraph();
        original.addNode(new Node("N1", 12.91, 77.51, "Node 1"));
        original.addNode(new Node("N2", 12.92, 77.52, "Node 2"));
        original.addNode(new Node("N3", 12.93, 77.53, "Node 3"));

        original.addBidirectionalEdge("N1", "N2", 5.0, 6.0, "Route A");
        original.addEdge("N2", "N3", 3.0, 4.0, "Route B");
        original.setRoadAvailability("N1", "N2", false); // Close forward edge

        try (Connection conn = connectionManager.getConnection()) {
            repository.saveGraph(original, conn);
        }

        // Reload graph
        RoadGraph loaded;
        try (Connection conn = connectionManager.getConnection()) {
            loaded = repository.loadGraph(conn);
        }

        assertEquals(3, loaded.getNodeCount());
        assertEquals(3, loaded.getEdgeCount()); // 2 for bidirectional + 1 directed

        assertTrue(loaded.containsNode("N1"));
        assertTrue(loaded.containsNode("N2"));
        assertTrue(loaded.containsNode("N3"));

        // Check availability was preserved
        assertFalse(loaded.isRoadAvailable("N1", "N2"));
        assertTrue(loaded.isRoadAvailable("N2", "N1"));
        assertTrue(loaded.isRoadAvailable("N2", "N3"));
    }

    @Test
    @DisplayName("Foreign-key constraint: inserting road with non-existent node throws SQLException")
    void testRoadForeignKeyConstraint() throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            // Node NON-EXISTENT does not exist in road_nodes
            assertThrows(SQLException.class, () ->
                    repository.saveRoad("NON-EXISTENT", "N2", 10.0, 12.0, "Ghost Road", true, conn));
        }
    }

    @Test
    @DisplayName("Update road availability updates status flag in database")
    void testUpdateRoadAvailability() throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            repository.saveNode(new Node("A", 10.0, 20.0, "Node A"), conn);
            repository.saveNode(new Node("B", 10.1, 20.1, "Node B"), conn);
            repository.saveRoad("A", "B", 4.0, 5.0, "Connector", true, conn);

            assertTrue(repository.isRoadAvailable("A", "B", conn));

            repository.updateRoadAvailability("A", "B", false, conn);
            assertFalse(repository.isRoadAvailable("A", "B", conn));

            repository.updateRoadAvailability("A", "B", true, conn);
            assertTrue(repository.isRoadAvailable("A", "B", conn));
        }
    }
}
