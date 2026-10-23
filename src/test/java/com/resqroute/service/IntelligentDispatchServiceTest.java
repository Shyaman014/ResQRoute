package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.dispatch.DispatchDecisionRecord;
import com.resqroute.dispatch.DispatchResult;
import com.resqroute.dispatch.DispatchStatus;
import com.resqroute.dispatch.FastestResponseRankingPolicy;
import com.resqroute.dispatch.SeverityWeightedRankingPolicy;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: IntelligentDispatchService Tests")
class IntelligentDispatchServiceTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;
    private EmergencyManagementService emergencyService;
    private EmergencyDispatchQueueService queueService;
    private AmbulanceDispatchService coreDispatch;
    private IntelligentDispatchService intelligentDispatch;
    private MutableTestClock testClock;

    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

    static class MutableTestClock extends Clock {
        private Instant instant;

        MutableTestClock(Instant initial) {
            this.instant = initial;
        }

        public void advanceMinutes(long mins) {
            this.instant = this.instant.plusSeconds(mins * 60);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();

        // Realistic 4-node network
        Node stNorth = new Node("ST-NORTH", 12.98, 77.59, "North Station");
        Node stSouth = new Node("ST-SOUTH", 12.96, 77.59, "South Station");
        Node sceneA  = new Node("SCENE-A",  12.97, 77.60, "Emergency Scene A");
        Node sceneB  = new Node("SCENE-B",  12.95, 77.61, "Emergency Scene B");
        Node isolated = new Node("ISOLATED", 13.50, 78.00, "Disconnected");

        graph.addNode(stNorth);
        graph.addNode(stSouth);
        graph.addNode(sceneA);
        graph.addNode(sceneB);
        graph.addNode(isolated);

        graph.addEdge("ST-NORTH", "SCENE-A", 2.0, 3.0, "North Ave");
        graph.addEdge("ST-SOUTH", "SCENE-A", 5.0, 8.0, "South Rd");
        graph.addEdge("ST-SOUTH", "SCENE-B", 2.0, 2.5, "South Conn");
        graph.addEdge("ST-NORTH", "SCENE-B", 6.0, 9.0, "North Bypass");

        testClock = new MutableTestClock(T0);
        fleetService = new AmbulanceFleetService(graph);
        emergencyService = new EmergencyManagementService(graph, fleetService, testClock);
        queueService = new EmergencyDispatchQueueService(emergencyService, new AgingDispatchPolicy(10.0), testClock);
        coreDispatch = new AmbulanceDispatchService(
                graph, new DijkstraRoutingAlgorithm(), emergencyService, fleetService, queueService);
        intelligentDispatch = new IntelligentDispatchService(coreDispatch, fleetService, queueService);
    }

    // ==========================================
    // 1. Basic synchronous dispatch
    // ==========================================

    @Test
    @DisplayName("dispatchNext selects fastest eligible ambulance")
    void testDispatchNextSelectsFastest() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Injury");
        queueService.enqueue(ec);

        DispatchResult result = intelligentDispatch.dispatchNext();

        assertTrue(result.isSuccess());
        assertEquals("AMB-N", result.getAssignedAmbulance().orElseThrow().getId());
        assertEquals(3.0, result.getEstimatedTravelTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("dispatchNext returns NO_PENDING_EMERGENCIES when queue is empty")
    void testDispatchNextNoPending() {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        // No emergencies enqueued

        DispatchResult result = intelligentDispatch.dispatchNext();
        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.NO_PENDING_EMERGENCIES, result.getStatus());
    }

    @Test
    @DisplayName("dispatchNext returns NO_AVAILABLE_AMBULANCES when fleet is empty")
    void testDispatchNextNoAmbulances() {
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.MEDICAL, EmergencySeverity.LOW, "SCENE-A", "Test");
        queueService.enqueue(ec);

        DispatchResult result = intelligentDispatch.dispatchNext();
        assertFalse(result.isSuccess());
        assertEquals(DispatchStatus.NO_AVAILABLE_AMBULANCES, result.getStatus());
    }

    @Test
    @DisplayName("dispatchEmergency dispatches a specific case")
    void testDispatchEmergency() {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "SCENE-A", "Head injury");

        DispatchResult result = intelligentDispatch.dispatchEmergency(ec);
        assertTrue(result.isSuccess());
        assertEquals("AMB-1", result.getAssignedAmbulance().orElseThrow().getId());
    }

    @Test
    @DisplayName("dispatchAllPending drains queue in priority order")
    void testDispatchAllPending() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        emergencyService.createCase("C-CRIT", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE-A", "Arrest");
        emergencyService.createCase("C-HIGH", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-B", "Fracture");
        emergencyService.createCase("C-LOW",  EmergencyType.OTHER,  EmergencySeverity.LOW,  "SCENE-A", "Minor");
        queueService.enqueue("C-CRIT");
        queueService.enqueue("C-HIGH");
        queueService.enqueue("C-LOW");

        List<DispatchResult> results = intelligentDispatch.dispatchAllPending();
        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("C-CRIT", results.get(0).getEmergencyCase().orElseThrow().getId());
        assertTrue(results.get(1).isSuccess());
        assertFalse(results.get(2).isSuccess()); // No ambulance left for C-LOW
    }

    // ==========================================
    // 2. Audit trail and rationale
    // ==========================================

    @Test
    @DisplayName("Audit record is populated after successful dispatch")
    void testAuditRecordPopulated() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        queueService.enqueue(ec);

        DispatchResult result = intelligentDispatch.dispatchNext();
        assertTrue(result.isSuccess());

        DispatchDecisionRecord record = intelligentDispatch.getLastDecisionRecord();
        assertNotNull(record);
        assertEquals("C1", record.getCaseId());
        assertEquals("AMB-N", record.getSelectedAmbulanceId());
        assertTrue(record.getEstimatedTravelTimeMinutes() > 0);
        assertFalse(record.getRankingPolicyName().isBlank());
        assertNotNull(record.buildSummary());
    }

    @Test
    @DisplayName("Audit log accumulates records for successive dispatches")
    void testAuditLogAccumulates() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "T1");
        emergencyService.createCase("C2", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-B", "T2");
        queueService.enqueue("C1");
        queueService.enqueue("C2");

        intelligentDispatch.dispatchNext();
        intelligentDispatch.dispatchNext();

        List<DispatchDecisionRecord> log = intelligentDispatch.getAuditLog();
        assertEquals(2, log.size());
        // Log is most-recent-first
        assertEquals("C2", log.get(0).getCaseId());
        assertEquals("C1", log.get(1).getCaseId());
    }

    @Test
    @DisplayName("Rationale string is non-empty after successful dispatch")
    void testRationaleNonEmpty() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.CRITICAL, "SCENE-A", "Arrest");
        queueService.enqueue(ec);

        intelligentDispatch.dispatchNext();

        String rationale = intelligentDispatch.getLastDispatchRationale();
        assertFalse(rationale.isBlank());
        assertTrue(rationale.contains("AMB-N") || rationale.contains("C1"),
                "Rationale should reference the dispatched ambulance or case: " + rationale);
    }

    @Test
    @DisplayName("Default rationale placeholder before any dispatch")
    void testDefaultRationalePlaceholder() {
        String rationale = intelligentDispatch.getLastDispatchRationale();
        assertNotNull(rationale);
        assertFalse(rationale.isBlank());
    }

    // ==========================================
    // 3. Concurrency guard
    // ==========================================

    @Test
    @DisplayName("Concurrent dispatch attempt returns result without deadlock")
    void testConcurrentDispatchNoDeadlock() throws InterruptedException {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        queueService.enqueue(ec);

        AtomicReference<DispatchResult> r1 = new AtomicReference<>();
        AtomicReference<DispatchResult> r2 = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            r1.set(intelligentDispatch.dispatchNext());
            latch.countDown();
        });
        Thread t2 = new Thread(() -> {
            r2.set(intelligentDispatch.dispatchNext());
            latch.countDown();
        });

        t1.start();
        t2.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Threads should complete within 5s");

        // Exactly one of them should succeed
        int successes = (r1.get().isSuccess() ? 1 : 0) + (r2.get().isSuccess() ? 1 : 0);
        assertEquals(1, successes, "Exactly one dispatch should succeed when only one ambulance is available");
    }

    // ==========================================
    // 4. Asynchronous dispatch
    // ==========================================

    @Test
    @DisplayName("dispatchNextAsync delivers result via callback")
    void testDispatchNextAsyncCallbackDelivered() throws InterruptedException {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        queueService.enqueue(ec);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DispatchResult> callbackResult = new AtomicReference<>();

        intelligentDispatch.dispatchNextAsync(result -> {
            callbackResult.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(callbackResult.get());
        assertTrue(callbackResult.get().isSuccess());
    }

    @Test
    @DisplayName("dispatchEmergencyAsync delivers result via callback")
    void testDispatchEmergencyAsyncCallbackDelivered() throws InterruptedException {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-A", "Test");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DispatchResult> callbackResult = new AtomicReference<>();

        intelligentDispatch.dispatchEmergencyAsync(ec, result -> {
            callbackResult.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(callbackResult.get());
        assertTrue(callbackResult.get().isSuccess());
    }

    // ==========================================
    // 5. Traffic-change ETA recalculation
    // ==========================================

    @Test
    @DisplayName("recalculateEtas returns map of caseId to best ETA for pending emergencies")
    void testRecalculateEtas() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        EmergencyCase ecA = emergencyService.createCase("C-A", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        EmergencyCase ecB = emergencyService.createCase("C-B", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "SCENE-B", "Test");
        queueService.enqueue(ecA);
        queueService.enqueue(ecB);

        Map<String, Double> etas = intelligentDispatch.recalculateEtas();

        assertEquals(2, etas.size());
        assertTrue(etas.containsKey("C-A"));
        assertTrue(etas.containsKey("C-B"));
        // SCENE-A: best ETA = 3.0 (AMB-N)
        assertEquals(3.0, etas.get("C-A"), 1e-4);
        // SCENE-B: best ETA = 2.5 (AMB-S)
        assertEquals(2.5, etas.get("C-B"), 1e-4);
    }

    @Test
    @DisplayName("recalculateEtas returns empty map when no pending emergencies")
    void testRecalculateEtasNoPending() {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        Map<String, Double> etas = intelligentDispatch.recalculateEtas();
        assertTrue(etas.isEmpty());
    }

    @Test
    @DisplayName("recalculateEtas returns -1.0 for unreachable emergency")
    void testRecalculateEtasUnreachable() {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C-ISO", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "ISOLATED", "Test");
        queueService.enqueue(ec);

        Map<String, Double> etas = intelligentDispatch.recalculateEtas();
        assertTrue(etas.containsKey("C-ISO"));
        assertEquals(-1.0, etas.get("C-ISO"), 1e-4);
    }

    @Test
    @DisplayName("notifyTrafficChanged(sync) returns updated ETAs")
    void testNotifyTrafficChangedSync() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        queueService.enqueue(ec);

        Map<String, Double> etas = intelligentDispatch.notifyTrafficChanged();
        assertFalse(etas.isEmpty());
        assertTrue(etas.containsKey("C1"));
    }

    // ==========================================
    // 6. SeverityWeightedRankingPolicy
    // ==========================================

    @Test
    @DisplayName("SeverityWeightedRankingPolicy selects same winner as FastestResponse on equal severity")
    void testSeverityWeightedPolicyMatchesFastestForEqualSeverity() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        // Use severity-weighted dispatch service
        AmbulanceDispatchService sevDispatch = new AmbulanceDispatchService(
                graph, new DijkstraRoutingAlgorithm(), emergencyService, fleetService, queueService,
                SeverityWeightedRankingPolicy.INSTANCE, com.resqroute.dispatch.DefaultCapabilityMatcher.INSTANCE,
                testClock);
        IntelligentDispatchService sevIntelligent = new IntelligentDispatchService(
                sevDispatch, fleetService, queueService);

        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        queueService.enqueue(ec);

        DispatchResult result = sevIntelligent.dispatchNext();
        assertTrue(result.isSuccess());
        // AMB-N is still faster (3.0 min vs 8.0 min) and severity is equal, so it wins
        assertEquals("AMB-N", result.getAssignedAmbulance().orElseThrow().getId());
    }

    @Test
    @DisplayName("SeverityWeightedRankingPolicy getPolicyName returns non-blank string")
    void testSeverityWeightedPolicyName() {
        SeverityWeightedRankingPolicy policy = new SeverityWeightedRankingPolicy(0.05);
        assertFalse(policy.getPolicyName().isBlank());
        assertTrue(policy.getPolicyName().contains("Severity"));
    }

    @Test
    @DisplayName("SeverityWeightedRankingPolicy rejects negative urgency scale")
    void testSeverityWeightedPolicyRejectsNegativeScale() {
        assertThrows(IllegalArgumentException.class, () -> new SeverityWeightedRankingPolicy(-0.1));
    }

    // ==========================================
    // 7. DispatchDecisionRecord
    // ==========================================

    @Test
    @DisplayName("DispatchDecisionRecord.buildSummary contains key dispatch data")
    void testDispatchDecisionRecordBuildSummary() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "SCENE-A", "Arrest");
        queueService.enqueue(ec);

        intelligentDispatch.dispatchNext();
        DispatchDecisionRecord record = intelligentDispatch.getLastDecisionRecord();
        assertNotNull(record);

        String summary = record.buildSummary();
        assertTrue(summary.contains("AMB-N"), "Summary should include ambulance ID");
        assertTrue(summary.contains("C1"), "Summary should include case ID");
        assertFalse(summary.isBlank());
    }

    // ==========================================
    // 8. State isolation — no cross-contamination
    // ==========================================

    @Test
    @DisplayName("Failed dispatch leaves no partial state changes")
    void testFailedDispatchNoPartialState() {
        fleetService.registerAmbulance("AMB-1", "ST-NORTH");
        EmergencyCase ec = emergencyService.createCase("C-ISO", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "ISOLATED", "No path");
        queueService.enqueue(ec);

        DispatchResult result = intelligentDispatch.dispatchNext();
        assertFalse(result.isSuccess());

        // Ambulance remains available
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-1").getStatus());
        assertEquals(Optional.empty(), fleetService.getAmbulanceOrThrow("AMB-1").getAssignedCaseId());
        // Case remains in queue
        assertEquals(1, queueService.size());
    }

    @Test
    @DisplayName("IntelligentDispatchService does not affect non-dispatched ambulances")
    void testNonDispatchedAmbulancesUnaffected() {
        fleetService.registerAmbulance("AMB-N", "ST-NORTH");
        fleetService.registerAmbulance("AMB-S", "ST-SOUTH");

        EmergencyCase ec = emergencyService.createCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "SCENE-A", "Test");
        queueService.enqueue(ec);

        intelligentDispatch.dispatchNext();

        // AMB-N was dispatched (faster), AMB-S should still be available
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-S").getStatus());
        assertEquals(Optional.empty(), fleetService.getAmbulanceOrThrow("AMB-S").getAssignedCaseId());
    }
}
