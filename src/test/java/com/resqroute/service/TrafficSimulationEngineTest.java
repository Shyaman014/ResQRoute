package com.resqroute.service;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.exception.InvalidCostException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: Dynamic Traffic Simulation & Rerouting Evaluation Tests")
class TrafficSimulationEngineTest {

    private RoadGraph graph;
    private TrafficSimulationEngine simulationEngine;
    private RouteOptimizationService routeService;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        simulationEngine = new TrafficSimulationEngine(graph, 42L); // Fixed seed for determinism
        routeService = new RouteOptimizationService(graph);
    }

    @ParameterizedTest
    @EnumSource(TrafficLevel.class)
    @DisplayName("Every TrafficLevel calculates expected travel time correctly based on base cost")
    void everyTrafficLevelCalculation(TrafficLevel level) {
        double baseTime = 10.0;
        TrafficCondition condition = TrafficCondition.of(level, "UNIT_TEST");

        assertEquals(level, condition.level());
        assertTrue(condition.delayMultiplier() >= 1.0);

        double effectiveTime = condition.calculateEffectiveTravelTime(baseTime);
        assertEquals(baseTime * level.getDefaultDelayMultiplier(), effectiveTime, 1e-4);
    }

    @Test
    @DisplayName("Travel time updates alter effective time while preserving physical road distance")
    void travelTimeUpdatesPreserveDistance() {
        Node a = new Node("A", 12.0, 77.0);
        Node b = new Node("B", 12.1, 77.1);
        graph.addNode(a);
        graph.addNode(b);

        graph.addEdge("A", "B", 10.0, 10.0, "State Highway");
        Edge initialEdge = graph.getEdge("A", "B").orElseThrow();

        assertEquals(10.0, initialEdge.getDistanceKm());
        assertEquals(10.0, initialEdge.getBaseTravelTimeMinutes());
        assertEquals(10.0, initialEdge.getTravelTimeMinutes());
        assertEquals(TrafficLevel.FREE_FLOW, initialEdge.getTrafficCondition().level());

        // Update traffic to HEAVY (2.2x)
        simulationEngine.setTraffic("A", "B", TrafficLevel.HEAVY);
        Edge updatedEdge = graph.getEdge("A", "B").orElseThrow();

        assertEquals(10.0, updatedEdge.getDistanceKm(), "Physical road distance must remain unchanged");
        assertEquals(10.0, updatedEdge.getBaseTravelTimeMinutes(), "Static base travel time must remain unchanged");
        assertEquals(22.0, updatedEdge.getTravelTimeMinutes(), 1e-4, "Dynamic travel time must scale by 2.2x");
        assertEquals(TrafficLevel.HEAVY, updatedEdge.getTrafficCondition().level());
    }

    @Test
    @DisplayName("Congestion on primary road dynamically changes route selection to alternative corridor")
    void congestionChangesRouteSelection() {
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 0.0);
        Node b = new Node("B", 0.0, 1.0);
        Node d = new Node("D", 1.0, 1.0);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(d);

        // Path 1 (via A): Base time = 3.0 + 3.0 = 6.0 min (Initial Fastest)
        graph.addEdge("S", "A", 3.0, 3.0);
        graph.addEdge("A", "D", 3.0, 3.0);

        // Path 2 (via B): Base time = 4.0 + 4.0 = 8.0 min (Initial Slower)
        graph.addEdge("S", "B", 4.0, 4.0);
        graph.addEdge("B", "D", 4.0, 4.0);

        // Initial query: chooses Path 1 (via A)
        RouteResult initialRoute = routeService.findFastestRoute("S", "D");
        assertEquals(List.of("S", "A", "D"), initialRoute.getNodeIds());
        assertEquals(6.0, initialRoute.getTotalTravelTimeMinutes(), 1e-4);

        // Inject SEVERE congestion on S -> A (3.5x multiplier -> 3.0 * 3.5 = 10.5 min, total Path 1 = 13.5 min)
        simulationEngine.setTraffic("S", "A", TrafficLevel.SEVERE);

        // Subsequent query: should automatically route via B (8.0 min < 13.5 min)
        RouteResult trafficRoute = routeService.findFastestRoute("S", "D");
        assertEquals(List.of("S", "B", "D"), trafficRoute.getNodeIds());
        assertEquals(8.0, trafficRoute.getTotalTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Simulate incident causes severe delay and recovery restores original performance")
    void incidentCreationAndRecovery() {
        Node s = new Node("S", 0.0, 0.0);
        Node d = new Node("D", 1.0, 1.0);
        graph.addNode(s);
        graph.addNode(d);
        graph.addEdge("S", "D", 5.0, 5.0, "Expressway");

        // Simulate severe pile-up incident with 6.0x delay
        simulationEngine.simulateIncident("S", "D", "Multi-Vehicle Collision", 6.0);
        assertTrue(simulationEngine.hasActiveIncident("S", "D"));

        Edge incidentEdge = graph.getEdge("S", "D").orElseThrow();
        assertEquals(30.0, incidentEdge.getTravelTimeMinutes(), 1e-4);
        assertEquals(TrafficLevel.SEVERE, incidentEdge.getTrafficCondition().level());
        assertTrue(incidentEdge.getTrafficCondition().dataSource().contains("Collision"));

        // Restore road to normal
        simulationEngine.restoreRoad("S", "D");
        assertFalse(simulationEngine.hasActiveIncident("S", "D"));

        Edge restoredEdge = graph.getEdge("S", "D").orElseThrow();
        assertEquals(5.0, restoredEdge.getTravelTimeMinutes(), 1e-4);
        assertEquals(TrafficLevel.FREE_FLOW, restoredEdge.getTrafficCondition().level());
    }

    @Test
    @DisplayName("Repeated traffic updates correctly overwrite prior state without memory leak")
    void repeatedUpdates() {
        Node a = new Node("A", 0.0, 0.0);
        Node b = new Node("B", 1.0, 1.0);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("A", "B", 10.0, 10.0);

        simulationEngine.setTraffic("A", "B", TrafficLevel.LIGHT);
        assertEquals(12.5, graph.getEdge("A", "B").orElseThrow().getTravelTimeMinutes(), 1e-4);

        simulationEngine.setTraffic("A", "B", TrafficLevel.MODERATE);
        assertEquals(16.0, graph.getEdge("A", "B").orElseThrow().getTravelTimeMinutes(), 1e-4);

        simulationEngine.setTraffic("A", "B", TrafficLevel.FREE_FLOW);
        assertEquals(10.0, graph.getEdge("A", "B").orElseThrow().getTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Invalid traffic values are strictly rejected")
    void invalidTrafficValuesRejected() {
        assertThrows(InvalidCostException.class, () ->
                new TrafficCondition(TrafficLevel.LIGHT, 0.5, Instant.now(), "SRC")); // multiplier < 1.0

        assertThrows(InvalidCostException.class, () ->
                new TrafficCondition(TrafficLevel.LIGHT, Double.NaN, Instant.now(), "SRC"));

        assertThrows(IllegalArgumentException.class, () ->
                new TrafficCondition(TrafficLevel.LIGHT, 1.5, Instant.now(), "   "));

        assertThrows(NullPointerException.class, () ->
                new TrafficCondition(null, 1.5, Instant.now(), "SRC"));
    }

    @Test
    @DisplayName("Deterministic simulation produces identical state progression with matching seed")
    void deterministicSimulationProgression() {
        RoadGraph g1 = new RoadGraph();
        RoadGraph g2 = new RoadGraph();

        Node n1 = new Node("1", 0.0, 0.0);
        Node n2 = new Node("2", 1.0, 1.0);
        g1.addNode(n1);
        g1.addNode(n2);
        g1.addEdge("1", "2", 10.0, 10.0);

        g2.addNode(n1);
        g2.addNode(n2);
        g2.addEdge("1", "2", 10.0, 10.0);

        TrafficSimulationEngine sim1 = new TrafficSimulationEngine(g1, 999L);
        TrafficSimulationEngine sim2 = new TrafficSimulationEngine(g2, 999L);

        // Advance 5 ticks
        for (int i = 0; i < 5; i++) {
            sim1.advanceSimulationStep();
            sim2.advanceSimulationStep();

            double t1 = g1.getEdge("1", "2").orElseThrow().getTravelTimeMinutes();
            double t2 = g2.getEdge("1", "2").orElseThrow().getTravelTimeMinutes();
            assertEquals(t1, t2, 1e-6, "Simulations with same seed must match at tick " + (i + 1));
        }
    }

    @Test
    @DisplayName("Both Dijkstra and A* produce identical routing results after dynamic traffic updates")
    void routingCorrectnessAfterTrafficChanges() {
        Node s = new Node("S", 12.9716, 77.5946);
        Node a = new Node("A", 12.9730, 77.5980);
        Node b = new Node("B", 12.9690, 77.6050);
        Node d = new Node("D", 12.9780, 77.6200);

        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(d);

        graph.addEdge("S", "A", 2.0, 4.0);
        graph.addEdge("A", "D", 3.0, 5.0);

        graph.addEdge("S", "B", 3.0, 3.5);
        graph.addEdge("B", "D", 4.0, 4.5);

        simulationEngine.setTraffic("S", "B", TrafficLevel.HEAVY); // slow down path B

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        AStarRoutingAlgorithm astar = new AStarRoutingAlgorithm();

        RouteResult dResult = dijkstra.findFastestRoute(graph, "S", "D");
        RouteResult aResult = astar.findFastestRoute(graph, "S", "D");

        assertTrue(dResult.isReachable());
        assertTrue(aResult.isReachable());
        assertEquals(dResult.getTotalTravelTimeMinutes(), aResult.getTotalTravelTimeMinutes(), 1e-4);
        assertEquals(dResult.getPath(), aResult.getPath());
    }

    @Test
    @DisplayName("Reroute evaluation recommends rerouting when traffic incident makes current route slower")
    void rerouteEvaluationRecommendsDetour() {
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

        // Active dispatched route is via A (6.0 min)
        RouteResult initialRoute = routeService.findFastestRoute("S", "D");
        assertEquals(List.of("S", "A", "D"), initialRoute.getNodeIds());

        // Incident strikes A -> D (10x delay -> 30.0 min, total path via A = 33.0 min)
        simulationEngine.simulateIncident("A", "D", "Bridge Collapse", 10.0);

        // Evaluate reroute with 1.0 min threshold
        RerouteEvaluation eval = routeService.evaluateReroute(initialRoute, 1.0);

        assertTrue(eval.rerouteRecommended());
        assertEquals(List.of("S", "B", "D"), eval.currentFastestRoute().getNodeIds());
        assertTrue(eval.timeSavedMinutes() > 20.0);
        assertTrue(eval.summary().contains("REROUTE RECOMMENDED"));
    }
}
