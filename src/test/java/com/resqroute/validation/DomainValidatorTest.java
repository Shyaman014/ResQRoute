package com.resqroute.validation;

import com.resqroute.exception.InvalidCostException;
import com.resqroute.exception.InvalidRouteException;
import com.resqroute.exception.ValidationException;
import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 21: DomainValidator Boundary and Invariant Tests")
class DomainValidatorTest {

    @Test
    @DisplayName("validateNodeId returns trimmed string for valid input and rejects null or blank")
    void testValidateNodeId() {
        assertEquals("NODE_1", DomainValidator.validateNodeId(" NODE_1 ", "Node ID"));
        assertEquals("A", DomainValidator.validateNodeId("A", "Origin"));

        assertThrows(ValidationException.class, () -> DomainValidator.validateNodeId(null, "Node ID"));
        assertThrows(ValidationException.class, () -> DomainValidator.validateNodeId("", "Node ID"));
        assertThrows(ValidationException.class, () -> DomainValidator.validateNodeId("   ", "Node ID"));
    }

    @Test
    @DisplayName("validateCoordinates accepts valid geographic boundaries")
    void testValidateCoordinatesValid() {
        assertDoesNotThrow(() -> DomainValidator.validateCoordinates(0.0, 0.0));
        assertDoesNotThrow(() -> DomainValidator.validateCoordinates(90.0, 180.0));
        assertDoesNotThrow(() -> DomainValidator.validateCoordinates(-90.0, -180.0));
        assertDoesNotThrow(() -> DomainValidator.validateCoordinates(12.9716, 77.5946));
    }

