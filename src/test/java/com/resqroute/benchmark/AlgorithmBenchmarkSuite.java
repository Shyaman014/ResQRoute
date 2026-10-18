package com.resqroute.benchmark;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.YenKShortestPathsAlgorithm;
import com.resqroute.dispatch.DispatchResult;
import com.resqroute.graph.RoadGraph;
import com.resqroute.hospital.FastestHospitalRankingPolicy;
import com.resqroute.hospital.HospitalSelectionResult;
import com.resqroute.model.*;
import com.resqroute.optimization.AssignmentCostModel;
import com.resqroute.optimization.FleetAssignmentPlan;
import com.resqroute.optimization.PriorityAwareCostModel;
import com.resqroute.scheduling.AgingDispatchPolicy;
import com.resqroute.service.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark suite containing isolated, reproducible workloads for:
 * 1. Dijkstra vs A* Routing.
 * 2. Yen's K-Shortest Alternative Routes.
 * 3. Greedy Ambulance Dispatch vs Optimal MCMF Fleet Matching.
 * 4. Hospital Selection & Bed Reservation across network scales.
 * 5. Dynamic Graph Mutations & Edge Lookups.
 */
public final class AlgorithmBenchmarkSuite {

    private final BenchmarkHarness harness;

    public AlgorithmBenchmarkSuite(BenchmarkHarness harness) {
        this.harness = harness;
    }

    // ==========================================
    // 1. Dijkstra vs A* Routing Benchmark
    // ==========================================

    public List<BenchmarkResult> benchmarkRouting(SyntheticNetworkGenerator.BenchmarkDataset dataset, String scaleName) {
        List<BenchmarkResult> results = new ArrayList<>();
        RoadGraph graph = dataset.graph();
        List<SyntheticNetworkGenerator.QueryPair> pairs = dataset.routingPairs();

        DijkstraRoutingAlgorithm dijkstra = new DijkstraRoutingAlgorithm();
        AStarRoutingAlgorithm aStar = new AStarRoutingAlgorithm();

        // Correctness verification before benchmark: verify Dijkstra and A* find equivalent costs
        for (SyntheticNetworkGenerator.QueryPair pair : pairs) {
            RouteResult dRes = dijkstra.findFastestRoute(graph, pair.sourceId(), pair.destinationId());
            RouteResult aRes = aStar.findFastestRoute(graph, pair.sourceId(), pair.destinationId());
            if (dRes.isReachable() && aRes.isReachable()) {
                double diff = Math.abs(dRes.getTotalTravelTimeMinutes() - aRes.getTotalTravelTimeMinutes());
                if (diff > 1e-4) {
                    throw new IllegalStateException(String.format(
                            "Routing discrepancy on %s: Dijkstra=%.4f min, A*=%.4f min",
                            scaleName, dRes.getTotalTravelTimeMinutes(), aRes.getTotalTravelTimeMinutes()));
                }
            }
        }

        // Benchmark Dijkstra
        results.add(harness.runBenchmark(
                "Dijkstra Routing",
                scaleName + " (" + pairs.size() + " pairs)",
                () -> {
                    for (SyntheticNetworkGenerator.QueryPair pair : pairs) {
                        dijkstra.findFastestRoute(graph, pair.sourceId(), pair.destinationId());
                    }
                },
                "Optimality: 100% matched"
        ));

        // Benchmark A*
        results.add(harness.runBenchmark(
                "A* Routing (Haversine)",
                scaleName + " (" + pairs.size() + " pairs)",
                () -> {
                    for (SyntheticNetworkGenerator.QueryPair pair : pairs) {
                        aStar.findFastestRoute(graph, pair.sourceId(), pair.destinationId());
                    }
                },
                "Optimality: 100% matched"
        ));

        return results;
    }

    // ==========================================
    // 2. Yen's K-Shortest Paths Benchmark
    // ==========================================

    public List<BenchmarkResult> benchmarkAlternativeRoutes(SyntheticNetworkGenerator.BenchmarkDataset dataset, String scaleName) {
        List<BenchmarkResult> results = new ArrayList<>();
        RoadGraph graph = dataset.graph();
        List<SyntheticNetworkGenerator.QueryPair> pairs = dataset.routingPairs();
        if (pairs.isEmpty()) return results;

        SyntheticNetworkGenerator.QueryPair testPair = pairs.getFirst();
        YenKShortestPathsAlgorithm yen = new YenKShortestPathsAlgorithm();

        for (int k : List.of(2, 3, 5)) {
            results.add(harness.runBenchmark(
                    "Yen's K-Shortest Paths (K=" + k + ")",
                    scaleName,
                    () -> yen.findKShortestPaths(graph, testPair.sourceId(), testPair.destinationId(), k),
                    "Target K=" + k + " paths"
            ));
        }

        return results;
    }

    // ==========================================
    // 3. Greedy Dispatch vs Optimal MCMF Matching
    // ==========================================

