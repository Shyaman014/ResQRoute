package com.resqroute.exception;

/**
 * Thrown when attempting to register a hospital with an ID that already exists.
 */
public class DuplicateHospitalException extends ResQRouteException {

    private final String hospitalId;

    public DuplicateHospitalException(String hospitalId) {
        super("A hospital is already registered with ID: '" + hospitalId + "'");
        this.hospitalId = hospitalId;
    }

    public String getHospitalId() {
        return hospitalId;
    }
}
