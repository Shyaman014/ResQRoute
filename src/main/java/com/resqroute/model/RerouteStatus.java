package com.resqroute.model;

/**
 * Outcome status of an automatic rerouting evaluation.
 */
public enum RerouteStatus {

    /**
     * No rerouting is necessary. The active route remains optimal, or the incident affects an unrelated road.
     */
    NO_ACTION_REQUIRED,

    /**
     * An alternative route was successfully identified, evaluated, and assigned to the tracked entity.
     */
    REROUTED,

    /**
     * The active route is blocked or severed, and no traversable alternative path exists to the destination.
     */
    NO_ALTERNATIVE_ROUTE,

    /**
     * Duplicate or redundant event was detected; recalculation was skipped to prevent repeated evaluation loops.
     */
    SKIPPED_DUPLICATE
}
