package com.resqroute.model;

import java.util.List;
import java.util.Objects;

/**
 * State container tracking an active emergency transit route, its destination,
 * and current transit progress along the planned path.
 */
public final class TrackedRoute {

    private final String trackingId;
    private final RouteResult activeRoute;
    private final String destinationId;
    private final int currentWaypointIndex;

    /**
     * Primary constructor.
     *
     * @param trackingId           Unique tracking identifier (e.g., vehicle or mission ID).
     * @param activeRoute          Active planned route; must be reachable.
     * @param currentWaypointIndex Zero-indexed position of the node the vehicle is currently at or has reached.
     */
    public TrackedRoute(String trackingId, RouteResult activeRoute, int currentWaypointIndex) {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking ID must not be null or blank");
        }
        this.trackingId = trackingId.trim();
        this.activeRoute = Objects.requireNonNull(activeRoute, "Active route must not be null");

        if (!activeRoute.isReachable()) {
            throw new IllegalArgumentException("Cannot track an unreachable route");
        }

        List<Node> path = activeRoute.getPath();
        if (currentWaypointIndex < 0 || currentWaypointIndex >= path.size()) {
            throw new IllegalArgumentException(String.format(
                    "Current waypoint index (%d) must be within path bounds [0, %d]",
                    currentWaypointIndex, path.size() - 1));
        }

        this.currentWaypointIndex = currentWaypointIndex;
        this.destinationId = activeRoute.getDestinationId();
    }

    /**
     * Constructs a tracked route initialized at the source waypoint (index 0).
     */
    public TrackedRoute(String trackingId, RouteResult activeRoute) {
        this(trackingId, activeRoute, 0);
    }

    public String getTrackingId() {
        return trackingId;
    }

    public RouteResult getActiveRoute() {
        return activeRoute;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    /**
     * Returns the node ID corresponding to the vehicle's current location along the path.
     */
    public String getCurrentOriginNodeId() {
        return activeRoute.getPath().get(currentWaypointIndex).getId();
    }

    /**
     * Checks whether the specified directed road segment is part of the remaining upcoming path
     * (at or ahead of the vehicle's current waypoint).
     *
     * @param sourceId      Source node identifier.
     * @param destinationId Destination node identifier.
     * @return true if the directed road lies ahead on the active route, false otherwise.
     */
    public boolean containsRemainingRoad(String sourceId, String destinationId) {
        if (sourceId == null || destinationId == null) {
            return false;
        }
        String src = sourceId.trim();
        String dst = destinationId.trim();

        List<Node> nodes = activeRoute.getPath();
        for (int i = currentWaypointIndex; i < nodes.size() - 1; i++) {
            if (nodes.get(i).getId().equals(src) && nodes.get(i + 1).getId().equals(dst)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates an updated TrackedRoute reflecting a new replacement route from the current location.
     */
    public TrackedRoute withNewRoute(RouteResult newRoute) {
        return new TrackedRoute(this.trackingId, newRoute, 0);
    }

    /**
     * Advances the vehicle's progress to a subsequent waypoint along the current path.
     */
    public TrackedRoute advanceToWaypoint(int newIndex) {
        return new TrackedRoute(this.trackingId, this.activeRoute, newIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedRoute that)) return false;
        return currentWaypointIndex == that.currentWaypointIndex
                && trackingId.equals(that.trackingId)
                && activeRoute.equals(that.activeRoute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackingId, activeRoute, currentWaypointIndex);
    }

    @Override
    public String toString() {
        return String.format("TrackedRoute{id='%s', current='%s', destination='%s', waypoint=%d/%d, time=%.2f min}",
                trackingId, getCurrentOriginNodeId(), destinationId, currentWaypointIndex,
                activeRoute.getPath().size() - 1, activeRoute.getTotalTravelTimeMinutes());
    }
}
