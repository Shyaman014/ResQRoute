package com.resqroute.ui.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Encapsulates user-facing status, warning, error, or success feedback messages.
 */
public record UiMessage(MessageType type, String text, Instant timestamp) {

    public enum MessageType {
        INFO("info", "ℹ️"),
        SUCCESS("success", "✓"),
        WARNING("warning", "⚠️"),
        ERROR("error", "❌");

        private final String cssClass;
        private final String symbol;

        MessageType(String cssClass, String symbol) {
            this.cssClass = cssClass;
            this.symbol = symbol;
        }

        public String getCssClass() {
            return cssClass;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public UiMessage {
        Objects.requireNonNull(type, "MessageType must not be null");
        Objects.requireNonNull(text, "Message text must not be null");
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
    }

    public static UiMessage info(String text) {
        return new UiMessage(MessageType.INFO, text, Instant.now());
    }

    public static UiMessage success(String text) {
        return new UiMessage(MessageType.SUCCESS, text, Instant.now());
    }

    public static UiMessage warning(String text) {
        return new UiMessage(MessageType.WARNING, text, Instant.now());
    }

    public static UiMessage error(String text) {
        return new UiMessage(MessageType.ERROR, text, Instant.now());
    }
}
