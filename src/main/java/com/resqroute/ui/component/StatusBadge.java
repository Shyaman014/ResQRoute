package com.resqroute.ui.component;

import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.model.HospitalStatus;
import com.resqroute.ui.model.UiSimulationState;
import javafx.geometry.Insets;
import javafx.scene.control.Label;

/**
 * Reusable stylized pill badge indicating statuses across emergency cases, ambulances, hospitals, and simulation.
 */
public class StatusBadge extends Label {

    public StatusBadge(String text, String badgeClass) {
        super(text);
        getStyleClass().addAll("status-badge", badgeClass);
        setPadding(new Insets(3, 8, 3, 8));
    }

    public static StatusBadge ofEmergencyStatus(EmergencyStatus status) {
        if (status == null) return new StatusBadge("UNKNOWN", "badge-neutral");
        String badgeClass = switch (status) {
            case REPORTED, WAITING_FOR_DISPATCH -> "badge-warning";
            case AMBULANCE_ASSIGNED, AMBULANCE_EN_ROUTE -> "badge-info";
            case PATIENT_PICKED_UP, TRANSPORTING -> "badge-primary";
            case COMPLETED -> "badge-success";
            case CANCELLED -> "badge-danger";
        };
        return new StatusBadge(status.name().replace('_', ' '), badgeClass);
    }

    public static StatusBadge ofSeverity(EmergencySeverity severity) {
        if (severity == null) return new StatusBadge("NORMAL", "badge-neutral");
        String badgeClass = switch (severity) {
            case CRITICAL -> "badge-danger";
            case HIGH -> "badge-warning";
            case MEDIUM -> "badge-info";
            case LOW -> "badge-neutral";
        };
        return new StatusBadge(severity.name(), badgeClass);
    }

    public static StatusBadge ofAmbulanceStatus(AmbulanceStatus status) {
        if (status == null) return new StatusBadge("UNKNOWN", "badge-neutral");
        String badgeClass = switch (status) {
            case AVAILABLE -> "badge-success";
            case ASSIGNED -> "badge-info";
            case EN_ROUTE -> "badge-info";
            case ON_SCENE -> "badge-primary";
            case TRANSPORTING -> "badge-warning";
            case OUT_OF_SERVICE -> "badge-danger";
        };
        return new StatusBadge(status.name().replace('_', ' '), badgeClass);
    }

    public static StatusBadge ofHospitalStatus(HospitalStatus status) {
        if (status == null) return new StatusBadge("UNKNOWN", "badge-neutral");
        String badgeClass = switch (status) {
            case OPEN -> "badge-success";
            case BUSY -> "badge-warning";
            case CLOSED -> "badge-danger";
        };
        return new StatusBadge(status.name(), badgeClass);
    }

    public static StatusBadge ofSimulationState(UiSimulationState state) {
        if (state == null) return new StatusBadge("UNKNOWN", "badge-neutral");
        return new StatusBadge(state.getDisplayName(), state.getBadgeStyleClass());
    }
}
