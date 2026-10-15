package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.util.*;

/**
 * Deterministic service that continuously monitors active emergency routes and calculates
 * optimal replacement detours when road closures, blockages, or severe traffic congestion arise.
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li><b>Targeted Recalculation:</b> Inspects remaining route waypoints and discards events
 *       on unrelated roads without invoking graph searches.</li>
 *   <li><b>Loop and Oscillation Prevention:</b> Tracks processed event signatures to prevent
 *       repeated rerouting cycles caused by duplicate or redundant telemetry updates.</li>
 *   <li><b>Exhaustive Failure Reporting:</b> Identifies when a route is severed and reports
 *       critical unavailability when no traversable detour exists.</li>
 *   <li><b>Stateful or Stateless:</b> Supports tracking long-running vehicle missions or performing
 *       one-off route evaluations.</li>
 * </ul>
 * </p>
 */
public class AutomaticReroutingService {

    private final RoadGraph graph;
    private final DijkstraRoutingAlgorithm dijkstra;
    private final Map<String, TrackedRoute> trackedRoutes;
    private final Set<String> processedUpdateSignatures;
    private double minTimeSavingsThresholdMinutes = 0.5;

    /**
     * Constructs an AutomaticReroutingService with an injected Dijkstra algorithm.
     *
     * @param graph    Road network graph; must not be null.
     * @param dijkstra Dijkstra routing engine; must not be null.
     */
    public AutomaticReroutingService(RoadGraph graph, DijkstraRoutingAlgorithm dijkstra) {
        this.graph = Objects.requireNonNull(graph, "RoadGraph must not be null");
        this.dijkstra = Objects.requireNonNull(dijkstra, "DijkstraRoutingAlgorithm must not be null");
        this.trackedRoutes = new LinkedHashMap<>();
        this.processedUpdateSignatures = new HashSet<>();
    }

    /**
     * Constructs an AutomaticReroutingService with default Dijkstra routing engine.
     */
    public AutomaticReroutingService(RoadGraph graph) {
        this(graph, new DijkstraRoutingAlgorithm());
    }

    /**
     * Registers an active route for continuous dynamic monitoring.
     *
     * @param trackingId Unique identifier for this tracked transit.
     * @param route      Active reachable route.
     * @return The created TrackedRoute.
     */
    public TrackedRoute trackRoute(String trackingId, RouteResult route) {
        TrackedRoute tracked = new TrackedRoute(trackingId, route);
        trackedRoutes.put(tracked.getTrackingId(), tracked);
        return tracked;
    }

    /**
     * Registers a pre-constructed TrackedRoute.
     */
    public void trackRoute(TrackedRoute trackedRoute) {
        Objects.requireNonNull(trackedRoute, "TrackedRoute must not be null");
        trackedRoutes.put(trackedRoute.getTrackingId(), trackedRoute);
    }

    /**
     * Retrieves a currently tracked route by its identifier.
     */
    public Optional<TrackedRoute> getTrackedRoute(String trackingId) {
        if (trackingId == null) return Optional.empty();
        return Optional.ofNullable(trackedRoutes.get(trackingId.trim()));
    }

    /**
     * Removes a tracked route from dynamic monitoring.
     */
    public boolean untrackRoute(String trackingId) {
        if (trackingId == null) return false;
        return trackedRoutes.remove(trackingId.trim()) != null;
    }

    /**
     * Clears all tracked routes.
     */
    public void clearTrackedRoutes() {
        trackedRoutes.clear();
    }

    /**
     * Returns total count of actively tracked routes.
     */
    public int getTrackedRouteCount() {
        return trackedRoutes.size();
    }

    /**
     * Evaluates all tracked routes in response to an individual directed road closure.
     *
     * @param sourceId      Source node of closed road.
     * @param destinationId Destination node of closed road.
     * @return List of RerouteResponse evaluations for every affected tracked route.
     */
    public List<RerouteResponse> onRoadClosure(String sourceId, String destinationId) {
        List<RerouteResponse> responses = new ArrayList<>();
        for (TrackedRoute tracked : new ArrayList<>(trackedRoutes.values())) {
            responses.add(evaluateTrackedRouteForRoadChange(tracked, sourceId, destinationId, true));
        }
        return Collections.unmodifiableList(responses);
    }

