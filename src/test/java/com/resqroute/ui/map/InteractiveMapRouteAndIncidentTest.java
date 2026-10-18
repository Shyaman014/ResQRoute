package com.resqroute.ui.map;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.RoadIncident;
import com.resqroute.model.RouteResult;
import com.resqroute.ui.controller.SimulationPresentationModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Interactive Map Routing & Incident Simulation Tests")
class InteractiveMapRouteAndIncidentTest {

    private SimulationPresentationModel model;

    @BeforeEach
    void setUp() {
        model = new SimulationPresentationModel(Runnable::run);
        model.initializeDefaultScenario();
    }

    @AfterEach
    void tearDown() {
        model.shutdown();
    }

    @Test
    @DisplayName("Dijkstra and A* routing produce valid reachable routes on the map graph")
    void routingAlgorithmsProduceValidRoutes() {
        RouteResult dijkstraRoute = model.calculateRoute("NORTH-STATION", "HOSP-APEX-NODE", "DIJKSTRA");
        assertTrue(dijkstraRoute.isReachable());
        assertEquals("NORTH-STATION", dijkstraRoute.getSourceId());
        assertEquals("HOSP-APEX-NODE", dijkstraRoute.getDestinationId());
        assertTrue(dijkstraRoute.getTotalDistanceKm() > 0);
        assertTrue(dijkstraRoute.getTotalTravelTimeMinutes() > 0);

        RouteResult aStarRoute = model.calculateRoute("NORTH-STATION", "HOSP-APEX-NODE", "A_STAR");
        assertTrue(aStarRoute.isReachable());
        assertEquals(dijkstraRoute.getTotalDistanceKm(), aStarRoute.getTotalDistanceKm(), 1e-4);
        assertEquals(dijkstraRoute.getTotalTravelTimeMinutes(), aStarRoute.getTotalTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Yen's K-Shortest paths returns ordered alternative routes")
    void yenAlternativeRoutes() {
        List<RouteResult> alternatives = model.calculateAlternativeRoutes("NORTH-STATION", "HOSP-APEX-NODE", 3);
        assertNotNull(alternatives);
        assertFalse(alternatives.isEmpty());

        for (int i = 0; i < alternatives.size() - 1; i++) {
            RouteResult r1 = alternatives.get(i);
            RouteResult r2 = alternatives.get(i + 1);
            assertTrue(r1.getTotalTravelTimeMinutes() <= r2.getTotalTravelTimeMinutes() + 1e-6,
                    "Alternative routes must be non-decreasing in travel time");
        }
    }

    @Test
    @DisplayName("Reporting road closure updates availability and forces route recalculation detour")
    void roadClosureForcesDetour() {
        // Initial fastest route: NORTH-STATION -> CENTRAL-PLAZA -> HOSP-APEX-NODE (4 min + 3 min = 7 min)
        RouteResult initialRoute = model.calculateRoute("NORTH-STATION", "HOSP-APEX-NODE", "DIJKSTRA");
        assertEquals(List.of("NORTH-STATION", "CENTRAL-PLAZA", "HOSP-APEX-NODE"), initialRoute.getNodeIds());
        assertEquals(7.0, initialRoute.getTotalTravelTimeMinutes(), 1e-4);

        // Close direct road NORTH-STATION -> CENTRAL-PLAZA
        model.reportRoadClosure("NORTH-STATION", "CENTRAL-PLAZA", "Emergency Road Work");

        // Verify edge is unavailable
        RoadGraph liveGraph = model.getLiveGraph().orElseThrow();
        Optional<Edge> closedEdge = liveGraph.getEdge("NORTH-STATION", "CENTRAL-PLAZA");
        assertTrue(closedEdge.isPresent());
        assertFalse(closedEdge.get().isAvailable());

        // Verify active incident registered
        List<RoadIncident> incidents = model.getActiveIncidents();
        assertFalse(incidents.isEmpty());

        // Recalculated route must detour via WEST-INDUSTRIAL (7 min + 5 min + 3 min = 15 min)
        RouteResult detouredRoute = model.calculateRoute("NORTH-STATION", "HOSP-APEX-NODE", "DIJKSTRA");
        assertTrue(detouredRoute.isReachable());
        assertEquals(List.of("NORTH-STATION", "WEST-INDUSTRIAL", "CENTRAL-PLAZA", "HOSP-APEX-NODE"), detouredRoute.getNodeIds());
        assertTrue(detouredRoute.getTotalTravelTimeMinutes() > initialRoute.getTotalTravelTimeMinutes());

        // Resolve incident and reopen road
        String incidentId = incidents.get(0).getId();
        model.resolveRoadIncident(incidentId);

        assertTrue(liveGraph.getEdge("NORTH-STATION", "CENTRAL-PLAZA").orElseThrow().isAvailable());

        // Fastest route restores to direct path
        RouteResult restoredRoute = model.calculateRoute("NORTH-STATION", "HOSP-APEX-NODE", "DIJKSTRA");
        assertEquals(List.of("NORTH-STATION", "CENTRAL-PLAZA", "HOSP-APEX-NODE"), restoredRoute.getNodeIds());
        assertEquals(7.0, restoredRoute.getTotalTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Rendering and querying does not mutate graph nodes or edges")
    void mapQueriesDoNotMutateGraph() {
        RoadGraph graph = model.getLiveGraph().orElseThrow();
        int initialNodeCount = graph.getNodeCount();
        int initialEdgeCount = graph.getEdgeCount();

        // Perform multiple queries
        model.calculateRoute("NORTH-STATION", "SOUTH-STATION", "DIJKSTRA");
        model.calculateAlternativeRoutes("NORTH-STATION", "SOUTH-STATION", 3);

        assertEquals(initialNodeCount, graph.getNodeCount());
        assertEquals(initialEdgeCount, graph.getEdgeCount());
    }
}
