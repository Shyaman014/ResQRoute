package com.resqroute.database.repository;

import com.resqroute.model.RoadIncident;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing road incidents and closures.
 */
public interface RoadIncidentRepository {

    void save(RoadIncident incident, Connection conn) throws SQLException;

    Optional<RoadIncident> findById(String incidentId, Connection conn) throws SQLException;

    List<RoadIncident> findActive(Connection conn) throws SQLException;

    List<RoadIncident> findAll(Connection conn) throws SQLException;

    void resolve(String incidentId, Instant resolvedAt, Connection conn) throws SQLException;
}
