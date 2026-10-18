package com.resqroute.exception;

/**
 * Thrown when a queried hospital does not exist in the management registry.
 */
public class HospitalNotFoundException extends ResQRouteException {

    private final String hospitalId;

    public HospitalNotFoundException(String hospitalId) {
        super("Hospital not found with ID: '" + hospitalId + "'");
        this.hospitalId = hospitalId;
    }

    public String getHospitalId() {
        return hospitalId;
    }
}
