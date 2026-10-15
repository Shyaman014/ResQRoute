package com.resqroute.algorithm;

import com.resqroute.exception.InvalidCostException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;

import java.util.*;

/**
 * A* search routing algorithm customized for emergency ambulance navigation.
 *
 * <h3>Heuristic Design & Admissibility</h3>
 * <p>A* guides search toward the destination using an evaluation function {@code f(n) = g(n) + h(n)}:
 * <ul>
 *   <li>{@code g(n)}: Exact accumulated travel time from the origin node to node {@code n} in minutes.</li>
 *   <li>{@code h(n)}: Admissible heuristic estimating remaining travel time from node {@code n} to the goal.</li>
 * </ul>
 * For travel time optimization, the heuristic is calculated using the straight-line Haversine distance
 * divided by an upper bound on vehicle speed:
 * <pre>
 *   h(n) = (haversineDistanceKm(n, goal) / maxSpeedKmh) * 60.0 minutes
 * </pre>
 * Because great-circle distance is the shortest possible spatial distance on Earth and {@code maxSpeedKmh}
 * is the theoretical upper speed bound across the network, {@code h(n)} is strictly <b>admissible</b>
 * (it never overestimates remaining travel time) and <b>consistent (monotonic)</b>. If coordinates or
 * speed bounds are unavailable, {@code h(n)} seamlessly falls back to 0.0 (Dijkstra mode).</p>
 *
 * <h3>Algorithmic Complexity</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> {@code O((|V| + |E|) * log(|V|))} worst-case bound. In practice, the
 *       directional heuristic pruning explores far fewer states than Dijkstra, achieving near-linear
 *       performance on typical planar road networks.</li>
 *   <li><b>Space Complexity:</b> {@code O(|V|)} auxiliary space to maintain open set min-priority queue,
 *       closed set, cost maps, and predecessor backpointers.</li>
 * </ul>
 */
public class AStarRoutingAlgorithm implements RoutingAlgorithm {

    private static final double EPSILON = 1e-9;
    private static final double DEFAULT_SPEED_BOUND_KMH = 120.0;

    private final TravelTimeHeuristic heuristicSupplier;
    private final Double fixedMaxSpeedKmh;

    /**
     * Internal entry representing a search frontier state in the A* min-priority queue.
     */
    private record AStarEntry(String nodeId, double fScore, double gScore, double distanceKm) implements Comparable<AStarEntry> {
        @Override
        public int compareTo(AStarEntry other) {
            int cmp = Double.compare(this.fScore, other.fScore);
            if (cmp != 0) {
                return cmp;
            }
            int gCmp = Double.compare(other.gScore, this.gScore);
            if (gCmp != 0) {
                return gCmp;
            }
            return Double.compare(this.distanceKm, other.distanceKm);
        }
    }

    /**
     * Constructs an A* routing engine that dynamically derives an admissible speed bound from graph topology.
     */
    public AStarRoutingAlgorithm() {
        this.fixedMaxSpeedKmh = null;
        this.heuristicSupplier = null;
    }

    /**
     * Constructs an A* routing engine with an explicit maximum speed bound for the Haversine heuristic.
     *
     * @param maxSpeedKmh Maximum conceivable vehicle speed in km/h; must be positive.
     */
    public AStarRoutingAlgorithm(double maxSpeedKmh) {
        if (Double.isNaN(maxSpeedKmh) || Double.isInfinite(maxSpeedKmh) || maxSpeedKmh <= 0.0) {
            throw new IllegalArgumentException("Maximum speed bound must be positive and finite: " + maxSpeedKmh);
        }
        this.fixedMaxSpeedKmh = maxSpeedKmh;
        this.heuristicSupplier = TravelTimeHeuristic.haversine(maxSpeedKmh);
    }

    /**
     * Constructs an A* routing engine with a custom travel time heuristic strategy.
     *
     * @param heuristic Custom heuristic; must not be null.
     */
    public AStarRoutingAlgorithm(TravelTimeHeuristic heuristic) {
        this.fixedMaxSpeedKmh = null;
        this.heuristicSupplier = Objects.requireNonNull(heuristic, "Heuristic must not be null");
    }

