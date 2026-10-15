package com.resqroute.algorithm;

import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dijkstra Routing Algorithm Emergency Tests")
class DijkstraRoutingAlgorithmTest {

    private RoadGraph graph;
    private DijkstraRoutingAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        algorithm = new DijkstraRoutingAlgorithm();
    }

    @Test
    @DisplayName("Algorithm strictly chooses the fastest path even if geographic distance is longer")
    void fastestRouteOverShortestDistance() {
        // Node setup
        Node start = new Node("START", 12.0, 77.0, "Station");
        Node midA = new Node("MID_A", 12.1, 77.1, "Congested Market");
        Node midB = new Node("MID_B", 12.2, 77.2, "Express Highway Entry");
        Node goal = new Node("GOAL", 12.3, 77.3, "Hospital");

        graph.addNode(start);
        graph.addNode(midA);
        graph.addNode(midB);
        graph.addNode(goal);

        // Path 1 (Short distance, heavy traffic):
        // Total: 3.0 km, 25.0 minutes
        graph.addEdge("START", "MID_A", 1.0, 10.0, "Market Approach");
        graph.addEdge("MID_A", "GOAL", 2.0, 15.0, "Narrow Alley");

        // Path 2 (Longer distance, free-flow highway):
        // Total: 10.0 km, 8.0 minutes
        graph.addEdge("START", "MID_B", 4.0, 3.0, "Highway Connector");
        graph.addEdge("MID_B", "GOAL", 6.0, 5.0, "Expressway");

        RouteResult result = algorithm.findFastestRoute(graph, "START", "GOAL");

        assertTrue(result.isReachable());
        // Verify optimal time chosen
        assertEquals(8.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(10.0, result.getTotalDistanceKm(), 1e-4);

        // Verify correct path reconstruction
        List<String> expectedPath = List.of("START", "MID_B", "GOAL");
        List<String> actualPath = result.getPath().stream().map(Node::getId).toList();
        assertEquals(expectedPath, actualPath);
    }

    @Test
    @DisplayName("Route reconstruction produces the correct node and edge sequence")
    void routeReconstructionAndAccumulation() {
        Node n1 = new Node("N1", 10.0, 20.0);
        Node n2 = new Node("N2", 10.1, 20.1);
        Node n3 = new Node("N3", 10.2, 20.2);
        Node n4 = new Node("N4", 10.3, 20.3);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(n3);
        graph.addNode(n4);

        graph.addEdge("N1", "N2", 2.5, 4.0, "Street 1");
        graph.addEdge("N2", "N3", 3.5, 5.5, "Street 2");
        graph.addEdge("N3", "N4", 1.0, 2.0, "Street 3");

        RouteResult result = algorithm.findFastestRoute(graph, "N1", "N4");

        assertTrue(result.isReachable());
        assertEquals(7.0, result.getTotalDistanceKm(), 1e-4);
        assertEquals(11.5, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(List.of("N1", "N2", "N3", "N4"), result.getPath().stream().map(Node::getId).toList());
        assertEquals(3, result.getEdges().size());
        assertEquals("Street 1", result.getEdges().get(0).getRoadName());
        assertEquals("Street 2", result.getEdges().get(1).getRoadName());
        assertEquals("Street 3", result.getEdges().get(2).getRoadName());
    }

    @Test
    @DisplayName("Unreachable destination returns unreachable RouteResult with correct flags")
    void unreachableDestination() {
        Node n1 = new Node("N1", 10.0, 20.0);
        Node n2 = new Node("N2", 10.1, 20.1);
        Node isolated = new Node("ISOLATED", 10.5, 20.5);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(isolated);

        graph.addEdge("N1", "N2", 5.0, 5.0);

        RouteResult result = algorithm.findFastestRoute(graph, "N1", "ISOLATED");

        assertFalse(result.isReachable());
        assertTrue(result.getPath().isEmpty());
        assertTrue(Double.isInfinite(result.getTotalTravelTimeMinutes()));
        assertTrue(Double.isInfinite(result.getTotalDistanceKm()));
        assertEquals("N1", result.getSourceId());
        assertEquals("ISOLATED", result.getDestinationId());
    }

    @Test
    @DisplayName("Source equals destination returns single-node route with 0 distance and 0 time")
    void sourceEqualsDestination() {
        Node n1 = new Node("STATION", 12.97, 77.59, "HQ");
        graph.addNode(n1);

        RouteResult result = algorithm.findFastestRoute(graph, "STATION", "STATION");

        assertTrue(result.isReachable());
        assertEquals(0.0, result.getTotalTravelTimeMinutes(), 1e-6);
        assertEquals(0.0, result.getTotalDistanceKm(), 1e-6);
        assertEquals(1, result.getNodeCount());
        assertEquals(n1, result.getPath().getFirst());
    }

    @Test
    @DisplayName("Algorithm rejects queries with invalid or non-existent node IDs")
    void invalidNodeIdsThrow() {
        Node n1 = new Node("N1", 10.0, 20.0);
        graph.addNode(n1);

        assertThrows(NodeNotFoundException.class, () -> algorithm.findFastestRoute(graph, "N1", "MISSING"));
        assertThrows(NodeNotFoundException.class, () -> algorithm.findFastestRoute(graph, "MISSING", "N1"));
        assertThrows(IllegalArgumentException.class, () -> algorithm.findFastestRoute(graph, null, "N1"));
        assertThrows(IllegalArgumentException.class, () -> algorithm.findFastestRoute(graph, "   ", "N1"));
        assertThrows(NullPointerException.class, () -> algorithm.findFastestRoute(null, "N1", "N1"));
    }

    @Test
    @DisplayName("Handles multiple alternative paths in a complex grid/diamond graph")
    void multipleAlternativePaths() {
        // Build diamond with multiple branching options:
        // S -> A -> D
        // S -> B -> D
        // S -> C -> D
        // A -> B cross connection
        Node s = new Node("S", 1.0, 1.0);
        Node a = new Node("A", 1.0, 2.0);
        Node b = new Node("B", 2.0, 1.0);
        Node c = new Node("C", 2.0, 2.0);
        Node d = new Node("D", 3.0, 3.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);

        // Path via A: 10 + 15 = 25 mins
        graph.addEdge("S", "A", 2.0, 10.0);
        graph.addEdge("A", "D", 3.0, 15.0);

        // Path via B: 5 + 18 = 23 mins
        graph.addEdge("S", "B", 4.0, 5.0);
        graph.addEdge("B", "D", 4.0, 18.0);

        // Path via C: 8 + 6 = 14 mins (Optimal!)
        graph.addEdge("S", "C", 5.0, 8.0);
        graph.addEdge("C", "D", 4.0, 6.0);

        // Sub-branch S -> A -> B -> D: 10 + 2 + 18 = 30 mins
        graph.addEdge("A", "B", 1.0, 2.0);

        RouteResult result = algorithm.findFastestRoute(graph, "S", "D");

        assertTrue(result.isReachable());
        assertEquals(14.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(9.0, result.getTotalDistanceKm(), 1e-4);
        assertEquals(List.of("S", "C", "D"), result.getPath().stream().map(Node::getId).toList());
    }

    @Test
    @DisplayName("Handles road graphs with directed cycles without infinite loops")
    void cyclicGraphHandling() {
        Node n1 = new Node("1", 0.0, 0.0);
        Node n2 = new Node("2", 0.0, 1.0);
        Node n3 = new Node("3", 1.0, 1.0);
        Node target = new Node("T", 2.0, 2.0);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(n3);
        graph.addNode(target);

        // Cycle 1 -> 2 -> 3 -> 1
        graph.addEdge("1", "2", 1.0, 3.0);
        graph.addEdge("2", "3", 1.0, 3.0);
        graph.addEdge("3", "1", 1.0, 3.0);

        // Exit from cycle: 3 -> T
        graph.addEdge("3", "T", 2.0, 4.0);

        RouteResult result = algorithm.findFastestRoute(graph, "1", "T");

        assertTrue(result.isReachable());
        assertEquals(10.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(4.0, result.getTotalDistanceKm(), 1e-4);
        assertEquals(List.of("1", "2", "3", "T"), result.getPath().stream().map(Node::getId).toList());
    }

    @Test
    @DisplayName("Algorithm adapts dynamically when a road is removed (emergency roadblock / detour)")
    void dynamicReroutingAfterRoadRemoval() {
        Node s = new Node("S", 0.0, 0.0);
        Node p1 = new Node("P1", 1.0, 0.0);
        Node p2 = new Node("P2", 0.0, 1.0);
        Node d = new Node("D", 1.0, 1.0);

        graph.addNode(s);
        graph.addNode(p1);
        graph.addNode(p2);
        graph.addNode(d);

        // Path 1 (Primary fast route): S -> P1 -> D = 4 + 4 = 8 mins
        graph.addEdge("S", "P1", 2.0, 4.0);
        graph.addEdge("P1", "D", 2.0, 4.0);

        // Path 2 (Secondary detour): S -> P2 -> D = 5 + 7 = 12 mins
        graph.addEdge("S", "P2", 3.0, 5.0);
        graph.addEdge("P2", "D", 4.0, 7.0);

        // First check: picks Path 1
        RouteResult initialRoute = algorithm.findFastestRoute(graph, "S", "D");
        assertTrue(initialRoute.isReachable());
        assertEquals(8.0, initialRoute.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(List.of("S", "P1", "D"), initialRoute.getPath().stream().map(Node::getId).toList());

        // Simulate road closure by removing edge P1 -> D
        assertTrue(graph.removeEdge("P1", "D"));

        // Re-query: should dynamically recalculate and route via Path 2
        RouteResult reroutedResult = algorithm.findFastestRoute(graph, "S", "D");
        assertTrue(reroutedResult.isReachable());
        assertEquals(12.0, reroutedResult.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(List.of("S", "P2", "D"), reroutedResult.getPath().stream().map(Node::getId).toList());
    }

    @Test
    @DisplayName("Algorithm handles intermediate node removal and discovers disconnection")
    void dynamicReroutingAfterNodeRemoval() {
        Node s = new Node("S", 0.0, 0.0);
        Node mid = new Node("MID", 1.0, 1.0);
        Node d = new Node("D", 2.0, 2.0);

        graph.addNode(s);
        graph.addNode(mid);
        graph.addNode(d);

        graph.addEdge("S", "MID", 2.0, 3.0);
        graph.addEdge("MID", "D", 2.0, 3.0);

        RouteResult route1 = algorithm.findFastestRoute(graph, "S", "D");
        assertTrue(route1.isReachable());

        // Remove intermediate node completely
        assertTrue(graph.removeNode("MID"));

        RouteResult routeAfterRemoval = algorithm.findFastestRoute(graph, "S", "D");
        assertFalse(routeAfterRemoval.isReachable(), "After removing the only bridge node, destination should be unreachable");
    }
}

