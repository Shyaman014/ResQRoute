# ResQRoute — Phase 20: SQLite Database & JDBC Persistence Architecture

## 1. Overview
ResQRoute uses SQLite through the official `sqlite-jdbc` driver as its embedded local relational database. The persistence layer delivers ACID transactional guarantees, foreign-key constraint enforcement, normalized relational schemas, versioned migrations, and non-blocking asynchronous execution designed to keep the JavaFX UI Application Thread completely responsive.

---

## 2. Configuration & Connection Management

### DatabaseConnectionManager
- **Class**: `com.resqroute.database.DatabaseConnectionManager`
- **Supported Modes**:
  - **Local File-backed**: `DatabaseConfig.ofFile(Path)` or `DatabaseConfig.defaultFile()` (defaults to `resqroute.db`).
  - **In-Memory Isolated**: `DatabaseConfig.inMemory()` (`jdbc:sqlite:file:<uuid>?mode=memory&cache=shared`). Maintains an internal keep-alive connection ensuring the in-memory database persists across transient connection closures throughout test suites.
- **Connection Pragmas**:
  - `PRAGMA foreign_keys = ON;`: Strictly enforced on every connection checkout (SQLite disables foreign keys by default).
  - `PRAGMA busy_timeout = 5000;`: Prevents transient file locks from immediately failing, waiting up to 5,000 ms.
- **Transaction Boundaries**:
  - `executeTransaction(Consumer<Connection>)` and `executeTransactionResult(Function<Connection, T>)` configure `conn.setAutoCommit(false)`, commit upon successful completion, and automatically roll back on any `SQLException` or `RuntimeException`.

---

## 3. Versioned Schema Migrations

### MigrationManager
- **Package**: `com.resqroute.database.migration`
- **Mechanism**:
  - Discovers versioned SQL migration scripts located under `src/main/resources/db/migration/` (e.g. `V1__initial_schema.sql`).
  - Automatically provisions the `schema_version` tracking table:
    ```sql
    CREATE TABLE IF NOT EXISTS schema_version (
        version INTEGER PRIMARY KEY,
        description TEXT NOT NULL,
        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    ```
  - Inspects applied versions and executes unapplied migrations in strictly ascending numerical order inside an isolated transaction.
  - Fully idempotent: multiple executions on an up-to-date database perform zero unnecessary writes.

---

## 4. Normalized Relational Schema

### 4.1 Road Network
- **`road_nodes`**: Physical intersection locations and coordinates.
  - `id TEXT PRIMARY KEY`
  - `name TEXT NOT NULL`
  - `latitude REAL NOT NULL CHECK(latitude BETWEEN -90.0 AND 90.0)`
  - `longitude REAL NOT NULL CHECK(longitude BETWEEN -180.0 AND 180.0)`
- **`roads`**: Directed roadway edges.
  - `id INTEGER PRIMARY KEY AUTOINCREMENT`
  - `source_node_id TEXT NOT NULL REFERENCES road_nodes(id) ON DELETE CASCADE`
  - `dest_node_id TEXT NOT NULL REFERENCES road_nodes(id) ON DELETE CASCADE`
  - `distance_km REAL NOT NULL CHECK(distance_km >= 0.0)`
  - `base_travel_time_minutes REAL NOT NULL CHECK(base_travel_time_minutes >= 0.0)`
  - `is_available INTEGER NOT NULL DEFAULT 1 CHECK(is_available IN (0, 1))`
  - `traffic_tier TEXT NOT NULL DEFAULT 'FREE_FLOW'`
  - `traffic_multiplier REAL NOT NULL DEFAULT 1.0`
  - `UNIQUE(source_node_id, dest_node_id)`

### 4.2 Ambulance Fleet & Capabilities
- **`ambulances`**:
  - `id TEXT PRIMARY KEY`
  - `current_location_node_id TEXT NOT NULL REFERENCES road_nodes(id)`
  - `status TEXT NOT NULL CHECK(status IN ('AVAILABLE', 'ASSIGNED', 'EN_ROUTE', 'ON_SCENE', 'TRANSPORTING', 'RETURNING', 'OUT_OF_SERVICE'))`
  - `assigned_case_id TEXT`
- **`ambulance_capabilities`**:
  - `ambulance_id TEXT NOT NULL REFERENCES ambulances(id) ON DELETE CASCADE`
  - `capability TEXT NOT NULL`
  - `PRIMARY KEY(ambulance_id, capability)`

### 4.3 Emergency Cases & Status History
- **`emergency_cases`**:
  - `id TEXT PRIMARY KEY`
  - `case_type TEXT NOT NULL`
  - `severity TEXT NOT NULL CHECK(severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))`
  - `pickup_node_id TEXT NOT NULL REFERENCES road_nodes(id)`
  - `status TEXT NOT NULL`
  - `reported_at TIMESTAMP NOT NULL`
  - `assigned_ambulance_id TEXT REFERENCES ambulances(id) ON DELETE SET NULL`
  - `destination_hospital_id TEXT REFERENCES hospitals(id) ON DELETE SET NULL`
  - `resolved_at TIMESTAMP`
