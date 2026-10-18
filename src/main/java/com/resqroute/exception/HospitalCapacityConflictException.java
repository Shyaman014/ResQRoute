package com.resqroute.exception;

/**
 * Thrown when a hospital capacity reservation fails due to concurrent allocation,
 * negative capacity violations, or hospital capacity exhaustion.
 */
public class HospitalCapacityConflictException extends ResQRouteException {

    private final String hospitalId;
    private final int availableCapacity;

    public HospitalCapacityConflictException(String hospitalId, int availableCapacity, String message) {
        super(String.format("Hospital capacity conflict for '%s' (available: %d): %s",
                hospitalId, availableCapacity, message));
        this.hospitalId = hospitalId;
        this.availableCapacity = availableCapacity;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }
}
