package com.resqroute.database.repository;

import com.resqroute.model.IncidentSeverity;
import com.resqroute.model.IncidentType;
import com.resqroute.model.RoadIncident;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite implementation of {@link RoadIncidentRepository}.
 */
public class SqliteRoadIncidentRepository implements RoadIncidentRepository {

    @Override
    public void save(RoadIncident incident, Connection conn) throws SQLException {
        Objects.requireNonNull(incident, "incident must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO road_incidents (id, incident_type, source_node_id, dest_node_id, severity,
                                        description, blocks_road, created_at, expiry_time, is_resolved)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT(id) DO UPDATE SET
                incident_type = excluded.incident_type,
                source_node_id = excluded.source_node_id,
                dest_node_id = excluded.dest_node_id,
                severity = excluded.severity,
                description = excluded.description,
                blocks_road = excluded.blocks_road,
                created_at = excluded.created_at,
                expiry_time = excluded.expiry_time;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, incident.getId());
            ps.setString(2, incident.getIncidentType().name());
            ps.setString(3, incident.getSourceNodeId());
            ps.setString(4, incident.getDestinationNodeId());
            ps.setString(5, incident.getSeverity().name());
            ps.setString(6, incident.getDescription());
            ps.setInt(7, incident.isBlocksRoad() ? 1 : 0);
            ps.setString(8, incident.getCreatedAt().toString());
            ps.setString(9, incident.getExpiryTime().map(Instant::toString).orElse(null));
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<RoadIncident> findById(String incidentId, Connection conn) throws SQLException {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            SELECT id, incident_type, source_node_id, dest_node_id, severity,
                   description, blocks_road, created_at, expiry_time, is_resolved
            FROM road_incidents WHERE id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapIncident(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<RoadIncident> findActive(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<RoadIncident> list = new ArrayList<>();
        String sql = """
            SELECT id, incident_type, source_node_id, dest_node_id, severity,
                   description, blocks_road, created_at, expiry_time, is_resolved
            FROM road_incidents
            WHERE is_resolved = 0
            ORDER BY created_at DESC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapIncident(rs));
            }
        }
        return list;
    }

    @Override
    public List<RoadIncident> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<RoadIncident> list = new ArrayList<>();
        String sql = """
            SELECT id, incident_type, source_node_id, dest_node_id, severity,
                   description, blocks_road, created_at, expiry_time, is_resolved
            FROM road_incidents
            ORDER BY created_at DESC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapIncident(rs));
            }
        }
        return list;
    }

    @Override
    public void resolve(String incidentId, Instant resolvedAt, Connection conn) throws SQLException {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Instant at = (resolvedAt != null) ? resolvedAt : Instant.now();
        String sql = "UPDATE road_incidents SET is_resolved = 1, resolved_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, at.toString());
            ps.setString(2, incidentId);
            ps.executeUpdate();
        }
    }

    private RoadIncident mapIncident(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        IncidentType type = IncidentType.valueOf(rs.getString("incident_type"));
        String src = rs.getString("source_node_id");
        String dst = rs.getString("dest_node_id");
        IncidentSeverity severity = IncidentSeverity.valueOf(rs.getString("severity"));
        String desc = rs.getString("description");
        boolean blocks = rs.getInt("blocks_road") == 1;
        Instant created = Instant.parse(rs.getString("created_at"));
        String expStr = rs.getString("expiry_time");
        Instant expiry = expStr != null ? Instant.parse(expStr) : null;

        return new RoadIncident(
                id,
                com.resqroute.model.EdgeKey.of(src, dst),
                type,
                severity,
                created,
                expiry,
                desc,
                blocks
        );
    }
}
