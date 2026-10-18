package com.resqroute.ui.model;

/**
 * High-level presentation states of the simulation engine for user interface display.
 */
public enum UiSimulationState {
    UNINITIALIZED("Not Initialized", "badge-neutral"),
    IDLE("Ready", "badge-info"),
    RUNNING("Running", "badge-success"),
    PAUSED("Paused", "badge-warning"),
    COMPLETED("Finished", "badge-neutral");

    private final String displayName;
    private final String badgeStyleClass;

    UiSimulationState(String displayName, String badgeStyleClass) {
        this.displayName = displayName;
        this.badgeStyleClass = badgeStyleClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeStyleClass() {
        return badgeStyleClass;
    }
}
