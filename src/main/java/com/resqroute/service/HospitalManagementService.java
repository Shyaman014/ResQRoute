package com.resqroute.service;

import com.resqroute.exception.*;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing hospital registries, dynamic admission capacity, operational intake status,
 * and thread-safe reservation lifecycles.
 *
 * <h3>Concurrency &amp; Transaction Strategy:</h3>
 * <p>
 * Hospital state mutations (capacity reservation, release, reconfiguration, and operational
 * status transitions) are synchronized per hospital monitor. This guarantees:
 * <ul>
 *   <li><b>Atomicity:</b> Capacity checks and decrements/increments occur as single atomic transactions.</li>
 *   <li><b>No Over-commit:</b> Concurrent reservations can never drive available capacity below zero.</li>
 *   <li><b>Idempotent Integrity:</b> Double-reservations and duplicate releases are blocked with explicit exceptions.</li>
 *   <li><b>Closure Safety:</b> Closing a hospital rejects new reservations while preserving existing
 *       in-flight reservations, allowing them to release safely without corrupting capacity bounds.</li>
 *   <li><b>Status Alignment:</b> Status never contradicts capacity (e.g. 0 available capacity cannot remain OPEN).</li>
 * </ul>
 * </p>
 */
public class HospitalManagementService {

    private final RoadGraph roadGraph;
    private final Clock clock;
    private final Map<String, Hospital> hospitalsById;
    private final Map<String, Set<String>> activeReservations;
    private final Map<String, Object> hospitalLocks;

