package com.resqroute.ui.component;

import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.model.HospitalStatus;
import com.resqroute.ui.model.UiSimulationState;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * Reusable accessible pill badge indicating statuses across emergency cases, ambulances, hospitals, and simulation.
 * Combines distinct text badges, geometric symbols/icons, and assistive accessibleText to ensure compliance
 * with accessibility standards without relying solely on color.
 */
public class StatusBadge extends Label {

    public StatusBadge(String text, String badgeClass, String accessibleDescription) {
        super(text);
        getStyleClass().addAll("status-badge", badgeClass);
        setPadding(new Insets(3, 8, 3, 8));
        if (accessibleDescription != null && !accessibleDescription.isBlank()) {
            setAccessibleText(accessibleDescription);
            setTooltip(new Tooltip(accessibleDescription));
        }
    }

    public static StatusBadge ofEmergencyStatus(EmergencyStatus status) {
        if (status == null) {
            return new StatusBadge("UNKNOWN", "badge-neutral", "Emergency Status: Unknown");
        }
        String badgeClass = switch (status) {
            case REPORTED, WAITING_FOR_DISPATCH -> "badge-warning";
            case AMBULANCE_ASSIGNED, AMBULANCE_EN_ROUTE -> "badge-info";
            case PATIENT_PICKED_UP, TRANSPORTING -> "badge-primary";
            case COMPLETED -> "badge-success";
            case CANCELLED -> "badge-danger";
        };
        String prefix = switch (status) {
            case REPORTED -> "📝 ";
            case WAITING_FOR_DISPATCH -> "⏳ ";
            case AMBULANCE_ASSIGNED -> "📋 ";
            case AMBULANCE_EN_ROUTE -> "🚑 ";
            case PATIENT_PICKED_UP -> "🩺 ";
            case TRANSPORTING -> "🏥 ";
            case COMPLETED -> "✓ ";
            case CANCELLED -> "✕ ";
        };
        String labelText = prefix + status.name().replace('_', ' ');
        return new StatusBadge(labelText, badgeClass, "Emergency Lifecycle Status: " + status.name());
    }

    public static StatusBadge ofSeverity(EmergencySeverity severity) {
        if (severity == null) {
            return new StatusBadge("NORMAL", "badge-neutral", "Severity: Normal");
        }
        String badgeClass = switch (severity) {
            case CRITICAL -> "badge-danger";
            case HIGH -> "badge-warning";
            case MEDIUM -> "badge-info";
            case LOW -> "badge-neutral";
        };
        String symbol = switch (severity) {
            case CRITICAL -> "⛔ [CRITICAL]";
            case HIGH -> "▲ [HIGH]";
            case MEDIUM -> "● [MEDIUM]";
            case LOW -> "○ [LOW]";
        };
        return new StatusBadge(symbol, badgeClass, "Triage Severity Level: " + severity.name());
    }

    public static StatusBadge ofAmbulanceStatus(AmbulanceStatus status) {
        if (status == null) {
            return new StatusBadge("UNKNOWN", "badge-neutral", "Ambulance Status: Unknown");
        }
        String badgeClass = switch (status) {
            case AVAILABLE -> "badge-success";
            case ASSIGNED, EN_ROUTE, RETURNING -> "badge-info";
            case ON_SCENE -> "badge-primary";
            case TRANSPORTING -> "badge-warning";
            case OUT_OF_SERVICE -> "badge-danger";
        };
        String prefix = switch (status) {
            case AVAILABLE -> "🟢 ";
            case ASSIGNED -> "📋 ";
            case EN_ROUTE -> "🚑 ";
            case ON_SCENE -> "🩺 ";
            case TRANSPORTING -> "🏥 ";
            case RETURNING -> "🔙 ";
            case OUT_OF_SERVICE -> "⛔ ";
        };
        String labelText = prefix + status.name().replace('_', ' ');
        return new StatusBadge(labelText, badgeClass, "Ambulance Vehicle Status: " + status.name());
    }

    public static StatusBadge ofHospitalStatus(HospitalStatus status) {
        if (status == null) {
            return new StatusBadge("UNKNOWN", "badge-neutral", "Hospital Status: Unknown");
        }
        String badgeClass = switch (status) {
            case OPEN -> "badge-success";
            case BUSY -> "badge-warning";
            case CLOSED -> "badge-danger";
        };
        String prefix = switch (status) {
            case OPEN -> "🟢 ";
            case BUSY -> "🟡 ";
            case CLOSED -> "🔴 ";
        };
        return new StatusBadge(prefix + status.name(), badgeClass, "Hospital Operational Status: " + status.name());
    }

    public static StatusBadge ofSimulationState(UiSimulationState state) {
        if (state == null) {
            return new StatusBadge("UNKNOWN", "badge-neutral", "Simulation State: Unknown");
        }
        String prefix = switch (state) {
            case RUNNING -> "▶ ";
            case PAUSED -> "⏸ ";
            case IDLE -> "⏹ ";
            case COMPLETED -> "✓ ";
            case UNINITIALIZED -> "○ ";
        };
        return new StatusBadge(prefix + state.getDisplayName(), state.getBadgeStyleClass(), "Simulation Execution State: " + state.getDisplayName());
    }
}
