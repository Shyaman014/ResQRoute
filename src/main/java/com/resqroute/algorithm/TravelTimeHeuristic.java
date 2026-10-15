package com.resqroute.algorithm;

import com.resqroute.model.Node;

/**
 * Functional interface for estimating remaining travel time between two nodes in A* search.
 * To ensure admissibility, the heuristic must never overestimate the true minimum travel time.
 */
@FunctionalInterface
public interface TravelTimeHeuristic {

    /**
     * Estimates remaining travel time in minutes from the current node to the destination goal.
     *
     * @param current Current exploration node.
     * @param goal    Target destination node.
     * @return Estimated travel time in minutes (must be >= 0 and admissible).
     */
    double estimateTravelTimeMinutes(Node current, Node goal);

    /**
     * Trivial zero-heuristic fallback. Always admissible; reduces A* to Dijkstra.
     */
    TravelTimeHeuristic ZERO = (current, goal) -> 0.0;

    /**
     * Creates an admissible Haversine-based travel time heuristic given a maximum vehicle speed bound.
     *
     * <p>Formula: {@code h(u) = (greatCircleDistance(u, goal) / maxSpeedKmh) * 60.0 minutes}.
     * Since great-circle distance is the shortest possible physical distance and maxSpeedKmh is the
     * upper bound on travel speed, this heuristic is strictly admissible and consistent.</p>
     *
     * @param maxSpeedKmh Upper bound on vehicle speed in km/h; must be positive.
     * @return Admissible travel time heuristic.
     */
    static TravelTimeHeuristic haversine(double maxSpeedKmh) {
        if (Double.isNaN(maxSpeedKmh) || Double.isInfinite(maxSpeedKmh) || maxSpeedKmh <= 0.0) {
            return ZERO;
        }
        return (current, goal) -> {
            if (current == null || goal == null || current.getCoordinates() == null || goal.getCoordinates() == null) {
                return 0.0;
            }
            double straightLineDistKm = current.getCoordinates().haversineDistanceKm(goal.getCoordinates());
            return (straightLineDistKm / maxSpeedKmh) * 60.0;
        };
    }
}
