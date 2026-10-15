package com.resqroute.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 7: Road Incident & Rerouting Model Tests")
class RoadIncidentModelTest {

    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    @DisplayName("Successfully creates RoadIncident with valid parameters")
    void testValidIncidentCreation() {
        Instant expiry = now.plus(2, ChronoUnit.HOURS);
        RoadIncident incident = new RoadIncident(
                "INC-001",
                EdgeKey.of("N1", "N2"),
                IncidentType.ACCIDENT,
                IncidentSeverity.HIGH,
                now,
                expiry,
                "Multi-car pileup",
                true
        );

        assertEquals("INC-001", incident.getId());
        assertEquals("N1", incident.getSourceNodeId());
        assertEquals("N2", incident.getDestinationNodeId());
        assertEquals(IncidentType.ACCIDENT, incident.getIncidentType());
        assertEquals(IncidentSeverity.HIGH, incident.getSeverity());
        assertEquals(now, incident.getCreatedAt());
        assertEquals(Optional.of(expiry), incident.getExpiryTime());
        assertEquals("Multi-car pileup", incident.getDescription());
        assertTrue(incident.isBlocksRoad());
        assertTrue(incident.isActive(now.plus(1, ChronoUnit.HOURS)));
        assertFalse(incident.isExpired(now.plus(1, ChronoUnit.HOURS)));
        assertTrue(incident.isExpired(now.plus(3, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("Rejects null or blank incident ID")
    void testRejectsInvalidIncidentId() {
        assertThrows(IllegalArgumentException.class, () ->
                new RoadIncident(null, EdgeKey.of("A", "B"), IncidentType.ACCIDENT, IncidentSeverity.HIGH, now, "Test"));
        assertThrows(IllegalArgumentException.class, () ->
                new RoadIncident("   ", EdgeKey.of("A", "B"), IncidentType.ACCIDENT, IncidentSeverity.HIGH, now, "Test"));
    }

    @Test
    @DisplayName("Rejects null required arguments")
    void testRejectsNullRequiredArguments() {
        assertThrows(NullPointerException.class, () ->
                new RoadIncident("I1", null, IncidentType.ACCIDENT, IncidentSeverity.HIGH, now, "Test"));
        assertThrows(NullPointerException.class, () ->
                new RoadIncident("I1", EdgeKey.of("A", "B"), null, IncidentSeverity.HIGH, now, "Test"));
        assertThrows(NullPointerException.class, () ->
                new RoadIncident("I1", EdgeKey.of("A", "B"), IncidentType.ACCIDENT, null, now, "Test"));
        assertThrows(NullPointerException.class, () ->
                new RoadIncident("I1", EdgeKey.of("A", "B"), IncidentType.ACCIDENT, IncidentSeverity.HIGH, null, "Test"));
    }

    @Test
    @DisplayName("Rejects expiry time prior to creation time")
    void testRejectsExpiryBeforeCreation() {
        Instant before = now.minus(10, ChronoUnit.MINUTES);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new RoadIncident("I-EXP", EdgeKey.of("A", "B"), IncidentType.ROAD_CLOSURE,
                        IncidentSeverity.CRITICAL, now, before, "Bad expiry", true));
        assertTrue(ex.getMessage().contains("Expiry time cannot be before creation time"));
    }

    @Test
    @DisplayName("Incident without explicit expiry never expires")
    void testIncidentWithoutExpiryNeverExpires() {
        RoadIncident incident = RoadIncident.roadClosure("INC-NO-EXP", "A", "B", now);
        assertTrue(incident.getExpiryTime().isEmpty());
        assertFalse(incident.isExpired(now.plus(100, ChronoUnit.DAYS)));
        assertTrue(incident.isActive(now.plus(100, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("Static factory methods create appropriate incident types")
    void testFactoryMethods() {
        RoadIncident acc = RoadIncident.accident("ACC-1", "A", "B", now);
        assertEquals(IncidentType.ACCIDENT, acc.getIncidentType());
        assertEquals(IncidentSeverity.HIGH, acc.getSeverity());

        RoadIncident closure = RoadIncident.roadClosure("CLO-1", "A", "B", now);
        assertEquals(IncidentType.ROAD_CLOSURE, closure.getIncidentType());
        assertEquals(IncidentSeverity.CRITICAL, closure.getSeverity());

        RoadIncident constr = RoadIncident.construction("CON-1", "A", "B", now, now.plus(1, ChronoUnit.DAYS));
        assertEquals(IncidentType.CONSTRUCTION, constr.getIncidentType());

        RoadIncident block = RoadIncident.temporaryBlockage("BLK-1", "A", "B", now, now.plus(30, ChronoUnit.MINUTES));
        assertEquals(IncidentType.TEMPORARY_BLOCKAGE, block.getIncidentType());
    }

    @Test
    @DisplayName("TrackedRoute correctly inspects remaining route path")
    void testTrackedRouteRemainingRoad() {
        Node n1 = new Node("A", 12.90, 77.50);
        Node n2 = new Node("B", 12.91, 77.51);
        Node n3 = new Node("C", 12.92, 77.52);

        Edge e1 = new Edge(n1, n2, 2.0, 3.0, "Road 1");
        Edge e2 = new Edge(n2, n3, 3.0, 4.0, "Road 2");

        RouteResult route = RouteResult.reachable(java.util.List.of(n1, n2, n3), java.util.List.of(e1, e2), 5.0, 7.0);

        TrackedRoute tracked = new TrackedRoute("AMB-01", route, 0);
        assertEquals("A", tracked.getCurrentOriginNodeId());
        assertEquals("C", tracked.getDestinationId());
        assertTrue(tracked.containsRemainingRoad("A", "B"));
        assertTrue(tracked.containsRemainingRoad("B", "C"));
        assertFalse(tracked.containsRemainingRoad("C", "A"));
        assertFalse(tracked.containsRemainingRoad("X", "Y"));

        // Advance progress to B
        TrackedRoute advanced = tracked.advanceToWaypoint(1);
        assertEquals("B", advanced.getCurrentOriginNodeId());
        assertFalse(advanced.containsRemainingRoad("A", "B")); // Already passed
        assertTrue(advanced.containsRemainingRoad("B", "C"));
    }

    @Test
    @DisplayName("RerouteResponse accurately calculates time and distance delta")
    void testRerouteResponseMetrics() {
        Node n1 = new Node("A", 12.90, 77.50);
        Node n2 = new Node("B", 12.91, 77.51);
        Edge e1 = new Edge(n1, n2, 10.0, 15.0);
        RouteResult oldRoute = RouteResult.reachable(java.util.List.of(n1, n2), java.util.List.of(e1), 10.0, 15.0);

        Node n3 = new Node("C", 12.93, 77.53);
        Edge e2 = new Edge(n1, n3, 8.0, 10.0);
        RouteResult newRoute = RouteResult.reachable(java.util.List.of(n1, n3), java.util.List.of(e2), 8.0, 10.0);

        RerouteResponse response = RerouteResponse.rerouted("TRK-1", RerouteTriggerReason.ROAD_CLOSED,
                oldRoute, newRoute, 15.0, 10.0, null);

        assertTrue(response.isRerouted());
        assertFalse(response.isBlockedWithNoAlternative());
        assertEquals(5.0, response.timeDeltaMinutes(), 1e-6); // 15 - 10 = +5.0 min saved
        assertEquals(2.0, response.distanceDeltaKm(), 1e-6); // 10 - 8 = +2.0 km saved
    }
}