    @Override
    public RouteResult findFastestRoute(RoadGraph graph, String sourceId, String destinationId) {
        Objects.requireNonNull(graph, "RoadGraph must not be null");
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        String srcTrimmed = sourceId.trim();
        String dstTrimmed = destinationId.trim();

        Node sourceNode = graph.getRequiredNode(srcTrimmed);
        Node destinationNode = graph.getRequiredNode(dstTrimmed);

        // Edge case: Source is identical to Destination
        if (srcTrimmed.equals(dstTrimmed)) {
            return RouteResult.singleNode(sourceNode);
        }

        TravelTimeHeuristic activeHeuristic = resolveHeuristic(graph);

        Map<String, Double> gScore = new HashMap<>();
        Map<String, Double> distanceToNode = new HashMap<>();
        Map<String, Node> predecessorNode = new HashMap<>();
        Map<String, Edge> predecessorEdge = new HashMap<>();
        Set<String> closedSet = new HashSet<>();

        PriorityQueue<AStarEntry> openSet = new PriorityQueue<>();

        gScore.put(srcTrimmed, 0.0);
        distanceToNode.put(srcTrimmed, 0.0);
        double initialH = computeHeuristic(activeHeuristic, sourceNode, destinationNode);
        openSet.offer(new AStarEntry(srcTrimmed, initialH, 0.0, 0.0));

        while (!openSet.isEmpty()) {
            AStarEntry current = openSet.poll();
            String u = current.nodeId();

            if (closedSet.contains(u)) {
                continue;
            }
            Double bestG = gScore.get(u);
            if (bestG != null && current.gScore() > bestG + EPSILON) {
                continue;
            }

            closedSet.add(u);

            if (u.equals(dstTrimmed)) {
                break;
            }

            double currentG = current.gScore();
            double currentDist = distanceToNode.getOrDefault(u, 0.0);

            for (Edge edge : graph.getOutgoingEdges(u)) {
                validateEdgeCost(edge);

                Node vNode = edge.getDestination();
                String v = vNode.getId();

                if (closedSet.contains(v)) {
                    continue;
                }

                double edgeTime = edge.getTravelTimeMinutes();
                double edgeDist = edge.getDistanceKm();
                double tentativeG = currentG + edgeTime;
                double tentativeDist = currentDist + edgeDist;

                double knownG = gScore.getOrDefault(v, Double.POSITIVE_INFINITY);
                double knownDist = distanceToNode.getOrDefault(v, Double.POSITIVE_INFINITY);

                boolean isFaster = tentativeG < knownG - EPSILON;
                boolean isTieBreak = Math.abs(tentativeG - knownG) <= EPSILON && tentativeDist < knownDist - EPSILON;

                if (isFaster || isTieBreak) {
                    gScore.put(v, tentativeG);
                    distanceToNode.put(v, tentativeDist);
                    predecessorNode.put(v, graph.getRequiredNode(u));
                    predecessorEdge.put(v, edge);

                    double h = computeHeuristic(activeHeuristic, vNode, destinationNode);
                    double f = tentativeG + h;
                    openSet.offer(new AStarEntry(v, f, tentativeG, tentativeDist));
                }
            }
        }

        // Delegate to shared, cycle-safe RouteReconstructor
        return RouteReconstructor.reconstruct(sourceNode, destinationNode, predecessorNode, predecessorEdge);
    }

    private TravelTimeHeuristic resolveHeuristic(RoadGraph graph) {
        if (heuristicSupplier != null) {
            return heuristicSupplier;
        }

        double maxSpeedFound = DEFAULT_SPEED_BOUND_KMH;
        for (Edge edge : graph.getAllEdges()) {
            double speed = edge.getAverageSpeedKmh();
            if (!Double.isNaN(speed) && !Double.isInfinite(speed) && speed > maxSpeedFound) {
                maxSpeedFound = speed;
            }
        }
        return TravelTimeHeuristic.haversine(maxSpeedFound);
    }

    private double computeHeuristic(TravelTimeHeuristic heuristic, Node from, Node goal) {
        if (heuristic == null || from == null || goal == null) {
            return 0.0;
        }
        try {
            double val = heuristic.estimateTravelTimeMinutes(from, goal);
            if (Double.isNaN(val) || Double.isInfinite(val) || val < 0.0) {
                return 0.0;
            }
            return val;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void validateNodeId(String id, String role) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(role + " node ID must not be null or blank");
        }
    }

    private void validateEdgeCost(Edge edge) {
        if (Double.isNaN(edge.getTravelTimeMinutes()) || Double.isInfinite(edge.getTravelTimeMinutes()) || edge.getTravelTimeMinutes() < 0.0) {
            throw new InvalidCostException("Encountered invalid travel time: " + edge.getTravelTimeMinutes());
        }
        if (Double.isNaN(edge.getDistanceKm()) || Double.isInfinite(edge.getDistanceKm()) || edge.getDistanceKm() < 0.0) {
            throw new InvalidCostException("Encountered invalid distance: " + edge.getDistanceKm());
        }
    }
}
