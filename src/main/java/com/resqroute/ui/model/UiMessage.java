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

    /**
     * Sanitizes an exception into a safe, user-friendly UI error message.
     * Prevents database internals, SQL snippets, or raw stack traces from leaking to the UI.
     */
    public static UiMessage fromException(Throwable ex) {
        if (ex == null) {
            return error("An unknown error occurred.");
        }

        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause
                && !(cause instanceof com.resqroute.exception.ResQRouteException)) {
            cause = cause.getCause();
        }

        if (cause instanceof com.resqroute.exception.ResQRouteException domainEx) {
            return error(domainEx.getMessage());
        }

        if (cause instanceof java.sql.SQLException sqlEx) {
            String msg = sqlEx.getMessage();
            String upper = (msg != null) ? msg.toUpperCase() : "";
            if (upper.contains("FOREIGN KEY") || upper.contains("FOREIGNKEY")) {
                return error("Database operation failed: Referenced network entity does not exist.");
            }
            if (upper.contains("UNIQUE")) {
                return error("Database operation failed: A record with this identifier already exists.");
            }
            if (upper.contains("CHECK")) {
                return error("Database operation failed: Data values violated domain validity constraints.");
            }
            return error("Database operation failed. Please check inputs and retry.");
        }

        String msg = cause.getMessage();
        if (msg != null && !msg.isBlank() && !msg.contains("Exception") && !msg.contains("at ")) {
            return error(msg);
        }
        return error("An unexpected system error occurred. Please verify your inputs and try again.");
    }
}
