package com.resqroute.benchmark;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * High-precision microbenchmark harness executing warm-up iterations, HotSpot JIT stabilization,
 * and statistical sample collection for fair algorithm evaluation.
 */
public final class BenchmarkHarness {

    private final int warmupIterations;
    private final int measurementIterations;

    public BenchmarkHarness(int warmupIterations, int measurementIterations) {
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("Warmup iterations must be >= 0");
        }
        if (measurementIterations < 1) {
            throw new IllegalArgumentException("Measurement iterations must be >= 1");
        }
        this.warmupIterations = warmupIterations;
        this.measurementIterations = measurementIterations;
    }

    public static BenchmarkHarness standard() {
        return new BenchmarkHarness(5, 15);
    }

    public static BenchmarkHarness smoke() {
        return new BenchmarkHarness(1, 3);
    }

    /**
     * Executes a benchmark measuring the supplied action.
     *
     * @param benchmarkName Descriptive name of the benchmark.
     * @param scenario      Dataset size or condition (e.g., "50 nodes", "K=3").
     * @param action        Executable benchmark body.
     * @param extraMetrics  Additional diagnostic metrics (e.g. route cost, nodes explored).
     * @return BenchmarkResult containing complete statistical summary.
     */
    public BenchmarkResult runBenchmark(String benchmarkName,
                                        String scenario,
                                        Runnable action,
                                        String extraMetrics) {
        Objects.requireNonNull(action, "Benchmark action must not be null");

        // 1. Warm-up Phase: Trigger JIT compilation (C1 / C2 tiers) without timing
        for (int i = 0; i < warmupIterations; i++) {
            action.run();
        }

        // Suggest garbage collection before measurement window
        System.gc();
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // 2. Measurement Phase
        long[] samplesNanos = new long[measurementIterations];
        for (int i = 0; i < measurementIterations; i++) {
            long start = System.nanoTime();
            action.run();
            long elapsed = System.nanoTime() - start;
            samplesNanos[i] = elapsed;
        }

        return BenchmarkResult.fromNanoseconds(benchmarkName, scenario, samplesNanos, extraMetrics);
    }

    /**
     * Executes a benchmark with a value-returning supplier.
     */
    public <T> BenchmarkResult runBenchmark(String benchmarkName,
                                            String scenario,
                                            Supplier<T> supplier,
                                            String extraMetrics) {
        Objects.requireNonNull(supplier, "Benchmark supplier must not be null");
        return runBenchmark(benchmarkName, scenario, () -> {
            T val = supplier.get();
            if (val == null) {
                throw new IllegalStateException("Benchmark supplier returned null");
            }
        }, extraMetrics);
    }

    public int getWarmupIterations() {
        return warmupIterations;
    }

    public int getMeasurementIterations() {
        return measurementIterations;
    }
}
