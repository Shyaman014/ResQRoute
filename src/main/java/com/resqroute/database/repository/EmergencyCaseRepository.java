package com.resqroute.database.repository;

import com.resqroute.database.model.EmergencyStatusRecord;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencyStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for persisting emergency cases and status history.
 */
public interface EmergencyCaseRepository {

    void save(EmergencyCase emergencyCase, Connection conn) throws SQLException;

    Optional<EmergencyCase> findById(String id, Connection conn) throws SQLException;

    List<EmergencyCase> findAll(Connection conn) throws SQLException;

    List<EmergencyCase> findActive(Connection conn) throws SQLException;

    void updateStatus(String caseId, EmergencyStatus status, String reason, Instant recordedAt, Connection conn) throws SQLException;

    void assignAmbulance(String caseId, String ambulanceId, Connection conn) throws SQLException;

    void assignHospital(String caseId, String hospitalId, Connection conn) throws SQLException;

    List<EmergencyStatusRecord> getStatusHistory(String caseId, Connection conn) throws SQLException;
}
