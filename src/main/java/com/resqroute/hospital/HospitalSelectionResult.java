package com.resqroute.hospital;

import com.resqroute.model.EmergencyCase;

import java.time.Instant;
import java.util.*;

/**
 * Immutable outcome container encapsulating hospital recommendation and reservation decisions.
 */
public final class HospitalSelectionResult {

    private final HospitalSelectionStatus status;
    private final EmergencyCase emergencyCase;
    private final HospitalRecommendation selectedHospital;
    private final List<HospitalRecommendation> rankedAlternatives;
    private final Instant timestamp;
    private final String message;

    private HospitalSelectionResult(HospitalSelectionStatus status,
                                    EmergencyCase emergencyCase,
                                    HospitalRecommendation selectedHospital,
                                    List<HospitalRecommendation> rankedAlternatives,
                                    Instant timestamp,
                                    String message) {
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.emergencyCase = emergencyCase;
        this.selectedHospital = selectedHospital;
        this.rankedAlternatives = (rankedAlternatives == null || rankedAlternatives.isEmpty())
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(rankedAlternatives));
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        this.message = (message == null) ? "" : message.trim();
    }

    public static HospitalSelectionResult success(EmergencyCase emergencyCase,
                                                  HospitalRecommendation selected,
                                                  List<HospitalRecommendation> alternatives,
                                                  Instant timestamp) {
        Objects.requireNonNull(selected, "Selected hospital recommendation must not be null");
        return new HospitalSelectionResult(
                HospitalSelectionStatus.SUCCESS,
                emergencyCase,
                selected,
                alternatives,
                timestamp,
                String.format("Successfully selected and reserved hospital '%s' (ETA: %.1f min)",
                        selected.getHospitalName(), selected.getEstimatedTravelTimeMinutes())
        );
    }

    public static HospitalSelectionResult failure(HospitalSelectionStatus status,
                                                  EmergencyCase emergencyCase,
                                                  String reason,
                                                  Instant timestamp) {
        if (status == HospitalSelectionStatus.SUCCESS) {
            throw new IllegalArgumentException("Failure constructor cannot use SUCCESS status");
        }
        return new HospitalSelectionResult(status, emergencyCase, null, List.of(), timestamp, reason);
    }

    public static HospitalSelectionResult failure(HospitalSelectionStatus status,
                                                  EmergencyCase emergencyCase,
                                                  List<HospitalRecommendation> unreservedCandidates,
                                                  String reason,
                                                  Instant timestamp) {
        if (status == HospitalSelectionStatus.SUCCESS) {
            throw new IllegalArgumentException("Failure constructor cannot use SUCCESS status");
        }
        return new HospitalSelectionResult(status, emergencyCase, null, unreservedCandidates, timestamp, reason);
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public HospitalSelectionStatus getStatus() {
        return status;
    }

    public Optional<EmergencyCase> getEmergencyCase() {
        return Optional.ofNullable(emergencyCase);
    }

    public Optional<HospitalRecommendation> getSelectedHospital() {
        return Optional.ofNullable(selectedHospital);
    }

    public List<HospitalRecommendation> getRankedAlternatives() {
        return rankedAlternatives;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("HospitalSelectionResult[status=%s, selected=%s, alternativesCount=%d, msg='%s']",
                status,
                selectedHospital != null ? selectedHospital.getHospitalId() : "none",
                rankedAlternatives.size(),
                message);
    }
}
