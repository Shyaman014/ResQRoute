package com.resqroute.database.migration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Executes versioned schema migrations against the SQLite database.
 */
public class MigrationManager {

    private static final Logger LOGGER = Logger.getLogger(MigrationManager.class.getName());

    private final List<SchemaMigration> registeredMigrations = new ArrayList<>();

    public MigrationManager() {
        registerDefaultMigrations();
    }

    private void registerDefaultMigrations() {
        registeredMigrations.add(new SchemaMigration(1, "Initial Schema", "/db/migration/V1__initial_schema.sql"));
    }

    /**
     * Registers an additional migration version.
     */
    public void registerMigration(SchemaMigration migration) {
        Objects.requireNonNull(migration, "migration must not be null");
        registeredMigrations.removeIf(m -> m.version() == migration.version());
        registeredMigrations.add(migration);
        registeredMigrations.sort(Comparator.comparingInt(SchemaMigration::version));
    }

    /**
     * Executes all pending schema migrations on the provided connection.
     *
     * @param connection Open JDBC connection; must not be null.
     * @return Number of newly applied migrations.
     * @throws SQLException if a migration fails.
     */
    public int migrate(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection must not be null");

        ensureVersionTable(connection);
        Set<Integer> appliedVersions = getAppliedVersions(connection);

        int newlyApplied = 0;
        for (SchemaMigration migration : registeredMigrations) {
            if (!appliedVersions.contains(migration.version())) {
                applyMigration(connection, migration);
                newlyApplied++;
            }
        }

        return newlyApplied;
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        String ddl = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(ddl);
        }
    }

    /**
     * Returns the set of migration versions already applied to the database.
     */
    public Set<Integer> getAppliedVersions(Connection connection) throws SQLException {
        ensureVersionTable(connection);
        Set<Integer> versions = new TreeSet<>();
        String sql = "SELECT version FROM schema_version ORDER BY version ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
        }
        return versions;
    }

    /**
     * Retrieves the latest applied schema version, or 0 if none.
     */
    public int getCurrentVersion(Connection connection) throws SQLException {
        ensureVersionTable(connection);
        String sql = "SELECT MAX(version) AS max_v FROM schema_version";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("max_v");
            }
        }
        return 0;
    }

    private void applyMigration(Connection connection, SchemaMigration migration) throws SQLException {
        LOGGER.info(String.format("Applying database migration V%d: %s", migration.version(), migration.description()));

        String scriptContent = loadScript(migration.scriptResourcePath());
        List<String> statements = parseStatements(scriptContent);

        boolean initialAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            try (Statement stmt = connection.createStatement()) {
                for (String sql : statements) {
                    if (!sql.isBlank()) {
                        stmt.execute(sql);
                    }
                }
            }

            // Record into schema_version
            String recordSql = "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(recordSql)) {
                ps.setInt(1, migration.version());
                ps.setString(2, migration.description());
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
            }

            connection.commit();
            LOGGER.info(String.format("Successfully applied migration V%d: %s", migration.version(), migration.description()));
        } catch (SQLException ex) {
            connection.rollback();
            LOGGER.severe(String.format("Migration V%d failed: %s. Rolled back.", migration.version(), ex.getMessage()));
            throw ex;
        } finally {
            connection.setAutoCommit(initialAutoCommit);
        }
    }

    private String loadScript(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Migration resource not found on classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load migration script: " + path, ex);
        }
    }

    static List<String> parseStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : script.split("\\r?\\n")) {
            String trimmed = rawLine.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue; // Skip comments and blank lines
            }

            current.append(rawLine).append("\n");
            if (trimmed.endsWith(";")) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
    }
}
