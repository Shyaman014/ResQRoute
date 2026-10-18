package com.resqroute.ui.view;

import com.resqroute.model.EmergencyType;
import com.resqroute.model.Hospital;
import com.resqroute.model.HospitalCapability;
import com.resqroute.model.HospitalStatus;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hospital management view tracking emergency medical facilities, live bed capacity utilization,
 * and supported critical trauma/cardiac capabilities.
 */
public class HospitalsView extends VBox {

    private final SimulationPresentationModel model;
    private final TableView<Hospital> table;

    public HospitalsView(SimulationPresentationModel model) {
        this.model = Objects.requireNonNull(model, "SimulationPresentationModel must not be null");

        setSpacing(16);
        setPadding(new Insets(24));
        getStyleClass().add("hospitals-view");
        VBox.setVgrow(this, Priority.ALWAYS);

        // 1. Header
        SectionHeader header = new SectionHeader(
                "Hospital Medical Network",
                "Regional healthcare facilities, real-time bed capacity reservations, and emergency care capabilities"
        );
        getChildren().add(header);

        // 2. TableView
        table = createTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(table);

        // Wire model
        model.addStateChangeListener(m -> refresh());
        refresh();
    }

    private TableView<Hospital> createTable() {
        TableView<Hospital> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new EmptyStateView("🏥", "No Hospitals Registered", "Initialize a city scenario to view regional healthcare network."));

        TableColumn<Hospital, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));
        idCol.setPrefWidth(85);

        TableColumn<Hospital, String> nameCol = new TableColumn<>("Hospital Facility");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        nameCol.setPrefWidth(180);

        TableColumn<Hospital, String> locCol = new TableColumn<>("Node Location");
        locCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLocationNodeId()));
        locCol.setPrefWidth(110);

        TableColumn<Hospital, Void> statusCol = new TableColumn<>("Status");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(StatusBadge.ofHospitalStatus(getTableRow().getItem().getStatus()));
                }
            }
        });
        statusCol.setPrefWidth(100);

        TableColumn<Hospital, Void> capCol = new TableColumn<>("Bed Capacity");
        capCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label label = new Label();
            private final VBox box = new VBox(2, label, bar);

            {
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.getStyleClass().add("capacity-bar");
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Hospital h = getTableRow().getItem();
                    int total = h.getTotalCapacity();
                    int occupied = h.getOccupiedCapacity();
                    int avail = h.getAvailableCapacity();
                    double fraction = (total > 0) ? (double) occupied / total : 0.0;
                    bar.setProgress(fraction);
                    label.setText(String.format("%d / %d Occupied (%d Free)", occupied, total, avail));
                    setGraphic(box);
                }
            }
        });
        capCol.setPrefWidth(180);

        TableColumn<Hospital, String> typeCol = new TableColumn<>("Supported Emergencies");
        typeCol.setCellValueFactory(d -> {
            Set<EmergencyType> types = d.getValue().getSupportedEmergencyTypes();
            String joined = types.stream().map(Enum::name).collect(Collectors.joining(", "));
            return new SimpleStringProperty(joined);
        });
        typeCol.setPrefWidth(200);

        TableColumn<Hospital, String> capMedCol = new TableColumn<>("Capabilities");
        capMedCol.setCellValueFactory(d -> {
            Set<HospitalCapability> caps = d.getValue().getCapabilities();
            String joined = caps.stream().map(Enum::name).collect(Collectors.joining(", "));
            return new SimpleStringProperty(joined);
        });
        capMedCol.setPrefWidth(200);

        t.getColumns().addAll(idCol, nameCol, locCol, statusCol, capCol, typeCol, capMedCol);
        return t;
    }

    public void refresh() {
        table.getItems().setAll(model.getHospitals());
    }
}
