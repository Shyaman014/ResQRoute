package com.resqroute.database;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuration settings for the SQLite database connection.
 */
public final class DatabaseConfig {

    private final String jdbcUrl;
    private final boolean inMemory;

    private DatabaseConfig(String jdbcUrl, boolean inMemory) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.inMemory = inMemory;
    }

    /**
     * Creates an in-memory SQLite configuration with an isolated database name.
     */
    public static DatabaseConfig inMemory() {
        String dbName = "mem_" + UUID.randomUUID().toString().replace("-", "");
        return new DatabaseConfig("jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared", true);
    }

    /**
     * Creates a file-based SQLite configuration pointing to the specified path.
     *
     * @param dbPath Path to the database file; must not be null.
     */
    public static DatabaseConfig ofFile(Path dbPath) {
        Objects.requireNonNull(dbPath, "dbPath must not be null");
        String normalized = dbPath.toAbsolutePath().toString().replace("\\", "/");
        return new DatabaseConfig("jdbc:sqlite:" + normalized, false);
    }

    /**
     * Creates a file-based SQLite configuration using standard default file {@code resqroute.db}.
     */
    public static DatabaseConfig defaultFile() {
        return ofFile(Path.of("resqroute.db"));
    }

    /**
     * Custom JDBC URL configuration.
     */
    public static DatabaseConfig custom(String jdbcUrl) {
        return new DatabaseConfig(jdbcUrl, jdbcUrl.contains(":memory:"));
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public boolean isInMemory() {
        return inMemory;
    }

    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "jdbcUrl='" + jdbcUrl + '\'' +
                ", inMemory=" + inMemory +
                '}';
    }
}
