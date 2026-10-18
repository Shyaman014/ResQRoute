package com.resqroute.exception;

/**
 * Thrown when persisted database records or external data sources violate domain invariants,
 * containing invalid coordinates, corrupt enum constants, or missing required attributes.
 */
public class CorruptedDataException extends PersistenceException {

    public CorruptedDataException(String message) {
        super(message);
    }

    public CorruptedDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
