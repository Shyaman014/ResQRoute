package com.resqroute.scheduling;

import com.resqroute.model.EmergencyCase;

import java.time.Instant;

/**
 * Strategy interface defining priority calculation and dynamic scheduling rules for emergency cases.
 *
 * <p>Decouples triage algorithms and aging policies from the queue storage structure,
 * allowing dispatch prioritization mechanisms to evolve independently.</p>
 */
public interface EmergencySchedulingPolicy {

    /**
     * Calculates the dynamic priority score of an emergency case at a given point in time.
     * Higher score indicates higher dispatch urgency.
     *
     * @param emergencyCase  The emergency case to evaluate; must not be null.
     * @param evaluationTime The timestamp at which to evaluate waiting time; must not be null.
     * @return Effective numerical priority score.
     */
    double calculatePriorityScore(EmergencyCase emergencyCase, Instant evaluationTime);

    /**
     * Compares two emergency cases for dispatch priority at a given point in time.
     *
     * <p>Standard comparison contract:
     * <ul>
     *   <li>Returns negative if {@code c1} has higher priority (dispatched first).</li>
     *   <li>Returns positive if {@code c2} has higher priority.</li>
     *   <li>Returns 0 if priority, creation timestamp, and ID are identical.</li>
     * </ul>
     * </p>
     *
     * @param c1             First emergency case.
     * @param c2             Second emergency case.
     * @param evaluationTime The evaluation timestamp.
     * @return Comparison result following the Comparator contract.
     */
    int compare(EmergencyCase c1, EmergencyCase c2, Instant evaluationTime);

    /**
     * Returns the human-readable identifier of this scheduling policy.
     */
    String getPolicyName();

    /**
     * Returns the aging rate in priority points accumulated per minute of waiting time.
     */
    double getAgingRatePerMinute();
}
