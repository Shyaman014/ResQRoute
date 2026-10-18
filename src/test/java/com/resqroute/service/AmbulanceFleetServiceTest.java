package com.resqroute.service;

import com.resqroute.exception.AmbulanceUnavailableException;
import com.resqroute.exception.NodeNotFoundException;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Ambulance;
import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ambulance Fleet Service Management Tests")
class AmbulanceFleetServiceTest {

    private RoadGraph graph;
    private AmbulanceFleetService fleetService;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        graph.addNode(new Node("HQ", 12.97, 77.59, "Metro Dispatch"));
        graph.addNode(new Node("INT-A", 12.98, 77.60, "Intersection A"));
        graph.addNode(new Node("HOSP-01", 12.99, 77.61, "Central Hospital"));

        fleetService = new AmbulanceFleetService(graph);
    }

    @Test
    @DisplayName("Successfully registers ambulance at valid graph node")
    void testRegisterAmbulanceSuccess() {
        Ambulance amb = fleetService.registerAmbulance("AMB-01", "HQ");

        assertEquals("AMB-01", amb.getId());
        assertEquals("HQ", amb.getCurrentLocationNodeId());
        assertEquals(AmbulanceStatus.AVAILABLE, amb.getStatus());
        assertTrue(amb.isAvailable());
        assertEquals(1, fleetService.getFleetSize());
        assertEquals(Optional.of(amb), fleetService.getAmbulance("AMB-01"));
    }

    @Test
    @DisplayName("Rejects registration at non-existent graph node")
    void testRegisterAtInvalidNodeThrows() {
        assertThrows(NodeNotFoundException.class, () ->
                fleetService.registerAmbulance("AMB-99", "NON-EXISTENT-NODE"));
    }

    @Test
    @DisplayName("Rejects duplicate ambulance ID")
    void testRegisterDuplicateIdThrows() {
        fleetService.registerAmbulance("AMB-01", "HQ");
        assertThrows(IllegalArgumentException.class, () ->
                fleetService.registerAmbulance("AMB-01", "INT-A"));
    }

    @Test
    @DisplayName("Assigns available ambulance to emergency case")
    void testAssignAmbulanceToCase() {
        fleetService.registerAmbulance("AMB-01", "HQ");

        Ambulance assigned = fleetService.assignAmbulanceToCase("AMB-01", "CASE-101");
        assertEquals(AmbulanceStatus.ASSIGNED, assigned.getStatus());
        assertEquals(Optional.of("CASE-101"), assigned.getAssignedCaseId());
        assertFalse(assigned.isAvailable());

        // Shows in case lookup
        assertEquals(Optional.of(assigned), fleetService.getAmbulanceAssignedToCase("CASE-101"));

        // Available list excludes assigned
        assertTrue(fleetService.getAvailableAmbulances().isEmpty());
    }

    @Test
    @DisplayName("Rejects assigning an already busy or non-existent ambulance")
    void testAssignBusyOrMissingAmbulanceThrows() {
        fleetService.registerAmbulance("AMB-01", "HQ");
        fleetService.assignAmbulanceToCase("AMB-01", "CASE-101");

        // Attempting to assign again throws AmbulanceUnavailableException
        assertThrows(AmbulanceUnavailableException.class, () ->
                fleetService.assignAmbulanceToCase("AMB-01", "CASE-202"));

        // Assigning non-existent ambulance throws
        assertThrows(AmbulanceUnavailableException.class, () ->
                fleetService.assignAmbulanceToCase("AMB-UNKNOWN", "CASE-303"));
    }

    @Test
    @DisplayName("Releasing ambulance restores availability and clears case assignment")
    void testReleaseAmbulance() {
        fleetService.registerAmbulance("AMB-01", "HQ");
        fleetService.assignAmbulanceToCase("AMB-01", "CASE-101");

        Ambulance released = fleetService.releaseAmbulance("AMB-01");
        assertEquals(AmbulanceStatus.AVAILABLE, released.getStatus());
        assertEquals(Optional.empty(), released.getAssignedCaseId());
        assertTrue(released.isAvailable());
        assertEquals(1, fleetService.getAvailableAmbulances().size());
    }

    @Test
    @DisplayName("Updates location to valid node and rejects invalid node")
    void testUpdateLocation() {
        fleetService.registerAmbulance("AMB-01", "HQ");

        Ambulance moved = fleetService.updateLocation("AMB-01", "INT-A");
        assertEquals("INT-A", moved.getCurrentLocationNodeId());

        assertThrows(NodeNotFoundException.class, () ->
                fleetService.updateLocation("AMB-01", "GHOST-NODE"));
    }
}
