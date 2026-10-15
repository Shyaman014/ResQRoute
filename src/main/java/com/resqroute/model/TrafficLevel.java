package com.resqroute.model;

/**
 * Enumeration of standardized roadway traffic congestion levels.
 * Each level defines a baseline delay multiplier applied to base free-flow travel time.
 */
public enum TrafficLevel {

    /** Free-flowing traffic at the maximum posted speed limit (delay multiplier = 1.0x). */
    FREE_FLOW(1.0, "Free Flow - Speeds at or near speed limit"),

    /** Minor volume causing slight deceleration at bottlenecks (delay multiplier = 1.25x). */
    LIGHT(1.25, "Light Congestion - Unimpeded flow with minor slowing"),

    /** Typical urban peak traffic with recurring deceleration (delay multiplier = 1.6x). */
    MODERATE(1.60, "Moderate Congestion - Stop-and-go pockets"),

    /** Dense bumper-to-bumper congestion (delay multiplier = 2.2x). */
    HEAVY(2.20, "Heavy Congestion - Significant delays and reduced speeds"),

    /** Gridlock or major incident resulting in severe delays (delay multiplier = 3.5x). */
    SEVERE(3.50, "Severe Congestion - Gridlock or incident-induced stoppage");

    private final double defaultDelayMultiplier;
    private final String description;

    TrafficLevel(double defaultDelayMultiplier, String description) {
        this.defaultDelayMultiplier = defaultDelayMultiplier;
        this.description = description;
    }

    public double getDefaultDelayMultiplier() {
        return defaultDelayMultiplier;
    }

    public String getDescription() {
        return description;
    }
}
