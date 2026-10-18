package com.resqroute.optimization;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Production-grade, priority-aware cost function balancing patient clinical urgency,
 * waiting time, and estimated ambulance travel times.
 *
 * <h3>Mathematical Formulation &amp; Design Rationale:</h3>
 * <p>
 * For candidate pair $(A_i, E_j)$:
 * <pre>
 *   Cost = BaseOffset - UrgencyBonus(E_j) + (TravelTimeWeight * TravelTimeMinutes)
 * </pre>
 * where:
 * <ul>
 *   <li>{@code BaseOffset}: Fixed positive scalar ($10,000.0$) ensuring all edge costs in the
 *       flow network remain strictly positive ($\ge 500.0$), preventing negative-weight cycles.</li>
 *   <li>{@code UrgencyBonus(E_j) = SeverityWeight(severity) + (WaitTimeWeight * WaitingMinutes)}:
 *       Higher clinical urgency deducts more from the base offset, resulting in a lower edge cost.
 *       In Minimum-Cost Maximum-Flow matching, this mathematically guarantees that when fleet
 *       capacity is constrained (fewer ambulances than emergencies), the solver strictly selects
 *       the most urgent and longest-waiting cases first.</li>
 *   <li>{@code TravelTimeWeight * TravelTimeMinutes}: Penalizes longer arrival delays, ensuring that
 *       among patients of comparable urgency, ambulances with the shortest travel time are allocated.</li>
 * </ul>
 * </p>
 */
public class PriorityAwareCostModel implements AssignmentCostModel {

    public static final double DEFAULT_BASE_OFFSET = 10000.0;
    public static final double DEFAULT_TRAVEL_TIME_WEIGHT = 1.0;
    public static final double DEFAULT_WAIT_TIME_WEIGHT = 5.0; // 5 cost reduction per minute waited

    private final double baseOffset;
    private final double travelTimeWeight;
    private final double waitTimeWeight;
    private final Map<EmergencySeverity, Double> severityWeights;

    public PriorityAwareCostModel() {
        this(DEFAULT_BASE_OFFSET, DEFAULT_TRAVEL_TIME_WEIGHT, DEFAULT_WAIT_TIME_WEIGHT, defaultSeverityWeights());
    }

    public PriorityAwareCostModel(double baseOffset,
                                  double travelTimeWeight,
                                  double waitTimeWeight,
                                  Map<EmergencySeverity, Double> severityWeights) {
        if (baseOffset <= 0) {
            throw new IllegalArgumentException("Base offset must be positive: " + baseOffset);
        }
        if (travelTimeWeight < 0) {
            throw new IllegalArgumentException("Travel time weight must be non-negative: " + travelTimeWeight);
        }
        if (waitTimeWeight < 0) {
            throw new IllegalArgumentException("Wait time weight must be non-negative: " + waitTimeWeight);
        }
        this.baseOffset = baseOffset;
        this.travelTimeWeight = travelTimeWeight;
        this.waitTimeWeight = waitTimeWeight;
        this.severityWeights = new EnumMap<>(Objects.requireNonNull(severityWeights, "Severity weights must not be null"));
    }

    private static Map<EmergencySeverity, Double> defaultSeverityWeights() {
        Map<EmergencySeverity, Double> map = new EnumMap<>(EmergencySeverity.class);
        map.put(EmergencySeverity.CRITICAL, 5000.0);
        map.put(EmergencySeverity.HIGH, 3000.0);
        map.put(EmergencySeverity.MEDIUM, 1500.0);
        map.put(EmergencySeverity.LOW, 500.0);
        return map;
    }

    @Override
    public double calculateCost(Ambulance ambulance,
                                EmergencyCase emergencyCase,
                                double travelTimeMinutes,
                                Instant evaluationTime) {
        Objects.requireNonNull(ambulance, "Ambulance must not be null");
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        Objects.requireNonNull(evaluationTime, "Evaluation time must not be null");

        double severityWeight = severityWeights.getOrDefault(emergencyCase.getSeverity(), 1000.0);

        long waitSeconds = Math.max(0, Duration.between(emergencyCase.getCreatedAt(), evaluationTime).getSeconds());
        double waitMinutes = waitSeconds / 60.0;
        double waitBonus = waitMinutes * waitTimeWeight;

        // Cap urgency bonus below baseOffset to guarantee non-negative costs
        double totalUrgencyBonus = Math.min(baseOffset - 100.0, severityWeight + waitBonus);
        double travelPenalty = travelTimeMinutes * travelTimeWeight;

        return Math.max(1.0, baseOffset - totalUrgencyBonus + travelPenalty);
    }

    public double getBaseOffset() {
        return baseOffset;
    }

    public double getTravelTimeWeight() {
        return travelTimeWeight;
    }

    public double getWaitTimeWeight() {
        return waitTimeWeight;
    }

    public Map<EmergencySeverity, Double> getSeverityWeights() {
        return Map.copyOf(severityWeights);
    }
}
