package com.resqroute.exception;

/**
 * Thrown when attempting to insert a node with an identifier that already exists in the road graph.
 */
public class DuplicateNodeException extends ResQRouteException {

    private final String nodeId;

    public DuplicateNodeException(String nodeId) {
        super("Node with ID '" + nodeId + "' already exists in the graph.");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
