package com.resqroute.exception;

/**
 * Thrown when an illegal or unsupported lifecycle status transition is attempted on an emergency case.
 */
public class InvalidStatusTransitionException extends ResQRouteException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(String caseId, String fromStatus, String toStatus, String reason) {
        super(String.format("Invalid status transition for case '%s' from %s to %s: %s",
                caseId, fromStatus, toStatus, reason));
    }
}
