package com.resqroute.benchmark;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Deterministic generator producing synthetic urban road networks with configurable scale,
 * planar mesh topology, realistic speed limits, traffic states, and emergency response assets.
 */
public final class SyntheticNetworkGenerator {

    public record BenchmarkDataset(
            RoadGraph graph,
            List<String> nodeIds,
            List<Ambulance> ambulances,
            List<Hospital> hospitals,
            List<EmergencyCase> emergencies,
            List<QueryPair> routingPairs
    ) {}

    public record QueryPair(String sourceId, String destinationId) {}

    private SyntheticNetworkGenerator() {
    }

    /**
     * Generates a deterministic benchmark network dataset.
     *
     * @param nodeCount       Total nodes to generate (e.g., 50, 250, 1000).
     * @param ambulanceCount  Fleet size to distribute.
     * @param hospitalCount   Hospitals to place.
     * @param emergencyCount  Pending emergency cases to create.
     * @param seed            Random seed for 100% reproducibility.
     * @return Fully populated BenchmarkDataset.
     */
    public static BenchmarkDataset generate(int nodeCount,
                                             int ambulanceCount,
                                             int hospitalCount,
                                             int emergencyCount,
                                             long seed) {
        Random random = new Random(seed);
        RoadGraph graph = new RoadGraph();

        // 1. Generate nodes arranged in a perturbed 2D spatial grid
        int gridDimension = (int) Math.ceil(Math.sqrt(nodeCount));
        double baseLat = 12.9000;
        double baseLon = 77.5000;
        double step = 0.015; // roughly 1.6 km spacing

        List<String> nodeIds = new ArrayList<>(nodeCount);
        Node[][] grid = new Node[gridDimension][gridDimension];

        int created = 0;
        for (int r = 0; r < gridDimension && created < nodeCount; r++) {
            for (int c = 0; c < gridDimension && created < nodeCount; c++) {
                String id = String.format("N_%04d", created);
                double lat = baseLat + (r * step) + (random.nextDouble() * 0.004 - 0.002);
                double lon = baseLon + (c * step) + (random.nextDouble() * 0.004 - 0.002);
                Node node = new Node(id, lat, lon, "Intersection " + created);
                graph.addNode(node);
                grid[r][c] = node;
                nodeIds.add(id);
                created++;
            }
        }

        // 2. Add realistic bidirectional road edges: Grid mesh + diagonal arteries
        for (int r = 0; r < gridDimension; r++) {
            for (int c = 0; c < gridDimension; c++) {
                Node u = grid[r][c];
                if (u == null) continue;

                // Horizontal connection (r, c) -> (r, c+1)
                if (c + 1 < gridDimension && grid[r][c + 1] != null) {
                    addRoadBidirectional(graph, u, grid[r][c + 1], 50.0, random);
                }

                // Vertical connection (r, c) -> (r+1, c)
                if (r + 1 < gridDimension && grid[r + 1][c] != null) {
                    addRoadBidirectional(graph, u, grid[r + 1][c], 50.0, random);
                }

                // Diagonal arterial highway (r, c) -> (r+1, c+1) with 40% probability
                if (r + 1 < gridDimension && c + 1 < gridDimension && grid[r + 1][c + 1] != null && random.nextDouble() < 0.40) {
                    addRoadBidirectional(graph, u, grid[r + 1][c + 1], 80.0, random);
                }
            }
        }

        // 3. Generate query pairs (fixed endpoints spanning small, medium, and large graph distances)
        List<QueryPair> queryPairs = new ArrayList<>();
        int pairCount = Math.min(20, nodeCount / 2);
        for (int i = 0; i < pairCount; i++) {
            int srcIdx = (i * (nodeCount / pairCount)) % nodeCount;
            int dstIdx = (srcIdx + nodeCount / 2) % nodeCount;
            if (srcIdx != dstIdx) {
                queryPairs.add(new QueryPair(nodeIds.get(srcIdx), nodeIds.get(dstIdx)));
            }
        }

        // 4. Generate Ambulances distributed across network nodes
        List<Ambulance> ambulances = new ArrayList<>(ambulanceCount);
        for (int i = 0; i < ambulanceCount; i++) {
            String ambId = String.format("AMB_%03d", i + 1);
            String locNode = nodeIds.get(random.nextInt(nodeCount));
            Set<AmbulanceCapability> caps = (i % 2 == 0)
                    ? EnumSet.of(AmbulanceCapability.BASIC_LIFE_SUPPORT, AmbulanceCapability.ADVANCED_LIFE_SUPPORT, AmbulanceCapability.CARDIAC_CARE)
                    : EnumSet.of(AmbulanceCapability.BASIC_LIFE_SUPPORT, AmbulanceCapability.HEAVY_RESCUE);
            ambulances.add(new Ambulance(ambId, locNode, caps));
        }

        // 5. Generate Hospitals distributed across key nodes
        List<Hospital> hospitals = new ArrayList<>(hospitalCount);
        for (int i = 0; i < hospitalCount; i++) {
            String hospId = String.format("HOSP_%03d", i + 1);
            String locNode = nodeIds.get((i * (nodeCount / hospitalCount)) % nodeCount);
            int capacity = 10 + random.nextInt(40);
            Hospital hospital = Hospital.builder()
                    .id(hospId)
                    .name("Hospital " + (i + 1))
                    .locationNodeId(locNode)
                    .totalCapacity(capacity)
                    .availableCapacity(capacity)
                    .supportedEmergencyTypes(EnumSet.allOf(EmergencyType.class))
                    .capabilities(EnumSet.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER, HospitalCapability.ICU, HospitalCapability.CARDIAC_CATH_LAB))
                    .build();
            hospitals.add(hospital);
        }

        // 6. Generate Pending Emergency Cases
        List<EmergencyCase> emergencies = new ArrayList<>(emergencyCount);
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        EmergencyType[] types = EmergencyType.values();
        EmergencySeverity[] severities = EmergencySeverity.values();

        for (int i = 0; i < emergencyCount; i++) {
            String caseId = String.format("CASE_%03d", i + 1);
            String pickupNode = nodeIds.get(random.nextInt(nodeCount));
            EmergencyType type = types[random.nextInt(types.length)];
            EmergencySeverity sev = severities[random.nextInt(severities.length)];
            emergencies.add(new EmergencyCase(caseId, type, sev, pickupNode, now.plusSeconds(i * 30L), "Synthetic Case " + (i + 1)));
        }

        return new BenchmarkDataset(graph, nodeIds, ambulances, hospitals, emergencies, queryPairs);
    }

    private static void addRoadBidirectional(RoadGraph graph, Node u, Node v, double speedKmh, Random random) {
        double straightLineKm = u.getCoordinates().haversineDistanceKm(v.getCoordinates());
        double distanceKm = Math.max(0.2, straightLineKm * (1.10 + random.nextDouble() * 0.15));
        double travelTimeMinutes = (distanceKm / speedKmh) * 60.0;

        graph.addEdge(u.getId(), v.getId(), distanceKm, travelTimeMinutes, u.getId() + "-" + v.getId());
        graph.addEdge(v.getId(), u.getId(), distanceKm, travelTimeMinutes, v.getId() + "-" + u.getId());
    }
}
