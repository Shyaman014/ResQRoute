package com.resqroute.simulation;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.engine.CitySimulationEngine;
import com.resqroute.simulation.event.SimulationEventType;
import com.resqroute.simulation.model.AmbulanceMovementTelemetry;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.simulation.model.VehicleVisualState;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.model.UiSimulationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 19: Route Animation & Live Simulation Controls Tests")
class AmbulanceMovementAnimationTest {

    private CityScenario scenario;
    private SimulationPresentationModel presentationModel;

    @BeforeEach
    void setUp() {
        scenario = CityScenario.createDefaultMetropolis(42L);
        presentationModel = new SimulationPresentationModel(Runnable::run);
        presentationModel.initializeScenario(scenario);
    }

    @AfterEach
    void tearDown() {
        presentationModel.shutdown();
    }

    @Test
    @DisplayName("Simulation controls: Start, pause, resume, reset, and logical time synchronization")
    void testSimulationPauseResumeResetControls() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();
        assertEquals(UiSimulationState.IDLE, presentationModel.getUiState());
        assertEquals(Duration.ZERO, engine.getElapsedSimulationTime());
        assertFalse(engine.isPaused());

        // 1. Start Auto-Run
        presentationModel.startAutoRun();
        assertEquals(UiSimulationState.RUNNING, presentationModel.getUiState());
        assertTrue(presentationModel.isRunning());

        // 2. Pause
        presentationModel.pause();
        assertEquals(UiSimulationState.PAUSED, presentationModel.getUiState());
        assertTrue(engine.isPaused());