    /**
     * Primary constructor injecting graph and clock dependencies.
     */
    public HospitalManagementService(RoadGraph roadGraph, Clock clock) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.hospitalsById = new ConcurrentHashMap<>();
        this.activeReservations = new ConcurrentHashMap<>();
        this.hospitalLocks = new ConcurrentHashMap<>();
    }

    /**
     * Convenience constructor with system UTC clock.
     */
    public HospitalManagementService(RoadGraph roadGraph) {
        this(roadGraph, Clock.systemUTC());
    }

    /**
     * Registers a new hospital in the management network.
     *
     * @param hospital The hospital instance to register; must not be null.
     * @return The registered Hospital.
     * @throws DuplicateHospitalException if a hospital with the same ID already exists.
     * @throws NodeNotFoundException     if the hospital location node is absent in the road graph.
     */
    public Hospital registerHospital(Hospital hospital) {
        Objects.requireNonNull(hospital, "Hospital must not be null");
        String id = hospital.getId();

        if (hospitalsById.containsKey(id)) {
            throw new DuplicateHospitalException(id);
        }

        String location = hospital.getLocationNodeId();
        if (!roadGraph.containsNode(location)) {
            throw new NodeNotFoundException(location);
        }

        hospitalLocks.putIfAbsent(id, new Object());
        activeReservations.put(id, Collections.synchronizedSet(new HashSet<>()));

        // If the initial hospital already has occupied capacity, we record reservations count alignment
        int occupied = hospital.getOccupiedCapacity();
        for (int i = 0; i < occupied; i++) {
            activeReservations.get(id).add("INITIAL-OCCUPANCY-" + (i + 1));
        }

        hospitalsById.put(id, hospital);
        return hospital;
    }

    /**
     * Retrieves a hospital by unique identifier.
     */
    public Optional<Hospital> getHospital(String hospitalId) {
        if (hospitalId == null || hospitalId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(hospitalsById.get(hospitalId.trim()));
    }

    /**
     * Retrieves a hospital by unique identifier or throws {@link HospitalNotFoundException}.
     */
    public Hospital getHospitalOrThrow(String hospitalId) {
        return getHospital(hospitalId).orElseThrow(() -> new HospitalNotFoundException(hospitalId));
    }

    /**
     * Checks if a hospital is registered.
     */
    public boolean containsHospital(String hospitalId) {
        return hospitalId != null && hospitalsById.containsKey(hospitalId.trim());
    }

    /**
     * Lists all registered hospitals.
     */
    public List<Hospital> getAllHospitals() {
        return List.copyOf(hospitalsById.values());
    }

    /**
     * Lists hospitals matching a specified operational status.
     */
    public List<Hospital> getHospitalsByStatus(HospitalStatus status) {
        Objects.requireNonNull(status, "HospitalStatus must not be null");
        return hospitalsById.values().stream()
                .filter(h -> h.getStatus() == status)
                .toList();
    }

    /**
     * Lists hospitals possessing a specified medical capability.
     */
    public List<Hospital> getHospitalsByCapability(HospitalCapability capability) {
        Objects.requireNonNull(capability, "HospitalCapability must not be null");
        return hospitalsById.values().stream()
                .filter(h -> h.hasCapability(capability))
                .toList();
    }

    /**
     * Lists hospitals that support a specific emergency type.
     */
    public List<Hospital> getHospitalsSupportingEmergencyType(EmergencyType type) {
        Objects.requireNonNull(type, "EmergencyType must not be null");
        return hospitalsById.values().stream()
                .filter(h -> h.supportsEmergencyType(type))
                .toList();
    }

    /**
     * Lists all hospitals currently open and available for admission.
     */
    public List<Hospital> getAvailableHospitals() {
        return hospitalsById.values().stream()
                .filter(Hospital::isAvailableForAdmission)
                .toList();
    }

    /**
     * Atomically reserves one unit of admission capacity at the specified hospital for an emergency case.
     *
     * @param hospitalId Unique hospital identifier.
     * @param caseId     Unique emergency case identifier.
     * @return The updated Hospital instance after reservation.
     * @throws HospitalNotFoundException         if hospital does not exist.
     * @throws HospitalClosedException           if hospital is status CLOSED.
     * @throws HospitalCapacityExceededException if hospital has zero available capacity.
     * @throws DuplicateReservationException     if caseId already holds an active reservation at this hospital.
     */
    public Hospital reserveCapacity(String hospitalId, String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Case ID must not be null or blank");
        }
        String cleanCaseId = caseId.trim();
        Object lock = getLockOrThrow(hospitalId);

        synchronized (lock) {
            Hospital current = getHospitalOrThrow(hospitalId);
            Instant now = clock.instant();

            if (current.isClosed()) {
                throw new HospitalClosedException(hospitalId);
            }

            if (current.getAvailableCapacity() <= 0) {
                throw new HospitalCapacityExceededException(hospitalId);
            }

            Set<String> reservations = activeReservations.get(current.getId());
            if (reservations.contains(cleanCaseId)) {
                throw new DuplicateReservationException(hospitalId, cleanCaseId);
            }

            int newAvailable = current.getAvailableCapacity() - 1;
            Hospital updated = current.withAvailableCapacity(newAvailable, now);
            reservations.add(cleanCaseId);
            hospitalsById.put(current.getId(), updated);
            return updated;
        }
    }

    /**
     * Atomically releases a previously reserved admission unit, restoring available capacity.
     *
     * @param hospitalId Unique hospital identifier.
     * @param caseId     Unique emergency case identifier whose reservation is releasing.
     * @return The updated Hospital instance after release.
     * @throws HospitalNotFoundException     if hospital does not exist.
     * @throws ReservationNotFoundException  if caseId has no active reservation at this hospital.
     */
    public Hospital releaseCapacity(String hospitalId, String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Case ID must not be null or blank");
        }
        String cleanCaseId = caseId.trim();
        Object lock = getLockOrThrow(hospitalId);

        synchronized (lock) {
            Hospital current = getHospitalOrThrow(hospitalId);
            Instant now = clock.instant();

            Set<String> reservations = activeReservations.get(current.getId());
            if (!reservations.contains(cleanCaseId)) {
                throw new ReservationNotFoundException(hospitalId, cleanCaseId);
            }

            int newAvailable = Math.min(current.getTotalCapacity(), current.getAvailableCapacity() + 1);
            Hospital updated = current.withAvailableCapacity(newAvailable, now);
            reservations.remove(cleanCaseId);
            hospitalsById.put(current.getId(), updated);
            return updated;
        }
    }

    /**
     * Updates total bed capacity for a hospital, adjusting available capacity proportionally
     * while guaranteeing existing reservations are preserved.
     *
     * @param hospitalId       Unique hospital identifier.
     * @param newTotalCapacity New total capacity; must be at least the active reservation count.
     * @return The updated Hospital instance.
     */
    public Hospital updateTotalCapacity(String hospitalId, int newTotalCapacity) {
        if (newTotalCapacity < 0) {
            throw new IllegalArgumentException("Total capacity cannot be negative: " + newTotalCapacity);
        }
        Object lock = getLockOrThrow(hospitalId);

        synchronized (lock) {
            Hospital current = getHospitalOrThrow(hospitalId);
            Set<String> reservations = activeReservations.get(current.getId());
            int activeCount = reservations.size();

            if (newTotalCapacity < activeCount) {
                throw new IllegalArgumentException(String.format(
                        "Cannot reduce total capacity to %d: %d active reservations exist",
                        newTotalCapacity, activeCount));
            }

            int newAvailable = newTotalCapacity - activeCount;
            Instant now = clock.instant();
            Hospital updated = current.withCapacity(newTotalCapacity, newAvailable, now);
            hospitalsById.put(current.getId(), updated);
            return updated;
        }
    }

    /**
     * Closes a hospital to new emergency intake. Existing active reservations remain valid
     * and can still be released safely.
     *
     * @param hospitalId Unique hospital identifier.
     * @return The updated Hospital instance.
     */
    public Hospital closeHospital(String hospitalId) {
        Object lock = getLockOrThrow(hospitalId);

        synchronized (lock) {
            Hospital current = getHospitalOrThrow(hospitalId);
            if (current.isClosed()) {
                return current;
            }
            Instant now = clock.instant();
            Hospital updated = current.withStatus(HospitalStatus.CLOSED, now);
            hospitalsById.put(current.getId(), updated);
            return updated;
        }
    }

    /**
     * Re-opens a hospital to emergency admissions. The resulting status will be
     * {@link HospitalStatus#OPEN} if available capacity &gt; 0, or {@link HospitalStatus#BUSY} if capacity is 0.
     *
     * @param hospitalId Unique hospital identifier.
     * @return The updated Hospital instance.
     */
    public Hospital openHospital(String hospitalId) {
        Object lock = getLockOrThrow(hospitalId);

        synchronized (lock) {
            Hospital current = getHospitalOrThrow(hospitalId);
            if (!current.isClosed()) {
                return current;
            }
            Instant now = clock.instant();
            HospitalStatus newStatus = (current.getAvailableCapacity() > 0)
                    ? HospitalStatus.OPEN
                    : HospitalStatus.BUSY;
            Hospital updated = current.withStatus(newStatus, now);
            hospitalsById.put(current.getId(), updated);
            return updated;
        }
    }

    /**
     * Returns an unmodifiable snapshot set of active reservation IDs at a hospital.
     */
    public Set<String> getActiveReservations(String hospitalId) {
        Hospital current = getHospitalOrThrow(hospitalId);
        Set<String> reservations = activeReservations.get(current.getId());
        synchronized (reservations) {
            return Set.copyOf(reservations);
        }
    }

    /**
     * Checks if a case currently holds an active reservation at a hospital.
     */
    public boolean hasActiveReservation(String hospitalId, String caseId) {
        if (!containsHospital(hospitalId) || caseId == null) {
            return false;
        }
        Set<String> reservations = activeReservations.get(hospitalId.trim());
        return reservations != null && reservations.contains(caseId.trim());
    }

    public int getHospitalCount() {
        return hospitalsById.size();
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public Clock getClock() {
        return clock;
    }

    private Object getLockOrThrow(String hospitalId) {
        if (hospitalId == null || hospitalId.trim().isEmpty()) {
            throw new HospitalNotFoundException(hospitalId);
        }
        String cleanId = hospitalId.trim();
        Object lock = hospitalLocks.get(cleanId);
        if (lock == null) {
            throw new HospitalNotFoundException(cleanId);
        }
        return lock;
    }
}
