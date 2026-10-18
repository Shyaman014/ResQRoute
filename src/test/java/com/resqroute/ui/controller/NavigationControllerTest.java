package com.resqroute.ui.controller;

import com.resqroute.ui.model.NavSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NavigationController Section State & Notification Tests")
class NavigationControllerTest {

    private NavigationController controller;

    @BeforeEach
    void setUp() {
        controller = new NavigationController();
    }

    @Test
    @DisplayName("Default initial section must be DASHBOARD")
    void defaultInitialSection() {
        assertEquals(NavSection.DASHBOARD, controller.getCurrentSection());
    }

    @Test
    @DisplayName("Custom initial section is honored")
    void customInitialSection() {
        NavigationController custom = new NavigationController(NavSection.SIMULATION);
        assertEquals(NavSection.SIMULATION, custom.getCurrentSection());
    }

    @Test
    @DisplayName("Null initial section throws NullPointerException")
    void nullInitialSectionThrows() {
        assertThrows(NullPointerException.class, () -> new NavigationController(null));
    }

    @Test
    @DisplayName("Navigating to new section updates state and notifies registered listeners")
    void navigateToUpdatesAndNotifies() {
        List<NavSection> events = new ArrayList<>();
        controller.addListener(events::add);

        controller.navigateTo(NavSection.EMERGENCIES);
        assertEquals(NavSection.EMERGENCIES, controller.getCurrentSection());

        controller.navigateTo(NavSection.AMBULANCES);
        assertEquals(NavSection.AMBULANCES, controller.getCurrentSection());

        assertEquals(List.of(NavSection.EMERGENCIES, NavSection.AMBULANCES), events);
    }

    @Test
    @DisplayName("Navigating to the already active section does not trigger redundant notifications")
    void navigateToSameSectionNoOp() {
        List<NavSection> events = new ArrayList<>();
        controller.addListener(events::add);

        controller.navigateTo(NavSection.DASHBOARD); // already at DASHBOARD
        assertTrue(events.isEmpty(), "No event should fire when section is unchanged");
    }

    @Test
    @DisplayName("Navigating to null section throws NullPointerException")
    void navigateToNullThrows() {
        assertThrows(NullPointerException.class, () -> controller.navigateTo(null));
    }

    @Test
    @DisplayName("Removed listener no longer receives notifications")
    void removeListener() {
        List<NavSection> events = new ArrayList<>();
        Consumer<NavSection> listener = events::add;

        controller.addListener(listener);
        controller.navigateTo(NavSection.MAP);
        assertEquals(1, events.size());

        controller.removeListener(listener);
        controller.navigateTo(NavSection.HOSPITALS);
        assertEquals(1, events.size(), "Removed listener must not receive further events");
    }
}
