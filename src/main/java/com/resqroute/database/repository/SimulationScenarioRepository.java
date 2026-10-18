package com.resqroute.database.repository;

import com.resqroute.simulation.event.SimulationEvent;
import com.resqroute.simulation.model.CityScenario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Repository interface for simulation scenario configurations and event audit persistence.
 */
public interface SimulationScenarioRepository {

    void saveScenario(String scenarioId, CityScenario scenario, Connection conn) throws SQLException;

    void saveSimulationEvent(String scenarioId, SimulationEvent event, Connection conn) throws SQLException;

    void saveSimulationEvents(String scenarioId, List<SimulationEvent> events, Connection conn) throws SQLException;

    List<SimulationEvent> getSimulationEvents(String scenarioId, Connection conn) throws SQLException;
}
