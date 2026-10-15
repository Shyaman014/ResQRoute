package com.resqroute.exception;

/**
 * Thrown when attempting to register an incident with an identifier that is already active.
 */
public class DuplicateIncidentException extends ResQRouteException {

    public DuplicateIncidentException(String incidentId) {
        super("An active incident already exists with ID: '" + incidentId + "'");
    }
}
