package com.resqroute.database.repository;

import com.resqroute.database.model.HospitalReservationRecord;
import com.resqroute.model.EmergencyType;
import com.resqroute.model.Hospital;
import com.resqroute.model.HospitalCapability;
import com.resqroute.model.HospitalStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * SQLite implementation of {@link HospitalRepository}.
 */
public class SqliteHospitalRepository implements HospitalRepository {

    @Override
    public void save(Hospital hospital, Connection conn) throws SQLException {
        Objects.requireNonNull(hospital, "hospital must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO hospitals (id, name, location_node_id, total_capacity, available_capacity, operational_status, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                location_node_id = excluded.location_node_id,
                total_capacity = excluded.total_capacity,
                available_capacity = excluded.available_capacity,
                operational_status = excluded.operational_status,
                last_updated = excluded.last_updated;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospital.getId());
            ps.setString(2, hospital.getName());
            ps.setString(3, hospital.getLocationNodeId());
            ps.setInt(4, hospital.getTotalCapacity());
            ps.setInt(5, hospital.getAvailableCapacity());
            ps.setString(6, hospital.getStatus().name());
            ps.setString(7, hospital.getLastUpdatedAt().toString());
            ps.executeUpdate();
        }

        // Save capabilities
        try (PreparedStatement delCap = conn.prepareStatement("DELETE FROM hospital_capabilities WHERE hospital_id = ?")) {
            delCap.setString(1, hospital.getId());
            delCap.executeUpdate();
        }
        String insCap = "INSERT INTO hospital_capabilities (hospital_id, capability) VALUES (?, ?)";
        try (PreparedStatement psCap = conn.prepareStatement(insCap)) {
            for (HospitalCapability cap : hospital.getCapabilities()) {
                psCap.setString(1, hospital.getId());
                psCap.setString(2, cap.name());
                psCap.addBatch();
            }
            psCap.executeBatch();
        }

