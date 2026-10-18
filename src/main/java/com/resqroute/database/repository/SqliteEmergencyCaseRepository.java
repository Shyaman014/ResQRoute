package com.resqroute.database.repository;

import com.resqroute.database.model.EmergencyStatusRecord;
import com.resqroute.model.*;

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
 * SQLite implementation of {@link EmergencyCaseRepository}.
 */
public class SqliteEmergencyCaseRepository implements EmergencyCaseRepository {

    @Override
    public void save(EmergencyCase emergencyCase, Connection conn) throws SQLException {
        Objects.requireNonNull(emergencyCase, "emergencyCase must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO emergency_cases (id, case_type, severity, pickup_node_id, description, status,
                                         reported_at, assigned_ambulance_id, destination_hospital_id, resolved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                case_type = excluded.case_type,
                severity = excluded.severity,
                pickup_node_id = excluded.pickup_node_id,
                description = excluded.description,
                status = excluded.status,
                reported_at = excluded.reported_at,
                assigned_ambulance_id = excluded.assigned_ambulance_id,
                destination_hospital_id = excluded.destination_hospital_id,
                resolved_at = excluded.resolved_at;
            """;

        boolean isClosed = (emergencyCase.getStatus() == EmergencyStatus.COMPLETED || emergencyCase.getStatus() == EmergencyStatus.CANCELLED);
        Instant resolvedAt = isClosed ? Instant.now() : null;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emergencyCase.getId());
            ps.setString(2, emergencyCase.getType().name());
            ps.setString(3, emergencyCase.getSeverity().name());
            ps.setString(4, emergencyCase.getPickupLocationNodeId());
            ps.setString(5, emergencyCase.getDescription());
            ps.setString(6, emergencyCase.getStatus().name());
            ps.setString(7, emergencyCase.getCreatedAt().toString());
            ps.setString(8, emergencyCase.getAssignedAmbulanceId().orElse(null));
            ps.setString(9, emergencyCase.getDestinationHospitalId().orElse(null));
            ps.setString(10, resolvedAt != null ? resolvedAt.toString() : null);
            ps.executeUpdate();
        }

        // Record initial or current status into status history
        recordStatusHistoryEntry(emergencyCase.getId(), emergencyCase.getStatus().name(), "Saved record", emergencyCase.getCreatedAt(), conn);
    }

    @Override
    public Optional<EmergencyCase> findById(String id, Connection conn) throws SQLException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            SELECT id, case_type, severity, pickup_node_id, description, status,
                   reported_at, assigned_ambulance_id, destination_hospital_id
            FROM emergency_cases WHERE id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapEmergencyCase(rs, conn));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<EmergencyCase> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<EmergencyCase> list = new ArrayList<>();
        String sql = """
            SELECT id, case_type, severity, pickup_node_id, description, status,
                   reported_at, assigned_ambulance_id, destination_hospital_id
            FROM emergency_cases ORDER BY reported_at DESC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapEmergencyCase(rs, conn));
            }
        }
        return list;
    }

    @Override
    public List<EmergencyCase> findActive(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<EmergencyCase> list = new ArrayList<>();
        String sql = """
            SELECT id, case_type, severity, pickup_node_id, description, status,
                   reported_at, assigned_ambulance_id, destination_hospital_id
            FROM emergency_cases
            WHERE status NOT IN ('COMPLETED', 'CANCELLED')
            ORDER BY reported_at ASC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapEmergencyCase(rs, conn));
            }
        }
        return list;
    }

    @Override
    public void updateStatus(String caseId, EmergencyStatus status, String reason, Instant recordedAt, Connection conn) throws SQLException {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Instant at = (recordedAt != null) ? recordedAt : Instant.now();
        String resolvedAt = (status == EmergencyStatus.COMPLETED || status == EmergencyStatus.CANCELLED) ? at.toString() : null;

        String sql = "UPDATE emergency_cases SET status = ?, resolved_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, resolvedAt);
            ps.setString(3, caseId);
            ps.executeUpdate();
        }

        recordStatusHistoryEntry(caseId, status.name(), reason, at, conn);
    }

    @Override
    public void assignAmbulance(String caseId, String ambulanceId, Connection conn) throws SQLException {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "UPDATE emergency_cases SET assigned_ambulance_id = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ambulanceId);
            ps.setString(2, caseId);
            ps.executeUpdate();
        }
    }

    @Override
    public void assignHospital(String caseId, String hospitalId, Connection conn) throws SQLException {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "UPDATE emergency_cases SET destination_hospital_id = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospitalId);
            ps.setString(2, caseId);
            ps.executeUpdate();
        }
    }

    @Override
    public List<EmergencyStatusRecord> getStatusHistory(String caseId, Connection conn) throws SQLException {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        List<EmergencyStatusRecord> list = new ArrayList<>();
        String sql = "SELECT id, case_id, status, reason, recorded_at FROM emergency_status_history WHERE case_id = ? ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new EmergencyStatusRecord(
                            rs.getLong("id"),
                            rs.getString("case_id"),
                            rs.getString("status"),
                            rs.getString("reason"),
                            Instant.parse(rs.getString("recorded_at"))
                    ));
                }
            }
        }
        return list;
    }

    private void recordStatusHistoryEntry(String caseId, String status, String reason, Instant recordedAt, Connection conn) throws SQLException {
        String histSql = "INSERT INTO emergency_status_history (case_id, status, reason, recorded_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(histSql)) {
            ps.setString(1, caseId);
            ps.setString(2, status);
            ps.setString(3, reason != null ? reason : "Status update");
            ps.setString(4, recordedAt.toString());
            ps.executeUpdate();
        }
    }

    private EmergencyCase mapEmergencyCase(ResultSet rs, Connection conn) throws SQLException {
        String id = rs.getString("id");
        EmergencyType type = EmergencyType.valueOf(rs.getString("case_type"));
        EmergencySeverity severity = EmergencySeverity.valueOf(rs.getString("severity"));
        String pickupNode = rs.getString("pickup_node_id");
        String desc = rs.getString("description");
        EmergencyStatus status = EmergencyStatus.valueOf(rs.getString("status"));
        Instant reportedAt = Instant.parse(rs.getString("reported_at"));
        String ambId = rs.getString("assigned_ambulance_id");
        String hospId = rs.getString("destination_hospital_id");

        List<EmergencyStatusRecord> rawHistory = getStatusHistory(id, conn);
        List<EmergencyStatusHistoryEntry> historyEntries = new ArrayList<>();
        for (EmergencyStatusRecord rec : rawHistory) {
            historyEntries.add(new EmergencyStatusHistoryEntry(
                    EmergencyStatus.valueOf(rec.status()),
                    rec.recordedAt(),
                    rec.reason()
            ));
        }

        return new EmergencyCase(
                id,
                type,
                severity,
                pickupNode,
                reportedAt,
                status,
                ambId,
                hospId,
                desc,
                historyEntries
        );
    }
}
