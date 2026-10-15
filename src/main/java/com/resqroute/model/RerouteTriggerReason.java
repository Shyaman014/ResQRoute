package com.resqroute.model;

/**
 * Diagnostic reason triggering or influencing an automatic rerouting evaluation.
 */
public enum RerouteTriggerReason {

    /**
     * An upcoming roadway segment along the active path was closed or blocked.
     */
    ROAD_CLOSED,

    /**
     * Significant dynamic traffic congestion or incident slowdown detected on the active path.
     */
    SIGNIFICANT_SLOWDOWN,

    /**
     * A blocking incident was formally registered on the route.
     */
    INCIDENT_REPORTED,

    /**
     * The incident or traffic change occurred on an unrelated roadway not traversed by the active route.
     */
    UNRELATED_ROAD,

    /**
     * An identical event or state was already processed; skipped to prevent cyclic rerouting loops.
     */
    DUPLICATE_EVENT,

    /**
     * Manual or periodic verification request.
     */
    MANUAL_REQUEST
}
