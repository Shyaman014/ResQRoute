package com.resqroute.scheduling;

import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Dynamic dispatch scheduling policy balancing clinical triage severity against waiting time
 * to prevent lower-priority case starvation.
 *
 * <h3>Design &amp; Trade-off Rationale:</h3>
 * <p>
 * In emergency medical response systems, strict static priority scheduling (where {@code CRITICAL}
 * unconditionally preempts all other tiers) creates an acute risk of <b>starvation</b>: under sustained
 * emergency surge conditions (e.g. mass-casualty events, bad weather), {@code MEDIUM} and {@code LOW}
 * priority cases can wait indefinitely without receiving an ambulance.
 * </p>
 * <p>
 * Conversely, pure FIFO or over-aggressive aging creates clinical hazard: an older, minor emergency
 * (e.g. minor contusion) should not preempt an incoming acute cardiac arrest or trauma incident.
 * </p>
 * <p>
 * This policy resolves the trade-off by combining:
 * <ol>
 *   <li><b>Baseline Severity Weight:</b> Substantial step intervals between severity tiers
 *       (CRITICAL=1000, HIGH=500, MEDIUM=250, LOW=100) ensuring that fresh emergencies are dispatched
 *       according to strict clinical urgency.</li>
 *   <li><b>Linear Aging Acceleration:</b> A configurable point accrual rate per minute of waiting time
 *       (default: 10.0 pts/min) steadily promotes older cases.</li>
 *   <li><b>Bounded Aging Ceiling:</b> An optional maximum boost cap prevents non-urgent cases from
 *       unboundedly escalating beyond clinical sanity thresholds.</li>
 *   <li><b>Deterministic Tie-breaking:</b> Identical priority scores are broken strictly by creation
 *       timestamp (FIFO), followed by unique case identifier for total determinism.</li>
 * </ol>
 * </p>
 */
public class AgingDispatchPolicy implements EmergencySchedulingPolicy {

    public static final double DEFAULT_AGING_RATE_PER_MINUTE = 10.0;
    public static final double DEFAULT_MAX_AGING_BOOST = 2000.0;

    private static final Map<EmergencySeverity, Double> DEFAULT_BASE_WEIGHTS = new EnumMap<>(EmergencySeverity.class);

    static {
        DEFAULT_BASE_WEIGHTS.put(EmergencySeverity.CRITICAL, 1000.0);
        DEFAULT_BASE_WEIGHTS.put(EmergencySeverity.HIGH, 500.0);
        DEFAULT_BASE_WEIGHTS.put(EmergencySeverity.MEDIUM, 250.0);
        DEFAULT_BASE_WEIGHTS.put(EmergencySeverity.LOW, 100.0);
    }

    private final Map<EmergencySeverity, Double> baseWeights;
    private final double agingRatePerMinute;
    private final double maxAgingBoost;

