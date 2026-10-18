package com.resqroute.scheduling;

import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 10: Dispatch Scheduling Policy & Aging Tests")
class AgingDispatchPolicyTest {

    private final Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

    private EmergencyCase createCase(String id, EmergencySeverity severity, Instant createdAt) {
        return new EmergencyCase(id, EmergencyType.MEDICAL, severity, "INT-A", createdAt, "Test description");
    }

    @Test
    @DisplayName("Severity ordering dominates when cases arrive simultaneously (0 waiting time)")
    void testSeverityOrderingAtZeroWaitingTime() {
        AgingDispatchPolicy policy = new AgingDispatchPolicy(10.0);

        EmergencyCase critical = createCase("C1", EmergencySeverity.CRITICAL, t0);
        EmergencyCase high = createCase("C2", EmergencySeverity.HIGH, t0);
        EmergencyCase medium = createCase("C3", EmergencySeverity.MEDIUM, t0);
        EmergencyCase low = createCase("C4", EmergencySeverity.LOW, t0);

        assertEquals(1000.0, policy.calculatePriorityScore(critical, t0));
        assertEquals(500.0, policy.calculatePriorityScore(high, t0));
        assertEquals(250.0, policy.calculatePriorityScore(medium, t0));
        assertEquals(100.0, policy.calculatePriorityScore(low, t0));

        // Comparison contracts (negative means c1 has higher priority than c2)
        assertTrue(policy.compare(critical, high, t0) < 0);
        assertTrue(policy.compare(high, medium, t0) < 0);
        assertTrue(policy.compare(medium, low, t0) < 0);
        assertTrue(policy.compare(low, critical, t0) > 0);
    }

    @Test
    @DisplayName("Waiting time accrues priority points linearly up to ceiling")
    void testWaitingTimeScoreAccrualAndCeiling() {
        // Aging rate: 20 pts/minute, max boost: 600 pts
        AgingDispatchPolicy policy = new AgingDispatchPolicy(Map.of(
                EmergencySeverity.CRITICAL, 1000.0,
                EmergencySeverity.HIGH, 500.0,
                EmergencySeverity.MEDIUM, 250.0,
                EmergencySeverity.LOW, 100.0
        ), 20.0, 600.0);

        EmergencyCase lowCase = createCase("LOW-1", EmergencySeverity.LOW, t0);

        // At T0 (0 min waiting): score = 100.0
        assertEquals(100.0, policy.calculatePriorityScore(lowCase, t0));

        // At T0 + 10 min: score = 100 + (10 * 20) = 300.0
        Instant t10 = t0.plusSeconds(600);
        assertEquals(300.0, policy.calculatePriorityScore(lowCase, t10));

        // At T0 + 30 min: score = 100 + (30 * 20) = 700.0 (boost is 600, hits ceiling)
        Instant t30 = t0.plusSeconds(1800);
        assertEquals(700.0, policy.calculatePriorityScore(lowCase, t30));

        // At T0 + 60 min: still capped at 700.0 (100 base + 600 max boost)
        Instant t60 = t0.plusSeconds(3600);
        assertEquals(700.0, policy.calculatePriorityScore(lowCase, t60));
    }

    @Test
    @DisplayName("Starvation prevention: long-waiting lower-severity case surpasses freshly arrived higher-severity case")
    void testStarvationPreventionAgingEffect() {
        AgingDispatchPolicy policy = new AgingDispatchPolicy(10.0); // 10 pts/min

        // Medium case created at T0 (base score: 250)
        EmergencyCase waitingMedium = createCase("MED-OLD", EmergencySeverity.MEDIUM, t0);

        // High case arrives at T0 + 30 minutes (base score: 500)
        Instant t30 = t0.plusSeconds(1800);
        EmergencyCase freshHigh = createCase("HIGH-NEW", EmergencySeverity.HIGH, t30);

        // At T0 + 30 minutes:
        // waitingMedium score: 250 + (30 * 10) = 550
        // freshHigh score: 500 + (0 * 10) = 500
        assertEquals(550.0, policy.calculatePriorityScore(waitingMedium, t30));
        assertEquals(500.0, policy.calculatePriorityScore(freshHigh, t30));

        // waitingMedium has higher priority than freshHigh (negative result)
        assertTrue(policy.compare(waitingMedium, freshHigh, t30) < 0);
    }

    @Test
    @DisplayName("Deterministic tie-breaking: earlier creation timestamp wins (FIFO)")
    void testTieBreakingByCreationTimestamp() {
        AgingDispatchPolicy policy = new AgingDispatchPolicy(0.0); // 0 aging rate for pure score match

        Instant t1 = t0.plusSeconds(60);
        EmergencyCase earlyCase = createCase("CASE-A", EmergencySeverity.HIGH, t0);
        EmergencyCase lateCase = createCase("CASE-B", EmergencySeverity.HIGH, t1);

        // Identical base scores (500.0 vs 500.0)
        assertEquals(policy.calculatePriorityScore(earlyCase, t1), policy.calculatePriorityScore(lateCase, t1));

        // Earlier created case wins
        assertTrue(policy.compare(earlyCase, lateCase, t1) < 0);
        assertTrue(policy.compare(lateCase, earlyCase, t1) > 0);
    }

    @Test
    @DisplayName("Deterministic tie-breaking: identical score and timestamp broken by case ID")
    void testTieBreakingByCaseId() {
        AgingDispatchPolicy policy = new AgingDispatchPolicy(10.0);

        EmergencyCase caseAlpha = createCase("CASE-ALPHA", EmergencySeverity.HIGH, t0);
        EmergencyCase caseBeta = createCase("CASE-BETA", EmergencySeverity.HIGH, t0);

        // Same score and same timestamp -> "CASE-ALPHA" < "CASE-BETA"
        assertTrue(policy.compare(caseAlpha, caseBeta, t0) < 0);
        assertTrue(policy.compare(caseBeta, caseAlpha, t0) > 0);
        assertEquals(0, policy.compare(caseAlpha, caseAlpha, t0));
    }

    @Test
    @DisplayName("Strict severity policy ignores elapsed time")
    void testStrictSeverityPolicy() {
        AgingDispatchPolicy strict = AgingDispatchPolicy.strictSeverity();
        assertEquals(0.0, strict.getAgingRatePerMinute());

        EmergencyCase lowOld = createCase("LOW-OLD", EmergencySeverity.LOW, t0);
        Instant t5Hours = t0.plusSeconds(18000);
        EmergencyCase highNew = createCase("HIGH-NEW", EmergencySeverity.HIGH, t5Hours);

        // Even after 5 hours, HIGH still beats LOW under strict policy
        assertTrue(strict.compare(highNew, lowOld, t5Hours) < 0);
        assertEquals(100.0, strict.calculatePriorityScore(lowOld, t5Hours));
        assertEquals(500.0, strict.calculatePriorityScore(highNew, t5Hours));
    }

    @Test
    @DisplayName("Rejects invalid policy arguments")
    void testInvalidPolicyArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new AgingDispatchPolicy(-5.0));

        assertThrows(IllegalArgumentException.class, () ->
                new AgingDispatchPolicy(Map.of(EmergencySeverity.CRITICAL, 100.0), 10.0, 100.0)); // missing other severities

        assertThrows(NullPointerException.class, () ->
                new AgingDispatchPolicy(null, 10.0, 100.0));
    }
}
