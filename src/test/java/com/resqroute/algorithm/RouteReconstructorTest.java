package com.resqroute.algorithm;

import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: RouteReconstructor Unit & Safety Tests")
class RouteReconstructorTest {

    @Test
    @DisplayName("Reconstructs valid straight path with exact edge metric summation")
    void reconstructValidPath() {
        Node src = new Node("SRC", 0.0, 0.0);
        Node mid = new Node("MID", 1.0, 1.0);
        Node dst = new Node("DST", 2.0, 2.0);

        Edge e1 = new Edge(src, mid, 2.5, 4.0, "Segment 1");
        Edge e2 = new Edge(mid, dst, 3.5, 6.0, "Segment 2");

        Map<String, Node> preds = new HashMap<>();
        preds.put("MID", src);
        preds.put("DST", mid);

        Map<String, Edge> predEdges = new HashMap<>();
        predEdges.put("MID", e1);
        predEdges.put("DST", e2);

        RouteResult result = RouteReconstructor.reconstruct(src, dst, preds, predEdges);

        assertTrue(result.isReachable());
        assertEquals(List.of("SRC", "MID", "DST"), result.getNodeIds());
        assertEquals(6.0, result.getTotalDistanceKm(), 1e-6);
        assertEquals(10.0, result.getTotalTravelTimeMinutes(), 1e-6);
        assertEquals(2, result.getEdges().size());
    }

    @Test
    @DisplayName("Source equals destination returns single-node 0-cost result")
    void sourceEqualsDestination() {
        Node node = new Node("STATION", 10.0, 10.0);
        RouteResult result = RouteReconstructor.reconstruct(node, node, Map.of(), Map.of());

        assertTrue(result.isReachable());
        assertEquals(0.0, result.getTotalTravelTimeMinutes());
        assertEquals(0.0, result.getTotalDistanceKm());
        assertEquals(List.of(node), result.getPath());
    }

    @Test
    @DisplayName("Unreachable destination returns unreachable result")
    void unreachableDestination() {
        Node src = new Node("SRC", 0.0, 0.0);
        Node dst = new Node("DST", 2.0, 2.0);

        RouteResult result = RouteReconstructor.reconstruct(src, dst, Map.of(), Map.of());

        assertFalse(result.isReachable());
        assertTrue(result.getPath().isEmpty());
    }

    @Test
    @DisplayName("Accidental cycle in predecessor links throws IllegalStateException")
    void cycleDetection() {
        Node src = new Node("SRC", 0.0, 0.0);
        Node a = new Node("A", 1.0, 1.0);
        Node b = new Node("B", 2.0, 2.0);

        // Cyclic predecessor link: B points to A, A points to B
        Map<String, Node> cyclicPreds = new HashMap<>();
        cyclicPreds.put("B", a);
        cyclicPreds.put("A", b);

        assertThrows(IllegalStateException.class, () ->
                RouteReconstructor.reconstruct(src, b, cyclicPreds, Map.of()));
    }
}
