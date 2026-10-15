package com.resqroute.algorithm;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: Randomized Stress & Reference Verifier Tests")
class DijkstraRandomizedVerificationTest {

    private final DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();

    /**
     * Independent reference ground-truth solver using exhaustive all-simple-paths search.
     * Guaranteed to find the global optimum without relying on PriorityQueue or Dijkstra heuristics.
     */
    static class ReferenceSolver {
        record Solution(boolean reachable, double totalTravelTime, double totalDistance, List<String> pathNodeIds) {}

        static Solution solveExhaustively(RoadGraph graph, String sourceId, String destinationId) {
            if (sourceId.equals(destinationId)) {
                return new Solution(true, 0.0, 0.0, List.of(sourceId));
            }

            List<List<Edge>> allPaths = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            visited.add(sourceId);

            dfsPaths(graph, sourceId, destinationId, visited, new ArrayList<>(), allPaths);

            if (allPaths.isEmpty()) {
                return new Solution(false, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, List.of());
            }

            List<Edge> bestPath = null;
            double bestTime = Double.POSITIVE_INFINITY;
            double bestDist = Double.POSITIVE_INFINITY;

            for (List<Edge> path : allPaths) {
                double time = 0.0;
                double dist = 0.0;
                for (Edge edge : path) {
                    time += edge.getTravelTimeMinutes();
                    dist += edge.getDistanceKm();
                }

                boolean isFaster = time < bestTime - 1e-9;
                boolean isSameTimeShorterDist = Math.abs(time - bestTime) <= 1e-9 && dist < bestDist - 1e-9;

                if (isFaster || isSameTimeShorterDist) {
                    bestTime = time;
                    bestDist = dist;
                    bestPath = path;
                }
            }

            List<String> nodeIds = new ArrayList<>();
            nodeIds.add(sourceId);
            for (Edge edge : bestPath) {
                nodeIds.add(edge.getDestination().getId());
            }

            return new Solution(true, bestTime, bestDist, nodeIds);
        }

        private static void dfsPaths(RoadGraph graph,
                                     String current,
                                     String target,
                                     Set<String> visited,
                                     List<Edge> currentPath,
                                     List<List<Edge>> allPaths) {
            if (current.equals(target)) {
                allPaths.add(new ArrayList<>(currentPath));
                return;
            }

            for (Edge edge : graph.getOutgoingEdges(current)) {
                String next = edge.getDestination().getId();
                if (!visited.contains(next)) {
                    visited.add(next);
                    currentPath.add(edge);

                    dfsPaths(graph, next, target, visited, currentPath, allPaths);

                    currentPath.removeLast();
                    visited.remove(next);
                }
            }
        }
    }

    @RepeatedTest(value = 50, name = "Randomized graph test run {currentRepetition} of {totalRepetitions}")
    @DisplayName("Dijkstra must match exhaustive reference solver on random directed graphs")
    void testAgainstExhaustiveReferenceOnRandomGraphs() {
        Random random = new Random();
        int nodeCount = 4 + random.nextInt(4); // 4 to 7 nodes for fast exhaustive reference search

        RoadGraph graph = new RoadGraph();
        List<String> nodeIds = new ArrayList<>();

        for (int i = 0; i < nodeCount; i++) {
            String id = "N" + i;
            nodeIds.add(id);
            graph.addNode(new Node(id, 12.0 + (i * 0.01), 77.0 + (i * 0.01), "Node " + id));
        }

        // Add random directed edges
        int edgeCount = nodeCount + random.nextInt(nodeCount * 2);
        for (int i = 0; i < edgeCount; i++) {
            int srcIdx = random.nextInt(nodeCount);
            int dstIdx = random.nextInt(nodeCount);
            if (srcIdx != dstIdx) {
                String u = nodeIds.get(srcIdx);
                String v = nodeIds.get(dstIdx);
                if (!graph.containsEdge(u, v)) {
                    double dist = 1.0 + random.nextInt(20);
                    double time = 1.0 + random.nextInt(30);
                    graph.addEdge(u, v, dist, time, u + "->" + v);
                }
            }
        }

        // Pick random source and destination
        String source = nodeIds.get(random.nextInt(nodeCount));
        String destination = nodeIds.get(random.nextInt(nodeCount));

        RouteResult dijkstraResult = dijkstra.findFastestRoute(graph, source, destination);
        ReferenceSolver.Solution refResult = ReferenceSolver.solveExhaustively(graph, source, destination);

        assertEquals(refResult.reachable(), dijkstraResult.isReachable(),
                "Reachability mismatch for " + source + " -> " + destination);

        if (refResult.reachable()) {
            assertEquals(refResult.totalTravelTime(), dijkstraResult.getTotalTravelTimeMinutes(), 1e-4,
                    "Total travel time mismatch for " + source + " -> " + destination);

            assertEquals(refResult.totalDistance(), dijkstraResult.getTotalDistanceKm(), 1e-4,
                    "Total distance mismatch for " + source + " -> " + destination);

            // Verify path validity: starts at source, ends at destination, each step is connected
            List<Node> path = dijkstraResult.getPath();
            assertFalse(path.isEmpty());
            assertEquals(source, path.getFirst().getId());
            assertEquals(destination, path.getLast().getId());

            for (int i = 0; i < path.size() - 1; i++) {
                String from = path.get(i).getId();
                String to = path.get(i + 1).getId();
                assertTrue(graph.containsEdge(from, to),
                        "Path step " + from + " -> " + to + " does not exist in graph");
            }
        }
    }

