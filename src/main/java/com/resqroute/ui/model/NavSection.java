package com.resqroute.ui.model;

/**
 * Enumerates top-level navigation sections within the ResQRoute desktop application.
 */
public enum NavSection {
    DASHBOARD("Dashboard", "Overview & Mission Status", "📊"),
    MAP("City Map", "Road Network & Spatial Topology", "🗺️"),
    EMERGENCIES("Emergencies", "Triage & Incident Lifecycle", "🚨"),
    AMBULANCES("Ambulances", "Fleet Telemetry & Status", "🚑"),
    HOSPITALS("Hospitals", "Capacity & Medical Capabilities", "🏥"),
    SIMULATION("Simulation", "Scenario Execution & Metrics", "⏱️"),
    SETTINGS("Settings", "Parameters & System Information", "⚙️");

    private final String title;
    private final String description;
    private final String icon;

    NavSection(String title, String description, String icon) {
        this.title = title;
        this.description = description;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }
}
