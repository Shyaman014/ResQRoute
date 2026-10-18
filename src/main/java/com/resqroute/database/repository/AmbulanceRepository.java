package com.resqroute.database.repository;

import com.resqroute.model.Ambulance;
import com.resqroute.model.AmbulanceStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing ambulance fleet persistence.
 */
public interface AmbulanceRepository {

    void save(Ambulance ambulance, Connection conn) throws SQLException;

    Optional<Ambulance> findById(String id, Connection conn) throws SQLException;

    List<Ambulance> findAll(Connection conn) throws SQLException;

    List<Ambulance> findByStatus(AmbulanceStatus status, Connection conn) throws SQLException;

    void updateStatus(String ambulanceId, AmbulanceStatus status, String assignedCaseId, Connection conn) throws SQLException;

    void updateLocation(String ambulanceId, String locationNodeId, Connection conn) throws SQLException;

    void delete(String ambulanceId, Connection conn) throws SQLException;
}
