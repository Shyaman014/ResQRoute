package com.resqroute.database.repository;

import com.resqroute.database.model.HospitalReservationRecord;
import com.resqroute.model.EmergencyType;
import com.resqroute.model.Hospital;
import com.resqroute.model.HospitalCapability;
import com.resqroute.model.HospitalStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for managing hospital facilities, capacities, and bed reservations.
 */
public interface HospitalRepository {

    void save(Hospital hospital, Connection conn) throws SQLException;

    Optional<Hospital> findById(String id, Connection conn) throws SQLException;

    List<Hospital> findAll(Connection conn) throws SQLException;

    List<Hospital> findEligible(EmergencyType emergencyType, Set<HospitalCapability> requiredCapabilities, Connection conn) throws SQLException;

    void updateCapacity(String hospitalId, int availableCapacity, Connection conn) throws SQLException;

    void updateStatus(String hospitalId, HospitalStatus status, Connection conn) throws SQLException;

    boolean reserveCapacity(String hospitalId, String caseId, String ambulanceId, Instant reservedAt, Connection conn) throws SQLException;

    boolean releaseCapacity(String hospitalId, String caseId, Instant releasedAt, Connection conn) throws SQLException;

    List<HospitalReservationRecord> getReservations(String hospitalId, Connection conn) throws SQLException;
}
