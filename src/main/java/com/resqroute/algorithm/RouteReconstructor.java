package com.resqroute.algorithm;

import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;

import java.util.*;

/**
 * Shared utility for reconstructing complete, cycle-free emergency route paths from predecessor pointers.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Reconstructs the exact ordered sequence of nodes and edges from origin to destination.</li>
 *   <li>Detects and rejects any accidental cyclical loops during back-tracing.</li>
 *   <li>Computes total distance and travel time directly from the sequence of traversed edges.</li>
 *   <li>Handles identical source/destination, unreachable targets, and disconnected graphs.</li>
 *   <li>Returns an immutable, structured {@link RouteResult}.</li>
 * </ul>
 */
public final class RouteReconstructor {

    private RouteReconstructor() {}

    /**
     * Reconstructs an optimal route by following predecessor pointers from destination back to source.
     *
     * @param sourceNode      Origin node; must not be null.
     * @param destinationNode Destination node; must not be null.
     * @param predecessorNode Map of node ID to its parent predecessor node.
     * @param predecessorEdge Map of node ID to the incoming edge traversed.
     * @return Immutable RouteResult containing the complete path and exact aggregated costs.
     * @throws IllegalStateException if a cycle is detected during path reconstruction.
     */
    public static RouteResult reconstruct(Node sourceNode,
                                          Node destinationNode,
                                          Map<String, Node> predecessorNode,
                                          Map<String, Edge> predecessorEdge) {
        Objects.requireNonNull(sourceNode, "Source node must not be null");
        Objects.requireNonNull(destinationNode, "Destination node must not be null");

        String srcId = sourceNode.getId();
        String dstId = destinationNode.getId();

        // Edge case: Source is identical to Destination
        if (srcId.equals(dstId)) {
            return RouteResult.singleNode(sourceNode);
        }

        // Unreachable if destination has no recorded predecessor
        if (predecessorNode == null || !predecessorNode.containsKey(dstId)) {
            return RouteResult.unreachable(srcId, dstId);
        }

        LinkedList<Node> reconstructedPath = new LinkedList<>();
        LinkedList<Edge> reconstructedEdges = new LinkedList<>();
        Set<String> visitedInBacktrace = new HashSet<>();

        Node curr = destinationNode;
        while (curr != null && !curr.getId().equals(srcId)) {
            String currId = curr.getId();

            // Cycle prevention check
            if (!visitedInBacktrace.add(currId)) {
                throw new IllegalStateException("Accidental cycle detected during route reconstruction at node: " + currId);
            }

            reconstructedPath.addFirst(curr);
            if (predecessorEdge != null) {
                Edge edgeToCurr = predecessorEdge.get(currId);
                if (edgeToCurr != null) {
                    reconstructedEdges.addFirst(edgeToCurr);
                }
            }

            curr = predecessorNode.get(currId);
        }

        // Disconnected or broken predecessor link
        if (curr == null || !curr.getId().equals(srcId)) {
            return RouteResult.unreachable(srcId, dstId);
        }

        // Prepend source node
        reconstructedPath.addFirst(sourceNode);

        // Sum exact edge distance and travel time
        double totalDistance = 0.0;
        double totalTime = 0.0;
        for (Edge edge : reconstructedEdges) {
            totalDistance += edge.getDistanceKm();
            totalTime += edge.getTravelTimeMinutes();
        }

        return RouteResult.reachable(reconstructedPath, reconstructedEdges, totalDistance, totalTime);
    }
}
