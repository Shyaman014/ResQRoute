package com.resqroute.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable comparison record for a single candidate emergency route evaluated relative to the baseline fastest route.
 *
 * All numeric differences maintain full floating-point precision without premature rounding.
 * Formatting is strictly separated from internal calculation metrics.
 *
 * @param rank                    1-based ranking by travel time (1 = fastest).
 * @param route                   The underlying RouteResult.
 * @param nodeIds                 Ordered sequence of waypoint identifiers.
 * @param totalTravelTimeMinutes  Total transit duration in minutes.
 * @param totalDistanceKm         Total roadway length in kilometres.
 * @param timeDifferenceMinutes   Difference in travel time relative to the fastest route (\Delta time >= 0.0).
 * @param distanceDifferenceKm    Difference in distance relative to the fastest route (\Delta distance).
 * @param percentageTimeIncrease  Percentage increase in travel time relative to the fastest route.
 */
public record RouteComparisonItem(
        int rank,
        RouteResult route,
        List<String> nodeIds,
        double totalTravelTimeMinutes,
        double totalDistanceKm,
        double timeDifferenceMinutes,
        double distanceDifferenceKm,
        double percentageTimeIncrease
) {
    public RouteComparisonItem {
        Objects.requireNonNull(route, "Route must not be null");
        Objects.requireNonNull(nodeIds, "Node IDs must not be null");
    }
}
