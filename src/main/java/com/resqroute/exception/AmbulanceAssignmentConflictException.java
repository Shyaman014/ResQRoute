package com.resqroute.exception;

/**
 * Thrown when an ambulance or emergency assignment causes a conflicting state,
 * such as dispatching an already occupied ambulance or reassigning an active emergency.
 */
public class AmbulanceAssignmentConflictException extends ResQRouteException {

    private final String ambulanceId;
    private final String caseId;

    public AmbulanceAssignmentConflictException(String ambulanceId, String caseId, String message) {
        super(String.format("Assignment conflict for ambulance '%s' and case '%s': %s",
                ambulanceId, caseId, message));
        this.ambulanceId = ambulanceId;
        this.caseId = caseId;
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }

    public String getCaseId() {
        return caseId;
    }
}
