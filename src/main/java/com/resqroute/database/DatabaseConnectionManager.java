package com.resqroute.database;

import com.resqroute.database.migration.MigrationManager;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Manages JDBC connections, SQLite pragmas (foreign keys, WAL mode),
 * automated schema migrations, and transactional boundaries.
 */
public class DatabaseConnectionManager implements Closeable, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnectionManager.class.getName());

    private final DatabaseConfig config;
    private final MigrationManager migrationManager;

    // For in-memory databases, keep one persistent connection open so SQLite does not drop the database
    private Connection keepAliveConnection;
    private boolean initialized = false;

    public DatabaseConnectionManager(DatabaseConfig config) {
        this(config, new MigrationManager());
    }

    public DatabaseConnectionManager(DatabaseConfig config, MigrationManager migrationManager) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.migrationManager = Objects.requireNonNull(migrationManager, "migrationManager must not be null");
    }

    /**
     * Initializes the connection manager, establishing keep-alive for in-memory mode
     * and executing all pending schema migrations.
     */
    public synchronized void initialize() throws SQLException {
        if (initialized) {
            return;
        }

        if (config.isInMemory()) {
            this.keepAliveConnection = createConnection();
        }

        try (Connection conn = getConnection()) {
            migrationManager.migrate(conn);
        }

        this.initialized = true;
        LOGGER.info("DatabaseConnectionManager initialized with URL: " + config.getJdbcUrl());
    }

    /**
     * Opens and prepares a new JDBC connection with foreign key enforcement and timeout pragmas.
     *
     * @return Configured {@link Connection}; caller must close via try-with-resources.
     * @throws SQLException if connection cannot be opened.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = createConnection();
        configurePragmas(conn);
        return conn;
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(config.getJdbcUrl());
    }

    private void configurePragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Strictly enforce foreign keys in SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");
            // Set busy timeout so concurrent locks wait up to 5 seconds
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
    }

    /**
     * Executes a transactional block on a managed connection.
     * Automatically commits on success and rolls back on exception.
     *
     * @param action Database operation receiving the transactional connection.
     * @throws SQLException on database failure.
     */
    public void executeTransaction(TransactionAction action) throws SQLException {
        executeTransactionResult(conn -> {
            action.execute(conn);
            return null;
        });
    }

    /**
     * Executes a transactional block returning a result on a managed connection.
     * Automatically commits on success and rolls back on exception.
     *
     * @param action Database operation receiving transactional connection and returning a value.
     * @param <T>    Return type.
     * @return Result of the action.
     * @throws SQLException on database failure.
     */
    public <T> T executeTransactionResult(TransactionResultAction<T> action) throws SQLException {
        try (Connection conn = getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                T result = action.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                throw (ex instanceof SQLException sqlEx) ? sqlEx : new SQLException("Transaction aborted: " + ex.getMessage(), ex);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Performs a online SQLite backup into the specified destination file path.
     *
     * @param targetPath Destination file path; must not be null.
     */
    public void backupTo(Path targetPath) throws SQLException {
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        String normalized = targetPath.toAbsolutePath().toString().replace("\\", "/");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + normalized + "';");
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public MigrationManager getMigrationManager() {
        return migrationManager;
    }

    @Override
    public synchronized void close() {
        if (keepAliveConnection != null) {
            try {
                if (!keepAliveConnection.isClosed()) {
                    keepAliveConnection.close();
                }
            } catch (SQLException ignored) {
            }
            keepAliveConnection = null;
        }
        initialized = false;
    }

    @FunctionalInterface
    public interface TransactionAction {
        void execute(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionResultAction<T> {
        T execute(Connection conn) throws SQLException;
    }
}
