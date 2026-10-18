package com.resqroute.hospital;

import com.resqroute.model.EmergencyCase;

/**
 * Strategy interface for ranking eligible hospital candidates based on clinical urgency,
 * travel time, capacity availability, and hospital capabilities.
 */
public interface HospitalRankingPolicy {

    /**
     * Compares two eligible hospital recommendations.
     *
     * @param r1            First hospital recommendation.
     * @param r2            Second hospital recommendation.
     * @param emergencyCase The emergency case being dispatched.
     * @return Negative if r1 is ranked higher, positive if r2 is ranked higher, 0 if equal.
     */
    int compare(HospitalRecommendation r1, HospitalRecommendation r2, EmergencyCase emergencyCase);
}
