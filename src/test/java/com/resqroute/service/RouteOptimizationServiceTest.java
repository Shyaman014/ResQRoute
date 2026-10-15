package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.exception.RouteNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RouteOptimizationService Orchestration Tests")
class RouteOptimizationServiceTest {

    private RoadGraph graph;
    private RouteOptimizationService service;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        graph.addNode(new Node("ORIGIN", 12.0, 77.0));
        graph.addNode(new Node("DEST", 12.1, 77.1));
        graph.addNode(new Node("ISOLATED", 12.2, 77.2));
        graph.addEdge("ORIGIN", "DEST", 5.0, 6.0, "Express Connector");

        service = new RouteOptimizationService(graph);
    }

    @Test
    @DisplayName("Service correctly delegates to default Dijkstra routing")
    void delegationToDijkstra() {
        RouteResult result = service.findFastestRoute("ORIGIN", "DEST");

        assertTrue(result.isReachable());
        assertEquals(6.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(5.0, result.getTotalDistanceKm(), 1e-4);
        assertEquals(2, result.getNodeCount());
    }

    @Test
    @DisplayName("findFastestRouteOrThrow succeeds for reachable paths")
    void findFastestRouteOrThrowSuccess() {
        RouteResult result = service.findFastestRouteOrThrow("ORIGIN", "DEST");
        assertNotNull(result);
        assertTrue(result.isReachable());
    }

    @Test
    @DisplayName("findFastestRouteOrThrow throws RouteNotFoundException when path is unreachable")
    void findFastestRouteOrThrowFailure() {
        RouteNotFoundException ex = assertThrows(RouteNotFoundException.class,
                () -> service.findFastestRouteOrThrow("ORIGIN", "ISOLATED"));

        assertEquals("ORIGIN", ex.getSourceId());
        assertEquals("ISOLATED", ex.getDestinationId());
    }

    @Test
    @DisplayName("Service constructor allows dependency injection of custom routing algorithms")
    void customAlgorithmInjection() {
        DijkstraRoutingAlgorithm customAlgo = new DijkstraRoutingAlgorithm();
        RouteOptimizationService customService = new RouteOptimizationService(graph, customAlgo);

        assertSame(customAlgo, customService.getRoutingAlgorithm());
        assertSame(graph, customService.getRoadGraph());
    }

    @Test
    @DisplayName("Service seamlessly supports A* routing strategy injection")
    void aStarRoutingStrategyInjection() {
        com.resqroute.algorithm.AStarRoutingAlgorithm astarAlgo = new com.resqroute.algorithm.AStarRoutingAlgorithm();
        RouteOptimizationService astarService = new RouteOptimizationService(graph, astarAlgo);

        RouteResult result = astarService.findFastestRoute("ORIGIN", "DEST");
        assertTrue(result.isReachable());
        assertEquals(6.0, result.getTotalTravelTimeMinutes(), 1e-4);
        assertSame(astarAlgo, astarService.getRoutingAlgorithm());
    }
}

