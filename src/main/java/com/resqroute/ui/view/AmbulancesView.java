package com.resqroute.ui.view;

import com.resqroute.model.Ambulance;
import com.resqroute.model.AmbulanceCapability;
import com.resqroute.model.AmbulanceStatus;
import com.resqroute.ui.component.EmptyStateView;
import com.resqroute.ui.component.SectionHeader;
import com.resqroute.ui.component.StatusBadge;
import com.resqroute.ui.component.UiDialogs;
import com.resqroute.ui.controller.SimulationPresentationModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Ambulance fleet management view detailing unit statuses, current locations, medical capabilities,
 * and active emergency mission assignments with search filtering and accessible detail modals.
 */
public class AmbulancesView extends VBox {

    private final SimulationPresentationModel model;
    private final TableView<Ambulance> table;
    private final TextField searchField;
    private final ToggleGroup filterGroup = new ToggleGroup();
    private Predicate<Ambulance> activeFilter = a -> true;

    public AmbulancesView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setSpacing(16);
        setPadding(new Insets(24));
        getStyleClass().add("ambulances-view");
        VBox.setVgrow(this, Priority.ALWAYS);

        // 1. Header
        SectionHeader header = new SectionHeader(
                "Emergency Vehicle Fleet",
                "Real-time vehicle readiness, spatial waypoint tracking, and medical equipment capabilities"
        );
        getChildren().add(header);

        // 2. Search & Filter Bar
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Search Unit ID, Location, or Capabilities...");
        searchField.setPrefWidth(280);
        searchField.getStyleClass().add("search-field");
        searchField.setAccessibleText("Filter ambulances by keyword");
        searchField.textProperty().addListener((obs, old, text) -> applyFilter());

        Button clearSearchBtn = new Button("✕");
        clearSearchBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        clearSearchBtn.setAccessibleText("Clear ambulance search field");
        clearSearchBtn.setTooltip(new Tooltip("Clear filter text"));
        clearSearchBtn.setOnAction(e -> searchField.clear());

        HBox filterButtons = new HBox(6);
        filterButtons.setAlignment(Pos.CENTER_LEFT);

        filterButtons.getChildren().addAll(
                createFilterButton("All Units", a -> true, true, "Display entire emergency response fleet"),
                createFilterButton("Available Only", Ambulance::isAvailable, false, "Show vehicles ready for immediate dispatch"),
                createFilterButton("Assigned", a -> a.getStatus() == AmbulanceStatus.ASSIGNED || a.getStatus() == AmbulanceStatus.EN_ROUTE, false, "Show units currently dispatched or en-route"),
                createFilterButton("Transporting", a -> a.getStatus() == AmbulanceStatus.TRANSPORTING, false, "Show units conveying patients to hospitals")
        );

        HBox.setHgrow(filterButtons, Priority.ALWAYS);
        filterBar.getChildren().addAll(searchField, clearSearchBtn, filterButtons);
        getChildren().add(filterBar);

        // 3. TableView
        table = createTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(table);

        // Wire model
        model.addStateChangeListener(m -> refresh());
        refresh();
    }

    private ToggleButton createFilterButton(String label, Predicate<Ambulance> predicate, boolean selected, String tooltip) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(filterGroup);
        btn.setSelected(selected);
        btn.getStyleClass().add("filter-toggle-btn");
        btn.setAccessibleText("Filter: " + label);
        btn.setTooltip(new Tooltip(tooltip));
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

    private TableView<Ambulance> createTable() {
        TableView<Ambulance> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new EmptyStateView("🚑", "No Ambulances Found", "No vehicles registered in fleet or matching active filter."));

        t.setRowFactory(tv -> {
            TableRow<Ambulance> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    UiDialogs.showAmbulanceDetails(getScene().getWindow(), row.getItem());
                }
            });
            return row;
        });

        TableColumn<Ambulance, String> idCol = new TableColumn<>("Unit ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));
        idCol.setPrefWidth(90);

        TableColumn<Ambulance, Void> statusCol = new TableColumn<>("Operational Status");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(StatusBadge.ofAmbulanceStatus(getTableRow().getItem().getStatus()));
                }
            }
        });
        statusCol.setPrefWidth(140);

        TableColumn<Ambulance, String> locCol = new TableColumn<>("Current Location");
        locCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCurrentLocationNodeId()));
        locCol.setPrefWidth(120);

        TableColumn<Ambulance, String> caseCol = new TableColumn<>("Assigned Incident");
        caseCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAssignedCaseId().orElse("None (Idle)")));
        caseCol.setPrefWidth(140);

        TableColumn<Ambulance, String> capCol = new TableColumn<>("Medical Capabilities");
        capCol.setCellValueFactory(d -> {
            Set<AmbulanceCapability> caps = d.getValue().getCapabilities();
            if (caps == null || caps.isEmpty()) {
                return new SimpleStringProperty("Standard");
            }
            String joined = caps.stream().map(Enum::name).collect(Collectors.joining(", "));
            return new SimpleStringProperty(joined);
        });
        capCol.setPrefWidth(260);

        t.getColumns().addAll(idCol, statusCol, locCol, caseCol, capCol);
        return t;
    }

    private void applyFilter() {
        String query = (searchField.getText() == null) ? "" : searchField.getText().trim().toLowerCase();
        List<Ambulance> all = model.getAmbulances();
        List<Ambulance> filtered = all.stream()
                .filter(activeFilter)
                .filter(a -> {
                    if (query.isEmpty()) return true;
                    return a.getId().toLowerCase().contains(query)
                            || a.getCurrentLocationNodeId().toLowerCase().contains(query)
                            || a.getStatus().name().toLowerCase().contains(query)
                            || (a.getCapabilities() != null && a.getCapabilities().stream().anyMatch(c -> c.name().toLowerCase().contains(query)));
                })
                .toList();
        table.getItems().setAll(filtered);
    }

    public void refresh() {
        applyFilter();
    }
}
