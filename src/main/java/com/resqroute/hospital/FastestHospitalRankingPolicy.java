package com.resqroute.hospital;

import com.resqroute.model.EmergencyCase;

import java.util.Objects;

/**
 * Default ranking policy prioritizing candidate hospitals that offer the minimal
 * traffic-aware arrival time from the incident location.
 *
 * <p>Tie-breaking hierarchy:
 * <ol>
 *   <li>Estimated travel time (minutes, ascending).</li>
 *   <li>Physical distance (km, ascending).</li>
 *   <li>Available bed capacity (descending, prioritizing facilities with higher admission buffer).</li>
 *   <li>Hospital ID (lexicographical, deterministic).</li>
 * </ol>
 * </p>
 */
public class FastestHospitalRankingPolicy implements HospitalRankingPolicy {

    public static final FastestHospitalRankingPolicy INSTANCE = new FastestHospitalRankingPolicy();

    @Override
    public int compare(HospitalRecommendation r1, HospitalRecommendation r2, EmergencyCase emergencyCase) {
        Objects.requireNonNull(r1, "Recommendation 1 must not be null");
        Objects.requireNonNull(r2, "Recommendation 2 must not be null");

        // 1. Primary: Estimated travel time
        int timeCmp = Double.compare(r1.getEstimatedTravelTimeMinutes(), r2.getEstimatedTravelTimeMinutes());
        if (timeCmp != 0) {
            return timeCmp;
        }

        // 2. Secondary: Physical road distance
        int distCmp = Double.compare(r1.getDistanceKm(), r2.getDistanceKm());
        if (distCmp != 0) {
            return distCmp;
        }

        // 3. Tertiary: Available bed buffer (higher available capacity preferred)
        int capCmp = Integer.compare(
                r2.getHospital().getAvailableCapacity(),
                r1.getHospital().getAvailableCapacity()
        );
        if (capCmp != 0) {
            return capCmp;
        }

        // 4. Deterministic tie-breaker: Hospital ID
        return r1.getHospitalId().compareTo(r2.getHospitalId());
    }
}
