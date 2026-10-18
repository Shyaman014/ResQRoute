package com.resqroute.hospital;

/**
 * Outcome status codes for hospital selection and reservation requests.
 */
public enum HospitalSelectionStatus {

    /**
     * An optimal hospital destination was successfully identified and capacity reserved.
     */
    SUCCESS,

    /**
     * No hospitals exist in the management network or match basic criteria.
     */
    NO_ELIGIBLE_HOSPITALS,

    /**
     * Registered hospitals exist, but none provide the medical capabilities or department
     * demanded by the emergency condition.
     */
    NO_COMPATIBLE_HOSPITALS,

    /**
     * Compatible hospitals exist, but all are operating at full capacity (BUSY) or are administratively CLOSED.
     */
    ALL_HOSPITALS_FULL_OR_BUSY,

    /**
     * Compatible open hospitals exist, but all road paths are blocked by incidents or network severance.
     */
    ALL_HOSPITALS_UNREACHABLE,

    /**
     * Hospital selection failed during atomic capacity reservation transaction.
     */
    RESERVATION_FAILED,

    /**
     * Emergency case is already terminal, cancelled, or invalid.
     */
    CASE_INELIGIBLE;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
