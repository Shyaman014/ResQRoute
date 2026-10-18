package com.resqroute.model;

/**
 * Lifecycle status of an emergency case in the ResQRoute dispatch pipeline.
 */
public enum EmergencyStatus {

    /**
     * Case has been created and recorded in the system.
     */
    REPORTED,

    /**
     * Case has been validated and placed into the dispatch triage queue.
     */
    WAITING_FOR_DISPATCH,

    /**
     * An emergency ambulance has been allocated to respond to this case.
     */
    AMBULANCE_ASSIGNED,

    /**
     * Assigned ambulance is traveling toward the patient pickup location.
     */
    AMBULANCE_EN_ROUTE,

    /**
     * Ambulance has arrived on scene and patient has been picked up/secured.
     */
    PATIENT_PICKED_UP,

    /**
     * Ambulance is actively transporting the patient to the destination hospital.
     */
    TRANSPORTING,

    /**
     * Emergency mission is finished and patient handed over to hospital facility (terminal).
     */
    COMPLETED,

    /**
     * Emergency case was revoked, false alarm, or safely cancelled prior to patient transport (terminal).
     */
    CANCELLED;

    /**
     * Returns true if this status represents an active (non-terminal) case lifecycle state.
     */
    public boolean isActive() {
        return !isTerminal();
    }

    /**
     * Returns true if this status is terminal (COMPLETED or CANCELLED).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Returns true if an ambulance must be assigned for this status.
     */
    public boolean requiresAmbulance() {
        return this == AMBULANCE_ASSIGNED
                || this == AMBULANCE_EN_ROUTE
                || this == PATIENT_PICKED_UP
                || this == TRANSPORTING;
    }
}