    @Test
    @DisplayName("Tie-breaker test: competing routes with identical travel time break tie by shorter distance")
    void competingRoutesWithIdenticalTimeBreakTieByDistance() {
        RoadGraph graph = new RoadGraph();

        Node start = new Node("START", 0.0, 0.0);
        Node midA = new Node("MID_A", 1.0, 0.0);
        Node midB = new Node("MID_B", 0.0, 1.0);
        Node goal = new Node("GOAL", 1.0, 1.0);

        graph.addNode(start);
        graph.addNode(midA);
        graph.addNode(midB);
        graph.addNode(goal);

        // Path A: travel time = 5.0 + 5.0 = 10.0 min, distance = 8.0 + 8.0 = 16.0 km
        graph.addEdge("START", "MID_A", 8.0, 5.0);
        graph.addEdge("MID_A", "GOAL", 8.0, 5.0);

        // Path B: travel time = 5.0 + 5.0 = 10.0 min, distance = 3.0 + 3.0 = 6.0 km (SHORTER DISTANCE!)
        graph.addEdge("START", "MID_B", 3.0, 5.0);
        graph.addEdge("MID_B", "GOAL", 3.0, 5.0);

        RouteResult result = dijkstra.findFastestRoute(graph, "START", "GOAL");

        assertTrue(result.isReachable());
        assertEquals(10.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(6.0, result.getTotalDistanceKm(), 1e-4, "Must pick shorter distance path on equal travel time");
        assertEquals(List.of("START", "MID_B", "GOAL"), result.getPath().stream().map(Node::getId).toList());
    }

    @Test
    @DisplayName("Handles fully disconnected graph with multiple isolated nodes")
    void fullyDisconnectedGraph() {
        RoadGraph graph = new RoadGraph();
        for (int i = 1; i <= 5; i++) {
            graph.addNode(new Node("ISO_" + i, (double) i, (double) i));
        }

        RouteResult result = dijkstra.findFastestRoute(graph, "ISO_1", "ISO_5");
        assertFalse(result.isReachable());
        assertTrue(result.getPath().isEmpty());
    }

    @Test
    @DisplayName("Handles dense complete graph (K5) with non-adjacent penalties")
    void denseCompleteGraph() {
        RoadGraph graph = new RoadGraph();
        int n = 5;
        for (int i = 0; i < n; i++) {
            graph.addNode(new Node("K" + i, (double) i, (double) i));
        }

        // Add directed edges between every pair
        // Adjacent step (j == i + 1): 2 km, 3 minutes
        // Non-adjacent jumps: heavy traffic / delay
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double dist = Math.abs(i - j) * 2.0;
                    double time = (j == i + 1) ? 3.0 : Math.abs(i - j) * 15.0;
                    graph.addEdge("K" + i, "K" + j, dist, time);
                }
            }
        }

        RouteResult result = dijkstra.findFastestRoute(graph, "K0", "K4");
        assertTrue(result.isReachable());
        // Sequential path: K0 -> K1 -> K2 -> K3 -> K4 has 4 steps * 3.0 min = 12.0 min
        // Direct jump K0 -> K4 has 4 * 15.0 min = 60.0 min
        assertEquals(12.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(8.0, result.getTotalDistanceKm(), 1e-4);
        assertEquals(List.of("K0", "K1", "K2", "K3", "K4"), result.getPath().stream().map(Node::getId).toList());
    }
}

