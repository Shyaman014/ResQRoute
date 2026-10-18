package com.resqroute.optimization.flow;

import java.util.*;

/**
 * Minimum-Cost Maximum-Flow (MCMF) graph solver implemented via the Successive Shortest Path
 * algorithm with Shortest Path Faster Algorithm (SPFA) cycle and negative-cost residual routing.
 *
 * <h3>Complexity:</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> $\mathcal{O}(F \cdot (V + E))$ where $F$ is the maximum flow value,
 *       $V$ is the number of flow nodes, and $E$ is the number of candidate assignment edges.
 *       For unit bipartite matching, $F \le \min(|A|, |E|)$.</li>
 *   <li><b>Space Complexity:</b> $\mathcal{O}(V + E)$ residual adjacency storage.</li>
 * </ul>
 */
public class MinCostMaxFlow {

    public record Result(int maxFlow, double minCost) {}

    private final int vertexCount;
    private final List<List<FlowEdge>> adj;
    private final List<FlowEdge> allEdges;

    public MinCostMaxFlow(int vertexCount) {
        if (vertexCount <= 0) {
            throw new IllegalArgumentException("Vertex count must be positive: " + vertexCount);
        }
        this.vertexCount = vertexCount;
        this.adj = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            this.adj.add(new ArrayList<>());
        }
        this.allEdges = new ArrayList<>();
    }

    /**
     * Adds a directed flow edge with corresponding residual backward edge.
     */
    public FlowEdge addEdge(int from, int to, int capacity, double cost) {
        FlowEdge forward = new FlowEdge(from, to, capacity, cost);
        FlowEdge backward = new FlowEdge(to, from, 0, -cost);
        forward.reverse = backward;
        backward.reverse = forward;

        adj.get(from).add(forward);
        adj.get(to).add(backward);
        allEdges.add(forward);
        return forward;
    }

    /**
     * Computes the maximum flow from source to sink while strictly minimizing the total edge cost.
     */
    public Result compute(int source, int sink) {
        int totalFlow = 0;
        double totalCost = 0.0;

        double[] dist = new double[vertexCount];
        FlowEdge[] parentEdge = new FlowEdge[vertexCount];
        boolean[] inQueue = new boolean[vertexCount];

        while (true) {
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(parentEdge, null);
            Arrays.fill(inQueue, false);

            Queue<Integer> queue = new ArrayDeque<>();
            dist[source] = 0.0;
            queue.add(source);
            inQueue[source] = true;

            while (!queue.isEmpty()) {
                int u = queue.poll();
                inQueue[u] = false;

                for (FlowEdge edge : adj.get(u)) {
                    if (edge.residualCapacity() > 0) {
                        double newDist = dist[u] + edge.cost;
                        if (newDist + 1e-9 < dist[edge.to]) {
                            dist[edge.to] = newDist;
                            parentEdge[edge.to] = edge;
                            if (!inQueue[edge.to]) {
                                queue.add(edge.to);
                                inQueue[edge.to] = true;
                            }
                        }
                    }
                }
            }

            if (dist[sink] == Double.POSITIVE_INFINITY) {
                // No augmenting path remaining in the residual network
                break;
            }

            // Find bottleneck capacity along the shortest augmenting path
            int bottleneck = Integer.MAX_VALUE;
            for (int curr = sink; curr != source; curr = parentEdge[curr].from) {
                bottleneck = Math.min(bottleneck, parentEdge[curr].residualCapacity());
            }

            // Augment flow along the path
            for (int curr = sink; curr != source; curr = parentEdge[curr].from) {
                parentEdge[curr].addFlow(bottleneck);
            }

            totalFlow += bottleneck;
            totalCost += bottleneck * dist[sink];
        }

        return new Result(totalFlow, totalCost);
    }

    public List<FlowEdge> getAllEdges() {
        return Collections.unmodifiableList(allEdges);
    }

    public List<FlowEdge> getIncidentEdges(int u) {
        return Collections.unmodifiableList(adj.get(u));
    }
}
