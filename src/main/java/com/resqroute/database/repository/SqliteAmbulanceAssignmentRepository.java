package com.resqroute.database.repository;

import com.resqroute.database.model.AmbulanceAssignmentRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQLite implementation of {@link AmbulanceAssignmentRepository}.
 */
public class SqliteAmbulanceAssignmentRepository implements AmbulanceAssignmentRepository {

    @Override
    public void recordAssignment(String ambulanceId, String caseId, Instant assignedAt, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Instant at = (assignedAt != null) ? assignedAt : Instant.now();
        String sql = """
            INSERT INTO ambulance_assignments (ambulance_id, case_id, assigned_at, status)
            VALUES (?, ?, ?, 'ACTIVE')
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ambulanceId);
            ps.setString(2, caseId);
            ps.setString(3, at.toString());
            ps.executeUpdate();
        }
    }

    @Override
    public void completeAssignment(String ambulanceId, String caseId, Instant completedAt, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Instant at = (completedAt != null) ? completedAt : Instant.now();
        String sql = """
            UPDATE ambulance_assignments
            SET status = 'COMPLETED', completed_at = ?
            WHERE ambulance_id = ? AND case_id = ? AND status = 'ACTIVE'
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, at.toString());
            ps.setString(2, ambulanceId);
            ps.setString(3, caseId);
            ps.executeUpdate();
        }
    }

    @Override
    public void cancelAssignment(String ambulanceId, String caseId, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            UPDATE ambulance_assignments
            SET status = 'CANCELLED', completed_at = ?
            WHERE ambulance_id = ? AND case_id = ? AND status = 'ACTIVE'
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, ambulanceId);
            ps.setString(3, caseId);
            ps.executeUpdate();
        }
    }

    @Override
    public List<AmbulanceAssignmentRecord> findActiveAssignments(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        String sql = """
            SELECT id, ambulance_id, case_id, assigned_at, completed_at, status
            FROM ambulance_assignments
            WHERE status = 'ACTIVE'
            ORDER BY id ASC
            """;
        return queryList(sql, conn);
    }

    @Override
    public List<AmbulanceAssignmentRecord> findByAmbulanceId(String ambulanceId, Connection conn) throws SQLException {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            SELECT id, ambulance_id, case_id, assigned_at, completed_at, status
            FROM ambulance_assignments
            WHERE ambulance_id = ?
            ORDER BY id ASC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ambulanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapResultSet(rs);
            }
        }
    }

    @Override
    public List<AmbulanceAssignmentRecord> findByCaseId(String caseId, Connection conn) throws SQLException {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            SELECT id, ambulance_id, case_id, assigned_at, completed_at, status
            FROM ambulance_assignments
            WHERE case_id = ?
            ORDER BY id ASC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapResultSet(rs);
            }
        }
    }

    private List<AmbulanceAssignmentRecord> queryList(String sql, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapResultSet(rs);
        }
    }

    private List<AmbulanceAssignmentRecord> mapResultSet(ResultSet rs) throws SQLException {
        List<AmbulanceAssignmentRecord> list = new ArrayList<>();
        while (rs.next()) {
            String comp = rs.getString("completed_at");
            list.add(new AmbulanceAssignmentRecord(
                    rs.getLong("id"),
                    rs.getString("ambulance_id"),
                    rs.getString("case_id"),
                    Instant.parse(rs.getString("assigned_at")),
                    comp != null ? Instant.parse(comp) : null,
                    rs.getString("status")
            ));
        }
        return list;
    }
}
