package com.resqroute.dispatch;

import com.resqroute.model.EmergencyCase;

/**
 * Standard dispatch policy prioritizing candidate ambulances strictly by minimum estimated travel time
 * to reach the patient pickup location (preserving the Golden Hour).
 *
 * <p>Ranking criteria:
 * <ol>
 *   <li>Estimated travel time (minutes) - fastest arrival prioritized.</li>
 *   <li>Physical distance (km) - shorter travel distance tie-breaker.</li>
 *   <li>Ambulance identifier - deterministic tie-breaker.</li>
 * </ol>
 * </p>
 */
public class FastestResponseRankingPolicy implements AmbulanceRankingPolicy {

    public static final FastestResponseRankingPolicy INSTANCE = new FastestResponseRankingPolicy();

    @Override
    public int compare(AmbulanceCandidate c1, AmbulanceCandidate c2, EmergencyCase emergencyCase) {
        if (c1 == c2) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;

        // 1. Primary: Travel time (faster is better)
        int timeComp = Double.compare(c1.estimatedTravelTimeMinutes(), c2.estimatedTravelTimeMinutes());
        if (timeComp != 0) {
            return timeComp;
        }

        // 2. Secondary: Route distance (shorter is better)
        int distComp = Double.compare(c1.estimatedDistanceKm(), c2.estimatedDistanceKm());
        if (distComp != 0) {
            return distComp;
        }

        // 3. Tertiary: Unique ambulance ID tie-breaker
        return c1.getAmbulanceId().compareTo(c2.getAmbulanceId());
    }

    @Override
    public String getPolicyName() {
        return "FastestResponseRankingPolicy";
    }
}
