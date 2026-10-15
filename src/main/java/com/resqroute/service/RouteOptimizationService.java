package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.RoutingAlgorithm;
import com.resqroute.exception.RouteNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.RouteResult;

import java.util.Objects;

/**
 * High-level orchestration service for calculating optimal emergency ambulance routes.
 *
 * <p>Coordinates route queries, delegates algorithmic computation to the injected
 * {@link RoutingAlgorithm}, and provides both safe (returning {@link RouteResult#isReachable()})
 * and strict (throwing {@link RouteNotFoundException}) querying patterns.</p>
 */
public class RouteOptimizationService {

    private final RoadGraph roadGraph;
    private final RoutingAlgorithm routingAlgorithm;

    /**
     * Constructs a RouteOptimizationService with custom road graph and routing strategy.
     *
     * @param roadGraph        The underlying road network graph.
     * @param routingAlgorithm The routing strategy (e.g., Dijkstra).
     */
    public RouteOptimizationService(RoadGraph roadGraph, RoutingAlgorithm routingAlgorithm) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.routingAlgorithm = Objects.requireNonNull(routingAlgorithm, "RoutingAlgorithm must not be null");
    }

    /**
     * Constructs a RouteOptimizationService using the default {@link DijkstraRoutingAlgorithm}.
     *
     * @param roadGraph The underlying road network graph.
     */
    public RouteOptimizationService(RoadGraph roadGraph) {
        this(roadGraph, new DijkstraRoutingAlgorithm());
    }

    /**
     * Finds the fastest route between source and destination.
     *
     * @param sourceId      Origin node identifier.
     * @param destinationId Destination node identifier.
     * @return RouteResult object detailing the route.
     */
    public RouteResult findFastestRoute(String sourceId, String destinationId) {
        return routingAlgorithm.findFastestRoute(roadGraph, sourceId, destinationId);
    }

    /**
     * Finds the fastest route between source and destination, or throws an exception if unreachable.
     *
     * @param sourceId      Origin node identifier.
     * @param destinationId Destination node identifier.
     * @return Reachable RouteResult.
     * @throws RouteNotFoundException if no path exists.
     */
    public RouteResult findFastestRouteOrThrow(String sourceId, String destinationId) {
        RouteResult result = findFastestRoute(sourceId, destinationId);
        if (!result.isReachable()) {
            throw new RouteNotFoundException(sourceId, destinationId);
        }
        return result;
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public RoutingAlgorithm getRoutingAlgorithm() {
        return routingAlgorithm;
    }
}
