package com.resqroute.dispatch;

/**
 * Status outcomes resulting from an automated emergency dispatch evaluation.
 */
public enum DispatchStatus {

    /**
     * An optimal eligible ambulance was successfully allocated and dispatched.
     */
    SUCCESS,

    /**
     * Dispatch queue is empty; no eligible emergencies waiting for assignment.
     */
    NO_PENDING_EMERGENCIES,

    /**
     * Emergency cases are pending, but all fleet ambulances are busy, assigned, or out of service.
     */
    NO_AVAILABLE_AMBULANCES,

    /**
     * Ambulances are available, but none possess the clinical capabilities required by the case.
     */
    NO_COMPATIBLE_AMBULANCES,

    /**
     * Compatible available ambulances exist, but all routes to the pickup location are blocked or severed.
     */
    UNREACHABLE_PICKUP,

    /**
     * Target emergency case is ineligible for dispatch (e.g. already cancelled or completed).
     */
    CASE_INELIGIBLE,

    /**
     * Concurrency conflict or state machine failure occurred during atomic assignment.
     */
    FAILED_TRANSACTION;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
