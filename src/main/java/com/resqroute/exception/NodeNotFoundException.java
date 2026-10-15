package com.resqroute.exception;

/**
 * Thrown when a specified node identifier does not exist within the road graph.
 */
public class NodeNotFoundException extends ResQRouteException {

    private final String nodeId;

    public NodeNotFoundException(String nodeId) {
        super("Node not found in graph: '" + nodeId + "'");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
