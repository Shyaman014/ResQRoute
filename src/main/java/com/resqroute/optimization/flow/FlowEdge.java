package com.resqroute.optimization.flow;

/**
 * Directed residual edge in the Minimum-Cost Maximum-Flow network.
 */
public class FlowEdge {

    public final int from;
    public final int to;
    public final int capacity;
    public final double cost;
    public int flow;
    public FlowEdge reverse;

    public FlowEdge(int from, int to, int capacity, double cost) {
        this.from = from;
        this.to = to;
        this.capacity = capacity;
        this.cost = cost;
        this.flow = 0;
    }

    public int residualCapacity() {
        return capacity - flow;
    }

    public void addFlow(int delta) {
        this.flow += delta;
        if (this.reverse != null) {
            this.reverse.flow -= delta;
        }
    }
}