    /**
     * Evaluates a single specific tracked route in response to a road closure.
     */
    public RerouteResponse onRoadClosureForTrackedRoute(String trackingId, String sourceId, String destinationId) {
        TrackedRoute tracked = trackedRoutes.get(trackingId);
        if (tracked == null) {
            throw new IllegalArgumentException("No active tracked route with ID: " + trackingId);
        }
        return evaluateTrackedRouteForRoadChange(tracked, sourceId, destinationId, true);
    }

    /**
     * Evaluates all tracked routes in response to a reported road incident.
     */
    public List<RerouteResponse> onRoadIncident(RoadIncident incident) {
        Objects.requireNonNull(incident, "Incident must not be null");
        return onRoadClosure(incident.getSourceNodeId(), incident.getDestinationNodeId());
    }

    /**
     * Evaluates all tracked routes when traffic conditions change on a road segment.
     */
    public List<RerouteResponse> onTrafficChange(String sourceId, String destinationId) {
        List<RerouteResponse> responses = new ArrayList<>();
        for (TrackedRoute tracked : new ArrayList<>(trackedRoutes.values())) {
            responses.add(evaluateTrackedRouteForRoadChange(tracked, sourceId, destinationId, false));
        }
        return Collections.unmodifiableList(responses);
    }

    /**
     * Core evaluation logic determining whether a tracked route requires replacement.
     */
    private RerouteResponse evaluateTrackedRouteForRoadChange(TrackedRoute tracked,
                                                              String sourceId,
                                                              String destinationId,
                                                              boolean isClosure) {
        String trackingId = tracked.getTrackingId();
        RouteResult currentRoute = tracked.getActiveRoute();

        // 1. Avoid unnecessary recalculation: If affected road is not on remaining route, abort early
        if (!tracked.containsRemainingRoad(sourceId, destinationId)) {
            return RerouteResponse.noActionRequired(
                    trackingId,
                    RerouteTriggerReason.UNRELATED_ROAD,
                    currentRoute,
                    String.format("Unrelated road '%s' -> '%s' does not affect active route to '%s'.",
                            sourceId, destinationId, tracked.getDestinationId())
            );
        }

        // 2. Prevent repeated rerouting loops caused by duplicate updates
        String signature = String.format("%s:%s->%s:%s:%s",
                trackingId, sourceId, destinationId, isClosure ? "CLOSED" : "TRAFFIC",
                currentRoute.getNodeIds().toString());
        if (processedUpdateSignatures.contains(signature)) {
            return RerouteResponse.skippedDuplicate(
                    trackingId,
                    RerouteTriggerReason.DUPLICATE_EVENT,
                    currentRoute,
                    signature
            );
        }
        processedUpdateSignatures.add(signature);

        // 3. Compute remaining metrics of the current path from current position
        String origin = tracked.getCurrentOriginNodeId();
        String destination = tracked.getDestinationId();

        double oldRemainingTime = computeRemainingTravelTime(tracked);
        double oldRemainingDist = computeRemainingDistance(tracked);

        // 4. Run Dijkstra from current origin to destination
        RouteResult replacement = dijkstra.findFastestRoute(graph, origin, destination);

        // 5. Handle case where no valid alternative exists
        if (!replacement.isReachable()) {
            return RerouteResponse.noAlternative(trackingId, RerouteTriggerReason.ROAD_CLOSED, currentRoute, sourceId, destinationId);
        }

        // Check if the replacement route is identical to the current remaining path
        List<String> currentRemainingNodeIds = getCurrentRemainingNodeIds(tracked);
        if (replacement.getNodeIds().equals(currentRemainingNodeIds)) {
            return RerouteResponse.noActionRequired(
                    trackingId,
                    RerouteTriggerReason.MANUAL_REQUEST,
                    currentRoute,
                    "Current path remains optimal; no reroute necessary."
            );
        }

        // For traffic changes (non-closures), only reroute if the time savings exceed threshold
        double timeSaved = oldRemainingTime - replacement.getTotalTravelTimeMinutes();
        if (!isClosure && timeSaved < minTimeSavingsThresholdMinutes) {
            return RerouteResponse.noActionRequired(
                    trackingId,
                    RerouteTriggerReason.SIGNIFICANT_SLOWDOWN,
                    currentRoute,
                    String.format("Alternative saves %.2f min, below minimum reroute threshold of %.2f min.",
                            timeSaved, minTimeSavingsThresholdMinutes)
            );
        }

        // 6. Assemble complete new route (prefix up to current waypoint + replacement route)
        RouteResult completeNewRoute = assembleCompleteRoute(tracked, replacement);

        // Update tracked route state
        trackedRoutes.put(trackingId, tracked.withNewRoute(completeNewRoute));

        RerouteTriggerReason reason = isClosure ? RerouteTriggerReason.ROAD_CLOSED : RerouteTriggerReason.SIGNIFICANT_SLOWDOWN;
        return RerouteResponse.rerouted(
                trackingId,
                reason,
                currentRoute,
                completeNewRoute,
                oldRemainingTime,
                oldRemainingDist,
                null
        );
    }

