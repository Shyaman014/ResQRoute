package com.resqroute.ui.controller;

import com.resqroute.ui.model.NavSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller managing navigation state across top-level application sections.
 */
public class NavigationController {

    private NavSection currentSection;
    private final List<Consumer<NavSection>> listeners = new ArrayList<>();

    public NavigationController() {
        this(NavSection.DASHBOARD);
    }

    public NavigationController(NavSection initialSection) {
        this.currentSection = Objects.requireNonNull(initialSection, "Initial section must not be null");
    }

    public NavSection getCurrentSection() {
        return currentSection;
    }

    public void navigateTo(NavSection section) {
        Objects.requireNonNull(section, "Target section must not be null");
        if (this.currentSection != section) {
            this.currentSection = section;
            notifyListeners(section);
        }
    }

    public void addListener(Consumer<NavSection> listener) {
        Objects.requireNonNull(listener, "Listener must not be null");
        listeners.add(listener);
    }

    public void removeListener(Consumer<NavSection> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(NavSection section) {
        for (Consumer<NavSection> listener : listeners) {
            listener.accept(section);
        }
    }
}
