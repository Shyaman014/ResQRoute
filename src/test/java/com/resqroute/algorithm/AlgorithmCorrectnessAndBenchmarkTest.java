package com.resqroute.algorithm;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.optimization.AssignmentCostModel;
import com.resqroute.optimization.AssignmentPair;
import com.resqroute.optimization.FleetAssignmentPlan;
import com.resqroute.optimization.PriorityAwareCostModel;
import com.resqroute.service.AmbulanceFleetService;
import com.resqroute.service.EmergencyManagementService;
import com.resqroute.service.FleetAssignmentOptimizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 22: Algorithm Correctness, Mathematical Optimality & Benchmark Tests")
class AlgorithmCorrectnessAndBenchmarkTest {

    // ==========================================
    // 1. Reference Solver for Graph Search
    // ==========================================

    record ReferencePath(List<String> nodeIds, double totalTravelTime, double totalDistance) {}

    static List<ReferencePath> findAllSimplePaths(RoadGraph graph, String current, String dest,
                                                 Set<String> visited, List<String> currentPath,
                                                 double currentTime, double currentDist) {
        currentPath.add(current);
        visited.add(current);

        List<ReferencePath> results = new ArrayList<>();
        if (current.equals(dest)) {
            results.add(new ReferencePath(new ArrayList<>(currentPath), currentTime, currentDist));
        } else {
            for (Edge edge : graph.getOutgoingEdges(current)) {
                if (edge.isAvailable() && !visited.contains(edge.getDestination().getId())) {
                    results.addAll(findAllSimplePaths(
                            graph,
                            edge.getDestination().getId(),
                            dest,
                            visited,
                            currentPath,
                            currentTime + edge.getTravelTimeMinutes(),
                            currentDist + edge.getDistanceKm()
                    ));
                }
            }
        }

        visited.remove(current);
        currentPath.remove(currentPath.size() - 1);
        return results;
    }

    // ==========================================
    // 2. Dijkstra vs A* vs Brute-Force Reference
    // ==========================================

    @Test
    @DisplayName("Dijkstra and A* find identical globally optimal travel times on 25 randomized graphs")
    void testDijkstraAndAStarMatchReferenceOnRandomGraphs() {
        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        AStarRoutingAlgorithm aStar = new AStarRoutingAlgorithm();

        Random random = new Random(12345L);

        for (int seed = 1; seed <= 25; seed++) {
            RoadGraph graph = new RoadGraph();
            int nodeCount = 8;

            for (int i = 0; i < nodeCount; i++) {
                double lat = 12.0 + (i * 0.05) + (random.nextDouble() * 0.02);
                double lon = 77.0 + (i * 0.05) + (random.nextDouble() * 0.02);
                graph.addNode(new Node("N" + i, lat, lon, "Node " + i));
            }

            // Create random directed edges with distance based on geographic coordinates
            for (int i = 0; i < nodeCount; i++) {
                for (int j = 0; j < nodeCount; j++) {
                    if (i != j && random.nextDouble() < 0.45) {
                        Node u = graph.getRequiredNode("N" + i);
                        Node v = graph.getRequiredNode("N" + j);
                        double straightLine = u.getCoordinates().haversineDistanceKm(v.getCoordinates());
                        double dist = Math.max(0.5, straightLine * (1.1 + random.nextDouble() * 0.2));
                        double speedKmh = 50.0 + random.nextDouble() * 30.0; // speeds between 50 and 80 km/h (below 120 km/h bound)
                        double timeMinutes = (dist / speedKmh) * 60.0;
                        graph.addEdge("N" + i, "N" + j, dist, timeMinutes, "Edge_" + i + "_" + j);
                    }
                }
            }

            String source = "N0";
            String dest = "N" + (nodeCount - 1);

            List<ReferencePath> allPaths = findAllSimplePaths(graph, source, dest, new HashSet<>(), new ArrayList<>(), 0.0, 0.0);
            allPaths.sort(Comparator.comparingDouble(ReferencePath::totalTravelTime));

            RouteResult dResult = dijkstra.findFastestRoute(graph, source, dest);
            RouteResult aResult = aStar.findFastestRoute(graph, source, dest);

            assertEquals(dResult.isReachable(), aResult.isReachable(), "Reachability mismatch on seed " + seed);

            if (allPaths.isEmpty()) {
                assertFalse(dResult.isReachable());
                assertFalse(aResult.isReachable());
            } else {
                assertTrue(dResult.isReachable());
                assertTrue(aResult.isReachable());

                double groundTruthTime = allPaths.getFirst().totalTravelTime();
                assertEquals(groundTruthTime, dResult.getTotalTravelTimeMinutes(), 1e-6, "Dijkstra optimal mismatch on seed " + seed);
                assertEquals(groundTruthTime, aResult.getTotalTravelTimeMinutes(), 1e-6, "A* optimal mismatch on seed " + seed);
                assertEquals(dResult.getTotalTravelTimeMinutes(), aResult.getTotalTravelTimeMinutes(), 1e-6);
            }
        }
    }

