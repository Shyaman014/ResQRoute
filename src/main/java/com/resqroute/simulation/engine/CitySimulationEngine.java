package com.resqroute.simulation.engine;

import com.resqroute.algorithm.DijkstraRoutingAlgorithm;
import com.resqroute.dispatch.DefaultCapabilityMatcher;
import com.resqroute.dispatch.DispatchResult;
import com.resqroute.graph.RoadGraph;
import com.resqroute.hospital.DefaultHospitalSuitabilityMatcher;
import com.resqroute.hospital.FastestHospitalRankingPolicy;
import com.resqroute.hospital.HospitalSelectionResult;
import com.resqroute.model.*;
import com.resqroute.scheduling.AgingDispatchPolicy;
import com.resqroute.service.*;
import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.event.SimulationEventType;
import com.resqroute.simulation.metrics.SimulationMetrics;
import com.resqroute.simulation.model.AmbulanceMovementTelemetry;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.simulation.model.ScheduledEmergencyEvent;
import com.resqroute.simulation.model.ScheduledIncidentEvent;
import com.resqroute.simulation.model.VehicleVisualState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic discrete-time simulation engine executing city emergency scenarios.
 *
 * <p>Orchestrates emergency incident lifecycles, dynamic road network updates, real-time traffic,
 * vehicle movement along calculated routes, hospital capacity admission, and performance telemetry.</p>
 */
public class CitySimulationEngine {

    private final CityScenario scenario;
    private final AtomicLong eventSequence = new AtomicLong(1);

    // Simulation Clock
    private SimulationClock simulationClock;
    private Duration elapsedSimulationTime;
    private boolean paused;

    // Services
    private RoadGraph liveGraph;
    private AmbulanceFleetService fleetService;
    private EmergencyManagementService emergencyService;
    private EmergencyDispatchQueueService queueService;
    private HospitalManagementService hospitalService;
    private HospitalSelectionService hospitalSelectionService;
    private AmbulanceDispatchService dispatchService;
    private RoadIncidentService incidentService;
    private TrafficSimulationEngine trafficEngine;
    private AutomaticReroutingService reroutingService;

    // Active Mission Tracking
    private final Map<String, ActiveMission> activeMissions = new LinkedHashMap<>();
    private final List<SimulationEvent> eventLog = new ArrayList<>();

    // Event Triggers Tracking
    private final Set<String> triggeredEmergencyIds = new HashSet<>();
    private final Set<String> activeIncidentIds = new HashSet<>();
    private final Set<String> resolvedIncidentIds = new HashSet<>();

    // Metrics Tracking
    private final Map<String, Instant> caseReportTimes = new HashMap<>();
    private final Map<String, Instant> caseDispatchTimes = new HashMap<>();
    private final Map<String, Instant> caseResponseTimes = new HashMap<>();
    private final Map<String, Instant> caseDeliveryTimes = new HashMap<>();
    private final Map<String, Long> ambulanceActiveSeconds = new HashMap<>();
    private final Set<String> completedCaseIds = new HashSet<>();
    private final Set<String> cancelledCaseIds = new HashSet<>();

    public enum MissionPhase {
        EN_ROUTE_TO_SCENE,
        TRANSPORTING_TO_HOSPITAL,
        RETURNING_TO_BASE
    }

    private static final class ActiveMission {
        final String ambulanceId;
        final String caseId;
        MissionPhase phase;
        RouteResult route;
        int currentEdgeIndex;
        double secondsOnCurrentEdge;
        String destinationHospitalId;

        ActiveMission(String ambulanceId, String caseId, MissionPhase phase, RouteResult route) {
            this.ambulanceId = ambulanceId;
            this.caseId = caseId;
            this.phase = phase;
            this.route = route;
            this.currentEdgeIndex = 0;
            this.secondsOnCurrentEdge = 0.0;
        }
    }

    private static class SimulationClock extends Clock {
        private Instant currentInstant;

        SimulationClock(Instant initial) {
            this.currentInstant = initial;
        }

