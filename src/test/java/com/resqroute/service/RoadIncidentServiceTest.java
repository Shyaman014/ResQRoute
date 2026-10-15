package com.resqroute.service;

import com.resqroute.exception.DuplicateIncidentException;
import com.resqroute.exception.InvalidEdgeException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 7: Road Incident Service & Availability Tests")
class RoadIncidentServiceTest {

    private RoadGraph graph;
    private RoadIncidentService incidentService;
    private final Instant now = Instant.parse("2026-10-16T12:00:00Z");

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        graph.addNode(new Node("A", 12.9716, 77.5946));
        graph.addNode(new Node("B", 12.9816, 77.6046));
        graph.addNode(new Node("C", 12.9916, 77.6146));

        graph.addBidirectionalEdge("A", "B", 5.0, 10.0, "MG Road");
        graph.addBidirectionalEdge("B", "C", 4.0, 8.0, "Brigade Road");

        incidentService = new RoadIncidentService(graph);
    }

    @Test
    @DisplayName("Reporting an incident closes road and preserves static distance and base time")
    void testReportIncidentClosesRoad() {
        assertTrue(graph.isRoadAvailable("A", "B"));
        assertFalse(graph.isRoadClosed("A", "B"));

        RoadIncident incident = RoadIncident.roadClosure("INC-101", "A", "B", now);
        incidentService.reportIncident(incident);

        assertTrue(graph.isRoadClosed("A", "B"));
        assertFalse(graph.isRoadAvailable("A", "B"));
        assertTrue(incidentService.isRoadBlocked("A", "B"));

        // Verify distance and base travel time are preserved
        Edge edge = graph.getEdge("A", "B").orElseThrow();
        assertEquals(5.0, edge.getDistanceKm(), 1e-9);
        assertEquals(10.0, edge.getBaseTravelTimeMinutes(), 1e-9);
        assertTrue(edge.isClosed());
    }

    @Test
    @DisplayName("Directed closure closes ONLY the target direction; opposite direction remains open")
    void testDirectedClosurePreservesOppositeDirection() {
        RoadIncident incident = RoadIncident.accident("INC-DIR-1", "A", "B", now);
        incidentService.reportIncident(incident);

        assertTrue(graph.isRoadClosed("A", "B"), "Direction A->B should be closed");
        assertFalse(graph.isRoadClosed("B", "A"), "Direction B->A should remain OPEN");
        assertTrue(graph.isRoadAvailable("B", "A"));

        // Unrelated road B->C is unaffected
        assertTrue(graph.isRoadAvailable("B", "C"));
        assertTrue(graph.isRoadAvailable("C", "B"));
    }

    @Test
    @DisplayName("Bidirectional incident closes both directions")
    void testBidirectionalIncidentClosesBothDirections() {
        List<RoadIncident> incidents = incidentService.reportBidirectionalIncident(
                "BI-INC-1", IncidentType.CONSTRUCTION, "A", "B", IncidentSeverity.HIGH, now, "Resurfacing");

        assertEquals(2, incidents.size());
        assertTrue(graph.isRoadClosed("A", "B"));
        assertTrue(graph.isRoadClosed("B", "A"));

        // Unrelated road remains open
        assertTrue(graph.isRoadAvailable("B", "C"));

        // Resolve bidirectional incident
        List<RoadIncident> resolved = incidentService.resolveBidirectionalIncident("BI-INC-1");
        assertEquals(2, resolved.size());
        assertTrue(graph.isRoadAvailable("A", "B"));
        assertTrue(graph.isRoadAvailable("B", "A"));
    }

    @Test
    @DisplayName("Multiple incidents on the same road: road reopens ONLY when ALL incidents are resolved")
    void testMultipleIncidentsOnSameRoad() {
        RoadIncident inc1 = RoadIncident.accident("INC-ACC", "A", "B", now);
        RoadIncident inc2 = RoadIncident.construction("INC-CONST", "A", "B", now, now.plus(3, ChronoUnit.HOURS));

        incidentService.reportIncident(inc1);
        incidentService.reportIncident(inc2);

        assertEquals(2, incidentService.getIncidentsOnRoad("A", "B").size());
        assertTrue(graph.isRoadClosed("A", "B"));

        // Resolve the first incident (accident cleared)
        Optional<RoadIncident> resolvedFirst = incidentService.resolveIncident("INC-ACC");
        assertTrue(resolvedFirst.isPresent());

        // Road must STILL be closed because construction is still active!
        assertTrue(graph.isRoadClosed("A", "B"), "Road must remain closed because construction is still active");
        assertTrue(incidentService.isRoadBlocked("A", "B"));
        assertEquals(1, incidentService.getIncidentsOnRoad("A", "B").size());

        // Resolve the second incident (construction completed)
        Optional<RoadIncident> resolvedSecond = incidentService.resolveIncident("INC-CONST");
        assertTrue(resolvedSecond.isPresent());

        // Now with 0 active incidents, the road must be reopened
        assertFalse(graph.isRoadClosed("A", "B"));
        assertTrue(graph.isRoadAvailable("A", "B"));
        assertFalse(incidentService.isRoadBlocked("A", "B"));
        assertEquals(0, incidentService.getIncidentsOnRoad("A", "B").size());
    }

    @Test
    @DisplayName("Expiry handling: expired incidents are cleared and roads reopened")
    void testIncidentExpiryHandling() {
        Instant exp1 = now.plus(1, ChronoUnit.HOURS);
        Instant exp2 = now.plus(4, ChronoUnit.HOURS);

        RoadIncident inc1 = RoadIncident.temporaryBlockage("INC-EXP-1", "A", "B", now, exp1);
        RoadIncident inc2 = RoadIncident.construction("INC-EXP-2", "B", "C", now, exp2);

        incidentService.reportIncident(inc1);
        incidentService.reportIncident(inc2);

        assertTrue(graph.isRoadClosed("A", "B"));
        assertTrue(graph.isRoadClosed("B", "C"));

        // Advance simulation time to 2 hours (inc1 has expired, inc2 is still active)
        Instant clockAt2Hours = now.plus(2, ChronoUnit.HOURS);
        List<RoadIncident> expiredAt2h = incidentService.expireIncidents(clockAt2Hours);

        assertEquals(1, expiredAt2h.size());
        assertEquals("INC-EXP-1", expiredAt2h.get(0).getId());

        // A->B should now be restored
        assertTrue(graph.isRoadAvailable("A", "B"));
        // B->C must still be closed
        assertTrue(graph.isRoadClosed("B", "C"));

        // Advance simulation time to 5 hours (inc2 also expires)
        Instant clockAt5Hours = now.plus(5, ChronoUnit.HOURS);
        List<RoadIncident> expiredAt5h = incidentService.expireIncidents(clockAt5Hours);

        assertEquals(1, expiredAt5h.size());
        assertEquals("INC-EXP-2", expiredAt5h.get(0).getId());

        // B->C should now also be restored
        assertTrue(graph.isRoadAvailable("B", "C"));
    }

    @Test
    @DisplayName("Rejects duplicate active incident ID with DuplicateIncidentException")
    void testRejectsDuplicateIncidentId() {
        RoadIncident inc1 = RoadIncident.accident("DUP-1", "A", "B", now);
        RoadIncident inc2 = RoadIncident.accident("DUP-1", "B", "C", now);

        incidentService.reportIncident(inc1);
        assertThrows(DuplicateIncidentException.class, () -> incidentService.reportIncident(inc2));
    }

    @Test
    @DisplayName("Rejects incident on non-existent node or road")
    void testRejectsInvalidNodeOrRoad() {
        assertThrows(NodeNotFoundException.class, () ->
                incidentService.reportIncident(RoadIncident.accident("I-1", "NON_EXISTENT", "B", now)));

        assertThrows(NodeNotFoundException.class, () ->
                incidentService.reportIncident(RoadIncident.accident("I-2", "A", "NON_EXISTENT", now)));

        // Both nodes exist, but no direct edge between A and C exists
        assertThrows(InvalidEdgeException.class, () ->
                incidentService.reportIncident(RoadIncident.accident("I-3", "A", "C", now)));
    }

    @Test
    @DisplayName("clearIncidentsOnRoad and clearAllIncidents restore road availability")
    void testClearIncidents() {
        incidentService.reportIncident(RoadIncident.accident("INC-1", "A", "B", now));
        incidentService.reportIncident(RoadIncident.accident("INC-2", "B", "C", now));

        assertTrue(graph.isRoadClosed("A", "B"));
        assertTrue(graph.isRoadClosed("B", "C"));

        incidentService.clearIncidentsOnRoad("A", "B");
        assertTrue(graph.isRoadAvailable("A", "B"));
        assertTrue(graph.isRoadClosed("B", "C"));

        incidentService.clearAllIncidents();
        assertTrue(graph.isRoadAvailable("B", "C"));
        assertEquals(0, incidentService.getActiveIncidentCount());
    }
}