        // Save supported emergency types
        try (PreparedStatement delTypes = conn.prepareStatement("DELETE FROM hospital_emergency_types WHERE hospital_id = ?")) {
            delTypes.setString(1, hospital.getId());
            delTypes.executeUpdate();
        }
        String insTypes = "INSERT INTO hospital_emergency_types (hospital_id, emergency_type) VALUES (?, ?)";
        try (PreparedStatement psType = conn.prepareStatement(insTypes)) {
            for (EmergencyType type : hospital.getSupportedEmergencyTypes()) {
                psType.setString(1, hospital.getId());
                psType.setString(2, type.name());
                psType.addBatch();
            }
            psType.executeBatch();
        }
    }

    @Override
    public Optional<Hospital> findById(String id, Connection conn) throws SQLException {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "SELECT id, name, location_node_id, total_capacity, available_capacity, operational_status, last_updated FROM hospitals WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapHospital(rs, conn));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Hospital> findAll(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<Hospital> list = new ArrayList<>();
        String sql = "SELECT id, name, location_node_id, total_capacity, available_capacity, operational_status, last_updated FROM hospitals ORDER BY name ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapHospital(rs, conn));
            }
        }
        return list;
    }

    @Override
    public List<Hospital> findEligible(EmergencyType emergencyType, Set<HospitalCapability> requiredCapabilities, Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<Hospital> all = findAll(conn);
        List<Hospital> eligible = new ArrayList<>();
        for (Hospital h : all) {
            if (h.getStatus() != HospitalStatus.CLOSED && h.getAvailableCapacity() > 0) {
                if (emergencyType == null || h.supportsEmergencyType(emergencyType)) {
                    if (requiredCapabilities == null || h.hasAllCapabilities(requiredCapabilities)) {
                        eligible.add(h);
                    }
                }
            }
        }
        return eligible;
    }

    @Override
    public void updateCapacity(String hospitalId, int availableCapacity, Connection conn) throws SQLException {
        Objects.requireNonNull(hospitalId, "hospitalId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Hospital existing = findById(hospitalId, conn).orElseThrow(
                () -> new SQLException("Hospital not found: " + hospitalId));

        Hospital updated = existing.withAvailableCapacity(availableCapacity, Instant.now());
        save(updated, conn);
    }

    @Override
    public void updateStatus(String hospitalId, HospitalStatus status, Connection conn) throws SQLException {
        Objects.requireNonNull(hospitalId, "hospitalId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Hospital existing = findById(hospitalId, conn).orElseThrow(
                () -> new SQLException("Hospital not found: " + hospitalId));

        Hospital updated = existing.withStatus(status, Instant.now());
        save(updated, conn);
    }

    @Override
    public boolean reserveCapacity(String hospitalId, String caseId, String ambulanceId, Instant reservedAt, Connection conn) throws SQLException {
        Objects.requireNonNull(hospitalId, "hospitalId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        Hospital hospital = findById(hospitalId, conn).orElse(null);
        if (hospital == null || hospital.getStatus() == HospitalStatus.CLOSED || hospital.getAvailableCapacity() <= 0) {
            return false;
        }

        Instant at = (reservedAt != null) ? reservedAt : Instant.now();
        int newCap = hospital.getAvailableCapacity() - 1;
        Hospital updated = hospital.withAvailableCapacity(newCap, at);
        save(updated, conn);

        String sql = """
            INSERT INTO hospital_reservations (hospital_id, case_id, ambulance_id, reserved_at, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospitalId);
            ps.setString(2, caseId);
            ps.setString(3, ambulanceId);
            ps.setString(4, at.toString());
            ps.executeUpdate();
        }

        return true;
    }

    @Override
    public boolean releaseCapacity(String hospitalId, String caseId, Instant releasedAt, Connection conn) throws SQLException {
        Objects.requireNonNull(hospitalId, "hospitalId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String updateResSql = """
            UPDATE hospital_reservations
            SET status = 'FULFILLED', released_at = ?
            WHERE hospital_id = ? AND case_id = ? AND status = 'ACTIVE'
            """;

        Instant at = (releasedAt != null) ? releasedAt : Instant.now();
        int rowsUpdated;
        try (PreparedStatement ps = conn.prepareStatement(updateResSql)) {
            ps.setString(1, at.toString());
            ps.setString(2, hospitalId);
            ps.setString(3, caseId);
            rowsUpdated = ps.executeUpdate();
        }

        if (rowsUpdated > 0) {
            Hospital hospital = findById(hospitalId, conn).orElse(null);
            if (hospital != null) {
                int newCap = Math.min(hospital.getTotalCapacity(), hospital.getAvailableCapacity() + 1);
                Hospital updated = hospital.withAvailableCapacity(newCap, at);
                save(updated, conn);
            }
            return true;
        }
        return false;
    }

    @Override
    public List<HospitalReservationRecord> getReservations(String hospitalId, Connection conn) throws SQLException {
        Objects.requireNonNull(hospitalId, "hospitalId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        List<HospitalReservationRecord> list = new ArrayList<>();
        String sql = """
            SELECT id, hospital_id, case_id, ambulance_id, reserved_at, released_at, status
            FROM hospital_reservations
            WHERE hospital_id = ?
            ORDER BY id ASC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rel = rs.getString("released_at");
                    list.add(new HospitalReservationRecord(
                            rs.getLong("id"),
                            rs.getString("hospital_id"),
                            rs.getString("case_id"),
                            rs.getString("ambulance_id"),
                            Instant.parse(rs.getString("reserved_at")),
                            rel != null ? Instant.parse(rel) : null,
                            rs.getString("status")
                    ));
                }
            }
        }
        return list;
    }

    private Hospital mapHospital(ResultSet rs, Connection conn) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String locationNode = rs.getString("location_node_id");
        int totalCap = rs.getInt("total_capacity");
        int availCap = rs.getInt("available_capacity");
        HospitalStatus status = HospitalStatus.valueOf(rs.getString("operational_status"));
        Instant lastUpdated = Instant.parse(rs.getString("last_updated"));

        try {
            com.resqroute.validation.DomainValidator.validateHospitalCapacity(totalCap, availCap);
        } catch (Exception ex) {
            throw new com.resqroute.exception.CorruptedDataException(
                    "Corrupted hospital capacity record in database for hospital id=" + id, ex);
        }

        Set<HospitalCapability> caps = loadCapabilities(id, conn);
        Set<EmergencyType> types = loadEmergencyTypes(id, conn);

        return new Hospital(
                id,
                name,
                locationNode,
                types,
                caps,
                totalCap,
                availCap,
                status,
                lastUpdated
        );
    }

    private Set<HospitalCapability> loadCapabilities(String hospitalId, Connection conn) throws SQLException {
        Set<HospitalCapability> caps = EnumSet.noneOf(HospitalCapability.class);
        String sql = "SELECT capability FROM hospital_capabilities WHERE hospital_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    caps.add(HospitalCapability.valueOf(rs.getString("capability")));
                }
            }
        }
        return caps;
    }

    private Set<EmergencyType> loadEmergencyTypes(String hospitalId, Connection conn) throws SQLException {
        Set<EmergencyType> types = EnumSet.noneOf(EmergencyType.class);
        String sql = "SELECT emergency_type FROM hospital_emergency_types WHERE hospital_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    types.add(EmergencyType.valueOf(rs.getString("emergency_type")));
                }
            }
        }
        return types;
    }
}
