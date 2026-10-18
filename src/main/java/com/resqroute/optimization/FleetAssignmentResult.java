package com.resqroute.optimization;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;

import java.time.Instant;
import java.util.*;

/**
 * Result outcome of evaluating and committing a fleet-wide assignment optimization plan.
 */
public final class FleetAssignmentResult {

    private final boolean committed;
    private final FleetAssignmentPlan plan;
    private final List<EmergencyCase> committedCases;
    private final List<Ambulance> committedAmbulances;
    private final Instant committedAt;
    private final String message;

    private FleetAssignmentResult(boolean committed,
                                  FleetAssignmentPlan plan,
                                  List<EmergencyCase> committedCases,
                                  List<Ambulance> committedAmbulances,
                                  Instant committedAt,
                                  String message) {
        this.committed = committed;
        this.plan = Objects.requireNonNull(plan, "Plan must not be null");
        this.committedCases = (committedCases == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(committedCases));
        this.committedAmbulances = (committedAmbulances == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(committedAmbulances));
        this.committedAt = Objects.requireNonNull(committedAt, "Committed timestamp must not be null");
        this.message = (message == null) ? "" : message.trim();
    }

    public static FleetAssignmentResult committed(FleetAssignmentPlan plan,
                                                  List<EmergencyCase> cases,
                                                  List<Ambulance> ambulances,
                                                  Instant committedAt) {
        return new FleetAssignmentResult(
                true,
                plan,
                cases,
                ambulances,
                committedAt,
                String.format("Successfully committed %d assignments (total cost: %.1f)",
                        plan.getAssignedCount(), plan.getTotalEstimatedCost())
        );
    }

    public static FleetAssignmentResult uncommitted(FleetAssignmentPlan plan, String reason, Instant plannedAt) {
        return new FleetAssignmentResult(false, plan, List.of(), List.of(), plannedAt, reason);
    }

    public boolean isCommitted() {
        return committed;
    }

    public FleetAssignmentPlan getPlan() {
        return plan;
    }

    public List<EmergencyCase> getCommittedCases() {
        return committedCases;
    }

    public List<Ambulance> getCommittedAmbulances() {
        return committedAmbulances;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public String getMessage() {
        return message;
    }

    public int getAssignedCount() {
        return plan.getAssignedCount();
    }

    public int getUnassignedCount() {
        return plan.getUnassignedCount();
    }

    @Override
    public String toString() {
        return String.format("FleetAssignmentResult[committed=%b, assigned=%d, unassigned=%d, msg='%s']",
                committed, getAssignedCount(), getUnassignedCount(), message);
    }
}
