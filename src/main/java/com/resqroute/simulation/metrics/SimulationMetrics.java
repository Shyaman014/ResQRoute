package com.resqroute.simulation.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * Audit and performance telemetry metrics computed from a city simulation run.
 *
 * <p>Formulas:
 * <ul>
 *   <li><b>Dispatch Latency:</b> Time elapsed from case creation (report) until an ambulance is assigned.</li>
 *   <li><b>Response Time:</b> Time elapsed from case report until ambulance arrival at the pickup scene.</li>
 *   <li><b>Delivery Time:</b> Time elapsed from case report until ambulance arrival at destination hospital.</li>
 *   <li><b>Ambulance Utilization:</b> Total active mission duration divided by available fleet operating time.</li>
 * </ul>
 * </p>
 */
public record SimulationMetrics(
        int totalReportedCases,
        int totalDispatchedCases,
        int totalCompletedCases,
        int totalCancelledCases,
        int totalUnservedCases,
        double averageDispatchLatencyMinutes,
        double averageResponseTimeMinutes,
        double averageHospitalDeliveryTimeMinutes,
        double overallAmbulanceUtilization,
        Map<String, Double> ambulanceUtilizationById
) {
    public SimulationMetrics {
        ambulanceUtilizationById = (ambulanceUtilizationById == null)
                ? Map.of()
                : Collections.unmodifiableMap(ambulanceUtilizationById);
    }

    public static SimulationMetrics empty() {
        return new SimulationMetrics(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, Map.of());
    }

    @Override
    public String toString() {
        return String.format("""
                Simulation Metrics Summary:
                  Cases Reported:    %d
                  Cases Dispatched:  %d
                  Cases Completed:   %d
                  Cases Cancelled:   %d
                  Cases Unserved:    %d
                  Avg Dispatch Latency: %.2f min
                  Avg Response Time:    %.2f min
                  Avg Delivery Time:    %.2f min
                  Overall Fleet Utilization: %.1f%%
                """,
                totalReportedCases, totalDispatchedCases, totalCompletedCases, totalCancelledCases, totalUnservedCases,
                averageDispatchLatencyMinutes, averageResponseTimeMinutes, averageHospitalDeliveryTimeMinutes,
                overallAmbulanceUtilization * 100.0);
    }
}
