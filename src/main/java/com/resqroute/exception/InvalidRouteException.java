package com.resqroute.exception;

/**
 * Thrown when route query parameters or path specifications are invalid
 * (e.g., origin node identical to destination, negative weights, or disconnected endpoints).
 */
public class InvalidRouteException extends ResQRouteException {

    private final String sourceNodeId;
    private final String destinationNodeId;

    public InvalidRouteException(String sourceNodeId, String destinationNodeId, String message) {
        super(String.format("Invalid route from '%s' to '%s': %s", sourceNodeId, destinationNodeId, message));
        this.sourceNodeId = sourceNodeId;
        this.destinationNodeId = destinationNodeId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getDestinationNodeId() {
        return destinationNodeId;
    }
}
