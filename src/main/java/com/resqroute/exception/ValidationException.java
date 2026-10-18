package com.resqroute.exception;

/**
 * Thrown when an input parameter or domain precondition fails validation before mutating state.
 */
public class ValidationException extends ResQRouteException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
