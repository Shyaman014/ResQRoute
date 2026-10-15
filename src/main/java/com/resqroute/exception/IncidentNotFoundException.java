package com.resqroute.exception;

/**
 * Thrown when an incident operation references an incident ID that does not exist or is not active.
 */
public class IncidentNotFoundException extends ResQRouteException {

    public IncidentNotFoundException(String incidentId) {
        super("Incident not found with ID: '" + incidentId + "'");
    }
}
