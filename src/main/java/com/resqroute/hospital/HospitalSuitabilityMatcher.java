package com.resqroute.hospital;

import com.resqroute.model.EmergencyCase;
import com.resqroute.model.Hospital;

/**
 * Strategy interface evaluating whether an acute care hospital is medically and operationally
 * suitable to receive a specific emergency case.
 */
public interface HospitalSuitabilityMatcher {

    /**
     * Checks if the hospital possesses the clinical capabilities and specialty care units
     * demanded by the emergency's medical condition and severity.
     *
     * @param hospital      The candidate hospital.
     * @param emergencyCase The incoming emergency case.
     * @return {@code true} if the hospital is medically compatible.
     */
    boolean isMedicallyCompatible(Hospital hospital, EmergencyCase emergencyCase);

    /**
     * Checks if the hospital is comprehensively suitable: medically compatible, operationally OPEN,
     * and possessing available admission capacity.
     *
     * @param hospital      The candidate hospital.
     * @param emergencyCase The incoming emergency case.
     * @return {@code true} if the hospital is ready and suitable for admission.
     */
    boolean isSuitable(Hospital hospital, EmergencyCase emergencyCase);
}