    /**
     * Assembles a full route combining the traversed historical path prefix with the newly calculated replacement.
     */
    private RouteResult assembleCompleteRoute(TrackedRoute tracked, RouteResult replacement) {
        int idx = tracked.getCurrentWaypointIndex();
        if (idx == 0) {
            return replacement;
        }

        List<Node> oldNodes = tracked.getActiveRoute().getPath();
        List<Edge> oldEdges = tracked.getActiveRoute().getEdges();

        List<Node> prefixNodes = oldNodes.subList(0, idx); // up to current waypoint (exclusive)
        List<Edge> prefixEdges = oldEdges.subList(0, idx);

        List<Node> combinedNodes = new ArrayList<>(prefixNodes);
        combinedNodes.addAll(replacement.getPath());

        List<Edge> combinedEdges = new ArrayList<>(prefixEdges);
        combinedEdges.addAll(replacement.getEdges());

        double totalDist = combinedEdges.stream().mapToDouble(Edge::getDistanceKm).sum();
        double totalTime = combinedEdges.stream().mapToDouble(Edge::getTravelTimeMinutes).sum();

        return RouteResult.reachable(combinedNodes, combinedEdges, totalDist, totalTime);
    }

    private double computeRemainingTravelTime(TrackedRoute tracked) {
        List<Edge> edges = tracked.getActiveRoute().getEdges();
        double time = 0.0;
        for (int i = tracked.getCurrentWaypointIndex(); i < edges.size(); i++) {
            Edge e = edges.get(i);
            // If the edge is closed, cost is effectively infinity
            if (e.isClosed() || graph.isRoadClosed(e.getSource().map(Node::getId).orElse(""), e.getDestination().getId())) {
                return Double.POSITIVE_INFINITY;
            }
            time += e.getTravelTimeMinutes();
        }
        return time;
    }

    private double computeRemainingDistance(TrackedRoute tracked) {
        List<Edge> edges = tracked.getActiveRoute().getEdges();
        double dist = 0.0;
        for (int i = tracked.getCurrentWaypointIndex(); i < edges.size(); i++) {
            dist += edges.get(i).getDistanceKm();
        }
        return dist;
    }

    private List<String> getCurrentRemainingNodeIds(TrackedRoute tracked) {
        List<Node> nodes = tracked.getActiveRoute().getPath();
        return nodes.subList(tracked.getCurrentWaypointIndex(), nodes.size())
                .stream()
                .map(Node::getId)
                .toList();
    }

    public void setMinTimeSavingsThresholdMinutes(double minTimeSavingsThresholdMinutes) {
        if (minTimeSavingsThresholdMinutes < 0.0) {
            throw new IllegalArgumentException("Threshold must be non-negative: " + minTimeSavingsThresholdMinutes);
        }
        this.minTimeSavingsThresholdMinutes = minTimeSavingsThresholdMinutes;
    }

    public double getMinTimeSavingsThresholdMinutes() {
        return minTimeSavingsThresholdMinutes;
    }

    public void clearProcessedSignatures() {
        processedUpdateSignatures.clear();
    }

    public RoadGraph getGraph() {
        return graph;
    }

    public DijkstraRoutingAlgorithm getDijkstra() {
        return dijkstra;
    }
}
