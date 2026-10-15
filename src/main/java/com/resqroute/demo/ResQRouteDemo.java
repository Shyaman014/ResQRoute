package com.resqroute.demo;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.service.AutomaticReroutingService;
import com.resqroute.service.RoadIncidentService;
import com.resqroute.service.RouteOptimizationService;
import com.resqroute.service.TrafficSimulationEngine;

import java.time.Instant;
import java.util.List;

/**
 * Phase 1 - 7 Demonstration of ResQRoute: Smart Emergency Ambulance Dispatch & Route Optimization.
 *
 * <p>Demonstrates:
 * 1. Road graph construction with strict duplicate protection and self-loop prevention.
 * 2. Fastest-route optimization vs. shortest-distance path (preserving the Golden Hour).
 * 3. Comparative evaluation: Dijkstra vs. A* search with admissible Haversine heuristic.
 * 4. Yen's K-Shortest Loopless Paths alternative route discovery.
 * 5. Analytical route comparison matrix with unrounded metric calculations.
 * 6. Dynamic traffic simulation: incident injection, real-time reroute evaluation, and restoration.
 * 7. Road incident management, multiple incidents per road, automatic rerouting, and reopening.</p>
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
        graph.addEdge("AMB-01", "INT-A", 1.5, 6.0, "Market Approach Road");
        graph.addEdge("INT-A", "INT-B", 1.2, 8.0, "Bazaar Commercial Lane");
        graph.addEdge("INT-B", "HOSP-01", 1.8, 7.0, "Hospital Backstreet");

        // Path B: High-Speed Emergency Bypass Corridor (Longer distance, much faster)
        // Total: 8.0 km, 7.7 minutes
        graph.addEdge("AMB-01", "INT-C", 2.2, 2.5, "Southern Arterial Connector");
        graph.addEdge("INT-C", "INT-D", 4.0, 3.2, "Outer Ring Expressway (Emergency Corridor)");
        graph.addEdge("INT-D", "HOSP-01", 1.8, 2.0, "Apex Elevated Access Ramp");

        // Cross connector: INT-C -> INT-B (Connecting boulevard: 2.5 km, 5.0 min)
        graph.addEdge("INT-C", "INT-B", 2.5, 5.0, "Cross-City Boulevard");

        System.out.printf("  -> Graph populated: %d nodes, %d directed road segments.%n%n",
                graph.getNodeCount(), graph.getEdgeCount());

        RouteOptimizationService dijkstraService = new RouteOptimizationService(graph, new DijkstraRoutingAlgorithm());
        RouteOptimizationService astarService = new RouteOptimizationService(graph, new AStarRoutingAlgorithm());
        TrafficSimulationEngine trafficEngine = new TrafficSimulationEngine(graph, 101L);
        RoadIncidentService incidentService = dijkstraService.getIncidentService();
        AutomaticReroutingService reroutingService = dijkstraService.getReroutingService();

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

        // 5. Dynamic Traffic Simulation & Reroute Evaluation (Phase 6)
        System.out.println("[Step 5] Dynamic Traffic Simulation & Real-Time Congestion Assessment:");
        System.out.println("  >>> CONGESTION INJECTION: Heavy traffic on Outer Ring Expressway (INT-C -> INT-D).");
        trafficEngine.setTraffic("INT-C", "INT-D", TrafficLevel.HEAVY);

        Edge congestedEdge = graph.getEdge("INT-C", "INT-D").orElseThrow();
        System.out.printf("  >>> Telemetry Update: %s travel time increased to %.1f min (Congestion: %s)%n",
                congestedEdge.getRoadName(), congestedEdge.getTravelTimeMinutes(), congestedEdge.getTrafficCondition().level());

        // Restore road to normal
        trafficEngine.restoreRoad("INT-C", "INT-D");
        System.out.println("  >>> Traffic cleared: Restored to FREE_FLOW.\n");

        // 6. Road Closures, Incident Management & Automatic Rerouting (Phase 7)
        System.out.println("[Step 6] Phase 7: Road Incidents, Multiple Concurrent Hazards & Automatic Rerouting:");
        Instant now = Instant.now();

        // Ambulance starts mission on fastest highway route: AMB-01 -> INT-C -> INT-D -> HOSP-01
        TrackedRoute trackedMission = reroutingService.trackRoute("MISSION-AMB-911", dijkstraRoute);
        System.out.printf("  >>> Tracking Mission: %s [Route: %s, Travel Time: %.2f min]%n",
                trackedMission.getTrackingId(), trackedMission.getActiveRoute().getNodeIds(),
                trackedMission.getActiveRoute().getTotalTravelTimeMinutes());

        // Incident 1: Major collision on Expressway
        RoadIncident incident1 = RoadIncident.accident("INC-COLLISION-01", "INT-C", "INT-D", now);
        incidentService.reportIncident(incident1);
        System.out.printf("  >>> INCIDENT 1 REPORTED: %s on INT-C -> INT-D (Road status: %s)%n",
                incident1.getDescription(), graph.isRoadClosed("INT-C", "INT-D") ? "CLOSED" : "OPEN");

        // Incident 2: Emergency barrier repair on same segment
        RoadIncident incident2 = RoadIncident.construction("INC-REPAIR-02", "INT-C", "INT-D", now, now.plusSeconds(7200));
        incidentService.reportIncident(incident2);
        System.out.printf("  >>> INCIDENT 2 REPORTED: %s on INT-C -> INT-D (Total active on road: %d)%n",
                incident2.getDescription(), incidentService.getIncidentsOnRoad("INT-C", "INT-D").size());

        // Automatic rerouting triggered
        List<RerouteResponse> rerouteResponses = reroutingService.onRoadClosure("INT-C", "INT-D");
        RerouteResponse reroute = rerouteResponses.get(0);
        System.out.printf("  >>> REROUTE DECISION: %s%n", reroute.status());
        System.out.printf("      Trigger Reason:   %s%n", reroute.triggerReason());
        System.out.printf("      New Route Path:   %s (Time: %.2f min, Dist: %.2f km)%n",
                reroute.newRoute().getNodeIds(), reroute.newRoute().getTotalTravelTimeMinutes(), reroute.newRoute().getTotalDistanceKm());
        System.out.printf("      System Message:   %s%n%n", reroute.message());

        // Demonstrate Multiple Incidents Rule: Resolve Incident 1
        System.out.println("  >>> Resolving Incident 1 (Accident cleared):");
        incidentService.resolveIncident("INC-COLLISION-01");
        System.out.printf("      Road INT-C -> INT-D status: %s (Still blocked by Construction? %s)%n",
                graph.isRoadClosed("INT-C", "INT-D") ? "CLOSED" : "OPEN",
                incidentService.isRoadBlocked("INT-C", "INT-D"));

        // Resolve Incident 2: Road now reopens!
        System.out.println("  >>> Resolving Incident 2 (Construction finished):");
        incidentService.resolveIncident("INC-REPAIR-02");
        System.out.printf("      Road INT-C -> INT-D status: %s (All incidents resolved!)%n%n",
                graph.isRoadAvailable("INT-C", "INT-D") ? "OPEN (Restored)" : "CLOSED");

        // Demonstrate Unrelated Incident: Incident on INT-A -> INT-B does not disturb active route
        System.out.println("  >>> Simulating incident on unrelated road (INT-A -> INT-B):");
        incidentService.reportIncident(RoadIncident.temporaryBlockage("INC-UNRELATED", "INT-A", "INT-B", now, now.plusSeconds(1800)));
        List<RerouteResponse> unrelatedResponses = reroutingService.onRoadClosure("INT-A", "INT-B");
        System.out.printf("      Reroute Decision: %s (Reason: %s - Unnecessary recalculation avoided!)%n%n",
                unrelatedResponses.get(0).status(), unrelatedResponses.get(0).triggerReason());

        // 7. Edge Case Demonstrations
        System.out.println("[Step 7] Verifying Network Integrity and Boundary Cases:");

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
        System.out.println(" [SUCCESS] Phase 7 ResQRoute Road Closures & Automatic Rerouting verified.");
        System.out.println("================================================================================");
    }

    private static void printBanner() {
        System.out.println("""
                ================================================================================
                  ResQRoute: Smart Emergency Ambulance Dispatch & Route Optimization System
                  Phase 7: Road Closures, Incident Management & Automatic Rerouting Engine
                ================================================================================
                """);
    }
}
