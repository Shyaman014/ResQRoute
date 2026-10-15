package com.resqroute.exception;

/**
 * Thrown when an edge definition is structurally invalid (such as a self-loop).
 */
public class InvalidEdgeException extends ResQRouteException {

    public InvalidEdgeException(String message) {
        super(message);
    }
}
