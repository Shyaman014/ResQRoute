package com.resqroute.database.repository;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed implementation of {@link RoadNetworkRepository}.
 */
public class SqliteRoadNetworkRepository implements RoadNetworkRepository {

    @Override
    public void saveGraph(RoadGraph graph, Connection conn) throws SQLException {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        for (Node node : graph.getAllNodes()) {
            saveNode(node, conn);
        }

        for (Edge edge : graph.getAllEdges()) {
            String src = edge.getSource().map(Node::getId).orElseThrow();
            String dst = edge.getDestination().getId();
            saveRoad(src, dst, edge.getDistanceKm(), edge.getBaseTravelTimeMinutes(),
                    edge.getRoadName(), edge.isAvailable(), conn);
        }
    }

    @Override
    public RoadGraph loadGraph(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        RoadGraph graph = new RoadGraph();

        // 1. Load nodes
        List<Node> nodes = findAllNodes(conn);
        for (Node node : nodes) {
            graph.addNode(node);
        }

        // 2. Load directed roads
        String sql = "SELECT source_node_id, dest_node_id, distance_km, base_travel_time_minutes, road_name, is_available FROM roads";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String src = rs.getString("source_node_id");
                String dst = rs.getString("dest_node_id");
                double dist = rs.getDouble("distance_km");
                double time = rs.getDouble("base_travel_time_minutes");
                String name = rs.getString("road_name");
                boolean available = rs.getInt("is_available") == 1;

                try {
                    com.resqroute.validation.DomainValidator.validateRoadWeights(dist, time);
                } catch (Exception ex) {
                    throw new com.resqroute.exception.CorruptedDataException(
                            String.format("Corrupted road edge in database: %s -> %s", src, dst), ex);
                }

                graph.addEdge(src, dst, dist, time, name);
                if (!available) {
                    graph.setRoadAvailability(src, dst, false);
                }
            }
        }

        return graph;
    }

    @Override
    public void saveNode(Node node, Connection conn) throws SQLException {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO road_nodes (id, name, latitude, longitude)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                latitude = excluded.latitude,
                longitude = excluded.longitude;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, node.getId());
            ps.setString(2, node.getName());
            ps.setDouble(3, node.getCoordinates().latitude());
            ps.setDouble(4, node.getCoordinates().longitude());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Node> findNodeById(String nodeId, Connection conn) throws SQLException {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "SELECT id, name, latitude, longitude FROM road_nodes WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Node(
                            rs.getString("id"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            rs.getString("name")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Node> findAllNodes(Connection conn) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        List<Node> list = new ArrayList<>();
        String sql = "SELECT id, name, latitude, longitude FROM road_nodes ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                double lat = rs.getDouble("latitude");
                double lon = rs.getDouble("longitude");

                try {
                    com.resqroute.validation.DomainValidator.validateCoordinates(lat, lon);
                } catch (Exception ex) {
                    throw new com.resqroute.exception.CorruptedDataException(
                            "Corrupted node record in database: id=" + id, ex);
                }

                list.add(new Node(id, lat, lon, name));
            }
        }
        return list;
    }

    @Override
    public void saveRoad(String sourceId, String destId, double distanceKm, double travelTimeMins,
                         String roadName, boolean isAvailable, Connection conn) throws SQLException {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(destId, "destId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = """
            INSERT INTO roads (source_node_id, dest_node_id, distance_km, base_travel_time_minutes, road_name, is_available)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(source_node_id, dest_node_id) DO UPDATE SET
                distance_km = excluded.distance_km,
                base_travel_time_minutes = excluded.base_travel_time_minutes,
                road_name = excluded.road_name,
                is_available = excluded.is_available;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceId);
            ps.setString(2, destId);
            ps.setDouble(3, distanceKm);
            ps.setDouble(4, travelTimeMins);
            ps.setString(5, roadName != null ? roadName : "Unnamed Road");
            ps.setInt(6, isAvailable ? 1 : 0);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateRoadAvailability(String sourceId, String destId, boolean isAvailable, Connection conn) throws SQLException {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(destId, "destId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "UPDATE roads SET is_available = ? WHERE source_node_id = ? AND dest_node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, isAvailable ? 1 : 0);
            ps.setString(2, sourceId);
            ps.setString(3, destId);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean isRoadAvailable(String sourceId, String destId, Connection conn) throws SQLException {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(destId, "destId must not be null");
        Objects.requireNonNull(conn, "conn must not be null");

        String sql = "SELECT is_available FROM roads WHERE source_node_id = ? AND dest_node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceId);
            ps.setString(2, destId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_available") == 1;
                }
            }
        }
        return false;
    }
}
