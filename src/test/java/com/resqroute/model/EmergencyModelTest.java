package com.resqroute.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 9: Emergency Model & Priority Tests")
class EmergencyModelTest {

    private final Instant testTime = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    @DisplayName("Successfully creates EmergencyCase with valid parameters")
    void testValidEmergencyCaseCreation() {
        EmergencyCase emergencyCase = new EmergencyCase(
                "CASE-101",
                EmergencyType.CARDIAC,
                EmergencySeverity.CRITICAL,
                "INT-A",
                testTime,
                "Patient collapsed with acute cardiac distress"
        );

        assertEquals("CASE-101", emergencyCase.getId());
        assertEquals(EmergencyType.CARDIAC, emergencyCase.getType());
        assertEquals(EmergencySeverity.CRITICAL, emergencyCase.getSeverity());
        assertEquals("INT-A", emergencyCase.getPickupLocationNodeId());
        assertEquals(testTime, emergencyCase.getCreatedAt());
        assertEquals(EmergencyStatus.REPORTED, emergencyCase.getStatus());
        assertEquals(Optional.empty(), emergencyCase.getAssignedAmbulanceId());
        assertEquals(Optional.empty(), emergencyCase.getDestinationHospitalId());
        assertEquals("Patient collapsed with acute cardiac distress", emergencyCase.getDescription());
        assertTrue(emergencyCase.isActive());
        assertFalse(emergencyCase.isCompleted());
        assertFalse(emergencyCase.isCancelled());
        assertFalse(emergencyCase.isTerminal());
        assertFalse(emergencyCase.hasAssignedAmbulance());
        assertFalse(emergencyCase.hasDestinationHospital());

        assertEquals(1, emergencyCase.getStatusHistory().size());
        assertEquals(EmergencyStatus.REPORTED, emergencyCase.getStatusHistory().get(0).getStatus());
        assertEquals(testTime, emergencyCase.getStatusHistory().get(0).getTimestamp());
    }

    @Test
    @DisplayName("EmergencyCase builder constructs fully populated instance")
    void testBuilderCreation() {
        EmergencyCase emergencyCase = EmergencyCase.builder()
                .id("CASE-BUILDER-1")
                .type(EmergencyType.TRAUMA)
                .severity(EmergencySeverity.HIGH)
                .pickupLocationNodeId("INT-B")
                .createdAt(testTime)
                .status(EmergencyStatus.WAITING_FOR_DISPATCH)
                .assignedAmbulanceId("AMB-05")
                .destinationHospitalId("HOSP-01")
                .description("Highway vehicle rollover")
                .build();

        assertEquals("CASE-BUILDER-1", emergencyCase.getId());
        assertEquals(EmergencyType.TRAUMA, emergencyCase.getType());
        assertEquals(EmergencySeverity.HIGH, emergencyCase.getSeverity());
        assertEquals("INT-B", emergencyCase.getPickupLocationNodeId());
        assertEquals(EmergencyStatus.WAITING_FOR_DISPATCH, emergencyCase.getStatus());
        assertEquals(Optional.of("AMB-05"), emergencyCase.getAssignedAmbulanceId());
        assertEquals(Optional.of("HOSP-01"), emergencyCase.getDestinationHospitalId());
        assertTrue(emergencyCase.hasAssignedAmbulance());
        assertTrue(emergencyCase.hasDestinationHospital());
    }

