package com.resqroute.exception;

/**
 * Thrown when attempting to create an emergency case with an identifier that already exists.
 */
public class DuplicateCaseException extends ResQRouteException {

    private final String caseId;

    public DuplicateCaseException(String caseId) {
        super("An emergency case already exists with ID: '" + caseId + "'");
        this.caseId = caseId;
    }

    public String getCaseId() {
        return caseId;
    }
}
