package com.resqroute.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable statistical outcome of a benchmark execution.
 */
public record BenchmarkResult(
        String benchmarkName,
        String scenario,
        int sampleCount,
        double meanMicros,
        double medianMicros,
        double minMicros,
        double maxMicros,
        double stdDevMicros,
        double opsPerSecond,
        String extraMetrics
) {

    public static BenchmarkResult fromNanoseconds(String benchmarkName, String scenario, long[] durationsNanos, String extraMetrics) {
        if (durationsNanos == null || durationsNanos.length == 0) {
            throw new IllegalArgumentException("durationsNanos must not be empty");
        }

        int count = durationsNanos.length;
        double[] micros = new double[count];
        double sum = 0.0;

        for (int i = 0; i < count; i++) {
            micros[i] = durationsNanos[i] / 1_000.0;
            sum += micros[i];
        }

        double mean = sum / count;
        double variance = 0.0;
        for (double val : micros) {
            variance += Math.pow(val - mean, 2);
        }
        double stdDev = Math.sqrt(variance / count);

        Arrays.sort(micros);
        double median = (count % 2 == 1)
                ? micros[count / 2]
                : (micros[(count / 2) - 1] + micros[count / 2]) / 2.0;

        double opsPerSec = (mean > 0.0) ? (1_000_000.0 / mean) : 0.0;

        return new BenchmarkResult(
                benchmarkName,
                scenario,
                count,
                mean,
                median,
                micros[0],
                micros[count - 1],
                stdDev,
                opsPerSec,
                (extraMetrics == null) ? "" : extraMetrics
        );
    }

    public String toTabularRow() {
        return String.format("| %-28s | %-12s | %8.2f µs | %8.2f µs | %8.2f µs | %8.2f µs | %10.1f | %s |",
                benchmarkName, scenario, meanMicros, medianMicros, minMicros, maxMicros, opsPerSecond, extraMetrics);
    }
}
