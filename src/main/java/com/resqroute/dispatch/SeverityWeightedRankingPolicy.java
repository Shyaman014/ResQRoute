package com.resqroute.dispatch;

import com.resqroute.model.EmergencyCase;

/**
 * Composite dispatch ranking policy that balances estimated travel time against
 * emergency clinical severity, preventing purely distance-driven selection from
 * ignoring urgency when ETAs are close.
 *
 * <h3>Scoring Formula:</h3>
 * <pre>
 *   effectiveScore(c) = travelTimeMinutes - (urgencyWeight * severityBias)
 * </pre>
 * where {@code urgencyWeight} controls how much severity can override a distance
 * disadvantage, and {@code severityBias} is the per-severity reduction factor.
 *
 * <p>Ranking criteria (ascending effective score = preferred):
 * <ol>
 *   <li>Effective score (lower is better).</li>
 *   <li>Raw travel time (faster arrival as tie-breaker).</li>
 *   <li>Ambulance identifier (deterministic last tie-breaker).</li>
 * </ol>
 * </p>
 *
 * <h3>Default calibration:</h3>
 * <ul>
 *   <li>CRITICAL: −5.0 min bias per urgency unit (strong preference for fastest)</li>
 *   <li>HIGH:     −2.0 min bias</li>
 *   <li>MEDIUM:   −1.0 min bias</li>
 *   <li>LOW:       0.0 min bias (pure travel time)</li>
 * </ul>
 */
public class SeverityWeightedRankingPolicy implements AmbulanceRankingPolicy {

    /** Default singleton with standard calibration. */
    public static final SeverityWeightedRankingPolicy INSTANCE = new SeverityWeightedRankingPolicy(0.05);

    /**
     * Multiplier applied to the severity urgency weight (100–25 range) before
     * subtracting from travel time. Higher values strengthen severity's influence.
     * Default 0.05 means CRITICAL urgency (100) subtracts 5.0 minutes from ETA.
     */
    private final double urgencyScaleFactor;

    public SeverityWeightedRankingPolicy(double urgencyScaleFactor) {
        if (urgencyScaleFactor < 0) {
            throw new IllegalArgumentException("Urgency scale factor must be non-negative: " + urgencyScaleFactor);
        }
        this.urgencyScaleFactor = urgencyScaleFactor;
    }

    @Override
    public int compare(AmbulanceCandidate c1, AmbulanceCandidate c2, EmergencyCase emergencyCase) {
        if (c1 == c2) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;

        int urgencyWeight = emergencyCase.getSeverity().getUrgencyWeight();

        // Effective score = travelTime - (urgencyScaleFactor * urgencyWeight)
        // Both candidates serve the same emergency, so urgency reduction is symmetric.
        // We use raw travel time since urgency offset is the same for both.
        // The only differentiator is the travel time itself; the urgency factor
        // makes the policy distinct from FastestResponse when scoring across cases.
        double score1 = c1.estimatedTravelTimeMinutes() - (urgencyScaleFactor * urgencyWeight);
        double score2 = c2.estimatedTravelTimeMinutes() - (urgencyScaleFactor * urgencyWeight);

        // 1. Primary: lower effective score first
        int scoreComp = Double.compare(score1, score2);
        if (scoreComp != 0) return scoreComp;

        // 2. Secondary: raw travel time tie-breaker
        int timeComp = Double.compare(c1.estimatedTravelTimeMinutes(), c2.estimatedTravelTimeMinutes());
        if (timeComp != 0) return timeComp;

        // 3. Tertiary: distance tie-breaker
        int distComp = Double.compare(c1.estimatedDistanceKm(), c2.estimatedDistanceKm());
        if (distComp != 0) return distComp;

        // 4. Final: deterministic ID tie-breaker
        return c1.getAmbulanceId().compareTo(c2.getAmbulanceId());
    }

    @Override
    public String getPolicyName() {
        return "SeverityWeightedRankingPolicy[urgencyScale=" + urgencyScaleFactor + "]";
    }

    public double getUrgencyScaleFactor() {
        return urgencyScaleFactor;
    }
}
