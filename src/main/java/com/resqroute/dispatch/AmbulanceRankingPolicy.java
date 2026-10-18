package com.resqroute.dispatch;

import com.resqroute.model.EmergencyCase;

/**
 * Strategy interface ranking feasible candidate ambulances for a specific emergency dispatch.
 *
 * <p>Separates vehicle selection scoring and candidate ranking from the dispatch orchestration engine,
 * enabling alternative policies (e.g. fastest arrival, fuel conservation, ALS preservation) to evolve independently.</p>
 */
public interface AmbulanceRankingPolicy {

    /**
     * Compares two feasible candidate ambulances for an emergency case.
     *
     * <p>Contract:
     * <ul>
     *   <li>Returns negative if {@code c1} is preferred over {@code c2}.</li>
     *   <li>Returns positive if {@code c2} is preferred over {@code c1}.</li>
     *   <li>Returns 0 if candidates are deemed equivalent.</li>
     * </ul>
     * </p>
     *
     * @param c1            First candidate.
     * @param c2            Second candidate.
     * @param emergencyCase The emergency case being dispatched.
     * @return Standard Comparator integer comparison result.
     */
    int compare(AmbulanceCandidate c1, AmbulanceCandidate c2, EmergencyCase emergencyCase);

    /**
     * Returns the human-readable identifier of this ranking policy.
     */
    String getPolicyName();
}
