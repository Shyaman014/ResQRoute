package com.resqroute.benchmark;

import java.util.Objects;

/**
 * Immutable record of a single algorithm execution against a benchmark scenario.
 * Captures timing, nodes explored, route validity, and total cost.
 */
public final class AlgorithmRunResult {

    private final String algorithmName;
    private final String scenarioId;
    private final String sourceNodeId;
    private final String destinationNodeId;
    private final boolean routeFound;
    private final double totalTravelTimeMinutes;
    private final double totalDistanceKm;
    private final int nodesExplored;
    private final int edgesExamined;
    private final long executionNanos;

    public AlgorithmRunResult(String algorithmName,
                               String scenarioId,
                               String sourceNodeId,
                               String destinationNodeId,
                               boolean routeFound,
                               double totalTravelTimeMinutes,
                               double totalDistanceKm,
                               int nodesExplored,
                               int edgesExamined,
                               long executionNanos) {
        this.algorithmName = Objects.requireNonNull(algorithmName);
        this.scenarioId = Objects.requireNonNull(scenarioId);
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId);
        this.destinationNodeId = Objects.requireNonNull(destinationNodeId);
        this.routeFound = routeFound;
        this.totalTravelTimeMinutes = totalTravelTimeMinutes;
        this.totalDistanceKm = totalDistanceKm;
        this.nodesExplored = nodesExplored;
        this.edgesExamined = edgesExamined;
        this.executionNanos = executionNanos;
    }

    public String getAlgorithmName()           { return algorithmName; }
    public String getScenarioId()              { return scenarioId; }
    public String getSourceNodeId()            { return sourceNodeId; }
    public String getDestinationNodeId()       { return destinationNodeId; }
    public boolean isRouteFound()              { return routeFound; }
    public double getTotalTravelTimeMinutes()   { return totalTravelTimeMinutes; }
    public double getTotalDistanceKm()          { return totalDistanceKm; }
    public int getNodesExplored()              { return nodesExplored; }
    public int getEdgesExamined()              { return edgesExamined; }
    public long getExecutionNanos()             { return executionNanos; }
    public double getExecutionMillis()          { return executionNanos / 1_000_000.0; }

    @Override
    public String toString() {
        return String.format("AlgorithmRunResult[algo=%s, found=%b, time=%.2fmin, dist=%.2fkm, " +
                             "nodes=%d, edges=%d, execNs=%d]",
                algorithmName, routeFound, totalTravelTimeMinutes, totalDistanceKm,
                nodesExplored, edgesExamined, executionNanos);
    }
}
