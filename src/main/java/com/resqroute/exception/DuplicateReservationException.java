package com.resqroute.exception;

/**
 * Thrown when an emergency case or reservation ID already holds an active reservation at a hospital.
 */
public class DuplicateReservationException extends ResQRouteException {

    private final String hospitalId;
    private final String caseId;

    public DuplicateReservationException(String hospitalId, String caseId) {
        super("Case '" + caseId + "' already holds an active capacity reservation at hospital '" + hospitalId + "'");
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
