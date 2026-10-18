package com.resqroute.model;

/**
 * Categorization of emergency medical and rescue situations.
 */
public enum EmergencyType {

    /**
     * General medical emergencies (e.g. respiratory distress, stroke, diabetic emergency).
     */
    MEDICAL,

    /**
     * Physical trauma and injuries (e.g. vehicular accidents, falls, fractures).
     */
    TRAUMA,

    /**
     * Acute cardiac incidents (e.g. cardiac arrest, chest pain, myocardial infarction).
     */
    CARDIAC,

    /**
     * Fire hazard, structural collapse, or specialized rescue operations.
     */
    FIRE_RESCUE,

    /**
     * Unclassified or other urgent emergency response scenarios.
     */
    OTHER;

    /**
     * Returns a human-friendly display label.
     */
    public String displayName() {
        return switch (this) {
            case MEDICAL -> "Medical Emergency";
            case TRAUMA -> "Trauma Incident";
            case CARDIAC -> "Cardiac Emergency";
            case FIRE_RESCUE -> "Fire & Rescue Operation";
            case OTHER -> "Other Emergency";
        };
    }
}
