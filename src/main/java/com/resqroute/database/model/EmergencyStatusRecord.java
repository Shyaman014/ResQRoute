package com.resqroute.database.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit record of an emergency case status transition.
 */
public record EmergencyStatusRecord(
        long id,
        String caseId,
        String status,
        String reason,
        Instant recordedAt
) {
    public EmergencyStatusRecord {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        recordedAt = (recordedAt != null) ? recordedAt : Instant.now();
    }
}
