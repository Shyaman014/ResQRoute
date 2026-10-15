package com.resqroute.graph;

import com.resqroute.exception.DuplicateEdgeException;
import com.resqroute.exception.DuplicateNodeException;
import com.resqroute.exception.InvalidEdgeException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.TrafficCondition;
import com.resqroute.model.TrafficLevel;

import java.util.*;

/**
 * Adjacency-list representation of an emergency road network with comprehensive validation
 * and dynamic topology mutation operations.
 *
 * <p>Key guarantees:
 * <ul>
 *   <li>Duplicate node and edge protection.</li>
 *   <li>Prevention of self-loops (a road cannot connect an intersection to itself).</li>
 *   <li>Validation of all geographic coordinates, distances, and travel times.</li>
 *   <li>Consistent bidirectional roadway operations.</li>
 *   <li>Safe removal of nodes and edges maintaining strict graph integrity (no dangling edges).</li>
 *   <li>Immutability of exposed collections (unmodifiable views only).</li>
 * </ul>
 */
public class RoadGraph {

    private final Map<String, Node> nodes;
    private final Map<String, List<Edge>> adjacencyList;

    /**
     * Constructs an empty road graph.
     */
    public RoadGraph() {
        this.nodes = new LinkedHashMap<>();
        this.adjacencyList = new LinkedHashMap<>();
    }

    /**
     * Inserts a new node into the graph.
     *
     * @param node The node to insert; must not be null.
     * @throws DuplicateNodeException if a node with the same ID already exists.
     * @throws NullPointerException   if node is null.
     */
    public void addNode(Node node) {
        Objects.requireNonNull(node, "Node must not be null");
        String id = node.getId();
        if (nodes.containsKey(id)) {
            throw new DuplicateNodeException(id);
        }
        nodes.put(id, node);
        adjacencyList.put(id, new ArrayList<>());
    }

    /**
     * Inserts a directed road edge from source node to destination node.
     *
     * @param sourceId          Identifier of the source node; must exist in graph.
     * @param destinationId     Identifier of the destination node; must exist in graph.
     * @param distanceKm        Road distance in kilometres; must be non-negative.
     * @param travelTimeMinutes Estimated travel time in minutes; must be non-negative.
     * @param roadName          Optional roadway name or description.
     * @throws NodeNotFoundException  if source or destination node does not exist in graph.
     * @throws InvalidEdgeException   if source and destination are identical (self-loop).
     * @throws DuplicateEdgeException if a road from source to destination already exists.
     */
    public void addEdge(String sourceId, String destinationId, double distanceKm, double travelTimeMinutes, String roadName) {
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        String srcTrimmed = sourceId.trim();
        String dstTrimmed = destinationId.trim();

        // Self-loop prevention
        if (srcTrimmed.equals(dstTrimmed)) {
            throw new InvalidEdgeException("Self-loops are not permitted in road graph: source and destination cannot be identical ('" + srcTrimmed + "')");
        }

        Node source = getRequiredNode(srcTrimmed);
        Node destination = getRequiredNode(dstTrimmed);

        // Duplicate edge check
        if (containsEdge(srcTrimmed, dstTrimmed)) {
            throw new DuplicateEdgeException(srcTrimmed, dstTrimmed);
        }

        Edge edge = new Edge(source, destination, distanceKm, travelTimeMinutes, roadName);
        adjacencyList.get(srcTrimmed).add(edge);
    }

    /**
     * Inserts a directed road edge without an explicit road name.
     */
    public void addEdge(String sourceId, String destinationId, double distanceKm, double travelTimeMinutes) {
        addEdge(sourceId, destinationId, distanceKm, travelTimeMinutes, null);
    }

