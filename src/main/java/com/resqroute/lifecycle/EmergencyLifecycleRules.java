package com.resqroute.lifecycle;

import com.resqroute.exception.InvalidStatusTransitionException;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencyStatus;

import java.util.*;

/**
 * Encapsulates the state machine rules and validation for the emergency case lifecycle.
 *
 * <p>Enforces deterministic, validated transitions across all emergency lifecycle states:
 * <ul>
 *   <li><b>Intake:</b> {@code REPORTED} transitions to {@code WAITING_FOR_DISPATCH}, {@code AMBULANCE_ASSIGNED}, or {@code CANCELLED}.</li>
 *   <li><b>Triage:</b> {@code WAITING_FOR_DISPATCH} transitions to {@code AMBULANCE_ASSIGNED} or {@code CANCELLED}.</li>
 *   <li><b>Dispatch:</b> {@code AMBULANCE_ASSIGNED} transitions to {@code AMBULANCE_EN_ROUTE}, {@code WAITING_FOR_DISPATCH} (recall), or {@code CANCELLED}.</li>
 *   <li><b>En Route:</b> {@code AMBULANCE_EN_ROUTE} transitions to {@code PATIENT_PICKED_UP}, {@code WAITING_FOR_DISPATCH} (reroute/breakdown), {@code AMBULANCE_ASSIGNED} (reassign), or {@code CANCELLED}.</li>
 *   <li><b>On Scene:</b> {@code PATIENT_PICKED_UP} transitions to {@code TRANSPORTING} or {@code COMPLETED} (treated on scene).</li>
 *   <li><b>Transport:</b> {@code TRANSPORTING} transitions to {@code COMPLETED} upon hospital delivery.</li>
 *   <li><b>Terminal States:</b> {@code COMPLETED} and {@code CANCELLED} accept no further state transitions.</li>
 * </ul>
 * </p>
 */
public final class EmergencyLifecycleRules {

    private static final Map<EmergencyStatus, Set<EmergencyStatus>> VALID_TRANSITIONS = new EnumMap<>(EmergencyStatus.class);

    private static final Set<EmergencyStatus> CANCELLATION_ELIGIBLE_STATUSES = EnumSet.of(
            EmergencyStatus.REPORTED,
            EmergencyStatus.WAITING_FOR_DISPATCH,
            EmergencyStatus.AMBULANCE_ASSIGNED,
            EmergencyStatus.AMBULANCE_EN_ROUTE
    );

    static {
        VALID_TRANSITIONS.put(EmergencyStatus.REPORTED, EnumSet.of(
                EmergencyStatus.WAITING_FOR_DISPATCH,
                EmergencyStatus.AMBULANCE_ASSIGNED,
                EmergencyStatus.CANCELLED
        ));

        VALID_TRANSITIONS.put(EmergencyStatus.WAITING_FOR_DISPATCH, EnumSet.of(
                EmergencyStatus.AMBULANCE_ASSIGNED,
                EmergencyStatus.CANCELLED
        ));

        VALID_TRANSITIONS.put(EmergencyStatus.AMBULANCE_ASSIGNED, EnumSet.of(
                EmergencyStatus.AMBULANCE_EN_ROUTE,
                EmergencyStatus.WAITING_FOR_DISPATCH,
                EmergencyStatus.CANCELLED
        ));

        VALID_TRANSITIONS.put(EmergencyStatus.AMBULANCE_EN_ROUTE, EnumSet.of(
                EmergencyStatus.PATIENT_PICKED_UP,
                EmergencyStatus.WAITING_FOR_DISPATCH,
                EmergencyStatus.AMBULANCE_ASSIGNED,
                EmergencyStatus.CANCELLED
        ));

        VALID_TRANSITIONS.put(EmergencyStatus.PATIENT_PICKED_UP, EnumSet.of(
                EmergencyStatus.TRANSPORTING,
                EmergencyStatus.COMPLETED
        ));

        VALID_TRANSITIONS.put(EmergencyStatus.TRANSPORTING, EnumSet.of(
                EmergencyStatus.COMPLETED
        ));

        VALID_TRANSITIONS.put(EmergencyStatus.COMPLETED, Collections.emptySet());
        VALID_TRANSITIONS.put(EmergencyStatus.CANCELLED, Collections.emptySet());
    }

