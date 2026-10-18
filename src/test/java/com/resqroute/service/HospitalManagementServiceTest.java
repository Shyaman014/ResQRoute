package com.resqroute.service;

import com.resqroute.exception.*;
import com.resqroute.graph.RoadGraph;
import com.resqroute.hospital.DefaultHospitalSuitabilityMatcher;
import com.resqroute.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 12: Hospital Capacity & Suitability Management Tests")
class HospitalManagementServiceTest {

    private RoadGraph graph;
    private HospitalManagementService hospitalService;
    private DefaultHospitalSuitabilityMatcher suitabilityMatcher;

    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

    static class FixedClock extends Clock {
        private final Instant instant;

        FixedClock(Instant instant) {
            this.instant = instant;
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
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        graph.addNode(new Node("HOSP-NORTH-NODE", 12.98, 77.59, "North Hub"));
        graph.addNode(new Node("HOSP-CENTRAL-NODE", 12.97, 77.60, "Central Hub"));
        graph.addNode(new Node("HOSP-SOUTH-NODE", 12.96, 77.59, "South Hub"));
        graph.addNode(new Node("SCENE-NODE", 12.975, 77.605, "Incident Scene"));

        hospitalService = new HospitalManagementService(graph, new FixedClock(T0));
        suitabilityMatcher = DefaultHospitalSuitabilityMatcher.INSTANCE;
    }

