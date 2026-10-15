package com.resqroute.service;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.YenKShortestPathsAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 7: Automatic Rerouting & Algorithm Integration Tests")
class AutomaticReroutingServiceTest {

    private RoadGraph graph;
    private DijkstraRoutingAlgorithm dijkstra;
    private AStarRoutingAlgorithm aStar;
    private YenKShortestPathsAlgorithm yen;
    private RoadIncidentService incidentService;
    private AutomaticReroutingService reroutingService;

    private final Instant now = Instant.parse("2026-10-16T14:00:00Z");

    /**
     * Diamond topology with two alternative paths:
     *
     *       [A]
     *      /   \
     *  (via B)  (via C)
     *    /       \
     *  [B]       [C]
     *    \       /
     *     \     /
     *       [D]
     *
     * Primary route: A -> B -> D (Fast highway: 4 + 4 = 8 min)
     * Detour route:  A -> C -> D (Local road:   7 + 7 = 14 min)
     */
    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        graph.addNode(new Node("A", 12.90, 77.50));
        graph.addNode(new Node("B", 12.92, 77.50));
        graph.addNode(new Node("C", 12.90, 77.52));
        graph.addNode(new Node("D", 12.92, 77.52));

        // Fast primary path A -> B -> D
        graph.addEdge("A", "B", 4.0, 4.0, "Expressway Part 1");
        graph.addEdge("B", "D", 4.0, 4.0, "Expressway Part 2");

        // Slower detour path A -> C -> D
        graph.addEdge("A", "C", 6.0, 7.0, "Detour Ave Part 1");
        graph.addEdge("C", "D", 6.0, 7.0, "Detour Ave Part 2");

        dijkstra = new DijkstraRoutingAlgorithm();
        aStar = new AStarRoutingAlgorithm();
        yen = new YenKShortestPathsAlgorithm();

