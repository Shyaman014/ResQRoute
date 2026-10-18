package com.resqroute.database.service;

import com.resqroute.database.DatabaseConnectionManager;
import com.resqroute.database.repository.*;
import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.model.CityScenario;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * High-level persistence coordinator providing transactional multi-entity operations,
 * asynchronous background execution for JavaFX thread safety, and unified repository access.
 */
public class ResQRouteDatabaseService implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(ResQRouteDatabaseService.class.getName());

    private final DatabaseConnectionManager connectionManager;
    private final RoadNetworkRepository roadRepository;
    private final AmbulanceRepository ambulanceRepository;
    private final EmergencyCaseRepository emergencyRepository;
    private final HospitalRepository hospitalRepository;
    private final RoadIncidentRepository incidentRepository;
    private final AmbulanceAssignmentRepository assignmentRepository;
    private final SimulationScenarioRepository scenarioRepository;

    private final ExecutorService asyncExecutor;

    public ResQRouteDatabaseService(DatabaseConnectionManager connectionManager) {
        this(
                connectionManager,
                new SqliteRoadNetworkRepository(),
                new SqliteAmbulanceRepository(),
                new SqliteEmergencyCaseRepository(),
                new SqliteHospitalRepository(),
                new SqliteRoadIncidentRepository(),
                new SqliteAmbulanceAssignmentRepository(),
                new SqliteSimulationScenarioRepository(),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    public ResQRouteDatabaseService(
            DatabaseConnectionManager connectionManager,
            RoadNetworkRepository roadRepository,
            AmbulanceRepository ambulanceRepository,
            EmergencyCaseRepository emergencyRepository,
            HospitalRepository hospitalRepository,
            RoadIncidentRepository incidentRepository,
            AmbulanceAssignmentRepository assignmentRepository,
            SimulationScenarioRepository scenarioRepository,
            ExecutorService asyncExecutor
    ) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.roadRepository = Objects.requireNonNull(roadRepository, "roadRepository must not be null");
        this.ambulanceRepository = Objects.requireNonNull(ambulanceRepository, "ambulanceRepository must not be null");
        this.emergencyRepository = Objects.requireNonNull(emergencyRepository, "emergencyRepository must not be null");
        this.hospitalRepository = Objects.requireNonNull(hospitalRepository, "hospitalRepository must not be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
        this.scenarioRepository = Objects.requireNonNull(scenarioRepository, "scenarioRepository must not be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor must not be null");
    }

    // ==========================================
    // Multi-Entity Transactional Operations
    // ==========================================

    /**
     * Persists a full scenario snapshot (nodes, edges, initial ambulances, and hospitals)
     * within a single atomic database transaction.
     */
    public void saveScenarioSnapshot(String scenarioId, CityScenario scenario) throws SQLException {
        connectionManager.executeTransaction(conn -> {
            scenarioRepository.saveScenario(scenarioId, scenario, conn);
            roadRepository.saveGraph(scenario.getRoadGraph(), conn);
            for (Ambulance amb : scenario.getInitialAmbulances()) {
                ambulanceRepository.save(amb, conn);
            }
            for (Hospital hosp : scenario.getInitialHospitals()) {
                hospitalRepository.save(hosp, conn);
            }
        });
    }

    /**
     * Atomically records ambulance dispatch to an emergency case:
     * updates emergency case, ambulance status, and creates an assignment audit record.
     */
    public void recordDispatchAssignment(String caseId, String ambulanceId, Instant assignedAt) throws SQLException {
        connectionManager.executeTransaction(conn -> {
            emergencyRepository.assignAmbulance(caseId, ambulanceId, conn);
            emergencyRepository.updateStatus(caseId, EmergencyStatus.AMBULANCE_EN_ROUTE, "Ambulance dispatched to scene", assignedAt, conn);

            ambulanceRepository.updateStatus(ambulanceId, AmbulanceStatus.EN_ROUTE, caseId, conn);
            assignmentRepository.recordAssignment(ambulanceId, caseId, assignedAt, conn);
        });
    }

    /**
     * Atomically reserves a hospital bed for an incoming patient:
     * decrements available hospital capacity, reserves slot, and updates case and ambulance states.
     */
    public boolean recordHospitalReservation(String hospitalId, String caseId, String ambulanceId, Instant reservedAt) throws SQLException {
        return connectionManager.executeTransactionResult(conn -> {
            boolean reserved = hospitalRepository.reserveCapacity(hospitalId, caseId, ambulanceId, reservedAt, conn);
            if (!reserved) {
                return false;
            }

            emergencyRepository.assignHospital(caseId, hospitalId, conn);
            emergencyRepository.updateStatus(caseId, EmergencyStatus.TRANSPORTING, "En route to destination hospital", reservedAt, conn);
            ambulanceRepository.updateStatus(ambulanceId, AmbulanceStatus.TRANSPORTING, caseId, conn);
            return true;
        });
    }

    /**
     * Atomically completes patient hospital delivery:
     * fulfills bed reservation, restores hospital available capacity, completes case,
     * completes ambulance assignment, and frees the ambulance back to available status.
     */
    public void recordHospitalDeliveryAndCompletion(String hospitalId, String caseId, String ambulanceId, Instant deliveredAt) throws SQLException {
        connectionManager.executeTransaction(conn -> {
            hospitalRepository.releaseCapacity(hospitalId, caseId, deliveredAt, conn);
            emergencyRepository.updateStatus(caseId, EmergencyStatus.COMPLETED, "Patient delivered and admitted", deliveredAt, conn);
            assignmentRepository.completeAssignment(ambulanceId, caseId, deliveredAt, conn);
            ambulanceRepository.updateStatus(ambulanceId, AmbulanceStatus.AVAILABLE, null, conn);
        });
    }

    /**
     * Atomically registers a road incident and updates graph edge availability if blocking.
     */
    public void recordRoadIncident(RoadIncident incident) throws SQLException {
        connectionManager.executeTransaction(conn -> {
            incidentRepository.save(incident, conn);
            if (incident.isBlocksRoad()) {
                roadRepository.updateRoadAvailability(incident.getSourceNodeId(), incident.getDestinationNodeId(), false, conn);
            }
        });
    }

    /**
     * Atomically resolves an incident and restores road availability.
     */
    public void resolveRoadIncident(String incidentId, String sourceId, String destId, Instant resolvedAt) throws SQLException {
        connectionManager.executeTransaction(conn -> {
            incidentRepository.resolve(incidentId, resolvedAt, conn);
            String src = sourceId;
            String dst = destId;
            if (src == null || src.isBlank() || dst == null || dst.isBlank()) {
                Optional<RoadIncident> inc = incidentRepository.findById(incidentId, conn);
                if (inc.isPresent()) {
                    src = inc.get().getSourceNodeId();
                    dst = inc.get().getDestinationNodeId();
                }
            }
            if (src != null && !src.isBlank() && dst != null && !dst.isBlank()) {
                roadRepository.updateRoadAvailability(src, dst, true, conn);
            }
        });
    }

    /**
     * Saves audit events for a simulation run in batch.
     */
    public void saveSimulationEvents(String scenarioId, List<SimulationEvent> events) throws SQLException {
        connectionManager.executeTransaction(conn -> {
            scenarioRepository.saveSimulationEvents(scenarioId, events, conn);
        });
    }

    // ==========================================
    // Query Methods
    // ==========================================

    public RoadGraph loadRoadGraph() throws SQLException {
        return connectionManager.executeTransactionResult(roadRepository::loadGraph);
    }

    public List<Ambulance> getAvailableAmbulances() throws SQLException {
        return connectionManager.executeTransactionResult(conn -> ambulanceRepository.findByStatus(AmbulanceStatus.AVAILABLE, conn));
    }

    public List<EmergencyCase> getActiveEmergencies() throws SQLException {
        return connectionManager.executeTransactionResult(emergencyRepository::findActive);
    }

    public List<Hospital> getEligibleHospitals(EmergencyType type, Set<HospitalCapability> caps) throws SQLException {
        return connectionManager.executeTransactionResult(conn -> hospitalRepository.findEligible(type, caps, conn));
    }

    public List<RoadIncident> getActiveIncidents() throws SQLException {
        return connectionManager.executeTransactionResult(incidentRepository::findActive);
    }

    public List<SimulationEvent> getSimulationAuditTrail(String scenarioId) throws SQLException {
        return connectionManager.executeTransactionResult(conn -> scenarioRepository.getSimulationEvents(scenarioId, conn));
    }

    // ==========================================
    // Asynchronous Execution Helpers (JavaFX UI Thread Safe)
    // ==========================================

    /**
     * Executes SQLite integrity check verifying file format and index integrity.
     *
     * @return true if database integrity check passed with "ok".
     * @throws SQLException on database failure.
     */
    public boolean validateDatabaseIntegrity() throws SQLException {
        return connectionManager.executeTransactionResult(conn -> {
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery("PRAGMA integrity_check;")) {
                if (rs.next()) {
                    String result = rs.getString(1);
                    return "ok".equalsIgnoreCase(result);
                }
            }
            return false;
        });
    }

    /**
     * Executes a database task asynchronously off the JavaFX Application Thread.
     */
    public <T> CompletableFuture<T> supplyAsync(DatabaseSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException ex) {
                LOGGER.severe("Asynchronous database operation failed: " + ex.getMessage());
                throw new com.resqroute.exception.PersistenceException("Database operation failed: " + ex.getMessage(), ex);
            }
        }, asyncExecutor);
    }

    /**
     * Executes a database runnable asynchronously off the JavaFX Application Thread.
     */
    public CompletableFuture<Void> runAsync(DatabaseRunnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (SQLException ex) {
                LOGGER.severe("Asynchronous database operation failed: " + ex.getMessage());
                throw new com.resqroute.exception.PersistenceException("Database operation failed: " + ex.getMessage(), ex);
            }
        }, asyncExecutor);
    }

    // ==========================================
    // Getters & Lifecycle
    // ==========================================

    public DatabaseConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public RoadNetworkRepository getRoadRepository() {
        return roadRepository;
    }

    public AmbulanceRepository getAmbulanceRepository() {
        return ambulanceRepository;
    }

    public EmergencyCaseRepository getEmergencyRepository() {
        return emergencyRepository;
    }

    public HospitalRepository getHospitalRepository() {
        return hospitalRepository;
    }

    public RoadIncidentRepository getIncidentRepository() {
        return incidentRepository;
    }

    public AmbulanceAssignmentRepository getAssignmentRepository() {
        return assignmentRepository;
    }

    public SimulationScenarioRepository getScenarioRepository() {
        return scenarioRepository;
    }

    @Override
    public void close() {
        asyncExecutor.shutdown();
        connectionManager.close();
    }

    @FunctionalInterface
    public interface DatabaseSupplier<T> {
        T get() throws SQLException;
    }

    @FunctionalInterface
    public interface DatabaseRunnable {
        void run() throws SQLException;
    }
}
