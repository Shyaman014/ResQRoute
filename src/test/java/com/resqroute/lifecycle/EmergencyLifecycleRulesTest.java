package com.resqroute.lifecycle;

import com.resqroute.exception.InvalidStatusTransitionException;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.model.EmergencyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 9: Emergency Lifecycle Transition Rules Tests")
class EmergencyLifecycleRulesTest {

    private final Instant now = Instant.parse("2026-10-18T14:00:00Z");

    private EmergencyCase createCaseInStatus(EmergencyStatus status, String ambulanceId) {
        return EmergencyCase.builder()
                .id("CASE-TEST")
                .type(EmergencyType.MEDICAL)
                .severity(EmergencySeverity.HIGH)
                .pickupLocationNodeId("INT-A")
                .createdAt(now)
                .status(status)
                .assignedAmbulanceId(ambulanceId)
                .build();
    }

    @Test
    @DisplayName("Valid forward lifecycle transitions succeed")
    void testValidForwardTransitions() {
        // REPORTED transitions
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.REPORTED, EmergencyStatus.WAITING_FOR_DISPATCH));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.REPORTED, EmergencyStatus.AMBULANCE_ASSIGNED));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.REPORTED, EmergencyStatus.CANCELLED));

        // WAITING_FOR_DISPATCH transitions
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.WAITING_FOR_DISPATCH, EmergencyStatus.AMBULANCE_ASSIGNED));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.WAITING_FOR_DISPATCH, EmergencyStatus.CANCELLED));

        // AMBULANCE_ASSIGNED transitions
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_ASSIGNED, EmergencyStatus.AMBULANCE_EN_ROUTE));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_ASSIGNED, EmergencyStatus.WAITING_FOR_DISPATCH));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_ASSIGNED, EmergencyStatus.CANCELLED));

        // AMBULANCE_EN_ROUTE transitions
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_EN_ROUTE, EmergencyStatus.PATIENT_PICKED_UP));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_EN_ROUTE, EmergencyStatus.WAITING_FOR_DISPATCH));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_EN_ROUTE, EmergencyStatus.AMBULANCE_ASSIGNED));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.AMBULANCE_EN_ROUTE, EmergencyStatus.CANCELLED));

        // PATIENT_PICKED_UP transitions
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.PATIENT_PICKED_UP, EmergencyStatus.TRANSPORTING));
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.PATIENT_PICKED_UP, EmergencyStatus.COMPLETED));

        // TRANSPORTING transitions
        assertTrue(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.TRANSPORTING, EmergencyStatus.COMPLETED));
    }

    @Test
    @DisplayName("Terminal states reject all transitions")
    void testTerminalStatesRejectTransitions() {
        assertTrue(EmergencyLifecycleRules.getValidNextStatuses(EmergencyStatus.COMPLETED).isEmpty());
        assertTrue(EmergencyLifecycleRules.getValidNextStatuses(EmergencyStatus.CANCELLED).isEmpty());

        for (EmergencyStatus target : EmergencyStatus.values()) {
            assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.COMPLETED, target));
            assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.CANCELLED, target));
        }

        EmergencyCase completedCase = createCaseInStatus(EmergencyStatus.COMPLETED, "AMB-01");
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateTransition(completedCase, EmergencyStatus.REPORTED));

        EmergencyCase cancelledCase = createCaseInStatus(EmergencyStatus.CANCELLED, null);
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateTransition(cancelledCase, EmergencyStatus.WAITING_FOR_DISPATCH));
    }

    @Test
    @DisplayName("Self-transitions are rejected")
    void testSelfTransitionsRejected() {
        for (EmergencyStatus status : EmergencyStatus.values()) {
            assertFalse(EmergencyLifecycleRules.isValidTransition(status, status));

            EmergencyCase c = createCaseInStatus(status, "AMB-01");
            assertThrows(InvalidStatusTransitionException.class, () ->
                    EmergencyLifecycleRules.validateTransition(c, status));
        }
    }

    @Test
    @DisplayName("Illegal transition hops are rejected")
    void testIllegalTransitionHops() {
        // Skipping steps
        assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.REPORTED, EmergencyStatus.COMPLETED));
        assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.REPORTED, EmergencyStatus.PATIENT_PICKED_UP));
        assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.WAITING_FOR_DISPATCH, EmergencyStatus.TRANSPORTING));

        // Backward transitions
        assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.TRANSPORTING, EmergencyStatus.REPORTED));
        assertFalse(EmergencyLifecycleRules.isValidTransition(EmergencyStatus.PATIENT_PICKED_UP, EmergencyStatus.WAITING_FOR_DISPATCH));

        EmergencyCase reported = createCaseInStatus(EmergencyStatus.REPORTED, null);
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateTransition(reported, EmergencyStatus.COMPLETED));
    }

    @Test
    @DisplayName("Requires assigned ambulance for dispatch and transport states")
    void testRequiresAssignedAmbulance() {
        EmergencyCase unassignedCase = createCaseInStatus(EmergencyStatus.REPORTED, null);

        // Attempting to transition to AMBULANCE_ASSIGNED without ambulance ID throws
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateTransition(unassignedCase, EmergencyStatus.AMBULANCE_ASSIGNED));

        // When ambulance is assigned, transition is valid
        EmergencyCase assignedCase = unassignedCase.withAssignedAmbulance("AMB-01");
        assertDoesNotThrow(() ->
                EmergencyLifecycleRules.validateTransition(assignedCase, EmergencyStatus.AMBULANCE_ASSIGNED));
    }

    @Test
    @DisplayName("Cancellation eligibility rules")
    void testCancellationEligibility() {
        // Eligible: pre-transport phases
        assertTrue(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.REPORTED));
        assertTrue(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.WAITING_FOR_DISPATCH));
        assertTrue(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.AMBULANCE_ASSIGNED));
        assertTrue(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.AMBULANCE_EN_ROUTE));

        // Ineligible: post-pickup, transit, or already closed
        assertFalse(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.PATIENT_PICKED_UP));
        assertFalse(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.TRANSPORTING));
        assertFalse(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.COMPLETED));
        assertFalse(EmergencyLifecycleRules.isCancellationEligible(EmergencyStatus.CANCELLED));

        // Validate cancellation method throws for ineligible
        EmergencyCase inTransit = createCaseInStatus(EmergencyStatus.TRANSPORTING, "AMB-01");
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateCancellation(inTransit));

        EmergencyCase pickedUp = createCaseInStatus(EmergencyStatus.PATIENT_PICKED_UP, "AMB-01");
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateCancellation(pickedUp));

        EmergencyCase completed = createCaseInStatus(EmergencyStatus.COMPLETED, "AMB-01");
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateCancellation(completed));

        EmergencyCase alreadyCancelled = createCaseInStatus(EmergencyStatus.CANCELLED, null);
        assertThrows(InvalidStatusTransitionException.class, () ->
                EmergencyLifecycleRules.validateCancellation(alreadyCancelled));

        // Valid cancellations do not throw
        EmergencyCase reported = createCaseInStatus(EmergencyStatus.REPORTED, null);
        assertDoesNotThrow(() -> EmergencyLifecycleRules.validateCancellation(reported));

        EmergencyCase enRoute = createCaseInStatus(EmergencyStatus.AMBULANCE_EN_ROUTE, "AMB-01");
        assertDoesNotThrow(() -> EmergencyLifecycleRules.validateCancellation(enRoute));
    }
}