    /**
     * Inserts a pre-constructed directed edge originating from the specified source node.
     *
     * @param sourceId Identifier of the source node; must exist in graph.
     * @param edge     The edge to add; must not be null and destination must exist in graph.
     */
    public void addEdge(String sourceId, Edge edge) {
        validateNodeId(sourceId, "Source");
        Objects.requireNonNull(edge, "Edge must not be null");

        String srcTrimmed = sourceId.trim();
        String destId = edge.getDestination().getId();

        if (srcTrimmed.equals(destId)) {
            throw new InvalidEdgeException("Self-loops are not permitted in road graph: source and destination cannot be identical ('" + srcTrimmed + "')");
        }

        Node sourceNode = getRequiredNode(srcTrimmed);
        if (!nodes.containsKey(destId)) {
            throw new NodeNotFoundException(destId);
        }

        if (containsEdge(srcTrimmed, destId)) {
            throw new DuplicateEdgeException(srcTrimmed, destId);
        }

        Edge populatedEdge = edge.getSource().isEmpty() ? edge.withSource(sourceNode) : edge;
        adjacencyList.get(srcTrimmed).add(populatedEdge);
    }

    /**
     * Updates an existing directed road segment with new distance and travel time.
     * Useful for updating dynamic traffic conditions without altering topology.
     *
     * @param sourceId          Identifier of the source node.
     * @param destinationId     Identifier of the destination node.
     * @param distanceKm        Updated physical distance in km.
     * @param travelTimeMinutes Updated travel time in minutes.
     * @param roadName          Updated road name.
     * @throws InvalidEdgeException if road does not exist.
     */
    public void updateEdge(String sourceId, String destinationId, double distanceKm, double travelTimeMinutes, String roadName) {
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        Node source = getRequiredNode(sourceId);
        Node destination = getRequiredNode(destinationId);

        List<Edge> edges = adjacencyList.get(source.getId());
        for (int i = 0; i < edges.size(); i++) {
            Edge current = edges.get(i);
            if (current.getDestination().getId().equals(destination.getId())) {
                String effectiveRoadName = (roadName != null) ? roadName : current.getRoadName();
                edges.set(i, new Edge(source, destination, distanceKm, travelTimeMinutes, current.getTrafficCondition(), effectiveRoadName, current.isAvailable()));
                return;
            }
        }
        throw new InvalidEdgeException(String.format("Cannot update road: road from '%s' to '%s' does not exist.", sourceId, destinationId));
    }

    /**
     * Updates an existing directed road segment without changing its road name.
     */
    public void updateEdge(String sourceId, String destinationId, double distanceKm, double travelTimeMinutes) {
        updateEdge(sourceId, destinationId, distanceKm, travelTimeMinutes, null);
    }

    /**
     * Updates the dynamic traffic condition on an existing directed road segment.
     * Static road distance and base travel time remain strictly preserved.
     *
     * @param sourceId      Source node identifier.
     * @param destinationId Destination node identifier.
     * @param condition     New dynamic traffic condition; must not be null.
     * @throws InvalidEdgeException if road does not exist.
     */
    public void updateTraffic(String sourceId, String destinationId, TrafficCondition condition) {
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");
        Objects.requireNonNull(condition, "TrafficCondition must not be null");

        Node source = getRequiredNode(sourceId);
        Node destination = getRequiredNode(destinationId);

        List<Edge> edges = adjacencyList.get(source.getId());
        for (int i = 0; i < edges.size(); i++) {
            Edge current = edges.get(i);
            if (current.getDestination().getId().equals(destination.getId())) {
                edges.set(i, current.withTrafficCondition(condition));
                return;
            }
        }
        throw new InvalidEdgeException(String.format("Cannot update traffic: road from '%s' to '%s' does not exist.", sourceId, destinationId));
    }

    /**
     * Updates the dynamic traffic condition on an existing directed road segment using standard TrafficLevel.
     */
    public void updateTraffic(String sourceId, String destinationId, TrafficLevel level) {
        updateTraffic(sourceId, destinationId, TrafficCondition.of(level, "ROAD_TELEMETRY"));
    }

