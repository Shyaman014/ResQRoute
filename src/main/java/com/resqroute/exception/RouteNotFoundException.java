package com.resqroute.exception;

/**
 * Thrown when no traversable path exists between the requested source and destination nodes.
 */
public class RouteNotFoundException extends ResQRouteException {

    private final String sourceId;
    private final String destinationId;

    public RouteNotFoundException(String sourceId, String destinationId) {
        super(String.format("No route exists from source '%s' to destination '%s'.", sourceId, destinationId));
        this.sourceId = sourceId;
        this.destinationId = destinationId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }
}
