package com.resqroute.model;

import com.resqroute.exception.InvalidCostException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable encapsulation of an emergency routing result.
 * Contains the ordered sequence of nodes traversed, total physical distance, and total travel time.
 */
public final class RouteResult {

    private final List<Node> path;
    private final List<Edge> edges;
    private final double totalDistanceKm;
    private final double totalTravelTimeMinutes;
    private final boolean reachable;
    private final String sourceId;
    private final String destinationId;

    private RouteResult(List<Node> path,
                        List<Edge> edges,
                        double totalDistanceKm,
                        double totalTravelTimeMinutes,
                        boolean reachable,
                        String sourceId,
                        String destinationId) {
        this.path = path != null ? List.copyOf(path) : List.of();
        this.edges = edges != null ? List.copyOf(edges) : List.of();
        this.totalDistanceKm = totalDistanceKm;
        this.totalTravelTimeMinutes = totalTravelTimeMinutes;
        this.reachable = reachable;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
    }

    /**
     * Creates a reachable route result with full node path and edges.
     */
    public static RouteResult reachable(List<Node> path, List<Edge> edges, double totalDistanceKm, double totalTravelTimeMinutes) {
        Objects.requireNonNull(path, "Path must not be null");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Reachable route path cannot be empty");
        }
        if (Double.isNaN(totalDistanceKm) || Double.isInfinite(totalDistanceKm) || totalDistanceKm < 0.0) {
            throw new InvalidCostException("Total distance must be non-negative: " + totalDistanceKm);
        }
        if (Double.isNaN(totalTravelTimeMinutes) || Double.isInfinite(totalTravelTimeMinutes) || totalTravelTimeMinutes < 0.0) {
            throw new InvalidCostException("Total travel time must be non-negative: " + totalTravelTimeMinutes);
        }

        String src = path.getFirst().getId();
        String dst = path.getLast().getId();
        return new RouteResult(path, edges, totalDistanceKm, totalTravelTimeMinutes, true, src, dst);
    }

    /**
     * Creates a reachable route result without edge details.
     */
    public static RouteResult reachable(List<Node> path, double totalDistanceKm, double totalTravelTimeMinutes) {
        return reachable(path, List.of(), totalDistanceKm, totalTravelTimeMinutes);
    }

    /**
     * Creates a route result for when source is identical to destination.
     */
    public static RouteResult singleNode(Node node) {
        Objects.requireNonNull(node, "Node must not be null");
        return new RouteResult(List.of(node), List.of(), 0.0, 0.0, true, node.getId(), node.getId());
    }

    /**
     * Creates an unreachable route result indicating no path exists.
     */
    public static RouteResult unreachable(String sourceId, String destinationId) {
        return new RouteResult(List.of(), List.of(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, false, sourceId, destinationId);
    }

    public List<Node> getPath() {
        return path;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public double getTotalTravelTimeMinutes() {
        return totalTravelTimeMinutes;
    }

    public boolean isReachable() {
        return reachable;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getNodeCount() {
        return path.size();
    }

    /**
     * Returns an emergency route summary string.
     */
    public String toFormattedSummary() {
        if (!reachable) {
            return String.format("ROUTE UNREACHABLE: No path found from '%s' to '%s'.", sourceId, destinationId);
        }
        String pathString = path.stream()
                .map(n -> String.format("%s (%s)", n.getId(), n.getName()))
                .collect(Collectors.joining(" -> "));
        double avgSpeed = totalTravelTimeMinutes > 0 ? (totalDistanceKm / totalTravelTimeMinutes) * 60.0 : 0.0;
        return String.format("""
                Emergency Route Summary:
                Path: %s
                Total Travel Time: %.2f minutes
                Total Distance:    %.2f km
                Average Speed:     %.1f km/h
                Waypoints Count:   %d
                """, pathString, totalTravelTimeMinutes, totalDistanceKm, avgSpeed, path.size());
    }

    @Override
    public String toString() {
        if (!reachable) {
            return String.format("RouteResult{unreachable from '%s' to '%s'}", sourceId, destinationId);
        }
        return String.format("RouteResult{path=%s, dist=%.2f km, time=%.2f min}",
                path.stream().map(Node::getId).toList(), totalDistanceKm, totalTravelTimeMinutes);
    }
}
