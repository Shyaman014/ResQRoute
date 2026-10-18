-- ResQRoute Database Schema
-- Version: 1 (Initial Schema)
-- Description: Core schema for road network, fleet, emergencies, hospitals, incidents, and simulation audit

-- 1. Schema Version Table
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Road Network Nodes
CREATE TABLE IF NOT EXISTS road_nodes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    latitude REAL NOT NULL CHECK(latitude >= -90.0 AND latitude <= 90.0),
    longitude REAL NOT NULL CHECK(longitude >= -180.0 AND longitude <= 180.0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Road Segments (Directed Edges)
CREATE TABLE IF NOT EXISTS roads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_node_id TEXT NOT NULL REFERENCES road_nodes(id) ON DELETE CASCADE,
    dest_node_id TEXT NOT NULL REFERENCES road_nodes(id) ON DELETE CASCADE,
    distance_km REAL NOT NULL CHECK(distance_km >= 0.0),
    base_travel_time_minutes REAL NOT NULL CHECK(base_travel_time_minutes >= 0.0),
    road_name TEXT NOT NULL DEFAULT 'Unnamed Road',
    is_available INTEGER NOT NULL DEFAULT 1 CHECK(is_available IN (0, 1)),
    traffic_tier TEXT NOT NULL DEFAULT 'FREE_FLOW',
    traffic_multiplier REAL NOT NULL DEFAULT 1.0 CHECK(traffic_multiplier > 0.0),
    UNIQUE(source_node_id, dest_node_id)
);

CREATE INDEX IF NOT EXISTS idx_roads_source ON roads(source_node_id);
CREATE INDEX IF NOT EXISTS idx_roads_dest ON roads(dest_node_id);

-- 4. Ambulances Fleet
CREATE TABLE IF NOT EXISTS ambulances (
    id TEXT PRIMARY KEY,
    current_location_node_id TEXT NOT NULL REFERENCES road_nodes(id),
    status TEXT NOT NULL CHECK(status IN ('AVAILABLE', 'ASSIGNED', 'EN_ROUTE', 'ON_SCENE', 'TRANSPORTING', 'RETURNING', 'OUT_OF_SERVICE')),
    assigned_case_id TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ambulances_status ON ambulances(status);

-- 5. Ambulance Capabilities (Normalized)
CREATE TABLE IF NOT EXISTS ambulance_capabilities (
    ambulance_id TEXT NOT NULL REFERENCES ambulances(id) ON DELETE CASCADE,
    capability TEXT NOT NULL,
    PRIMARY KEY(ambulance_id, capability)
);

-- 6. Hospitals
CREATE TABLE IF NOT EXISTS hospitals (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    location_node_id TEXT NOT NULL REFERENCES road_nodes(id),
    total_capacity INTEGER NOT NULL CHECK(total_capacity >= 0),
    available_capacity INTEGER NOT NULL CHECK(available_capacity >= 0 AND available_capacity <= total_capacity),
    operational_status TEXT NOT NULL CHECK(operational_status IN ('OPEN', 'BUSY', 'CLOSED')),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hospitals_status ON hospitals(operational_status);

-- 7. Hospital Capabilities (Normalized)
CREATE TABLE IF NOT EXISTS hospital_capabilities (
    hospital_id TEXT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    capability TEXT NOT NULL,
    PRIMARY KEY(hospital_id, capability)
);

-- 8. Hospital Supported Emergency Types (Normalized)
CREATE TABLE IF NOT EXISTS hospital_emergency_types (
    hospital_id TEXT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    emergency_type TEXT NOT NULL,
    PRIMARY KEY(hospital_id, emergency_type)
);

-- 9. Emergency Cases
CREATE TABLE IF NOT EXISTS emergency_cases (
    id TEXT PRIMARY KEY,
    case_type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK(severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    pickup_node_id TEXT NOT NULL REFERENCES road_nodes(id),
    description TEXT,
    status TEXT NOT NULL,
    reported_at TIMESTAMP NOT NULL,
    assigned_ambulance_id TEXT REFERENCES ambulances(id) ON DELETE SET NULL,
    destination_hospital_id TEXT REFERENCES hospitals(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emergency_status ON emergency_cases(status);

-- 10. Emergency Status History (Audit Trail)
CREATE TABLE IF NOT EXISTS emergency_status_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL REFERENCES emergency_cases(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    reason TEXT,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_status_history_case ON emergency_status_history(case_id);

-- 11. Hospital Capacity Reservations
CREATE TABLE IF NOT EXISTS hospital_reservations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    hospital_id TEXT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    case_id TEXT NOT NULL REFERENCES emergency_cases(id) ON DELETE CASCADE,
    ambulance_id TEXT REFERENCES ambulances(id) ON DELETE SET NULL,
    reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'FULFILLED', 'CANCELLED')),
    UNIQUE(hospital_id, case_id)
);

CREATE INDEX IF NOT EXISTS idx_reservations_hospital ON hospital_reservations(status, hospital_id);

-- 12. Ambulance Mission Assignments
CREATE TABLE IF NOT EXISTS ambulance_assignments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ambulance_id TEXT NOT NULL REFERENCES ambulances(id) ON DELETE CASCADE,
    case_id TEXT NOT NULL REFERENCES emergency_cases(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_assignments_amb ON ambulance_assignments(status, ambulance_id);

-- 13. Road Incidents
CREATE TABLE IF NOT EXISTS road_incidents (
    id TEXT PRIMARY KEY,
    incident_type TEXT NOT NULL,
    source_node_id TEXT NOT NULL REFERENCES road_nodes(id),
    dest_node_id TEXT NOT NULL REFERENCES road_nodes(id),
    severity TEXT NOT NULL,
    description TEXT,
    blocks_road INTEGER NOT NULL DEFAULT 1 CHECK(blocks_road IN (0, 1)),
    created_at TIMESTAMP NOT NULL,
    expiry_time TIMESTAMP,
    resolved_at TIMESTAMP,
    is_resolved INTEGER NOT NULL DEFAULT 0 CHECK(is_resolved IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_incidents_active ON road_incidents(is_resolved, source_node_id, dest_node_id);

-- 14. Simulation Scenarios
CREATE TABLE IF NOT EXISTS simulation_scenarios (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    time_step_seconds INTEGER NOT NULL,
    random_seed INTEGER NOT NULL,
    start_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 15. Simulation Event Audit Log
CREATE TABLE IF NOT EXISTS simulation_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scenario_id TEXT NOT NULL REFERENCES simulation_scenarios(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    description TEXT NOT NULL,
    sim_time_offset_seconds INTEGER NOT NULL,
    real_timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sim_events_scenario ON simulation_events(scenario_id, sequence_number);
