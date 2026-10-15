package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.RoutingAlgorithm;
import com.resqroute.algorithm.YenKShortestPathsAlgorithm;
import com.resqroute.exception.RouteNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.RouteComparisonReport;
import com.resqroute.model.RouteResult;

import java.util.List;
import java.util.Objects;

/**
 * High-level orchestration service for calculating optimal and alternative emergency ambulance routes.
 *
 * <p>Coordinates route queries, delegates algorithmic computation to the injected
 * {@link RoutingAlgorithm}, and provides multi-route alternative generation via Yen's K-shortest
 * paths algorithm and analytical route comparisons.</p>
 */
public class RouteOptimizationService {

    private final RoadGraph roadGraph;
    private final RoutingAlgorithm routingAlgorithm;
    private final YenKShortestPathsAlgorithm yenAlgorithm;
    private final RouteComparisonService comparisonService;

    /**
     * Constructs a RouteOptimizationService with custom road graph and routing strategy.
     *
     * @param roadGraph        The underlying road network graph.
     * @param routingAlgorithm The routing strategy (e.g., Dijkstra or A*).
     */
    public RouteOptimizationService(RoadGraph roadGraph, RoutingAlgorithm routingAlgorithm) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.routingAlgorithm = Objects.requireNonNull(routingAlgorithm, "RoutingAlgorithm must not be null");
        this.yenAlgorithm = new YenKShortestPathsAlgorithm();
        this.comparisonService = new RouteComparisonService();
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
     * Finds the single fastest route between source and destination.
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

    /**
     * Finds up to K loopless alternative emergency routes between source and destination, ranked by travel time.
     *
     * @param sourceId      Origin node identifier.
     * @param destinationId Destination node identifier.
     * @param k             Maximum number of routes to discover; must be >= 1.
     * @return Ordered list of up to K alternative RouteResult instances.
     */
    public List<RouteResult> findKFastestRoutes(String sourceId, String destinationId, int k) {
        return yenAlgorithm.findKShortestPaths(roadGraph, sourceId, destinationId, k);
    }

    /**
     * Finds and compiles a detailed analytical comparison report of up to K alternative routes.
     *
     * @param sourceId      Origin node identifier.
     * @param destinationId Destination node identifier.
     * @param k             Maximum number of alternative routes.
     * @return RouteComparisonReport detailing exact delta time and distance metrics.
     */
    public RouteComparisonReport compareKFastestRoutes(String sourceId, String destinationId, int k) {
        List<RouteResult> routes = findKFastestRoutes(sourceId, destinationId, k);
        if (routes.isEmpty()) {
            return new RouteComparisonReport(sourceId, destinationId, List.of());
        }
        return comparisonService.compareRoutes(routes);
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public RoutingAlgorithm getRoutingAlgorithm() {
        return routingAlgorithm;
    }
}