    /**
     * Checks if a transition between two statuses is structurally permitted.
     */
    public static boolean isValidTransition(EmergencyStatus currentStatus, EmergencyStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return false;
        }
        Set<EmergencyStatus> allowed = VALID_TRANSITIONS.get(currentStatus);
        return allowed != null && allowed.contains(targetStatus);
    }

    /**
     * Returns the set of valid next statuses reachable from the specified status.
     */
    public static Set<EmergencyStatus> getValidNextStatuses(EmergencyStatus currentStatus) {
        if (currentStatus == null) {
            return Collections.emptySet();
        }
        Set<EmergencyStatus> allowed = VALID_TRANSITIONS.get(currentStatus);
        return allowed != null ? Collections.unmodifiableSet(allowed) : Collections.emptySet();
    }

    /**
     * Checks if a case currently in the given status can be safely cancelled.
     */
    public static boolean isCancellationEligible(EmergencyStatus status) {
        return status != null && CANCELLATION_ELIGIBLE_STATUSES.contains(status);
    }

    /**
     * Validates a proposed state transition for a given emergency case, throwing an exception if invalid.
     *
     * @param emergencyCase The active emergency case.
     * @param targetStatus  The proposed destination status.
     * @throws InvalidStatusTransitionException if transition is rejected.
     */
    public static void validateTransition(EmergencyCase emergencyCase, EmergencyStatus targetStatus) {
        Objects.requireNonNull(emergencyCase, "Emergency case must not be null");
        Objects.requireNonNull(targetStatus, "Target status must not be null");

        EmergencyStatus currentStatus = emergencyCase.getStatus();

        if (currentStatus == targetStatus) {
            throw new InvalidStatusTransitionException(
                    emergencyCase.getId(),
                    currentStatus.name(),
                    targetStatus.name(),
                    "Case is already in status " + targetStatus
            );
        }

        if (currentStatus.isTerminal()) {
            throw new InvalidStatusTransitionException(
                    emergencyCase.getId(),
                    currentStatus.name(),
                    targetStatus.name(),
                    "Cannot transition from terminal state " + currentStatus
            );
        }

        if (!isValidTransition(currentStatus, targetStatus)) {
            throw new InvalidStatusTransitionException(
                    emergencyCase.getId(),
                    currentStatus.name(),
                    targetStatus.name(),
                    String.format("Transition not permitted. Allowed next states: %s", getValidNextStatuses(currentStatus))
            );
        }

        // Validate operational prerequisites
        if (targetStatus.requiresAmbulance() && !emergencyCase.hasAssignedAmbulance()) {
            throw new InvalidStatusTransitionException(
                    emergencyCase.getId(),
                    currentStatus.name(),
                    targetStatus.name(),
                    "Target status requires an assigned ambulance, but none is currently assigned"
            );
        }
    }

    /**
     * Validates whether an emergency case can be cancelled, throwing an exception if ineligible.
     */
    public static void validateCancellation(EmergencyCase emergencyCase) {
        Objects.requireNonNull(emergencyCase, "Emergency case must not be null");
        EmergencyStatus currentStatus = emergencyCase.getStatus();

        if (!isCancellationEligible(currentStatus)) {
            String reason = switch (currentStatus) {
                case COMPLETED -> "Case is already completed and closed";
                case CANCELLED -> "Case is already cancelled";
                case PATIENT_PICKED_UP -> "Cannot cancel case after patient has been picked up on scene";
                case TRANSPORTING -> "Cannot cancel case while patient is in transit to medical facility";
                default -> "Status " + currentStatus + " is not eligible for cancellation";
            };
            throw new InvalidStatusTransitionException(
                    emergencyCase.getId(),
                    currentStatus.name(),
                    EmergencyStatus.CANCELLED.name(),
                    reason
            );
        }
    }

    private EmergencyLifecycleRules() {
        // Utility / rules class
    }
}