        incidentService = new RoadIncidentService(graph);
        reroutingService = new AutomaticReroutingService(graph, dijkstra);
    }

    @Test
    @DisplayName("Closing a road on the active route triggers automatic rerouting to valid alternative")
    void testClosureTriggersReroute() {
        // Initial fastest route is A -> B -> D (8.0 min)
        RouteResult initialRoute = dijkstra.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "B", "D"), initialRoute.getNodeIds());
        assertEquals(8.0, initialRoute.getTotalTravelTimeMinutes(), 1e-9);

        // Track the ambulance on this route
        reroutingService.trackRoute("AMB-101", initialRoute);

        // Incident occurs closing A -> B
        RoadIncident incident = RoadIncident.roadClosure("INC-CLO-AB", "A", "B", now);
        incidentService.reportIncident(incident);

        // Trigger reroute evaluation
        List<RerouteResponse> responses = reroutingService.onRoadClosure("A", "B");
        assertEquals(1, responses.size());

        RerouteResponse response = responses.get(0);
        assertTrue(response.isRerouted());
        assertEquals(RerouteStatus.REROUTED, response.status());
        assertEquals(RerouteTriggerReason.ROAD_CLOSED, response.triggerReason());

        // New route must avoid closed A -> B and use A -> C -> D (14.0 min)
        RouteResult newRoute = response.newRoute();
        assertEquals(List.of("A", "C", "D"), newRoute.getNodeIds());
        assertEquals(14.0, newRoute.getTotalTravelTimeMinutes(), 1e-9);

        // Verify tracked route state was updated
        TrackedRoute updatedTracked = reroutingService.getTrackedRoute("AMB-101").orElseThrow();
        assertEquals(List.of("A", "C", "D"), updatedTracked.getActiveRoute().getNodeIds());
    }

    @Test
    @DisplayName("Reports NO_ALTERNATIVE_ROUTE when all routes to destination are severed")
    void testNoAlternativeRoute() {
        RouteResult initialRoute = dijkstra.findFastestRoute(graph, "A", "D");
        reroutingService.trackRoute("AMB-102", initialRoute);

        // Close BOTH corridors leading to D: B -> D and C -> D
        incidentService.reportIncident(RoadIncident.roadClosure("INC-BD", "B", "D", now));
        incidentService.reportIncident(RoadIncident.roadClosure("INC-CD", "C", "D", now));

        RerouteResponse response = reroutingService.onRoadClosureForTrackedRoute("AMB-102", "B", "D");

        assertEquals(RerouteStatus.NO_ALTERNATIVE_ROUTE, response.status());
        assertTrue(response.isBlockedWithNoAlternative());
        assertFalse(response.newRoute().isReachable());
        assertTrue(response.message().contains("NO alternative route exists"));
    }

    @Test
    @DisplayName("Unrelated road incidents do NOT trigger unnecessary rerouting")
    void testUnrelatedRoadIncidentSkipsReroute() {
        RouteResult initialRoute = dijkstra.findFastestRoute(graph, "A", "D");
        reroutingService.trackRoute("AMB-103", initialRoute); // Uses A -> B -> D

        // An incident occurs on A -> C (which is NOT on the active route A -> B -> D)
        incidentService.reportIncident(RoadIncident.accident("INC-AC", "A", "C", now));

        List<RerouteResponse> responses = reroutingService.onRoadClosure("A", "C");
        assertEquals(1, responses.size());

        RerouteResponse response = responses.get(0);
        assertEquals(RerouteStatus.NO_ACTION_REQUIRED, response.status());
        assertEquals(RerouteTriggerReason.UNRELATED_ROAD, response.triggerReason());
        assertFalse(response.isRerouted());

        // Tracked route remains exactly unchanged
        TrackedRoute tracked = reroutingService.getTrackedRoute("AMB-103").orElseThrow();
        assertEquals(List.of("A", "B", "D"), tracked.getActiveRoute().getNodeIds());
    }

    @Test
    @DisplayName("Duplicate events are detected and skipped to prevent cyclic rerouting loops")
    void testDuplicateUpdateLoopPrevention() {
        RouteResult initialRoute = dijkstra.findFastestRoute(graph, "A", "D");
        reroutingService.trackRoute("AMB-104", initialRoute);

        // First closure event
        incidentService.reportIncident(RoadIncident.roadClosure("INC-AB-1", "A", "B", now));
        RerouteResponse firstResponse = reroutingService.onRoadClosureForTrackedRoute("AMB-104", "A", "B");
        assertEquals(RerouteStatus.REROUTED, firstResponse.status());

        // Duplicate event received with identical state
        RerouteResponse duplicateResponse = reroutingService.onRoadClosureForTrackedRoute("AMB-104", "A", "B");
        // Because the route was rerouted to A -> C -> D, A -> B is no longer on the remaining route!
        assertEquals(RerouteStatus.NO_ACTION_REQUIRED, duplicateResponse.status());
        assertEquals(RerouteTriggerReason.UNRELATED_ROAD, duplicateResponse.triggerReason());
    }

    @Test
    @DisplayName("Reopening a road restores the original fastest route")
    void testRoadReopeningRestoresFastestPath() {
        RouteResult route1 = dijkstra.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "B", "D"), route1.getNodeIds());

        // Close A -> B
        incidentService.reportIncident(RoadIncident.roadClosure("INC-TEMP", "A", "B", now));
        RouteResult routeWhileClosed = dijkstra.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "C", "D"), routeWhileClosed.getNodeIds());

        // Resolve incident and reopen A -> B
        incidentService.resolveIncident("INC-TEMP");
        RouteResult routeAfterReopen = dijkstra.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "B", "D"), routeAfterReopen.getNodeIds());
        assertEquals(8.0, routeAfterReopen.getTotalTravelTimeMinutes(), 1e-9);
    }

    @Test
    @DisplayName("Dijkstra, A*, and Yen's algorithm all respect road closures")
    void testAlgorithmsRespectRoadClosures() {
        // Initially, all algorithms find A -> B -> D
        assertEquals(List.of("A", "B", "D"), dijkstra.findFastestRoute(graph, "A", "D").getNodeIds());
        assertEquals(List.of("A", "B", "D"), aStar.findFastestRoute(graph, "A", "D").getNodeIds());

        List<RouteResult> kRoutesBefore = yen.findKShortestPaths(graph, "A", "D", 2);
        assertEquals(2, kRoutesBefore.size());
        assertEquals(List.of("A", "B", "D"), kRoutesBefore.get(0).getNodeIds());
        assertEquals(List.of("A", "C", "D"), kRoutesBefore.get(1).getNodeIds());

        // Close A -> B
        graph.closeRoad("A", "B");

        // Dijkstra must find A -> C -> D
        RouteResult dijkstraResult = dijkstra.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "C", "D"), dijkstraResult.getNodeIds());

        // A* must find A -> C -> D
        RouteResult aStarResult = aStar.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "C", "D"), aStarResult.getNodeIds());

        // Yen's algorithm must only return paths using open roads (only 1 path remains available)
        List<RouteResult> kRoutesAfter = yen.findKShortestPaths(graph, "A", "D", 2);
        assertEquals(1, kRoutesAfter.size());
        assertEquals(List.of("A", "C", "D"), kRoutesAfter.get(0).getNodeIds());
    }

    @Test
    @DisplayName("Combined traffic congestion and road closures: algorithm safely routes around both")
    void testCombinedTrafficAndClosures() {
        // Add a third pathway via E: A -> E -> D
        graph.addNode(new Node("E", 12.88, 77.51));
        graph.addEdge("A", "E", 5.0, 9.0, "Outer Ring Road 1");
        graph.addEdge("E", "D", 5.0, 9.0, "Outer Ring Road 2"); // Total 18 min

        // Close primary road A -> B
        graph.closeRoad("A", "B");

        // Slower detour A -> C -> D gets SEVERE traffic (delay multiplier = 3.5x, time = 14 * 3.5 = 49.0 min)
        graph.updateTraffic("A", "C", TrafficCondition.of(TrafficLevel.SEVERE, "SURGE"));
        graph.updateTraffic("C", "D", TrafficCondition.of(TrafficLevel.SEVERE, "SURGE"));

        // Now Outer Ring Road via E (18.0 min) beats congested A -> C -> D (49.0 min)
        RouteResult optimalRoute = dijkstra.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "E", "D"), optimalRoute.getNodeIds());
        assertEquals(18.0, optimalRoute.getTotalTravelTimeMinutes(), 1e-9);

        // A* finds the identical path
        RouteResult aStarRoute = aStar.findFastestRoute(graph, "A", "D");
        assertEquals(List.of("A", "E", "D"), aStarRoute.getNodeIds());
    }

    @Test
    @DisplayName("Rerouting in-progress mission from an advanced waypoint preserves historical path")
    void testRerouteFromAdvancedWaypoint() {
        // Route: A -> B -> D
        RouteResult initialRoute = dijkstra.findFastestRoute(graph, "A", "D");

        // Ambulance has reached waypoint B (index 1)
        TrackedRoute trackedAtB = new TrackedRoute("AMB-PROGRESS", initialRoute, 1);
        reroutingService.trackRoute(trackedAtB);

        // Road ahead B -> D is suddenly closed!
        // We add an alternative connection from B to C so B can detour to D via C
        graph.addEdge("B", "C", 2.0, 3.0, "Connector B-C");

        incidentService.reportIncident(RoadIncident.roadClosure("INC-BD-BLOCKED", "B", "D", now));
        RerouteResponse response = reroutingService.onRoadClosureForTrackedRoute("AMB-PROGRESS", "B", "D");

        assertTrue(response.isRerouted());
        RouteResult completeNewRoute = response.newRoute();

        // Complete route should be: historical A -> B + newly calculated detour B -> C -> D
        assertEquals(List.of("A", "B", "C", "D"), completeNewRoute.getNodeIds());
    }
}
