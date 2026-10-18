package com.resqroute.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Reusable summary metric card for displaying key performance indicators and fleet metrics.
 */
public class StatCard extends VBox {

    private final Label titleLabel;
    private final Label valueLabel;
    private final Label subtextLabel;
    private final Label iconLabel;

    public StatCard(String icon, String title, String initialValue, String subtext, String accentColorClass) {
        setSpacing(6);
        setPadding(new Insets(16, 18, 16, 18));
        getStyleClass().addAll("stat-card", accentColorClass);
        setAlignment(Pos.CENTER_LEFT);

        // Header with title and icon
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("stat-card-icon");

        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-card-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        header.getChildren().addAll(iconLabel, titleLabel);

        // Big value
        valueLabel = new Label(initialValue);
        valueLabel.getStyleClass().add("stat-card-value");

        // Subtext / description
        subtextLabel = new Label(subtext);
        subtextLabel.getStyleClass().add("stat-card-subtext");
        subtextLabel.setWrapText(true);

        getChildren().addAll(header, valueLabel, subtextLabel);
    }

    public void updateValue(String value) {
        valueLabel.setText(value);
    }

    public void updateSubtext(String subtext) {
        subtextLabel.setText(subtext);
    }

    public void update(String value, String subtext) {
        updateValue(value);
        updateSubtext(subtext);
    }

    public Label getValueLabel() {
        return valueLabel;
    }

    public Label getSubtextLabel() {
        return subtextLabel;
    }
}
