package com.resqroute.exception;

/**
 * Thrown when attempting to release a capacity reservation that does not exist or was already released.
 */
public class ReservationNotFoundException extends ResQRouteException {

    private final String hospitalId;
    private final String caseId;

    public ReservationNotFoundException(String hospitalId, String caseId) {
        super("No active reservation found for case '" + caseId + "' at hospital '" + hospitalId + "'");
        this.hospitalId = hospitalId;
        this.caseId = caseId;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public String getCaseId() {
        return caseId;
    }
}
