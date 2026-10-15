package com.resqroute.algorithm;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.RouteResult;

/**
 * Strategy interface defining emergency routing algorithms.
 * Implementations compute optimal paths through a road graph prioritizing travel time.
 */
public interface RoutingAlgorithm {

    /**
     * Calculates the fastest emergency route between source and destination nodes.
     *
     * @param graph         Road network graph; must not be null.
     * @param sourceId      Identifier of origin waypoint; must exist in graph.
     * @param destinationId Identifier of destination waypoint; must exist in graph.
     * @return RouteResult encapsulating path sequence, physical distance, and total travel time.
     */
    RouteResult findFastestRoute(RoadGraph graph, String sourceId, String destinationId);
}
