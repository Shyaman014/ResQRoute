package com.resqroute.exception;

/**
 * Thrown when attempting to reserve capacity on a hospital that has zero available capacity.
 */
public class HospitalCapacityExceededException extends ResQRouteException {

    private final String hospitalId;

    public HospitalCapacityExceededException(String hospitalId) {
        super("Hospital '" + hospitalId + "' has no available capacity for admission");
        this.hospitalId = hospitalId;
    }

    public HospitalCapacityExceededException(String hospitalId, String details) {
        super("Hospital '" + hospitalId + "' capacity exceeded: " + details);
        this.hospitalId = hospitalId;
    }

    public String getHospitalId() {
        return hospitalId;
    }
}
