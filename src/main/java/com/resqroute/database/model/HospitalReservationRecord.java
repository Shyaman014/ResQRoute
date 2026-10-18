package com.resqroute.database.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Record representing a hospital emergency bed/capacity reservation.
 */
public record HospitalReservationRecord(
        long id,
        String hospitalId,
        String caseId,
        String ambulanceId,
        Instant reservedAt,
        Instant releasedAt,
        String status
) {
    public HospitalReservationRecord {
        Objects.requireNonNull(hospitalId, "hospitalId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        reservedAt = (reservedAt != null) ? reservedAt : Instant.now();
    }
}
