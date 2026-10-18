package com.resqroute.database.repository;

import com.resqroute.database.model.AmbulanceAssignmentRecord;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Repository interface for persisting ambulance assignments and tracking fleet mission cycles.
 */
public interface AmbulanceAssignmentRepository {

    void recordAssignment(String ambulanceId, String caseId, Instant assignedAt, Connection conn) throws SQLException;

    void completeAssignment(String ambulanceId, String caseId, Instant completedAt, Connection conn) throws SQLException;

    void cancelAssignment(String ambulanceId, String caseId, Connection conn) throws SQLException;

    List<AmbulanceAssignmentRecord> findActiveAssignments(Connection conn) throws SQLException;

    List<AmbulanceAssignmentRecord> findByAmbulanceId(String ambulanceId, Connection conn) throws SQLException;

    List<AmbulanceAssignmentRecord> findByCaseId(String caseId, Connection conn) throws SQLException;
}
