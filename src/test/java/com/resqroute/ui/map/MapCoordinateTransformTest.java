package com.resqroute.ui.map;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.GeoCoordinate;
import com.resqroute.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MapCoordinateTransform Projection, Pan, Zoom, and Selection Tests")
class MapCoordinateTransformTest {

    private MapCoordinateTransform transform;
    private Node nodeNorth;
    private Node nodeSouth;
    private Node nodeEast;
    private Node nodeWest;

    @BeforeEach
    void setUp() {
        transform = new MapCoordinateTransform();
        transform.updateCanvasSize(800.0, 600.0);

        nodeNorth = new Node("N-NORTH", 12.98, 77.60, "North Node");
        nodeSouth = new Node("N-SOUTH", 12.94, 77.60, "South Node");
        nodeEast = new Node("N-EAST", 12.96, 77.65, "East Node");
        nodeWest = new Node("N-WEST", 12.96, 77.55, "West Node");
    }

    @Test
    @DisplayName("fitToBounds encloses all nodes within canvas dimensions with padding")
    void fitToBoundsEnclosesNodes() {
        List<Node> nodes = List.of(nodeNorth, nodeSouth, nodeEast, nodeWest);
        transform.fitToBounds(nodes);

        for (Node node : nodes) {
            MapCoordinateTransform.ScreenPoint pt = transform.toScreen(node.getCoordinates());
            assertTrue(pt.x() >= 0 && pt.x() <= 800.0, "Screen X should be inside canvas width: " + pt.x());
            assertTrue(pt.y() >= 0 && pt.y() <= 600.0, "Screen Y should be inside canvas height: " + pt.y());
        }

        // North latitude should have lower screen Y than South latitude (Y increases downward)
        MapCoordinateTransform.ScreenPoint ptNorth = transform.toScreen(nodeNorth.getCoordinates());
        MapCoordinateTransform.ScreenPoint ptSouth = transform.toScreen(nodeSouth.getCoordinates());
        assertTrue(ptNorth.y() < ptSouth.y(), "North latitude must project to smaller Y than South");

        // West longitude should have lower screen X than East longitude
        MapCoordinateTransform.ScreenPoint ptWest = transform.toScreen(nodeWest.getCoordinates());
        MapCoordinateTransform.ScreenPoint ptEast = transform.toScreen(nodeEast.getCoordinates());
        assertTrue(ptWest.x() < ptEast.x(), "West longitude must project to smaller X than East");
    }

    @Test
    @DisplayName("Roundtrip projection (toScreen -> toGeo) preserves coordinate accuracy")
    void roundtripProjectionAccuracy() {
        transform.fitToBounds(List.of(nodeNorth, nodeSouth, nodeEast, nodeWest));

        GeoCoordinate original = new GeoCoordinate(12.965, 77.615);
        MapCoordinateTransform.ScreenPoint screen = transform.toScreen(original);
        GeoCoordinate reconstructed = transform.toGeo(screen.x(), screen.y());

        assertEquals(original.latitude(), reconstructed.latitude(), 1e-4, "Latitude must match within precision tolerance");
        assertEquals(original.longitude(), reconstructed.longitude(), 1e-4, "Longitude must match within precision tolerance");
    }

    @Test
    @DisplayName("Panning shifts screen coordinates by exact delta values")
    void panningShiftsCoordinates() {
        transform.fitToBounds(List.of(nodeNorth, nodeSouth));
        MapCoordinateTransform.ScreenPoint initial = transform.toScreen(nodeNorth.getCoordinates());

        transform.pan(50.0, -30.0);
        MapCoordinateTransform.ScreenPoint panned = transform.toScreen(nodeNorth.getCoordinates());

        assertEquals(initial.x() + 50.0, panned.x(), 1e-6);
        assertEquals(initial.y() - 30.0, panned.y(), 1e-6);
    }

    @Test
    @DisplayName("Zooming scales distances from pivot point")
    void zoomingScalesFromPivot() {
        transform.fitToBounds(List.of(nodeNorth, nodeSouth));

        double pivotX = 400.0;
        double pivotY = 300.0;
        MapCoordinateTransform.ScreenPoint before = transform.toScreen(nodeNorth.getCoordinates());

        transform.zoomAt(2.0, pivotX, pivotY);
        MapCoordinateTransform.ScreenPoint after = transform.toScreen(nodeNorth.getCoordinates());

        double distBefore = Math.hypot(before.x() - pivotX, before.y() - pivotY);
        double distAfter = Math.hypot(after.x() - pivotX, after.y() - pivotY);

        assertEquals(distBefore * 2.0, distAfter, 1e-4, "Distance from zoom pivot should double with 2.0x zoom");
    }

    @Test
    @DisplayName("findNearestNode finds closest node within hit radius and rejects distant nodes")
    void findNearestNodeHitTesting() {
        List<Node> nodes = List.of(nodeNorth, nodeSouth, nodeEast, nodeWest);
        transform.fitToBounds(nodes);

        MapCoordinateTransform.ScreenPoint ptNorth = transform.toScreen(nodeNorth.getCoordinates());

        // Hit close to North node (within 10px)
        Optional<Node> hit = transform.findNearestNode(nodes, ptNorth.x() + 5, ptNorth.y() - 5, 20.0);
        assertTrue(hit.isPresent());
        assertEquals("N-NORTH", hit.get().getId());

        // Miss (100px away with 20px hit radius)
        Optional<Node> miss = transform.findNearestNode(nodes, ptNorth.x() + 100, ptNorth.y() + 100, 20.0);
        assertTrue(miss.isEmpty());
    }

    @Test
    @DisplayName("findNearestEdge identifies closest road segment within hit threshold")
    void findNearestEdgeHitTesting() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(nodeNorth);
        graph.addNode(nodeSouth);
        graph.addEdge("N-NORTH", "N-SOUTH", 5.0, 6.0, "North-South Expressway");

        transform.fitToBounds(graph.getAllNodes());

        MapCoordinateTransform.ScreenPoint ptN = transform.toScreen(nodeNorth.getCoordinates());
        MapCoordinateTransform.ScreenPoint ptS = transform.toScreen(nodeSouth.getCoordinates());

        // Midpoint of edge
        double midX = (ptN.x() + ptS.x()) / 2.0;
        double midY = (ptN.y() + ptS.y()) / 2.0;

        // Query near midpoint (3px offset)
        Optional<MapCoordinateTransform.EdgeMatch> match = transform.findNearestEdge(graph, midX + 3.0, midY, 15.0);
        assertTrue(match.isPresent());
        assertEquals("N-NORTH", match.get().sourceNodeId());
        assertEquals("N-SOUTH", match.get().edge().getDestination().getId());
        assertEquals("North-South Expressway", match.get().edge().getRoadName());

        // Far query should not match
        Optional<MapCoordinateTransform.EdgeMatch> farMatch = transform.findNearestEdge(graph, midX + 150.0, midY, 15.0);
        assertTrue(farMatch.isEmpty());
    }

    @Test
    @DisplayName("Null or empty nodes handling")
    void nullAndEmptyNodesSafe() {
        assertDoesNotThrow(() -> transform.fitToBounds(null));
        assertDoesNotThrow(() -> transform.fitToBounds(List.of()));
        assertTrue(transform.findNearestNode(null, 100, 100, 20).isEmpty());
        assertTrue(transform.findNearestEdge(null, 100, 100, 20).isEmpty());
    }
}
