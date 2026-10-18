package com.resqroute.exception;

/**
 * Thrown when an underlying database, SQL execution, or persistence operation fails.
 * Keeps database internals encapsulated away from UI consumers while preserving the cause.
 */
public class PersistenceException extends ResQRouteException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
