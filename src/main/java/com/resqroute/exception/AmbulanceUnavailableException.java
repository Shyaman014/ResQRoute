package com.resqroute.exception;

/**
 * Thrown when an ambulance cannot be assigned to an emergency case because it is unavailable,
 * out of service, already assigned, or non-existent.
 */
public class AmbulanceUnavailableException extends ResQRouteException {

    private final String ambulanceId;

    public AmbulanceUnavailableException(String ambulanceId, String reason) {
        super(String.format("Ambulance '%s' is unavailable: %s", ambulanceId, reason));
        this.ambulanceId = ambulanceId;
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }
}
