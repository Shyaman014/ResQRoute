package com.resqroute.simulation.model;

import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Encapsulates a complete, reproducible simulation scenario including road topography,
 * initial ambulance positions, hospitals, scheduled incidents, and emergency generation parameters.
 */
public final class CityScenario {

    private final String name;
    private final RoadGraph roadGraph;
    private final List<Ambulance> initialAmbulances;
    private final List<Hospital> initialHospitals;
    private final List<ScheduledEmergencyEvent> scheduledEmergencies;
    private final List<ScheduledIncidentEvent> scheduledIncidents;
    private final Duration simulationDuration;
    private final Duration timeStep;
    private final long randomSeed;
    private final Instant baseTime;

    public CityScenario(String name,
                        RoadGraph roadGraph,
                        List<Ambulance> initialAmbulances,
                        List<Hospital> initialHospitals,
                        List<ScheduledEmergencyEvent> scheduledEmergencies,
                        List<ScheduledIncidentEvent> scheduledIncidents,
                        Duration simulationDuration,
                        Duration timeStep,
                        long randomSeed,
                        Instant baseTime) {
        this.name = (name == null || name.trim().isEmpty()) ? "Unnamed Scenario" : name.trim();
        this.roadGraph = Objects.requireNonNull(roadGraph, "RoadGraph must not be null");
        this.initialAmbulances = (initialAmbulances == null) ? List.of() : List.copyOf(initialAmbulances);
        this.initialHospitals = (initialHospitals == null) ? List.of() : List.copyOf(initialHospitals);
        this.scheduledEmergencies = (scheduledEmergencies == null) ? List.of() : List.copyOf(scheduledEmergencies);
        this.scheduledIncidents = (scheduledIncidents == null) ? List.of() : List.copyOf(scheduledIncidents);
        this.simulationDuration = Objects.requireNonNull(simulationDuration, "Simulation duration must not be null");
        this.timeStep = Objects.requireNonNull(timeStep, "Time step must not be null");
        this.randomSeed = randomSeed;
        this.baseTime = (baseTime == null) ? Instant.parse("2026-10-18T08:00:00Z") : baseTime;

        validate();
    }

    /**
     * Validates that all entities reference valid nodes in the road graph and parameters are within legal bounds.
     */
    public void validate() {
        if (simulationDuration.isZero() || simulationDuration.isNegative()) {
            throw new IllegalArgumentException("Simulation duration must be positive: " + simulationDuration);
        }
        if (timeStep.isZero() || timeStep.isNegative()) {
            throw new IllegalArgumentException("Time step must be positive: " + timeStep);
        }
        if (timeStep.compareTo(simulationDuration) > 0) {
            throw new IllegalArgumentException("Time step cannot exceed simulation duration");
        }
        if (roadGraph.getNodeCount() == 0) {
            throw new IllegalArgumentException("Road graph cannot be empty");
        }

        // Validate ambulance locations
        for (Ambulance amb : initialAmbulances) {
            if (!roadGraph.containsNode(amb.getCurrentLocationNodeId())) {
                throw new NodeNotFoundException(amb.getCurrentLocationNodeId());
            }
        }

        // Validate hospital locations
        for (Hospital hosp : initialHospitals) {
            if (!roadGraph.containsNode(hosp.getLocationNodeId())) {
                throw new NodeNotFoundException(hosp.getLocationNodeId());
            }
        }

        // Validate scheduled emergency pickup locations
        for (ScheduledEmergencyEvent event : scheduledEmergencies) {
            if (!roadGraph.containsNode(event.pickupNodeId())) {
                throw new NodeNotFoundException(event.pickupNodeId());
            }
        }

        // Validate scheduled incident endpoints
        for (ScheduledIncidentEvent event : scheduledIncidents) {
            if (!roadGraph.containsNode(event.sourceNodeId())) {
                throw new NodeNotFoundException(event.sourceNodeId());
            }
            if (!roadGraph.containsNode(event.destinationNodeId())) {
                throw new NodeNotFoundException(event.destinationNodeId());
            }
        }
    }

