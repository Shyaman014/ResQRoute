package com.resqroute.model;

import com.resqroute.exception.InvalidCostException;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a directed roadway segment connecting an origin to a destination node,
 * maintaining physical distance (km), static base travel time, dynamic real-time traffic conditions,
 * and operational availability (open/closed status).
 *
 * <p>Separates static road properties (roadway length and baseline free-flow travel time) from
 * real-time dynamic traffic conditions (congestion tier, delay multiplier) and operational status
 * (temporary closures due to incidents or construction).</p>
 */
public final class Edge {

    private final Node source;
    private final Node destination;
    private final double distanceKm;
    private final double baseTravelTimeMinutes;
    private final TrafficCondition trafficCondition;
    private final double currentTravelTimeMinutes;
    private final String roadName;
    private final boolean available;

    /**
     * Primary constructor accepting complete static and dynamic road properties along with availability.
     *
     * @param source                Source origin node; may be null if implicit.
     * @param destination           Destination node; must not be null.
     * @param distanceKm            Physical road length in kilometres; must be non-negative and finite.
     * @param baseTravelTimeMinutes Static free-flow travel time in minutes; must be non-negative and finite.
     * @param trafficCondition      Dynamic real-time traffic condition; must not be null.
     * @param roadName              Descriptive road name or identifier.
     * @param available             Operational availability of this road segment (true = open, false = closed).
     */
    public Edge(Node source,
                Node destination,
                double distanceKm,
                double baseTravelTimeMinutes,
                TrafficCondition trafficCondition,
                String roadName,
                boolean available) {
        this.source = source;
        this.destination = Objects.requireNonNull(destination, "Destination node must not be null");
        this.trafficCondition = trafficCondition != null ? trafficCondition : TrafficCondition.freeFlow();

        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) || distanceKm < 0.0) {
            throw new InvalidCostException("Distance in kilometres must be a non-negative finite number: " + distanceKm);
        }
        if (Double.isNaN(baseTravelTimeMinutes) || Double.isInfinite(baseTravelTimeMinutes) || baseTravelTimeMinutes < 0.0) {
            throw new InvalidCostException("Base travel time in minutes must be a non-negative finite number: " + baseTravelTimeMinutes);
        }

        this.distanceKm = distanceKm;
        this.baseTravelTimeMinutes = baseTravelTimeMinutes;
        this.currentTravelTimeMinutes = this.trafficCondition.calculateEffectiveTravelTime(baseTravelTimeMinutes);
        this.roadName = (roadName == null || roadName.trim().isEmpty()) ? "Unnamed Road" : roadName.trim();
        this.available = available;
    }

    /**
     * Constructs a directed edge with explicit traffic conditions and default open availability.
     */
    public Edge(Node source,
                Node destination,
                double distanceKm,
                double baseTravelTimeMinutes,
                TrafficCondition trafficCondition,
                String roadName) {
        this(source, destination, distanceKm, baseTravelTimeMinutes, trafficCondition, roadName, true);
    }

    /**
     * Constructs a directed edge with default free-flow traffic conditions.
     */
    public Edge(Node source, Node destination, double distanceKm, double travelTimeMinutes, String roadName) {
        this(source, destination, distanceKm, travelTimeMinutes, TrafficCondition.freeFlow(), roadName, true);
    }

    /**
     * Constructs a directed edge with default free-flow traffic without explicit road name.
     */
    public Edge(Node source, Node destination, double distanceKm, double travelTimeMinutes) {
        this(source, destination, distanceKm, travelTimeMinutes, null);
    }

    /**
     * Constructs a directed edge where source node is implicit from the graph context.
     */
    public Edge(Node destination, double distanceKm, double travelTimeMinutes, String roadName) {
        this(null, destination, distanceKm, travelTimeMinutes, roadName);
    }

    /**
     * Constructs a directed edge without an explicit road name or source node.
     */
    public Edge(Node destination, double distanceKm, double travelTimeMinutes) {
        this(null, destination, distanceKm, travelTimeMinutes, null);
    }

    public Optional<Node> getSource() {
        return Optional.ofNullable(source);
    }

    public Node getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    /**
     * Returns the static, unimpeded free-flow travel time in minutes.
     */
    public double getBaseTravelTimeMinutes() {
        return baseTravelTimeMinutes;
    }

    /**
     * Returns the current estimated travel time adjusted for dynamic real-time traffic conditions.
     */
    public double getTravelTimeMinutes() {
        return currentTravelTimeMinutes;
    }

    /**
     * Returns the current real-time dynamic traffic condition on this road segment.
     */
    public TrafficCondition getTrafficCondition() {
        return trafficCondition;
    }

    public String getRoadName() {
        return roadName;
    }

    /**
     * Indicates whether this road segment is currently open and traversable by vehicles.
     *
     * @return true if open, false if temporarily disabled or closed due to incidents.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Indicates whether this road segment is currently closed.
     *
     * @return true if closed/blocked, false if open.
     */
    public boolean isClosed() {
        return !available;
    }

    /**
     * Computes the current effective average vehicle speed in km/h.
     */
    public double getAverageSpeedKmh() {
        if (currentTravelTimeMinutes <= 0.0) {
            return 0.0;
        }
        return (distanceKm / currentTravelTimeMinutes) * 60.0;
    }

    /**
     * Computes the theoretical free-flow speed limit in km/h.
     */
    public double getFreeFlowSpeedKmh() {
        if (baseTravelTimeMinutes <= 0.0) {
            return 0.0;
        }
        return (distanceKm / baseTravelTimeMinutes) * 60.0;
    }

    /**
     * Creates an immutable copy of this edge with updated dynamic traffic conditions.
     * Static road distance, base travel time, and availability remain strictly preserved.
     */
    public Edge withTrafficCondition(TrafficCondition newCondition) {
        return new Edge(this.source, this.destination, this.distanceKm, this.baseTravelTimeMinutes, newCondition, this.roadName, this.available);
    }

    /**
     * Creates an immutable copy of this edge with updated operational availability.
     * Static road distance, base travel time, and traffic conditions remain strictly preserved.
     */
    public Edge withAvailability(boolean newAvailable) {
        return new Edge(this.source, this.destination, this.distanceKm, this.baseTravelTimeMinutes, this.trafficCondition, this.roadName, newAvailable);
    }

    /**
     * Creates an immutable copy of this edge associated with the specified source node.
     */
    public Edge withSource(Node newSource) {
        return new Edge(newSource, this.destination, this.distanceKm, this.baseTravelTimeMinutes, this.trafficCondition, this.roadName, this.available);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge edge)) return false;
        return Double.compare(edge.distanceKm, distanceKm) == 0
                && Double.compare(edge.currentTravelTimeMinutes, currentTravelTimeMinutes) == 0
                && available == edge.available
                && Objects.equals(source, edge.source)
                && destination.equals(edge.destination)
                && roadName.equals(edge.roadName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination, distanceKm, currentTravelTimeMinutes, roadName, available);
    }

    @Override
    public String toString() {
        String srcStr = source != null ? source.getId() : "?";
        return String.format("Edge{'%s' -> '%s' (%s), dist=%.2f km, time=%.2f min (base=%.2f min, traffic=%s, status=%s), road='%s'}",
                srcStr, destination.getId(), destination.getName(), distanceKm, currentTravelTimeMinutes,
                baseTravelTimeMinutes, trafficCondition.level(), available ? "OPEN" : "CLOSED", roadName);
    }
}
