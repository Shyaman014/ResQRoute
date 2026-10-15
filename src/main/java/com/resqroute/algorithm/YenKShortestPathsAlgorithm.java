package com.resqroute.algorithm;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.EdgeKey;
import com.resqroute.model.Node;
import com.resqroute.model.RouteResult;

import java.util.*;

/**
 * Implementation of Yen's K-Shortest Loopless Paths algorithm for generating alternative emergency routes.
 *
 * <h3>Algorithm Overview</h3>
 * <p>Yen's algorithm computes the top {@code K} loopless paths in non-decreasing order of cost
 * (minimizing total travel time in minutes):
 * <ol>
 *   <li><b>Baseline Path:</b> Computes the initial 1st shortest path \(A[0]\) using the core Dijkstra engine.</li>
 *   <li><b>Spur Iterations:</b> For each subsequent path \(k \in [1, K-1]\), iterates through every node
 *       (except the destination) of the previous path \(A[k-1]\) as a candidate <i>spur node</i>.
 *       <ul>
 *         <li><b>Root Path:</b> The sub-path from origin to the spur node.</li>
 *         <li><b>Exclusions:</b>
 *           <ul>
 *             <li>Temporarily excludes edges originating from the spur node if they were already used
 *                 by any existing path in \(A\) that shares the identical root path.</li>
 *             <li>Temporarily excludes all prior nodes in the root path (except the spur node itself)
 *                 to mathematically guarantee <b>loop-free (simple)</b> paths.</li>
 *           </ul>
 *         </li>
 *         <li><b>Spur Path:</b> Computes the shortest path from the spur node to the destination using Dijkstra
 *             subject to the active exclusion constraints.</li>
 *         <li><b>Candidate Assembly:</b> If a spur path exists, joins Root Path + Spur Path and inserts
 *             into a candidate min-priority queue \(B\) if not previously discovered.</li>
 *       </ul>
 *   </li>
 *   <li><b>Selection:</b> Pops the lowest travel-time candidate from \(B\) and appends it to \(A\).
 *       If \(B\) is exhausted, fewer than \(K\) loopless routes exist, and the search terminates early.</li>
 * </ol>
 * </p>
 *
 * <h3>Non-Destructive Graph Invariant</h3>
 * <p>The original {@link RoadGraph} is <b>never mutated</b>. All node and directed-edge exclusions
 * are evaluated transiently during traversal via memory-efficient sets passed into the routing subroutine.
 * This guarantees absolute thread-safety and preserves graph integrity for concurrent readers.</p>
 *
 * <h3>Algorithmic Complexity</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> \(\mathcal{O}(K \cdot |V| \cdot (|E| + |V| \log |V|))\), where \(K\) is
 *       the requested route count, \(|V|\) is node count, and \(|E|\) is edge count.
 *       For each of the \(K\) paths, at most \(|V|\) spur nodes are tested, each running a Dijkstra query.</li>
 *   <li><b>Space Complexity:</b> \(\mathcal{O}(K \cdot |V|)\) auxiliary space to store candidate and accepted paths.</li>
 * </ul>
 */
public class YenKShortestPathsAlgorithm {

    private final DijkstraRoutingAlgorithm dijkstra;

    /**
     * Constructs Yen's algorithm using the default Dijkstra routing engine.
     */
    public YenKShortestPathsAlgorithm() {
        this.dijkstra = new DijkstraRoutingAlgorithm();
    }

    /**
     * Constructs Yen's algorithm using an injected Dijkstra routing engine.
     *
     * @param dijkstra Dijkstra instance; must not be null.
     */
    public YenKShortestPathsAlgorithm(DijkstraRoutingAlgorithm dijkstra) {
        this.dijkstra = Objects.requireNonNull(dijkstra, "Dijkstra routing algorithm must not be null");
    }

    /**
     * Finds up to K loopless alternative routes between source and destination, ranked by travel time.
     *
     * @param graph         Road network graph; must not be null.
     * @param sourceId      Origin waypoint identifier; must exist in graph.
     * @param destinationId Destination waypoint identifier; must exist in graph.
     * @param k             Maximum number of alternative routes desired; must be >= 1.
     * @return Ordered list of up to K valid, loopless, unique alternative RouteResults.
     * @throws IllegalArgumentException if k < 1 or node IDs are invalid.
     */
    public List<RouteResult> findKShortestPaths(RoadGraph graph, String sourceId, String destinationId, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Maximum route count K must be at least 1, but got: " + k);
        }
        Objects.requireNonNull(graph, "RoadGraph must not be null");
        validateNodeId(sourceId, "Source");
        validateNodeId(destinationId, "Destination");

