package com.resqroute.hospital;

import com.resqroute.model.Hospital;
import com.resqroute.model.RouteResult;

import java.util.Objects;

/**
 * Immutable recommendation details for an eligible hospital destination.
 *
 * <p>Contains the hospital entity, the calculated traffic-aware route from the patient
 * pickup location, estimated arrival time, physical distance, ranking position, and
 * a clinical rationale explaining the recommendation.</p>
 */
public final class HospitalRecommendation {

    private final Hospital hospital;
    private final RouteResult route;
    private final double estimatedTravelTimeMinutes;
    private final double distanceKm;
    private final boolean eligible;
    private final int rank;
    private final String rationale;

    public HospitalRecommendation(Hospital hospital,
                                  RouteResult route,
                                  double estimatedTravelTimeMinutes,
                                  double distanceKm,
                                  boolean eligible,
                                  int rank,
                                  String rationale) {
        this.hospital = Objects.requireNonNull(hospital, "Hospital must not be null");
        this.route = Objects.requireNonNull(route, "RouteResult must not be null");
        if (estimatedTravelTimeMinutes < 0) {
            throw new IllegalArgumentException("Travel time cannot be negative: " + estimatedTravelTimeMinutes);
        }
        if (distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative: " + distanceKm);
        }
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.distanceKm = distanceKm;
        this.eligible = eligible;
        this.rank = rank;
        this.rationale = (rationale == null || rationale.trim().isEmpty())
                ? "Eligible hospital destination"
                : rationale.trim();
    }

    public Hospital getHospital() {
        return hospital;
    }

    public String getHospitalId() {
        return hospital.getId();
    }

    public String getHospitalName() {
        return hospital.getName();
    }

    public String getLocationNodeId() {
        return hospital.getLocationNodeId();
    }

    public RouteResult getRoute() {
        return route;
    }

    public double getEstimatedTravelTimeMinutes() {
        return estimatedTravelTimeMinutes;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public boolean isEligible() {
        return eligible;
    }

    public int getRank() {
        return rank;
    }

    public String getRationale() {
        return rationale;
    }

    public HospitalRecommendation withRank(int newRank) {
        return new HospitalRecommendation(
                this.hospital,
                this.route,
                this.estimatedTravelTimeMinutes,
                this.distanceKm,
                this.eligible,
                newRank,
                this.rationale
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HospitalRecommendation that)) return false;
        return hospital.equals(that.hospital);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hospital);
    }

    @Override
    public String toString() {
        return String.format("HospitalRecommendation[rank=%d, hospital='%s' (%s), ETA=%.1f min, dist=%.1f km, beds=%d, rationale='%s']",
                rank, hospital.getName(), hospital.getId(), estimatedTravelTimeMinutes, distanceKm,
                hospital.getAvailableCapacity(), rationale);
    }
}