    @Test
    @DisplayName("Hospital registration and retrieval by ID")
    void testRegisterAndRetrieveHospital() {
        Hospital hospital = Hospital.builder()
                .id("HOSP-01")
                .name("Apex General Hospital")
                .locationNodeId("HOSP-NORTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER, HospitalCapability.ICU))
                .totalCapacity(20)
                .availableCapacity(15)
                .build();

        Hospital registered = hospitalService.registerHospital(hospital);
        assertEquals("HOSP-01", registered.getId());
        assertEquals("Apex General Hospital", registered.getName());
        assertEquals(15, registered.getAvailableCapacity());
        assertEquals(20, registered.getTotalCapacity());
        assertEquals(5, registered.getOccupiedCapacity());
        assertEquals(HospitalStatus.OPEN, registered.getStatus());

        Optional<Hospital> retrieved = hospitalService.getHospital("HOSP-01");
        assertTrue(retrieved.isPresent());
        assertEquals(registered, retrieved.get());
        assertEquals(1, hospitalService.getHospitalCount());
    }

    @Test
    @DisplayName("Rejects duplicate hospital IDs")
    void testRejectDuplicateHospitalId() {
        Hospital h1 = new Hospital("HOSP-01", "Hospital 1", "HOSP-NORTH-NODE", 10);
        Hospital h2 = new Hospital("HOSP-01", "Hospital 2", "HOSP-CENTRAL-NODE", 15);

        hospitalService.registerHospital(h1);
        assertThrows(DuplicateHospitalException.class, () -> hospitalService.registerHospital(h2));
    }

    @Test
    @DisplayName("Rejects hospital at an invalid road network location")
    void testRejectInvalidLocationNode() {
        Hospital h = new Hospital("HOSP-INVALID", "Hospital Invalid", "NON_EXISTENT_NODE", 10);
        assertThrows(NodeNotFoundException.class, () -> hospitalService.registerHospital(h));
    }

    @Test
    @DisplayName("Capacity reservation and release cycle")
    void testCapacityReservationAndRelease() {
        Hospital hospital = new Hospital("HOSP-01", "Apex General", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(hospital);

        assertEquals(5, hospitalService.getHospitalOrThrow("HOSP-01").getAvailableCapacity());

        // Reserve 1 bed for CASE-101
        Hospital afterReserve = hospitalService.reserveCapacity("HOSP-01", "CASE-101");
        assertEquals(4, afterReserve.getAvailableCapacity());
        assertTrue(hospitalService.hasActiveReservation("HOSP-01", "CASE-101"));

        // Release bed for CASE-101
        Hospital afterRelease = hospitalService.releaseCapacity("HOSP-01", "CASE-101");
        assertEquals(5, afterRelease.getAvailableCapacity());
        assertFalse(hospitalService.hasActiveReservation("HOSP-01", "CASE-101"));
    }

    @Test
    @DisplayName("Status consistency: Hospital transitions to BUSY when full, and back to OPEN when capacity freed")
    void testStatusConsistencyFullHospitalTransitionsToBusy() {
        Hospital hospital = new Hospital("HOSP-SMALL", "Community Clinic", "HOSP-NORTH-NODE", 2);
        hospitalService.registerHospital(hospital);

        assertEquals(HospitalStatus.OPEN, hospitalService.getHospitalOrThrow("HOSP-SMALL").getStatus());

        // Fill capacity: reservation 1
        hospitalService.reserveCapacity("HOSP-SMALL", "CASE-1");
        assertEquals(HospitalStatus.OPEN, hospitalService.getHospitalOrThrow("HOSP-SMALL").getStatus());

        // Fill capacity: reservation 2 (available becomes 0)
        Hospital full = hospitalService.reserveCapacity("HOSP-SMALL", "CASE-2");
        assertEquals(0, full.getAvailableCapacity());
        assertEquals(HospitalStatus.BUSY, full.getStatus());
        assertFalse(full.isAvailableForAdmission());

        // Attempting another reservation while BUSY throws HospitalCapacityExceededException
        assertThrows(HospitalCapacityExceededException.class, () ->
                hospitalService.reserveCapacity("HOSP-SMALL", "CASE-3"));

        // Release 1 spot -> transitions back to OPEN
        Hospital freed = hospitalService.releaseCapacity("HOSP-SMALL", "CASE-1");
        assertEquals(1, freed.getAvailableCapacity());
        assertEquals(HospitalStatus.OPEN, freed.getStatus());
        assertTrue(freed.isAvailableForAdmission());
    }

    @Test
    @DisplayName("Hospital closure rejects reservations but preserves existing reservations for release")
    void testClosedHospitalRejectsReservationsAndPreservesState() {
        Hospital hospital = new Hospital("HOSP-01", "Metro Hospital", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(hospital);

        hospitalService.reserveCapacity("HOSP-01", "CASE-A");
        assertEquals(4, hospitalService.getHospitalOrThrow("HOSP-01").getAvailableCapacity());

        // Administratively close hospital
        Hospital closed = hospitalService.closeHospital("HOSP-01");
        assertEquals(HospitalStatus.CLOSED, closed.getStatus());
        assertFalse(closed.isAvailableForAdmission());

        // New reservations rejected
        assertThrows(HospitalClosedException.class, () ->
                hospitalService.reserveCapacity("HOSP-01", "CASE-B"));

        // Existing reservation CASE-A can still be released safely
        Hospital released = hospitalService.releaseCapacity("HOSP-01", "CASE-A");
        assertEquals(5, released.getAvailableCapacity());
        // Status remains CLOSED (does not revert to OPEN automatically because it was explicitly closed)
        assertEquals(HospitalStatus.CLOSED, released.getStatus());

        // Reopen hospital -> transitions to OPEN
        Hospital reopened = hospitalService.openHospital("HOSP-01");
        assertEquals(HospitalStatus.OPEN, reopened.getStatus());
        assertTrue(reopened.isAvailableForAdmission());
    }

    @Test
    @DisplayName("Capacity boundaries prevent underflow and overflow")
    void testCapacityBoundaries() {
        Hospital h = new Hospital("HOSP-01", "Test Hospital", "HOSP-NORTH-NODE", 2);
        hospitalService.registerHospital(h);

        hospitalService.reserveCapacity("HOSP-01", "C1");
        hospitalService.reserveCapacity("HOSP-01", "C2");

        // Available is 0. Underflow blocked
        assertThrows(HospitalCapacityExceededException.class, () ->
                hospitalService.reserveCapacity("HOSP-01", "C3"));

        // Cannot reduce total capacity below active reservations count (2)
        assertThrows(IllegalArgumentException.class, () ->
                hospitalService.updateTotalCapacity("HOSP-01", 1));

        // Expanding total capacity to 4 adjusts available to 2 (4 - 2 active)
        Hospital updated = hospitalService.updateTotalCapacity("HOSP-01", 4);
        assertEquals(4, updated.getTotalCapacity());
        assertEquals(2, updated.getAvailableCapacity());
        assertEquals(HospitalStatus.OPEN, updated.getStatus());
    }

    @Test
    @DisplayName("Duplicate reservations and double releases are strictly prevented")
    void testDuplicateReservationsAndReleases() {
        Hospital h = new Hospital("HOSP-01", "Metro General", "HOSP-NORTH-NODE", 5);
        hospitalService.registerHospital(h);

        hospitalService.reserveCapacity("HOSP-01", "CASE-100");

        // Duplicate reservation with same caseId throws DuplicateReservationException
        assertThrows(DuplicateReservationException.class, () ->
                hospitalService.reserveCapacity("HOSP-01", "CASE-100"));

        // Release once succeeds
        hospitalService.releaseCapacity("HOSP-01", "CASE-100");

        // Double release throws ReservationNotFoundException
        assertThrows(ReservationNotFoundException.class, () ->
                hospitalService.releaseCapacity("HOSP-01", "CASE-100"));

        // Release non-existent case throws ReservationNotFoundException
        assertThrows(ReservationNotFoundException.class, () ->
                hospitalService.releaseCapacity("HOSP-01", "CASE-NONEXISTENT"));
    }

    @Test
    @DisplayName("Concurrent reservations cannot overcommit available capacity")
    void testConcurrentCapacityReservations() throws InterruptedException {
        final int INITIAL_CAPACITY = 10;
        final int THREAD_COUNT = 30; // 30 concurrent attempts for 10 spots

        Hospital h = new Hospital("HOSP-CONCURRENT", "Stress Care", "HOSP-NORTH-NODE", INITIAL_CAPACITY);
        hospitalService.registerHospital(h);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger capacityExceededCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final String caseId = "CASE-CONCURRENT-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    hospitalService.reserveCapacity("HOSP-CONCURRENT", caseId);
                    successCount.incrementAndGet();
                } catch (HospitalCapacityExceededException e) {
                    capacityExceededCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly 10 reservations must succeed, exactly 20 must fail
        assertEquals(INITIAL_CAPACITY, successCount.get());
        assertEquals(THREAD_COUNT - INITIAL_CAPACITY, capacityExceededCount.get());

        Hospital finalState = hospitalService.getHospitalOrThrow("HOSP-CONCURRENT");
        assertEquals(0, finalState.getAvailableCapacity());
        assertEquals(HospitalStatus.BUSY, finalState.getStatus());
        assertEquals(INITIAL_CAPACITY, hospitalService.getActiveReservations("HOSP-CONCURRENT").size());
    }

    @Test
    @DisplayName("Emergency capability compatibility matching")
    void testCapabilityCompatibility() {
        Hospital traumaCardiacHospital = Hospital.builder()
                .id("HOSP-APEX")
                .name("Apex Trauma & Cardiac Center")
                .locationNodeId("HOSP-NORTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.CARDIAC, EmergencyType.TRAUMA, EmergencyType.MEDICAL))
                .capabilities(Set.of(
                        HospitalCapability.EMERGENCY_ROOM,
                        HospitalCapability.TRAUMA_CENTER,
                        HospitalCapability.CARDIAC_CATH_LAB,
                        HospitalCapability.ICU))
                .totalCapacity(10)
                .build();

        Hospital communityHospital = Hospital.builder()
                .id("HOSP-COMMUNITY")
                .name("Community Primary Care")
                .locationNodeId("HOSP-SOUTH-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.OTHER))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(10)
                .build();

        // 1. Cardiac Critical Case
        EmergencyCase cardiacCase = EmergencyCase.builder()
                .id("CASE-CARDIAC")
                .type(EmergencyType.CARDIAC)
                .severity(EmergencySeverity.CRITICAL)
                .pickupLocationNodeId("SCENE-NODE")
                .build();

        // Apex has CARDIAC_CATH_LAB and ICU -> medically compatible
        assertTrue(suitabilityMatcher.isMedicallyCompatible(traumaCardiacHospital, cardiacCase));
        assertTrue(suitabilityMatcher.isSuitable(traumaCardiacHospital, cardiacCase));

        // Community lacks CARDIAC_CATH_LAB and ICU and doesn't support CARDIAC type -> incompatible
        assertFalse(suitabilityMatcher.isMedicallyCompatible(communityHospital, cardiacCase));
        assertFalse(suitabilityMatcher.isSuitable(communityHospital, cardiacCase));

        // 2. Severe Trauma Case
        EmergencyCase traumaCase = EmergencyCase.builder()
                .id("CASE-TRAUMA")
                .type(EmergencyType.TRAUMA)
                .severity(EmergencySeverity.HIGH)
                .pickupLocationNodeId("SCENE-NODE")
                .build();

        assertTrue(suitabilityMatcher.isMedicallyCompatible(traumaCardiacHospital, traumaCase));
        assertFalse(suitabilityMatcher.isMedicallyCompatible(communityHospital, traumaCase));

        // 3. Simple Medical Case
        EmergencyCase minorCase = EmergencyCase.builder()
                .id("CASE-MINOR")
                .type(EmergencyType.MEDICAL)
                .severity(EmergencySeverity.LOW)
                .pickupLocationNodeId("SCENE-NODE")
                .build();

        assertTrue(suitabilityMatcher.isMedicallyCompatible(traumaCardiacHospital, minorCase));
        assertTrue(suitabilityMatcher.isMedicallyCompatible(communityHospital, minorCase));
    }

    @Test
    @DisplayName("Lists hospitals by status, capability, and availability")
    void testFilteringByStatusAndCapability() {
        Hospital openTrauma = Hospital.builder()
                .id("H1")
                .name("H1")
                .locationNodeId("HOSP-NORTH-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER))
                .totalCapacity(5)
                .availableCapacity(3)
                .status(HospitalStatus.OPEN)
                .build();

        Hospital busyCardiac = Hospital.builder()
                .id("H2")
                .name("H2")
                .locationNodeId("HOSP-CENTRAL-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.CARDIAC_CATH_LAB))
                .totalCapacity(5)
                .availableCapacity(0)
                .status(HospitalStatus.BUSY)
                .build();

        Hospital closedHosp = Hospital.builder()
                .id("H3")
                .name("H3")
                .locationNodeId("HOSP-SOUTH-NODE")
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(5)
                .availableCapacity(5)
                .status(HospitalStatus.CLOSED)
                .build();

        hospitalService.registerHospital(openTrauma);
        hospitalService.registerHospital(busyCardiac);
        hospitalService.registerHospital(closedHosp);

        assertEquals(1, hospitalService.getHospitalsByStatus(HospitalStatus.OPEN).size());
        assertEquals(1, hospitalService.getHospitalsByStatus(HospitalStatus.BUSY).size());
        assertEquals(1, hospitalService.getHospitalsByStatus(HospitalStatus.CLOSED).size());

        List<Hospital> traumaHospitals = hospitalService.getHospitalsByCapability(HospitalCapability.TRAUMA_CENTER);
        assertEquals(1, traumaHospitals.size());
        assertEquals("H1", traumaHospitals.get(0).getId());

        List<Hospital> availableHospitals = hospitalService.getAvailableHospitals();
        assertEquals(1, availableHospitals.size());
        assertEquals("H1", availableHospitals.get(0).getId());
    }
}
