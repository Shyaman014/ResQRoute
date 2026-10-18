package com.resqroute.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Reusable container presenting a clean empty-state visual when data collections or tables have no items.
 */
public class EmptyStateView extends VBox {

    private final Label iconLabel;
    private final Label titleLabel;
    private final Label descriptionLabel;
    private final VBox actionContainer;

    public EmptyStateView(String icon, String title, String description) {
        setSpacing(12);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(40, 20, 40, 20));
        getStyleClass().add("empty-state-view");

        iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("empty-state-icon");

        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");

        descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("empty-state-desc");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(420);

        actionContainer = new VBox(8);
        actionContainer.setAlignment(Pos.CENTER);

        getChildren().addAll(iconLabel, titleLabel, descriptionLabel, actionContainer);
    }

    public void setAction(Node actionNode) {
        actionContainer.getChildren().setAll(actionNode);
    }
}
