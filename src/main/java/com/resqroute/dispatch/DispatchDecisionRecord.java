package com.resqroute.dispatch;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.RouteResult;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, structured audit record documenting a single ambulance dispatch decision.
 *
 * <p>Captures the full decision rationale including which ambulances were evaluated,
 * why the winner was preferred, what the estimated arrival time is, and which policy
 * was active at dispatch time. Used for dashboard display, audit logs, and test assertions.</p>
 */
public final class DispatchDecisionRecord {

    private final EmergencyCase emergencyCase;
    private final Ambulance selectedAmbulance;
    private final RouteResult selectedRoute;
    private final double estimatedTravelTimeMinutes;
    private final double estimatedDistanceKm;
    private final int candidatesEvaluated;
    private final int candidatesFiltered;
    private final String rankingPolicyName;
    private final String rationale;
    private final Instant decidedAt;

    public DispatchDecisionRecord(EmergencyCase emergencyCase,
                                  Ambulance selectedAmbulance,
                                  RouteResult selectedRoute,
                                  int candidatesEvaluated,
                                  int candidatesFiltered,
                                  String rankingPolicyName,
                                  String rationale,
                                  Instant decidedAt) {
        this.emergencyCase = Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        this.selectedAmbulance = Objects.requireNonNull(selectedAmbulance, "Selected ambulance must not be null");
        this.selectedRoute = Objects.requireNonNull(selectedRoute, "Selected route must not be null");
        this.estimatedTravelTimeMinutes = selectedRoute.getTotalTravelTimeMinutes();
        this.estimatedDistanceKm = selectedRoute.getTotalDistanceKm();
        this.candidatesEvaluated = candidatesEvaluated;
        this.candidatesFiltered = candidatesFiltered;
        this.rankingPolicyName = Objects.requireNonNull(rankingPolicyName, "Ranking policy name must not be null");
        this.rationale = rationale != null ? rationale : "";
        this.decidedAt = Objects.requireNonNull(decidedAt, "Decision timestamp must not be null");
    }

    /**
     * Builds a human-readable summary of the dispatch decision for UI display and audit logs.
     */
    public String buildSummary() {
        return String.format(
                "Dispatched '%s' → '%s' | ETA: %.1f min | Dist: %.1f km | " +
                "Candidates: %d evaluated / %d eligible | Policy: %s | %s",
                selectedAmbulance.getId(),
                emergencyCase.getId(),
                estimatedTravelTimeMinutes,
                estimatedDistanceKm,
                candidatesEvaluated,
                candidatesEvaluated - candidatesFiltered,
                rankingPolicyName,
                rationale
        );
    }

    public EmergencyCase getEmergencyCase() {
        return emergencyCase;
    }

    public String getCaseId() {
        return emergencyCase.getId();
    }

    public Ambulance getSelectedAmbulance() {
        return selectedAmbulance;
    }

    public String getSelectedAmbulanceId() {
        return selectedAmbulance.getId();
    }

    public RouteResult getSelectedRoute() {
        return selectedRoute;
    }

    public double getEstimatedTravelTimeMinutes() {
        return estimatedTravelTimeMinutes;
    }

    public double getEstimatedDistanceKm() {
        return estimatedDistanceKm;
    }

    public int getCandidatesEvaluated() {
        return candidatesEvaluated;
    }

    public int getCandidatesFiltered() {
        return candidatesFiltered;
    }

    public int getEligibleCandidates() {
        return candidatesEvaluated - candidatesFiltered;
    }

    public String getRankingPolicyName() {
        return rankingPolicyName;
    }

    public String getRationale() {
        return rationale;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    @Override
    public String toString() {
        return "DispatchDecisionRecord[" + buildSummary() + "]";
    }
}