    /**
     * Updates dynamic traffic conditions on a bidirectional roadway in both directions.
     */
    public void updateBidirectionalTraffic(String nodeAId, String nodeBId, TrafficCondition condition) {
        updateTraffic(nodeAId, nodeBId, condition);
        updateTraffic(nodeBId, nodeAId, condition);
    }

    /**
     * Updates dynamic traffic conditions on a bidirectional roadway using standard TrafficLevel.
     */
    public void updateBidirectionalTraffic(String nodeAId, String nodeBId, TrafficLevel level) {
        updateBidirectionalTraffic(nodeAId, nodeBId, TrafficCondition.of(level, "ROAD_TELEMETRY"));
    }

    /**
     * Updates the operational availability of an existing directed road segment.
     * Original road distance, base travel time, and traffic conditions remain strictly preserved.
     *
     * @param sourceId      Source node identifier.
     * @param destinationId Destination node identifier.
     * @param available     true if road is open, false if temporarily closed.
     * @throws InvalidEdgeException if road does not exist.
     */
    public void setRoadAvailability(String sourceId, String destinationId, boolean available) {
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        Node source = getRequiredNode(sourceId);
        Node destination = getRequiredNode(destinationId);

        List<Edge> edges = adjacencyList.get(source.getId());
        for (int i = 0; i < edges.size(); i++) {
            Edge current = edges.get(i);
            if (current.getDestination().getId().equals(destination.getId())) {
                edges.set(i, current.withAvailability(available));
                return;
            }
        }
        throw new InvalidEdgeException(String.format("Cannot update road availability: road from '%s' to '%s' does not exist.", sourceId, destinationId));
    }

    /**
     * Updates operational availability in both directions for a bidirectional roadway.
     */
    public void setBidirectionalRoadAvailability(String nodeAId, String nodeBId, boolean available) {
        setRoadAvailability(nodeAId, nodeBId, available);
        setRoadAvailability(nodeBId, nodeAId, available);
    }

    /**
     * Temporarily closes an individual directed road segment to traffic.
     */
    public void closeRoad(String sourceId, String destinationId) {
        setRoadAvailability(sourceId, destinationId, false);
    }

    /**
     * Reopens a previously closed directed road segment.
     */
    public void reopenRoad(String sourceId, String destinationId) {
        setRoadAvailability(sourceId, destinationId, true);
    }

    /**
     * Temporarily closes a bidirectional roadway in both directions.
     */
    public void closeBidirectionalRoad(String nodeAId, String nodeBId) {
        setBidirectionalRoadAvailability(nodeAId, nodeBId, false);
    }

    /**
     * Reopens a bidirectional roadway in both directions.
     */
    public void reopenBidirectionalRoad(String nodeAId, String nodeBId) {
        setBidirectionalRoadAvailability(nodeAId, nodeBId, true);
    }

    /**
     * Checks whether a directed road segment exists and is currently open to traffic.
     */
    public boolean isRoadAvailable(String sourceId, String destinationId) {
        return getEdge(sourceId, destinationId).map(Edge::isAvailable).orElse(false);
    }

    /**
     * Checks whether a directed road segment exists and is currently closed.
     */
    public boolean isRoadClosed(String sourceId, String destinationId) {
        return getEdge(sourceId, destinationId).map(Edge::isClosed).orElse(false);
    }

    /**
     * Retrieves an unmodifiable list of all road segments currently closed to traffic.
     */
    public List<Edge> getClosedEdges() {
        return getAllEdges().stream().filter(Edge::isClosed).toList();
    }

    /**
     * Retrieves an unmodifiable list of all road segments currently open to traffic.
     */
    public List<Edge> getAvailableEdges() {
        return getAllEdges().stream().filter(Edge::isAvailable).toList();
    }