    // ==========================================
    // 3. A* Heuristic Admissibility Verification
    // ==========================================

    @Test
    @DisplayName("Verify A* Euclidean distance heuristic satisfies strict admissibility: h(n) <= true distance")
    void testAStarHeuristicAdmissibility() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("A", 12.0, 77.0, "Node A"));
        graph.addNode(new Node("B", 12.02, 77.03, "Node B"));
        graph.addNode(new Node("C", 12.05, 77.06, "Node C"));
        graph.addNode(new Node("GOAL", 12.10, 77.10, "Goal"));

        graph.addEdge("A", "B", 4.0, 4.0, "A-B");
        graph.addEdge("B", "C", 5.0, 5.0, "B-C");
        graph.addEdge("C", "GOAL", 6.0, 6.0, "C-GOAL");

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();

        // For each node, verify straight-line heuristic time <= actual shortest path travel time to GOAL
        for (Node node : graph.getAllNodes()) {
            if (node.getId().equals("GOAL")) continue;

            RouteResult trueRoute = dijkstra.findFastestRoute(graph, node.getId(), "GOAL");
            assertTrue(trueRoute.isReachable());

            double straightLineDist = node.getCoordinates().haversineDistanceKm(graph.getRequiredNode("GOAL").getCoordinates());
            // At max speed 120 km/h: time = (dist / 120) * 60 = dist / 2.0
            double heuristicTime = (straightLineDist / 120.0) * 60.0;

            assertTrue(heuristicTime <= trueRoute.getTotalTravelTimeMinutes() + 1e-9,
                    String.format("Heuristic violated admissibility at %s: h=%f > true=%f",
                            node.getId(), heuristicTime, trueRoute.getTotalTravelTimeMinutes()));
        }
    }

    // ==========================================
    // 4. Yen's K-Shortest Paths vs Exhaustive Simple Paths
    // ==========================================

    @Test
    @DisplayName("Yen's K-Shortest Paths returns top K paths exactly matching sorted exhaustive simple paths")
    void testYenKShortestPathsMatchesExhaustiveSimplePaths() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("S", 12.0, 77.0, "Start"));
        graph.addNode(new Node("A", 12.1, 77.0, "Node A"));
        graph.addNode(new Node("B", 12.0, 77.1, "Node B"));
        graph.addNode(new Node("C", 12.1, 77.1, "Node C"));
        graph.addNode(new Node("D", 12.2, 77.2, "Destination"));

        // Multiple distinct paths from S to D
        // Path 1: S -> A -> C -> D : 2 + 1 + 3 = 6 mins
        // Path 2: S -> B -> C -> D : 3 + 2 + 3 = 8 mins
        // Path 3: S -> A -> D : 2 + 7 = 9 mins
        // Path 4: S -> B -> D : 3 + 8 = 11 mins
        graph.addEdge("S", "A", 2.0, 2.0, "S-A");
        graph.addEdge("S", "B", 3.0, 3.0, "S-B");
        graph.addEdge("A", "C", 1.0, 1.0, "A-C");
        graph.addEdge("B", "C", 2.0, 2.0, "B-C");
        graph.addEdge("C", "D", 3.0, 3.0, "C-D");
        graph.addEdge("A", "D", 7.0, 7.0, "A-D");
        graph.addEdge("B", "D", 8.0, 8.0, "B-D");

        List<ReferencePath> allSimplePaths = findAllSimplePaths(graph, "S", "D", new HashSet<>(), new ArrayList<>(), 0.0, 0.0);
        allSimplePaths.sort(Comparator.comparingDouble(ReferencePath::totalTravelTime));

        YenKShortestPathsAlgorithm yen = new YenKShortestPathsAlgorithm();
        int k = 4;
        List<RouteResult> yenPaths = yen.findKShortestPaths(graph, "S", "D", k);

        assertEquals(k, yenPaths.size());
        for (int i = 0; i < k; i++) {
            assertEquals(allSimplePaths.get(i).totalTravelTime(), yenPaths.get(i).getTotalTravelTimeMinutes(), 1e-6,
                    "Rank " + i + " route travel time mismatch");
            assertEquals(allSimplePaths.get(i).nodeIds(), yenPaths.get(i).getNodeIds(),
                    "Rank " + i + " route node sequence mismatch");
        }
    }

    // ==========================================
    // 5. Fleet Assignment Optimizer vs Brute Force Permutation
    // ==========================================

    @Test
    @DisplayName("FleetAssignmentOptimizer finds globally optimal matching matching exhaustive permutation solver")
    void testFleetAssignmentOptimizerMatchesBruteForceOptimalMatching() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("AMB_LOC1", 12.0, 77.0, "Station 1"));
        graph.addNode(new Node("AMB_LOC2", 12.1, 77.1, "Station 2"));
        graph.addNode(new Node("AMB_LOC3", 12.2, 77.2, "Station 3"));
        graph.addNode(new Node("CASE_LOC1", 12.05, 77.05, "Scene 1"));
        graph.addNode(new Node("CASE_LOC2", 12.15, 77.15, "Scene 2"));
        graph.addNode(new Node("CASE_LOC3", 12.25, 77.25, "Scene 3"));

        // Interconnect stations and scenes with varying costs
        graph.addEdge("AMB_LOC1", "CASE_LOC1", 3.0, 5.0, "A1-C1");
        graph.addEdge("AMB_LOC1", "CASE_LOC2", 7.0, 12.0, "A1-C2");
        graph.addEdge("AMB_LOC1", "CASE_LOC3", 10.0, 20.0, "A1-C3");

        graph.addEdge("AMB_LOC2", "CASE_LOC1", 8.0, 14.0, "A2-C1");
        graph.addEdge("AMB_LOC2", "CASE_LOC2", 2.0, 4.0, "A2-C2");
        graph.addEdge("AMB_LOC2", "CASE_LOC3", 6.0, 10.0, "A2-C3");

        graph.addEdge("AMB_LOC3", "CASE_LOC1", 12.0, 22.0, "A3-C1");
        graph.addEdge("AMB_LOC3", "CASE_LOC2", 5.0, 9.0, "A3-C2");
        graph.addEdge("AMB_LOC3", "CASE_LOC3", 1.5, 3.0, "A3-C3");

        AmbulanceFleetService fleetService = new AmbulanceFleetService(graph);
        EmergencyManagementService emergencyService = new EmergencyManagementService(graph, fleetService);

        Ambulance amb1 = fleetService.registerAmbulance("AMB-1", "AMB_LOC1");
        Ambulance amb2 = fleetService.registerAmbulance("AMB-2", "AMB_LOC2");
        Ambulance amb3 = fleetService.registerAmbulance("AMB-3", "AMB_LOC3");

        Instant now = Instant.now();
        EmergencyCase case1 = new EmergencyCase("C1", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "CASE_LOC1", now, "Case 1");
        EmergencyCase case2 = new EmergencyCase("C2", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "CASE_LOC2", now, "Case 2");
        EmergencyCase case3 = new EmergencyCase("C3", EmergencyType.TRAUMA, EmergencySeverity.MEDIUM, "CASE_LOC3", now, "Case 3");

        List<EmergencyCase> emergencies = List.of(case1, case2, case3);
        List<Ambulance> ambulances = List.of(amb1, amb2, amb3);

        // Brute-force solver: Enumerate all 6 permutations of ambulances -> cases
        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        AssignmentCostModel costModel = new PriorityAwareCostModel();

        List<List<Integer>> permutations = List.of(
                List.of(0, 1, 2), List.of(0, 2, 1),
                List.of(1, 0, 2), List.of(1, 2, 0),
                List.of(2, 0, 1), List.of(2, 1, 0)
        );

        double minBruteForceCost = Double.POSITIVE_INFINITY;
        for (List<Integer> perm : permutations) {
            double permCost = 0.0;
            for (int i = 0; i < 3; i++) {
                Ambulance a = ambulances.get(perm.get(i));
                EmergencyCase c = emergencies.get(i);
                RouteResult route = dijkstra.findFastestRoute(graph, a.getCurrentLocationNodeId(), c.getPickupLocationNodeId());
                permCost += costModel.calculateCost(a, c, route.getTotalTravelTimeMinutes(), now);
            }
            if (permCost < minBruteForceCost) {
                minBruteForceCost = permCost;
            }
        }

        // Run FleetAssignmentOptimizer
        FleetAssignmentOptimizer optimizer = new FleetAssignmentOptimizer(
                graph, dijkstra, fleetService, emergencyService, null,
                com.resqroute.dispatch.DefaultCapabilityMatcher.INSTANCE,
                costModel, Clock.fixed(now, java.time.ZoneOffset.UTC)
        );

        FleetAssignmentPlan plan = optimizer.computeOptimalPlan(emergencies, ambulances);

        assertEquals(3, plan.getAssignments().size());
        assertEquals(minBruteForceCost, plan.getTotalEstimatedCost(), 1e-4, "Optimizer total cost must match brute-force global minimum");
    }

    // ==========================================
    // 6. Network Topology Edge Cases
    // ==========================================

    @Test
    @DisplayName("Topology Edge Cases: Disconnected graphs, one-way directed loops, equal cost alternatives")
    void testTopologyEdgeCases() {
        RoadGraph graph = new RoadGraph();
        graph.addNode(new Node("ISLAND_A1", 12.0, 77.0, "Island A1"));
        graph.addNode(new Node("ISLAND_A2", 12.1, 77.1, "Island A2"));
        graph.addNode(new Node("ISLAND_B1", 13.0, 78.0, "Island B1"));

        // Island A has internal connected edge
        graph.addEdge("ISLAND_A1", "ISLAND_A2", 5.0, 5.0, "A1-A2");

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();

        // 1. Disconnected destination
        RouteResult unreachable = dijkstra.findFastestRoute(graph, "ISLAND_A1", "ISLAND_B1");
        assertFalse(unreachable.isReachable());
        assertEquals(Double.POSITIVE_INFINITY, unreachable.getTotalTravelTimeMinutes());

        // 2. One-way directed road: reverse lookup fails
        RouteResult oneWayReverse = dijkstra.findFastestRoute(graph, "ISLAND_A2", "ISLAND_A1");
        assertFalse(oneWayReverse.isReachable());

        // 3. Identical equal-cost alternative routes are handled deterministically
        graph.addNode(new Node("EQ_TOP", 12.05, 77.02, "Eq Top"));
        graph.addNode(new Node("EQ_BOT", 12.05, 77.08, "Eq Bottom"));

        graph.addEdge("ISLAND_A1", "EQ_TOP", 2.0, 2.0, "A1-Top");
        graph.addEdge("EQ_TOP", "ISLAND_A2", 2.0, 2.0, "Top-A2");

        graph.addEdge("ISLAND_A1", "EQ_BOT", 2.0, 2.0, "A1-Bot");
        graph.addEdge("EQ_BOT", "ISLAND_A2", 2.0, 2.0, "Bot-A2");

        RouteResult eqResult1 = dijkstra.findFastestRoute(graph, "ISLAND_A1", "ISLAND_A2");
        RouteResult eqResult2 = dijkstra.findFastestRoute(graph, "ISLAND_A1", "ISLAND_A2");
        assertTrue(eqResult1.isReachable());
        assertEquals(4.0, eqResult1.getTotalTravelTimeMinutes(), 1e-6);
        assertEquals(eqResult1.getNodeIds(), eqResult2.getNodeIds(), "Deterministic path selection for equal-cost routes");
    }
}
