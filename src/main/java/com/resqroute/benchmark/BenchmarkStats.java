package com.resqroute.benchmark;

import java.util.*;

/**
 * Aggregated statistics for multiple runs of one algorithm on one scenario.
 * Provides median, min, max timing and consistent node/edge exploration counts.
 */
public final class BenchmarkStats {

    private final String algorithmName;
    private final String scenarioId;
    private final int totalRuns;
    private final int successfulRuns;
    private final int failedRuns;
    private final long medianNanos;
    private final long minNanos;
    private final long maxNanos;
    private final double medianMillis;
    private final double minMillis;
    private final double maxMillis;
    private final double avgNodesExplored;
    private final double avgEdgesExamined;
    private final boolean routeFound;
    private final double routeCostMinutes;
    private final double routeDistanceKm;

    private BenchmarkStats(Builder b) {
        this.algorithmName   = b.algorithmName;
        this.scenarioId      = b.scenarioId;
        this.totalRuns       = b.totalRuns;
        this.successfulRuns  = b.successfulRuns;
        this.failedRuns      = b.failedRuns;
        this.medianNanos     = b.medianNanos;
        this.minNanos        = b.minNanos;
        this.maxNanos        = b.maxNanos;
        this.medianMillis    = b.medianNanos / 1_000_000.0;
        this.minMillis       = b.minNanos    / 1_000_000.0;
        this.maxMillis       = b.maxNanos    / 1_000_000.0;
        this.avgNodesExplored = b.avgNodesExplored;
        this.avgEdgesExamined = b.avgEdgesExamined;
        this.routeFound      = b.routeFound;
        this.routeCostMinutes = b.routeCostMinutes;
        this.routeDistanceKm = b.routeDistanceKm;
    }

    /**
     * Computes BenchmarkStats from a list of individual run results.
     *
     * @param runs Non-empty list of AlgorithmRunResult.
     * @return Aggregated statistics.
     */
    public static BenchmarkStats from(List<AlgorithmRunResult> runs) {
        if (runs == null || runs.isEmpty()) {
            throw new IllegalArgumentException("Run list must not be empty");
        }

        String algoName  = runs.getFirst().getAlgorithmName();
        String scenId    = runs.getFirst().getScenarioId();
        int total        = runs.size();
        int successes    = (int) runs.stream().filter(AlgorithmRunResult::isRouteFound).count();
        int failures     = total - successes;

        long[] sorted = runs.stream().mapToLong(AlgorithmRunResult::getExecutionNanos).sorted().toArray();
        long medNanos  = sorted[sorted.length / 2];
        long minNanos  = sorted[0];
        long maxNanos  = sorted[sorted.length - 1];

        double avgNodes = runs.stream().mapToInt(AlgorithmRunResult::getNodesExplored).average().orElse(0);
        double avgEdges = runs.stream().mapToInt(AlgorithmRunResult::getEdgesExamined).average().orElse(0);

        // Route validity/cost: use median run's (or first successful run's) values
        boolean found  = successes > 0;
        double cost    = runs.stream().filter(AlgorithmRunResult::isRouteFound)
                            .mapToDouble(AlgorithmRunResult::getTotalTravelTimeMinutes).average().orElse(0);
        double dist    = runs.stream().filter(AlgorithmRunResult::isRouteFound)
                            .mapToDouble(AlgorithmRunResult::getTotalDistanceKm).average().orElse(0);

        return new Builder()
                .algorithmName(algoName).scenarioId(scenId)
                .totalRuns(total).successfulRuns(successes).failedRuns(failures)
                .medianNanos(medNanos).minNanos(minNanos).maxNanos(maxNanos)
                .avgNodesExplored(avgNodes).avgEdgesExamined(avgEdges)
                .routeFound(found).routeCostMinutes(cost).routeDistanceKm(dist)
                .build();
    }

    public String getAlgorithmName()     { return algorithmName; }
    public String getScenarioId()        { return scenarioId; }
    public int getTotalRuns()            { return totalRuns; }
    public int getSuccessfulRuns()       { return successfulRuns; }
    public int getFailedRuns()           { return failedRuns; }
    public long getMedianNanos()         { return medianNanos; }
    public long getMinNanos()            { return minNanos; }
    public long getMaxNanos()            { return maxNanos; }
    public double getMedianMillis()      { return medianMillis; }
    public double getMinMillis()         { return minMillis; }
    public double getMaxMillis()         { return maxMillis; }
    public double getAvgNodesExplored()  { return avgNodesExplored; }
    public double getAvgEdgesExamined()  { return avgEdgesExamined; }
    public boolean isRouteFound()        { return routeFound; }
    public double getRouteCostMinutes()  { return routeCostMinutes; }
    public double getRouteDistanceKm()   { return routeDistanceKm; }

    @Override
    public String toString() {
        return String.format("BenchmarkStats[%s/%s | runs=%d | median=%.3fms | nodes=%.1f | found=%b | cost=%.2fmin]",
                algorithmName, scenarioId, totalRuns, medianMillis, avgNodesExplored, routeFound, routeCostMinutes);
    }

    // ==========================================
    // Builder
    // ==========================================

    public static final class Builder {
        private String algorithmName;
        private String scenarioId;
        private int totalRuns, successfulRuns, failedRuns;
        private long medianNanos, minNanos, maxNanos;
        private double avgNodesExplored, avgEdgesExamined;
        private boolean routeFound;
        private double routeCostMinutes, routeDistanceKm;

        public Builder algorithmName(String v)       { this.algorithmName = v;      return this; }
        public Builder scenarioId(String v)          { this.scenarioId = v;         return this; }
        public Builder totalRuns(int v)              { this.totalRuns = v;          return this; }
        public Builder successfulRuns(int v)         { this.successfulRuns = v;     return this; }
        public Builder failedRuns(int v)             { this.failedRuns = v;         return this; }
        public Builder medianNanos(long v)           { this.medianNanos = v;        return this; }
        public Builder minNanos(long v)              { this.minNanos = v;           return this; }
        public Builder maxNanos(long v)              { this.maxNanos = v;           return this; }
        public Builder avgNodesExplored(double v)    { this.avgNodesExplored = v;   return this; }
        public Builder avgEdgesExamined(double v)    { this.avgEdgesExamined = v;   return this; }
        public Builder routeFound(boolean v)         { this.routeFound = v;         return this; }
        public Builder routeCostMinutes(double v)    { this.routeCostMinutes = v;   return this; }
        public Builder routeDistanceKm(double v)     { this.routeDistanceKm = v;    return this; }
        public BenchmarkStats build()                { return new BenchmarkStats(this); }
    }
}
