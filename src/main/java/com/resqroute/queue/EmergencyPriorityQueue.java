package com.resqroute.queue;

import com.resqroute.exception.DuplicateQueueEntryException;
import com.resqroute.model.EmergencyCase;
import com.resqroute.scheduling.AgingDispatchPolicy;
import com.resqroute.scheduling.EmergencySchedulingPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Dedicated dispatch priority queue backed by Java's {@link PriorityQueue}.
 *
 * <p>Enforces strict queue integrity and scheduling guarantees:
 * <ul>
 *   <li><b>Starvation Prevention &amp; Triage:</b> Prioritizes emergencies using an injected
 *       {@link EmergencySchedulingPolicy} evaluating severity, waiting time, creation timestamp, and case ID.</li>
 *   <li><b>Heap Corruption Protection:</b> Elements inside the binary heap are never mutated in-place.
 *       Modifications (such as severity changes or re-queuing) are executed safely via atomic removal and
 *       re-insertion, preserving the binary heap invariant.</li>
 *   <li><b>Duplicate Prevention:</b> Rejects duplicate active entries using an indexed hash lookup ($O(1)$).</li>
 *   <li><b>Deterministic Time:</b> Uses an injectable {@link Clock} for reproducible evaluation.</li>
 * </ul>
 * </p>
 *
 * <h3>Complexity Guarantees:</h3>
 * <table border="1">
 *   <caption>Time and Space Complexity</caption>
 *   <tr><th>Operation</th><th>Time Complexity</th><th>Space Complexity</th><th>Description</th></tr>
 *   <tr><td>{@code enqueue}</td><td>O(log N)</td><td>O(1)</td><td>Heap sift-up + hash map put</td></tr>
 *   <tr><td>{@code peek}</td><td>O(1)*</td><td>O(1)</td><td>Root inspection (O(N) if reheapify triggered)</td></tr>
 *   <tr><td>{@code poll}</td><td>O(log N)*</td><td>O(1)</td><td>Heap extraction sift-down + hash map removal</td></tr>
 *   <tr><td>{@code remove}</td><td>O(N)</td><td>O(1)</td><td>Linear scan removal from binary heap + hash map removal</td></tr>
 *   <tr><td>{@code update}</td><td>O(N)</td><td>O(1)</td><td>Safe removal + re-insertion preserving heap invariant</td></tr>
 *   <tr><td>{@code rebuild}</td><td>O(N)</td><td>O(N)</td><td>Floyd's bottom-up linear heap reconstruction</td></tr>
 *   <tr><td>{@code getPendingEmergencies}</td><td>O(N log N)</td><td>O(N)</td><td>Sorted defensive snapshot copy</td></tr>
 * </table>
 */
public class EmergencyPriorityQueue {

    private final EmergencySchedulingPolicy policy;
    private final Clock clock;
    private final Map<String, EmergencyCase> indexedCases;
    private PriorityQueue<EmergencyCase> priorityQueue;

    /**
     * Constructs an EmergencyPriorityQueue with custom policy and clock.
     *
     * @param policy Dispatch scheduling strategy; must not be null.
     * @param clock  Clock for evaluating dynamic waiting time; must not be null.
     */
    public EmergencyPriorityQueue(EmergencySchedulingPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "EmergencySchedulingPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.indexedCases = new HashMap<>();
        this.priorityQueue = new PriorityQueue<>((c1, c2) -> policy.compare(c1, c2, clock.instant()));
    }

    /**
     * Constructs an EmergencyPriorityQueue with default aging policy and UTC clock.
     */
    public EmergencyPriorityQueue() {
        this(new AgingDispatchPolicy(), Clock.systemUTC());
    }

    /**
     * Constructs an EmergencyPriorityQueue with default aging policy and specified clock.
     */
    public EmergencyPriorityQueue(Clock clock) {
        this(new AgingDispatchPolicy(), clock);
    }

    /**
     * Constructs an EmergencyPriorityQueue with custom policy and UTC clock.
     */
    public EmergencyPriorityQueue(EmergencySchedulingPolicy policy) {
        this(policy, Clock.systemUTC());
    }

    /**
     * Enqueues an emergency case into the priority queue.
     *
     * <p>Complexity: O(log N) time, O(1) space.</p>
     *
     * @param emergencyCase The emergency case to enqueue; must not be null.
     * @throws DuplicateQueueEntryException if the case ID is already active in the queue.
     */
    public synchronized void enqueue(EmergencyCase emergencyCase) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        String id = emergencyCase.getId();

        if (indexedCases.containsKey(id)) {
            throw new DuplicateQueueEntryException(id);
        }

