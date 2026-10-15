package com.resqroute.algorithm;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: Yen's K-Shortest Loopless Paths Algorithm Tests")
class YenKShortestPathsAlgorithmTest {

    private RoadGraph graph;
    private YenKShortestPathsAlgorithm yenAlgorithm;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        yenAlgorithm = new YenKShortestPathsAlgorithm();
    }

    @Test
    @DisplayName("Invalid K values (< 1) must throw IllegalArgumentException")
    void invalidKValuesThrow() {
        graph.addNode(new Node("A", 0.0, 0.0));
        graph.addNode(new Node("B", 1.0, 1.0));
        graph.addEdge("A", "B", 1.0, 1.0);

        assertThrows(IllegalArgumentException.class, () -> yenAlgorithm.findKShortestPaths(graph, "A", "B", 0));
        assertThrows(IllegalArgumentException.class, () -> yenAlgorithm.findKShortestPaths(graph, "A", "B", -5));
    }

    @Test
    @DisplayName("Source equals destination returns exactly 1 single-node route even when K > 1")
    void sourceEqualsDestination() {
        graph.addNode(new Node("A", 0.0, 0.0));

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "A", "A", 3);
        assertEquals(1, routes.size());
        assertTrue(routes.getFirst().isReachable());
        assertEquals(List.of("A"), routes.getFirst().getNodeIds());
        assertEquals(0.0, routes.getFirst().getTotalTravelTimeMinutes());
    }

    @Test
    @DisplayName("Disconnected graph returns empty list of routes")
    void disconnectedGraphReturnsEmpty() {
        graph.addNode(new Node("A", 0.0, 0.0));
        graph.addNode(new Node("B", 1.0, 1.0));

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "A", "B", 3);
        assertTrue(routes.isEmpty());
    }

    @Test
    @DisplayName("Finds exactly one route when only one route physically exists (Fewer than K routes)")
    void singleAvailableRouteWhenKIsLarge() {
        graph.addNode(new Node("A", 0.0, 0.0));
        graph.addNode(new Node("B", 1.0, 1.0));
        graph.addEdge("A", "B", 5.0, 10.0);

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "A", "B", 5);
        assertEquals(1, routes.size());
        assertEquals(List.of("A", "B"), routes.getFirst().getNodeIds());
        assertEquals(10.0, routes.getFirst().getTotalTravelTimeMinutes());
    }

    @Test
    @DisplayName("Discovers and ranks multiple alternative routes by travel time on a multi-path diamond")
    void multipleAlternativeRoutesRankedByTravelTime() {
        // Multi-path graph from S to D:
        // Path 1: S -> A -> D (Time: 3 + 4 = 7 min, Dist: 2 + 3 = 5 km)
        // Path 2: S -> B -> D (Time: 5 + 4 = 9 min, Dist: 4 + 4 = 8 km)
        // Path 3: S -> C -> D (Time: 6 + 6 = 12 min, Dist: 5 + 5 = 10 km)
        // Cross edge: A -> B (Time: 1 min, Dist: 1 km)
        // Path 4: S -> A -> B -> D (Time: 3 + 1 + 4 = 8 min, Dist: 2 + 1 + 4 = 7 km)
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 0.0);
        Node b = new Node("B", 0.0, 1.0);
        Node c = new Node("C", -1.0, 0.0);
        Node d = new Node("D", 2.0, 2.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);

        graph.addEdge("S", "A", 2.0, 3.0);
        graph.addEdge("A", "D", 3.0, 4.0);

        graph.addEdge("S", "B", 4.0, 5.0);
        graph.addEdge("B", "D", 4.0, 4.0);

        graph.addEdge("S", "C", 5.0, 6.0);
        graph.addEdge("C", "D", 5.0, 6.0);

        graph.addEdge("A", "B", 1.0, 1.0);

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "S", "D", 4);

        assertEquals(4, routes.size());

        // Rank 1: S -> A -> D (7.0 min)
        assertEquals(List.of("S", "A", "D"), routes.get(0).getNodeIds());
        assertEquals(7.0, routes.get(0).getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(5.0, routes.get(0).getTotalDistanceKm(), 1e-4);

        // Rank 2: S -> A -> B -> D (8.0 min)
        assertEquals(List.of("S", "A", "B", "D"), routes.get(1).getNodeIds());
        assertEquals(8.0, routes.get(1).getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(7.0, routes.get(1).getTotalDistanceKm(), 1e-4);

        // Rank 3: S -> B -> D (9.0 min)
        assertEquals(List.of("S", "B", "D"), routes.get(2).getNodeIds());
        assertEquals(9.0, routes.get(2).getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(8.0, routes.get(2).getTotalDistanceKm(), 1e-4);

        // Rank 4: S -> C -> D (12.0 min)
        assertEquals(List.of("S", "C", "D"), routes.get(3).getNodeIds());
        assertEquals(12.0, routes.get(3).getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(10.0, routes.get(3).getTotalDistanceKm(), 1e-4);
    }

    @Test
    @DisplayName("Guarantees all returned routes are strictly loopless (simple paths)")
    void loopPrevention() {
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 0.0);
        Node b = new Node("B", 1.0, 1.0);
        Node d = new Node("D", 2.0, 2.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(d);

        // Cycle between A and B
        graph.addEdge("S", "A", 1.0, 2.0);
        graph.addEdge("A", "B", 1.0, 2.0);
        graph.addEdge("B", "A", 1.0, 2.0);
        graph.addEdge("B", "D", 2.0, 3.0);
        graph.addEdge("A", "D", 4.0, 8.0);

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "S", "D", 5);

        for (RouteResult route : routes) {
            List<String> nodeIds = route.getNodeIds();
            long uniqueCount = nodeIds.stream().distinct().count();
            assertEquals(nodeIds.size(), uniqueCount, "Path must not contain any repeated nodes (loops): " + nodeIds);
        }
    }

    @Test
    @DisplayName("Prevents duplicate routes: all returned routes have distinct node sequences")
    void duplicateRoutePrevention() {
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 0.0);
        Node d = new Node("D", 2.0, 0.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(d);

        graph.addEdge("S", "A", 2.0, 3.0);
        graph.addEdge("A", "D", 3.0, 4.0);

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "S", "D", 10);
        assertEquals(1, routes.size());
    }

    @Test
    @DisplayName("Directed road restrictions are strictly honored")
    void directedRoadRestrictions() {
        Node a = new Node("A", 0.0, 0.0);
        Node b = new Node("B", 1.0, 0.0);
        Node c = new Node("C", 2.0, 0.0);

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        // Directed road A -> B -> C. No reverse C -> B.
        graph.addEdge("A", "B", 1.0, 2.0);
        graph.addEdge("B", "C", 1.0, 2.0);

        List<RouteResult> forwardRoutes = yenAlgorithm.findKShortestPaths(graph, "A", "C", 2);
        assertEquals(1, forwardRoutes.size());

        List<RouteResult> reverseRoutes = yenAlgorithm.findKShortestPaths(graph, "C", "A", 2);
        assertTrue(reverseRoutes.isEmpty(), "Reverse direction must be unreachable on directed edges");
    }

    @Test
    @DisplayName("Original RoadGraph remains strictly unchanged after K-shortest paths computation")
    void originalGraphUnchangedAfterExecution() {
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 0.0);
        Node b = new Node("B", 0.0, 1.0);
        Node d = new Node("D", 1.0, 1.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(d);

        graph.addEdge("S", "A", 2.0, 3.0);
        graph.addEdge("A", "D", 2.0, 3.0);
        graph.addEdge("S", "B", 3.0, 4.0);
        graph.addEdge("B", "D", 3.0, 4.0);

        int initialNodeCount = graph.getNodeCount();
        int initialEdgeCount = graph.getEdgeCount();

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "S", "D", 3);
        assertEquals(2, routes.size());

        // Invariant: original graph is never mutated
        assertEquals(initialNodeCount, graph.getNodeCount());
        assertEquals(initialEdgeCount, graph.getEdgeCount());
        assertTrue(graph.containsEdge("S", "A"));
        assertTrue(graph.containsEdge("A", "D"));
        assertTrue(graph.containsEdge("S", "B"));
        assertTrue(graph.containsEdge("B", "D"));
    }

    @Test
    @DisplayName("Handles competing routes with equal travel times, ranking by distance tie-break")
    void equalTravelTimesTieBreakByDistance() {
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 0.0);
        Node b = new Node("B", 0.0, 1.0);
        Node d = new Node("D", 1.0, 1.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(d);

        // Path A: time 5.0 + 5.0 = 10.0 min, dist 4.0 + 4.0 = 8.0 km
        graph.addEdge("S", "A", 4.0, 5.0);
        graph.addEdge("A", "D", 4.0, 5.0);

        // Path B: time 5.0 + 5.0 = 10.0 min, dist 2.0 + 2.0 = 4.0 km (SHORTER DISTANCE)
        graph.addEdge("S", "B", 2.0, 5.0);
        graph.addEdge("B", "D", 2.0, 5.0);

        List<RouteResult> routes = yenAlgorithm.findKShortestPaths(graph, "S", "D", 2);
        assertEquals(2, routes.size());

        // Rank 1: S -> B -> D (10.0 min, 4.0 km)
        assertEquals(List.of("S", "B", "D"), routes.get(0).getNodeIds());
        assertEquals(10.0, routes.get(0).getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(4.0, routes.get(0).getTotalDistanceKm(), 1e-4);

        // Rank 2: S -> A -> D (10.0 min, 8.0 km)
        assertEquals(List.of("S", "A", "D"), routes.get(1).getNodeIds());
        assertEquals(10.0, routes.get(1).getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(8.0, routes.get(1).getTotalDistanceKm(), 1e-4);
    }
}
