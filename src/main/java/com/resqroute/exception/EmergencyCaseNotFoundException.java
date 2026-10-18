package com.resqroute.exception;

/**
 * Thrown when an emergency case identifier cannot be located in the management service.
 */
public class EmergencyCaseNotFoundException extends ResQRouteException {

    private final String caseId;

    public EmergencyCaseNotFoundException(String caseId) {
        super("Emergency case not found with ID: '" + caseId + "'");
        this.caseId = caseId;
    }

    public String getCaseId() {
        return caseId;
    }
}
