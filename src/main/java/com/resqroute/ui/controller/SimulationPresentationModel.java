package com.resqroute.ui.controller;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.algorithm.RoutingAlgorithm;
import com.resqroute.algorithm.YenKShortestPathsAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.Hospital;
import com.resqroute.model.RoadIncident;
import com.resqroute.model.RouteResult;
import com.resqroute.service.RoadIncidentService;
import com.resqroute.simulation.engine.CitySimulationEngine;
import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.metrics.SimulationMetrics;
import com.resqroute.simulation.model.AmbulanceMovementTelemetry;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.ui.model.UiMessage;
import com.resqroute.ui.model.UiSimulationState;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Presentation model and orchestration controller for the ResQRoute simulation.
 *
 * <p>Bridges JavaFX user interface components with the underlying {@link CitySimulationEngine}.
 * Keeps simulation advancement separate from the JavaFX UI thread via configurable background execution,
 * while safely dispatching state notifications through a pluggable UI thread dispatcher.</p>
 */
public class SimulationPresentationModel {

    private final Consumer<Runnable> uiThreadDispatcher;
    private final List<Consumer<SimulationPresentationModel>> stateListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UiMessage>> messageListeners = new CopyOnWriteArrayList<>();

    private CityScenario currentScenario;
    private CitySimulationEngine engine;
    private UiSimulationState uiState = UiSimulationState.UNINITIALIZED;
    private UiMessage latestMessage = UiMessage.info("System ready. Initialize a city scenario to begin.");

    // Execution configuration
    private Duration defaultStepDuration = Duration.ofMinutes(1);
    private long autoRunIntervalMs = 800; // milliseconds between automatic simulation steps
    private long randomSeed = 42L;

    // Background auto-runner
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> autoRunTask;

    /**
     * Constructs a presentation model using synchronous immediate dispatch (ideal for headless unit testing).
     */
    public SimulationPresentationModel() {
        this(Runnable::run);
    }

    /**
     * Constructs a presentation model with a customized UI thread dispatcher (e.g., {@code Platform::runLater}).
     *
     * @param uiThreadDispatcher Dispatcher for executing callbacks on the UI thread; must not be null.
     */
    public SimulationPresentationModel(Consumer<Runnable> uiThreadDispatcher) {
        this.uiThreadDispatcher = Objects.requireNonNull(uiThreadDispatcher, "uiThreadDispatcher must not be null");
    }

    // ==========================================
    // Lifecycle & Scenario Controls
    // ==========================================

    /**
     * Initializes the standard default Metropolis scenario.
     */
    public synchronized void initializeDefaultScenario() {
        try {
            stopAutoRunInternal();
            CityScenario scenario = CityScenario.createDefaultMetropolis(randomSeed);
            initializeScenario(scenario);
            publishMessage(UiMessage.success("Default Metropolis scenario initialized successfully with seed " + randomSeed));
        } catch (Exception ex) {
            publishMessage(UiMessage.error("Failed to initialize default scenario: " + ex.getMessage()));
        }
    }

    /**
     * Initializes the simulation engine with a specified scenario.
     */
    public synchronized void initializeScenario(CityScenario scenario) {
        Objects.requireNonNull(scenario, "Scenario must not be null");
        stopAutoRunInternal();

        this.currentScenario = scenario;
        this.engine = new CitySimulationEngine(scenario);
        this.uiState = UiSimulationState.IDLE;

        notifyStateChanged();
    }

    /**
     * Resets the active simulation back to T = 0.
     */
    public synchronized void reset() {
        stopAutoRunInternal();
        if (engine != null) {
            engine.reset();
            this.uiState = UiSimulationState.IDLE;
            publishMessage(UiMessage.info("Simulation reset to initial state (T = 0)."));
            notifyStateChanged();
        } else {
            publishMessage(UiMessage.warning("No active scenario to reset."));
        }
    }

