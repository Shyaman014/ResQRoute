package com.resqroute.service;

import com.resqroute.model.RouteComparisonItem;
import com.resqroute.model.RouteComparisonReport;
import com.resqroute.model.RouteResult;

import java.util.*;

/**
 * Service providing analytical route comparison across multiple alternative paths.
 *
 * <p>Preserves unrounded double precision in all differential calculations.
 * Identifies the baseline fastest route, computes differential time and distance offsets,
 * and compiles structured {@link RouteComparisonReport} representations.</p>
 */
public class RouteComparisonService {

    /**
     * Compares an ordered or unordered collection of alternative routes.
     *
     * @param routes List of candidate routes; must not be null or empty.
     * @return Structured RouteComparisonReport.
     * @throws IllegalArgumentException if routes is null or empty.
     */
    public RouteComparisonReport compareRoutes(List<RouteResult> routes) {
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("Routes list to compare must not be null or empty");
        }

        // Filter for reachable routes only
        List<RouteResult> reachableRoutes = routes.stream()
                .filter(RouteResult::isReachable)
                .sorted(Comparator
                        .comparingDouble(RouteResult::getTotalTravelTimeMinutes)
                        .thenComparingDouble(RouteResult::getTotalDistanceKm))
                .toList();

        if (reachableRoutes.isEmpty()) {
            RouteResult sample = routes.getFirst();
            return new RouteComparisonReport(sample.getSourceId(), sample.getDestinationId(), List.of());
        }

        RouteResult fastest = reachableRoutes.getFirst();
        double baselineTime = fastest.getTotalTravelTimeMinutes();
        double baselineDistance = fastest.getTotalDistanceKm();
        String sourceId = fastest.getSourceId();
        String destinationId = fastest.getDestinationId();

        List<RouteComparisonItem> items = new ArrayList<>();
        for (int i = 0; i < reachableRoutes.size(); i++) {
            RouteResult route = reachableRoutes.get(i);
            int rank = i + 1;
            double time = route.getTotalTravelTimeMinutes();
            double dist = route.getTotalDistanceKm();

            double timeDiff = time - baselineTime;
            double distDiff = dist - baselineDistance;
            double percentTimeIncrease = baselineTime > 0.0 ? (timeDiff / baselineTime) * 100.0 : 0.0;

            items.add(new RouteComparisonItem(
                    rank,
                    route,
                    route.getNodeIds(),
                    time,
                    dist,
                    timeDiff,
                    distDiff,
                    percentTimeIncrease
            ));
        }

        return new RouteComparisonReport(sourceId, destinationId, items);
    }
}
