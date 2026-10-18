package com.resqroute.service;

import com.resqroute.exception.DuplicateQueueEntryException;
import com.resqroute.exception.EmergencyCaseNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
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

@DisplayName("Phase 10: Emergency Dispatch Queue Service Orchestration Tests")
class EmergencyDispatchQueueServiceTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;
    private MutableTestClock testClock;
    private EmergencyManagementService emergencyService;
    private EmergencyDispatchQueueService queueService;

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

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        graph.addNode(new Node("HQ", 12.9716, 77.5946, "Metro Dispatch"));
        graph.addNode(new Node("INT-A", 12.9730, 77.5980, "Crossing A"));
        graph.addNode(new Node("INT-B", 12.9760, 77.6010, "Intersection B"));
        graph.addNode(new Node("HOSP-01", 12.9780, 77.6200, "Apex Trauma Hospital"));

        testClock = new MutableTestClock(T0);
        fleetService = new AmbulanceFleetService(graph);
        emergencyService = new EmergencyManagementService(graph, fleetService, testClock);

        // Aging policy with 10 pts/minute
        queueService = new EmergencyDispatchQueueService(emergencyService, new AgingDispatchPolicy(10.0), testClock);

        fleetService.registerAmbulance("AMB-01", "HQ");
    }

    @Test
    @DisplayName("Empty queue operations return empty safely")
    void testEmptyQueue() {
        assertTrue(queueService.isEmpty());
        assertEquals(0, queueService.size());
        assertEquals(Optional.empty(), queueService.peekNextEmergency());
        assertEquals(Optional.empty(), queueService.dequeueNextEmergency());
        assertTrue(queueService.getPendingEmergencies().isEmpty());
    }

    @Test
    @DisplayName("Enqueuing a REPORTED case transitions status to WAITING_FOR_DISPATCH")
    void testEnqueueTransitionsStatusToWaitingForDispatch() {
        EmergencyCase created = emergencyService.createCase(
                "CASE-1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Heart attack");
        assertEquals(EmergencyStatus.REPORTED, created.getStatus());

        EmergencyCase enqueued = queueService.enqueue("CASE-1");
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, enqueued.getStatus());
        assertEquals(1, queueService.size());
        assertFalse(queueService.isEmpty());

        // Also updated in the management service
        EmergencyCase retrieved = emergencyService.getCaseOrThrow("CASE-1");
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, retrieved.getStatus());
    }

    @Test
    @DisplayName("Enqueuing a case already in WAITING_FOR_DISPATCH succeeds directly")
    void testEnqueueAlreadyWaitingCase() {
        EmergencyCase created = emergencyService.createCase(
                "CASE-2", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Laceration");
        emergencyService.updateCaseStatus("CASE-2", EmergencyStatus.WAITING_FOR_DISPATCH);

        EmergencyCase enqueued = queueService.enqueue("CASE-2");
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, enqueued.getStatus());
        assertEquals(1, queueService.size());
    }

    @Test
    @DisplayName("Rejects enqueuing terminal or already dispatched cases")
    void testRejectIneligibleEnqueue() {
        EmergencyCase created = emergencyService.createCase(
                "CASE-TERM", EmergencyType.OTHER, EmergencySeverity.LOW, "INT-A", "Minor");
        emergencyService.cancelCase("CASE-TERM", "Cancelled");

        // Terminal case cannot be enqueued
        assertThrows(IllegalStateException.class, () ->
                queueService.enqueue("CASE-TERM"));

        // Case with ambulance assigned cannot be enqueued
        EmergencyCase active = emergencyService.createCase(
                "CASE-DISPATCHED", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Arrest");
        emergencyService.assignAmbulance("CASE-DISPATCHED", "AMB-01");
        assertThrows(IllegalStateException.class, () ->
                queueService.enqueue("CASE-DISPATCHED"));
    }

    @Test
    @DisplayName("Rejects duplicate active queue entries")
    void testRejectDuplicateEnqueue() {
        emergencyService.createCase("CASE-DUP", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", "Fever");
        queueService.enqueue("CASE-DUP");

        assertThrows(DuplicateQueueEntryException.class, () ->
                queueService.enqueue("CASE-DUP"));

        assertEquals(1, queueService.size());
    }

    @Test
    @DisplayName("Severity ordering and tie-breaking by creation timestamp and case ID")
    void testSeverityOrderingAndTieBreaking() {
        // Cases created simultaneously at T0
        emergencyService.createCase("CASE-LOW", EmergencyType.OTHER, EmergencySeverity.LOW, "INT-A", "Low");
        emergencyService.createCase("CASE-MED", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", "Med");
        emergencyService.createCase("CASE-HIGH", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "High");
        emergencyService.createCase("CASE-CRIT", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Crit");

        queueService.enqueue("CASE-LOW");
        queueService.enqueue("CASE-MED");
        queueService.enqueue("CASE-HIGH");
        queueService.enqueue("CASE-CRIT");

        assertEquals(4, queueService.size());

        // Dequeues in strict clinical severity order: CRITICAL -> HIGH -> MEDIUM -> LOW
        assertEquals("CASE-CRIT", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertEquals("CASE-HIGH", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertEquals("CASE-MED", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertEquals("CASE-LOW", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertTrue(queueService.isEmpty());
    }

    @Test
    @DisplayName("Waiting-time aging promotes older cases past newly arrived higher-severity cases")
    void testWaitingTimeAgingEffect() {
        // T0: Emergency Medium arrives (base score: 250)
        emergencyService.createCase("CASE-WAITING-MED", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", "Moderate pain");
        queueService.enqueue("CASE-WAITING-MED");

        // Advance time by 30 minutes (30 * 10 = +300 pts -> total score: 550)
        testClock.advanceSeconds(1800);

        // T0 + 30 min: Fresh High case arrives (base score: 500)
        emergencyService.createCase("CASE-FRESH-HIGH", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Compound fracture");
        queueService.enqueue("CASE-FRESH-HIGH");

        // Because CASE-WAITING-MED has waited 30 minutes, its dynamic score (550) > CASE-FRESH-HIGH (500)
        assertEquals("CASE-WAITING-MED", queueService.peekNextEmergency().orElseThrow().getId());
        assertEquals("CASE-WAITING-MED", queueService.dequeueNextEmergency().orElseThrow().getId());

        // Next is the fresh high
        assertEquals("CASE-FRESH-HIGH", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertTrue(queueService.isEmpty());
    }

    @Test
    @DisplayName("Priority update adjusts queue ordering safely")
    void testPriorityUpdateAdjustsQueue() {
        emergencyService.createCase("C-LOW", EmergencyType.OTHER, EmergencySeverity.LOW, "INT-A", "Minor");
        emergencyService.createCase("C-HIGH", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Urgent");

        queueService.enqueue("C-LOW");
        queueService.enqueue("C-HIGH");

        // Initially, C-HIGH is ahead of C-LOW
        assertEquals("C-HIGH", queueService.peekNextEmergency().orElseThrow().getId());

        // Escalate C-LOW to CRITICAL
        EmergencyCase updated = queueService.updateSeverity("C-LOW", EmergencySeverity.CRITICAL);
        assertEquals(EmergencySeverity.CRITICAL, updated.getSeverity());

        // C-LOW is now at the head of the queue
        assertEquals("C-LOW", queueService.peekNextEmergency().orElseThrow().getId());
        assertEquals("C-LOW", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertEquals("C-HIGH", queueService.dequeueNextEmergency().orElseThrow().getId());
    }

    @Test
    @DisplayName("Updating severity of non-queued case throws EmergencyCaseNotFoundException")
    void testUpdateSeverityNonQueuedThrows() {
        assertThrows(EmergencyCaseNotFoundException.class, () ->
                queueService.updateSeverity("CASE-NOT-IN-QUEUE", EmergencySeverity.HIGH));
    }

    @Test
    @DisplayName("Cancelling a queued emergency removes it and updates status to CANCELLED")
    void testCancelQueuedEmergency() {
        emergencyService.createCase("C-CANCEL", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "INT-A", "Spasm");
        queueService.enqueue("C-CANCEL");
        assertEquals(1, queueService.size());

        EmergencyCase cancelled = queueService.cancelQueuedEmergency("C-CANCEL", "Caller stood down");
        assertEquals(EmergencyStatus.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.isCancelled());

        // Removed from queue
        assertEquals(0, queueService.size());
        assertTrue(queueService.isEmpty());
        assertFalse(queueService.containsCase("C-CANCEL"));
    }

    @Test
    @DisplayName("Cases cancelled or completed externally are lazily skipped and purged from queue")
    void testExternalCancellationAndCompletionSkipped() {
        emergencyService.createCase("C-EXT-CANCEL", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Crit 1");
        emergencyService.createCase("C-VALID", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "High 1");

        queueService.enqueue("C-EXT-CANCEL");
        queueService.enqueue("C-VALID");

        assertEquals(2, queueService.size());

        // Externally cancel C-EXT-CANCEL via emergencyService
        emergencyService.cancelCase("C-EXT-CANCEL", "Direct dispatch cancellation");

        // When queue peeks/polls, the cancelled case is automatically skipped and purged
        Optional<EmergencyCase> next = queueService.peekNextEmergency();
        assertTrue(next.isPresent());
        assertEquals("C-VALID", next.get().getId());
        assertEquals(1, queueService.size());

        assertEquals("C-VALID", queueService.dequeueNextEmergency().orElseThrow().getId());
        assertTrue(queueService.isEmpty());
    }

    @Test
    @DisplayName("Integration with emergency lifecycle: Dequeued case assigned ambulance exits queue eligibility")
    void testLifecycleAmbulanceAssignmentIntegration() {
        emergencyService.createCase("CASE-DISPATCH", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Cardiac event");
        queueService.enqueue("CASE-DISPATCH");

        // Controller retrieves highest priority case
        EmergencyCase toDispatch = queueService.dequeueNextEmergency().orElseThrow();
        assertEquals("CASE-DISPATCH", toDispatch.getId());

        // Allocate ambulance AMB-01 (without automatic optimization, manual dispatch)
        EmergencyCase assignedCase = emergencyService.assignAmbulance("CASE-DISPATCH", "AMB-01");
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, assignedCase.getStatus());
        assertEquals(Optional.of("AMB-01"), assignedCase.getAssignedAmbulanceId());

        // Ambulance is ASSIGNED
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());

        // Queue is empty
        assertTrue(queueService.isEmpty());
    }

    @Test
    @DisplayName("Queue consistency preserved after repeated updates, removals, and re-queuing")
    void testRepeatedUpdatesAndRequeuing() {
        emergencyService.createCase("C1", EmergencyType.MEDICAL, EmergencySeverity.LOW, "INT-A", "C1");
        emergencyService.createCase("C2", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", "C2");

        queueService.enqueue("C1");
        queueService.enqueue("C2");

        // Multiple severity updates
        queueService.updateSeverity("C1", EmergencySeverity.HIGH);
        queueService.updateSeverity("C1", EmergencySeverity.CRITICAL);
        assertEquals("C1", queueService.peekNextEmergency().orElseThrow().getId());

        queueService.updateSeverity("C2", EmergencySeverity.CRITICAL);
        // Both CRITICAL at T0: tie-breaker is case ID ("C1" < "C2")
        assertEquals("C1", queueService.peekNextEmergency().orElseThrow().getId());

        // Remove C1 and re-enqueue
        queueService.remove("C1");
        assertEquals("C2", queueService.peekNextEmergency().orElseThrow().getId());

        // Re-enqueue C1 (both C1 and C2 are CRITICAL, so tie-breaker ID 'C1' < 'C2' places C1 at head)
        queueService.enqueue("C1");
        assertEquals("C1", queueService.peekNextEmergency().orElseThrow().getId());

        List<EmergencyCase> pending = queueService.getPendingEmergencies();
        assertEquals(2, pending.size());
    }
}