        // 3. Step while paused
        presentationModel.step(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30), engine.getElapsedSimulationTime());

        // 4. Resume
        presentationModel.resume();
        assertEquals(UiSimulationState.RUNNING, presentationModel.getUiState());
        assertFalse(engine.isPaused());

        // 5. Reset
        presentationModel.reset();
        assertEquals(UiSimulationState.IDLE, presentationModel.getUiState());
        assertEquals(Duration.ZERO, engine.getElapsedSimulationTime());
        assertFalse(engine.isPaused());

        // Ambulances reset to initial positions
        for (Ambulance initial : scenario.getInitialAmbulances()) {
            Ambulance current = engine.getFleetService().getAmbulanceOrThrow(initial.getId());
            assertEquals(initial.getCurrentLocationNodeId(), current.getCurrentLocationNodeId());
            assertEquals(AmbulanceStatus.AVAILABLE, current.getStatus());
        }
    }

    @Test
    @DisplayName("Single-step discrete advancement and speed adjustment")
    void testSingleStepAdvancementAndSpeedAdjustment() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();

        presentationModel.step(Duration.ofSeconds(10));
        assertEquals(Duration.ofSeconds(10), engine.getElapsedSimulationTime());

        presentationModel.step(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(40), engine.getElapsedSimulationTime());

        presentationModel.step(Duration.ofMinutes(1));
        assertEquals(Duration.ofSeconds(100), engine.getElapsedSimulationTime());

        // Adjust speed
        presentationModel.setAutoRunIntervalMs(200);
        assertEquals(200, presentationModel.getAutoRunIntervalMs());

        presentationModel.setAutoRunIntervalMs(20); // Clamp minimum
        assertEquals(50, presentationModel.getAutoRunIntervalMs());
    }

    @Test
    @DisplayName("Ambulance movement progression and coordinate interpolation between road nodes")
    void testMovementInterpolationBetweenRoadNodes() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();

        // Initial telemetry: all stationary at base stations
        List<AmbulanceMovementTelemetry> initialTelemetry = engine.getAmbulanceTelemetry();
        assertEquals(scenario.getInitialAmbulances().size(), initialTelemetry.size());

        for (AmbulanceMovementTelemetry tel : initialTelemetry) {
            assertEquals(VehicleVisualState.AVAILABLE, tel.visualState());
            assertFalse(tel.isMoving());
            assertEquals(1.0, tel.routeProgress(), 1e-4);
            assertEquals(0.0, tel.estimatedSecondsRemaining(), 1e-4);
            assertFalse(tel.isBlocked());
        }

        // Advance to T = 1 min: CASE-01 triggers and AMB-01 is dispatched en route
        presentationModel.step(Duration.ofMinutes(1));

        List<AmbulanceMovementTelemetry> enRouteTelemetry = engine.getAmbulanceTelemetry();
        AmbulanceMovementTelemetry activeTel = enRouteTelemetry.stream()
                .filter(t -> t.visualState() == VehicleVisualState.RESPONDING)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least one ambulance en route responding to scene"));

        assertTrue(activeTel.isMoving());
        assertTrue(activeTel.getActiveRoute().isPresent());
        RouteResult route = activeTel.getActiveRoute().get();
        assertTrue(route.isReachable());
        assertTrue(route.getTotalDistanceKm() > 0);

        // Advance small steps (15 seconds) and verify continuous interpolation
        GeoCoordinate coordBefore = activeTel.currentCoordinates();
        double progressBefore = activeTel.routeProgress();
        double etaBefore = activeTel.estimatedSecondsRemaining();

        presentationModel.step(Duration.ofSeconds(15));

        AmbulanceMovementTelemetry advancedTel = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals(activeTel.ambulanceId()))
                .findFirst()
                .orElseThrow();

        assertTrue(advancedTel.routeProgress() >= progressBefore, "Route progress must be monotonically non-decreasing");
        assertTrue(advancedTel.estimatedSecondsRemaining() <= etaBefore, "ETA must decrease as vehicle advances");

        // Coordinates must be valid and within network bounding box
        assertTrue(advancedTel.currentCoordinates().latitude() >= 40.70 && advancedTel.currentCoordinates().latitude() <= 40.80);
        assertTrue(advancedTel.currentCoordinates().longitude() >= -74.05 && advancedTel.currentCoordinates().longitude() <= -73.95);
        assertTrue(advancedTel.headingDegrees() >= 0.0 && advancedTel.headingDegrees() <= 360.0);
        assertNotNull(advancedTel.getFormattedEta());
        assertNotNull(advancedTel.getFormattedCoordinates());
    }

    @Test
    @DisplayName("Distinct visual states throughout complete mission lifecycle")
    void testDistinctVisualStatesThroughoutLifecycle() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();

        // 1. Initial State: AVAILABLE
        AmbulanceMovementTelemetry t0 = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals("AMB-01"))
                .findFirst()
                .orElseThrow();
        assertEquals(VehicleVisualState.AVAILABLE, t0.visualState());

        // 2. T = 1 min: Dispatched -> RESPONDING
        presentationModel.step(Duration.ofMinutes(1));
        AmbulanceMovementTelemetry t1 = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals("AMB-01"))
                .findFirst()
                .orElseThrow();
        assertEquals(VehicleVisualState.RESPONDING, t1.visualState());
        assertTrue(t1.isMoving());

        // 3. Step until patient is picked up on scene or admitted to hospital
        presentationModel.step(Duration.ofMinutes(10));

        // Event log verifies mission reached PATIENT_PICKED_UP and HOSPITAL_SELECTED
        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.PATIENT_PICKED_UP && e.entityId().equals("CASE-01")));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.HOSPITAL_SELECTED));
    }

    @Test
    @DisplayName("Dynamic rerouting when road closure occurs during ambulance transit")
    void testDynamicReroutingDuringMovement() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();

        // Dispatched at T = 1 min
        presentationModel.step(Duration.ofMinutes(1));

        AmbulanceMovementTelemetry amb = engine.getAmbulanceTelemetry().stream()
                .filter(AmbulanceMovementTelemetry::isMoving)
                .findFirst()
                .orElseThrow();

        RouteResult initialRoute = amb.getActiveRoute().orElseThrow();
        List<Edge> edges = initialRoute.getEdges();
        assertTrue(edges.size() >= 2, "Route should have at least 2 edges for mid-transit detour testing");

        // Close edge 1 on the route
        Edge roadToClose = edges.get(1);
        String srcClose = roadToClose.getSource().orElseThrow().getId();
        String dstClose = roadToClose.getDestination().getId();

        presentationModel.reportRoadClosure(srcClose, dstClose, "Emergency pipe burst");

        // Live graph reflects closed road
        assertTrue(engine.getLiveGraph().isRoadClosed(srcClose, dstClose));

        // Rerouting event was recorded
        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.ROUTE_RECALCULATED && e.entityId().equals(amb.ambulanceId())));

        // Telemetry updated to detour route without the closed road
        AmbulanceMovementTelemetry reroutedAmb = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals(amb.ambulanceId()))
                .findFirst()
                .orElseThrow();

        assertTrue(reroutedAmb.getActiveRoute().isPresent());
        RouteResult newRoute = reroutedAmb.getActiveRoute().get();
        for (Edge e : newRoute.getEdges()) {
            assertFalse(e.getSource().map(Node::getId).orElse("").equals(srcClose)
                            && e.getDestination().getId().equals(dstClose),
                    "Rerouted detour must not traverse the closed road corridor");
        }
    }

    @Test
    @DisplayName("Handling blocked roads and unreachable destinations prevents stale animation")
    void testBlockedRoadAndUnreachableDestination() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();

        // Dispatched at T = 1 min
        presentationModel.step(Duration.ofMinutes(1));

        AmbulanceMovementTelemetry amb = engine.getAmbulanceTelemetry().stream()
                .filter(AmbulanceMovementTelemetry::isMoving)
                .findFirst()
                .orElseThrow();

        String targetDestination = amb.destinationNodeId();

        // Sever all incoming roads into destination
        for (Edge inEdge : engine.getLiveGraph().getIncomingEdges(targetDestination)) {
            String src = inEdge.getSource().orElseThrow().getId();
            presentationModel.reportRoadClosure(src, targetDestination, "Complete quarantine lockdown");
        }

        AmbulanceMovementTelemetry blockedAmb = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals(amb.ambulanceId()))
                .findFirst()
                .orElseThrow();

        assertTrue(blockedAmb.isBlocked(), "Ambulance must be flagged as blocked when destination is unreachable");
        assertFalse(blockedAmb.isMoving(), "Halted ambulance must not report active movement");
        assertEquals("0s", blockedAmb.getFormattedEta());

        // Step forward in time: blocked ambulance stays halted without throwing exceptions
        assertDoesNotThrow(() -> presentationModel.step(Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("Reopening closed road unblocks halted ambulance and resumes route")
    void testRoadReopeningResumesBlockedMission() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();
        presentationModel.step(Duration.ofMinutes(1));

        AmbulanceMovementTelemetry amb = engine.getAmbulanceTelemetry().stream()
                .filter(AmbulanceMovementTelemetry::isMoving)
                .findFirst()
                .orElseThrow();

        String target = amb.destinationNodeId();
        List<Edge> inEdges = engine.getLiveGraph().getIncomingEdges(target);
        for (Edge inEdge : inEdges) {
            String src = inEdge.getSource().orElseThrow().getId();
            presentationModel.reportRoadClosure(src, target, "Temporary barricade");
        }

        AmbulanceMovementTelemetry blockedAmb = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals(amb.ambulanceId()))
                .findFirst()
                .orElseThrow();
        assertTrue(blockedAmb.isBlocked());

        // Reopen road incidents
        for (RoadIncident inc : presentationModel.getActiveIncidents()) {
            presentationModel.resolveRoadIncident(inc.getId());
        }

        AmbulanceMovementTelemetry resumedAmb = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals(amb.ambulanceId()))
                .findFirst()
                .orElseThrow();

        assertFalse(resumedAmb.isBlocked(), "Ambulance should no longer be blocked after road reopening");
        assertTrue(resumedAmb.isMoving(), "Ambulance should resume movement toward destination");
    }

    @Test
    @DisplayName("Return idle ambulance to base station")
    void testReturnAmbulanceToBaseStation() {
        CitySimulationEngine engine = presentationModel.getEngine().orElseThrow();

        // AMB-01 is initially at NORTH-STATION
        Ambulance amb01 = engine.getFleetService().getAmbulanceOrThrow("AMB-01");
        assertEquals("NORTH-STATION", amb01.getCurrentLocationNodeId());

        // Relocate ambulance to a hospital node
        engine.getFleetService().updateLocation("AMB-01", "HOSP-APEX-NODE");

        // Dispatch return to base
        boolean ok = presentationModel.returnAmbulanceToBase("AMB-01");
        assertTrue(ok, "Return to base dispatch should succeed");

        AmbulanceMovementTelemetry tel = engine.getAmbulanceTelemetry().stream()
                .filter(t -> t.ambulanceId().equals("AMB-01"))
                .findFirst()
                .orElseThrow();

        assertEquals(VehicleVisualState.RETURNING, tel.visualState());
        assertEquals("NORTH-STATION", tel.destinationNodeId());
        assertTrue(tel.isMoving());

        // Step simulation until arrival back at base
        presentationModel.step(Duration.ofMinutes(10));

        Ambulance returned = engine.getFleetService().getAmbulanceOrThrow("AMB-01");
        assertEquals("NORTH-STATION", returned.getCurrentLocationNodeId());
        assertEquals(AmbulanceStatus.AVAILABLE, returned.getStatus());
    }

    @Test
    @DisplayName("Thread-safe UI state change listeners and clean background executor shutdown")
    void testThreadSafeUiNotificationsAndCleanShutdown() {
        AtomicInteger updateCount = new AtomicInteger(0);
        presentationModel.addStateChangeListener(m -> updateCount.incrementAndGet());

        // Advance several steps synchronously
        presentationModel.step(Duration.ofSeconds(10));
        presentationModel.step(Duration.ofSeconds(10));
        presentationModel.step(Duration.ofSeconds(10));

        assertTrue(updateCount.get() >= 3, "Listeners should receive state update notifications");

        // Clean shutdown
        assertDoesNotThrow(() -> presentationModel.shutdown());
    }
}
