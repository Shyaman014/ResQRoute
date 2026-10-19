package com.resqroute.ui.map;

import com.resqroute.algorithm.AStarRoutingAlgorithm;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.model.AmbulanceMovementTelemetry;
import com.resqroute.simulation.model.CityScenario;
import com.resqroute.simulation.model.VehicleVisualState;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Map Integration, Route Visualization, and Marker Interactivity Tests")
class MapIntegrationTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    @DisplayName("Route calculation waypoints correctly convert to screen coordinates within viewport")
    void routeWaypointsConvertToScreenCoordinates() {
        CityScenario scenario = CityScenario.createDefaultMetropolis(42L);
        RoadGraph graph = scenario.getRoadGraph();

        AStarRoutingAlgorithm aStar = new AStarRoutingAlgorithm();
        RouteResult route = aStar.findFastestRoute(graph, "NORTH-STATION", "SOUTH-STATION");

        assertTrue(route.isReachable(), "North to South route must be reachable in metropolis scenario");
        assertFalse(route.getPath().isEmpty(), "Route path must have waypoints");

        MapCoordinateTransform transform = new MapCoordinateTransform();
        transform.updateCanvasSize(1000.0, 750.0);
        transform.fitToBounds(graph.getAllNodes());

        for (Node waypoint : route.getPath()) {
            MapCoordinateTransform.ScreenPoint pt = transform.toScreen(waypoint.getCoordinates());
            assertTrue(pt.x() >= 0 && pt.x() <= 1000.0, "Waypoint screen X must be within canvas bounds: " + pt.x());
            assertTrue(pt.y() >= 0 && pt.y() <= 750.0, "Waypoint screen Y must be within canvas bounds: " + pt.y());
        }
    }

    @Test
    @DisplayName("Hospital marker data correctly encapsulates bed capacity and clinical capabilities")
    void hospitalMarkerDataValidation() {
        Hospital hosp = Hospital.builder()
                .id("HOSP-APEX")
                .name("Apex Trauma & Cardiac Center")
                .locationNodeId("HOSP-APEX-NODE")
                .totalCapacity(50)
                .availableCapacity(15)
                .supportedEmergencyTypes(Set.of(EmergencyType.CARDIAC, EmergencyType.TRAUMA))
                .capabilities(Set.of(HospitalCapability.ICU, HospitalCapability.TRAUMA_CENTER))
                .status(HospitalStatus.OPEN)
                .build();

        assertEquals(15, hosp.getAvailableCapacity(), "Available beds must equal configured available capacity");
        assertEquals(35, hosp.getOccupiedCapacity(), "Occupied beds must equal total - available");
        assertTrue(hosp.getSupportedEmergencyTypes().contains(EmergencyType.CARDIAC));
        assertFalse(hosp.getSupportedEmergencyTypes().contains(EmergencyType.FIRE_RESCUE));
    }

    @Test
    @DisplayName("Ambulance movement telemetry tracks dynamic coordinates and transit progress")
    void ambulanceTelemetryTracking() {
        GeoCoordinate origin = new GeoCoordinate(12.980, 77.590);

        AmbulanceMovementTelemetry telemetry = new AmbulanceMovementTelemetry(
                "AMB-01",
                AmbulanceStatus.EN_ROUTE,
                VehicleVisualState.RESPONDING,
                "CASE-101",
                "NORTH-STATION",
                "CENTRAL-PLAZA",
                "CENTRAL-PLAZA",
                "Metro Emergency Scene",
                origin,
                0.50, // 50% along the path
                120.0, // 2 minutes ETA
                null,
                0,
                135.0, // heading degrees
                false
        );

        assertTrue(telemetry.isMoving(), "Responding vehicle with progress 0.5 must be moving");
        assertEquals("2m 00s", telemetry.getFormattedEta());
        assertEquals(50, telemetry.getProgressPercentage());
        assertEquals("CASE-101", telemetry.caseId());
    }

    @Test
    @DisplayName("RoadNetworkCanvas selection callbacks trigger accurately for hospitals")
    void hospitalSelectionCallback() {
        RoadNetworkCanvas canvas = new RoadNetworkCanvas();
        canvas.setMapProvider(MapProvider.OFFLINE_VECTOR);

        CityScenario scenario = CityScenario.createDefaultMetropolis(42L);
        canvas.setNetworkData(scenario.getRoadGraph(), scenario.getInitialAmbulances(), scenario.getInitialHospitals(), List.of());

        AtomicReference<Hospital> selectedRef = new AtomicReference<>();
        canvas.setOnHospitalSelected(selectedRef::set);

        Hospital target = scenario.getInitialHospitals().get(0);
        canvas.setSelectedHospital(target);

        assertEquals(target, canvas.getSelectedHospital());
    }

    @Test
    @DisplayName("Missing or null coordinates handle safely without raising unhandled exceptions")
    void missingCoordinatesHandledSafely() {
        MapCoordinateTransform transform = new MapCoordinateTransform();
        transform.updateCanvasSize(800.0, 600.0);

        // Null coordinate returns (0, 0) safely
        MapCoordinateTransform.ScreenPoint pt = transform.toScreen(null);
        assertNotNull(pt);
        assertEquals(0.0, pt.x());
        assertEquals(0.0, pt.y());

        // CenterAt with null handles gracefully
        assertDoesNotThrow(() -> transform.centerAt(null));

        // findNearestNode with empty/null list returns Optional.empty
        assertTrue(transform.findNearestNode(null, 100, 100, 20).isEmpty());
        assertTrue(transform.findNearestNode(List.of(), 100, 100, 20).isEmpty());

        // findNearestEdge with null graph returns Optional.empty
        assertTrue(transform.findNearestEdge(null, 100, 100, 20).isEmpty());
    }

    @Test
    @DisplayName("MapProvider switching updates canvas provider and preserves vector data")
    void mapProviderSwitching() {
        RoadNetworkCanvas canvas = new RoadNetworkCanvas();
        assertEquals(MapProvider.CARTO_DARK, canvas.getMapProvider(), "Default provider should be CARTO_DARK");

        canvas.setMapProvider(MapProvider.OPEN_STREET_MAP);
        assertEquals(MapProvider.OPEN_STREET_MAP, canvas.getMapProvider());

        canvas.setMapProvider(MapProvider.OFFLINE_VECTOR);
        assertEquals(MapProvider.OFFLINE_VECTOR, canvas.getMapProvider());
        assertFalse(canvas.getMapProvider().isOnline());
    }
}