        void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
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
            return currentInstant;
        }
    }

    public CitySimulationEngine(CityScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "CityScenario must not be null");
        initialize();
    }

    /**
     * Initializes or resets the simulation engine to initial scenario state (T = 0).
     */
    public synchronized void initialize() {
        scenario.validate();

        this.simulationClock = new SimulationClock(scenario.getBaseTime());
        this.elapsedSimulationTime = Duration.ZERO;
        this.paused = false;
        this.eventSequence.set(1);

        // Deep copy road graph
        this.liveGraph = copyGraph(scenario.getRoadGraph());

        // Initialize Services
        this.fleetService = new AmbulanceFleetService(liveGraph);
        for (Ambulance amb : scenario.getInitialAmbulances()) {
            fleetService.registerAmbulance(new Ambulance(amb.getId(), amb.getCurrentLocationNodeId(), amb.getCapabilities()));
        }

        this.emergencyService = new EmergencyManagementService(liveGraph, fleetService, simulationClock);
        this.queueService = new EmergencyDispatchQueueService(emergencyService, new AgingDispatchPolicy(10.0), simulationClock);
        this.hospitalService = new HospitalManagementService(liveGraph, simulationClock);

        for (Hospital hosp : scenario.getInitialHospitals()) {
            hospitalService.registerHospital(hosp);
        }

        this.hospitalSelectionService = new HospitalSelectionService(
                liveGraph,
                new DijkstraRoutingAlgorithm(),
                hospitalService,
                emergencyService,
                DefaultHospitalSuitabilityMatcher.INSTANCE,
                FastestHospitalRankingPolicy.INSTANCE,
                simulationClock
        );

        this.dispatchService = new AmbulanceDispatchService(
                liveGraph,
                new DijkstraRoutingAlgorithm(),
                emergencyService,
                fleetService,
                queueService
        );

        this.incidentService = new RoadIncidentService(liveGraph);
        this.trafficEngine = new TrafficSimulationEngine(liveGraph, scenario.getRandomSeed());
        this.reroutingService = new AutomaticReroutingService(liveGraph, new DijkstraRoutingAlgorithm());

        // Clear tracking collections
        this.activeMissions.clear();
        this.eventLog.clear();
        this.triggeredEmergencyIds.clear();
        this.activeIncidentIds.clear();
        this.resolvedIncidentIds.clear();

        this.caseReportTimes.clear();
        this.caseDispatchTimes.clear();
        this.caseResponseTimes.clear();
        this.caseDeliveryTimes.clear();
        this.ambulanceActiveSeconds.clear();
        for (Ambulance amb : scenario.getInitialAmbulances()) {
            ambulanceActiveSeconds.put(amb.getId(), 0L);
        }
        this.completedCaseIds.clear();
        this.cancelledCaseIds.clear();
    }

    public synchronized void reset() {
        initialize();
    }

    /**
     * Advances simulation by an explicit time delta.
     */
    public synchronized void step(Duration timeDelta) {
        if (paused || timeDelta.isZero() || timeDelta.isNegative()) {
            return;
        }

        // 1. Advance simulation clock
        simulationClock.advance(timeDelta);
        elapsedSimulationTime = elapsedSimulationTime.plus(timeDelta);
        Instant now = simulationClock.instant();

        // 2. Trigger scheduled emergencies
        for (ScheduledEmergencyEvent event : scenario.getScheduledEmergencies()) {
            if (!triggeredEmergencyIds.contains(event.caseId())
                    && elapsedSimulationTime.compareTo(event.timeOffset()) >= 0) {
                triggerEmergency(event, now);
            }
        }

        // 3. Trigger & resolve scheduled road incidents
        for (ScheduledIncidentEvent event : scenario.getScheduledIncidents()) {
            if (!activeIncidentIds.contains(event.incidentId())
                    && elapsedSimulationTime.compareTo(event.startTimeOffset()) >= 0) {
                activateIncident(event, now);
            }
            if (activeIncidentIds.contains(event.incidentId())
                    && !resolvedIncidentIds.contains(event.incidentId())
                    && elapsedSimulationTime.compareTo(event.getEndTimeOffset()) >= 0) {
                resolveIncident(event, now);
            }
        }

        // 4. Update traffic conditions
        trafficEngine.advanceSimulationStep();

        // 5. Process emergency dispatch queue
        processDispatchQueue(now);

        // 6. Advance active ambulance movements
        advanceAmbulanceMissions(timeDelta, now);
    }

    public synchronized void stepSeconds(long seconds) {
        step(Duration.ofSeconds(seconds));
    }

    public synchronized void runFor(Duration duration) {
        Duration remaining = duration;
        Duration dt = scenario.getTimeStep();
        while (remaining.compareTo(Duration.ZERO) > 0 && elapsedSimulationTime.compareTo(scenario.getSimulationDuration()) < 0) {
            Duration currentStep = (remaining.compareTo(dt) < 0) ? remaining : dt;
            step(currentStep);
            remaining = remaining.minus(currentStep);
        }
    }

    public synchronized void runUntil(Instant targetTime) {
        if (targetTime.isBefore(simulationClock.instant())) {
            return;
        }
        Duration needed = Duration.between(simulationClock.instant(), targetTime);
        runFor(needed);
    }

    public synchronized void runToEnd() {
        Duration remaining = scenario.getSimulationDuration().minus(elapsedSimulationTime);
        if (!remaining.isNegative() && !remaining.isZero()) {
            runFor(remaining);
        }
    }

    public synchronized void pause() {
        this.paused = true;
    }

    public synchronized void resume() {
        this.paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    private void triggerEmergency(ScheduledEmergencyEvent event, Instant now) {
        triggeredEmergencyIds.add(event.caseId());
        EmergencyCase emergency = emergencyService.createCase(
                event.caseId(), event.type(), event.severity(), event.pickupNodeId(), event.description());
        queueService.enqueue(emergency);
        caseReportTimes.put(event.caseId(), now);

        recordEvent(SimulationEventType.EMERGENCY_REPORTED, event.caseId(),
                String.format("Reported: %s at %s (%s)", event.type(), event.pickupNodeId(), event.severity()), now);
    }

    private void activateIncident(ScheduledIncidentEvent event, Instant now) {
        activeIncidentIds.add(event.incidentId());
        RoadIncident incident = RoadIncident.roadClosure(
                event.incidentId(), event.sourceNodeId(), event.destinationNodeId(), now);
        incidentService.reportIncident(incident);

        recordEvent(SimulationEventType.INCIDENT_ACTIVATED, event.incidentId(),
                String.format("Road closed: %s -> %s (%s)", event.sourceNodeId(), event.destinationNodeId(), event.description()), now);

        // Check if any en-route missions need immediate dynamic rerouting
        rerouteAffectedMissions(event.sourceNodeId(), event.destinationNodeId(), now);
    }

    private void resolveIncident(ScheduledIncidentEvent event, Instant now) {
        resolvedIncidentIds.add(event.incidentId());
        incidentService.resolveIncident(event.incidentId());

        recordEvent(SimulationEventType.INCIDENT_RESOLVED, event.incidentId(),
                String.format("Road reopened: %s -> %s", event.sourceNodeId(), event.destinationNodeId()), now);

        rerouteBlockedMissions(now);
    }

    /**
     * Reports a live road incident (e.g., manual closure), immediately updating the road graph
     * and recalculating active mission routes traversing the closed segment.
     */
    public synchronized void reportIncident(RoadIncident incident) {
        Objects.requireNonNull(incident, "RoadIncident must not be null");
        activeIncidentIds.add(incident.getId());
        incidentService.reportIncident(incident);
        Instant now = simulationClock.instant();

        recordEvent(SimulationEventType.INCIDENT_ACTIVATED, incident.getId(),
                String.format("Road closed: %s -> %s (%s)", incident.getSourceNodeId(), incident.getDestinationNodeId(), incident.getDescription()), now);

        rerouteAffectedMissions(incident.getSourceNodeId(), incident.getDestinationNodeId(), now);
    }

    /**
     * Resolves an active road incident, reopening the roadway and unblocking any halted missions.
     */
    public synchronized void resolveIncident(String incidentId) {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        resolvedIncidentIds.add(incidentId);
        incidentService.resolveIncident(incidentId);
        Instant now = simulationClock.instant();

        recordEvent(SimulationEventType.INCIDENT_RESOLVED, incidentId,
                String.format("Road reopened for incident %s", incidentId), now);

        rerouteBlockedMissions(now);
    }

    private void processDispatchQueue(Instant now) {
        while (!queueService.isEmpty()) {
            DispatchResult result = dispatchService.dispatchNext();
            if (!result.isSuccess()) {
                break;
            }

            EmergencyCase assignedCase = result.getEmergencyCase().orElseThrow();
            Ambulance ambulance = result.getAssignedAmbulance().orElseThrow();
            RouteResult route = result.getRouteToPickup().orElseThrow();

            // Transition status: AMBULANCE_ASSIGNED -> AMBULANCE_EN_ROUTE
            emergencyService.updateCaseStatus(assignedCase.getId(), EmergencyStatus.AMBULANCE_EN_ROUTE, "Ambulance en route to scene");
            caseDispatchTimes.put(assignedCase.getId(), now);

            ActiveMission mission = new ActiveMission(ambulance.getId(), assignedCase.getId(), MissionPhase.EN_ROUTE_TO_SCENE, route);
            activeMissions.put(ambulance.getId(), mission);

            recordEvent(SimulationEventType.AMBULANCE_DISPATCHED, ambulance.getId(),
                    String.format("Dispatched to %s at %s (ETA: %.1f min)",
                            assignedCase.getId(), assignedCase.getPickupLocationNodeId(), result.getEstimatedTravelTimeMinutes()), now);
        }
    }

    private void advanceAmbulanceMissions(Duration timeDelta, Instant now) {
        List<String> finishedMissions = new ArrayList<>();
        double deltaSeconds = timeDelta.toSeconds();

        for (ActiveMission mission : activeMissions.values()) {
            if (mission.route == null || !mission.route.isReachable()) {
                // Route is blocked / unreachable; ambulance halted safely until road reopens
                continue;
            }

            ambulanceActiveSeconds.merge(mission.ambulanceId, (long) deltaSeconds, Long::sum);

            List<Edge> edges = mission.route.getEdges();
            if (edges.isEmpty()) {
                // Origin and destination are identical
                completeMissionPhase(mission, now);
                if (mission.phase == null) {
                    finishedMissions.add(mission.ambulanceId);
                }
                continue;
            }

            mission.secondsOnCurrentEdge += deltaSeconds;

            while (mission.route != null && mission.route.isReachable()) {
                edges = mission.route.getEdges();
                if (edges.isEmpty() || mission.currentEdgeIndex >= edges.size()) {
                    completeMissionPhase(mission, now);
                    if (mission.phase == null) {
                        finishedMissions.add(mission.ambulanceId);
                    }
                    break;
                }

                Edge currentEdge = edges.get(mission.currentEdgeIndex);
                double edgeTravelTimeSeconds = currentEdge.getTravelTimeMinutes() * 60.0;

                if (mission.secondsOnCurrentEdge >= edgeTravelTimeSeconds) {
                    mission.secondsOnCurrentEdge -= edgeTravelTimeSeconds;
                    mission.currentEdgeIndex++;

                    String reachedNodeId = currentEdge.getDestination().getId();
                    fleetService.updateLocation(mission.ambulanceId, reachedNodeId);

                    recordEvent(SimulationEventType.AMBULANCE_WAYPOINT_REACHED, mission.ambulanceId,
                            "Arrived at waypoint " + reachedNodeId, now);

                    if (mission.currentEdgeIndex >= edges.size()) {
                        completeMissionPhase(mission, now);
                        if (mission.phase == null) {
                            finishedMissions.add(mission.ambulanceId);
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
        }

        for (String ambId : finishedMissions) {
            activeMissions.remove(ambId);
        }
    }

    private void completeMissionPhase(ActiveMission mission, Instant now) {
        if (mission.phase == MissionPhase.EN_ROUTE_TO_SCENE) {
            // 1. Arrived at scene -> PATIENT_PICKED_UP
            emergencyService.updateCaseStatus(mission.caseId, EmergencyStatus.PATIENT_PICKED_UP, "Patient picked up on scene");
            caseResponseTimes.put(mission.caseId, now);

            recordEvent(SimulationEventType.PATIENT_PICKED_UP, mission.caseId,
                    "Patient secured by " + mission.ambulanceId, now);

            // 2. Select & reserve destination hospital
            EmergencyCase currentCase = emergencyService.getCaseOrThrow(mission.caseId);
            String pickupNode = currentCase.getPickupLocationNodeId();
            HospitalSelectionResult hospResult = hospitalSelectionService.selectAndReserveHospital(currentCase, pickupNode);

            if (hospResult.isSuccess()) {
                Hospital chosenHospital = hospResult.getSelectedHospital().orElseThrow().getHospital();
                mission.destinationHospitalId = chosenHospital.getId();

                recordEvent(SimulationEventType.HOSPITAL_SELECTED, chosenHospital.getId(),
                        String.format("Reserved %s for %s", chosenHospital.getName(), mission.caseId), now);

                // Transition status to TRANSPORTING
                emergencyService.updateCaseStatus(mission.caseId, EmergencyStatus.TRANSPORTING, "En route to hospital");

                // Route from pickup to hospital
                RouteResult toHospitalRoute = hospResult.getSelectedHospital().orElseThrow().getRoute();
                mission.phase = MissionPhase.TRANSPORTING_TO_HOSPITAL;
                mission.route = toHospitalRoute;
                mission.currentEdgeIndex = 0;
                mission.secondsOnCurrentEdge = 0.0;

            } else {
                // Hospital unavailable or unreachable: treat on scene and complete
                emergencyService.updateCaseStatus(mission.caseId, EmergencyStatus.COMPLETED, "Treated and stabilized on scene (hospital admission unavailable)");
                completedCaseIds.add(mission.caseId);
                fleetService.releaseAmbulance(mission.ambulanceId);
                mission.phase = null;

                recordEvent(SimulationEventType.CASE_COMPLETED, mission.caseId,
                        "Completed on scene; ambulance released", now);
            }

        } else if (mission.phase == MissionPhase.TRANSPORTING_TO_HOSPITAL) {
            // Arrived at destination hospital -> HOSPITAL_ARRIVED & CASE_COMPLETED
            caseDeliveryTimes.put(mission.caseId, now);
            recordEvent(SimulationEventType.HOSPITAL_ARRIVED, mission.destinationHospitalId,
                    String.format("%s delivered %s to hospital", mission.ambulanceId, mission.caseId), now);

            emergencyService.updateCaseStatus(mission.caseId, EmergencyStatus.COMPLETED, "Delivered and admitted to hospital");
            completedCaseIds.add(mission.caseId);

            recordEvent(SimulationEventType.CASE_COMPLETED, mission.caseId, "Case closed successfully", now);

            // Release ambulance back to available
            fleetService.releaseAmbulance(mission.ambulanceId);
            mission.phase = null;

        } else if (mission.phase == MissionPhase.RETURNING_TO_BASE) {
            // Arrived back at home base station
            fleetService.updateAmbulanceStatus(mission.ambulanceId, AmbulanceStatus.AVAILABLE);
            recordEvent(SimulationEventType.AMBULANCE_WAYPOINT_REACHED, mission.ambulanceId,
                    "Arrived back at base station " + fleetService.getAmbulanceOrThrow(mission.ambulanceId).getCurrentLocationNodeId(), now);
            mission.phase = null;
        }
    }

    private void rerouteAffectedMissions(String closedSource, String closedDest, Instant now) {
        for (ActiveMission mission : activeMissions.values()) {
            List<Edge> edges = (mission.route != null) ? mission.route.getEdges() : List.of();
            boolean affected = false;
            for (int i = mission.currentEdgeIndex; i < edges.size(); i++) {
                Edge e = edges.get(i);
                String s = e.getSource().map(Node::getId).orElse("");
                String d = e.getDestination().getId();
                if ((s.equals(closedSource) && d.equals(closedDest)) || !liveGraph.isRoadAvailable(s, d)) {
                    affected = true;
                    break;
                }
            }

            if (affected) {
                // Recalculate route from current location
                String currentLoc = fleetService.getAmbulanceOrThrow(mission.ambulanceId).getCurrentLocationNodeId();
                String targetNode;
                if (mission.phase == MissionPhase.EN_ROUTE_TO_SCENE) {
                    targetNode = emergencyService.getCaseOrThrow(mission.caseId).getPickupLocationNodeId();
                } else if (mission.phase == MissionPhase.TRANSPORTING_TO_HOSPITAL) {
                    targetNode = hospitalService.getHospitalOrThrow(mission.destinationHospitalId).getLocationNodeId();
                } else {
                    targetNode = getBaseNodeForAmbulance(mission.ambulanceId);
                }

                RouteResult detour = reroutingService.getDijkstra().findFastestRoute(liveGraph, currentLoc, targetNode);
                if (detour.isReachable()) {
                    mission.route = detour;
                    mission.currentEdgeIndex = 0;
                    mission.secondsOnCurrentEdge = 0.0;

                    recordEvent(SimulationEventType.ROUTE_RECALCULATED, mission.ambulanceId,
                            String.format("Detoured around closure %s->%s (new ETA: %.1f min)",
                                    closedSource, closedDest, detour.getTotalTravelTimeMinutes()), now);
                } else {
                    // Try alternative hospital if in TRANSPORTING phase
                    if (mission.phase == MissionPhase.TRANSPORTING_TO_HOSPITAL) {
                        EmergencyCase currentCase = emergencyService.getCaseOrThrow(mission.caseId);
                        HospitalSelectionResult altHosp = hospitalSelectionService.selectAndReserveHospital(currentCase, currentLoc);
                        if (altHosp.isSuccess()) {
                            Hospital chosen = altHosp.getSelectedHospital().orElseThrow().getHospital();
                            mission.destinationHospitalId = chosen.getId();
                            mission.route = altHosp.getSelectedHospital().orElseThrow().getRoute();
                            mission.currentEdgeIndex = 0;
                            mission.secondsOnCurrentEdge = 0.0;

                            recordEvent(SimulationEventType.ROUTE_RECALCULATED, mission.ambulanceId,
                                    String.format("Detoured to alternate hospital %s due to closure %s->%s",
                                            chosen.getName(), closedSource, closedDest), now);
                            continue;
                        }
                    }

                    // Target completely unreachable; mark route unreachable and halt movement
                    mission.route = RouteResult.unreachable(currentLoc, targetNode);
                    mission.currentEdgeIndex = 0;
                    mission.secondsOnCurrentEdge = 0.0;
                    recordEvent(SimulationEventType.INCIDENT_ACTIVATED, mission.ambulanceId,
                            String.format("Route blocked: Destination %s unreachable due to road closures", targetNode), now);
                }
            }
        }
    }

    private void rerouteBlockedMissions(Instant now) {
        for (ActiveMission mission : activeMissions.values()) {
            if (mission.route == null || !mission.route.isReachable()) {
                String currentLoc = fleetService.getAmbulanceOrThrow(mission.ambulanceId).getCurrentLocationNodeId();
                String targetNode;
                if (mission.phase == MissionPhase.EN_ROUTE_TO_SCENE) {
                    targetNode = emergencyService.getCaseOrThrow(mission.caseId).getPickupLocationNodeId();
                } else if (mission.phase == MissionPhase.TRANSPORTING_TO_HOSPITAL) {
                    targetNode = hospitalService.getHospitalOrThrow(mission.destinationHospitalId).getLocationNodeId();
                } else {
                    targetNode = getBaseNodeForAmbulance(mission.ambulanceId);
                }

                RouteResult resumeRoute = reroutingService.getDijkstra().findFastestRoute(liveGraph, currentLoc, targetNode);
                if (resumeRoute.isReachable()) {
                    mission.route = resumeRoute;
                    mission.currentEdgeIndex = 0;
                    mission.secondsOnCurrentEdge = 0.0;
                    recordEvent(SimulationEventType.ROUTE_RECALCULATED, mission.ambulanceId,
                            String.format("Resumed transit to %s after road reopening (new ETA: %.1f min)",
                                    targetNode, resumeRoute.getTotalTravelTimeMinutes()), now);
                }
            }
        }
    }

    /**
     * Orders an idle or available ambulance to return to its initial base station.
     *
     * @param ambulanceId ID of ambulance to dispatch back to base; must not be null.
     * @return true if return route successfully calculated and initiated.
     */
    public synchronized boolean returnAmbulanceToBase(String ambulanceId) {
        Ambulance amb = fleetService.getAmbulanceOrThrow(ambulanceId);
        if (amb.getStatus() != AmbulanceStatus.AVAILABLE && amb.getStatus() != AmbulanceStatus.RETURNING) {
            return false;
        }

        String baseNode = getBaseNodeForAmbulance(ambulanceId);
        if (baseNode.equals(amb.getCurrentLocationNodeId())) {
            fleetService.updateAmbulanceStatus(ambulanceId, AmbulanceStatus.AVAILABLE);
            return true;
        }

        RouteResult route = reroutingService.getDijkstra().findFastestRoute(liveGraph, amb.getCurrentLocationNodeId(), baseNode);
        if (route.isReachable()) {
            fleetService.updateAmbulanceStatus(ambulanceId, AmbulanceStatus.RETURNING);
            ActiveMission mission = new ActiveMission(ambulanceId, null, MissionPhase.RETURNING_TO_BASE, route);
            activeMissions.put(ambulanceId, mission);

            recordEvent(SimulationEventType.ROUTE_RECALCULATED, ambulanceId,
                    String.format("Returning to base %s (ETA: %.1f min)", baseNode, route.getTotalTravelTimeMinutes()), simulationClock.instant());
            return true;
        }
        return false;
    }

    /**
     * Returns full movement and position telemetry for all ambulances in the fleet.
     *
     * <p>Interpolates geospatial coordinates along active road corridors using current logical simulation time,
     * calculates overall route completion progress, remaining ETA, and visual presentation state.</p>
     */
    public synchronized List<AmbulanceMovementTelemetry> getAmbulanceTelemetry() {
        List<AmbulanceMovementTelemetry> list = new ArrayList<>();
        for (Ambulance amb : fleetService.getAllAmbulances()) {
            list.add(calculateTelemetryForAmbulance(amb));
        }
        return Collections.unmodifiableList(list);
    }

    private AmbulanceMovementTelemetry calculateTelemetryForAmbulance(Ambulance amb) {
        ActiveMission mission = activeMissions.get(amb.getId());
        String currentLocId = amb.getCurrentLocationNodeId();
        Node currentLocNode = liveGraph.getNode(currentLocId).orElse(null);
        GeoCoordinate defaultCoord = (currentLocNode != null)
                ? currentLocNode.getCoordinates()
                : new GeoCoordinate(40.7128, -74.0060);

        if (mission == null) {
            VehicleVisualState visualState = switch (amb.getStatus()) {
                case AVAILABLE -> VehicleVisualState.AVAILABLE;
                case ASSIGNED, EN_ROUTE -> VehicleVisualState.RESPONDING;
                case ON_SCENE -> VehicleVisualState.AT_SCENE;
                case TRANSPORTING -> VehicleVisualState.TRANSPORTING;
                case RETURNING -> VehicleVisualState.RETURNING;
                case OUT_OF_SERVICE -> VehicleVisualState.OUT_OF_SERVICE;
            };
            String destName = (amb.getStatus() == AmbulanceStatus.ON_SCENE)
                    ? "On Scene (" + currentLocId + ")"
                    : "Stationed at " + currentLocId;

            return new AmbulanceMovementTelemetry(
                    amb.getId(),
                    amb.getStatus(),
                    visualState,
                    amb.getAssignedCaseId().orElse(null),
                    currentLocId,
                    currentLocId,
                    currentLocId,
                    destName,
                    defaultCoord,
                    1.0,
                    0.0,
                    null,
                    0,
                    0.0,
                    false
            );
        }

        // Active Mission
        VehicleVisualState visualState;
        String destName;
        String destNodeId;

        if (mission.phase == MissionPhase.EN_ROUTE_TO_SCENE) {
            visualState = VehicleVisualState.RESPONDING;
            destNodeId = emergencyService.getCase(mission.caseId)
                    .map(EmergencyCase::getPickupLocationNodeId)
                    .orElse(currentLocId);
            destName = "Scene (" + destNodeId + ")";
        } else if (mission.phase == MissionPhase.TRANSPORTING_TO_HOSPITAL) {
            visualState = VehicleVisualState.TRANSPORTING;
            Hospital h = (mission.destinationHospitalId != null) ? hospitalService.getHospital(mission.destinationHospitalId).orElse(null) : null;
            destNodeId = (h != null) ? h.getLocationNodeId() : currentLocId;
            destName = (h != null) ? h.getName() : "Hospital (" + destNodeId + ")";
        } else {
            visualState = VehicleVisualState.RETURNING;
            destNodeId = getBaseNodeForAmbulance(amb.getId());
            destName = "Base Station (" + destNodeId + ")";
        }

        RouteResult route = mission.route;
        if (route == null || !route.isReachable() || route.getEdges().isEmpty()) {
            boolean blocked = (route != null && !route.isReachable());
            return new AmbulanceMovementTelemetry(
                    amb.getId(),
                    amb.getStatus(),
                    visualState,
                    mission.caseId,
                    currentLocId,
                    currentLocId,
                    destNodeId,
                    destName,
                    defaultCoord,
                    blocked ? 0.0 : 1.0,
                    0.0,
                    route,
                    0,
                    0.0,
                    blocked
            );
        }

        List<Edge> edges = route.getEdges();
        int edgeIdx = Math.min(mission.currentEdgeIndex, edges.size() - 1);
        Edge curEdge = edges.get(edgeIdx);

        Node src = curEdge.getSource().orElse(currentLocNode);
        Node dst = curEdge.getDestination();

        double curEdgeTravelSec = Math.max(0.1, curEdge.getTravelTimeMinutes() * 60.0);
        double edgeProgress = Math.min(1.0, Math.max(0.0, mission.secondsOnCurrentEdge / curEdgeTravelSec));

        double lat1 = (src != null) ? src.getCoordinates().latitude() : defaultCoord.latitude();
        double lon1 = (src != null) ? src.getCoordinates().longitude() : defaultCoord.longitude();
        double lat2 = dst.getCoordinates().latitude();
        double lon2 = dst.getCoordinates().longitude();

        double interpLat = lat1 + edgeProgress * (lat2 - lat1);
        double interpLon = lon1 + edgeProgress * (lon2 - lon1);
        GeoCoordinate interpolatedCoord = new GeoCoordinate(interpLat, interpLon);

        double heading = calculateHeading(lat1, lon1, lat2, lon2);

        // Overall progress & remaining travel time
        double totalRouteSec = 0.0;
        double traversedSec = 0.0;
        for (int i = 0; i < edges.size(); i++) {
            double eSec = edges.get(i).getTravelTimeMinutes() * 60.0;
            totalRouteSec += eSec;
            if (i < edgeIdx) {
                traversedSec += eSec;
            }
        }
        traversedSec += Math.min(curEdgeTravelSec, mission.secondsOnCurrentEdge);

        double overallProgress = (totalRouteSec > 0) ? Math.min(1.0, Math.max(0.0, traversedSec / totalRouteSec)) : 1.0;
        double remainingSec = Math.max(0.0, totalRouteSec - traversedSec);

        String finalDestNode = edges.get(edges.size() - 1).getDestination().getId();

        return new AmbulanceMovementTelemetry(
                amb.getId(),
                amb.getStatus(),
                visualState,
                mission.caseId,
                (src != null ? src.getId() : currentLocId),
                dst.getId(),
                finalDestNode,
                destName,
                interpolatedCoord,
                overallProgress,
                remainingSec,
                route,
                edgeIdx,
                heading,
                false
        );
    }

    private String getBaseNodeForAmbulance(String ambId) {
        return scenario.getInitialAmbulances().stream()
                .filter(a -> a.getId().equals(ambId))
                .findFirst()
                .map(Ambulance::getCurrentLocationNodeId)
                .orElseGet(() -> fleetService.getAmbulanceOrThrow(ambId).getCurrentLocationNodeId());
    }

    private static double calculateHeading(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        if (Math.abs(dLat) < 1e-9 && Math.abs(dLon) < 1e-9) {
            return 0.0;
        }
        double deg = Math.toDegrees(Math.atan2(dLon, dLat));
        return (deg < 0) ? deg + 360.0 : deg;
    }

    /**
     * Returns the active reachable routes of all en-route, transporting, and returning ambulances.
     */
    public synchronized List<RouteResult> getActiveMissionRoutes() {
        List<RouteResult> routes = new ArrayList<>();
        for (ActiveMission mission : activeMissions.values()) {
            if (mission.route != null && mission.route.isReachable() && !mission.route.getEdges().isEmpty()) {
                routes.add(mission.route);
            }
        }
        return Collections.unmodifiableList(routes);
    }

    private void recordEvent(SimulationEventType type, String entityId, String msg, Instant now) {
        SimulationEvent event = new SimulationEvent(
                eventSequence.getAndIncrement(),
                now,
                elapsedSimulationTime,
                type,
                entityId,
                msg
        );
        eventLog.add(event);
    }

    private RoadGraph copyGraph(RoadGraph original) {
        RoadGraph copy = new RoadGraph();
        for (Node node : original.getAllNodes()) {
            copy.addNode(new Node(node.getId(), node.getCoordinates().latitude(), node.getCoordinates().longitude(), node.getName()));
        }
        for (Edge edge : original.getAllEdges()) {
            String src = edge.getSource().map(Node::getId).orElseThrow();
            String dst = edge.getDestination().getId();
            copy.addEdge(src, dst, edge.getDistanceKm(), edge.getBaseTravelTimeMinutes(), edge.getRoadName());
        }
        return copy;
    }

    /**
     * Calculates telemetry and audit metrics based on simulation state.
     */
    public synchronized SimulationMetrics getMetrics() {
        int totalReported = triggeredEmergencyIds.size();
        int totalDispatched = caseDispatchTimes.size();
        int totalCompleted = completedCaseIds.size();
        int totalCancelled = cancelledCaseIds.size();
        int totalUnserved = totalReported - totalCompleted - totalCancelled;

        double avgDispatchLatency = calculateAverageMinutes(caseReportTimes, caseDispatchTimes);
        double avgResponseTime = calculateAverageMinutes(caseReportTimes, caseResponseTimes);
        double avgDeliveryTime = calculateAverageMinutes(caseReportTimes, caseDeliveryTimes);

        // Fleet utilization: active seconds / (total simulation seconds * total ambulances)
        long totalSimSeconds = Math.max(1, elapsedSimulationTime.toSeconds());
        int ambCount = Math.max(1, scenario.getInitialAmbulances().size());
        long totalCapacitySeconds = totalSimSeconds * ambCount;

        long sumActiveSeconds = ambulanceActiveSeconds.values().stream().mapToLong(Long::longValue).sum();
        double overallUtilization = Math.min(1.0, (double) sumActiveSeconds / totalCapacitySeconds);

        Map<String, Double> ambUtilization = new HashMap<>();
        for (Map.Entry<String, Long> entry : ambulanceActiveSeconds.entrySet()) {
            ambUtilization.put(entry.getKey(), Math.min(1.0, (double) entry.getValue() / totalSimSeconds));
        }

        return new SimulationMetrics(
                totalReported,
                totalDispatched,
                totalCompleted,
                totalCancelled,
                totalUnserved,
                avgDispatchLatency,
                avgResponseTime,
                avgDeliveryTime,
                overallUtilization,
                ambUtilization
        );
    }

    private double calculateAverageMinutes(Map<String, Instant> startMap, Map<String, Instant> endMap) {
        List<Double> durations = new ArrayList<>();
        for (Map.Entry<String, Instant> entry : endMap.entrySet()) {
            Instant start = startMap.get(entry.getKey());
            if (start != null) {
                double mins = Duration.between(start, entry.getValue()).toMillis() / 60000.0;
                durations.add(mins);
            }
        }
        return durations.isEmpty() ? 0.0 : durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public CityScenario getScenario() {
        return scenario;
    }

    public Duration getElapsedSimulationTime() {
        return elapsedSimulationTime;
    }

    public Instant getCurrentInstant() {
        return simulationClock.instant();
    }

    public List<SimulationEvent> getSimulationEvents() {
        return Collections.unmodifiableList(eventLog);
    }

    public RoadGraph getLiveGraph() {
        return liveGraph;
    }

    public AmbulanceFleetService getFleetService() {
        return fleetService;
    }

    public EmergencyManagementService getEmergencyService() {
        return emergencyService;
    }

    public EmergencyDispatchQueueService getQueueService() {
        return queueService;
    }

    public HospitalManagementService getHospitalService() {
        return hospitalService;
    }

    public HospitalSelectionService getHospitalSelectionService() {
        return hospitalSelectionService;
    }

    public RoadIncidentService getIncidentService() {
        return incidentService;
    }

    public TrafficSimulationEngine getTrafficEngine() {
        return trafficEngine;
    }
}
