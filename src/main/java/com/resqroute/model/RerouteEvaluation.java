package com.resqroute.model;

import java.util.Objects;

/**
 * Immutable evaluation result comparing an in-progress or previously selected route against
 * the current real-time optimal route under updated traffic conditions.
 */
public record RerouteEvaluation(
        String sourceId,
        String destinationId,
        RouteResult previousRoute,
        RouteResult updatedPreviousRoute,
        RouteResult currentFastestRoute,
        boolean rerouteRecommended,
        double timeSavedMinutes,
        double percentageTimeSaved,
        String summary
) {

    public RerouteEvaluation {
        Objects.requireNonNull(sourceId, "Source ID must not be null");
        Objects.requireNonNull(destinationId, "Destination ID must not be null");
        Objects.requireNonNull(previousRoute, "Previous route must not be null");
        Objects.requireNonNull(updatedPreviousRoute, "Updated previous route must not be null");
        Objects.requireNonNull(currentFastestRoute, "Current fastest route must not be null");
        Objects.requireNonNull(summary, "Summary must not be null");
    }

    /**
     * Factory evaluating whether dynamic rerouting is recommended.
     *
     * @param previousRoute              The previously chosen route at original traffic conditions.
     * @param updatedPreviousRoute       The previous route re-evaluated under current traffic conditions.
     * @param currentFastestRoute        The newly calculated fastest route under current traffic conditions.
     * @param minTimeSavedThresholdMin   Minimum time savings required to justify a reroute recommendation.
     * @return Fully populated RerouteEvaluation.
     */
    public static RerouteEvaluation evaluate(RouteResult previousRoute,
                                            RouteResult updatedPreviousRoute,
                                            RouteResult currentFastestRoute,
                                            double minTimeSavedThresholdMin) {
        String src = previousRoute.getSourceId();
        String dst = previousRoute.getDestinationId();

        double prevCurrentTime = updatedPreviousRoute.getTotalTravelTimeMinutes();
        double newTime = currentFastestRoute.getTotalTravelTimeMinutes();
        double timeSaved = prevCurrentTime - newTime;

        boolean pathsEqual = previousRoute.getNodeIds().equals(currentFastestRoute.getNodeIds());
        boolean recommended = !pathsEqual && timeSaved >= minTimeSavedThresholdMin;
        double percentSaved = prevCurrentTime > 0.0 ? Math.max(0.0, (timeSaved / prevCurrentTime) * 100.0) : 0.0;

        String summary;
        if (pathsEqual) {
            summary = "Current route remains the fastest available path under updated traffic conditions.";
        } else if (recommended) {
            summary = String.format("REROUTE RECOMMENDED: Alternative path via %s saves %.2f minutes (%.1f%% faster).",
                    currentFastestRoute.getNodeIds(), timeSaved, percentSaved);
        } else {
            summary = String.format("REROUTE NOT RECOMMENDED: Alternative path saves only %.2f min, below threshold of %.2f min.",
                    timeSaved, minTimeSavedThresholdMin);
        }

        return new RerouteEvaluation(
                src,
                dst,
                previousRoute,
                updatedPreviousRoute,
                currentFastestRoute,
                recommended,
                Math.max(0.0, timeSaved),
                percentSaved,
                summary
        );
    }
}
