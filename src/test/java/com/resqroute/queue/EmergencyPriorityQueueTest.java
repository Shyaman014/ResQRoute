package com.resqroute.queue;

import com.resqroute.exception.DuplicateQueueEntryException;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyType;
import com.resqroute.scheduling.AgingDispatchPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 10: Emergency Priority Queue Tests")
class EmergencyPriorityQueueTest {

    private MutableTestClock testClock;
    private EmergencyPriorityQueue queue;

    private static final Instant T0 = Instant.parse("2026-10-18T10:00:00Z");

    static class MutableTestClock extends Clock {
        private Instant currentInstant;

        MutableTestClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        public void advanceSeconds(long seconds) {
            this.currentInstant = this.currentInstant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    private EmergencyCase createCase(String id, EmergencySeverity severity, Instant createdAt) {
        return new EmergencyCase(id, EmergencyType.MEDICAL, severity, "INT-A", createdAt, "Test case " + id);
    }

    @BeforeEach
    void setUp() {
        testClock = new MutableTestClock(T0);
        queue = new EmergencyPriorityQueue(new AgingDispatchPolicy(10.0), testClock);
    }

    @Test
    @DisplayName("Empty queue operations behave safely")
    void testEmptyQueueBehavior() {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertEquals(Optional.empty(), queue.peek());
        assertEquals(Optional.empty(), queue.poll());
        assertTrue(queue.getPendingEmergencies().isEmpty());
        assertFalse(queue.containsCase("ANY-ID"));
    }

    @Test
    @DisplayName("Basic enqueue, peek, and poll operations preserve severity order")
    void testBasicEnqueuePeekPoll() {
        EmergencyCase low = createCase("C-LOW", EmergencySeverity.LOW, T0);
        EmergencyCase high = createCase("C-HIGH", EmergencySeverity.HIGH, T0);
        EmergencyCase crit = createCase("C-CRIT", EmergencySeverity.CRITICAL, T0);

        queue.enqueue(low);
        queue.enqueue(high);
        queue.enqueue(crit);

        assertEquals(3, queue.size());
        assertFalse(queue.isEmpty());
        assertTrue(queue.containsCase("C-LOW"));
        assertTrue(queue.containsCase("C-HIGH"));
        assertTrue(queue.containsCase("C-CRIT"));

        // Peek should show highest priority (CRITICAL) without removal
        assertEquals(Optional.of(crit), queue.peek());
        assertEquals(3, queue.size());

        // Dequeue in order: CRITICAL -> HIGH -> LOW
        assertEquals(Optional.of(crit), queue.poll());
        assertEquals(2, queue.size());

        assertEquals(Optional.of(high), queue.poll());
        assertEquals(1, queue.size());

        assertEquals(Optional.of(low), queue.poll());
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("Rejects duplicate case IDs in queue")
    void testDuplicatePrevention() {
        EmergencyCase c1 = createCase("CASE-DUP", EmergencySeverity.HIGH, T0);
        queue.enqueue(c1);

        DuplicateQueueEntryException ex = assertThrows(DuplicateQueueEntryException.class, () ->
                queue.enqueue(createCase("CASE-DUP", EmergencySeverity.CRITICAL, T0)));

        assertEquals("CASE-DUP", ex.getCaseId());
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("Safe priority updates adjust queue position without heap corruption")
    void testPriorityUpdateWithoutHeapCorruption() {
        EmergencyCase c1 = createCase("C1", EmergencySeverity.LOW, T0);
        EmergencyCase c2 = createCase("C2", EmergencySeverity.MEDIUM, T0);

        queue.enqueue(c1);
        queue.enqueue(c2);

        // Before update: C2 (MEDIUM) has higher priority than C1 (LOW)
        assertEquals(Optional.of(c2), queue.peek());

        // Escalate C1 from LOW to CRITICAL
        EmergencyCase c1Escalated = c1.withSeverity(EmergencySeverity.CRITICAL);
        boolean updated = queue.update(c1Escalated);
        assertTrue(updated);

        // After update: C1 (now CRITICAL) becomes head of queue
        assertEquals(Optional.of(c1Escalated), queue.peek());
        assertEquals(c1Escalated, queue.poll().orElseThrow());
        assertEquals(c2, queue.poll().orElseThrow());
    }

    @Test
    @DisplayName("Waiting time promotion dynamically updates head of queue")
    void testWaitingTimeDynamicReordering() {
        // C-MED created at T0 (score: 250)
        EmergencyCase cMed = createCase("C-MED", EmergencySeverity.MEDIUM, T0);
        queue.enqueue(cMed);

        // Advance clock by 30 minutes (30 * 10 = +300 pts -> score: 550)
        testClock.advanceSeconds(1800);

        // C-HIGH arrives at T0 + 30 min (score: 500)
        EmergencyCase cHigh = createCase("C-HIGH", EmergencySeverity.HIGH, testClock.instant());
        queue.enqueue(cHigh);

        // Because C-MED has waited 30 minutes, its dynamic score (550) > C-HIGH (500)
        assertEquals(Optional.of(cMed), queue.peek());
        assertEquals(cMed, queue.poll().orElseThrow());
        assertEquals(cHigh, queue.poll().orElseThrow());
    }

    @Test
    @DisplayName("Removal by ID succeeds and maintains heap consistency")
    void testRemoveById() {
        EmergencyCase c1 = createCase("C1", EmergencySeverity.LOW, T0);
        EmergencyCase c2 = createCase("C2", EmergencySeverity.HIGH, T0);
        queue.enqueue(c1);
        queue.enqueue(c2);

        Optional<EmergencyCase> removed = queue.remove("C2");
        assertTrue(removed.isPresent());
        assertEquals(c2, removed.get());
        assertEquals(1, queue.size());
        assertFalse(queue.containsCase("C2"));

        // Remaining element is C1
        assertEquals(Optional.of(c1), queue.peek());

        // Removing non-existent returns empty
        assertTrue(queue.remove("NON-EXISTENT").isEmpty());
    }

    @Test
    @DisplayName("Queue consistency preserved after repeated updates, removals, and re-insertions")
    void testRepeatedUpdatesAndReinsertions() {
        EmergencyCase c1 = createCase("C1", EmergencySeverity.LOW, T0);
        EmergencyCase c2 = createCase("C2", EmergencySeverity.MEDIUM, T0);
        EmergencyCase c3 = createCase("C3", EmergencySeverity.HIGH, T0);

        queue.enqueue(c1);
        queue.enqueue(c2);
        queue.enqueue(c3);

        // Repeated updates
        queue.update(c1.withSeverity(EmergencySeverity.CRITICAL)); // C1 is now top
        assertEquals("C1", queue.peek().orElseThrow().getId());

        queue.update(c2.withSeverity(EmergencySeverity.CRITICAL)); // C2 is now CRITICAL as well
        // Both CRITICAL at T0: tie-breaker is case ID ("C1" < "C2")
        assertEquals("C1", queue.peek().orElseThrow().getId());

        // Remove C1
        queue.remove("C1");
        assertEquals("C2", queue.peek().orElseThrow().getId());

        // Reinsert C1 as LOW
        queue.enqueue(c1);
        assertEquals("C2", queue.peek().orElseThrow().getId());

        // Clear queue
        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("getPendingEmergencies returns unmodifiable list in exact sorted priority order")
    void testGetPendingEmergenciesSorted() {
        EmergencyCase cLow = createCase("C-LOW", EmergencySeverity.LOW, T0);
        EmergencyCase cMed = createCase("C-MED", EmergencySeverity.MEDIUM, T0);
        EmergencyCase cHigh = createCase("C-HIGH", EmergencySeverity.HIGH, T0);

        queue.enqueue(cLow);
        queue.enqueue(cHigh);
        queue.enqueue(cMed);

        List<EmergencyCase> pending = queue.getPendingEmergencies();
        assertEquals(3, pending.size());
        assertEquals("C-HIGH", pending.get(0).getId());
        assertEquals("C-MED", pending.get(1).getId());
        assertEquals("C-LOW", pending.get(2).getId());

        assertThrows(UnsupportedOperationException.class, () ->
                pending.add(createCase("NEW", EmergencySeverity.CRITICAL, T0)));
    }
}
