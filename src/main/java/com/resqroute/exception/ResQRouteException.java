package com.resqroute.exception;

/**
 * Base unchecked exception for all ResQRoute domain errors.
 */
public class ResQRouteException extends RuntimeException {

    public ResQRouteException(String message) {
        super(message);
    }

    public ResQRouteException(String message, Throwable cause) {
        super(message, cause);
    }
}
