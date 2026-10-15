package com.resqroute.exception;

/**
 * Thrown when attempting to add a road between two nodes when a directed road between
 * them already exists.
 */
public class DuplicateEdgeException extends ResQRouteException {

    private final String sourceId;
    private final String destinationId;

    public DuplicateEdgeException(String sourceId, String destinationId) {
        super(String.format("Road from '%s' to '%s' already exists in the graph.", sourceId, destinationId));
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