    @Test
    @DisplayName("Rejects null or blank case ID")
    void testRejectsInvalidCaseId() {
        assertThrows(IllegalArgumentException.class, () ->
                new EmergencyCase(null, EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", testTime, "Test"));
        assertThrows(IllegalArgumentException.class, () ->
                new EmergencyCase("   ", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "INT-A", testTime, "Test"));
    }

    @Test
    @DisplayName("Rejects null or blank pickup location")
    void testRejectsInvalidPickupLocation() {
        assertThrows(IllegalArgumentException.class, () ->
                new EmergencyCase("C1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, null, testTime, "Test"));
        assertThrows(IllegalArgumentException.class, () ->
                new EmergencyCase("C1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "  ", testTime, "Test"));
    }

    @Test
    @DisplayName("Rejects null required enum arguments and timestamp")
    void testRejectsNullRequiredArguments() {
        assertThrows(NullPointerException.class, () ->
                new EmergencyCase("C1", null, EmergencySeverity.HIGH, "INT-A", testTime, "Desc"));
        assertThrows(NullPointerException.class, () ->
                new EmergencyCase("C1", EmergencyType.TRAUMA, null, "INT-A", testTime, "Desc"));
        assertThrows(NullPointerException.class, () ->
                new EmergencyCase("C1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "INT-A", null, "Desc"));
    }

    @Test
    @DisplayName("Status history is immutable and preserves chronological order")
    void testStatusHistoryImmutability() {
        EmergencyCase emergencyCase = new EmergencyCase("C1", EmergencyType.MEDICAL, EmergencySeverity.LOW, "INT-A", testTime, "Desc");
        List<EmergencyStatusHistoryEntry> history = emergencyCase.getStatusHistory();

        assertThrows(UnsupportedOperationException.class, () ->
                history.add(new EmergencyStatusHistoryEntry(EmergencyStatus.COMPLETED, testTime)));
    }

    @Test
    @DisplayName("Immutable copy methods create updated instances correctly")
    void testImmutableCopyMethods() {
        EmergencyCase original = new EmergencyCase("C1", EmergencyType.MEDICAL, EmergencySeverity.LOW, "INT-A", testTime, "Desc");

        Instant t1 = testTime.plusSeconds(60);
        EmergencyCase withAmb = original.withAssignedAmbulance("AMB-01");
        assertEquals(Optional.of("AMB-01"), withAmb.getAssignedAmbulanceId());
        assertEquals(Optional.empty(), original.getAssignedAmbulanceId());

        EmergencyCase withHosp = withAmb.withDestinationHospital("HOSP-01");
        assertEquals(Optional.of("HOSP-01"), withHosp.getDestinationHospitalId());

        EmergencyCase withStatus = withHosp.withStatus(EmergencyStatus.AMBULANCE_ASSIGNED, t1, "Dispatched");
        assertEquals(EmergencyStatus.AMBULANCE_ASSIGNED, withStatus.getStatus());
        assertEquals(2, withStatus.getStatusHistory().size());
        assertEquals("Dispatched", withStatus.getStatusHistory().get(1).getNotes());

        EmergencyCase unassigned = withStatus.withUnassignedAmbulance();
        assertEquals(Optional.empty(), unassigned.getAssignedAmbulanceId());
    }

    @Test
    @DisplayName("EmergencySeverity priority ranking and score comparison")
    void testSeverityPriorityRanking() {
        assertEquals(1, EmergencySeverity.CRITICAL.getPriorityRank());
        assertEquals(2, EmergencySeverity.HIGH.getPriorityRank());
        assertEquals(3, EmergencySeverity.MEDIUM.getPriorityRank());
        assertEquals(4, EmergencySeverity.LOW.getPriorityRank());

        assertEquals(100, EmergencySeverity.CRITICAL.getUrgencyWeight());
        assertEquals(75, EmergencySeverity.HIGH.getUrgencyWeight());
        assertEquals(50, EmergencySeverity.MEDIUM.getUrgencyWeight());
        assertEquals(25, EmergencySeverity.LOW.getUrgencyWeight());

        assertTrue(EmergencySeverity.CRITICAL.isHigherPriorityThan(EmergencySeverity.HIGH));
        assertTrue(EmergencySeverity.HIGH.isHigherPriorityThan(EmergencySeverity.MEDIUM));
        assertTrue(EmergencySeverity.MEDIUM.isHigherPriorityThan(EmergencySeverity.LOW));
        assertFalse(EmergencySeverity.LOW.isHigherPriorityThan(EmergencySeverity.CRITICAL));
    }

    @Test
    @DisplayName("EmergencyPriorityComparator ranks by severity, then FIFO timestamp, then ID")
    void testPriorityComparator() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:05:00Z");

        EmergencyCase criticalLate = new EmergencyCase("CASE-CRIT-2", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "N1", t1, "Desc");
        EmergencyCase criticalEarly = new EmergencyCase("CASE-CRIT-1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "N1", t0, "Desc");
        EmergencyCase highEarly = new EmergencyCase("CASE-HIGH-1", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "N1", t0, "Desc");
        EmergencyCase mediumEarly = new EmergencyCase("CASE-MED-1", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "N1", t0, "Desc");
        EmergencyCase lowEarly = new EmergencyCase("CASE-LOW-1", EmergencyType.OTHER, EmergencySeverity.LOW, "N1", t0, "Desc");

        List<EmergencyCase> list = new java.util.ArrayList<>(List.of(lowEarly, highEarly, criticalLate, mediumEarly, criticalEarly));
        list.sort(EmergencyPriorityComparator.INSTANCE);

        // Expected order:
        // 1. criticalEarly (CRITICAL, t0)
        // 2. criticalLate (CRITICAL, t1)
        // 3. highEarly (HIGH, t0)
        // 4. mediumEarly (MEDIUM, t0)
        // 5. lowEarly (LOW, t0)
        assertEquals("CASE-CRIT-1", list.get(0).getId());
        assertEquals("CASE-CRIT-2", list.get(1).getId());
        assertEquals("CASE-HIGH-1", list.get(2).getId());
        assertEquals("CASE-MED-1", list.get(3).getId());
        assertEquals("CASE-LOW-1", list.get(4).getId());
    }

    @Test
    @DisplayName("EmergencyStatus state properties")
    void testStatusStateProperties() {
        assertTrue(EmergencyStatus.REPORTED.isActive());
        assertTrue(EmergencyStatus.WAITING_FOR_DISPATCH.isActive());
        assertTrue(EmergencyStatus.AMBULANCE_ASSIGNED.isActive());
        assertTrue(EmergencyStatus.AMBULANCE_EN_ROUTE.isActive());
        assertTrue(EmergencyStatus.PATIENT_PICKED_UP.isActive());
        assertTrue(EmergencyStatus.TRANSPORTING.isActive());

        assertFalse(EmergencyStatus.COMPLETED.isActive());
        assertTrue(EmergencyStatus.COMPLETED.isTerminal());

        assertFalse(EmergencyStatus.CANCELLED.isActive());
        assertTrue(EmergencyStatus.CANCELLED.isTerminal());

        assertFalse(EmergencyStatus.REPORTED.requiresAmbulance());
        assertFalse(EmergencyStatus.WAITING_FOR_DISPATCH.requiresAmbulance());
        assertTrue(EmergencyStatus.AMBULANCE_ASSIGNED.requiresAmbulance());
        assertTrue(EmergencyStatus.AMBULANCE_EN_ROUTE.requiresAmbulance());
        assertTrue(EmergencyStatus.PATIENT_PICKED_UP.requiresAmbulance());
        assertTrue(EmergencyStatus.TRANSPORTING.requiresAmbulance());
    }

    @Test
    @DisplayName("EmergencyType display names")
    void testEmergencyTypeDisplayNames() {
        assertEquals("Medical Emergency", EmergencyType.MEDICAL.displayName());
        assertEquals("Trauma Incident", EmergencyType.TRAUMA.displayName());
        assertEquals("Cardiac Emergency", EmergencyType.CARDIAC.displayName());
        assertEquals("Fire & Rescue Operation", EmergencyType.FIRE_RESCUE.displayName());
        assertEquals("Other Emergency", EmergencyType.OTHER.displayName());
    }
}