- **`emergency_status_history`**:
  - `id INTEGER PRIMARY KEY AUTOINCREMENT`
  - `case_id TEXT NOT NULL REFERENCES emergency_cases(id) ON DELETE CASCADE`
  - `status TEXT NOT NULL`
  - `reason TEXT`
  - `recorded_at TIMESTAMP NOT NULL`

### 4.4 Hospitals & Capacity Reservations
- **`hospitals`**:
  - `id TEXT PRIMARY KEY`
  - `name TEXT NOT NULL`
  - `location_node_id TEXT NOT NULL REFERENCES road_nodes(id)`
  - `total_capacity INTEGER NOT NULL CHECK(total_capacity >= 0)`
  - `available_capacity INTEGER NOT NULL CHECK(available_capacity >= 0 AND available_capacity <= total_capacity)`
  - `operational_status TEXT NOT NULL CHECK(operational_status IN ('OPEN', 'BUSY', 'CLOSED'))`
- **`hospital_capabilities`**: `(hospital_id, capability)` composite primary key.
- **`hospital_emergency_types`**: `(hospital_id, emergency_type)` composite primary key.
- **`hospital_reservations`**:
  - `id INTEGER PRIMARY KEY AUTOINCREMENT`
  - `hospital_id TEXT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE`
  - `case_id TEXT NOT NULL REFERENCES emergency_cases(id) ON DELETE CASCADE`
  - `ambulance_id TEXT REFERENCES ambulances(id) ON DELETE SET NULL`
  - `reserved_at TIMESTAMP NOT NULL`
  - `released_at TIMESTAMP`
  - `status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'FULFILLED', 'CANCELLED'))`
  - `UNIQUE(hospital_id, case_id)`

### 4.5 Road Incidents & Assignments
- **`road_incidents`**:
  - `id TEXT PRIMARY KEY`
  - `incident_type TEXT NOT NULL`
  - `source_node_id TEXT NOT NULL REFERENCES road_nodes(id)`
  - `dest_node_id TEXT NOT NULL REFERENCES road_nodes(id)`
  - `severity TEXT NOT NULL`
  - `blocks_road INTEGER NOT NULL DEFAULT 1 CHECK(blocks_road IN (0, 1))`
  - `created_at TIMESTAMP NOT NULL`
  - `is_resolved INTEGER NOT NULL DEFAULT 0 CHECK(is_resolved IN (0, 1))`
- **`ambulance_assignments`**:
  - `id INTEGER PRIMARY KEY AUTOINCREMENT`
  - `ambulance_id TEXT NOT NULL REFERENCES ambulances(id) ON DELETE CASCADE`
  - `case_id TEXT NOT NULL REFERENCES emergency_cases(id) ON DELETE CASCADE`
  - `assigned_at TIMESTAMP NOT NULL`
  - `status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))`

### 4.6 Simulation Scenarios & Audit Trail
- **`simulation_scenarios`**: `(id, name, duration_seconds, time_step_seconds, random_seed, start_time)`
- **`simulation_events`**: `(id, scenario_id, sequence_number, event_type, entity_id, description, sim_time_offset_seconds, real_timestamp)`

---

## 5. Multi-Entity Transactional Guarantees

The coordinator service (`ResQRouteDatabaseService`) wraps composite lifecycle transitions in single atomic database transactions:
1. **Dispatch Assignment**: Atomically updates `emergency_cases` status to `AMBULANCE_EN_ROUTE`, updates `ambulances` status to `EN_ROUTE`, and appends an `ambulance_assignments` record.
2. **Hospital Bed Reservation**: Decrements `hospitals.available_capacity` (switching to `BUSY` if zero), updates `emergency_cases.destination_hospital_id` and status to `TRANSPORTING`, updates `ambulances.status` to `TRANSPORTING`, and logs `hospital_reservations`.
3. **Hospital Delivery & Closure**: Marks reservation `FULFILLED`, restores hospital capacity (+1), completes case (`COMPLETED`), completes assignment, and frees ambulance back to `AVAILABLE`.
4. **Road Incident & Closure**: Atomically records the incident in `road_incidents` and updates `roads.is_available = 0`. Resolution restores `roads.is_available = 1` and marks incident resolved.

---

## 6. Backup & Restore Considerations

1. **Online Non-Blocking Hot Backup**:
   - `DatabaseConnectionManager.backupTo(Path destinationPath)` uses the native SQLite `VACUUM INTO 'backup.db';` statement.
   - Creates a consistent point-in-time snapshot of the active database without stopping application services or causing writer starvation.
2. **Offline File Copying**:
   - Safe whenever connections are closed (`DatabaseConnectionManager.close()`).
   - Standard file copy of `resqroute.db` preserves all tables and indexes.
3. **Recovery & Restart**:
   - Re-opening `DatabaseConnectionManager(DatabaseConfig.ofFile(path))` runs `initialize()`, automatically verifying schema version and recovering full topology, fleet, case history, and reservations without data loss.