    @ParameterizedTest
    @ValueSource(doubles = {90.0001, -90.0001, 150.0, -120.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("validateCoordinates rejects invalid latitudes")
    void testValidateCoordinatesInvalidLatitude(double invalidLat) {
        assertThrows(ValidationException.class, () -> DomainValidator.validateCoordinates(invalidLat, 0.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {180.0001, -180.0001, 200.0, -250.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("validateCoordinates rejects invalid longitudes")
    void testValidateCoordinatesInvalidLongitude(double invalidLon) {
        assertThrows(ValidationException.class, () -> DomainValidator.validateCoordinates(0.0, invalidLon));
    }

    @Test
    @DisplayName("validateRoadWeights accepts non-negative finite distances and travel times")
    void testValidateRoadWeightsValid() {
        assertDoesNotThrow(() -> DomainValidator.validateRoadWeights(0.0, 0.0));
        assertDoesNotThrow(() -> DomainValidator.validateRoadWeights(5.5, 12.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, -10.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("validateRoadWeights rejects negative or non-finite road distance")
    void testValidateRoadWeightsInvalidDistance(double invalidDist) {
        assertThrows(InvalidCostException.class, () -> DomainValidator.validateRoadWeights(invalidDist, 10.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, -10.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("validateRoadWeights rejects negative or non-finite travel time")
    void testValidateRoadWeightsInvalidTime(double invalidTime) {
        assertThrows(InvalidCostException.class, () -> DomainValidator.validateRoadWeights(10.0, invalidTime));
    }

    @Test
    @DisplayName("validateRouteEndpoints rejects identical origin and destination")
    void testValidateRouteEndpointsIdentical() {
        assertThrows(InvalidRouteException.class, () -> DomainValidator.validateRouteEndpoints("NODE_A", "NODE_A"));
        assertThrows(InvalidRouteException.class, () -> DomainValidator.validateRouteEndpoints("NODE_A", " NODE_A "));
        assertDoesNotThrow(() -> DomainValidator.validateRouteEndpoints("NODE_A", "NODE_B"));
    }

    @Test
    @DisplayName("validateRouteEndpoints rejects null or blank endpoints")
    void testValidateRouteEndpointsNullOrBlank() {
        assertThrows(ValidationException.class, () -> DomainValidator.validateRouteEndpoints(null, "NODE_B"));
        assertThrows(ValidationException.class, () -> DomainValidator.validateRouteEndpoints("NODE_A", ""));
        assertThrows(ValidationException.class, () -> DomainValidator.validateRouteEndpoints("  ", "NODE_B"));
    }

    @Test
    @DisplayName("validateHospitalCapacity enforces non-negative bounds and available <= total")
    void testValidateHospitalCapacity() {
        assertDoesNotThrow(() -> DomainValidator.validateHospitalCapacity(10, 5));
        assertDoesNotThrow(() -> DomainValidator.validateHospitalCapacity(10, 10));
        assertDoesNotThrow(() -> DomainValidator.validateHospitalCapacity(0, 0));

        assertThrows(ValidationException.class, () -> DomainValidator.validateHospitalCapacity(-1, 0));
        assertThrows(ValidationException.class, () -> DomainValidator.validateHospitalCapacity(10, -1));
        assertThrows(ValidationException.class, () -> DomainValidator.validateHospitalCapacity(10, 11));
    }

    @Test
    @DisplayName("validateEmergencyInputs rejects null or blank parameters")
    void testValidateEmergencyInputs() {
        assertDoesNotThrow(() -> DomainValidator.validateEmergencyInputs("CASE_1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "NODE_A"));

        assertThrows(ValidationException.class, () -> DomainValidator.validateEmergencyInputs(null, EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "NODE_A"));
        assertThrows(ValidationException.class, () -> DomainValidator.validateEmergencyInputs(" ", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, "NODE_A"));
        assertThrows(NullPointerException.class, () -> DomainValidator.validateEmergencyInputs("CASE_1", null, EmergencySeverity.CRITICAL, "NODE_A"));
        assertThrows(NullPointerException.class, () -> DomainValidator.validateEmergencyInputs("CASE_1", EmergencyType.CARDIAC, null, "NODE_A"));
        assertThrows(ValidationException.class, () -> DomainValidator.validateEmergencyInputs("CASE_1", EmergencyType.CARDIAC, EmergencySeverity.CRITICAL, ""));
    }

    @Test
    @DisplayName("validateAmbulanceInputs rejects null or blank parameters")
    void testValidateAmbulanceInputs() {
        assertDoesNotThrow(() -> DomainValidator.validateAmbulanceInputs("AMB_1", "NODE_A", AmbulanceStatus.AVAILABLE));

        assertThrows(ValidationException.class, () -> DomainValidator.validateAmbulanceInputs(null, "NODE_A", AmbulanceStatus.AVAILABLE));
        assertThrows(ValidationException.class, () -> DomainValidator.validateAmbulanceInputs("AMB_1", "  ", AmbulanceStatus.AVAILABLE));
        assertThrows(NullPointerException.class, () -> DomainValidator.validateAmbulanceInputs("AMB_1", "NODE_A", null));
    }

    @Test
    @DisplayName("validateSimulationSettings enforces positive durations and timeStep <= duration")
    void testValidateSimulationSettings() {
        assertDoesNotThrow(() -> DomainValidator.validateSimulationSettings(Duration.ofMinutes(10), Duration.ofSeconds(1), 42L));
        assertDoesNotThrow(() -> DomainValidator.validateSimulationSettings(Duration.ofSeconds(1), Duration.ofSeconds(1), 42L));

        assertThrows(NullPointerException.class, () -> DomainValidator.validateSimulationSettings(null, Duration.ofSeconds(1), 42L));
        assertThrows(NullPointerException.class, () -> DomainValidator.validateSimulationSettings(Duration.ofMinutes(1), null, 42L));
        assertThrows(ValidationException.class, () -> DomainValidator.validateSimulationSettings(Duration.ZERO, Duration.ofSeconds(1), 42L));
        assertThrows(ValidationException.class, () -> DomainValidator.validateSimulationSettings(Duration.ofSeconds(-10), Duration.ofSeconds(1), 42L));
        assertThrows(ValidationException.class, () -> DomainValidator.validateSimulationSettings(Duration.ofMinutes(1), Duration.ZERO, 42L));
        assertThrows(ValidationException.class, () -> DomainValidator.validateSimulationSettings(Duration.ofMinutes(1), Duration.ofSeconds(-5), 42L));
        assertThrows(ValidationException.class, () -> DomainValidator.validateSimulationSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), 42L));
    }
}