        String srcTrimmed = sourceId.trim();
        String dstTrimmed = destinationId.trim();

        Node sourceNode = graph.getRequiredNode(srcTrimmed);
        Node destinationNode = graph.getRequiredNode(dstTrimmed);

        // Edge case: Source is identical to Destination
        if (srcTrimmed.equals(dstTrimmed)) {
            return List.of(RouteResult.singleNode(sourceNode));
        }

        // List A: Holds the finalized K shortest loopless paths
        List<RouteResult> A = new ArrayList<>();

        // PriorityQueue B: Min-heap of potential candidate paths ordered by travel time, tie-broken by distance
        PriorityQueue<RouteResult> B = new PriorityQueue<>(Comparator
                .comparingDouble(RouteResult::getTotalTravelTimeMinutes)
                .thenComparingDouble(RouteResult::getTotalDistanceKm));

        // Deduplication set tracking unique node ID sequences
        Set<List<String>> discoveredPathSignatures = new HashSet<>();

        // Step 1: Determine the 1st shortest path using Dijkstra
        RouteResult firstPath = dijkstra.findFastestRoute(graph, srcTrimmed, dstTrimmed);
        if (!firstPath.isReachable()) {
            return Collections.emptyList();
        }

        A.add(firstPath);
        discoveredPathSignatures.add(firstPath.getNodeIds());

        // Step 2: Iteratively discover the 2nd through K-th paths
        for (int kIdx = 1; kIdx < k; kIdx++) {
            RouteResult previousPath = A.get(kIdx - 1);
            List<Node> prevNodes = previousPath.getPath();
            List<Edge> prevEdges = previousPath.getEdges();

            // The spur node ranges from the first node up to the second-to-last node
            for (int i = 0; i < prevNodes.size() - 1; i++) {
                Node spurNode = prevNodes.get(i);
                List<Node> rootPathNodes = prevNodes.subList(0, i + 1);
                List<Edge> rootPathEdges = prevEdges.subList(0, i);

                Set<EdgeKey> excludedEdges = new HashSet<>();
                Set<String> excludedNodes = new HashSet<>();

                // Exclude edges branching from the spur node that are used by existing paths in A with the same root path
                for (RouteResult existingPath : A) {
                    List<Node> existingNodes = existingPath.getPath();
                    if (existingNodes.size() > i && existingNodes.subList(0, i + 1).equals(rootPathNodes)) {
                        if (i + 1 < existingNodes.size()) {
                            excludedEdges.add(new EdgeKey(existingNodes.get(i).getId(), existingNodes.get(i + 1).getId()));
                        }
                    }
                }

                // Exclude all nodes in the root path except the spur node to guarantee loopless paths
                for (int r = 0; r < i; r++) {
                    excludedNodes.add(rootPathNodes.get(r).getId());
                }

                // Calculate spur path from spurNode to destination using Dijkstra subject to exclusions
                RouteResult spurPath = dijkstra.findFastestRoute(graph, spurNode.getId(), dstTrimmed, excludedNodes, excludedEdges);

                if (spurPath.isReachable()) {
                    // Assemble full candidate path: Root Path + Spur Path
                    List<Node> totalNodes = new ArrayList<>(rootPathNodes);
                    List<Node> spurNodesList = spurPath.getPath();
                    // Append spur path nodes excluding the spur node (index 0) to avoid duplication
                    for (int s = 1; s < spurNodesList.size(); s++) {
                        totalNodes.add(spurNodesList.get(s));
                    }

                    List<Edge> totalEdges = new ArrayList<>(rootPathEdges);
                    totalEdges.addAll(spurPath.getEdges());

                    double totalDistance = totalEdges.stream().mapToDouble(Edge::getDistanceKm).sum();
                    double totalTime = totalEdges.stream().mapToDouble(Edge::getTravelTimeMinutes).sum();

                    RouteResult candidate = RouteResult.reachable(totalNodes, totalEdges, totalDistance, totalTime);
                    List<String> candidateSignature = candidate.getNodeIds();

                    if (!discoveredPathSignatures.contains(candidateSignature)) {
                        discoveredPathSignatures.add(candidateSignature);
                        B.offer(candidate);
                    }
                }
            }

            if (B.isEmpty()) {
                // No further alternative loopless paths exist
                break;
            }

            // Move the best candidate from B to A
            A.add(B.poll());
        }

        return Collections.unmodifiableList(A);
    }

    private void validateNodeId(String id, String role) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(role + " node ID must not be null or blank");
        }
    }
}
