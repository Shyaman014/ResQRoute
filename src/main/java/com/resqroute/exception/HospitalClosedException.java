package com.resqroute.exception;

/**
 * Thrown when attempting an admission or reservation on a hospital that is operational status CLOSED.
 */
public class HospitalClosedException extends ResQRouteException {

    private final String hospitalId;

    public HospitalClosedException(String hospitalId) {
        super("Hospital '" + hospitalId + "' is CLOSED for emergency admissions");
        this.hospitalId = hospitalId;
    }

    public String getHospitalId() {
        return hospitalId;
    }
}
