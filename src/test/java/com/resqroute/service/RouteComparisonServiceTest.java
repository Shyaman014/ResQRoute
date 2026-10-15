package com.resqroute.service;

import com.resqroute.model.Node;
import com.resqroute.model.RouteComparisonItem;
import com.resqroute.model.RouteComparisonReport;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: RouteComparisonService Analytical Reporting Tests")
class RouteComparisonServiceTest {

    private final RouteComparisonService service = new RouteComparisonService();

    @Test
    @DisplayName("Calculates exact deltas relative to baseline fastest route without premature rounding")
    void calculatesExactDeltas() {
        Node s = new Node("S", 0.0, 0.0);
        Node a = new Node("A", 1.0, 1.0);
        Node b = new Node("B", 2.0, 2.0);
        Node d = new Node("D", 3.0, 3.0);

        // Route 1 (Fastest): 10.123456 minutes, 8.543210 km
        RouteResult r1 = RouteResult.reachable(List.of(s, a, d), 8.543210, 10.123456);

        // Route 2 (Alternative): 15.654321 minutes, 7.123456 km
        RouteResult r2 = RouteResult.reachable(List.of(s, b, d), 7.123456, 15.654321);

        RouteComparisonReport report = service.compareRoutes(List.of(r2, r1)); // pass unordered

        assertEquals(2, report.getRouteCount());
        assertEquals("S", report.getSourceId());
        assertEquals("D", report.getDestinationId());

        RouteComparisonItem item1 = report.getItems().get(0);
        assertEquals(1, item1.rank());
        assertEquals(r1, item1.route());
        assertEquals(10.123456, item1.totalTravelTimeMinutes(), 1e-9);
        assertEquals(8.543210, item1.totalDistanceKm(), 1e-9);
        assertEquals(0.0, item1.timeDifferenceMinutes(), 1e-9);
        assertEquals(0.0, item1.distanceDifferenceKm(), 1e-9);
        assertEquals(0.0, item1.percentageTimeIncrease(), 1e-9);

        RouteComparisonItem item2 = report.getItems().get(1);
        assertEquals(2, item2.rank());
        assertEquals(r2, item2.route());
        // Verify exact double delta calculation without premature rounding
        double expectedTimeDelta = 15.654321 - 10.123456;
        double expectedDistDelta = 7.123456 - 8.543210;
        double expectedPercent = (expectedTimeDelta / 10.123456) * 100.0;

        assertEquals(expectedTimeDelta, item2.timeDifferenceMinutes(), 1e-9);
        assertEquals(expectedDistDelta, item2.distanceDifferenceKm(), 1e-9);
        assertEquals(expectedPercent, item2.percentageTimeIncrease(), 1e-9);
    }

    @Test
    @DisplayName("Rejects null or empty route lists")
    void invalidInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.compareRoutes(null));
        assertThrows(IllegalArgumentException.class, () -> service.compareRoutes(List.of()));
    }

    @Test
    @DisplayName("Generates readable formatted table containing comparative metrics")
    void formatsTableCleanly() {
        Node s = new Node("AMB", 0.0, 0.0);
        Node d = new Node("HOSP", 1.0, 1.0);

        RouteResult r1 = RouteResult.reachable(List.of(s, d), 5.0, 7.5);
        RouteResult r2 = RouteResult.reachable(List.of(s, d), 4.0, 12.0);

        RouteComparisonReport report = service.compareRoutes(List.of(r1, r2));
        String table = report.toFormattedTable();

        assertNotNull(table);
        assertTrue(table.contains("Rank"));
        assertTrue(table.contains("FASTEST"));
        assertTrue(table.contains("AMB -> HOSP"));
    }
}
