package com.resqroute.optimization;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;

import java.time.Instant;

/**
 * Strategy interface defining the objective cost function for matching an ambulance
 * to an emergency case in fleet-wide optimization.
 *
 * <p>Lower costs indicate more desirable assignments.</p>
 */
public interface AssignmentCostModel {

    /**
     * Calculates the scalar optimization cost for an ambulance-emergency candidate pair.
     *
     * @param ambulance         Candidate ambulance vehicle.
     * @param emergencyCase     Pending emergency case.
     * @param travelTimeMinutes Estimated traffic-aware travel time from ambulance to pickup.
     * @param evaluationTime    Current evaluation timestamp.
     * @return Non-negative cost scalar.
     */
    double calculateCost(Ambulance ambulance,
                         EmergencyCase emergencyCase,
                         double travelTimeMinutes,
                         Instant evaluationTime);
}
