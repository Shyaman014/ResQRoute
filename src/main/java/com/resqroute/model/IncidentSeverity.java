package com.resqroute.model;

/**
 * Severity rating for roadway incidents affecting transit safety and road capacity.
 */
public enum IncidentSeverity {

    /**
     * Minor incident (e.g. debris on shoulder, minimal disruption).
     */
    LOW,

    /**
     * Moderate incident (e.g. single-lane blockage, noticeable slowdown).
     */
    MEDIUM,

    /**
     * High severity incident (e.g. multi-vehicle crash, major blockage).
     */
    HIGH,

    /**
     * Critical incident requiring complete road closure to all vehicular traffic.
     */
    CRITICAL
}
