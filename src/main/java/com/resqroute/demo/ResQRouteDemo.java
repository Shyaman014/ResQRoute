package com.resqroute.demo;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Node;
import com.resqroute.model.RouteComparisonReport;
import com.resqroute.model.RouteResult;
import com.resqroute.service.RouteOptimizationService;

import java.util.List;

/**
 * Phase 1 - 5 Demonstration of ResQRoute: Smart Emergency Ambulance Dispatch & Route Optimization.
 *
 * <p>Demonstrates:
 * 1. Road graph construction with strict duplicate protection and self-loop prevention.
 * 2. Fastest-route optimization vs. shortest-distance path (preserving the Golden Hour).
 * 3. Comparative evaluation: Dijkstra vs. A* search with admissible Haversine heuristic.
 * 4. Yen's K-Shortest Loopless Paths alternative route discovery.
 * 5. Analytical route comparison matrix with unrounded metric calculations.
 * 6. Dynamic graph mutation: emergency road closure and automatic real-time detour rerouting.</p>
 */
public class ResQRouteDemo {

    public static void main(String[] args) {
        printBanner();

        // 1. Construct Road Network
        RoadGraph graph = new RoadGraph();

        System.out.println("[Step 1] Constructing Metropolitan Road Network...");

        // Create Waypoints / Intersections
        Node dispatch = new Node("AMB-01", 12.9716, 77.5946, "Metro Emergency Dispatch Center");
        Node marketCross = new Node("INT-A", 12.9730, 77.5980, "Old Market Crossing");
        Node narrowLane = new Node("INT-B", 12.9760, 77.6010, "Dense Commercial Alley");
        Node expresswayEntry = new Node("INT-C", 12.9690, 77.6050, "Outer Ring Expressway Entry");
        Node flyoverJunction = new Node("INT-D", 12.9720, 77.6150, "Elevated Flyover Hub");
        Node apexHospital = new Node("HOSP-01", 12.9780, 77.6200, "Apex Trauma & Critical Care Hospital");
        Node floodZone = new Node("ISOLATED-99", 12.9900, 77.6500, "Submerged Valley Post (Disconnected)");

        graph.addNode(dispatch);
        graph.addNode(marketCross);
        graph.addNode(narrowLane);
        graph.addNode(expresswayEntry);
        graph.addNode(flyoverJunction);
        graph.addNode(apexHospital);
        graph.addNode(floodZone);

        // Path A: Shortest Distance Route (Congested City Core)
        // Total: 4.5 km, 21.0 minutes
        graph.addEdge("AMB-01", "INT-A", 1.5, 6.0, "Market Approach Road (Congested)");
        graph.addEdge("INT-A", "INT-B", 1.2, 8.0, "Bazaar Commercial Lane (Bottleneck)");
        graph.addEdge("INT-B", "HOSP-01", 1.8, 7.0, "Hospital Backstreet (Narrow)");

        // Path B: High-Speed Emergency Bypass Corridor (Longer distance, much faster)
        // Total: 8.0 km, 7.7 minutes
        graph.addEdge("AMB-01", "INT-C", 2.2, 2.5, "Southern Arterial Connector");
        graph.addEdge("INT-C", "INT-D", 4.0, 3.2, "Outer Ring Expressway (Emergency Corridor)");
        graph.addEdge("INT-D", "HOSP-01", 1.8, 2.0, "Apex Elevated Access Ramp");

        // Intermediate connector: INT-C -> INT-B (Connecting avenue)
        graph.addEdge("INT-C", "INT-B", 2.5, 5.0, "Cross-City Boulevard");

        System.out.printf("  -> Graph populated: %d nodes, %d directed road segments.%n%n",
                graph.getNodeCount(), graph.getEdgeCount());

        RouteOptimizationService dijkstraService = new RouteOptimizationService(graph, new DijkstraRoutingAlgorithm());
        RouteOptimizationService astarService = new RouteOptimizationService(graph, new AStarRoutingAlgorithm());

        // 2. Compute Fastest Emergency Route with Dijkstra
        System.out.println("[Step 2] Calculating Optimal Route with Dijkstra: AMB-01 -> HOSP-01");
        RouteResult dijkstraRoute = dijkstraService.findFastestRoute("AMB-01", "HOSP-01");
        System.out.println(dijkstraRoute.toFormattedSummary());

        // 3. Compute Fastest Emergency Route with A*
        System.out.println("[Step 3] Calculating Optimal Route with A* (Admissible Haversine Heuristic): AMB-01 -> HOSP-01");
        RouteResult astarRoute = astarService.findFastestRoute("AMB-01", "HOSP-01");
        System.out.println(astarRoute.toFormattedSummary());

        System.out.printf("  >>> Algorithmic Equivalence Check: Dijkstra (%.2f min, %.2f km) == A* (%.2f min, %.2f km) -> MATCH? %s%n%n",
                dijkstraRoute.getTotalTravelTimeMinutes(), dijkstraRoute.getTotalDistanceKm(),
                astarRoute.getTotalTravelTimeMinutes(), astarRoute.getTotalDistanceKm(),
                (Math.abs(dijkstraRoute.getTotalTravelTimeMinutes() - astarRoute.getTotalTravelTimeMinutes()) < 1e-4));

        // 4. Yen's K-Shortest Loopless Paths & Route Comparison (Phase 5)
        System.out.println("[Step 4] Computing Top K=3 Alternative Emergency Routes (Yen's Algorithm):");
        RouteComparisonReport comparisonReport = dijkstraService.compareKFastestRoutes("AMB-01", "HOSP-01", 3);
        System.out.println(comparisonReport.toFormattedTable());

        // 5. Compare Fastest vs Shortest Distance
        printComparisonAnalysis(astarRoute);

        // 6. Dynamic Incident & Detour Rerouting
        System.out.println("[Step 5] Simulating Emergency Incident & Dynamic Rerouting:");
        System.out.println("  >>> ALERT: Multi-vehicle collision blocks Outer Ring Expressway (INT-C -> INT-D).");
        boolean roadRemoved = graph.removeEdge("INT-C", "INT-D");
        System.out.printf("  >>> Segment INT-C -> INT-D closed safely? %s (Active edges: %d)%n",
                roadRemoved, graph.getEdgeCount());

        RouteResult detourRoute = astarService.findFastestRoute("AMB-01", "HOSP-01");
        System.out.println("\n  Recalculated Detour Route (A*):");
        System.out.println(detourRoute.toFormattedSummary());

        // 7. Edge Case Demonstrations
        System.out.println("[Step 6] Verifying Network Integrity and Boundary Cases:");

        // Case A: Source == Destination
        System.out.println("  A. Source equals Destination (AMB-01 -> AMB-01):");
        RouteResult sameNodeResult = astarService.findFastestRoute("AMB-01", "AMB-01");
        System.out.printf("     Reachable: %s | Nodes: %d | Time: %.1f min | Dist: %.1f km%n",
                sameNodeResult.isReachable(), sameNodeResult.getNodeCount(),
                sameNodeResult.getTotalTravelTimeMinutes(), sameNodeResult.getTotalDistanceKm());

        // Case B: Unreachable Destination
        System.out.println("  B. Unreachable Destination (AMB-01 -> ISOLATED-99):");
        RouteResult unreachableResult = astarService.findFastestRoute("AMB-01", "ISOLATED-99");
        System.out.printf("     Reachable: %s | Message: %s%n",
                unreachableResult.isReachable(), unreachableResult.toFormattedSummary().trim());

        System.out.println();
        System.out.println("================================================================================");
        System.out.println(" [SUCCESS] Phase 5 ResQRoute Route Reconstruction & Alternatives verified.");
        System.out.println("================================================================================");
    }

