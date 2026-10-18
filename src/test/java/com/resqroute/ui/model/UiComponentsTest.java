package com.resqroute.ui.model;

import com.resqroute.model.AmbulanceStatus;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.model.HospitalStatus;
import com.resqroute.ui.component.StatusBadge;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UI Models, Messages, and StatusBadge Mapping Tests")
class UiComponentsTest {

    private static boolean fxAvailable = false;

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
            fxAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            fxAvailable = true;
        } catch (Throwable t) {
            fxAvailable = false;
        }
    }

    @Test
    @DisplayName("NavSection enum has consistent icons, titles, and descriptions")
    void navSectionProperties() {
        for (NavSection section : NavSection.values()) {
            assertNotNull(section.getTitle());
            assertFalse(section.getTitle().isBlank());
            assertNotNull(section.getDescription());
            assertFalse(section.getDescription().isBlank());
            assertNotNull(section.getIcon());
            assertFalse(section.getIcon().isBlank());
        }

        assertEquals("Dashboard", NavSection.DASHBOARD.getTitle());
        assertEquals("City Map", NavSection.MAP.getTitle());
        assertEquals("Emergencies", NavSection.EMERGENCIES.getTitle());
        assertEquals("Ambulances", NavSection.AMBULANCES.getTitle());
        assertEquals("Hospitals", NavSection.HOSPITALS.getTitle());
        assertEquals("Simulation", NavSection.SIMULATION.getTitle());
        assertEquals("Settings", NavSection.SETTINGS.getTitle());
    }

    @Test
    @DisplayName("UiSimulationState displays appropriate labels and badge styles")
    void uiSimulationStateProperties() {
        assertEquals("Not Initialized", UiSimulationState.UNINITIALIZED.getDisplayName());
        assertEquals("Ready", UiSimulationState.IDLE.getDisplayName());
        assertEquals("Running", UiSimulationState.RUNNING.getDisplayName());
        assertEquals("Paused", UiSimulationState.PAUSED.getDisplayName());
        assertEquals("Finished", UiSimulationState.COMPLETED.getDisplayName());

        assertEquals("badge-success", UiSimulationState.RUNNING.getBadgeStyleClass());
        assertEquals("badge-warning", UiSimulationState.PAUSED.getBadgeStyleClass());
    }

    @Test
    @DisplayName("UiMessage factory creates correct message types and symbols")
    void uiMessageFactory() {
        UiMessage info = UiMessage.info("Info text");
        assertEquals(UiMessage.MessageType.INFO, info.type());
        assertEquals("Info text", info.text());
        assertEquals("ℹ️", info.type().getSymbol());
        assertEquals("info", info.type().getCssClass());

        UiMessage success = UiMessage.success("Success text");
        assertEquals(UiMessage.MessageType.SUCCESS, success.type());
        assertEquals("✓", success.type().getSymbol());
        assertEquals("success", success.type().getCssClass());

        UiMessage warning = UiMessage.warning("Warning text");
        assertEquals(UiMessage.MessageType.WARNING, warning.type());
        assertEquals("⚠️", warning.type().getSymbol());

        UiMessage error = UiMessage.error("Error text");
        assertEquals(UiMessage.MessageType.ERROR, error.type());
        assertEquals("❌", error.type().getSymbol());
    }

    @Test
    @DisplayName("StatusBadge mappings correctly resolve when JavaFX toolkit is active")
    void statusBadgeMappings() {
        if (!fxAvailable) {
            return;
        }

        StatusBadge badge1 = StatusBadge.ofEmergencyStatus(EmergencyStatus.REPORTED);
        assertTrue(badge1.getStyleClass().contains("badge-warning"));

        StatusBadge badge2 = StatusBadge.ofEmergencyStatus(EmergencyStatus.COMPLETED);
        assertTrue(badge2.getStyleClass().contains("badge-success"));

        StatusBadge badge3 = StatusBadge.ofSeverity(EmergencySeverity.CRITICAL);
        assertTrue(badge3.getStyleClass().contains("badge-danger"));

        StatusBadge badge4 = StatusBadge.ofAmbulanceStatus(AmbulanceStatus.AVAILABLE);
        assertTrue(badge4.getStyleClass().contains("badge-success"));

        StatusBadge badge5 = StatusBadge.ofHospitalStatus(HospitalStatus.OPEN);
        assertTrue(badge5.getStyleClass().contains("badge-success"));

        StatusBadge badge6 = StatusBadge.ofHospitalStatus(HospitalStatus.CLOSED);
        assertTrue(badge6.getStyleClass().contains("badge-danger"));
    }
}
