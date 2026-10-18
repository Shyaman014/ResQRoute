package com.resqroute.dispatch;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;

import java.util.Objects;

/**
 * Standard capability matching engine verifying that an ambulance satisfies all clinical capabilities
 * demanded by the emergency case.
 */
public class DefaultCapabilityMatcher implements AmbulanceCapabilityMatcher {

    public static final DefaultCapabilityMatcher INSTANCE = new DefaultCapabilityMatcher();

    @Override
    public boolean isCompatible(Ambulance ambulance, EmergencyCase emergencyCase) {
        Objects.requireNonNull(ambulance, "Ambulance must not be null");
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");

        return ambulance.hasAllCapabilities(emergencyCase.getRequiredCapabilities());
    }
}