    /**
     * Inserts a bidirectional (two-way) roadway connecting two nodes.
     * Automatically verifies that neither direction already exists before inserting.
     *
     * @param nodeAId           First node identifier.
     * @param nodeBId           Second node identifier.
     * @param distanceKm        Road length in kilometres.
     * @param travelTimeMinutes Estimated travel time in minutes.
     * @param roadName          Optional roadway name.
     */
    public void addBidirectionalEdge(String nodeAId, String nodeBId, double distanceKm, double travelTimeMinutes, String roadName) {
        validateNodeId(nodeAId, "Node A");
        validateNodeId(nodeBId, "Node B");

        String a = nodeAId.trim();
        String b = nodeBId.trim();

        if (a.equals(b)) {
            throw new InvalidEdgeException("Self-loops are not permitted in road graph: ('" + a + "')");
        }

        getRequiredNode(a);
        getRequiredNode(b);

        if (containsEdge(a, b)) {
            throw new DuplicateEdgeException(a, b);
        }
        if (containsEdge(b, a)) {
            throw new DuplicateEdgeException(b, a);
        }

        addEdge(a, b, distanceKm, travelTimeMinutes, roadName);
        addEdge(b, a, distanceKm, travelTimeMinutes, roadName);
    }

    /**
     * Inserts an unnamed bidirectional roadway.
     */
    public void addBidirectionalEdge(String nodeAId, String nodeBId, double distanceKm, double travelTimeMinutes) {
        addBidirectionalEdge(nodeAId, nodeBId, distanceKm, travelTimeMinutes, null);
    }

    /**
     * Checks if a directed edge exists from sourceId to destinationId.
     *
     * @param sourceId      Source node identifier.
     * @param destinationId Destination node identifier.
     * @return true if the directed edge exists, false otherwise.
     */
    public boolean containsEdge(String sourceId, String destinationId) {
        if (sourceId == null || destinationId == null) {
            return false;
        }
        List<Edge> edges = adjacencyList.get(sourceId.trim());
        if (edges == null) {
            return false;
        }
        String dst = destinationId.trim();
        return edges.stream().anyMatch(e -> e.getDestination().getId().equals(dst));
    }

    /**
     * Checks if a bidirectional roadway exists connecting two nodes in both directions.
     */
    public boolean containsBidirectionalEdge(String nodeAId, String nodeBId) {
        return containsEdge(nodeAId, nodeBId) && containsEdge(nodeBId, nodeAId);
    }

    /**
     * Retrieves the directed edge from sourceId to destinationId, if present.
     */
    public Optional<Edge> getEdge(String sourceId, String destinationId) {
        if (sourceId == null || destinationId == null) {
            return Optional.empty();
        }
        List<Edge> edges = adjacencyList.get(sourceId.trim());
        if (edges == null) {
            return Optional.empty();
        }
        String dst = destinationId.trim();
        return edges.stream().filter(e -> e.getDestination().getId().equals(dst)).findFirst();
    }

    /**
     * Safely removes a directed road from sourceId to destinationId.
     *
     * @param sourceId      Source node identifier.
     * @param destinationId Destination node identifier.
     * @return true if the edge was present and removed, false otherwise.
     */
    public boolean removeEdge(String sourceId, String destinationId) {
        if (sourceId == null || destinationId == null) {
            return false;
        }
        List<Edge> edges = adjacencyList.get(sourceId.trim());
        if (edges == null) {
            return false;
        }
        String dst = destinationId.trim();
        return edges.removeIf(e -> e.getDestination().getId().equals(dst));
    }

    /**
     * Safely removes roads connecting nodeA and nodeB in both directions.
     *
     * @return true if at least one direction was removed, false if neither existed.
     */
    public boolean removeBidirectionalEdge(String nodeAId, String nodeBId) {
        boolean removedAB = removeEdge(nodeAId, nodeBId);
        boolean removedBA = removeEdge(nodeBId, nodeAId);
        return removedAB || removedBA;
    }

    /**
     * Safely removes a node and ALL associated incoming and outgoing roads from the graph.
     * Guarantees that no dangling edges remain in the graph after removal.
     *
     * @param nodeId Identifier of the node to remove.
     * @return true if the node was present and removed, false otherwise.
     */
    public boolean removeNode(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return false;
        }
        String id = nodeId.trim();
        if (!nodes.containsKey(id)) {
            return false;
        }