    public List<BenchmarkResult> benchmarkDispatchStrategies(SyntheticNetworkGenerator.BenchmarkDataset dataset, String scaleName) {
        List<BenchmarkResult> results = new ArrayList<>();
        RoadGraph graph = dataset.graph();
        List<Ambulance> ambulances = dataset.ambulances();
        List<EmergencyCase> emergencies = dataset.emergencies();
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        Clock fixedClock = Clock.fixed(now, java.time.ZoneOffset.UTC);

        // 1. Benchmark Optimal MCMF Optimizer
        AssignmentCostModel costModel = new PriorityAwareCostModel();
        FleetAssignmentOptimizer optimizer = new FleetAssignmentOptimizer(
                graph, new DijkstraRoutingAlgorithm(),
                new AmbulanceFleetService(graph),
                new EmergencyManagementService(graph, new AmbulanceFleetService(graph), fixedClock),
                null,
                com.resqroute.dispatch.DefaultCapabilityMatcher.INSTANCE,
                costModel,
                fixedClock
        );

        FleetAssignmentPlan optimalPlan = optimizer.computeOptimalPlan(emergencies, ambulances);
        double optimalCost = optimalPlan.getTotalEstimatedCost();
        int optimalAssigned = optimalPlan.getAssignments().size();

        results.add(harness.runBenchmark(
                "Optimal Fleet Optimizer (MCMF)",
                scaleName + " (" + ambulances.size() + " amb, " + emergencies.size() + " cases)",
                () -> optimizer.computeOptimalPlan(emergencies, ambulances),
                String.format("Assigned: %d, Cost: %.1f", optimalAssigned, optimalCost)
        ));

        // 2. Benchmark Greedy Sequential Dispatch
        AmbulanceFleetService greedyFleet = new AmbulanceFleetService(graph);
        for (Ambulance amb : ambulances) {
            greedyFleet.registerAmbulance(new Ambulance(amb.getId(), amb.getCurrentLocationNodeId(), amb.getCapabilities()));
        }
        EmergencyManagementService greedyEmergency = new EmergencyManagementService(graph, greedyFleet, fixedClock);
        EmergencyDispatchQueueService greedyQueue = new EmergencyDispatchQueueService(greedyEmergency, new AgingDispatchPolicy(10.0), fixedClock);
        for (EmergencyCase c : emergencies) {
            greedyEmergency.createCase(c);
            greedyEmergency.updateCaseStatus(c.getId(), EmergencyStatus.WAITING_FOR_DISPATCH);
            greedyQueue.enqueue(greedyEmergency.getCaseOrThrow(c.getId()));
        }

        AmbulanceDispatchService greedyDispatcher = new AmbulanceDispatchService(graph, greedyEmergency, greedyFleet, greedyQueue);

        results.add(harness.runBenchmark(
                "Greedy Sequential Dispatch",
                scaleName + " (" + ambulances.size() + " amb, " + emergencies.size() + " cases)",
                () -> {
                    // Reset and dispatch
                    for (int i = 0; i < emergencies.size(); i++) {
                        greedyDispatcher.dispatchNext();
                    }
                },
                String.format("Max capacity: %d", ambulances.size())
        ));

        return results;
    }

    // ==========================================
    // 4. Hospital Selection Benchmark
    // ==========================================

    public BenchmarkResult benchmarkHospitalSelection(SyntheticNetworkGenerator.BenchmarkDataset dataset, String scaleName) {
        RoadGraph graph = dataset.graph();
        List<Hospital> hospitals = dataset.hospitals();
        List<EmergencyCase> emergencies = dataset.emergencies();
        Clock clock = Clock.systemUTC();

        HospitalManagementService hospitalService = new HospitalManagementService(graph, clock);
        for (Hospital h : hospitals) {
            hospitalService.registerHospital(h);
        }

        HospitalSelectionService selectionService = new HospitalSelectionService(
                graph,
                new DijkstraRoutingAlgorithm(),
                hospitalService,
                null,
                com.resqroute.hospital.DefaultHospitalSuitabilityMatcher.INSTANCE,
                FastestHospitalRankingPolicy.INSTANCE,
                clock
        );

        EmergencyCase testCase = emergencies.getFirst();
        String originNode = dataset.nodeIds().getFirst();

        return harness.runBenchmark(
                "Hospital Selection & Ranking",
                scaleName + " (" + hospitals.size() + " hospitals)",
                () -> selectionService.evaluateHospitals(testCase, originNode),
                "Candidates evaluated: " + hospitals.size()
        );
    }

    // ==========================================
    // 5. Graph Operations Benchmark
    // ==========================================

    public List<BenchmarkResult> benchmarkGraphOperations(SyntheticNetworkGenerator.BenchmarkDataset dataset, String scaleName) {
        List<BenchmarkResult> results = new ArrayList<>();
        RoadGraph graph = dataset.graph();
        List<String> nodeIds = dataset.nodeIds();
        int probeCount = Math.min(50, nodeIds.size());

        // 1. Node Lookup Benchmark
        results.add(harness.runBenchmark(
                "Graph Node Lookup (O(1))",
                scaleName + " (" + probeCount + " lookups)",
                () -> {
                    for (int i = 0; i < probeCount; i++) {
                        graph.getNode(nodeIds.get(i));
                    }
                },
                "Map hash retrieval"
        ));

        // 2. Edge Traversal Benchmark
        results.add(harness.runBenchmark(
                "Outgoing Edges Iteration",
                scaleName + " (" + probeCount + " nodes)",
                () -> {
                    for (int i = 0; i < probeCount; i++) {
                        graph.getOutgoingEdges(nodeIds.get(i));
                    }
                },
                "Adjacency list scan"
        ));

        // 3. Dynamic Traffic Update Benchmark
        if (nodeIds.size() >= 2) {
            String u = nodeIds.get(0);
            String v = nodeIds.get(1);
            if (graph.containsEdge(u, v)) {
                results.add(harness.runBenchmark(
                        "Dynamic Traffic Update",
                        scaleName,
                        () -> graph.updateTraffic(u, v, TrafficLevel.HEAVY),
                        "Cost update without reconstruction"
                ));
            }
        }

        return results;
    }
}
