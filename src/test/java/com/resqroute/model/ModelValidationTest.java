package com.resqroute.model;

import com.resqroute.exception.InvalidCostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Models Validation Tests")
class ModelValidationTest {

    @Test
    @DisplayName("GeoCoordinate should accept valid latitude and longitude")
    void validCoordinates() {
        GeoCoordinate coord = new GeoCoordinate(12.9716, 77.5946);
        assertEquals(12.9716, coord.latitude(), 1e-6);
        assertEquals(77.5946, coord.longitude(), 1e-6);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.001, 90.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("GeoCoordinate should reject invalid latitudes")
    void invalidLatitude(double lat) {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(lat, 0.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.001, 180.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("GeoCoordinate should reject invalid longitudes")
    void invalidLongitude(double lon) {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, lon));
    }

    @Test
    @DisplayName("Haversine formula computes accurate distance between known coordinates")
    void haversineDistanceCalculation() {
        // Bangalore Central (12.9716, 77.5946) to Bangalore Airport (13.1986, 77.7066) ~27.7 km
        GeoCoordinate central = new GeoCoordinate(12.9716, 77.5946);
        GeoCoordinate airport = new GeoCoordinate(13.1986, 77.7066);
        double dist = central.haversineDistanceKm(airport);
        assertTrue(dist > 25.0 && dist < 30.0, "Expected distance around 27.7 km, got: " + dist);
    }

    @Test
    @DisplayName("Node equality is strictly based on unique identifier")
    void nodeEquality() {
        Node n1 = new Node("A", 10.0, 20.0, "Node Alpha");
        Node n2 = new Node("A", 30.0, 40.0, "Node Alpha Updated");
        Node n3 = new Node("B", 10.0, 20.0, "Node Beta");

        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());
        assertNotEquals(n1, n3);
    }

    @Test
    @DisplayName("Node constructor rejects null or blank IDs")
    void nodeBlankIdRejection() {
        assertThrows(IllegalArgumentException.class, () -> new Node(null, 10.0, 20.0));
        assertThrows(IllegalArgumentException.class, () -> new Node("   ", 10.0, 20.0));
    }

    @Test
    @DisplayName("Edge rejects negative or invalid travel times and distances")
    void edgeCostValidation() {
        Node dest = new Node("D", 10.0, 20.0);

        assertThrows(InvalidCostException.class, () -> new Edge(dest, -1.0, 5.0));
        assertThrows(InvalidCostException.class, () -> new Edge(dest, 5.0, -0.1));
        assertThrows(InvalidCostException.class, () -> new Edge(dest, Double.NaN, 5.0));
        assertThrows(InvalidCostException.class, () -> new Edge(dest, 5.0, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> new Edge(null, 5.0, 5.0));
    }

    @Test
    @DisplayName("Edge calculates average vehicle speed correctly")
    void edgeAverageSpeed() {
        Node dest = new Node("D", 10.0, 20.0);
        Edge edge = new Edge(dest, 30.0, 30.0); // 30 km in 30 minutes = 60 km/h
        assertEquals(60.0, edge.getAverageSpeedKmh(), 1e-4);

        Edge zeroTimeEdge = new Edge(dest, 10.0, 0.0);
        assertEquals(0.0, zeroTimeEdge.getAverageSpeedKmh(), 1e-4);
    }

    @Test
    @DisplayName("RouteResult enforces unmodifiable path list")
    void routeResultImmutability() {
        Node n1 = new Node("A", 10.0, 20.0);
        Node n2 = new Node("B", 10.0, 21.0);
        RouteResult result = RouteResult.reachable(List.of(n1, n2), 5.0, 10.0);

        assertTrue(result.isReachable());
        assertEquals(2, result.getNodeCount());
        assertThrows(UnsupportedOperationException.class, () -> result.getPath().add(new Node("C", 10.0, 22.0)));
    }

    @Test
    @DisplayName("Edge keeps distance and travel time separate and supports source node")
    void edgeSourceAndMetricsSeparation() {
        Node src = new Node("SRC", 10.0, 20.0);
        Node dst = new Node("DST", 11.0, 21.0);

        Edge edgeWithSource = new Edge(src, dst, 15.0, 12.0, "State Highway 1");
        assertTrue(edgeWithSource.getSource().isPresent());
        assertEquals(src, edgeWithSource.getSource().get());
        assertEquals(dst, edgeWithSource.getDestination());
        assertEquals(15.0, edgeWithSource.getDistanceKm());
        assertEquals(12.0, edgeWithSource.getTravelTimeMinutes());
        assertEquals("State Highway 1", edgeWithSource.getRoadName());

        Edge edgeWithoutSource = new Edge(dst, 10.0, 10.0);
        assertTrue(edgeWithoutSource.getSource().isEmpty());

        Edge updatedWithSource = edgeWithoutSource.withSource(src);
        assertTrue(updatedWithSource.getSource().isPresent());
        assertEquals(src, updatedWithSource.getSource().get());
    }

    @Test
    @DisplayName("Edge with dynamic TrafficCondition scales travel time but preserves base time and distance")
    void edgeDynamicTrafficSeparation() {
        Node src = new Node("SRC", 10.0, 20.0);
        Node dst = new Node("DST", 11.0, 21.0);

        Edge baseEdge = new Edge(src, dst, 10.0, 10.0, "Avenue");
        assertEquals(10.0, baseEdge.getBaseTravelTimeMinutes());
        assertEquals(10.0, baseEdge.getTravelTimeMinutes());

        TrafficCondition heavy = TrafficCondition.of(TrafficLevel.HEAVY, "CAMERA_FEED");
        Edge heavyEdge = baseEdge.withTrafficCondition(heavy);

        assertEquals(10.0, heavyEdge.getDistanceKm(), "Distance must not be affected by traffic");
        assertEquals(10.0, heavyEdge.getBaseTravelTimeMinutes(), "Base travel time must remain static");
        assertEquals(22.0, heavyEdge.getTravelTimeMinutes(), 1e-4, "Current travel time must reflect congestion multiplier");
        assertEquals(heavy, heavyEdge.getTrafficCondition());
    }
}


