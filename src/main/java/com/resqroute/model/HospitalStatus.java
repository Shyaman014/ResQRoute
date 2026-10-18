package com.resqroute.model;

/**
 * Operational intake status of an emergency hospital.
 *
 * <p>Invariant rules:
 * <ul>
 *   <li>{@link #OPEN}: Hospital is accepting new emergency admissions (available capacity &gt; 0).</li>
 *   <li>{@link #BUSY}: Hospital has saturated available capacity (available capacity == 0).</li>
 *   <li>{@link #CLOSED}: Hospital is administratively closed to emergency intake regardless of remaining capacity.</li>
 * </ul>
 * </p>
 */
public enum HospitalStatus {

    /**
     * Hospital is fully operational and has available capacity for incoming emergency admissions.
     */
    OPEN,

    /**
     * Hospital is operational but currently has zero available capacity for new admissions.
     */
    BUSY,

    /**
     * Hospital is temporarily closed for emergency intake due to administrative diversion,
     * structural issues, or disaster lockdown.
     */
    CLOSED;

    /**
     * Indicates whether this status permits receiving new emergency admissions.
     */
    public boolean canAcceptAdmissions() {
        return this == OPEN;
    }
}
