package com.resqroute.algorithm;

import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 4: A* Routing Algorithm & Equivalence to Dijkstra Tests")
class AStarRoutingAlgorithmTest {

    private RoadGraph graph;
    private DijkstraRoutingAlgorithm dijkstra;
    private AStarRoutingAlgorithm astar;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        dijkstra = new DijkstraRoutingAlgorithm();
        astar = new AStarRoutingAlgorithm();
    }

    @Test
    @DisplayName("A* correctly selects fastest route when geographic distance is longer, matching Dijkstra")
    void fastestRouteOverShortestDistanceMatchesDijkstra() {
        Node start = new Node("START", 12.0, 77.0, "Station");
        Node midA = new Node("MID_A", 12.1, 77.1, "Congested Market");
        Node midB = new Node("MID_B", 12.2, 77.2, "Express Highway Entry");
        Node goal = new Node("GOAL", 12.3, 77.3, "Hospital");

        graph.addNode(start);
        graph.addNode(midA);
        graph.addNode(midB);
        graph.addNode(goal);

        // Path 1 (Short distance, heavy traffic): 3.0 km, 25.0 minutes
        graph.addEdge("START", "MID_A", 1.0, 10.0);
        graph.addEdge("MID_A", "GOAL", 2.0, 15.0);

        // Path 2 (Longer distance, free-flow highway): 10.0 km, 8.0 minutes
        graph.addEdge("START", "MID_B", 4.0, 3.0);
        graph.addEdge("MID_B", "GOAL", 6.0, 5.0);

        RouteResult dijkstraResult = dijkstra.findFastestRoute(graph, "START", "GOAL");
        RouteResult astarResult = astar.findFastestRoute(graph, "START", "GOAL");

        assertTrue(astarResult.isReachable());
        assertEquals(dijkstraResult.getTotalTravelTimeMinutes(), astarResult.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(dijkstraResult.getTotalDistanceKm(), astarResult.getTotalDistanceKm(), 1e-4);
        assertEquals(dijkstraResult.getPath(), astarResult.getPath());
    }

    @Test
    @DisplayName("Source equals destination returns immediate single-node route with 0 cost")
    void sourceEqualsDestination() {
        Node hq = new Node("HQ", 12.9716, 77.5946, "Ambulance HQ");
        graph.addNode(hq);

        RouteResult result = astar.findFastestRoute(graph, "HQ", "HQ");

        assertTrue(result.isReachable());
        assertEquals(0.0, result.getTotalTravelTimeMinutes(), 1e-6);
        assertEquals(0.0, result.getTotalDistanceKm(), 1e-6);
        assertEquals(1, result.getNodeCount());
        assertEquals(hq, result.getPath().getFirst());
    }

    @Test
    @DisplayName("Unreachable destination returns unreachable RouteResult matching Dijkstra")
    void unreachableDestination() {
        Node n1 = new Node("N1", 10.0, 20.0);
        Node n2 = new Node("N2", 10.1, 20.1);
        Node isolated = new Node("ISOLATED", 10.5, 20.5);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(isolated);
        graph.addEdge("N1", "N2", 5.0, 5.0);

        RouteResult dijkstraResult = dijkstra.findFastestRoute(graph, "N1", "ISOLATED");
        RouteResult astarResult = astar.findFastestRoute(graph, "N1", "ISOLATED");

        assertFalse(astarResult.isReachable());
        assertEquals(dijkstraResult.isReachable(), astarResult.isReachable());
        assertTrue(astarResult.getPath().isEmpty());
        assertTrue(Double.isInfinite(astarResult.getTotalTravelTimeMinutes()));
    }

    @Test
    @DisplayName("Invalid or non-existent node IDs throw expected exceptions")
    void invalidNodeIdsThrow() {
        Node n1 = new Node("N1", 10.0, 20.0);
        graph.addNode(n1);

        assertThrows(NodeNotFoundException.class, () -> astar.findFastestRoute(graph, "N1", "MISSING"));
        assertThrows(NodeNotFoundException.class, () -> astar.findFastestRoute(graph, "MISSING", "N1"));
        assertThrows(IllegalArgumentException.class, () -> astar.findFastestRoute(graph, null, "N1"));
        assertThrows(IllegalArgumentException.class, () -> astar.findFastestRoute(graph, "   ", "N1"));
        assertThrows(NullPointerException.class, () -> astar.findFastestRoute(null, "N1", "N1"));
    }

    @Test
    @DisplayName("Zero heuristic fallback executes and matches Dijkstra identically")
    void zeroHeuristicMatchesDijkstra() {
        AStarRoutingAlgorithm zeroAstar = new AStarRoutingAlgorithm(TravelTimeHeuristic.ZERO);

        Node s = new Node("S", 1.0, 1.0);
        Node a = new Node("A", 1.0, 2.0);
        Node b = new Node("B", 2.0, 1.0);
        Node d = new Node("D", 3.0, 3.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(d);

        graph.addEdge("S", "A", 2.0, 10.0);
        graph.addEdge("A", "D", 3.0, 15.0);
        graph.addEdge("S", "B", 4.0, 5.0);
        graph.addEdge("B", "D", 4.0, 6.0);

        RouteResult dResult = dijkstra.findFastestRoute(graph, "S", "D");
        RouteResult aResult = zeroAstar.findFastestRoute(graph, "S", "D");

        assertEquals(dResult.getTotalTravelTimeMinutes(), aResult.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(dResult.getTotalDistanceKm(), aResult.getTotalDistanceKm(), 1e-4);
        assertEquals(dResult.getPath(), aResult.getPath());
    }

    @Test
    @DisplayName("Heuristic is strictly admissible: h(u, goal) never exceeds actual minimum travel time")
    void heuristicAdmissibilityVerification() {
        Node n0 = new Node("N0", 12.9716, 77.5946);
        Node n1 = new Node("N1", 12.9780, 77.6000);
        Node n2 = new Node("N2", 12.9850, 77.6100);
        Node goal = new Node("GOAL", 12.9900, 77.6200);

        graph.addNode(n0);
        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(goal);

        graph.addEdge("N0", "N1", 1.5, 3.0);
        graph.addEdge("N1", "N2", 2.0, 4.0);
        graph.addEdge("N2", "GOAL", 1.8, 3.5);

        double maxSpeed = 120.0;
        TravelTimeHeuristic h = TravelTimeHeuristic.haversine(maxSpeed);

        for (Node node : List.of(n0, n1, n2)) {
            RouteResult actualFastest = dijkstra.findFastestRoute(graph, node.getId(), "GOAL");
            double estimatedTime = h.estimateTravelTimeMinutes(node, goal);

            assertTrue(estimatedTime <= actualFastest.getTotalTravelTimeMinutes() + 1e-9,
                    String.format("Heuristic %.4f overestimated actual time %.4f from %s to GOAL",
                            estimatedTime, actualFastest.getTotalTravelTimeMinutes(), node.getId()));
        }
    }

    @RepeatedTest(value = 50, name = "A* vs Dijkstra equivalence on random graph {currentRepetition} of {totalRepetitions}")
    @DisplayName("A* must produce identical travel times and reachability as Dijkstra on random graphs")
    void testAStarEquivalenceToDijkstraOnRandomGraphs() {
        Random random = new Random();
        int nodeCount = 5 + random.nextInt(6); // 5 to 10 nodes

        RoadGraph testGraph = new RoadGraph();
        List<String> nodeIds = new java.util.ArrayList<>();

        for (int i = 0; i < nodeCount; i++) {
            String id = "V" + i;
            nodeIds.add(id);
            // Realistic Bengaluru geo coordinates with small offsets
            testGraph.addNode(new Node(id, 12.90 + (i * 0.02) + (random.nextDouble() * 0.01),
                                           77.50 + (i * 0.02) + (random.nextDouble() * 0.01),
                                           "Vertex " + id));
        }

        int edgeCount = nodeCount * 2 + random.nextInt(nodeCount * 2);
        for (int i = 0; i < edgeCount; i++) {
            int srcIdx = random.nextInt(nodeCount);
            int dstIdx = random.nextInt(nodeCount);
            if (srcIdx != dstIdx) {
                String u = nodeIds.get(srcIdx);
                String v = nodeIds.get(dstIdx);
                if (!testGraph.containsEdge(u, v)) {
                    double dist = 1.0 + random.nextInt(15);
                    double time = 1.0 + random.nextInt(25);
                    testGraph.addEdge(u, v, dist, time);
                }
            }
        }

        String source = nodeIds.get(random.nextInt(nodeCount));
        String destination = nodeIds.get(random.nextInt(nodeCount));

        RouteResult dijkstraResult = dijkstra.findFastestRoute(testGraph, source, destination);
        RouteResult astarResult = astar.findFastestRoute(testGraph, source, destination);

        assertEquals(dijkstraResult.isReachable(), astarResult.isReachable(),
                "Reachability mismatch for " + source + " -> " + destination);

        if (dijkstraResult.isReachable()) {
            assertEquals(dijkstraResult.getTotalTravelTimeMinutes(), astarResult.getTotalTravelTimeMinutes(), 1e-4,
                    "Total travel time mismatch for " + source + " -> " + destination);

            assertEquals(dijkstraResult.getTotalDistanceKm(), astarResult.getTotalDistanceKm(), 1e-4,
                    "Total distance mismatch for " + source + " -> " + destination);
        }
    }
}
