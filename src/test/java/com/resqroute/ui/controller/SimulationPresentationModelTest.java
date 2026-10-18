package com.resqroute.ui.controller;

import com.resqroute.model.Ambulance;
import com.resqroute.model.Hospital;
import com.resqroute.simulation.metrics.SimulationMetrics;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.ui.model.UiMessage;
import com.resqroute.ui.model.UiSimulationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimulationPresentationModel UI Controller Tests")
class SimulationPresentationModelTest {

    private SimulationPresentationModel model;

    @BeforeEach
    void setUp() {
        // Direct synchronous dispatcher for deterministic headless unit testing
        model = new SimulationPresentationModel(Runnable::run);
    }

    @AfterEach
    void tearDown() {
        model.shutdown();
    }

    @Test
    @DisplayName("Initial state is UNINITIALIZED with empty collections")
    void initialState() {
        assertEquals(UiSimulationState.UNINITIALIZED, model.getUiState());
        assertFalse(model.isInitialized());
        assertFalse(model.isRunning());
        assertFalse(model.isCompleted());
        assertTrue(model.getEngine().isEmpty());
        assertTrue(model.getScenario().isEmpty());
        assertEquals(Duration.ZERO, model.getElapsedDuration());
        assertEquals("00:00", model.getFormattedElapsedTime());
        assertTrue(model.getAmbulances().isEmpty());
        assertTrue(model.getActiveEmergencies().isEmpty());
        assertTrue(model.getHospitals().isEmpty());
        assertTrue(model.getRecentEvents(10).isEmpty());
    }

    @Test
    @DisplayName("Initializing default Metropolis scenario populates entities and transitions to IDLE")
    void initializeDefaultScenario() {
        List<UiMessage> messages = new ArrayList<>();
        model.addMessageListener(messages::add);

        model.initializeDefaultScenario();

        assertEquals(UiSimulationState.IDLE, model.getUiState());
        assertTrue(model.isInitialized());
        assertTrue(model.getEngine().isPresent());
        assertTrue(model.getScenario().isPresent());
        assertEquals("Metropolis Standard 15", model.getScenario().get().getName());

        List<Ambulance> ambulances = model.getAmbulances();
        assertFalse(ambulances.isEmpty());
        assertEquals(2, ambulances.size());

        List<Hospital> hospitals = model.getHospitals();
        assertFalse(hospitals.isEmpty());
        assertEquals(2, hospitals.size());

        assertFalse(messages.isEmpty());
        assertEquals(UiMessage.MessageType.SUCCESS, messages.get(messages.size() - 1).type());
    }

    @Test
    @DisplayName("Single step advancement increases simulation clock and updates state listeners")
    void singleStepAdvancement() {
        model.initializeDefaultScenario();

        AtomicInteger stateChangeCount = new AtomicInteger();
        model.addStateChangeListener(m -> stateChangeCount.incrementAndGet());

        model.step(Duration.ofMinutes(1));

        assertEquals(Duration.ofMinutes(1), model.getElapsedDuration());
        assertEquals("01:00", model.getFormattedElapsedTime());
        assertTrue(stateChangeCount.get() > 0);
    }

    @Test
    @DisplayName("stepDefault uses scenario configured time step")
    void stepDefaultUsesScenarioStep() {
        model.initializeDefaultScenario();
        Duration scenarioStep = model.getScenario().get().getTimeStep();

        model.stepDefault();

        assertEquals(scenarioStep, model.getElapsedDuration());
    }

    @Test
    @DisplayName("Stepping to scenario end transitions UI state to COMPLETED")
    void steppingToEndTransitionsToCompleted() {
        model.initializeDefaultScenario();
        CityScenario scenario = model.getScenario().get();

        // Step entire duration
        model.step(scenario.getSimulationDuration());

        assertTrue(model.isCompleted());
        assertEquals(UiSimulationState.COMPLETED, model.getUiState());

        // Stepping further publishes notification and does not corrupt state
        model.step(Duration.ofMinutes(1));
        assertEquals(UiSimulationState.COMPLETED, model.getUiState());
    }

    @Test
    @DisplayName("Toggle play/pause transitions between RUNNING and PAUSED")
    void togglePlayPause() {
        model.initializeDefaultScenario();
        assertEquals(UiSimulationState.IDLE, model.getUiState());

        model.togglePlayPause();
        assertEquals(UiSimulationState.RUNNING, model.getUiState());
        assertTrue(model.isRunning());

        model.togglePlayPause();
        assertEquals(UiSimulationState.PAUSED, model.getUiState());
        assertFalse(model.isRunning());

        model.resume();
        assertEquals(UiSimulationState.RUNNING, model.getUiState());

        model.pause();
        assertEquals(UiSimulationState.PAUSED, model.getUiState());
    }

    @Test
    @DisplayName("Resetting simulation returns state to initial T=0 without corrupting graph")
    void resetSimulation() {
        model.initializeDefaultScenario();
        model.step(Duration.ofMinutes(10));
        assertNotEquals(Duration.ZERO, model.getElapsedDuration());

        model.reset();

        assertEquals(Duration.ZERO, model.getElapsedDuration());
        assertEquals(UiSimulationState.IDLE, model.getUiState());
        assertEquals("00:00", model.getFormattedElapsedTime());
        assertEquals(2, model.getAmbulances().size());
        assertEquals(2, model.getHospitals().size());
    }

    @Test
    @DisplayName("Telemetry metrics are dynamically computed from active engine")
    void telemetryMetricsReadout() {
        model.initializeDefaultScenario();
        model.step(Duration.ofMinutes(15));

        SimulationMetrics metrics = model.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.totalReportedCases() >= 1);
        assertTrue(metrics.overallAmbulanceUtilization() >= 0.0);
    }

    @Test
    @DisplayName("Settings customization persists new seed, step duration, and interval")
    void settingsCustomization() {
        model.setRandomSeed(9999L);
        assertEquals(9999L, model.getRandomSeed());

        model.setDefaultStepDuration(Duration.ofSeconds(45));
        assertEquals(Duration.ofSeconds(45), model.getDefaultStepDuration());

        model.setAutoRunIntervalMs(500);
        assertEquals(500, model.getAutoRunIntervalMs());
    }

    @Test
    @DisplayName("Step without initialized scenario publishes warning message")
    void stepWithoutScenarioWarning() {
        List<UiMessage> messages = new ArrayList<>();
        model.addMessageListener(messages::add);

        model.step(Duration.ofMinutes(1));

        assertFalse(messages.isEmpty());
        assertEquals(UiMessage.MessageType.WARNING, messages.get(0).type());
    }

    @Test
    @DisplayName("formatDuration formats seconds, minutes, and hours accurately")
    void formatDurationAccuracy() {
        assertEquals("00:00", SimulationPresentationModel.formatDuration(null));
        assertEquals("00:00", SimulationPresentationModel.formatDuration(Duration.ZERO));
        assertEquals("00:45", SimulationPresentationModel.formatDuration(Duration.ofSeconds(45)));
        assertEquals("03:15", SimulationPresentationModel.formatDuration(Duration.ofSeconds(195)));
        assertEquals("01:05:30", SimulationPresentationModel.formatDuration(Duration.ofSeconds(3930)));
    }
}
