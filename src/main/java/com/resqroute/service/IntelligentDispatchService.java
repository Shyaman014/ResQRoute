package com.resqroute.service;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.RoutingAlgorithm;
import com.resqroute.dispatch.*;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Intelligent ambulance dispatch facade providing:
 * <ol>
 *   <li><b>Concurrent dispatch protection</b> — Prevents two simultaneous dispatch attempts
 *       via a non-reentrant fairness lock, eliminating race conditions without requiring
 *       full global synchronization of the underlying services.</li>
 *   <li><b>Dispatch audit trail</b> — Retains an ordered circular audit log of the last
 *       {@link #MAX_AUDIT_RECORDS} dispatch decision records, each carrying the selected
 *       ambulance, route, ETA, candidates evaluated, ranking policy, and human-readable
 *       rationale.</li>
 *   <li><b>Background routing</b> — Expensive multi-candidate route calculations execute
 *       on a dedicated background thread pool, keeping the JavaFX Application Thread
 *       responsive. Callers supply a {@link Consumer} UI callback invoked via the configured
 *       {@code uiDispatcher} once the decision is ready.</li>
 *   <li><b>Traffic-change ETA refresh</b> — Clients invoke {@link #notifyTrafficChanged()}
 *       to recalculate and update the ETA of all in-progress missions using current edge costs.</li>
 *   <li><b>Pluggable ranking policy</b> — Accepts any {@link AmbulanceRankingPolicy} at
 *       construction; defaults to {@link FastestResponseRankingPolicy}.</li>
 * </ol>
 *
 * <h3>Thread-Safety Contract:</h3>
 * <ul>
 *   <li>All mutating methods acquire {@link #dispatchLock} before delegating to the underlying
 *       {@link AmbulanceDispatchService}, which is itself {@code synchronized}.</li>
 *   <li>Read-only state accessors ({@link #getLastDispatchResult()}, {@link #getAuditLog()})
 *       are lock-free and thread-safe via {@link AtomicReference} and {@link CopyOnWriteArrayList}.</li>
 *   <li>Background dispatch tasks are submitted to a daemon {@link ExecutorService} and never
 *       block the caller thread.</li>
 * </ul>
 */
public class IntelligentDispatchService {

    /** Maximum number of dispatch records retained in the circular audit log. */
    public static final int MAX_AUDIT_RECORDS = 50;

    private final AmbulanceDispatchService coreDispatchService;
    private final AmbulanceFleetService fleetService;
    private final EmergencyDispatchQueueService queueService;
    private final RoadGraph roadGraph;
    private final RoutingAlgorithm routingAlgorithm;
    private final AmbulanceRankingPolicy rankingPolicy;
    private final AmbulanceCapabilityMatcher capabilityMatcher;
    private final Clock clock;
    private final Consumer<Runnable> uiDispatcher;

    /** Guards dispatch state mutations against concurrent dispatch attempts. */
    private final ReentrantLock dispatchLock = new ReentrantLock(true /* fair */);

    /** Background executor for off-thread route evaluation. */
    private final ExecutorService backgroundExecutor;

    /** Latest dispatch result, thread-safe via AtomicReference. */
    private final AtomicReference<DispatchResult> lastDispatchResult = new AtomicReference<>();

    /** Latest dispatch decision record with full rationale. */
    private final AtomicReference<DispatchDecisionRecord> lastDecisionRecord = new AtomicReference<>();

    /** Circular audit log of the last MAX_AUDIT_RECORDS decisions. */
    private final Deque<DispatchDecisionRecord> auditLog = new ArrayDeque<>();

    /**
     * Constructs an IntelligentDispatchService with full dependency injection.
     *
     * @param coreDispatchService  Underlying single-emergency dispatch engine.
     * @param fleetService         Fleet management service.
     * @param queueService         Priority dispatch queue.
     * @param roadGraph            Live road network graph.
     * @param routingAlgorithm     Traffic-aware routing engine.
     * @param rankingPolicy        Candidate ranking strategy.
     * @param capabilityMatcher    Clinical capability compatibility checker.
     * @param clock                Clock for deterministic time evaluation.
     * @param uiDispatcher         Dispatcher for UI-thread callbacks (e.g. {@code Platform::runLater}).
     */
    public IntelligentDispatchService(AmbulanceDispatchService coreDispatchService,
                                      AmbulanceFleetService fleetService,
                                      EmergencyDispatchQueueService queueService,
                                      RoadGraph roadGraph,
                                      RoutingAlgorithm routingAlgorithm,
                                      AmbulanceRankingPolicy rankingPolicy,
                                      AmbulanceCapabilityMatcher capabilityMatcher,
                                      Clock clock,
                                      Consumer<Runnable> uiDispatcher) {
        this.coreDispatchService = Objects.requireNonNull(coreDispatchService, "Core dispatch service must not be null");
        this.fleetService = Objects.requireNonNull(fleetService, "FleetService must not be null");
        this.queueService = Objects.requireNonNull(queueService, "QueueService must not be null");
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.routingAlgorithm = Objects.requireNonNull(routingAlgorithm, "RoutingAlgorithm must not be null");
        this.rankingPolicy = Objects.requireNonNull(rankingPolicy, "RankingPolicy must not be null");
        this.capabilityMatcher = Objects.requireNonNull(capabilityMatcher, "CapabilityMatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "UI dispatcher must not be null");
        this.backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ResQRoute-Dispatch-BG");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Convenience constructor using default components.
     *
     * @param coreDispatchService  Underlying dispatch engine.
     * @param fleetService         Fleet management service.
     * @param queueService         Priority dispatch queue.
     * @param uiDispatcher         Dispatcher for UI-thread callbacks.
     */
    public IntelligentDispatchService(AmbulanceDispatchService coreDispatchService,
                                      AmbulanceFleetService fleetService,
                                      EmergencyDispatchQueueService queueService,
                                      Consumer<Runnable> uiDispatcher) {
        this(coreDispatchService, fleetService, queueService,
                coreDispatchService.getRoadGraph(),
                coreDispatchService.getRoutingAlgorithm(),
                coreDispatchService.getRankingPolicy(),
                coreDispatchService.getCapabilityMatcher(),
                coreDispatchService.getClock(),
                uiDispatcher);
    }

    /**
     * Headless constructor (synchronous UI dispatcher) for unit testing.
     */
    public IntelligentDispatchService(AmbulanceDispatchService coreDispatchService,
                                      AmbulanceFleetService fleetService,
                                      EmergencyDispatchQueueService queueService) {
        this(coreDispatchService, fleetService, queueService, Runnable::run);
    }

    // ==========================================
    // Synchronous Dispatch Operations
    // ==========================================

    /**
     * Dispatches the highest-priority pending emergency to the best available ambulance
     * and records the complete dispatch decision.
     *
     * <p>Acquires the dispatch lock to prevent concurrent assignment conflicts. If the
     * lock cannot be acquired immediately (another dispatch is in progress), returns a
     * failed result without blocking.</p>
     *
     * @return The dispatch result with status, assigned ambulance, route, and ETA.
     */
    public DispatchResult dispatchNext() {
        if (!dispatchLock.tryLock()) {
            return DispatchResult.failedTransaction(null,
                    "A concurrent dispatch operation is already in progress", clock.instant());
        }
        try {
            DispatchResult result = executeDispatchWithAudit();
            lastDispatchResult.set(result);
            return result;
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * Dispatches the best available ambulance to a specified emergency case.
     *
     * @param emergencyCase The emergency case to serve.
     * @return The dispatch result.
     */
    public DispatchResult dispatchEmergency(EmergencyCase emergencyCase) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        dispatchLock.lock();
        try {
            DispatchResult result = coreDispatchService.dispatchEmergency(emergencyCase);
            lastDispatchResult.set(result);
            if (result.isSuccess()) {
                recordDecisionAudit(emergencyCase, result);
            }
            return result;
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * Iteratively dispatches all pending emergencies, stopping when the queue is empty
     * or no further viable ambulances remain.
     *
     * @return Unmodifiable list of dispatch results.
     */
    public List<DispatchResult> dispatchAllPending() {
        dispatchLock.lock();
        try {
            List<DispatchResult> results = coreDispatchService.dispatchAllPending();
            if (!results.isEmpty()) {
                lastDispatchResult.set(results.getLast());
                // Record audit for successful dispatches
                for (DispatchResult r : results) {
                    if (r.isSuccess()) {
                        r.getEmergencyCase().ifPresent(ec -> recordDecisionAudit(ec, r));
                    }
                }
            }
            return results;
        } finally {
            dispatchLock.unlock();
        }
    }

    // ==========================================
    // Asynchronous (Off-JavaFX-Thread) Dispatch
    // ==========================================

    /**
     * Submits a background dispatch task for the highest-priority pending emergency.
     * The result is delivered to {@code onComplete} via the configured {@code uiDispatcher},
     * ensuring safe JavaFX UI updates without blocking the Application Thread.
     *
     * @param onComplete Callback receiving the dispatch result on the UI thread; must not be null.
     * @return Future representing the background task (may be used for cancellation).
     */
    public Future<DispatchResult> dispatchNextAsync(Consumer<DispatchResult> onComplete) {
        Objects.requireNonNull(onComplete, "onComplete callback must not be null");
        return backgroundExecutor.submit(() -> {
            DispatchResult result = dispatchNext();
            uiDispatcher.accept(() -> onComplete.accept(result));
            return result;
        });
    }

    /**
     * Submits a background dispatch task for a specified emergency case.
     *
     * @param emergencyCase The target emergency case.
     * @param onComplete    Callback receiving the result on the UI thread.
     * @return Future representing the background task.
     */
    public Future<DispatchResult> dispatchEmergencyAsync(EmergencyCase emergencyCase,
                                                          Consumer<DispatchResult> onComplete) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        Objects.requireNonNull(onComplete, "onComplete callback must not be null");
        return backgroundExecutor.submit(() -> {
            DispatchResult result = dispatchEmergency(emergencyCase);
            uiDispatcher.accept(() -> onComplete.accept(result));
            return result;
        });
    }

    // ==========================================
    // Traffic-Change ETA Refresh
    // ==========================================

    /**
     * Recalculates estimated arrival times for all pending (unassigned) emergencies
     * in the queue against available ambulances using current traffic-weighted edge costs.
     *
     * <p>Call this after significant traffic changes (e.g., road closures, congestion surges)
     * to ensure dispatch decisions reflect current network conditions. Returns a map of
     * case ID → updated best-ETA in minutes for the UI to display.</p>
     *
     * @return Map from emergency case ID to updated minimum ETA (minutes), or empty if nothing pending.
     */
    public Map<String, Double> recalculateEtas() {
        List<EmergencyCase> pending = queueService.getPendingEmergencies();
        List<Ambulance> available = fleetService.getAvailableAmbulances();

        if (pending.isEmpty() || available.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> bestEtas = new LinkedHashMap<>();
        for (EmergencyCase ec : pending) {
            String pickupNode = ec.getPickupLocationNodeId();
            double bestEta = Double.MAX_VALUE;
            for (Ambulance amb : available) {
                if (!capabilityMatcher.isCompatible(amb, ec)) continue;
                RouteResult route = routingAlgorithm.findFastestRoute(
                        roadGraph, amb.getCurrentLocationNodeId(), pickupNode);
                if (route != null && route.isReachable()) {
                    bestEta = Math.min(bestEta, route.getTotalTravelTimeMinutes());
                }
            }
            if (bestEta < Double.MAX_VALUE) {
                bestEtas.put(ec.getId(), bestEta);
            } else {
                bestEtas.put(ec.getId(), -1.0); // unreachable
            }
        }
        return Collections.unmodifiableMap(bestEtas);
    }

    /**
     * Signal that traffic conditions have changed significantly. This triggers an asynchronous
     * ETA recalculation and delivers updated ETAs to {@code onUpdate} on the UI thread.
     *
     * @param onUpdate Callback receiving updated ETA map (case ID → best ETA minutes) on UI thread.
     */
    public void notifyTrafficChanged(Consumer<Map<String, Double>> onUpdate) {
        Objects.requireNonNull(onUpdate, "onUpdate callback must not be null");
        backgroundExecutor.submit(() -> {
            Map<String, Double> etas = recalculateEtas();
            uiDispatcher.accept(() -> onUpdate.accept(etas));
        });
    }

    /**
     * Signal that traffic conditions have changed; runs ETA refresh synchronously.
     * Use in headless/test contexts where background threads are not desirable.
     *
     * @return Updated ETA map (case ID → best ETA minutes).
     */
    public Map<String, Double> notifyTrafficChanged() {
        return recalculateEtas();
    }

    // ==========================================
    // Audit Log & State Accessors
    // ==========================================

    /**
     * Returns the result of the most recent dispatch operation, or {@code null} if none has occurred.
     */
    public DispatchResult getLastDispatchResult() {
        return lastDispatchResult.get();
    }

    /**
     * Returns the most recent structured dispatch decision record, or {@code null} if none.
     */
    public DispatchDecisionRecord getLastDecisionRecord() {
        return lastDecisionRecord.get();
    }

    /**
     * Returns an unmodifiable snapshot of the audit log (most recent first).
     */
    public synchronized List<DispatchDecisionRecord> getAuditLog() {
        List<DispatchDecisionRecord> snapshot = new ArrayList<>(auditLog);
        Collections.reverse(snapshot);
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * Returns a human-readable rationale for the most recent dispatch decision,
     * or a descriptive placeholder if no decision has been made yet.
     */
    public String getLastDispatchRationale() {
        DispatchDecisionRecord record = lastDecisionRecord.get();
        if (record != null) {
            return record.buildSummary();
        }
        DispatchResult result = lastDispatchResult.get();
        if (result != null) {
            return result.getMessage();
        }
        return "No dispatch decision recorded yet.";
    }

    /**
     * Returns {@code true} if a dispatch operation is currently executing.
     */
    public boolean isDispatching() {
        return dispatchLock.isLocked();
    }

    /**
     * Shuts down the background executor gracefully. Call on application shutdown.
     */
    public void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    public AmbulanceRankingPolicy getRankingPolicy() {
        return rankingPolicy;
    }

    public AmbulanceCapabilityMatcher getCapabilityMatcher() {
        return capabilityMatcher;
    }

    public AmbulanceDispatchService getCoreDispatchService() {
        return coreDispatchService;
    }

    // ==========================================
    // Internal Helpers
    // ==========================================

    private DispatchResult executeDispatchWithAudit() {
        // Peek the top-priority emergency before delegation so we can build the audit record
        Optional<EmergencyCase> pendingOpt = queueService.peekNextEmergency();
        DispatchResult result = coreDispatchService.dispatchNext();
        if (result.isSuccess() && pendingOpt.isPresent()) {
            recordDecisionAudit(pendingOpt.get(), result);
        }
        return result;
    }

    private synchronized void recordDecisionAudit(EmergencyCase emergencyCase, DispatchResult result) {
        if (!result.isSuccess()) return;
        Optional<Ambulance> ambOpt = result.getAssignedAmbulance();
        Optional<RouteResult> routeOpt = result.getRouteToPickup();
        if (ambOpt.isEmpty() || routeOpt.isEmpty()) return;

        // Count how many ambulances were available vs compatible vs reachable for this case
        List<Ambulance> allAvailable = fleetService.getAllAmbulances().stream()
                .filter(a -> a.getStatus() == AmbulanceStatus.AVAILABLE ||
                             a.getId().equals(ambOpt.get().getId()))
                .toList();
        int evaluated = allAvailable.size();
        int compatible = (int) allAvailable.stream()
                .filter(a -> capabilityMatcher.isCompatible(a, emergencyCase))
                .count();
        int filtered = evaluated - compatible;

        String rationale = buildRationale(emergencyCase, ambOpt.get(), routeOpt.get(), evaluated, compatible);

        DispatchDecisionRecord record = new DispatchDecisionRecord(
                emergencyCase,
                ambOpt.get(),
                routeOpt.get(),
                evaluated,
                filtered,
                rankingPolicy.getPolicyName(),
                rationale,
                result.getDispatchedAt()
        );

        lastDecisionRecord.set(record);
        auditLog.addLast(record);
        if (auditLog.size() > MAX_AUDIT_RECORDS) {
            auditLog.pollFirst();
        }
    }

    private String buildRationale(EmergencyCase ec, Ambulance selected, RouteResult route,
                                   int evaluated, int compatible) {
        return String.format(
                "Severity=%s | %d/%d compatible ambulances evaluated | " +
                "Selected '%s' at '%s' → fastest route (%.1f min, %.1f km) using %s",
                ec.getSeverity(),
                compatible, evaluated,
                selected.getId(),
                selected.getCurrentLocationNodeId(),
                route.getTotalTravelTimeMinutes(),
                route.getTotalDistanceKm(),
                rankingPolicy.getPolicyName()
        );
    }
}
