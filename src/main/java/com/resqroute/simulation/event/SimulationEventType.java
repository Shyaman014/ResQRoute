package com.resqroute.simulation.event;

/**
 * Enumeration of discrete operational events that occur during city scenario simulation.
 */
public enum SimulationEventType {

    /** An emergency case has been reported and entered the dispatch backlog. */
    EMERGENCY_REPORTED,

    /** An ambulance vehicle has been allocated and dispatched to an emergency scene. */
    AMBULANCE_DISPATCHED,

    /** An ambulance in transit has traversed an edge and arrived at an intermediate node waypoint. */
    AMBULANCE_WAYPOINT_REACHED,

    /** Ambulance arrived at the scene and patient has been picked up. */
    PATIENT_PICKED_UP,

    /** Destination hospital has been selected and admission capacity reserved. */
    HOSPITAL_SELECTED,

    /** Ambulance arrived at destination hospital. */
    HOSPITAL_ARRIVED,

    /** Patient handed over, bed occupied/admitted, and case successfully closed. */
    CASE_COMPLETED,

    /** Case was cancelled before completion. */
    CASE_CANCELLED,

    /** Road blockage or incident activated. */
    INCIDENT_ACTIVATED,

    /** Road incident cleared and roadway reopened. */
    INCIDENT_RESOLVED,

    /** Dynamic traffic congestion updated on roadway. */
    TRAFFIC_UPDATED,

    /** Active route dynamically recalculated to detour around a road incident or traffic jam. */
    ROUTE_RECALCULATED
}
