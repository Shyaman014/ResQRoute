package com.resqroute.database.migration;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata record representing an applied or available database schema migration.
 */
public record SchemaMigration(
        int version,
        String description,
        String scriptResourcePath,
        Instant appliedAt
) {
    public SchemaMigration {
        if (version < 1) {
            throw new IllegalArgumentException("Migration version must be >= 1: " + version);
        }
        Objects.requireNonNull(description, "description must not be null");
    }

    public SchemaMigration(int version, String description, String scriptResourcePath) {
        this(version, description, scriptResourcePath, null);
    }
}
