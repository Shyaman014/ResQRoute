package com.resqroute.model;

import java.util.Objects;

/**
 * Triage severity rating for emergency cases, establishing clear clinical and dispatch priorities.
 *
 * <p>Priority ranking follows standard emergency triage standards (such as the Manchester Triage Scale):
 * rank 1 is highest urgency (CRITICAL), progressing to rank 4 (LOW). Urgency weight is proportionally inverted
 * (100 down to 25) for numerical scoring models.</p>
 */
public enum EmergencySeverity {

    /**
     * Immediate life threat requiring instantaneous dispatch and priority routing.
     */
    CRITICAL(1, 100),

    /**
     * Severe condition requiring urgent medical intervention.
     */
    HIGH(2, 75),

    /**
     * Potentially serious condition requiring timely medical evaluation.
     */
    MEDIUM(3, 50),

    /**
     * Minor or non-urgent emergency condition.
     */
    LOW(4, 25);

    private final int priorityRank;
    private final int urgencyWeight;

    EmergencySeverity(int priorityRank, int urgencyWeight) {
        this.priorityRank = priorityRank;
        this.urgencyWeight = urgencyWeight;
    }

    /**
     * Lower number indicates higher triage priority (1 = highest priority).
     */
    public int getPriorityRank() {
        return priorityRank;
    }

    /**
     * Higher score indicates higher urgency level (100 = highest urgency).
     */
    public int getUrgencyWeight() {
        return urgencyWeight;
    }

    /**
     * Checks if this severity has strictly higher dispatch priority than another severity.
     */
    public boolean isHigherPriorityThan(EmergencySeverity other) {
        Objects.requireNonNull(other, "Comparison severity must not be null");
        return this.priorityRank < other.priorityRank;
    }
}
