package com.resqroute.optimization;

import java.time.Instant;
import java.util.*;

/**
 * Immutable plan representing an optimal fleet-wide assignment matching multiple available ambulances
 * to pending emergencies.
 */
public final class FleetAssignmentPlan {

    private final List<AssignmentPair> assignments;
    private final List<UnassignedEmergency> unassignedEmergencies;
    private final double totalEstimatedCost;
    private final double totalEstimatedTravelTimeMinutes;
    private final Instant plannedAt;

    public FleetAssignmentPlan(List<AssignmentPair> assignments,
                               List<UnassignedEmergency> unassignedEmergencies,
                               double totalEstimatedCost,
                               double totalEstimatedTravelTimeMinutes,
                               Instant plannedAt) {
        this.assignments = (assignments == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(assignments));
        this.unassignedEmergencies = (unassignedEmergencies == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(unassignedEmergencies));
        this.totalEstimatedCost = totalEstimatedCost;
        this.totalEstimatedTravelTimeMinutes = totalEstimatedTravelTimeMinutes;
        this.plannedAt = Objects.requireNonNull(plannedAt, "Planned timestamp must not be null");

        // Validate plan invariant: no duplicate ambulance or emergency bindings
        Set<String> ambIds = new HashSet<>();
        for (AssignmentPair pair : this.assignments) {
            if (!ambIds.add(pair.getAmbulanceId())) {
                throw new IllegalStateException("Invalid plan: Ambulance '" + pair.getAmbulanceId() + "' assigned more than once");
            }
        }

        Set<String> caseIds = new HashSet<>();
        for (AssignmentPair pair : this.assignments) {
            if (!caseIds.add(pair.getCaseId())) {
                throw new IllegalStateException("Invalid plan: Emergency case '" + pair.getCaseId() + "' assigned more than once");
            }
        }
    }

    public List<AssignmentPair> getAssignments() {
        return assignments;
    }

    public List<UnassignedEmergency> getUnassignedEmergencies() {
        return unassignedEmergencies;
    }

    public double getTotalEstimatedCost() {
        return totalEstimatedCost;
    }

    public double getTotalEstimatedTravelTimeMinutes() {
        return totalEstimatedTravelTimeMinutes;
    }

    public Instant getPlannedAt() {
        return plannedAt;
    }

    public int getAssignedCount() {
        return assignments.size();
    }

    public int getUnassignedCount() {
        return unassignedEmergencies.size();
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }

    public boolean isAmbulanceAssigned(String ambulanceId) {
        if (ambulanceId == null) return false;
        return assignments.stream().anyMatch(p -> p.getAmbulanceId().equals(ambulanceId.trim()));
    }

    public boolean isCaseAssigned(String caseId) {
        if (caseId == null) return false;
        return assignments.stream().anyMatch(p -> p.getCaseId().equals(caseId.trim()));
    }

    public Optional<AssignmentPair> getAssignmentForCase(String caseId) {
        if (caseId == null) return Optional.empty();
        return assignments.stream().filter(p -> p.getCaseId().equals(caseId.trim())).findFirst();
    }

    public Optional<AssignmentPair> getAssignmentForAmbulance(String ambulanceId) {
        if (ambulanceId == null) return Optional.empty();
        return assignments.stream().filter(p -> p.getAmbulanceId().equals(ambulanceId.trim())).findFirst();
    }

    @Override
    public String toString() {
        return String.format("FleetAssignmentPlan[assigned=%d, unassigned=%d, totalTime=%.1f min, totalCost=%.1f]",
                assignments.size(), unassignedEmergencies.size(), totalEstimatedTravelTimeMinutes, totalEstimatedCost);
    }
}