        rebuild(clock.instant());
        indexedCases.put(id, emergencyCase);
        priorityQueue.offer(emergencyCase);
    }

    /**
     * Inspects the highest-priority emergency at the current clock time without removing it.
     *
     * <p>Complexity: O(N) if aging-induced reheapify is needed, O(1) otherwise.</p>
     *
     * @return Optional containing the top emergency, or empty if queue is empty.
     */
    public synchronized Optional<EmergencyCase> peek() {
        if (indexedCases.isEmpty()) {
            return Optional.empty();
        }
        rebuild(clock.instant());
        return Optional.ofNullable(priorityQueue.peek());
    }

    /**
     * Retrieves and removes the highest-priority emergency at the current clock time.
     *
     * <p>Complexity: O(log N) heap extraction (sift-down) + O(1) map removal.</p>
     *
     * @return Optional containing the dequeued emergency, or empty if queue is empty.
     */
    public synchronized Optional<EmergencyCase> poll() {
        if (indexedCases.isEmpty()) {
            return Optional.empty();
        }
        rebuild(clock.instant());
        EmergencyCase top = priorityQueue.poll();
        if (top != null) {
            indexedCases.remove(top.getId());
            return Optional.of(top);
        }
        return Optional.empty();
    }

    /**
     * Removes an emergency from the queue by its unique case identifier.
     *
     * <p>Complexity: O(N) time to scan and delete from heap, O(1) space.</p>
     *
     * @param caseId Unique emergency case identifier.
     * @return Optional containing the removed case, or empty if not present.
     */
    public synchronized Optional<EmergencyCase> remove(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanId = caseId.trim();
        EmergencyCase removed = indexedCases.remove(cleanId);
        if (removed != null) {
            priorityQueue.remove(removed);
            return Optional.of(removed);
        }
        return Optional.empty();
    }

    /**
     * Safely updates a queued case (e.g. following a severity revision), preserving heap ordering.
     *
     * <p>Complexity: O(N) time (safe removal and re-insertion), O(1) space.</p>
     *
     * @param updatedCase The updated emergency case; must have the same ID as an existing queued case.
     * @return true if updated, false if the case was not found in the queue.
     */
    public synchronized boolean update(EmergencyCase updatedCase) {
        Objects.requireNonNull(updatedCase, "Updated emergency case must not be null");
        String id = updatedCase.getId();

        EmergencyCase existing = indexedCases.get(id);
        if (existing == null) {
            return false;
        }

        // Remove old entry from both index and heap
        indexedCases.remove(id);
        priorityQueue.remove(existing);

        // Reinsert updated instance
        indexedCases.put(id, updatedCase);
        priorityQueue.offer(updatedCase);
        rebuild(clock.instant());
        return true;
    }

    /**
     * Rebuilds the underlying PriorityQueue heap based on the specified evaluation timestamp.
     * Uses Floyd's bottom-up heap construction algorithm in O(N) time.
     *
     * @param evaluationTime Timestamp at which to re-calculate all dynamic priority scores.
     */
    public synchronized void rebuild(Instant evaluationTime) {
        Instant evalTime = evaluationTime != null ? evaluationTime : clock.instant();
        PriorityQueue<EmergencyCase> freshQueue = new PriorityQueue<>(
                Math.max(11, indexedCases.size()),
                (c1, c2) -> policy.compare(c1, c2, evalTime)
        );
        freshQueue.addAll(indexedCases.values());
        this.priorityQueue = freshQueue;
    }

    /**
     * Rebuilds queue using current clock instant.
     */
    public synchronized void rebuild() {
        rebuild(clock.instant());
    }

    /**
     * Returns an unmodifiable list of all pending emergencies, sorted in descending priority order.
     *
     * <p>Complexity: O(N log N) time, O(N) space.</p>
     */
    public synchronized List<EmergencyCase> getPendingEmergencies() {
        Instant now = clock.instant();
        List<EmergencyCase> list = new ArrayList<>(indexedCases.values());
        list.sort((c1, c2) -> policy.compare(c1, c2, now));
        return Collections.unmodifiableList(list);
    }

    /**
     * Checks if a case is currently active in the queue.
     *
     * <p>Complexity: O(1) time.</p>
     */
    public synchronized boolean containsCase(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return false;
        }
        return indexedCases.containsKey(caseId.trim());
    }

    /**
     * Retrieves a queued emergency by ID without removing it.
     *
     * <p>Complexity: O(1) time.</p>
     */
    public synchronized Optional<EmergencyCase> getCase(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(indexedCases.get(caseId.trim()));
    }

    /**
     * Returns the total count of emergencies currently in the queue.
     *
     * <p>Complexity: O(1) time.</p>
     */
    public synchronized int size() {
        return indexedCases.size();
    }

    /**
     * Checks if the queue is empty.
     *
     * <p>Complexity: O(1) time.</p>
     */
    public synchronized boolean isEmpty() {
        return indexedCases.isEmpty();
    }

    /**
     * Clears all cases from the queue.
     */
    public synchronized void clear() {
        indexedCases.clear();
        priorityQueue.clear();
    }

    public EmergencySchedulingPolicy getPolicy() {
        return policy;
    }

    public Clock getClock() {
        return clock;
    }
}
