package com.resqroute.graph;

import com.resqroute.exception.*;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoadGraph Core Structure, Validation, and Topology Consistency Tests")
class RoadGraphTest {

    private RoadGraph graph;
    private Node nodeA;
    private Node nodeB;
    private Node nodeC;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        nodeA = new Node("A", 12.97, 77.59, "Intersection A");
        nodeB = new Node("B", 12.98, 77.60, "Intersection B");
        nodeC = new Node("C", 12.99, 77.61, "Intersection C");
    }

    @Test
    @DisplayName("Empty graph should report zero nodes and edges")
    void emptyGraph() {
        assertTrue(graph.isEmpty());
        assertEquals(0, graph.getNodeCount());
        assertEquals(0, graph.getEdgeCount());
        assertFalse(graph.containsNode("NON_EXISTENT"));
        assertFalse(graph.containsEdge("A", "B"));
        assertTrue(graph.getAllEdges().isEmpty());
    }

    @Test
    @DisplayName("Nodes are added successfully and discoverable")
    void addNodeSuccess() {
        graph.addNode(nodeA);
        assertFalse(graph.isEmpty());
        assertEquals(1, graph.getNodeCount());
        assertTrue(graph.containsNode("A"));
        assertEquals(nodeA, graph.getRequiredNode("A"));
        assertTrue(graph.getNode("A").isPresent());
        assertEquals(nodeA, graph.getNode("A").get());
    }

    @Test
    @DisplayName("Duplicate node ID insertion must throw DuplicateNodeException")
    void duplicateNodeProtection() {
        graph.addNode(nodeA);
        Node duplicateNode = new Node("A", 13.00, 78.00, "Different Coordinates Same ID");

        DuplicateNodeException ex = assertThrows(DuplicateNodeException.class, () -> graph.addNode(duplicateNode));
        assertEquals("A", ex.getNodeId());
    }

    @Test
    @DisplayName("Self-loops are rejected with InvalidEdgeException")
    void selfLoopRejection() {
        graph.addNode(nodeA);

        assertThrows(InvalidEdgeException.class, () -> graph.addEdge("A", "A", 1.0, 2.0));
        assertThrows(InvalidEdgeException.class, () -> graph.addBidirectionalEdge("A", "A", 1.0, 2.0));

        Edge selfEdge = new Edge(nodeA, 1.0, 2.0);
        assertThrows(InvalidEdgeException.class, () -> graph.addEdge("A", selfEdge));
    }

    @Test
    @DisplayName("Duplicate road insertion must throw DuplicateEdgeException")
    void duplicateEdgeProtection() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        graph.addEdge("A", "B", 5.0, 10.0, "Route 1");

        DuplicateEdgeException ex = assertThrows(DuplicateEdgeException.class,
                () -> graph.addEdge("A", "B", 6.0, 8.0, "Route 1 Alt"));
        assertEquals("A", ex.getSourceId());
        assertEquals("B", ex.getDestinationId());
    }

    @Test
    @DisplayName("Adding edge creates a directed path from source to destination only")
    void directedEdgeInsertion() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addEdge("A", "B", 5.0, 10.0, "Route 66");

        assertEquals(1, graph.getEdgeCount());
        assertTrue(graph.containsEdge("A", "B"));
        assertFalse(graph.containsEdge("B", "A"));

        List<Edge> outA = graph.getOutgoingEdges("A");
        assertEquals(1, outA.size());
        assertEquals("B", outA.getFirst().getDestination().getId());
        assertEquals(5.0, outA.getFirst().getDistanceKm());
        assertEquals(10.0, outA.getFirst().getTravelTimeMinutes());
        assertEquals("Route 66", outA.getFirst().getRoadName());

        // Source node should be populated on the edge
        assertTrue(outA.getFirst().getSource().isPresent());
        assertEquals("A", outA.getFirst().getSource().get().getId());

        // Verify directed nature: B should NOT automatically have an edge back to A
        List<Edge> outB = graph.getOutgoingEdges("B");
        assertTrue(outB.isEmpty(), "Roads are directed; reverse edge must not exist implicitly");
    }

    @Test
    @DisplayName("Bidirectional edge helper inserts directed edges in both directions")
    void bidirectionalEdgeInsertion() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addBidirectionalEdge("A", "B", 4.0, 8.0, "Two-Way Avenue");

        assertEquals(2, graph.getEdgeCount());
        assertTrue(graph.containsBidirectionalEdge("A", "B"));
        assertTrue(graph.containsBidirectionalEdge("B", "A"));

        assertEquals(1, graph.getOutgoingEdges("A").size());
        assertEquals(1, graph.getOutgoingEdges("B").size());
        assertEquals("B", graph.getOutgoingEdges("A").getFirst().getDestination().getId());
        assertEquals("A", graph.getOutgoingEdges("B").getFirst().getDestination().getId());
    }

    @Test
    @DisplayName("Bidirectional edge insertion fails safely if one direction already exists")
    void bidirectionalEdgePartialDuplicateProtection() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addEdge("A", "B", 4.0, 8.0);

        assertThrows(DuplicateEdgeException.class, () -> graph.addBidirectionalEdge("A", "B", 4.0, 8.0));
        assertThrows(DuplicateEdgeException.class, () -> graph.addBidirectionalEdge("B", "A", 4.0, 8.0));
    }

    @Test
    @DisplayName("Updating edge alters distance and travel time in-place")
    void updateEdgeSuccess() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addEdge("A", "B", 5.0, 10.0, "Expressway");

        graph.updateEdge("A", "B", 5.0, 15.0, "Expressway (Heavy Congestion)");

        Optional<Edge> edgeOpt = graph.getEdge("A", "B");
        assertTrue(edgeOpt.isPresent());
        assertEquals(15.0, edgeOpt.get().getTravelTimeMinutes());
        assertEquals("Expressway (Heavy Congestion)", edgeOpt.get().getRoadName());
    }

    @Test
    @DisplayName("Updating non-existent edge throws InvalidEdgeException")
    void updateNonExistentEdgeThrows() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        assertThrows(InvalidEdgeException.class, () -> graph.updateEdge("A", "B", 5.0, 10.0));
    }

    @Test
    @DisplayName("Removing directed edge safely decrements edge count and preserves nodes")
    void removeEdgeSuccess() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addEdge("A", "B", 5.0, 10.0);

        assertTrue(graph.removeEdge("A", "B"));
        assertFalse(graph.containsEdge("A", "B"));
        assertEquals(0, graph.getEdgeCount());
        assertEquals(2, graph.getNodeCount());

        // Removing non-existent edge returns false
        assertFalse(graph.removeEdge("A", "B"));
        assertFalse(graph.removeEdge("UNKNOWN_SRC", "UNKNOWN_DST"));
    }

    @Test
    @DisplayName("Removing bidirectional edge clears both directions")
    void removeBidirectionalEdgeSuccess() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addBidirectionalEdge("A", "B", 3.0, 5.0);

        assertTrue(graph.removeBidirectionalEdge("A", "B"));
        assertFalse(graph.containsEdge("A", "B"));
        assertFalse(graph.containsEdge("B", "A"));
        assertEquals(0, graph.getEdgeCount());
    }

    @Test
    @DisplayName("Removing a node removes all outgoing AND incoming edges, maintaining graph integrity")
    void removeNodeMaintainsGraphConsistency() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        // A -> B, B -> C, C -> B
        graph.addEdge("A", "B", 2.0, 3.0);
        graph.addEdge("B", "C", 4.0, 5.0);
        graph.addEdge("C", "B", 4.0, 5.0);

        assertEquals(3, graph.getEdgeCount());
        assertEquals(2, graph.getInDegree("B"));
        assertEquals(1, graph.getOutDegree("B"));

        // Remove node B
        assertTrue(graph.removeNode("B"));

        assertFalse(graph.containsNode("B"));
        assertEquals(2, graph.getNodeCount());

        // Crucial consistency check: A must no longer have an edge to B!
        assertEquals(0, graph.getOutgoingEdges("A").size(), "Incoming edge from A to B must be removed");
        assertEquals(0, graph.getOutgoingEdges("C").size(), "Incoming edge from C to B must be removed");
        assertEquals(0, graph.getEdgeCount(), "All edges referencing removed node B must be eliminated");

        // Verify every edge in graph points between existing nodes
        for (Edge edge : graph.getAllEdges()) {
            assertTrue(graph.containsNode(edge.getDestination().getId()));
            edge.getSource().ifPresent(src -> assertTrue(graph.containsNode(src.getId())));
        }
    }

    @Test
    @DisplayName("In-degree and out-degree reflect connectivity correctly")
    void inDegreeAndOutDegree() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge("A", "B", 2.0, 3.0);
        graph.addEdge("C", "B", 3.0, 4.0);

        assertEquals(0, graph.getInDegree("A"));
        assertEquals(1, graph.getOutDegree("A"));

        assertEquals(2, graph.getInDegree("B"));
        assertEquals(0, graph.getOutDegree("B"));
        assertEquals(2, graph.getIncomingEdges("B").size());
    }

    @Test
    @DisplayName("Adding edge with non-existent source or destination throws NodeNotFoundException")
    void edgeWithMissingNodeThrows() {
        graph.addNode(nodeA);

        assertThrows(NodeNotFoundException.class, () -> graph.addEdge("A", "MISSING", 5.0, 10.0));
        assertThrows(NodeNotFoundException.class, () -> graph.addEdge("MISSING", "A", 5.0, 10.0));
    }

    @Test
    @DisplayName("Adding pre-constructed edge with unregistered destination node throws NodeNotFoundException")
    void preConstructedEdgeMissingDestThrows() {
        graph.addNode(nodeA);
        Edge edgeToUnregistered = new Edge(nodeB, 5.0, 10.0); // nodeB is not added to graph

        assertThrows(NodeNotFoundException.class, () -> graph.addEdge("A", edgeToUnregistered));
    }

    @Test
    @DisplayName("Outgoing, incoming, all edges, and node collections must be unmodifiable")
    void encapsulationProtection() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addEdge("A", "B", 2.0, 3.0);

        Edge dummyEdge = new Edge(nodeC, 1.0, 1.0);
        assertThrows(UnsupportedOperationException.class, () -> graph.getOutgoingEdges("A").add(dummyEdge));
        assertThrows(UnsupportedOperationException.class, () -> graph.getIncomingEdges("B").add(dummyEdge));
        assertThrows(UnsupportedOperationException.class, () -> graph.getAllEdges().add(dummyEdge));
        assertThrows(UnsupportedOperationException.class, () -> graph.getAllNodes().add(nodeC));
    }

    @Test
    @DisplayName("Edge insertion validates distances and travel times")
    void edgeCostValidation() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        assertThrows(InvalidCostException.class, () -> graph.addEdge("A", "B", -1.0, 5.0));
        assertThrows(InvalidCostException.class, () -> graph.addEdge("A", "B", 5.0, -2.0));
    }

    @Test
    @DisplayName("Querying non-existent node throws NodeNotFoundException")
    void getRequiredNodeNotFound() {
        assertThrows(NodeNotFoundException.class, () -> graph.getRequiredNode("UNKNOWN"));
        assertThrows(NodeNotFoundException.class, () -> graph.getOutgoingEdges("UNKNOWN"));
        assertThrows(NodeNotFoundException.class, () -> graph.getIncomingEdges("UNKNOWN"));
    }

    @Test
    @DisplayName("Clear removes all nodes and edges")
    void clearGraph() {
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addEdge("A", "B", 2.0, 3.0);

        graph.clear();
        assertTrue(graph.isEmpty());
        assertEquals(0, graph.getNodeCount());
        assertEquals(0, graph.getEdgeCount());
    }
}
