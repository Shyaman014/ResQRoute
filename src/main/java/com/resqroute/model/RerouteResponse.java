package com.resqroute.model;

import java.util.Objects;

/**
 * Result of an automatic rerouting evaluation comparing the prior route against current conditions.
 */
public record RerouteResponse(
        String trackingId,
        RerouteStatus status,
        RerouteTriggerReason triggerReason,
        RouteResult originalRoute,
        RouteResult newRoute,
        double oldTravelTimeMinutes,
        double newTravelTimeMinutes,
        double timeDeltaMinutes,
        double distanceDeltaKm,
        String message
) {

    public RerouteResponse {
        Objects.requireNonNull(trackingId, "Tracking ID must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        Objects.requireNonNull(triggerReason, "Trigger reason must not be null");
        Objects.requireNonNull(originalRoute, "Original route must not be null");
        Objects.requireNonNull(message, "Message must not be null");
    }

    public static RerouteResponse rerouted(String trackingId,
                                           RerouteTriggerReason reason,
                                           RouteResult oldRoute,
                                           RouteResult replacementRoute,
                                           double oldRemainingTime,
                                           double oldRemainingDist,
                                           String customMessage) {
        double newTime = replacementRoute.getTotalTravelTimeMinutes();
        double newDist = replacementRoute.getTotalDistanceKm();
        double timeDelta = oldRemainingTime - newTime;
        double distDelta = oldRemainingDist - newDist;

        String msg = (customMessage != null) ? customMessage : String.format(
                "Rerouted via %s: estimated travel time %.2f min (delta: %+.2f min, dist: %+.2f km).",
                replacementRoute.getNodeIds(), newTime, timeDelta, distDelta);

        return new RerouteResponse(trackingId, RerouteStatus.REROUTED, reason, oldRoute, replacementRoute,
                oldRemainingTime, newTime, timeDelta, distDelta, msg);
    }

    public static RerouteResponse noActionRequired(String trackingId,
                                                   RerouteTriggerReason reason,
                                                   RouteResult currentRoute,
                                                   String explanation) {
        double time = currentRoute.getTotalTravelTimeMinutes();
        return new RerouteResponse(trackingId, RerouteStatus.NO_ACTION_REQUIRED, reason, currentRoute,
                currentRoute, time, time, 0.0, 0.0, explanation);
    }

    public static RerouteResponse noAlternative(String trackingId,
                                                RerouteTriggerReason reason,
                                                RouteResult oldRoute,
                                                String blockedSource,
                                                String blockedDest) {
        String destId = oldRoute.getDestinationId();
        RouteResult unreachable = RouteResult.unreachable(blockedSource, destId);
        String msg = String.format("CRITICAL: Road segment '%s' -> '%s' is blocked and NO alternative route exists to destination '%s'.",
                blockedSource, blockedDest, destId);

        return new RerouteResponse(trackingId, RerouteStatus.NO_ALTERNATIVE_ROUTE, reason, oldRoute,
                unreachable, oldRoute.getTotalTravelTimeMinutes(), Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 0.0, msg);
    }

    public static RerouteResponse skippedDuplicate(String trackingId,
                                                    RerouteTriggerReason reason,
                                                    RouteResult currentRoute,
                                                    String signature) {
        double time = currentRoute.getTotalTravelTimeMinutes();
        String msg = String.format("Skipped redundant rerouting calculation for duplicate event [%s].", signature);
        return new RerouteResponse(trackingId, RerouteStatus.SKIPPED_DUPLICATE, reason, currentRoute,
                currentRoute, time, time, 0.0, 0.0, msg);
    }

    public boolean isRerouted() {
        return status == RerouteStatus.REROUTED;
    }

    public boolean isBlockedWithNoAlternative() {
        return status == RerouteStatus.NO_ALTERNATIVE_ROUTE;
    }
}
