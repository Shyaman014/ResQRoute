package com.resqroute.database.repository;

import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.event.SimulationEventType;
import com.resqroute.simulation.model.CityScenario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQLite implementation of {@link SimulationScenarioRepository}.
 */
public class SqliteSimulationScenarioRepository implements SimulationScenarioRepository {

    @Override
    public void saveScenario(String scenarioId, CityScenario scenario, Connection conn) throws SQLException {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO simulation_scenarios (id, name, duration_seconds, time_step_seconds, random_seed, start_time)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                duration_seconds = excluded.duration_seconds,
                time_step_seconds = excluded.time_step_seconds,
                random_seed = excluded.random_seed,
                start_time = excluded.start_time;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scenarioId);
            ps.setString(2, scenario.getName());
            ps.setLong(3, scenario.getSimulationDuration().toSeconds());
            ps.setLong(4, scenario.getTimeStep().toSeconds());
            ps.setLong(5, scenario.getRandomSeed());
            ps.setString(6, scenario.getBaseTime().toString());
            ps.executeUpdate();
        }
    }

    @Override
    public void saveSimulationEvent(String scenarioId, SimulationEvent event, Connection conn) throws SQLException {
        saveSimulationEvents(scenarioId, List.of(event), conn);
    }

    @Override
    public void saveSimulationEvents(String scenarioId, List<SimulationEvent> events, Connection conn) throws SQLException {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO simulation_events (scenario_id, sequence_number, event_type, entity_id,
                                           description, sim_time_offset_seconds, real_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SimulationEvent event : events) {
                ps.setString(1, scenarioId);
                ps.setLong(2, event.sequenceId());
                ps.setString(3, event.type().name());
                ps.setString(4, event.entityId());
                ps.setString(5, event.message());
                ps.setLong(6, event.simulationTime().toSeconds());
                ps.setString(7, event.timestamp().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<SimulationEvent> getSimulationEvents(String scenarioId, Connection conn) throws SQLException {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        List<SimulationEvent> list = new ArrayList<>();
        String sql = """
            SELECT sequence_number, event_type, entity_id, description, sim_time_offset_seconds, real_timestamp
            FROM simulation_events
            WHERE scenario_id = ?
            ORDER BY sequence_number ASC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scenarioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SimulationEvent(
                            rs.getLong("sequence_number"),
                            Instant.parse(rs.getString("real_timestamp")),
                            Duration.ofSeconds(rs.getLong("sim_time_offset_seconds")),
                            SimulationEventType.valueOf(rs.getString("event_type")),
                            rs.getString("entity_id"),
                            rs.getString("description")
                    ));
                }
            }
        }
        return list;
    }
}