        // 1. Remove outgoing edges
        adjacencyList.remove(id);

        // 2. Remove node from registry
        nodes.remove(id);

        // 3. Remove all incoming edges pointing to this node from remaining nodes
        for (List<Edge> edges : adjacencyList.values()) {
            edges.removeIf(edge -> edge.getDestination().getId().equals(id));
        }

        return true;
    }

    /**
     * Retrieves an unmodifiable list of outgoing edges from the specified node.
     *
     * @param nodeId Identifier of the node.
     * @return Unmodifiable list of outgoing edges.
     * @throws NodeNotFoundException if node does not exist in graph.
     */
    public List<Edge> getOutgoingEdges(String nodeId) {
        validateNodeId(nodeId, "Node");
        List<Edge> edges = adjacencyList.get(nodeId.trim());
        if (edges == null) {
            throw new NodeNotFoundException(nodeId);
        }
        return Collections.unmodifiableList(edges);
    }

    /**
     * Retrieves an unmodifiable list of incoming edges leading into the specified node.
     *
     * @param nodeId Identifier of the node.
     * @return Unmodifiable list of incoming edges.
     * @throws NodeNotFoundException if node does not exist in graph.
     */
    public List<Edge> getIncomingEdges(String nodeId) {
        getRequiredNode(nodeId); // verifies existence
        String targetId = nodeId.trim();
        List<Edge> incoming = new ArrayList<>();
        for (List<Edge> edges : adjacencyList.values()) {
            for (Edge edge : edges) {
                if (edge.getDestination().getId().equals(targetId)) {
                    incoming.add(edge);
                }
            }
        }
        return Collections.unmodifiableList(incoming);
    }

    /**
     * Calculates the in-degree (number of incoming roads) for a node.
     */
    public int getInDegree(String nodeId) {
        return getIncomingEdges(nodeId).size();
    }

    /**
     * Calculates the out-degree (number of outgoing roads) for a node.
     */
    public int getOutDegree(String nodeId) {
        return getOutgoingEdges(nodeId).size();
    }

    /**
     * Retrieves an unmodifiable list of all directed edges across the entire network.
     */
    public List<Edge> getAllEdges() {
        List<Edge> all = new ArrayList<>();
        for (List<Edge> edges : adjacencyList.values()) {
            all.addAll(edges);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Retrieves an unmodifiable list of adjacent neighbour nodes reachable from the specified node.
     *
     * @param nodeId Identifier of the node.
     * @return List of destination nodes.
     * @throws NodeNotFoundException if node does not exist in graph.
     */
    public List<Node> getNeighbors(String nodeId) {
        return getOutgoingEdges(nodeId).stream()
                .map(Edge::getDestination)
                .toList();
    }

    /**
     * Checks if a node with the given ID exists in the graph.
     */
    public boolean containsNode(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return false;
        }
        return nodes.containsKey(nodeId.trim());
    }

    /**
     * Finds a node by ID, returning an Optional.
     */
    public Optional<Node> getNode(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(nodes.get(nodeId.trim()));
    }

    /**
     * Retrieves a node by ID, or throws NodeNotFoundException if it does not exist.
     */
    public Node getRequiredNode(String nodeId) {
        validateNodeId(nodeId, "Node");
        Node node = nodes.get(nodeId.trim());
        if (node == null) {
            throw new NodeNotFoundException(nodeId);
        }
        return node;
    }

    /**
     * Returns an unmodifiable collection of all nodes currently registered in the graph.
     */
    public Collection<Node> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Returns total number of nodes in the graph.
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Returns total number of directed edges in the graph.
     */
    public int getEdgeCount() {
        return adjacencyList.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Checks whether the graph has no nodes.
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Clears all nodes and edges from the graph.
     */
    public void clear() {
        nodes.clear();
        adjacencyList.clear();
    }

    private void validateNodeId(String nodeId, String role) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException(role + " node ID must not be null or blank");
        }
    }
}
