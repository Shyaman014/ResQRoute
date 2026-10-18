package com.resqroute.logging;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structured application logger for ResQRoute.
 *
 * <p>Provides standardized logging formats across dispatch operations, database access,
 * and routing engines without leaking sensitive patient information or flooding logs
 * during real-time animation cycles.</p>
 */
public final class AppLogger {

    private static final Logger LOGGER = Logger.getLogger("com.resqroute");

    private AppLogger() {
    }

    public static void info(String message) {
        LOGGER.info(format("INFO", message));
    }

    public static void warn(String message) {
        LOGGER.warning(format("WARN", message));
    }

    public static void error(String message, Throwable cause) {
        LOGGER.log(Level.SEVERE, format("ERROR", message), cause);
    }

    public static void error(String message) {
        LOGGER.severe(format("ERROR", message));
    }

    public static void logLifecycle(String component, String action, String entityId, String details) {
        String msg = String.format("[%s] action=%s entity=%s details=%s",
                component, action, entityId != null ? entityId : "none", details);
        LOGGER.info(format("LIFECYCLE", msg));
    }

    public static void logDatabaseOperation(String operation, String targetTable, String outcome, long durationMs) {
        String msg = String.format("[DATABASE] op=%s table=%s outcome=%s duration=%dms",
                operation, targetTable, outcome, durationMs);
        LOGGER.fine(format("DB", msg));
    }

    private static String format(String category, String message) {
        return String.format("[%s] [%s] %s", Instant.now(), category, message);
    }
}
