package com.resqroute.database.repository;

import com.resqroute.model.Ambulance;
import com.resqroute.model.AmbulanceCapability;
import com.resqroute.model.AmbulanceStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * SQLite implementation of {@link AmbulanceRepository}.
 */
public class SqliteAmbulanceRepository implements AmbulanceRepository {

    @Override
    public void save(Ambulance ambulance, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulance, "ambulance must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO ambulances (id, current_location_node_id, status, assigned_case_id, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(id) DO UPDATE SET
                current_location_node_id = excluded.current_location_node_id,
                status = excluded.status,
                assigned_case_id = excluded.assigned_case_id,
                updated_at = CURRENT_TIMESTAMP;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ambulance.getId());
            ps.setString(2, ambulance.getCurrentLocationNodeId());
            ps.setString(3, ambulance.getStatus().name());
            ps.setString(4, ambulance.getAssignedCaseId().orElse(null));
            ps.executeUpdate();
        }

        // Save capabilities
        try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM ambulance_capabilities WHERE ambulance_id = ?")) {
            delPs.setString(1, ambulance.getId());
            delPs.executeUpdate();
        }

        String capSql = "INSERT INTO ambulance_capabilities (ambulance_id, capability) VALUES (?, ?)";
        try (PreparedStatement capPs = conn.prepareStatement(capSql)) {
            for (AmbulanceCapability cap : ambulance.getCapabilities()) {
                capPs.setString(1, ambulance.getId());
                capPs.setString(2, cap.name());
                capPs.addBatch();
            }
            capPs.executeBatch();
        }
    }

    @Override
    public Optional<Ambulance> findById(String id, Connection conn) throws SQLException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "SELECT id, current_location_node_id, status, assigned_case_id FROM ambulances WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAmbulance(rs, conn));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Ambulance> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<Ambulance> list = new ArrayList<>();
        String sql = "SELECT id, current_location_node_id, status, assigned_case_id FROM ambulances ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapAmbulance(rs, conn));
            }
        }
        return list;
    }

    @Override
    public List<Ambulance> findByStatus(AmbulanceStatus status, Connection conn) throws SQLException {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        List<Ambulance> list = new ArrayList<>();
        String sql = "SELECT id, current_location_node_id, status, assigned_case_id FROM ambulances WHERE status = ? ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapAmbulance(rs, conn));
                }
            }
        }
        return list;
    }

    @Override
    public void updateStatus(String ambulanceId, AmbulanceStatus status, String assignedCaseId, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "UPDATE ambulances SET status = ?, assigned_case_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, assignedCaseId);
            ps.setString(3, ambulanceId);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateLocation(String ambulanceId, String locationNodeId, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(locationNodeId, "locationNodeId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "UPDATE ambulances SET current_location_node_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, locationNodeId);
            ps.setString(2, ambulanceId);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(String ambulanceId, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "DELETE FROM ambulances WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ambulanceId);
            ps.executeUpdate();
        }
    }

    private Ambulance mapAmbulance(ResultSet rs, Connection conn) throws SQLException {
        String id = rs.getString("id");
        String locationNode = rs.getString("current_location_node_id");
        AmbulanceStatus status = AmbulanceStatus.valueOf(rs.getString("status"));
        String assignedCase = rs.getString("assigned_case_id");

        Set<AmbulanceCapability> caps = loadCapabilities(id, conn);
        return new Ambulance(id, locationNode, status, assignedCase, caps);
    }

    private Set<AmbulanceCapability> loadCapabilities(String ambulanceId, Connection conn) throws SQLException {
        Set<AmbulanceCapability> set = EnumSet.noneOf(AmbulanceCapability.class);
        String sql = "SELECT capability FROM ambulance_capabilities WHERE ambulance_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ambulanceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(AmbulanceCapability.valueOf(rs.getString("capability")));
                }
            }
        }
        return set;
    }
}
