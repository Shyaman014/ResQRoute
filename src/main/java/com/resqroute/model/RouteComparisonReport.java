package com.resqroute.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable report summarizing comparative metrics across multiple alternative emergency routes.
 *
 * <p>Separates mathematical calculations from display presentation:
 * internal metrics retain unrounded precision, while presentation methods format cleanly for human operators.</p>
 */
public final class RouteComparisonReport {

    private final String sourceId;
    private final String destinationId;
    private final List<RouteComparisonItem> items;

    public RouteComparisonReport(String sourceId, String destinationId, List<RouteComparisonItem> items) {
        this.sourceId = Objects.requireNonNull(sourceId, "Source ID must not be null");
        this.destinationId = Objects.requireNonNull(destinationId, "Destination ID must not be null");
        this.items = items != null ? List.copyOf(items) : List.of();
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public List<RouteComparisonItem> getItems() {
        return items;
    }

    public int getRouteCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public RouteResult getFastestRoute() {
        if (items.isEmpty()) {
            return null;
        }
        return items.getFirst().route();
    }

    /**
     * Renders a human-readable ASCII comparison matrix formatting internal metrics without altering underlying values.
     */
    public String toFormattedTable() {
        if (items.isEmpty()) {
            return String.format("No routes available to compare between '%s' and '%s'.", sourceId, destinationId);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("========================================================================================%n"));
        sb.append(String.format(" EMERGENCY ROUTE ALTERNATIVES COMPARISON [%s -> %s]%n", sourceId, destinationId));
        sb.append(String.format("========================================================================================%n"));
        sb.append(String.format("%-5s | %-12s | %-10s | %-10s | %-10s | %s%n",
                "Rank", "Time (min)", "Dist (km)", "+Time", "+Dist", "Waypoint Sequence"));
        sb.append(String.format("------+--------------+------------+------------+------------+---------------------------%n"));

        for (RouteComparisonItem item : items) {
            String plusTime = item.rank() == 1 ? "FASTEST" : String.format("+%.2f min", item.timeDifferenceMinutes());
            String plusDist = item.rank() == 1 ? "BASELINE" : String.format("%+.2f km", item.distanceDifferenceKm());
            String pathStr = String.join(" -> ", item.nodeIds());

            sb.append(String.format("#%-4d | %10.2f   | %8.2f   | %-10s | %-10s | %s%n",
                    item.rank(),
                    item.totalTravelTimeMinutes(),
                    item.totalDistanceKm(),
                    plusTime,
                    plusDist,
                    pathStr));
        }
        sb.append(String.format("========================================================================================%n"));
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("RouteComparisonReport{from='%s', to='%s', candidateCount=%d}",
                sourceId, destinationId, items.size());
    }
}
