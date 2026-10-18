package com.resqroute.ui.component;

import com.resqroute.ui.model.UiMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Dismissible alert banner for user feedback notifications (success, info, warning, error).
 */
public class NotificationBanner extends HBox {

    private final Label iconLabel;
    private final Label messageLabel;
    private final Button closeButton;

    public NotificationBanner() {
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 14, 10, 14));
        getStyleClass().add("notification-banner");
        setVisible(false);
        setManaged(false);

        iconLabel = new Label("ℹ️");
        iconLabel.getStyleClass().add("banner-icon");

        messageLabel = new Label();
        messageLabel.getStyleClass().add("banner-message");
        messageLabel.setWrapText(true);
        HBox.setHgrow(messageLabel, Priority.ALWAYS);

        closeButton = new Button("✕");
        closeButton.getStyleClass().add("banner-close-btn");
        closeButton.setOnAction(e -> dismiss());

        getChildren().addAll(iconLabel, messageLabel, closeButton);
    }

    public void showMessage(UiMessage message) {
        if (message == null) {
            dismiss();
            return;
        }

        getStyleClass().removeAll("banner-info", "banner-success", "banner-warning", "banner-error");
        getStyleClass().add("banner-" + message.type().getCssClass());

        iconLabel.setText(message.type().getSymbol());
        messageLabel.setText(message.text());

        setVisible(true);
        setManaged(true);
    }

    public void dismiss() {
        setVisible(false);
        setManaged(false);
    }
}
