package com.resqroute.database;

import com.resqroute.database.migration.MigrationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 20: Database Schema & Migration Tests")
class DatabaseSchemaAndMigrationTest {

    private DatabaseConnectionManager connectionManager;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = new DatabaseConnectionManager(DatabaseConfig.inMemory());
        connectionManager.initialize();
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("Schema initialization applies V1 migration and records version")
    void testSchemaInitializationAndVersionRecording() throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            MigrationManager migrationManager = connectionManager.getMigrationManager();
            Set<Integer> applied = migrationManager.getAppliedVersions(conn);

            assertTrue(applied.contains(1), "Schema version 1 must be recorded as applied");
            assertEquals(1, migrationManager.getCurrentVersion(conn));

            // Verify foreign keys pragma is strictly enabled
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys must be enabled (1)");
            }
        }
    }

    @Test
    @DisplayName("Migrations are idempotent: subsequent migrate() calls apply 0 new migrations")
    void testMigrationIdempotence() throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            int reapplied = connectionManager.getMigrationManager().migrate(conn);
            assertEquals(0, reapplied, "No new migrations should be applied when already up-to-date");
        }
    }

    @Test
    @DisplayName("All normalized domain tables, indexes, and constraints exist in schema")
    void testAllDomainTablesExist() throws SQLException {
        Set<String> expectedTables = Set.of(
                "schema_version",
                "road_nodes",
                "roads",
                "ambulances",
                "ambulance_capabilities",
                "hospitals",
                "hospital_capabilities",
                "hospital_emergency_types",
                "emergency_cases",
                "emergency_status_history",
                "hospital_reservations",
                "ambulance_assignments",
                "road_incidents",
                "simulation_scenarios",
                "simulation_events"
        );

        Set<String> actualTables = new HashSet<>();
        try (Connection conn = connectionManager.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }

        for (String expected : expectedTables) {
            assertTrue(actualTables.contains(expected), "Database schema missing table: " + expected);
        }
    }
}