    /**
     * Constructs a standard metropolitan city scenario for testing and baseline demonstrations.
     */
    public static CityScenario createDefaultMetropolis(long seed) {
        RoadGraph graph = new RoadGraph();

        // Nodes
        Node nNorth = new Node("NORTH-STATION", 12.980, 77.590, "North Station");
        Node nSouth = new Node("SOUTH-STATION", 12.940, 77.590, "South Station");
        Node nCentral = new Node("CENTRAL-PLAZA", 12.960, 77.600, "Central Plaza");
        Node nWest = new Node("WEST-INDUSTRIAL", 12.960, 77.570, "West Industrial");
        Node nEast = new Node("EAST-COMMERCE", 12.960, 77.630, "East Commercial");
        Node nHospApex = new Node("HOSP-APEX-NODE", 12.970, 77.610, "Apex Trauma Hospital");
        Node nHospMetro = new Node("HOSP-METRO-NODE", 12.950, 77.580, "Metro Community Hospital");

        graph.addNode(nNorth);
        graph.addNode(nSouth);
        graph.addNode(nCentral);
        graph.addNode(nWest);
        graph.addNode(nEast);
        graph.addNode(nHospApex);
        graph.addNode(nHospMetro);

        // Network Edges (bidirectional)
        graph.addBidirectionalEdge("NORTH-STATION", "CENTRAL-PLAZA", 3.0, 4.0, "North Ave");
        graph.addBidirectionalEdge("SOUTH-STATION", "CENTRAL-PLAZA", 3.0, 4.0, "South Ave");
        graph.addBidirectionalEdge("WEST-INDUSTRIAL", "CENTRAL-PLAZA", 4.0, 5.0, "West Blvd");
        graph.addBidirectionalEdge("EAST-COMMERCE", "CENTRAL-PLAZA", 4.0, 5.0, "East Blvd");
        graph.addBidirectionalEdge("CENTRAL-PLAZA", "HOSP-APEX-NODE", 2.0, 3.0, "Hospital Way N");
        graph.addBidirectionalEdge("CENTRAL-PLAZA", "HOSP-METRO-NODE", 2.5, 3.5, "Hospital Way S");
        graph.addBidirectionalEdge("NORTH-STATION", "WEST-INDUSTRIAL", 5.0, 7.0, "Northwest Ring");
        graph.addBidirectionalEdge("SOUTH-STATION", "EAST-COMMERCE", 5.0, 7.0, "Southeast Ring");

        // Ambulances
        Ambulance ambNorth = new Ambulance("AMB-NORTH", "NORTH-STATION",
                Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT, AmbulanceCapability.ADVANCED_LIFE_SUPPORT, AmbulanceCapability.CARDIAC_CARE));
        Ambulance ambSouth = new Ambulance("AMB-SOUTH", "SOUTH-STATION",
                Set.of(AmbulanceCapability.BASIC_LIFE_SUPPORT, AmbulanceCapability.ADVANCED_LIFE_SUPPORT, AmbulanceCapability.CARDIAC_CARE));

        // Hospitals
        Hospital hospApex = Hospital.builder()
                .id("HOSP-APEX")
                .name("Apex Trauma & Cardiac Center")
                .locationNodeId("HOSP-APEX-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.CARDIAC, EmergencyType.TRAUMA, EmergencyType.MEDICAL))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM, HospitalCapability.TRAUMA_CENTER, HospitalCapability.CARDIAC_CATH_LAB, HospitalCapability.ICU))
                .totalCapacity(10)
                .availableCapacity(8)
                .build();

        Hospital hospMetro = Hospital.builder()
                .id("HOSP-METRO")
                .name("Metro Community Hospital")
                .locationNodeId("HOSP-METRO-NODE")
                .supportedEmergencyTypes(Set.of(EmergencyType.MEDICAL, EmergencyType.OTHER))
                .capabilities(Set.of(HospitalCapability.EMERGENCY_ROOM))
                .totalCapacity(6)
                .availableCapacity(4)
                .build();

        // Scheduled Emergencies
        List<ScheduledEmergencyEvent> emergencies = List.of(
                new ScheduledEmergencyEvent(Duration.ofMinutes(1), "CASE-01", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "CENTRAL-PLAZA", "Cardiac arrest"),
                new ScheduledEmergencyEvent(Duration.ofMinutes(3), "CASE-02", EmergencyType.TRAUMA, EmergencySeverity.HIGH, "WEST-INDUSTRIAL", "Machine trauma"),
                new ScheduledEmergencyEvent(Duration.ofMinutes(6), "CASE-03", EmergencyType.MEDICAL, EmergencySeverity.MEDIUM, "EAST-COMMERCE", "Severe asthma")
        );

        // Scheduled Incidents
        List<ScheduledIncidentEvent> incidents = List.of(
                new ScheduledIncidentEvent(Duration.ofMinutes(4), Duration.ofMinutes(10), "INC-01", "NORTH-STATION", "CENTRAL-PLAZA", "Collision on North Ave")
        );

        return new CityScenario(
                "Metropolis Standard 15",
                graph,
                List.of(ambNorth, ambSouth),
                List.of(hospApex, hospMetro),
                emergencies,
                incidents,
                Duration.ofMinutes(30),
                Duration.ofSeconds(10),
                seed,
                Instant.parse("2026-10-18T08:00:00Z")
        );
    }

    public String getName() {
        return name;
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }

    public List<Ambulance> getInitialAmbulances() {
        return initialAmbulances;
    }

    public List<Hospital> getInitialHospitals() {
        return initialHospitals;
    }

    public List<ScheduledEmergencyEvent> getScheduledEmergencies() {
        return scheduledEmergencies;
    }

    public List<ScheduledIncidentEvent> getScheduledIncidents() {
        return scheduledIncidents;
    }

    public Duration getSimulationDuration() {
        return simulationDuration;
    }

    public Duration getTimeStep() {
        return timeStep;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public Instant getBaseTime() {
        return baseTime;
    }
}
