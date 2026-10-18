package com.resqroute.ui.view;

import com.resqroute.ui.controller.NavigationController;
import com.resqroute.ui.model.NavSection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Left navigation sidebar displaying application identity, section links, and version information.
 */
public class SidebarNav extends VBox {

    private final NavigationController navigationController;
    private final Map<NavSection, Button> navButtons = new EnumMap<>(NavSection.class);

    public SidebarNav(NavigationController navigationController) {
        this.navigationController = Objects.requireNonNull(navigationController, "NavigationController must not be null");

        setSpacing(8);
        setPadding(new Insets(18, 14, 18, 14));
        setPrefWidth(240);
        setMinWidth(220);
        setMaxWidth(260);
        getStyleClass().add("sidebar");

        // 1. Branding Header
        VBox brandBox = new VBox(2);
        brandBox.setPadding(new Insets(0, 0, 16, 6));

        HBox logoRow = new HBox(8);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        Label logoIcon = new Label("🚑");
        logoIcon.getStyleClass().add("brand-logo-icon");

        Label brandTitle = new Label("ResQRoute");
        brandTitle.getStyleClass().add("brand-title");

        logoRow.getChildren().addAll(logoIcon, brandTitle);

        Label brandSubtitle = new Label("Emergency Dispatch & Routing");
        brandSubtitle.getStyleClass().add("brand-subtitle");

        brandBox.getChildren().addAll(logoRow, brandSubtitle);
        getChildren().add(brandBox);

        // 2. Navigation Items
        VBox navItemsBox = new VBox(4);
        VBox.setVgrow(navItemsBox, Priority.ALWAYS);

        for (NavSection section : NavSection.values()) {
            Button btn = createNavButton(section);
            navButtons.put(section, btn);
            navItemsBox.getChildren().add(btn);
        }

        getChildren().add(navItemsBox);

        // 3. Footer
        VBox footerBox = new VBox(2);
        footerBox.setPadding(new Insets(12, 6, 0, 6));
        footerBox.getStyleClass().add("sidebar-footer");

        Label buildLabel = new Label("ResQRoute Core v1.0 • Phase 24");
        buildLabel.getStyleClass().add("footer-build");

        Label runtimeLabel = new Label("Java 21 • JavaFX Desktop");
        runtimeLabel.getStyleClass().add("footer-runtime");

        footerBox.getChildren().addAll(buildLabel, runtimeLabel);
        getChildren().add(footerBox);

        // Listen for navigation state updates
        navigationController.addListener(this::updateActiveButton);
        updateActiveButton(navigationController.getCurrentSection());
    }

    private Button createNavButton(NavSection section) {
        Button btn = new Button(section.getIcon() + "   " + section.getTitle());
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.getStyleClass().add("nav-button");

        btn.setAccessibleText("Navigate to " + section.getTitle());
        btn.setAccessibleHelp("Displays the " + section.getTitle() + " screen");
        btn.setTooltip(new Tooltip("Navigate to " + section.getTitle() + " (Alt+" + (section.ordinal() + 1) + ")"));

        btn.setOnAction(e -> navigationController.navigateTo(section));
        return btn;
    }

    private void updateActiveButton(NavSection activeSection) {
        for (Map.Entry<NavSection, Button> entry : navButtons.entrySet()) {
            if (entry.getKey() == activeSection) {
                if (!entry.getValue().getStyleClass().contains("nav-button-active")) {
                    entry.getValue().getStyleClass().add("nav-button-active");
                }
            } else {
                entry.getValue().getStyleClass().remove("nav-button-active");
            }
        }
    }
}
