package com.resqroute.dispatch;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.RouteResult;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome container encapsulating the complete result of an ambulance dispatch decision.
 *
 * <p>Stores the assigned emergency case, selected ambulance, traffic-aware navigation route,
 * estimated arrival metrics, and detailed diagnostic status.</p>
 */
public final class DispatchResult {

    private final DispatchStatus status;
    private final EmergencyCase emergencyCase;
    private final Ambulance assignedAmbulance;
    private final RouteResult routeToPickup;
    private final double estimatedTravelTimeMinutes;
    private final double estimatedDistanceKm;
    private final Instant dispatchedAt;
    private final String message;

    private DispatchResult(DispatchStatus status,
                           EmergencyCase emergencyCase,
                           Ambulance assignedAmbulance,
                           RouteResult routeToPickup,
                           double estimatedTravelTimeMinutes,
                           double estimatedDistanceKm,
                           Instant dispatchedAt,
                           String message) {
        this.status = Objects.requireNonNull(status, "DispatchStatus must not be null");
        this.emergencyCase = emergencyCase;
        this.assignedAmbulance = assignedAmbulance;
        this.routeToPickup = routeToPickup;
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.dispatchedAt = dispatchedAt != null ? dispatchedAt : Instant.now();
        this.message = message != null ? message : status.name();
    }

    public static DispatchResult success(EmergencyCase emergencyCase,
                                         Ambulance ambulance,
                                         RouteResult routeToPickup,
                                         Instant dispatchedAt) {
        Objects.requireNonNull(emergencyCase, "EmergencyCase must not be null");
        Objects.requireNonNull(ambulance, "Ambulance must not be null");
        Objects.requireNonNull(routeToPickup, "RouteResult must not be null");

        String msg = String.format("Dispatched ambulance '%s' to case '%s' (ETA: %.2f min, Distance: %.2f km)",
                ambulance.getId(), emergencyCase.getId(),
                routeToPickup.getTotalTravelTimeMinutes(), routeToPickup.getTotalDistanceKm());

        return new DispatchResult(
                DispatchStatus.SUCCESS,
                emergencyCase,
                ambulance,
                routeToPickup,
                routeToPickup.getTotalTravelTimeMinutes(),
                routeToPickup.getTotalDistanceKm(),
                dispatchedAt,
                msg
        );
    }

    public static DispatchResult noPendingEmergencies(Instant dispatchedAt) {
        return new DispatchResult(
                DispatchStatus.NO_PENDING_EMERGENCIES,
                null, null, null, 0.0, 0.0, dispatchedAt,
                "No eligible pending emergency cases waiting in dispatch queue"
        );
    }

    public static DispatchResult noAvailableAmbulances(EmergencyCase emergencyCase, Instant dispatchedAt) {
        return new DispatchResult(
                DispatchStatus.NO_AVAILABLE_AMBULANCES,
                emergencyCase, null, null, 0.0, 0.0, dispatchedAt,
                String.format("All fleet ambulances are currently busy or unavailable for case '%s'", emergencyCase.getId())
        );
    }

    public static DispatchResult noCompatibleAmbulances(EmergencyCase emergencyCase, Instant dispatchedAt) {
        return new DispatchResult(
                DispatchStatus.NO_COMPATIBLE_AMBULANCES,
                emergencyCase, null, null, 0.0, 0.0, dispatchedAt,
                String.format("No available ambulances possess required capabilities %s for case '%s'",
                        emergencyCase.getRequiredCapabilities(), emergencyCase.getId())
        );
    }

    public static DispatchResult unreachablePickup(EmergencyCase emergencyCase, Instant dispatchedAt) {
        return new DispatchResult(
                DispatchStatus.UNREACHABLE_PICKUP,
                emergencyCase, null, null, 0.0, 0.0, dispatchedAt,
                String.format("Pickup location '%s' is completely unreachable from all available ambulances",
                        emergencyCase.getPickupLocationNodeId())
        );
    }

    public static DispatchResult caseIneligible(EmergencyCase emergencyCase, String reason, Instant dispatchedAt) {
        return new DispatchResult(
                DispatchStatus.CASE_INELIGIBLE,
                emergencyCase, null, null, 0.0, 0.0, dispatchedAt,
                String.format("Emergency case '%s' is ineligible for dispatch: %s",
                        emergencyCase != null ? emergencyCase.getId() : "null", reason)
        );
    }

    public static DispatchResult failedTransaction(EmergencyCase emergencyCase, String reason, Instant dispatchedAt) {
        return new DispatchResult(
                DispatchStatus.FAILED_TRANSACTION,
                emergencyCase, null, null, 0.0, 0.0, dispatchedAt,
                String.format("Atomic assignment transaction failed for case '%s': %s",
                        emergencyCase != null ? emergencyCase.getId() : "null", reason)
        );
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public Optional<EmergencyCase> getEmergencyCase() {
        return Optional.ofNullable(emergencyCase);
    }

    public Optional<Ambulance> getAssignedAmbulance() {
        return Optional.ofNullable(assignedAmbulance);
    }

    public Optional<RouteResult> getRouteToPickup() {
        return Optional.ofNullable(routeToPickup);
    }

    public double getEstimatedTravelTimeMinutes() {
        return estimatedTravelTimeMinutes;
    }

    public double getEstimatedDistanceKm() {
        return estimatedDistanceKm;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("DispatchResult[status=%s, msg='%s']", status, message);
    }
}
