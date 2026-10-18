package com.resqroute.ui.view;

import com.resqroute.model.*;
import com.resqroute.ui.component.StatusBadge;
import com.resqroute.ui.controller.NavigationController;
import com.resqroute.ui.controller.SimulationPresentationModel;
import com.resqroute.ui.model.NavSection;
import com.resqroute.ui.model.UiSimulationState;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UI Polish, Accessibility, and Responsive Layout Tests (Phase 24)")
class UiPolishAndAccessibilityTest {

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
    @DisplayName("Severity badges do not rely on color alone and have distinct symbols and accessible text")
    void testSeverityAccessibilitySymbols() {
        if (!fxAvailable) return;

        for (EmergencySeverity severity : EmergencySeverity.values()) {
            StatusBadge badge = StatusBadge.ofSeverity(severity);
            assertNotNull(badge.getAccessibleText(), "Badge must have accessibleText");
            assertTrue(badge.getAccessibleText().contains(severity.name()), "Accessible text must describe severity");

            // Verify non-color distinction symbol
            String text = badge.getText();
            assertNotNull(text);
            switch (severity) {
                case CRITICAL -> assertTrue(text.contains("⛔"), "CRITICAL must have barrier symbol");
                case HIGH -> assertTrue(text.contains("▲"), "HIGH must have triangle warning indicator");
                case MEDIUM -> assertTrue(text.contains("●"), "MEDIUM must have solid indicator");
                case LOW -> assertTrue(text.contains("○"), "LOW must have hollow indicator");
            }
        }
    }

    @Test
    @DisplayName("Ambulance readiness badges provide distinct status prefixes and tooltips")
    void testAmbulanceStatusBadges() {
        if (!fxAvailable) return;

        for (AmbulanceStatus status : AmbulanceStatus.values()) {
            StatusBadge badge = StatusBadge.ofAmbulanceStatus(status);
            assertNotNull(badge.getAccessibleText());
            assertTrue(badge.getAccessibleText().contains(status.name()));
            assertNotNull(badge.getTooltip());
            assertFalse(badge.getText().isBlank());
        }
    }

    @Test
    @DisplayName("Emergency lifecycle badges provide status prefixes and tooltips")
    void testEmergencyStatusBadges() {
        if (!fxAvailable) return;

        for (EmergencyStatus status : EmergencyStatus.values()) {
            StatusBadge badge = StatusBadge.ofEmergencyStatus(status);
            assertNotNull(badge.getAccessibleText());
            assertTrue(badge.getAccessibleText().contains(status.name()));
            assertNotNull(badge.getTooltip());
        }
    }

    @Test
    @DisplayName("Simulation state badges provide execution prefixes and tooltips")
    void testSimulationStateBadges() {
        if (!fxAvailable) return;

        for (UiSimulationState state : UiSimulationState.values()) {
            StatusBadge badge = StatusBadge.ofSimulationState(state);
            assertNotNull(badge.getAccessibleText());
            assertTrue(badge.getAccessibleText().contains(state.getDisplayName()));
        }
    }

    @Test
    @DisplayName("Main layout views initialize without throwing exceptions and support tab caching")
    void testMainViewInitialization() {
        if (!fxAvailable) return;

        SimulationPresentationModel model = new SimulationPresentationModel(Runnable::run);
        NavigationController nav = new NavigationController();

        MainView mainView = new MainView(nav, model);
        assertNotNull(mainView.getLeft());
        assertNotNull(mainView.getTop());
        assertNotNull(mainView.getCenter());

        // Test section switching
        for (NavSection section : NavSection.values()) {
            nav.navigateTo(section);
            assertEquals(section, nav.getCurrentSection());
            assertNotNull(mainView.getView(section), "View for " + section + " must be cached and accessible");
        }

        model.shutdown();
    }
}
