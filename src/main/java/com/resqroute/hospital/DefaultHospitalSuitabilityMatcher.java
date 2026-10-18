package com.resqroute.hospital;

import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyType;
import com.resqroute.model.Hospital;
import com.resqroute.model.HospitalCapability;

import java.util.*;

/**
 * Default implementation of {@link HospitalSuitabilityMatcher} enforcing evidence-based clinical
 * triage rules matching emergency cases to hospital departments and capabilities.
 */
public class DefaultHospitalSuitabilityMatcher implements HospitalSuitabilityMatcher {

    public static final DefaultHospitalSuitabilityMatcher INSTANCE = new DefaultHospitalSuitabilityMatcher();

    private final Map<EmergencyType, Set<HospitalCapability>> typeRequirements;
    private final Map<EmergencySeverity, Set<HospitalCapability>> severityRequirements;

    public DefaultHospitalSuitabilityMatcher() {
        this.typeRequirements = new EnumMap<>(EmergencyType.class);
        this.severityRequirements = new EnumMap<>(EmergencySeverity.class);

        // Standard clinical defaults
        typeRequirements.put(EmergencyType.CARDIAC, EnumSet.of(HospitalCapability.CARDIAC_CATH_LAB));
        typeRequirements.put(EmergencyType.MEDICAL, EnumSet.of(HospitalCapability.EMERGENCY_ROOM));
        typeRequirements.put(EmergencyType.OTHER, EnumSet.of(HospitalCapability.EMERGENCY_ROOM));

        // Critical and High Severity requirements
        severityRequirements.put(EmergencySeverity.CRITICAL, EnumSet.of(HospitalCapability.ICU));
    }

    /**
     * Constructs a configurable matcher with tailored clinical capability rules.
     */
    public DefaultHospitalSuitabilityMatcher(Map<EmergencyType, Set<HospitalCapability>> typeRequirements,
                                            Map<EmergencySeverity, Set<HospitalCapability>> severityRequirements) {
        this.typeRequirements = new EnumMap<>(typeRequirements);
        this.severityRequirements = new EnumMap<>(severityRequirements);
    }

    @Override
    public boolean isMedicallyCompatible(Hospital hospital, EmergencyCase emergencyCase) {
        Objects.requireNonNull(hospital, "Hospital must not be null");
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");

        // 1. Check supported emergency type
        if (!hospital.supportsEmergencyType(emergencyCase.getType())) {
            return false;
        }

        // 2. Base emergency room capability
        if (!hospital.hasCapability(HospitalCapability.EMERGENCY_ROOM)) {
            return false;
        }

        // 3. Triage severity: CRITICAL cases mandate ICU capabilities
        if (emergencyCase.getSeverity() == EmergencySeverity.CRITICAL) {
            if (!hospital.hasCapability(HospitalCapability.ICU)) {
                return false;
            }
        }

        // 4. Severe trauma cases mandate a certified TRAUMA_CENTER
        if (emergencyCase.getType() == EmergencyType.TRAUMA) {
            if (emergencyCase.getSeverity() == EmergencySeverity.CRITICAL
                    || emergencyCase.getSeverity() == EmergencySeverity.HIGH) {
                if (!hospital.hasCapability(HospitalCapability.TRAUMA_CENTER)) {
                    return false;
                }
            }
        }

        // 5. Cardiac cases mandate CARDIAC_CATH_LAB
        if (emergencyCase.getType() == EmergencyType.CARDIAC) {
            if (!hospital.hasCapability(HospitalCapability.CARDIAC_CATH_LAB)) {
                return false;
            }
        }

        // 6. Check custom configured type/severity requirements
        Set<HospitalCapability> reqByType = typeRequirements.get(emergencyCase.getType());
        if (reqByType != null && !hospital.hasAllCapabilities(reqByType)) {
            return false;
        }

        Set<HospitalCapability> reqBySev = severityRequirements.get(emergencyCase.getSeverity());
        if (reqBySev != null && !hospital.hasAllCapabilities(reqBySev)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isSuitable(Hospital hospital, EmergencyCase emergencyCase) {
        return isMedicallyCompatible(hospital, emergencyCase) && hospital.isAvailableForAdmission();
    }
}
