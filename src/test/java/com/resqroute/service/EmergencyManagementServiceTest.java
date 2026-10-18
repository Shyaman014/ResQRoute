package com.resqroute.service;

import com.resqroute.exception.AmbulanceUnavailableException;
import com.resqroute.exception.DuplicateCaseException;
import com.resqroute.exception.EmergencyCaseNotFoundException;
import com.resqroute.exception.InvalidStatusTransitionException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 9: Emergency Case Management Service Tests")
class EmergencyManagementServiceTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;
    private MutableTestClock testClock;
    private EmergencyManagementService service;

    private static final Instant T0 = Instant.parse("2026-10-18T10:00:00Z");

    /**
     * Controllable deterministic Clock for testing timestamp progression.
     */
    static class MutableTestClock extends Clock {
        private Instant currentInstant;
        private final ZoneOffset zone;

        MutableTestClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
            this.zone = ZoneOffset.UTC;
        }

        public void advanceSeconds(long seconds) {
            this.currentInstant = this.currentInstant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return zone;
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
        graph.addNode(new Node("HQ", 12.9716, 77.5946, "Metro Dispatch Center"));
        graph.addNode(new Node("INT-A", 12.9730, 77.5980, "Crossing A"));
        graph.addNode(new Node("INT-B", 12.9760, 77.6010, "Intersection B"));
        graph.addNode(new Node("HOSP-01", 12.9780, 77.6200, "Apex Trauma Hospital"));

        testClock = new MutableTestClock(T0);
        fleetService = new AmbulanceFleetService(graph);
        service = new EmergencyManagementService(graph, fleetService, testClock);

        // Register two baseline ambulances
        fleetService.registerAmbulance("AMB-01", "HQ");
        fleetService.registerAmbulance("AMB-02", "HQ");
    }

    @Test
    @DisplayName("Case creation and retrieval succeeds with valid parameters")
    void testCreateAndRetrieveCase() {
        EmergencyCase created = service.createCase(
                "CASE-101",
                EmergencyType.CARDIAC,
                EmergencySeverity.CRITICAL,
                "INT-A",
                "Severe chest pain and collapse"
        );

        assertNotNull(created);
        assertEquals("CASE-101", created.getId());
        assertEquals(EmergencyType.CARDIAC, created.getType());
        assertEquals(EmergencySeverity.CRITICAL, created.getSeverity());
        assertEquals("INT-A", created.getPickupLocationNodeId());
        assertEquals(T0, created.getCreatedAt());
        assertEquals(EmergencyStatus.REPORTED, created.getStatus());

        // Retrieval by ID
        Optional<EmergencyCase> retrievedOpt = service.getCase("CASE-101");
        assertTrue(retrievedOpt.isPresent());
        assertEquals(created, retrievedOpt.get());

        EmergencyCase retrievedDirect = service.getCaseOrThrow("CASE-101");
        assertEquals(created, retrievedDirect);

        assertEquals(1, service.getTotalCaseCount());
        assertEquals(1, service.getActiveCaseCount());
    }

    @Test
    @DisplayName("Retrieval of non-existent case ID")
    void testGetNonExistentCase() {
        assertTrue(service.getCase("CASE-GHOST").isEmpty());
        assertThrows(EmergencyCaseNotFoundException.class, () ->
                service.getCaseOrThrow("CASE-GHOST"));
    }

    @Test
    @DisplayName("Prevents duplicate case IDs")
    void testPreventDuplicateCaseId() {
        service.createCase("CASE-101", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "First case");

        DuplicateCaseException ex = assertThrows(DuplicateCaseException.class, () ->
                service.createCase("CASE-101", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-B", "Duplicate ID case"));

        assertEquals("CASE-101", ex.getCaseId());
    }

    @Test
    @DisplayName("Rejects invalid pickup location not in road graph")
    void testRejectInvalidPickupLocation() {
        NodeNotFoundException ex = assertThrows(NodeNotFoundException.class, () ->
                service.createCase("CASE-INVALID-LOC", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "NON-EXISTENT-NODE", "Desc"));

        assertEquals("NON-EXISTENT-NODE", ex.getNodeId());
    }

    @Test
    @DisplayName("Rejects invalid destination hospital node")
    void testRejectInvalidHospitalNode() {
        service.createCase("CASE-101", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Chest pain");

        assertThrows(NodeNotFoundException.class, () ->
                service.setDestinationHospital("CASE-101", "GHOST-HOSPITAL"));

        // Valid hospital succeeds
        EmergencyCase withHosp = service.setDestinationHospital("CASE-101", "HOSP-01");
        assertEquals(Optional.of("HOSP-01"), withHosp.getDestinationHospitalId());
    }

    @Test
    @DisplayName("Valid step-by-step lifecycle progression and status history accuracy")
    void testFullLifecycleProgressionAndHistoryAccuracy() {
        // Step 1: Create case (REPORTED at T0)
        EmergencyCase c = service.createCase("CASE-LIFECYCLE-1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Cardiac event");
        service.setDestinationHospital("CASE-LIFECYCLE-1", "HOSP-01");

        // Step 2: Triage to WAITING_FOR_DISPATCH at T0 + 30s
        testClock.advanceSeconds(30);
        c = service.updateCaseStatus("CASE-LIFECYCLE-1", EmergencyStatus.WAITING_FOR_DISPATCH, "Triaged by nurse");
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, c.getStatus());

        // Step 3: Assign ambulance AMB-01 at T0 + 60s
        testClock.advanceSeconds(30);
        c = service.assignAmbulance("CASE-LIFECYCLE-1", "AMB-01");
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, c.getStatus());
        assertEquals(Optional.of("AMB-01"), c.getAssignedAmbulanceId());
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());

        // Step 4: Ambulance departs (AMBULANCE_EN_ROUTE) at T0 + 90s
        testClock.advanceSeconds(30);
        c = service.updateCaseStatus("CASE-LIFECYCLE-1", EmergencyStatus.AMBULANCE_EN_ROUTE, "Departed station");
        assertEquals(EmergencyStatus.AMBULANCE_EN_ROUTE, c.getStatus());
        assertEquals(AmbulanceStatus.EN_ROUTE, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());

        // Step 5: Arrive on scene and patient picked up (PATIENT_PICKED_UP) at T0 + 300s
        testClock.advanceSeconds(210);
        c = service.updateCaseStatus("CASE-LIFECYCLE-1", EmergencyStatus.PATIENT_PICKED_UP, "Arrived at patient location");
        assertEquals(EmergencyStatus.PATIENT_PICKED_UP, c.getStatus());
        assertEquals(AmbulanceStatus.ON_SCENE, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());
        assertEquals("INT-A", fleetService.getAmbulanceOrThrow("AMB-01").getCurrentLocationNodeId());

        // Step 6: Begin transit to hospital (TRANSPORTING) at T0 + 400s
        testClock.advanceSeconds(100);
        c = service.updateCaseStatus("CASE-LIFECYCLE-1", EmergencyStatus.TRANSPORTING, "Transporting under siren");
        assertEquals(EmergencyStatus.TRANSPORTING, c.getStatus());
        assertEquals(AmbulanceStatus.TRANSPORTING, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());

        // Step 7: Delivery to hospital (COMPLETED) at T0 + 600s
        testClock.advanceSeconds(200);
        c = service.updateCaseStatus("CASE-LIFECYCLE-1", EmergencyStatus.COMPLETED, "Patient admitted to ER");
        assertEquals(EmergencyStatus.COMPLETED, c.getStatus());
        assertTrue(c.isCompleted());
        assertFalse(c.isActive());

        // Ambulance freed and positioned at hospital
        Ambulance freedAmb = fleetService.getAmbulanceOrThrow("AMB-01");
        assertEquals(AmbulanceStatus.AVAILABLE, freedAmb.getStatus());
        assertEquals(Optional.empty(), freedAmb.getAssignedCaseId());
        assertEquals("HOSP-01", freedAmb.getCurrentLocationNodeId());

        // Verify status history accuracy
        List<EmergencyStatusHistoryEntry> history = c.getStatusHistory();
        assertEquals(7, history.size());
        assertEquals(EmergencyStatus.REPORTED, history.get(0).getStatus());
        assertEquals(T0, history.get(0).getTimestamp());

        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, history.get(1).getStatus());
        assertEquals(T0.plusSeconds(30), history.get(1).getTimestamp());
        assertEquals("Triaged by nurse", history.get(1).getNotes());

        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, history.get(2).getStatus());
        assertEquals(T0.plusSeconds(60), history.get(2).getTimestamp());

        assertEquals(EmergencyStatus.AMBULANCE_EN_ROUTE, history.get(3).getStatus());
        assertEquals(T0.plusSeconds(90), history.get(3).getTimestamp());

        assertEquals(EmergencyStatus.PATIENT_PICKED_UP, history.get(4).getStatus());
        assertEquals(T0.plusSeconds(300), history.get(4).getTimestamp());

        assertEquals(EmergencyStatus.TRANSPORTING, history.get(5).getStatus());
        assertEquals(T0.plusSeconds(400), history.get(5).getTimestamp());

        assertEquals(EmergencyStatus.COMPLETED, history.get(6).getStatus());
        assertEquals(T0.plusSeconds(600), history.get(6).getTimestamp());
        assertEquals("Patient admitted to ER", history.get(6).getNotes());
    }

    @Test
    @DisplayName("Rejects invalid lifecycle state transitions")
    void testRejectInvalidStatusTransitions() {
        service.createCase("CASE-INVALID-1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", "Fever");

        // Cannot jump directly from REPORTED to COMPLETED
        assertThrows(InvalidStatusTransitionException.class, () ->
                service.updateCaseStatus("CASE-INVALID-1", EmergencyStatus.COMPLETED));

        // Cannot jump directly from REPORTED to TRANSPORTING
        assertThrows(InvalidStatusTransitionException.class, () ->
                service.updateCaseStatus("CASE-INVALID-1", EmergencyStatus.TRANSPORTING));

        // Cannot transition to same status
        assertThrows(InvalidStatusTransitionException.class, () ->
                service.updateCaseStatus("CASE-INVALID-1", EmergencyStatus.REPORTED));
    }

    @Test
    @DisplayName("Cancellation rules: permits safe cancellation from eligible states and frees ambulance")
    void testCancellationRulesEligible() {
        // Case A: Cancel from REPORTED
        service.createCase("CASE-CANCEL-1", EmergencyType.OTHER, EmergencySeverity.LOW, "INT-A", "False alarm");
        EmergencyCase cancelled1 = service.cancelCase("CASE-CANCEL-1", "Caller reported patient recovered");
        assertEquals(EmergencyStatus.CANCELLED, cancelled1.getStatus());
        assertTrue(cancelled1.isCancelled());

        // Case B: Cancel from AMBULANCE_ASSIGNED -> frees assigned ambulance
        service.createCase("CASE-CANCEL-2", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Fall");
        service.assignAmbulance("CASE-CANCEL-2", "AMB-01");
        assertEquals(AmbulanceStatus.ASSIGNED, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());

        EmergencyCase cancelled2 = service.cancelCase("CASE-CANCEL-2", "Cancelled while ambulance preparing");
        assertEquals(EmergencyStatus.CANCELLED, cancelled2.getStatus());
        // Ambulance safely restored to AVAILABLE
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-01").getStatus());
        assertEquals(Optional.empty(), fleetService.getAmbulanceOrThrow("AMB-01").getAssignedCaseId());

        // Case C: Cancel while AMBULANCE_EN_ROUTE -> frees assigned ambulance
        service.createCase("CASE-CANCEL-3", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Heart attack");
        service.assignAmbulance("CASE-CANCEL-3", "AMB-02");
        service.updateCaseStatus("CASE-CANCEL-3", EmergencyStatus.AMBULANCE_EN_ROUTE);
        assertEquals(AmbulanceStatus.EN_ROUTE, fleetService.getAmbulanceOrThrow("AMB-02").getStatus());

        EmergencyCase cancelled3 = service.cancelCase("CASE-CANCEL-3", "Stood down by police on scene");
        assertEquals(EmergencyStatus.CANCELLED, cancelled3.getStatus());
        assertEquals(AmbulanceStatus.AVAILABLE, fleetService.getAmbulanceOrThrow("AMB-02").getStatus());
    }

    @Test
    @DisplayName("Cancellation rules: rejects cancellation from ineligible states (post-pickup, transit, completed, cancelled)")
    void testCancellationRulesIneligible() {
        // Case in TRANSPORTING
        service.createCase("CASE-INELIGIBLE", EmergencyType.TRAUMA, EmergencySeverity.CRITICAL, "INT-A", "Fracture");
        service.assignAmbulance("CASE-INELIGIBLE", "AMB-01");
        service.updateCaseStatus("CASE-INELIGIBLE", EmergencyStatus.AMBULANCE_EN_ROUTE);
        service.updateCaseStatus("CASE-INELIGIBLE", EmergencyStatus.PATIENT_PICKED_UP);

        // Cannot cancel after patient picked up
        assertThrows(InvalidStatusTransitionException.class, () ->
                service.cancelCase("CASE-INELIGIBLE", "Attempted cancellation"));

        service.updateCaseStatus("CASE-INELIGIBLE", EmergencyStatus.TRANSPORTING);

        // Cannot cancel in transit
        assertThrows(InvalidStatusTransitionException.class, () ->
                service.cancelCase("CASE-INELIGIBLE", "Attempted cancellation"));

        service.updateCaseStatus("CASE-INELIGIBLE", EmergencyStatus.COMPLETED);

        // Cannot cancel completed case
        assertThrows(InvalidStatusTransitionException.class, () ->
                service.cancelCase("CASE-INELIGIBLE", "Attempted cancellation"));
    }

    @Test
    @DisplayName("Completed cases are strictly excluded from active-case queries")
    void testActiveAndCompletedQueries() {
        service.createCase("C-ACTIVE-1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Active 1");
        service.createCase("C-ACTIVE-2", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Active 2");
        service.createCase("C-COMPLETED-1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", "To complete");
        service.createCase("C-CANCELLED-1", EmergencyType.OTHER, EmergencySeverity.LOW, "INT-A", "To cancel");

        // Complete C-COMPLETED-1
        service.assignAmbulance("C-COMPLETED-1", "AMB-01");
        service.updateCaseStatus("C-COMPLETED-1", EmergencyStatus.AMBULANCE_EN_ROUTE);
        service.updateCaseStatus("C-COMPLETED-1", EmergencyStatus.PATIENT_PICKED_UP);
        service.updateCaseStatus("C-COMPLETED-1", EmergencyStatus.COMPLETED);

        // Cancel C-CANCELLED-1
        service.cancelCase("C-CANCELLED-1", "False report");

        List<EmergencyCase> activeCases = service.getActiveCases();
        List<EmergencyCase> completedCases = service.getCompletedCases();
        List<EmergencyCase> cancelledCases = service.getCancelledCases();

        assertEquals(2, activeCases.size());
        assertTrue(activeCases.stream().anyMatch(c -> c.getId().equals("C-ACTIVE-1")));
        assertTrue(activeCases.stream().anyMatch(c -> c.getId().equals("C-ACTIVE-2")));
        assertFalse(activeCases.stream().anyMatch(c -> c.getId().equals("C-COMPLETED-1")));
        assertFalse(activeCases.stream().anyMatch(c -> c.getId().equals("C-CANCELLED-1")));

        assertEquals(1, completedCases.size());
        assertEquals("C-COMPLETED-1", completedCases.get(0).getId());

        assertEquals(1, cancelledCases.size());
        assertEquals("C-CANCELLED-1", cancelledCases.get(0).getId());
    }

    @Test
    @DisplayName("Filtering cases by severity, type, and status")
    void testFilteringCapabilities() {
        service.createCase("C1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Cardiac 1");
        service.createCase("C2", EmergencyType.CARDIAC, EmergencySeverity.HIGH, "INT-A", "Cardiac 2");
        service.createCase("C3", EmergencyType.TRAUMA, EmergencySeverity.CRITICAL, "INT-A", "Trauma 1");
        service.createCase("C4", EmergencyType.FIRE_RESCUE, EmergencySeverity.MEDIUM, "INT-A", "Fire 1");
        service.createCase("C5", EmergencyType.MEDICAL, EmergencySeverity.LOW, "INT-A", "Medical 1");

        // Filter by Severity
        List<EmergencyCase> criticalCases = service.getCasesBySeverity(EmergencySeverity.CRITICAL);
        assertEquals(2, criticalCases.size());
        assertTrue(criticalCases.stream().allMatch(c -> c.getSeverity() == EmergencySeverity.CRITICAL));

        // Filter by Type
        List<EmergencyCase> cardiacCases = service.getCasesByType(EmergencyType.CARDIAC);
        assertEquals(2, cardiacCases.size());
        assertTrue(cardiacCases.stream().allMatch(c -> c.getType() == EmergencyType.CARDIAC));

        // Filter by Status
        service.updateCaseStatus("C1", EmergencyStatus.WAITING_FOR_DISPATCH);
        List<EmergencyCase> waitingCases = service.getCasesByStatus(EmergencyStatus.WAITING_FOR_DISPATCH);
        assertEquals(1, waitingCases.size());
        assertEquals("C1", waitingCases.get(0).getId());

        // Multi-criteria filter: CRITICAL severity + CARDIAC type
        List<EmergencyCase> critCardiac = service.filterCases(EmergencySeverity.CRITICAL, EmergencyType.CARDIAC, null);
        assertEquals(1, critCardiac.size());
        assertEquals("C1", critCardiac.get(0).getId());
    }

    @Test
    @DisplayName("Active cases ranked by triage priority (severity first, then creation time)")
    void testActiveCasesRankedByPriority() {
        // T0: Low severity
        EmergencyCase low = service.createCase("C-LOW", EmergencyType.OTHER, EmergencySeverity.LOW, "INT-A", "Sprained ankle");

        // T0 + 10s: High severity
        testClock.advanceSeconds(10);
        EmergencyCase high = service.createCase("C-HIGH", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Deep laceration");

        // T0 + 20s: First Critical
        testClock.advanceSeconds(10);
        EmergencyCase critEarly = service.createCase("C-CRIT-1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Arrest 1");

        // T0 + 30s: Second Critical (later)
        testClock.advanceSeconds(10);
        EmergencyCase critLate = service.createCase("C-CRIT-2", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Arrest 2");

        List<EmergencyCase> ranked = service.getActiveCasesRankedByPriority();
        assertEquals(4, ranked.size());

        // Ranking order:
        // 1. C-CRIT-1 (CRITICAL, earlier)
        // 2. C-CRIT-2 (CRITICAL, later)
        // 3. C-HIGH   (HIGH)
        // 4. C-LOW    (LOW)
        assertEquals("C-CRIT-1", ranked.get(0).getId());
        assertEquals("C-CRIT-2", ranked.get(1).getId());
        assertEquals("C-HIGH", ranked.get(2).getId());
        assertEquals("C-LOW", ranked.get(3).getId());
    }

    @Test
    @DisplayName("Fleet integration: Prevents assigning an already allocated or unavailable ambulance")
    void testFleetConflictPrevention() {
        service.createCase("CASE-A", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", "Case A");
        service.createCase("CASE-B", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "INT-A", "Case B");

        // Assign AMB-01 to Case A
        service.assignAmbulance("CASE-A", "AMB-01");

        // Attempting to assign AMB-01 to Case B must be rejected
        assertThrows(AmbulanceUnavailableException.class, () ->
                service.assignAmbulance("CASE-B", "AMB-01"));

        // Assigning available AMB-02 to Case B succeeds
        assertDoesNotThrow(() ->
                service.assignAmbulance("CASE-B", "AMB-02"));
    }
}
