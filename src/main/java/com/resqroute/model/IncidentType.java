package com.resqroute.model;

/**
 * Standardized categorization of roadway incidents that may impact emergency transit.
 */
public enum IncidentType {

    /**
     * Motor vehicle collision or crash obstructing one or more lanes.
     */
    ACCIDENT,

    /**
     * Complete physical closure of the road (e.g., civic events, fallen trees, infrastructure failure).
     */
    ROAD_CLOSURE,

    /**
     * Active construction or roadwork impeding flow or closing lanes.
     */
    CONSTRUCTION,

    /**
     * Temporary blockage (e.g., stalled vehicle, flash flood, debris).
     */
    TEMPORARY_BLOCKAGE
}
