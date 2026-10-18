package com.resqroute.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Benchmark Framework Smoke & Correctness Test")
class BenchmarkSmokeTest {

    @Test
    @DisplayName("Verify synthetic network generation produces valid, connected graph")
    void testSyntheticNetworkGeneration() {
        SyntheticNetworkGenerator.BenchmarkDataset dataset =
                SyntheticNetworkGenerator.generate(35, 6, 4, 6, 777L);

        assertNotNull(dataset.graph());
        assertEquals(35, dataset.nodeIds().size());
        assertEquals(6, dataset.ambulances().size());
        assertEquals(4, dataset.hospitals().size());
        assertEquals(6, dataset.emergencies().size());
        assertFalse(dataset.routingPairs().isEmpty());

        for (String nodeId : dataset.nodeIds()) {
            assertNotNull(dataset.graph().getNode(nodeId));
            assertFalse(dataset.graph().getOutgoingEdges(nodeId).isEmpty(), "Node should have connected edges");
        }
    }

    @Test
    @DisplayName("Verify algorithm benchmark suite executes without error and collects valid metrics")
    void testBenchmarkSuiteExecution() {
        SyntheticNetworkGenerator.BenchmarkDataset dataset =
                SyntheticNetworkGenerator.generate(30, 5, 3, 5, 888L);
        BenchmarkHarness harness = BenchmarkHarness.smoke();
        AlgorithmBenchmarkSuite suite = new AlgorithmBenchmarkSuite(harness);

        // 1. Dijkstra vs A* Routing
        List<BenchmarkResult> routingResults = suite.benchmarkRouting(dataset, "Smoke-Scale");
        assertEquals(2, routingResults.size());
        for (BenchmarkResult res : routingResults) {
            assertTrue(res.meanMicros() > 0.0);
            assertTrue(res.opsPerSecond() > 0.0);
            assertEquals("Smoke-Scale (15 pairs)", res.scenario());
        }

        // 2. Yen's K-Shortest Paths
        List<BenchmarkResult> yenResults = suite.benchmarkAlternativeRoutes(dataset, "Smoke-Scale");
        assertEquals(3, yenResults.size()); // K=2, 3, 5
        for (BenchmarkResult res : yenResults) {
            assertTrue(res.meanMicros() > 0.0);
            assertTrue(res.benchmarkName().contains("Yen"));
        }

        // 3. Dispatch Strategies
        List<BenchmarkResult> dispatchResults = suite.benchmarkDispatchStrategies(dataset, "Smoke-Scale");
        assertEquals(2, dispatchResults.size()); // MCMF & Greedy
        for (BenchmarkResult res : dispatchResults) {
            assertTrue(res.meanMicros() > 0.0);
        }

        // 4. Hospital Selection
        BenchmarkResult hospitalResult = suite.benchmarkHospitalSelection(dataset, "Smoke-Scale");
        assertNotNull(hospitalResult);
        assertTrue(hospitalResult.meanMicros() > 0.0);

        // 5. Graph Operations
        List<BenchmarkResult> graphResults = suite.benchmarkGraphOperations(dataset, "Smoke-Scale");
        assertFalse(graphResults.isEmpty());
        for (BenchmarkResult res : graphResults) {
            assertTrue(res.meanMicros() > 0.0);
            assertNotNull(res.toTabularRow());
        }
    }
}
