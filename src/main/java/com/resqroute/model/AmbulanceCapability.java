package com.resqroute.model;

/**
 * Operational capabilities and clinical care tiers supported by emergency response ambulances.
 */
public enum AmbulanceCapability {

    /**
     * Basic Life Support (BLS): first aid, oxygen administration, basic splinting, patient transport.
     */
    BASIC_LIFE_SUPPORT,

    /**
     * Advanced Life Support (ALS): advanced airway management, IV therapy, pharmacology, paramedic crew.
     */
    ADVANCED_LIFE_SUPPORT,

    /**
     * Cardiac Care: 12-lead ECG telemetry, defibrillation, pacing, acute cardiac monitoring.
     */
    CARDIAC_CARE,

    /**
     * Neonatal & Pediatric Care: specialized incubators, pediatric monitoring, neonatal ventilators.
     */
    NEONATAL_CARE,

    /**
     * Heavy Rescue: extrication equipment, hydraulic cutters, rescue gear for structural or vehicular collapse.
     */
    HEAVY_RESCUE
}
