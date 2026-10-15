package com.resqroute.algorithm;

import com.resqroute.exception.InvalidCostException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;

import java.util.*;

/**
 * High-performance Dijkstra's shortest-path algorithm optimized specifically for emergency response.
 *
 * <h3>Optimization Objective</h3>
 * <p>Standard geometric shortest-path algorithms minimize spatial distance in kilometres.
 * In emergency medical transport, this can be fatal because narrow, congested city alleys
 * often have short distances but prolonged transit times. This implementation minimizes
 * <b>total travel time in minutes</b> as its primary objective. If two competing routes offer
 * the exact same travel time, the algorithm applies a secondary tie-breaker minimizing
 * <b>total physical distance</b>.</p>
 *
 * <h3>Graph Representation</h3>
 * <p>The algorithm operates on an <b>adjacency-list</b> graph representation ({@link RoadGraph}).
 * For each vertex {@code u}, only its active outgoing directed edges are traversed.</p>
 *
 * <h3>Algorithmic Complexity</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> {@code O((|V| + |E|) * log(|V|))}, where {@code |V|} is the number of
 *       nodes (intersections/stations) and {@code |E|} is the number of directed road segments.
 *       Each node is settled at most once. Each edge is relaxed at most once. Priority queue operations
 *       (insert and extract-min) require {@code O(log |V|)} amortized time in the binary min-heap.
 *       Early termination halts search immediately when the destination node is settled.</li>
 *   <li><b>Space Complexity:</b> {@code O(|V|)} auxiliary space to maintain the min-priority queue,
 *       settled set, distance/time trackers, and predecessor pointers for route reconstruction.</li>
 * </ul>
 */
public class DijkstraRoutingAlgorithm implements RoutingAlgorithm {

    private static final double EPSILON = 1e-9;

    /**
     * Entry in the min-priority queue.
     * Orders primarily by travel time (ascending), secondarily by distance (ascending).
     */
    private record QueueEntry(String nodeId, double travelTimeMinutes, double distanceKm) implements Comparable<QueueEntry> {
        @Override
        public int compareTo(QueueEntry other) {
            int cmp = Double.compare(this.travelTimeMinutes, other.travelTimeMinutes);
            if (cmp != 0) {
                return cmp;
            }
            return Double.compare(this.distanceKm, other.distanceKm);
        }
    }

    @Override
    public RouteResult findFastestRoute(RoadGraph graph, String sourceId, String destinationId) {
        Objects.requireNonNull(graph, "RoadGraph must not be null");
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        String srcTrimmed = sourceId.trim();
        String dstTrimmed = destinationId.trim();

        // Verify both source and destination exist in the graph
        Node sourceNode = graph.getRequiredNode(srcTrimmed);
        Node destinationNode = graph.getRequiredNode(dstTrimmed);

        // Edge case: Source is identical to Destination
        if (srcTrimmed.equals(dstTrimmed)) {
            return RouteResult.singleNode(sourceNode);
        }

        // Dijkstra state tracking structures
        Map<String, Double> minTravelTime = new HashMap<>();
        Map<String, Double> distanceToNode = new HashMap<>();
        Map<String, Node> predecessorNode = new HashMap<>();
        Map<String, Edge> predecessorEdge = new HashMap<>();
        Set<String> settled = new HashSet<>();

        PriorityQueue<QueueEntry> pq = new PriorityQueue<>();

        // Initialize origin
        minTravelTime.put(srcTrimmed, 0.0);
        distanceToNode.put(srcTrimmed, 0.0);
        pq.offer(new QueueEntry(srcTrimmed, 0.0, 0.0));

        while (!pq.isEmpty()) {
            QueueEntry current = pq.poll();
            String u = current.nodeId();

            // Discard stale entries from previous relaxations
            if (settled.contains(u)) {
                continue;
            }

            // If entry cost is strictly worse than best known travel time, skip it
            Double bestKnownTime = minTravelTime.get(u);
            if (bestKnownTime != null && current.travelTimeMinutes() > bestKnownTime + EPSILON) {
                continue;
            }

            settled.add(u);

            // Early exit: Target settled with globally optimal travel time
            if (u.equals(dstTrimmed)) {
                break;
            }

            double currentTime = current.travelTimeMinutes();
            double currentDist = distanceToNode.getOrDefault(u, 0.0);

            for (Edge edge : graph.getOutgoingEdges(u)) {
                validateEdgeCost(edge);

                Node vNode = edge.getDestination();
                String v = vNode.getId();

                if (settled.contains(v)) {
                    continue;
                }

                double edgeTime = edge.getTravelTimeMinutes();
                double edgeDist = edge.getDistanceKm();
                double newTime = currentTime + edgeTime;
                double newDist = currentDist + edgeDist;

                double knownBestTime = minTravelTime.getOrDefault(v, Double.POSITIVE_INFINITY);
                double knownBestDist = distanceToNode.getOrDefault(v, Double.POSITIVE_INFINITY);

                // Primary criterion: strictly faster travel time
                boolean isFaster = newTime < knownBestTime - EPSILON;
                // Secondary criterion (tie-breaking): identical travel time, but shorter physical distance
                boolean isTieBreakShorterDistance = Math.abs(newTime - knownBestTime) <= EPSILON && newDist < knownBestDist - EPSILON;

                if (isFaster || isTieBreakShorterDistance) {
                    minTravelTime.put(v, newTime);
                    distanceToNode.put(v, newDist);
                    predecessorNode.put(v, graph.getRequiredNode(u));
                    predecessorEdge.put(v, edge);
                    pq.offer(new QueueEntry(v, newTime, newDist));
                }
            }
        }

        // Unreachable destination (no path found or graph disconnected)
        if (!predecessorNode.containsKey(dstTrimmed)) {
            return RouteResult.unreachable(srcTrimmed, dstTrimmed);
        }

        // Efficient route reconstruction from destination back to source
        LinkedList<Node> reconstructedPath = new LinkedList<>();
        LinkedList<Edge> reconstructedEdges = new LinkedList<>();

        Node curr = destinationNode;
        while (curr != null && !curr.getId().equals(srcTrimmed)) {
            reconstructedPath.addFirst(curr);
            Edge edgeToCurr = predecessorEdge.get(curr.getId());
            if (edgeToCurr != null) {
                reconstructedEdges.addFirst(edgeToCurr);
            }
            curr = predecessorNode.get(curr.getId());
        }

        // Prepend source node
        reconstructedPath.addFirst(sourceNode);

        // Calculate exact accumulated totals
        double totalDistance = 0.0;
        double totalTime = 0.0;
        for (Edge edge : reconstructedEdges) {
            totalDistance += edge.getDistanceKm();
            totalTime += edge.getTravelTimeMinutes();
        }

        return RouteResult.reachable(reconstructedPath, reconstructedEdges, totalDistance, totalTime);
    }

    private void validateNodeId(String id, String role) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(role + " node ID must not be null or blank");
        }
    }

    private void validateEdgeCost(Edge edge) {
        if (Double.isNaN(edge.getTravelTimeMinutes()) || Double.isInfinite(edge.getTravelTimeMinutes()) || edge.getTravelTimeMinutes() < 0.0) {
            throw new InvalidCostException("Encountered invalid travel time: " + edge.getTravelTimeMinutes());
        }
        if (Double.isNaN(edge.getDistanceKm()) || Double.isInfinite(edge.getDistanceKm()) || edge.getDistanceKm() < 0.0) {
            throw new InvalidCostException("Encountered invalid distance: " + edge.getDistanceKm());
        }
    }
}
