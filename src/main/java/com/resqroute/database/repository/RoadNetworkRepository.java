package com.resqroute.database.repository;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for persisting and loading road network topologies.
 */
public interface RoadNetworkRepository {

    void saveGraph(RoadGraph graph, Connection conn) throws SQLException;

    RoadGraph loadGraph(Connection conn) throws SQLException;

    void saveNode(Node node, Connection conn) throws SQLException;

    Optional<Node> findNodeById(String nodeId, Connection conn) throws SQLException;

    List<Node> findAllNodes(Connection conn) throws SQLException;

    void saveRoad(String sourceId, String destId, double distanceKm, double travelTimeMins,
                  String roadName, boolean isAvailable, Connection conn) throws SQLException;

    void updateRoadAvailability(String sourceId, String destId, boolean isAvailable, Connection conn) throws SQLException;

    boolean isRoadAvailable(String sourceId, String destId, Connection conn) throws SQLException;
}
