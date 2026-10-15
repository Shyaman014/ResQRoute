package com.resqroute.exception;

/**
 * Thrown when an edge or travel cost is invalid (e.g., negative travel time, negative distance, NaN, or infinite).
 */
public class InvalidCostException extends ResQRouteException {

    public InvalidCostException(String message) {
        super(message);
    }
}
