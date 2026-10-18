package com.resqroute.ui.view;

import com.resqroute.model.EmergencyCase;
import com.resqroute.model.EmergencySeverity;
import com.resqroute.model.EmergencyStatus;
import com.resqroute.model.EmergencyType;
import com.resqroute.ui.component.EmptyStateView;
import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.component.StatusBadge;
import com.resqroute.ui.controller.SimulationPresentationModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Dedicated emergencies management view displaying active and historical emergency cases,
 * triage levels, assigned ambulances, and lifecycle state transitions.
 */
public class EmergenciesView extends VBox {

    private final SimulationPresentationModel model;
    private final TableView<EmergencyCase> table;
    private final TextField searchField;
    private Predicate<EmergencyCase> activeFilter = c -> true;

    // Filter Buttons
    private final ToggleGroup filterGroup = new ToggleGroup();

    public EmergenciesView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setSpacing(16);
        setPadding(new Insets(24));
        getStyleClass().add("emergencies-view");
        VBox.setVgrow(this, Priority.ALWAYS);

        // 1. Header
        SectionHeader header = new SectionHeader(
                "Emergency Incident Queue",
                "Real-time triage classification, spatial coordinates, and dispatch lifecycle"
        );
        getChildren().add(header);

        // 2. Filter & Search Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Search by Case ID, Node, or Type...");
        searchField.setPrefWidth(260);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, old, text) -> applyFilter());

        HBox filterButtons = new HBox(6);
        filterButtons.setAlignment(Pos.CENTER_LEFT);

        filterButtons.getChildren().addAll(
                createFilterButton("All Cases", c -> true, true),
                createFilterButton("Active Only", EmergencyCase::isActive, false),
                createFilterButton("Waiting Dispatch", c -> c.getStatus() == EmergencyStatus.WAITING_FOR_DISPATCH, false),
                createFilterButton("In Transit", c -> c.getStatus() == EmergencyStatus.AMBULANCE_EN_ROUTE || c.getStatus() == EmergencyStatus.TRANSPORTING, false),
                createFilterButton("Completed", EmergencyCase::isCompleted, false),
                createFilterButton("Cancelled", EmergencyCase::isCancelled, false)
        );

        HBox.setHgrow(filterButtons, Priority.ALWAYS);
        filterBar.getChildren().addAll(searchField, filterButtons);
        getChildren().add(filterBar);

        // 3. TableView
        table = createTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(table);

        // Wire model
        model.addStateChangeListener(m -> refresh());
        refresh();
    }

    private ToggleButton createFilterButton(String label, Predicate<EmergencyCase> predicate, boolean selected) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(filterGroup);
        btn.setSelected(selected);
        btn.getStyleClass().add("filter-toggle-btn");
        btn.setOnAction(e -> {
            if (btn.isSelected()) {
                this.activeFilter = predicate;
                applyFilter();
            } else {
                btn.setSelected(true);
            }
        });
        return btn;
    }

    private TableView<EmergencyCase> createTable() {
        TableView<EmergencyCase> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new EmptyStateView("🚨", "No Emergencies Found", "No emergency cases match the active filter or no scenario is loaded."));

        TableColumn<EmergencyCase, String> idCol = new TableColumn<>("Case ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));
        idCol.setPrefWidth(100);

        TableColumn<EmergencyCase, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType().name()));
        typeCol.setPrefWidth(110);

        TableColumn<EmergencyCase, Void> sevCol = new TableColumn<>("Severity");
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(StatusBadge.ofSeverity(getTableRow().getItem().getSeverity()));
                }
            }
        });
        sevCol.setPrefWidth(95);

        TableColumn<EmergencyCase, String> nodeCol = new TableColumn<>("Pickup Node");
        nodeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPickupLocationNodeId()));
        nodeCol.setPrefWidth(100);

        TableColumn<EmergencyCase, Void> statusCol = new TableColumn<>("Status");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(StatusBadge.ofEmergencyStatus(getTableRow().getItem().getStatus()));
                }
            }
        });
        statusCol.setPrefWidth(140);

        TableColumn<EmergencyCase, String> ambCol = new TableColumn<>("Assigned Ambulance");
        ambCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAssignedAmbulanceId().orElse("—")));
        ambCol.setPrefWidth(130);

        TableColumn<EmergencyCase, String> hospCol = new TableColumn<>("Destination Hospital");
        hospCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDestinationHospitalId().orElse("—")));
        hospCol.setPrefWidth(130);

        TableColumn<EmergencyCase, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        descCol.setPrefWidth(220);

        t.getColumns().addAll(idCol, typeCol, sevCol, nodeCol, statusCol, ambCol, hospCol, descCol);
        return t;
    }

    private void applyFilter() {
        String searchText = (searchField.getText() == null) ? "" : searchField.getText().trim().toLowerCase();
        List<EmergencyCase> allCases = model.getAllEmergencies();

        List<EmergencyCase> filtered = allCases.stream()
                .filter(activeFilter)
                .filter(c -> {
                    if (searchText.isEmpty()) return true;
                    return c.getId().toLowerCase().contains(searchText)
                            || c.getPickupLocationNodeId().toLowerCase().contains(searchText)
                            || c.getType().name().toLowerCase().contains(searchText)
                            || c.getDescription().toLowerCase().contains(searchText)
                            || c.getAssignedAmbulanceId().map(id -> id.toLowerCase().contains(searchText)).orElse(false);
                })
                .toList();

        table.getItems().setAll(filtered);
    }

    public void refresh() {
        applyFilter();
    }
}
