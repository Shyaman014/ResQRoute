package com.resqroute.model;

/**
 * Specialized medical and clinical capabilities provided by acute care hospitals.
 */
public enum HospitalCapability {

    /**
     * General 24/7 Emergency Department / Emergency Room for acute triage and stabilization.
     */
    EMERGENCY_ROOM,

    /**
     * Level I/II Trauma Center equipped with trauma resuscitation bays and specialized surgical teams.
     */
    TRAUMA_CENTER,

    /**
     * Cardiac Catheterization Lab for acute myocardial infarction, stenting, and angioplasty.
     */
    CARDIAC_CATH_LAB,

    /**
     * Intensive Care Unit (ICU) for high-acuity life support and critical organ monitoring.
     */
    ICU,

    /**
     * Dedicated Pediatric Emergency & Neonatal Intensive Care Department.
     */
    PEDIATRIC_CARE,

    /**
     * Specialized Burn Center for high-degree thermal, chemical, and inhalation injuries.
     */
    BURN_UNIT,

    /**
     * Comprehensive Stroke & Neurosurgical Care Center for acute intracranial hemorrhage and trauma.
     */
    NEUROSURGERY,

    /**
     * Emergency Psychiatric Assessment and Crisis Stabilization Unit.
     */
    PSYCHIATRIC_CRISIS
}
