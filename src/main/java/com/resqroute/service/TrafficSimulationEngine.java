package com.resqroute.service;

import com.resqroute.exception.InvalidEdgeException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.EdgeKey;
import com.resqroute.model.TrafficCondition;
import com.resqroute.model.TrafficLevel;

import java.util.*;

/**
 * Deterministic, offline traffic simulation engine.
 *
 * <p>Enables realistic testing and operational drills for dynamic emergency routing:
 * <ul>
 *   <li>Simulates dynamic stochastic traffic fluctuations without requiring external APIs.</li>
 *   <li>Supports targeted congestion injections and incident simulations (accidents, construction).</li>
 *   <li>Supports isolated road restorations and network-wide reset to free-flow conditions.</li>
 *   <li>Guarantees 100% deterministic reproducibility when initialized with a fixed random seed.</li>
 * </ul>
 */
public class TrafficSimulationEngine {

    private final RoadGraph graph;
    private final Random random;
    private final Map<EdgeKey, String> activeIncidents;
    private int currentTick;

    /**
     * Constructs a simulation engine with a randomized seed.
     */
    public TrafficSimulationEngine(RoadGraph graph) {
        this(graph, new Random().nextLong());
    }

    /**
     * Constructs a simulation engine with a fixed random seed for strictly deterministic testing.
     *
     * @param graph Road network graph; must not be null.
     * @param seed  Random generator seed.
     */
    public TrafficSimulationEngine(RoadGraph graph, long seed) {
        this.graph = Objects.requireNonNull(graph, "RoadGraph must not be null");
        this.random = new Random(seed);
        this.activeIncidents = new HashMap<>();
        this.currentTick = 0;
    }

    /**
     * Sets a specific traffic level on a directed road segment.
     */
    public void setTraffic(String sourceId, String destinationId, TrafficLevel level) {
        Objects.requireNonNull(level, "TrafficLevel must not be null");
        TrafficCondition condition = TrafficCondition.of(level, "SIMULATION_MANUAL_TICK_" + currentTick);
        graph.updateTraffic(sourceId, destinationId, condition);
        activeIncidents.remove(EdgeKey.of(sourceId, destinationId));
    }

    /**
     * Injects an incident (e.g. road accident, major obstruction) causing severe delays on a road.
     *
     * @param sourceId            Source node identifier.
     * @param destinationId       Destination node identifier.
     * @param incidentDescription Description of the incident.
     * @param delayMultiplier     Delay multiplier (must be >= 1.0, typically 3.0x - 8.0x).
     */
    public void simulateIncident(String sourceId, String destinationId, String incidentDescription, double delayMultiplier) {
        String desc = (incidentDescription == null || incidentDescription.isBlank()) ? "Road Blockage" : incidentDescription.trim();
        TrafficCondition condition = TrafficCondition.incident(delayMultiplier, desc);
        graph.updateTraffic(sourceId, destinationId, condition);
        activeIncidents.put(EdgeKey.of(sourceId, destinationId), desc);
    }

    /**
     * Restores a single road segment to normal free-flow conditions and clears any active incident.
     */
    public void restoreRoad(String sourceId, String destinationId) {
        graph.updateTraffic(sourceId, destinationId, TrafficCondition.freeFlow("RESTORED_FREE_FLOW"));
        activeIncidents.remove(EdgeKey.of(sourceId, destinationId));
    }

    /**
     * Restores all roads across the entire network to pristine free-flow conditions.
     */
    public void restoreAllRoads() {
        for (Edge edge : graph.getAllEdges()) {
            edge.getSource().ifPresent(src -> {
                graph.updateTraffic(src.getId(), edge.getDestination().getId(), TrafficCondition.freeFlow("RESTORED_ALL"));
            });
        }
        activeIncidents.clear();
    }

    /**
     * Advances simulation by one discrete time tick.
     * Applies realistic stochastic traffic variations across non-incident roads.
     */
    public void advanceSimulationStep() {
        currentTick++;
        TrafficLevel[] levels = TrafficLevel.values();

        for (Edge edge : graph.getAllEdges()) {
            if (edge.getSource().isEmpty()) {
                continue;
            }
            String src = edge.getSource().get().getId();
            String dst = edge.getDestination().getId();
            EdgeKey key = EdgeKey.of(src, dst);

            // Skip roads with active manual incidents
            if (activeIncidents.containsKey(key)) {
                continue;
            }

            TrafficLevel currentLevel = edge.getTrafficCondition().level();
            int ordinal = currentLevel.ordinal();

            // Stochastic fluctuation: 60% stay same, 20% congestion increases, 20% congestion decreases
            int roll = random.nextInt(100);
            TrafficLevel newLevel;
            if (roll < 60) {
                newLevel = currentLevel;
            } else if (roll < 80) {
                newLevel = levels[Math.min(levels.length - 1, ordinal + 1)];
            } else {
                newLevel = levels[Math.max(0, ordinal - 1)];
            }

            if (newLevel != currentLevel) {
                TrafficCondition updated = TrafficCondition.of(newLevel, "SIMULATION_TICK_" + currentTick);
                graph.updateTraffic(src, dst, updated);
            }
        }
    }

    /**
     * Checks if an incident is currently active on the specified directed road.
     */
    public boolean hasActiveIncident(String sourceId, String destinationId) {
        return activeIncidents.containsKey(EdgeKey.of(sourceId, destinationId));
    }

    /**
     * Returns an unmodifiable view of all currently active incidents.
     */
    public Map<EdgeKey, String> getActiveIncidents() {
        return Collections.unmodifiableMap(activeIncidents);
    }

    /**
     * Retrieves all roads currently experiencing congestion at or above the specified level.
     */
    public List<Edge> getCongestedRoads(TrafficLevel minimumLevel) {
        Objects.requireNonNull(minimumLevel, "Minimum traffic level must not be null");
        return graph.getAllEdges().stream()
                .filter(e -> e.getTrafficCondition().level().ordinal() >= minimumLevel.ordinal())
                .toList();
    }

    public int getCurrentTick() {
        return currentTick;
    }
}
