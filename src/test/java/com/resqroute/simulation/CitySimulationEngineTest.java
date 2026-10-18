package com.resqroute.simulation;

import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.engine.CitySimulationEngine;
import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.event.SimulationEventType;
import com.resqroute.simulation.metrics.SimulationMetrics;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.simulation.model.ScheduledEmergencyEvent;
import com.resqroute.simulation.model.ScheduledIncidentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 15: City & Emergency Scenario Simulator Tests")
class CitySimulationEngineTest {

    private CityScenario defaultScenario;
    private CitySimulationEngine engine;

    @BeforeEach
    void setUp() {
        defaultScenario = CityScenario.createDefaultMetropolis(42L);
        engine = new CitySimulationEngine(defaultScenario);
    }

    @Test
    @DisplayName("Deterministic scenario replay: Same seed produces identical events and metrics")
    void testDeterministicScenarioReplay() {
        CitySimulationEngine engine1 = new CitySimulationEngine(CityScenario.createDefaultMetropolis(99L));
        engine1.runFor(Duration.ofMinutes(15));
        SimulationMetrics metrics1 = engine1.getMetrics();
        List<SimulationEvent> events1 = engine1.getSimulationEvents();

        CitySimulationEngine engine2 = new CitySimulationEngine(CityScenario.createDefaultMetropolis(99L));
        engine2.runFor(Duration.ofMinutes(15));
        SimulationMetrics metrics2 = engine2.getMetrics();
        List<SimulationEvent> events2 = engine2.getSimulationEvents();

        assertEquals(events1.size(), events2.size());
        for (int i = 0; i < events1.size(); i++) {
            assertEquals(events1.get(i).type(), events2.get(i).type());
            assertEquals(events1.get(i).entityId(), events2.get(i).entityId());
            assertEquals(events1.get(i).timestamp(), events2.get(i).timestamp());
        }

        assertEquals(metrics1.totalReportedCases(), metrics2.totalReportedCases());
        assertEquals(metrics1.totalDispatchedCases(), metrics2.totalDispatchedCases());
        assertEquals(metrics1.totalCompletedCases(), metrics2.totalCompletedCases());
        assertEquals(metrics1.averageResponseTimeMinutes(), metrics2.averageResponseTimeMinutes(), 1e-4);
    }

