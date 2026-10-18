package com.resqroute.model;

/**
 * Operational status of an emergency response ambulance in the fleet.
 */
public enum AmbulanceStatus {

    /**
     * Stationary or cruising, available immediately for new emergency dispatch.
     */
    AVAILABLE,

    /**
     * Dispatched and designated to an active emergency case.
     */
    ASSIGNED,

    /**
     * In transit toward the emergency pickup location.
     */
    EN_ROUTE,

    /**
     * Arrived on scene at the emergency pickup location.
     */
    ON_SCENE,

    /**
     * Transporting patient to destination hospital facility.
     */
    TRANSPORTING,

    /**
     * Returning to base station or designated hub after completing a mission.
     */
    RETURNING,

    /**
     * Temporarily unavailable due to maintenance, shift change, or offline status.
     */
    OUT_OF_SERVICE;

    /**
     * Returns true if ambulance is available for new emergency dispatch.
     */
    public boolean isAvailable() {
        return this == AVAILABLE;
    }
}
