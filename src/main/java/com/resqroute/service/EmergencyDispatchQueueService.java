package com.resqroute.service;

import com.resqroute.exception.DuplicateQueueEntryException;
import com.resqroute.exception.EmergencyCaseNotFoundException;
import com.resqroute.lifecycle.EmergencyLifecycleRules;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.queue.EmergencyPriorityQueue;
import com.resqroute.scheduling.AgingDispatchPolicy;
import com.resqroute.scheduling.EmergencySchedulingPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Service managing the dispatch-priority queue, scheduling policies, and coordination
 * with emergency case lifecycles.
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li><b>Starvation Prevention:</b> Manages priority order using a configurable
 *       {@link EmergencySchedulingPolicy} combining triage severity and waiting time.</li>
 *   <li><b>Lifecycle Synchronization:</b> Enqueuing a case in {@link EmergencyStatus#REPORTED}
 *       automatically transitions it to {@link EmergencyStatus#WAITING_FOR_DISPATCH}.</li>
 *   <li><b>Stale Case Exclusion:</b> Lazily and proactively skips or removes cases that have been
 *       cancelled or completed externally.</li>
 *   <li><b>Heap-Safe Priority Updates:</b> Revising an emergency's severity updates the underlying
 *       binary heap via safe extraction and reinsertion without in-place mutation.</li>
 *   <li><b>Non-Dispatched Separation:</b> Purely ranks and manages the dispatch backlog; does not
 *       perform ambulance dispatch optimization (reserved for subsequent phases).</li>
 * </ul>
 * </p>
 */
public class EmergencyDispatchQueueService {

    private final EmergencyPriorityQueue priorityQueue;
    private final EmergencyManagementService emergencyService;
    private final EmergencySchedulingPolicy schedulingPolicy;
    private final Clock clock;

    /**
     * Complete constructor with full dependency injection.
     *
     * @param emergencyService Underlying emergency management service; must not be null.
     * @param schedulingPolicy Priority and aging policy; must not be null.
     * @param clock            Clock for deterministic time evaluation; must not be null.
     */
    public EmergencyDispatchQueueService(EmergencyManagementService emergencyService,
                                         EmergencySchedulingPolicy schedulingPolicy,
                                         Clock clock) {
        this.emergencyService = Objects.requireNonNull(emergencyService, "EmergencyManagementService must not be null");
        this.schedulingPolicy = Objects.requireNonNull(schedulingPolicy, "EmergencySchedulingPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.priorityQueue = new EmergencyPriorityQueue(schedulingPolicy, clock);
    }

    /**
     * Constructs an EmergencyDispatchQueueService with specified management service and default policy/clock.
     */
    public EmergencyDispatchQueueService(EmergencyManagementService emergencyService) {
        this(emergencyService, new AgingDispatchPolicy(), emergencyService.getClock());
    }

    /**
     * Constructs an EmergencyDispatchQueueService with specified management service and policy.
     */
    public EmergencyDispatchQueueService(EmergencyManagementService emergencyService,
                                         EmergencySchedulingPolicy schedulingPolicy) {
        this(emergencyService, schedulingPolicy, emergencyService.getClock());
    }

    /**
     * Enqueues an emergency case for dispatch triage.
     *
     * <p>If the case is in {@link EmergencyStatus#REPORTED}, it transitions to
     * {@link EmergencyStatus#WAITING_FOR_DISPATCH}.</p>
     *
     * @param emergencyCase The emergency case to enqueue; must not be null.
     * @return The enqueued emergency case instance.
     * @throws DuplicateQueueEntryException if the case is already active in the queue.
     * @throws IllegalStateException        if the case is terminal or already assigned to an ambulance.
     */
    public synchronized EmergencyCase enqueue(EmergencyCase emergencyCase) {
        Objects.requireNonNull(emergencyCase, "Emergency case must not be null");
        String id = emergencyCase.getId();

        if (priorityQueue.containsCase(id)) {
            throw new DuplicateQueueEntryException(id);
        }

        if (emergencyCase.isTerminal()) {
            throw new IllegalStateException("Cannot enqueue completed or cancelled case: " + id);
        }

        // Ensure case is registered in the management service
        EmergencyCase managedCase = emergencyService.getCase(id).orElseGet(() ->
                emergencyService.createCase(emergencyCase));

        // If reported, transition status to WAITING_FOR_DISPATCH
        if (managedCase.getStatus() == EmergencyStatus.REPORTED) {
            managedCase = emergencyService.updateCaseStatus(id, EmergencyStatus.WAITING_FOR_DISPATCH, "Enqueued for ambulance dispatch");
        } else if (managedCase.getStatus() != EmergencyStatus.WAITING_FOR_DISPATCH) {
            throw new IllegalStateException(String.format(
                    "Cannot enqueue emergency case in status %s (must be REPORTED or WAITING_FOR_DISPATCH)",
                    managedCase.getStatus()));
        }

        priorityQueue.enqueue(managedCase);
        return managedCase;
    }

    /**
     * Convenience method to enqueue a registered emergency case by its unique ID.
     */
    public EmergencyCase enqueue(String caseId) {
        EmergencyCase emergencyCase = emergencyService.getCaseOrThrow(caseId);
        return enqueue(emergencyCase);
    }

    /**
     * Inspects the highest-priority eligible emergency without removing it.
     * Automatically filters out and purges stale (cancelled, completed, or already assigned) entries.
     */
    public synchronized Optional<EmergencyCase> peekNextEmergency() {
        while (!priorityQueue.isEmpty()) {
            Optional<EmergencyCase> topOpt = priorityQueue.peek();
            if (topOpt.isEmpty()) {
                return Optional.empty();
            }

            EmergencyCase top = topOpt.get();
            // Verify up-to-date state from management service
            Optional<EmergencyCase> latestOpt = emergencyService.getCase(top.getId());
            if (latestOpt.isEmpty() || !isEligibleForDispatch(latestOpt.get())) {
                // Stale, cancelled, or completed: purge from queue
                priorityQueue.poll();
                continue;
            }

            return latestOpt;
        }
        return Optional.empty();
    }

    /**
     * Retrieves and dequeues the highest-priority eligible emergency case.
     * Automatically purges stale (cancelled, completed, or already assigned) entries.
     */
    public synchronized Optional<EmergencyCase> dequeueNextEmergency() {
        while (!priorityQueue.isEmpty()) {
            Optional<EmergencyCase> topOpt = priorityQueue.poll();
            if (topOpt.isEmpty()) {
                return Optional.empty();
            }

            EmergencyCase top = topOpt.get();
            Optional<EmergencyCase> latestOpt = emergencyService.getCase(top.getId());
            if (latestOpt.isEmpty() || !isEligibleForDispatch(latestOpt.get())) {
                continue; // Stale item skipped and removed
            }

            return latestOpt;
        }
        return Optional.empty();
    }

    /**
     * Removes an emergency case from the dispatch queue by ID.
     */
    public synchronized Optional<EmergencyCase> remove(String caseId) {
        return priorityQueue.remove(caseId);
    }

    /**
     * Cancels a queued emergency case safely, removing it from the queue and marking it cancelled.
     */
    public synchronized EmergencyCase cancelQueuedEmergency(String caseId, String reason) {
        priorityQueue.remove(caseId);
        return emergencyService.cancelCase(caseId, reason);
    }

    /**
     * Cancels a queued emergency case with default reason.
     */
    public EmergencyCase cancelQueuedEmergency(String caseId) {
        return cancelQueuedEmergency(caseId, "Cancelled from dispatch queue");
    }

    /**
     * Updates the triage severity rating of a queued case, safely adjusting heap position.
     *
     * @param caseId      Identifier of the target case.
     * @param newSeverity The new severity rating.
     * @return The updated EmergencyCase instance.
     * @throws EmergencyCaseNotFoundException if the case is not found.
     */
    public synchronized EmergencyCase updateSeverity(String caseId, EmergencySeverity newSeverity) {
        Objects.requireNonNull(newSeverity, "EmergencySeverity must not be null");

        if (!priorityQueue.containsCase(caseId)) {
            throw new EmergencyCaseNotFoundException(caseId);
        }

        // Update in management service
        EmergencyCase updated = emergencyService.updateCaseSeverity(caseId, newSeverity);

        // Safe heap re-insertion preserving binary heap invariant
        priorityQueue.update(updated);
        return updated;
    }

    /**
     * Rebuilds the queue heap based on the current evaluation time.
     */
    public synchronized void rebuildQueue() {
        purgeIneligibleCases();
        priorityQueue.rebuild(clock.instant());
    }

    /**
     * Returns an unmodifiable list of eligible pending emergencies in priority order.
     */
    public synchronized List<EmergencyCase> getPendingEmergencies() {
        purgeIneligibleCases();
        return priorityQueue.getPendingEmergencies();
    }

    /**
     * Purges cancelled, completed, or already-assigned cases from the queue.
     */
    public synchronized void purgeIneligibleCases() {
        List<String> staleIds = new ArrayList<>();
        for (EmergencyCase queued : priorityQueue.getPendingEmergencies()) {
            Optional<EmergencyCase> current = emergencyService.getCase(queued.getId());
            if (current.isEmpty() || !isEligibleForDispatch(current.get())) {
                staleIds.add(queued.getId());
            }
        }
        for (String id : staleIds) {
            priorityQueue.remove(id);
        }
    }

    private boolean isEligibleForDispatch(EmergencyCase emergencyCase) {
        return emergencyCase.getStatus() == EmergencyStatus.WAITING_FOR_DISPATCH
                && !emergencyCase.hasAssignedAmbulance();
    }

    public synchronized int size() {
        purgeIneligibleCases();
        return priorityQueue.size();
    }

    public synchronized boolean isEmpty() {
        return size() == 0;
    }

    public synchronized boolean containsCase(String caseId) {
        return priorityQueue.containsCase(caseId);
    }

    public synchronized Optional<EmergencyCase> getCase(String caseId) {
        return priorityQueue.getCase(caseId);
    }

    public EmergencySchedulingPolicy getSchedulingPolicy() {
        return schedulingPolicy;
    }

    public EmergencyManagementService getEmergencyService() {
        return emergencyService;
    }

    public Clock getClock() {
        return clock;
    }
}
