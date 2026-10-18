package com.resqroute.database.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit record of an ambulance mission dispatch assignment.
 */
public record AmbulanceAssignmentRecord(
        long id,
        String ambulanceId,
        String caseId,
        Instant assignedAt,
        Instant completedAt,
        String status
) {
    public AmbulanceAssignmentRecord {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        assignedAt = (assignedAt != null) ? assignedAt : Instant.now();
    }
}