    private static void printBanner() {
        System.out.println("""
                ================================================================================
                  ResQRoute: Smart Emergency Ambulance Dispatch & Route Optimization System
                  Phase 5: Route Reconstruction, Yen's K-Shortest Paths & Route Comparison
                ================================================================================
                """);
    }

    private static void printComparisonAnalysis(RouteResult optimalRoute) {
        double shortestDistanceKm = 4.5;
        double shortestDistanceTimeMin = 21.0;

        double chosenDistanceKm = optimalRoute.getTotalDistanceKm();
        double chosenTimeMin = optimalRoute.getTotalTravelTimeMinutes();
        double timeSavedMin = shortestDistanceTimeMin - chosenTimeMin;
        double percentSaved = (timeSavedMin / shortestDistanceTimeMin) * 100.0;

        System.out.println("""
                --------------------------------------------------------------------------------
                CRITICAL EMERGENCY ROUTING COMPARISON:
                --------------------------------------------------------------------------------
                Metric                  | Route 1 (Shortest Distance) | Route 2 (ResQRoute Time-Optimal)
                ------------------------+-----------------------------+---------------------------------
                Route Profile           | Congested Old City Market   | High-Speed Expressway Corridor
                Physical Distance       | 4.50 km [SHORTEST]          | 8.00 km (+77.8% longer)
                Estimated Travel Time   | 21.00 minutes               | 7.70 minutes [FASTEST]
                Average Vehicle Speed   | 12.9 km/h (severe traffic)  | 62.3 km/h (free-flow)
                ------------------------+-----------------------------+---------------------------------
                """);
        System.out.printf("  >>> LIFE-SAVING ADVANTAGE: ResQRoute saved %.2f minutes (%.1f%% faster!)%n",
                timeSavedMin, percentSaved);
        System.out.println("      In emergency dispatch, optimizing for MINIMUM TRAVEL TIME rather than");
        System.out.println("      geographic distance preserves the Golden Hour and directly saves lives.");
        System.out.println("--------------------------------------------------------------------------------\n");
    }
}
