package com.resqroute.dispatch;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;

/**
 * Strategy interface validating clinical capability compatibility between an ambulance and an emergency case.
 */
@FunctionalInterface
public interface AmbulanceCapabilityMatcher {

    /**
     * Determines whether an ambulance possesses the necessary clinical equipment, crew, and capabilities
     * to safely respond to the specified emergency case.
     *
     * @param ambulance     The candidate ambulance; must not be null.
     * @param emergencyCase The target emergency case; must not be null.
     * @return true if compatible and capable of servicing the emergency, false otherwise.
     */
    boolean isCompatible(Ambulance ambulance, EmergencyCase emergencyCase);
}