    /**
     * Constructs an AgingDispatchPolicy with custom weights, aging rate, and boost ceiling.
     *
     * @param baseWeights        Map defining base priority weights per severity; must not be null.
     * @param agingRatePerMinute Points earned per minute waiting; must be non-negative and finite.
     * @param maxAgingBoost      Maximum allowable aging bonus points; must be non-negative.
     */
    public AgingDispatchPolicy(Map<EmergencySeverity, Double> baseWeights,
                               double agingRatePerMinute,
                               double maxAgingBoost) {
        Objects.requireNonNull(baseWeights, "Base weights map must not be null");
        if (Double.isNaN(agingRatePerMinute) || agingRatePerMinute < 0.0 || Double.isInfinite(agingRatePerMinute)) {
            throw new IllegalArgumentException("Aging rate per minute must be a non-negative finite number: " + agingRatePerMinute);
        }
        if (Double.isNaN(maxAgingBoost) || maxAgingBoost < 0.0) {
            throw new IllegalArgumentException("Max aging boost must be non-negative: " + maxAgingBoost);
        }

        this.baseWeights = new EnumMap<>(EmergencySeverity.class);
        for (EmergencySeverity s : EmergencySeverity.values()) {
            Double weight = baseWeights.get(s);
            if (weight == null || Double.isNaN(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Missing or invalid base weight for severity " + s);
            }
            this.baseWeights.put(s, weight);
        }

        this.agingRatePerMinute = agingRatePerMinute;
        this.maxAgingBoost = maxAgingBoost;
    }

    /**
     * Constructs an AgingDispatchPolicy with default severity weights and specified aging rate.
     */
    public AgingDispatchPolicy(double agingRatePerMinute) {
        this(DEFAULT_BASE_WEIGHTS, agingRatePerMinute, DEFAULT_MAX_AGING_BOOST);
    }

    /**
     * Constructs an AgingDispatchPolicy with default baseline weights and default aging parameters.
     */
    public AgingDispatchPolicy() {
        this(DEFAULT_BASE_WEIGHTS, DEFAULT_AGING_RATE_PER_MINUTE, DEFAULT_MAX_AGING_BOOST);
    }

    /**
     * Factory method creating a strict severity policy with zero waiting-time aging (pure triage priority).
     */
    public static AgingDispatchPolicy strictSeverity() {
        return new AgingDispatchPolicy(DEFAULT_BASE_WEIGHTS, 0.0, 0.0);
    }

    @Override
    public double calculatePriorityScore(EmergencyCase emergencyCase, Instant evaluationTime) {
        Objects.requireNonNull(emergencyCase, "Emergency case must not be null");
        Objects.requireNonNull(evaluationTime, "Evaluation time must not be null");

        double baseWeight = baseWeights.getOrDefault(emergencyCase.getSeverity(), 0.0);
        if (agingRatePerMinute <= 0.0) {
            return baseWeight;
        }

        Instant created = emergencyCase.getCreatedAt();
        long elapsedMillis = Duration.between(created, evaluationTime).toMillis();
        double elapsedMinutes = Math.max(0.0, elapsedMillis / 60000.0);

        double rawBoost = elapsedMinutes * agingRatePerMinute;
        double agingBoost = Math.min(maxAgingBoost, rawBoost);

        return baseWeight + agingBoost;
    }

    @Override
    public int compare(EmergencyCase c1, EmergencyCase c2, Instant evaluationTime) {
        if (c1 == c2) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;

        double score1 = calculatePriorityScore(c1, evaluationTime);
        double score2 = calculatePriorityScore(c2, evaluationTime);

        // 1. Primary ordering: higher dynamic priority score first (descending)
        int scoreComp = Double.compare(score2, score1);
        if (scoreComp != 0) {
            return scoreComp;
        }

        // 2. Secondary tie-breaker: FIFO by creation timestamp (earlier first)
        int timeComp = c1.getCreatedAt().compareTo(c2.getCreatedAt());
        if (timeComp != 0) {
            return timeComp;
        }

        // 3. Tertiary tie-breaker: deterministic case ID order
        return c1.getId().compareTo(c2.getId());
    }

    @Override
    public String getPolicyName() {
        return "AgingDispatchPolicy[rate=" + agingRatePerMinute + "pts/min, maxBoost=" + maxAgingBoost + "]";
    }

    @Override
    public double getAgingRatePerMinute() {
        return agingRatePerMinute;
    }

    public double getMaxAgingBoost() {
        return maxAgingBoost;
    }

    public double getBaseWeight(EmergencySeverity severity) {
        return baseWeights.getOrDefault(severity, 0.0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<EmergencySeverity, Double> weights = new EnumMap<>(DEFAULT_BASE_WEIGHTS);
        private double agingRatePerMinute = DEFAULT_AGING_RATE_PER_MINUTE;
        private double maxAgingBoost = DEFAULT_MAX_AGING_BOOST;

        public Builder baseWeight(EmergencySeverity severity, double weight) {
            weights.put(severity, weight);
            return this;
        }

        public Builder agingRatePerMinute(double rate) {
            this.agingRatePerMinute = rate;
            return this;
        }

        public Builder maxAgingBoost(double maxBoost) {
            this.maxAgingBoost = maxBoost;
            return this;
        }

        public AgingDispatchPolicy build() {
            return new AgingDispatchPolicy(weights, agingRatePerMinute, maxAgingBoost);
        }
    }
}
