package com.resqroute.ui.component;

import com.resqroute.model.Ambulance;
import com.resqroute.model.EmergencyCase;
import com.resqroute.model.Hospital;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Standardized, accessible dialog utilities for user confirmations, alerts,
 * and comprehensive domain entity inspection modal popups.
 */
public final class UiDialogs {

    private static final String CSS_PATH = "/com/resqroute/ui/css/resqroute.css";

    private UiDialogs() {
    }

    /**
     * Prompts the user with a standardized confirmation modal dialog before destructive actions.
     *
     * @param owner   Parent window/stage.
     * @param title   Dialog window title.
     * @param header  Primary confirmation prompt question.
     * @param content Detailed implications of the action.
     * @return true if the user explicitly clicked Confirm; false if cancelled or dismissed.
     */
    public static boolean confirm(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        initDialogStyle(alert, owner, title, header);

        alert.setContentText(content);

        ButtonType confirmButton = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        // Accessible labels
        DialogPane pane = alert.getDialogPane();
        pane.setAccessibleText(title + ": " + header);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == confirmButton;
    }

    /**
     * Displays an accessible informational notification dialog.
     */
    public static void info(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        initDialogStyle(alert, owner, title, header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Displays an accessible error notification dialog with actionable remediation guidance.
     */
    public static void error(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        initDialogStyle(alert, owner, title, header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Opens an accessible detail modal for inspecting an Emergency Case.
     */
    public static void showEmergencyCaseDetails(Window owner, EmergencyCase emergencyCase) {
        if (emergencyCase == null) return;

        Dialog<Void> dialog = new Dialog<>();
        initDialogStyle(dialog, owner, "Emergency Case Details — " + emergencyCase.getId(),
                "Incident ID: " + emergencyCase.getId());

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = createDetailsGrid();
        int row = 0;

        grid.add(createFieldLabel("Triage Type:"), 0, row);
        grid.add(new Label(emergencyCase.getType().name()), 1, row++);

        grid.add(createFieldLabel("Triage Severity:"), 0, row);
        grid.add(StatusBadge.ofSeverity(emergencyCase.getSeverity()), 1, row++);

        grid.add(createFieldLabel("Lifecycle Status:"), 0, row);
        grid.add(StatusBadge.ofEmergencyStatus(emergencyCase.getStatus()), 1, row++);

        grid.add(createFieldLabel("Pickup Node Location:"), 0, row);
        grid.add(new Label(emergencyCase.getPickupLocationNodeId()), 1, row++);

        grid.add(createFieldLabel("Reported Timestamp:"), 0, row);
        grid.add(new Label(emergencyCase.getCreatedAt().toString()), 1, row++);

        grid.add(createFieldLabel("Assigned Ambulance:"), 0, row);
        grid.add(new Label(emergencyCase.getAssignedAmbulanceId().orElse("None (Awaiting Dispatch)")), 1, row++);

        grid.add(createFieldLabel("Destination Hospital:"), 0, row);
        grid.add(new Label(emergencyCase.getDestinationHospitalId().orElse("None (Not Assigned Yet)")), 1, row++);

        grid.add(createFieldLabel("Incident Description:"), 0, row);
        Label descLabel = new Label(emergencyCase.getDescription());
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(380);
        grid.add(descLabel, 1, row);

        pane.setContent(grid);
        dialog.showAndWait();
    }

    /**
     * Opens an accessible detail modal for inspecting an Ambulance unit.
     */
    public static void showAmbulanceDetails(Window owner, Ambulance ambulance) {
        if (ambulance == null) return;

        Dialog<Void> dialog = new Dialog<>();
        initDialogStyle(dialog, owner, "Emergency Vehicle Details — " + ambulance.getId(),
                "Ambulance Unit: " + ambulance.getId());

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = createDetailsGrid();
        int row = 0;

        grid.add(createFieldLabel("Operational Status:"), 0, row);
        grid.add(StatusBadge.ofAmbulanceStatus(ambulance.getStatus()), 1, row++);

        grid.add(createFieldLabel("Current Node Location:"), 0, row);
        grid.add(new Label(ambulance.getCurrentLocationNodeId()), 1, row++);

        grid.add(createFieldLabel("Assigned Mission / Case:"), 0, row);
        grid.add(new Label(ambulance.getAssignedCaseId().orElse("Idle (Available for Dispatch)")), 1, row++);

        grid.add(createFieldLabel("Clinical Capabilities:"), 0, row);
        String caps = (ambulance.getCapabilities() == null || ambulance.getCapabilities().isEmpty())
                ? "Standard Vehicle"
                : ambulance.getCapabilities().stream().map(Enum::name).collect(Collectors.joining(", "));
        Label capsLabel = new Label(caps);
        capsLabel.setWrapText(true);
        capsLabel.setMaxWidth(380);
        grid.add(capsLabel, 1, row);

        pane.setContent(grid);
        dialog.showAndWait();
    }

    /**
     * Opens an accessible detail modal for inspecting a Hospital facility.
     */
    public static void showHospitalDetails(Window owner, Hospital hospital) {
        if (hospital == null) return;

        Dialog<Void> dialog = new Dialog<>();
        initDialogStyle(dialog, owner, "Hospital Facility — " + hospital.getName(),
                hospital.getName() + " (" + hospital.getId() + ")");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = createDetailsGrid();
        int row = 0;

        grid.add(createFieldLabel("Facility ID:"), 0, row);
        grid.add(new Label(hospital.getId()), 1, row++);

        grid.add(createFieldLabel("Location Node:"), 0, row);
        grid.add(new Label(hospital.getLocationNodeId()), 1, row++);

        grid.add(createFieldLabel("Facility Status:"), 0, row);
        grid.add(StatusBadge.ofHospitalStatus(hospital.getStatus()), 1, row++);

        grid.add(createFieldLabel("Bed Capacity:"), 0, row);
        grid.add(new Label(String.format("%d Total Beds | %d Occupied | %d Available",
                hospital.getTotalCapacity(), hospital.getOccupiedCapacity(), hospital.getAvailableCapacity())), 1, row++);

        grid.add(createFieldLabel("Supported Emergencies:"), 0, row);
        String types = hospital.getSupportedEmergencyTypes().stream().map(Enum::name).collect(Collectors.joining(", "));
        Label typesLabel = new Label(types);
        typesLabel.setWrapText(true);
        typesLabel.setMaxWidth(380);
        grid.add(typesLabel, 1, row++);

        grid.add(createFieldLabel("Medical Capabilities:"), 0, row);
        String caps = hospital.getCapabilities().stream().map(Enum::name).collect(Collectors.joining(", "));
        Label capsLabel = new Label(caps);
        capsLabel.setWrapText(true);
        capsLabel.setMaxWidth(380);
        grid.add(capsLabel, 1, row);

        pane.setContent(grid);
        dialog.showAndWait();
    }

    private static void initDialogStyle(Dialog<?> dialog, Window owner, String title, String header) {
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("custom-dialog-pane");

        URL css = UiDialogs.class.getResource(CSS_PATH);
        if (css != null) {
            dialogPane.getStylesheets().add(css.toExternalForm());
        }

        Stage stage = (Stage) dialogPane.getScene().getWindow();
        stage.setMinWidth(420);
    }

    private static GridPane createDetailsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 16, 20));
        grid.setAlignment(Pos.CENTER_LEFT);
        return grid;
    }

    private static Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("param-label");
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
