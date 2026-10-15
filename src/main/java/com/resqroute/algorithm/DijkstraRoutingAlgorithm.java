package com.resqroute.algorithm;

import com.resqroute.exception.InvalidCostException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.EdgeKey;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;

import java.util.*;

/**
 * High-performance Dijkstra's shortest-path algorithm tailored for emergency response optimization.
 *
 * <p>Unlike standard geometric shortest-path algorithms that optimize for spatial distance,
 * this implementation strictly minimizes <b>total travel time in minutes</b>. This critical
 * design choice ensures ambulances and emergency vehicles bypass slow, congested, or low-speed
 * urban corridors even if the physical distance is slightly greater.</p>
 *
 * <h3>Algorithmic Complexity</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> {@code O((|V| + |E|) * log(|V|))}, where {@code |V|} is the number of
 *       intersections (nodes) and {@code |E|} is the number of directed road segments (edges).
 *       Extract-min and decrease-key (re-insertion) operations in the binary min-heap take {@code O(log |V|)}
 *       amortized time. Early termination halts computation as soon as the target node is extracted from the heap.</li>
 *   <li><b>Space Complexity:</b> {@code O(|V|)} auxiliary space to store best-known travel times,
 *       predecessor pointers for route reconstruction, visited sets, and binary min-heap entries.</li>
 * </ul>
 */
public class DijkstraRoutingAlgorithm implements RoutingAlgorithm {

    private static final double EPSILON = 1e-9;

    /**
     * Internal entry representing a node in the min-priority queue ordered by travel time.
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
        return findFastestRoute(graph, sourceId, destinationId, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Calculates the fastest emergency route while ignoring temporarily excluded nodes and edges.
     * Essential for Yen's K-shortest paths algorithm to explore branch deviations without mutating the graph.
     *
     * @param graph           Road network graph.
     * @param sourceId        Origin waypoint identifier.
     * @param destinationId   Destination waypoint identifier.
     * @param excludedNodeIds Set of node IDs to avoid during traversal; may be empty or null.
     * @param excludedEdges   Set of directed edge keys to avoid; may be empty or null.
     * @return Reachable RouteResult, or unreachable if no valid path exists.
     */
    public RouteResult findFastestRoute(RoadGraph graph,
                                        String sourceId,
                                        String destinationId,
                                        Set<String> excludedNodeIds,
                                        Set<EdgeKey> excludedEdges) {
        Objects.requireNonNull(graph, "RoadGraph must not be null");
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        String srcTrimmed = sourceId.trim();
        String dstTrimmed = destinationId.trim();

        // If source or destination are explicitly excluded, path is unreachable
        if (excludedNodeIds != null && (excludedNodeIds.contains(srcTrimmed) || excludedNodeIds.contains(dstTrimmed))) {
            return RouteResult.unreachable(srcTrimmed, dstTrimmed);
        }

        Node sourceNode = graph.getRequiredNode(srcTrimmed);
        Node destinationNode = graph.getRequiredNode(dstTrimmed);

        // Edge case: Source is identical to Destination
        if (srcTrimmed.equals(dstTrimmed)) {
            return RouteResult.singleNode(sourceNode);
        }

        Map<String, Double> minTravelTime = new HashMap<>();
        Map<String, Double> distanceToNode = new HashMap<>();
        Map<String, Node> predecessorNode = new HashMap<>();
        Map<String, Edge> predecessorEdge = new HashMap<>();
        Set<String> settled = new HashSet<>();

        PriorityQueue<QueueEntry> pq = new PriorityQueue<>();

        minTravelTime.put(srcTrimmed, 0.0);
        distanceToNode.put(srcTrimmed, 0.0);
        pq.offer(new QueueEntry(srcTrimmed, 0.0, 0.0));

        while (!pq.isEmpty()) {
            QueueEntry current = pq.poll();
            String u = current.nodeId();

            if (settled.contains(u)) {
                continue;
            }

            Double bestKnownTime = minTravelTime.get(u);
            if (bestKnownTime != null && current.travelTimeMinutes() > bestKnownTime + EPSILON) {
                continue;
            }

            settled.add(u);

            if (u.equals(dstTrimmed)) {
                break;
            }

            double currentTime = current.travelTimeMinutes();
            double currentDist = distanceToNode.getOrDefault(u, 0.0);

            for (Edge edge : graph.getOutgoingEdges(u)) {
                validateEdgeCost(edge);

                Node vNode = edge.getDestination();
                String v = vNode.getId();

                // Skip excluded nodes
                if (excludedNodeIds != null && excludedNodeIds.contains(v)) {
                    continue;
                }

                // Skip excluded directed edges
                if (excludedEdges != null && excludedEdges.contains(new EdgeKey(u, v))) {
                    continue;
                }

                if (settled.contains(v)) {
                    continue;
                }

                double edgeTime = edge.getTravelTimeMinutes();
                double edgeDist = edge.getDistanceKm();
                double newTime = currentTime + edgeTime;
                double newDist = currentDist + edgeDist;

                double knownBestTime = minTravelTime.getOrDefault(v, Double.POSITIVE_INFINITY);
                double knownBestDist = distanceToNode.getOrDefault(v, Double.POSITIVE_INFINITY);

                boolean isFaster = newTime < knownBestTime - EPSILON;
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

        // Delegate to shared, cycle-safe RouteReconstructor
        return RouteReconstructor.reconstruct(sourceNode, destinationNode, predecessorNode, predecessorEdge);
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
