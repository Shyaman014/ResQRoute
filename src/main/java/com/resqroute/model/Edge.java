package com.resqroute.model;

import com.resqroute.exception.InvalidCostException;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a directed roadway segment connecting an origin to a destination node,
 * maintaining physical distance (km) and expected travel time (minutes) independently.
 *
 * Travel time accounts for speed limits, congestion factors, and road tier.
 * ResQRoute optimizes primarily on travel time.
 */
public final class Edge {

    private final Node source;
    private final Node destination;
    private final double distanceKm;
    private final double travelTimeMinutes;
    private final String roadName;

    /**
     * Constructs a directed road edge with explicit source and destination.
     *
     * @param source            Source origin node; may be null if unspecified.
     * @param destination       Destination node; must not be null.
     * @param distanceKm        Physical road length in kilometres; must be non-negative and finite.
     * @param travelTimeMinutes Estimated travel time in minutes; must be non-negative and finite.
     * @param roadName          Optional descriptive name or identifier for the road segment.
     */
    public Edge(Node source, Node destination, double distanceKm, double travelTimeMinutes, String roadName) {
        this.source = source;
        this.destination = Objects.requireNonNull(destination, "Destination node must not be null");

        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) || distanceKm < 0.0) {
            throw new InvalidCostException("Distance in kilometres must be a non-negative finite number: " + distanceKm);
        }
        if (Double.isNaN(travelTimeMinutes) || Double.isInfinite(travelTimeMinutes) || travelTimeMinutes < 0.0) {
            throw new InvalidCostException("Travel time in minutes must be a non-negative finite number: " + travelTimeMinutes);
        }

        this.distanceKm = distanceKm;
        this.travelTimeMinutes = travelTimeMinutes;
        this.roadName = (roadName == null || roadName.trim().isEmpty()) ? "Unnamed Road" : roadName.trim();
    }

    /**
     * Constructs a directed road edge with explicit source and destination without road name.
     */
    public Edge(Node source, Node destination, double distanceKm, double travelTimeMinutes) {
        this(source, destination, distanceKm, travelTimeMinutes, null);
    }

    /**
     * Constructs a directed road edge where source node is implicit from the graph context.
     *
     * @param destination       Destination node; must not be null.
     * @param distanceKm        Physical road length in kilometres; must be non-negative and finite.
     * @param travelTimeMinutes Estimated travel time in minutes; must be non-negative and finite.
     * @param roadName          Optional descriptive name for the road segment.
     */
    public Edge(Node destination, double distanceKm, double travelTimeMinutes, String roadName) {
        this(null, destination, distanceKm, travelTimeMinutes, roadName);
    }

    /**
     * Constructs a directed road edge without an explicit road name or source node.
     */
    public Edge(Node destination, double distanceKm, double travelTimeMinutes) {
        this(null, destination, distanceKm, travelTimeMinutes, null);
    }

    /**
     * Returns an Optional containing the source node if known.
     */
    public Optional<Node> getSource() {
        return Optional.ofNullable(source);
    }

    public Node getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public String getRoadName() {
        return roadName;
    }

    /**
     * Computes the average vehicle speed along this road segment in km/h.
     * Returns 0.0 if travel time is 0.
     */
    public double getAverageSpeedKmh() {
        if (travelTimeMinutes <= 0.0) {
            return 0.0;
        }
        return (distanceKm / travelTimeMinutes) * 60.0;
    }

    /**
     * Creates a copy of this edge associated with the specified source node.
     */
    public Edge withSource(Node newSource) {
        return new Edge(newSource, this.destination, this.distanceKm, this.travelTimeMinutes, this.roadName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge edge)) return false;
        return Double.compare(edge.distanceKm, distanceKm) == 0
                && Double.compare(edge.travelTimeMinutes, travelTimeMinutes) == 0
                && Objects.equals(source, edge.source)
                && destination.equals(edge.destination)
                && roadName.equals(edge.roadName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination, distanceKm, travelTimeMinutes, roadName);
    }

    @Override
    public String toString() {
        String srcStr = source != null ? source.getId() : "?";
        return String.format("Edge{'%s' -> '%s' (%s), dist=%.2f km, time=%.2f min, road='%s'}",
                srcStr, destination.getId(), destination.getName(), distanceKm, travelTimeMinutes, roadName);
    }
}