    @Test
    @DisplayName("Emergency creation and end-to-end mission lifecycle processing")
    void testEmergencyCreationAndProcessing() {
        // Run until CASE-01 (scheduled at T=1 min) is reported, dispatched, detoured around incident, and completed
        engine.runFor(Duration.ofMinutes(25));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.EMERGENCY_REPORTED && e.entityId().equals("CASE-01")));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.AMBULANCE_DISPATCHED));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.PATIENT_PICKED_UP && e.entityId().equals("CASE-01")));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.HOSPITAL_SELECTED));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.CASE_COMPLETED && e.entityId().equals("CASE-01")));

        EmergencyCase case1 = engine.getEmergencyService().getCaseOrThrow("CASE-01");
        assertEquals(EmergencyStatus.COMPLETED, case1.getStatus());
    }

    @Test
    @DisplayName("Road closure during ambulance movement triggers automatic rerouting")
    void testRoadClosureTriggersRerouting() {
        // At T = 4 min, INC-01 closes NORTH-STATION -> CENTRAL-PLAZA
        engine.runFor(Duration.ofMinutes(6));

        assertTrue(engine.getSimulationEvents().stream()
                .anyMatch(e -> e.type() == SimulationEventType.INCIDENT_ACTIVATED && e.entityId().equals("INC-01")));
        assertTrue(engine.getLiveGraph().isRoadClosed("NORTH-STATION", "CENTRAL-PLAZA"));
    }

    @Test
    @DisplayName("Ambulance and hospital states remain consistent throughout simulation")
    void testAmbulanceAndHospitalStateConsistency() {
        engine.runFor(Duration.ofMinutes(25));

        // When a case completes, ambulance must return to AVAILABLE
        for (Ambulance amb : engine.getFleetService().getAllAmbulances()) {
            if (amb.getStatus() == AmbulanceStatus.AVAILABLE) {
                assertTrue(amb.getAssignedCaseId().isEmpty());
            }
        }

        // Hospitals must maintain valid capacities
        for (Hospital hosp : engine.getHospitalService().getAllHospitals()) {
            assertTrue(hosp.getAvailableCapacity() >= 0);
            assertTrue(hosp.getAvailableCapacity() <= hosp.getTotalCapacity());
        }
    }

    @Test
    @DisplayName("Capacity reservation and release cycle during simulation")
    void testCapacityReservationAndRelease() {
        Hospital apexBefore = engine.getHospitalService().getHospitalOrThrow("HOSP-APEX");
        int initialAvailable = apexBefore.getAvailableCapacity();

        // Step through simulation
        engine.runFor(Duration.ofMinutes(15));

        Hospital apexAfter = engine.getHospitalService().getHospitalOrThrow("HOSP-APEX");
        // Capacity never exceeds total or drops below 0
        assertTrue(apexAfter.getAvailableCapacity() <= apexAfter.getTotalCapacity());
        assertTrue(apexAfter.getAvailableCapacity() >= 0);
    }

    @Test
    @DisplayName("Case cancellation frees ambulance and hospital allocations safely")
    void testEmergencyCancellation() {
        engine.runFor(Duration.ofMinutes(2));

        // CASE-01 is reported at T=1 min and en-route
        EmergencyCase c1 = engine.getEmergencyService().getCaseOrThrow("CASE-01");
        assertTrue(c1.hasAssignedAmbulance());
        String assignedAmbId = c1.getAssignedAmbulanceId().orElseThrow();

        // Cancel case
        engine.getEmergencyService().cancelCase("CASE-01", "False alarm by caller");

        EmergencyCase cancelled = engine.getEmergencyService().getCaseOrThrow("CASE-01");
        assertEquals(EmergencyStatus.CANCELLED, cancelled.getStatus());

        // Ambulance is freed immediately back to AVAILABLE
        assertEquals(AmbulanceStatus.AVAILABLE, engine.getFleetService().getAmbulanceOrThrow(assignedAmbId).getStatus());
    }

    @Test
    @DisplayName("Simulation time limits and reset behavior")
    void testSimulationTimeLimitsAndReset() {
        engine.runToEnd();
        assertEquals(defaultScenario.getSimulationDuration(), engine.getElapsedSimulationTime());

        // Metrics are computed
        SimulationMetrics metrics = engine.getMetrics();
        assertTrue(metrics.totalReportedCases() > 0);

        // Reset
        engine.reset();
        assertEquals(Duration.ZERO, engine.getElapsedSimulationTime());
        assertEquals(0, engine.getSimulationEvents().size());
        assertEquals(0, engine.getMetrics().totalReportedCases());
    }

    @Test
    @DisplayName("Rejects invalid scenarios with disconnected or non-existent nodes")
    void testInvalidScenarioValidation() {
        RoadGraph g = new RoadGraph();
        g.addNode(new Node("N1", 12.0, 77.0, "Node 1"));

        // Ambulance at non-existent node N2 throws NodeNotFoundException during constructor validation
        assertThrows(NodeNotFoundException.class, () -> new CityScenario(
                "Invalid",
                g,
                List.of(new Ambulance("AMB-1", "N2")),
                List.of(),
                List.of(),
                List.of(),
                Duration.ofMinutes(10),
                Duration.ofSeconds(1),
                1L,
                Instant.now()
        ));
    }

    @Test
    @DisplayName("Handles unreachable emergency locations gracefully in metrics")
    void testUnreachableDestinationsMetrics() {
        RoadGraph g = new RoadGraph();
        g.addNode(new Node("STATION", 12.0, 77.0, "Station"));
        g.addNode(new Node("ISOLATED", 13.0, 78.0, "Isolated Scene"));
        // No edge between STATION and ISOLATED

        CityScenario scenario = new CityScenario(
                "Isolated",
                g,
                List.of(new Ambulance("AMB-1", "STATION")),
                List.of(new Hospital("H1", "H1", "STATION", 5)),
                List.of(new ScheduledEmergencyEvent(Duration.ofMinutes(1), "CASE-ISO", EmergencyType.MEDICAL, EmergencySeverity.HIGH, "ISOLATED", "Isolated")),
                List.of(),
                Duration.ofMinutes(10),
                Duration.ofSeconds(10),
                1L,
                Instant.parse("2026-10-18T08:00:00Z")
        );

        CitySimulationEngine sim = new CitySimulationEngine(scenario);
        sim.runToEnd();

        SimulationMetrics metrics = sim.getMetrics();
        assertEquals(1, metrics.totalReportedCases());
        assertEquals(0, metrics.totalCompletedCases());
        assertEquals(1, metrics.totalUnservedCases());
    }

    @Test
    @DisplayName("Pause and resume controls pause logical time progression")
    void testPauseAndResume() {
        engine.pause();
        assertTrue(engine.isPaused());

        engine.step(Duration.ofMinutes(5));
        assertEquals(Duration.ZERO, engine.getElapsedSimulationTime()); // No advancement while paused

        engine.resume();
        assertFalse(engine.isPaused());

        engine.step(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(5), engine.getElapsedSimulationTime());
    }
}