    /**
     * Advances the simulation by a discrete time delta.
     */
    public synchronized void step(Duration timeDelta) {
        if (engine == null) {
            publishMessage(UiMessage.warning("Initialize a scenario before advancing simulation."));
            return;
        }

        if (isCompleted()) {
            publishMessage(UiMessage.info("Simulation reached configured duration limit."));
            this.uiState = UiSimulationState.COMPLETED;
            notifyStateChanged();
            return;
        }

        try {
            engine.step(timeDelta);
            if (isCompleted()) {
                stopAutoRunInternal();
                this.uiState = UiSimulationState.COMPLETED;
                publishMessage(UiMessage.success("Simulation completed scenario duration (" + formatDuration(engine.getElapsedSimulationTime()) + ")."));
            } else if (uiState != UiSimulationState.RUNNING) {
                this.uiState = UiSimulationState.IDLE;
            }
            notifyStateChanged();
        } catch (Exception ex) {
            publishMessage(UiMessage.error("Simulation step error: " + ex.getMessage()));
        }
    }

    /**
     * Advances the simulation by the configured default time step.
     */
    public void stepDefault() {
        Duration step = (currentScenario != null) ? currentScenario.getTimeStep() : defaultStepDuration;
        step(step);
    }

    /**
     * Starts continuous auto-advancement of the simulation in a background thread.
     */
    public synchronized void startAutoRun() {
        if (engine == null) {
            publishMessage(UiMessage.warning("Initialize a scenario before running simulation."));
            return;
        }

        if (isCompleted()) {
            publishMessage(UiMessage.info("Simulation has already completed. Reset to run again."));
            return;
        }

        if (engine.isPaused()) {
            engine.resume();
        }

        this.uiState = UiSimulationState.RUNNING;
        notifyStateChanged();
        publishMessage(UiMessage.info("Simulation auto-run started."));

        ensureExecutorService();
        if (autoRunTask != null && !autoRunTask.isDone()) {
            autoRunTask.cancel(false);
        }

        autoRunTask = executorService.scheduleWithFixedDelay(() -> {
            try {
                synchronized (SimulationPresentationModel.this) {
                    if (uiState != UiSimulationState.RUNNING) {
                        return;
                    }
                    Duration stepSize = (currentScenario != null) ? currentScenario.getTimeStep() : defaultStepDuration;
                    step(stepSize);
                }
            } catch (Exception ex) {
                publishMessage(UiMessage.error("Background runner encountered error: " + ex.getMessage()));
                pause();
            }
        }, 0, autoRunIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Pauses the simulation.
     */
    public synchronized void pause() {
        if (engine == null) return;
        engine.pause();
        stopAutoRunInternal();
        this.uiState = UiSimulationState.PAUSED;
        notifyStateChanged();
        publishMessage(UiMessage.info("Simulation paused."));
    }

    /**
     * Resumes the paused simulation.
     */
    public synchronized void resume() {
        if (engine == null) return;
        engine.resume();
        startAutoRun();
    }

    /**
     * Toggles between running and paused states.
     */
    public synchronized void togglePlayPause() {
        if (uiState == UiSimulationState.RUNNING) {
            pause();
        } else {
            startAutoRun();
        }
    }

    private void stopAutoRunInternal() {
        if (autoRunTask != null) {
            autoRunTask.cancel(false);
            autoRunTask = null;
        }
    }

    /**
     * Cleans up background threads upon application shutdown.
     */
    public synchronized void shutdown() {
        stopAutoRunInternal();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void ensureExecutorService() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ResQRoute-Sim-Runner");
                t.setDaemon(true);
                return t;
            });
        }
    }

    // ==========================================
    // State Accessors
    // ==========================================

    public Optional<CitySimulationEngine> getEngine() {
        return Optional.ofNullable(engine);
    }

    public Optional<CityScenario> getScenario() {
        return Optional.ofNullable(currentScenario);
    }

    public UiSimulationState getUiState() {
        return uiState;
    }

    public boolean isInitialized() {
        return engine != null;
    }

    public boolean isRunning() {
        return uiState == UiSimulationState.RUNNING;
    }

    public boolean isCompleted() {
        if (engine == null || currentScenario == null) return false;
        return engine.getElapsedSimulationTime().compareTo(currentScenario.getSimulationDuration()) >= 0;
    }

    public Duration getElapsedDuration() {
        return engine != null ? engine.getElapsedSimulationTime() : Duration.ZERO;
    }

    public Instant getCurrentInstant() {
        return engine != null ? engine.getCurrentInstant() : (currentScenario != null ? currentScenario.getBaseTime() : Instant.EPOCH);
    }

    public String getFormattedElapsedTime() {
        return formatDuration(getElapsedDuration());
    }

    public List<Ambulance> getAmbulances() {
        return engine != null ? engine.getFleetService().getAllAmbulances() : Collections.emptyList();
    }

    public List<EmergencyCase> getActiveEmergencies() {
        return engine != null ? engine.getEmergencyService().getActiveCases() : Collections.emptyList();
    }

    public List<EmergencyCase> getAllEmergencies() {
        return engine != null ? engine.getEmergencyService().getAllCases() : Collections.emptyList();
    }

    public List<Hospital> getHospitals() {
        return engine != null ? engine.getHospitalService().getAllHospitals() : Collections.emptyList();
    }

    public List<SimulationEvent> getRecentEvents(int limit) {
        if (engine == null) return Collections.emptyList();
        List<SimulationEvent> all = engine.getSimulationEvents();
        int size = all.size();
        if (size <= limit) {
            List<SimulationEvent> reversed = new ArrayList<>(all);
            Collections.reverse(reversed);
            return reversed;
        }
        List<SimulationEvent> sub = new ArrayList<>(all.subList(size - limit, size));
        Collections.reverse(sub);
        return sub;
    }

    public SimulationMetrics getMetrics() {
        return engine != null ? engine.getMetrics() : SimulationMetrics.empty();
    }

    public UiMessage getLatestMessage() {
        return latestMessage;
    }

    // ==========================================
    // Configuration Properties
    // ==========================================

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public Duration getDefaultStepDuration() {
        return defaultStepDuration;
    }

    public void setDefaultStepDuration(Duration defaultStepDuration) {
        this.defaultStepDuration = Objects.requireNonNull(defaultStepDuration);
    }

    public long getAutoRunIntervalMs() {
        return autoRunIntervalMs;
    }

    public synchronized void setAutoRunIntervalMs(long autoRunIntervalMs) {
        this.autoRunIntervalMs = Math.max(50, autoRunIntervalMs);
        if (uiState == UiSimulationState.RUNNING && executorService != null && !executorService.isShutdown()) {
            if (autoRunTask != null && !autoRunTask.isDone()) {
                autoRunTask.cancel(false);
            }
            autoRunTask = executorService.scheduleWithFixedDelay(() -> {
                try {
                    synchronized (SimulationPresentationModel.this) {
                        if (uiState != UiSimulationState.RUNNING) {
                            return;
                        }
                        Duration stepSize = (currentScenario != null) ? currentScenario.getTimeStep() : defaultStepDuration;
                        step(stepSize);
                    }
                } catch (Exception ex) {
                    publishMessage(UiMessage.error("Background runner encountered error: " + ex.getMessage()));
                    pause();
                }
            }, 0, this.autoRunIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    // ==========================================
    // Event & Message Broadcasting
    // ==========================================

    private final AtomicBoolean isUiUpdatePending = new AtomicBoolean(false);

    public void addStateChangeListener(Consumer<SimulationPresentationModel> listener) {
        stateListeners.add(Objects.requireNonNull(listener));
    }

    public void removeStateChangeListener(Consumer<SimulationPresentationModel> listener) {
        stateListeners.remove(listener);
    }

    public void addMessageListener(Consumer<UiMessage> listener) {
        messageListeners.add(Objects.requireNonNull(listener));
    }

    public void removeMessageListener(Consumer<UiMessage> listener) {
        messageListeners.remove(listener);
    }

    private void notifyStateChanged() {
        if (isUiUpdatePending.compareAndSet(false, true)) {
            uiThreadDispatcher.accept(() -> {
                try {
                    for (Consumer<SimulationPresentationModel> listener : stateListeners) {
                        listener.accept(this);
                    }
                } finally {
                    isUiUpdatePending.set(false);
                }
            });
        }
    }

    public void publishMessage(UiMessage message) {
        this.latestMessage = Objects.requireNonNull(message);
        uiThreadDispatcher.accept(() -> {
            for (Consumer<UiMessage> listener : messageListeners) {
                listener.accept(message);
            }
        });
    }

    public Optional<RoadGraph> getLiveGraph() {
        if (engine != null) return Optional.ofNullable(engine.getLiveGraph());
        if (currentScenario != null) return Optional.ofNullable(currentScenario.getRoadGraph());
        return Optional.empty();
    }

    public Optional<RoadIncidentService> getIncidentService() {
        return (engine != null) ? Optional.ofNullable(engine.getIncidentService()) : Optional.empty();
    }

    public List<RoadIncident> getActiveIncidents() {
        if (engine == null || engine.getIncidentService() == null) return List.of();
        return List.copyOf(engine.getIncidentService().getActiveIncidents());
    }

    public synchronized void reportRoadClosure(String sourceId, String destId, String reason) {
        if (engine == null) {
            publishMessage(UiMessage.warning("Initialize a scenario before managing road incidents."));
            return;
        }
        try {
            String incidentId = "INC-MANUAL-" + (System.currentTimeMillis() % 10000);
            RoadIncident incident = RoadIncident.roadClosure(incidentId, sourceId, destId, engine.getCurrentInstant());
            engine.reportIncident(incident);
            notifyStateChanged();
            publishMessage(UiMessage.warning("Road closed: " + sourceId + " -> " + destId + " (" + reason + ")"));
        } catch (Exception ex) {
            publishMessage(UiMessage.error("Failed to close road: " + ex.getMessage()));
        }
    }

    public synchronized void resolveRoadIncident(String incidentId) {
        if (engine == null) return;
        try {
            engine.resolveIncident(incidentId);
            notifyStateChanged();
            publishMessage(UiMessage.success("Road incident " + incidentId + " resolved and road reopened."));
        } catch (Exception ex) {
            publishMessage(UiMessage.error("Failed to resolve incident: " + ex.getMessage()));
        }
    }

    /**
     * Retrieves full movement and interpolation telemetry for all ambulances in the simulation fleet.
     */
    public List<AmbulanceMovementTelemetry> getAmbulanceTelemetry() {
        return (engine != null) ? engine.getAmbulanceTelemetry() : Collections.emptyList();
    }

    /**
     * Retrieves all active routes currently being navigated by responding, transporting, or returning ambulances.
     */
    public List<RouteResult> getActiveMissionRoutes() {
        return (engine != null) ? engine.getActiveMissionRoutes() : Collections.emptyList();
    }

    /**
     * Dispatches an idle or available ambulance to navigate back to its assigned base station.
     */
    public synchronized boolean returnAmbulanceToBase(String ambulanceId) {
        if (engine == null) {
            publishMessage(UiMessage.warning("Initialize a scenario first."));
            return false;
        }
        boolean ok = engine.returnAmbulanceToBase(ambulanceId);
        if (ok) {
            publishMessage(UiMessage.info("Ambulance " + ambulanceId + " dispatched to return to base."));
            notifyStateChanged();
        } else {
            publishMessage(UiMessage.warning("Ambulance " + ambulanceId + " cannot return to base (busy, already at base, or unreachable)."));
        }
        return ok;
    }

    public RouteResult calculateRoute(String sourceId, String destId, String algorithmType) {
        Optional<RoadGraph> graphOpt = getLiveGraph();
        if (graphOpt.isEmpty()) {
            throw new IllegalStateException("No road graph available");
        }
        RoadGraph graph = graphOpt.get();
        RoutingAlgorithm algorithm = "A_STAR".equalsIgnoreCase(algorithmType)
                ? new AStarRoutingAlgorithm()
                : new DijkstraRoutingAlgorithm();
        return algorithm.findFastestRoute(graph, sourceId, destId);
    }

    public List<RouteResult> calculateAlternativeRoutes(String sourceId, String destId, int k) {
        Optional<RoadGraph> graphOpt = getLiveGraph();
        if (graphOpt.isEmpty()) {
            return List.of();
        }
        YenKShortestPathsAlgorithm yen = new YenKShortestPathsAlgorithm();
        return yen.findKShortestPaths(graphOpt.get(), sourceId, destId, k);
    }

    public static String formatDuration(Duration duration) {
        if (duration == null) return "00:00";
        long totalSeconds = duration.toSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
