package com.resqroute.exception;

/**
 * Thrown when attempting to enqueue an emergency case that is already active in the dispatch queue.
 */
public class DuplicateQueueEntryException extends ResQRouteException {

    private final String caseId;

    public DuplicateQueueEntryException(String caseId) {
        super("Emergency case is already present in the dispatch queue: '" + caseId + "'");
        this.caseId = caseId;
    }

    public String getCaseId() {
        return caseId;
    }
}
