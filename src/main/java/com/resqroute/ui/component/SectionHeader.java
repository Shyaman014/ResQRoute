package com.resqroute.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Standardized section header with title, descriptive subtitle, and an optional action buttons toolbar.
 */
public class SectionHeader extends HBox {

    private final Label titleLabel;
    private final Label subtitleLabel;
    private final HBox actionsBox;

    public SectionHeader(String title, String subtitle) {
        setSpacing(16);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(0, 0, 16, 0));

        VBox titleContainer = new VBox(4);
        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-header-title");

        subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("section-header-subtitle");

        titleContainer.getChildren().addAll(titleLabel, subtitleLabel);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        actionsBox = new HBox(10);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(titleContainer, actionsBox);
    }

    public void addAction(Node actionNode) {
        actionsBox.getChildren().add(actionNode);
    }

    public void setActions(Node... actionNodes) {
        actionsBox.getChildren().setAll(actionNodes);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
    }
}
