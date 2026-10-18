package com.resqroute.ui.map;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.*;
import com.resqroute.simulation.model.AmbulanceMovementTelemetry;
import com.resqroute.simulation.model.VehicleVisualState;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.function.Consumer;

/**
 * High-performance 2D JavaFX Canvas rendering the city road network, real-time traffic congestion,
 * road closures, calculated emergency routes, vehicle telemetry, and medical facilities.
 */
public class RoadNetworkCanvas extends Canvas {

    private final MapCoordinateTransform transform = new MapCoordinateTransform();

    // Data sources
    private RoadGraph graph;
    private List<Ambulance> ambulances = Collections.emptyList();
    private List<Hospital> hospitals = Collections.emptyList();
    private List<EmergencyCase> activeEmergencies = Collections.emptyList();
    private List<AmbulanceMovementTelemetry> ambulanceTelemetry = Collections.emptyList();
    private List<RouteResult> activeMissionRoutes = Collections.emptyList();

    // Route visualization
    private RouteResult primaryRoute;
    private List<RouteResult> alternativeRoutes = Collections.emptyList();

    // Selection & Waypoints
    private Node selectedNode;
    private MapCoordinateTransform.EdgeMatch selectedEdge;
    private String selectedAmbulanceId;
    private String routeSourceNodeId;
    private String routeDestinationNodeId;

    // Callbacks
    private Consumer<Node> onNodeSelected;
    private Consumer<MapCoordinateTransform.EdgeMatch> onEdgeSelected;
    private Consumer<AmbulanceMovementTelemetry> onAmbulanceSelected;

    // Drag interaction tracking
    private double lastDragX;
    private double lastDragY;
    private boolean isDragging = false;

    public RoadNetworkCanvas() {
        // Redraw on canvas resize
        widthProperty().addListener((obs, oldW, newW) -> {
            transform.updateCanvasSize(newW.doubleValue(), getHeight());
            redraw();
        });
        heightProperty().addListener((obs, oldH, newH) -> {
            transform.updateCanvasSize(getWidth(), newH.doubleValue());
            redraw();
        });

        // Mouse Pan & Zoom Interaction
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY) {
                lastDragX = e.getX();
                lastDragY = e.getY();
                isDragging = false;
            }
        });

        setOnMouseDragged(e -> {
            double dx = e.getX() - lastDragX;
            double dy = e.getY() - lastDragY;
            if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
                isDragging = true;
            }
            transform.pan(dx, dy);
            lastDragX = e.getX();
            lastDragY = e.getY();
            redraw();
        });

        setOnScroll(e -> {
            double zoomFactor = (e.getDeltaY() > 0) ? 1.15 : 0.85;
            transform.zoomAt(zoomFactor, e.getX(), e.getY());
            redraw();
        });

        setOnMouseClicked(e -> {
            if (isDragging) {
                return; // Drag ended, not a click
            }

            if (e.getClickCount() == 2) {
                // Double click fits network to screen
                fitToNetwork();
                return;
            }

            if (graph == null) return;

            // 1. Check for ambulance vehicle click (hit radius = 26px)
            if (ambulanceTelemetry != null && !ambulanceTelemetry.isEmpty()) {
                for (AmbulanceMovementTelemetry ambTel : ambulanceTelemetry) {
                    MapCoordinateTransform.ScreenPoint pt = transform.toScreen(ambTel.currentCoordinates());
                    double dist = Math.hypot(e.getX() - pt.x(), e.getY() - pt.y());
                    if (dist <= 26.0) {
                        setSelectedAmbulanceId(ambTel.ambulanceId());
                        setSelectedNode(null);
                        setSelectedEdge(null);
                        if (onAmbulanceSelected != null) {
                            onAmbulanceSelected.accept(ambTel);
                        }
                        return;
                    }
                }
            }

            // 2. Check for node click (hit radius = 22px)
            Optional<Node> hitNode = transform.findNearestNode(graph.getAllNodes(), e.getX(), e.getY(), 22.0);
            if (hitNode.isPresent()) {
                setSelectedNode(hitNode.get());
                setSelectedEdge(null);
                setSelectedAmbulanceId(null);
                if (onNodeSelected != null) {
                    onNodeSelected.accept(hitNode.get());
                }
                if (onAmbulanceSelected != null) {
                    onAmbulanceSelected.accept(null);
                }
                return;
            }

            // 3. Check for edge click (hit distance = 14px)
            Optional<MapCoordinateTransform.EdgeMatch> hitEdge = transform.findNearestEdge(graph, e.getX(), e.getY(), 14.0);
            if (hitEdge.isPresent()) {
                setSelectedEdge(hitEdge.get());
                setSelectedNode(null);
                setSelectedAmbulanceId(null);
                if (onEdgeSelected != null) {
                    onEdgeSelected.accept(hitEdge.get());
                }
                if (onAmbulanceSelected != null) {
                    onAmbulanceSelected.accept(null);
                }
            } else {
                // Deselect
                setSelectedNode(null);
                setSelectedEdge(null);
                setSelectedAmbulanceId(null);
                if (onNodeSelected != null) onNodeSelected.accept(null);
                if (onEdgeSelected != null) onEdgeSelected.accept(null);
                if (onAmbulanceSelected != null) onAmbulanceSelected.accept(null);
            }
        });
    }

    public void setNetworkData(RoadGraph graph, List<Ambulance> ambulances, List<Hospital> hospitals, List<EmergencyCase> emergencies) {
        this.graph = graph;
        this.ambulances = (ambulances == null) ? Collections.emptyList() : List.copyOf(ambulances);
        this.hospitals = (hospitals == null) ? Collections.emptyList() : List.copyOf(hospitals);
        this.activeEmergencies = (emergencies == null) ? Collections.emptyList() : List.copyOf(emergencies);
        redraw();
    }

    public void setTelemetryData(List<AmbulanceMovementTelemetry> telemetry, List<RouteResult> missionRoutes) {
        this.ambulanceTelemetry = (telemetry == null) ? Collections.emptyList() : List.copyOf(telemetry);
        this.activeMissionRoutes = (missionRoutes == null) ? Collections.emptyList() : List.copyOf(missionRoutes);
        redraw();
    }

    public void setSelectedAmbulanceId(String ambulanceId) {
        this.selectedAmbulanceId = ambulanceId;
        redraw();
    }

    public void setOnAmbulanceSelected(Consumer<AmbulanceMovementTelemetry> onAmbulanceSelected) {
        this.onAmbulanceSelected = onAmbulanceSelected;
    }

    public void fitToNetwork() {
        if (graph != null && !graph.isEmpty()) {
            transform.fitToBounds(graph.getAllNodes());
            redraw();
        }
    }

    public void setPrimaryRoute(RouteResult route) {
        this.primaryRoute = route;
        redraw();
    }

    public void setAlternativeRoutes(List<RouteResult> alternatives) {
        this.alternativeRoutes = (alternatives == null) ? Collections.emptyList() : List.copyOf(alternatives);
        redraw();
    }

    public void clearRoutes() {
        this.primaryRoute = null;
        this.alternativeRoutes = Collections.emptyList();
        redraw();
    }

    public void setRouteWaypoints(String sourceNodeId, String destNodeId) {
        this.routeSourceNodeId = sourceNodeId;
        this.routeDestinationNodeId = destNodeId;
        redraw();
    }

    public void setSelectedNode(Node node) {
        this.selectedNode = node;
        redraw();
    }

    public void setSelectedEdge(MapCoordinateTransform.EdgeMatch edgeMatch) {
        this.selectedEdge = edgeMatch;
        redraw();
    }

    public void setOnNodeSelected(Consumer<Node> onNodeSelected) {
        this.onNodeSelected = onNodeSelected;
    }

    public void setOnEdgeSelected(Consumer<MapCoordinateTransform.EdgeMatch> onEdgeSelected) {
        this.onEdgeSelected = onEdgeSelected;
    }

    public MapCoordinateTransform getTransform() {
        return transform;
    }

    // ==========================================
    // Drawing Pipeline
    // ==========================================

    public void redraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = getGraphicsContext2D();

        // 1. Dark Blueprint Background
        gc.setFill(Color.web("#090d16"));
        gc.fillRect(0, 0, w, h);

        // Grid lines for spatial sense
        drawBackgroundGrid(gc, w, h);

        if (graph == null || graph.isEmpty()) {
            drawEmptyState(gc, w, h);
            return;
        }

        // 2. Draw Road Corridors (Edges)
        drawRoadEdges(gc);

        // 3. Draw Active Mission Routes (en-route, transporting, returning)
        drawActiveMissionRoutes(gc);

        // 4. Draw Alternative Routes
        drawAlternativeRoutes(gc);

        // 5. Draw Primary Route
        drawPrimaryRoute(gc);

        // 6. Draw Intersections (Nodes)
        drawIntersectionNodes(gc);

        // 7. Draw Facility & Emergency Markers
        drawHospitals(gc);
        drawActiveEmergencies(gc);
        drawAmbulanceFleet(gc);
    }

    private void drawBackgroundGrid(GraphicsContext gc, double w, double h) {
        gc.setStroke(Color.web("#141d2e"));
        gc.setLineWidth(1.0);
        double gridSize = 40.0;
        for (double x = 0; x < w; x += gridSize) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = 0; y < h; y += gridSize) {
            gc.strokeLine(0, y, w, y);
        }
    }

    private void drawEmptyState(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#64748b"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("No Road Network Scenario Loaded\nInitialize Metropolis to begin simulation", w / 2.0, h / 2.0);
    }

    private void drawRoadEdges(GraphicsContext gc) {
        for (Node source : graph.getAllNodes()) {
            MapCoordinateTransform.ScreenPoint ptA = transform.toScreen(source.getCoordinates());

            for (Edge edge : graph.getOutgoingEdges(source.getId())) {
                Node dest = edge.getDestination();
                MapCoordinateTransform.ScreenPoint ptB = transform.toScreen(dest.getCoordinates());

                boolean isClosed = !edge.isAvailable();
                boolean isSelected = (selectedEdge != null
                        && selectedEdge.sourceNodeId().equals(source.getId())
                        && selectedEdge.edge().getDestination().getId().equals(dest.getId()));

                if (isClosed) {
                    // Closed Road: Dashed Red Line
                    gc.setStroke(Color.web("#ef4444"));
                    gc.setLineWidth(isSelected ? 5.0 : 3.5);
                    gc.setLineDashes(8.0, 6.0);
                    gc.strokeLine(ptA.x(), ptA.y(), ptB.x(), ptB.y());
                    gc.setLineDashes(null);

                    // Draw Closure Warning Icon at midpoint
                    double midX = (ptA.x() + ptB.x()) / 2.0;
                    double midY = (ptA.y() + ptB.y()) / 2.0;
                    drawRoadClosureMarker(gc, midX, midY);
                } else {
                    // Open Road: Color based on dynamic congestion level
                    Color roadColor = resolveTrafficColor(edge.getTrafficCondition());
                    gc.setStroke(roadColor);
                    gc.setLineWidth(isSelected ? 4.5 : 2.5);
                    gc.strokeLine(ptA.x(), ptA.y(), ptB.x(), ptB.y());

                    // Draw subtle directional arrow
                    drawDirectionalArrow(gc, ptA.x(), ptA.y(), ptB.x(), ptB.y(), roadColor);
                }
            }
        }
    }

    private Color resolveTrafficColor(TrafficCondition condition) {
        if (condition == null) return Color.web("#475569");
        return switch (condition.level()) {
            case FREE_FLOW -> Color.web("#38bdf8");
            case LIGHT -> Color.web("#4ade80");
            case MODERATE -> Color.web("#f59e0b");
            case HEAVY -> Color.web("#ea580c");
            case SEVERE -> Color.web("#dc2626");
        };
    }

    private void drawRoadClosureMarker(GraphicsContext gc, double x, double y) {
        gc.setFill(Color.web("#ef4444"));
        gc.fillOval(x - 9, y - 9, 18, 18);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2.0);
        gc.strokeLine(x - 5, y, x + 5, y);
    }

    private void drawDirectionalArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 30.0) return;

        // Position arrow at 65% of the edge
        double arrowX = x1 + dx * 0.65;
        double arrowY = y1 + dy * 0.65;
        double angle = Math.atan2(dy, dx);

        double arrowLen = 7.0;
        double arrowAngle = Math.PI / 6.0;

        double ax1 = arrowX - arrowLen * Math.cos(angle - arrowAngle);
        double ay1 = arrowY - arrowLen * Math.sin(angle - arrowAngle);
        double ax2 = arrowX - arrowLen * Math.cos(angle + arrowAngle);
        double ay2 = arrowY - arrowLen * Math.sin(angle + arrowAngle);

        gc.setStroke(color.deriveColor(0, 1, 1, 0.7));
        gc.setLineWidth(1.5);
        gc.strokeLine(arrowX, arrowY, ax1, ay1);
        gc.strokeLine(arrowX, arrowY, ax2, ay2);
    }

    private void drawAlternativeRoutes(GraphicsContext gc) {
        if (alternativeRoutes == null || alternativeRoutes.isEmpty()) return;

        gc.setStroke(Color.web("#a855f7"));
        gc.setLineWidth(3.5);
        gc.setLineDashes(6.0, 4.0);

        for (RouteResult altRoute : alternativeRoutes) {
            if (!altRoute.isReachable()) continue;
            List<Node> waypoints = altRoute.getPath();
            for (int i = 0; i < waypoints.size() - 1; i++) {
                MapCoordinateTransform.ScreenPoint pt1 = transform.toScreen(waypoints.get(i).getCoordinates());
                MapCoordinateTransform.ScreenPoint pt2 = transform.toScreen(waypoints.get(i + 1).getCoordinates());
                gc.strokeLine(pt1.x(), pt1.y(), pt2.x(), pt2.y());
            }
        }
        gc.setLineDashes(null);
    }

    private void drawPrimaryRoute(GraphicsContext gc) {
        if (primaryRoute == null || !primaryRoute.isReachable()) return;

        List<Node> waypoints = primaryRoute.getPath();
        if (waypoints.size() < 2) return;

        // Glowing outer stroke
        gc.setStroke(Color.web("#06b6d4", 0.4));
        gc.setLineWidth(8.0);
        for (int i = 0; i < waypoints.size() - 1; i++) {
            MapCoordinateTransform.ScreenPoint pt1 = transform.toScreen(waypoints.get(i).getCoordinates());
            MapCoordinateTransform.ScreenPoint pt2 = transform.toScreen(waypoints.get(i + 1).getCoordinates());
            gc.strokeLine(pt1.x(), pt1.y(), pt2.x(), pt2.y());
        }

        // Crisp inner core
        gc.setStroke(Color.web("#22d3ee"));
        gc.setLineWidth(4.0);
        for (int i = 0; i < waypoints.size() - 1; i++) {
            MapCoordinateTransform.ScreenPoint pt1 = transform.toScreen(waypoints.get(i).getCoordinates());
            MapCoordinateTransform.ScreenPoint pt2 = transform.toScreen(waypoints.get(i + 1).getCoordinates());
            gc.strokeLine(pt1.x(), pt1.y(), pt2.x(), pt2.y());
        }
    }

    private void drawIntersectionNodes(GraphicsContext gc) {
        for (Node node : graph.getAllNodes()) {
            MapCoordinateTransform.ScreenPoint pt = transform.toScreen(node.getCoordinates());

            boolean isSelected = (selectedNode != null && selectedNode.getId().equals(node.getId()));
            boolean isRouteSource = (routeSourceNodeId != null && routeSourceNodeId.equals(node.getId()));
            boolean isRouteDest = (routeDestinationNodeId != null && routeDestinationNodeId.equals(node.getId()));

            // Outer highlight ring if selected or route endpoint
            if (isSelected) {
                gc.setStroke(Color.web("#f59e0b"));
                gc.setLineWidth(3.0);
                gc.strokeOval(pt.x() - 12, pt.y() - 12, 24, 24);
            }
            if (isRouteSource) {
                gc.setStroke(Color.web("#10b981"));
                gc.setLineWidth(3.0);
                gc.strokeOval(pt.x() - 14, pt.y() - 14, 28, 28);
            }
            if (isRouteDest) {
                gc.setStroke(Color.web("#f43f5e"));
                gc.setLineWidth(3.0);
                gc.strokeOval(pt.x() - 14, pt.y() - 14, 28, 28);
            }

            // Junction circle
            gc.setFill(Color.web("#1e293b"));
            gc.fillOval(pt.x() - 7, pt.y() - 7, 14, 14);

            gc.setStroke(Color.web("#64748b"));
            gc.setLineWidth(2.0);
            gc.strokeOval(pt.x() - 7, pt.y() - 7, 14, 14);

            // Node ID Label
            gc.setFill(Color.web("#cbd5e1"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.TOP);
            gc.fillText(node.getId(), pt.x(), pt.y() + 10);
        }
    }

    private void drawHospitals(GraphicsContext gc) {
        for (Hospital hosp : hospitals) {
            graph.getNode(hosp.getLocationNodeId()).ifPresent(node -> {
                MapCoordinateTransform.ScreenPoint pt = transform.toScreen(node.getCoordinates());

                // Hospital badge container
                double badgeX = pt.x() - 12;
                double badgeY = pt.y() - 28;

                Color statusColor = hosp.isOpen() ? Color.web("#10b981") : (hosp.isBusy() ? Color.web("#f59e0b") : Color.web("#ef4444"));

                gc.setFill(Color.web("#0f172a"));
                gc.fillRoundRect(badgeX - 4, badgeY - 4, 32, 22, 6, 6);

                gc.setStroke(statusColor);
                gc.setLineWidth(1.5);
                gc.strokeRoundRect(badgeX - 4, badgeY - 4, 32, 22, 6, 6);

                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.TOP);
                gc.fillText("🏥", badgeX, badgeY);
            });
        }
    }

    private void drawActiveEmergencies(GraphicsContext gc) {
        for (EmergencyCase emergency : activeEmergencies) {
            graph.getNode(emergency.getPickupLocationNodeId()).ifPresent(node -> {
                MapCoordinateTransform.ScreenPoint pt = transform.toScreen(node.getCoordinates());

                double badgeX = pt.x() + 8;
                double badgeY = pt.y() - 24;

                // Pulsing red alert beacon
                gc.setFill(Color.web("#ef4444", 0.3));
                gc.fillOval(pt.x() - 16, pt.y() - 16, 32, 32);

                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.TOP);
                gc.fillText("🚨", badgeX, badgeY);
            });
        }
    }

    private void drawActiveMissionRoutes(GraphicsContext gc) {
        if (activeMissionRoutes == null || activeMissionRoutes.isEmpty()) return;

        for (RouteResult missionRoute : activeMissionRoutes) {
            if (!missionRoute.isReachable()) continue;
            List<Node> waypoints = missionRoute.getPath();
            if (waypoints.size() < 2) continue;

            // Outer soft glow
            gc.setStroke(Color.web("#0ea5e9", 0.35));
            gc.setLineWidth(7.0);
            for (int i = 0; i < waypoints.size() - 1; i++) {
                MapCoordinateTransform.ScreenPoint pt1 = transform.toScreen(waypoints.get(i).getCoordinates());
                MapCoordinateTransform.ScreenPoint pt2 = transform.toScreen(waypoints.get(i + 1).getCoordinates());
                gc.strokeLine(pt1.x(), pt1.y(), pt2.x(), pt2.y());
            }

            // Inner dashed corridor indicating active transit
            gc.setStroke(Color.web("#38bdf8"));
            gc.setLineWidth(2.5);
            gc.setLineDashes(10.0, 6.0);
            for (int i = 0; i < waypoints.size() - 1; i++) {
                MapCoordinateTransform.ScreenPoint pt1 = transform.toScreen(waypoints.get(i).getCoordinates());
                MapCoordinateTransform.ScreenPoint pt2 = transform.toScreen(waypoints.get(i + 1).getCoordinates());
                gc.strokeLine(pt1.x(), pt1.y(), pt2.x(), pt2.y());
            }
            gc.setLineDashes(null);
        }
    }

    private void drawAmbulanceFleet(GraphicsContext gc) {
        if (ambulanceTelemetry != null && !ambulanceTelemetry.isEmpty()) {
            drawAnimatedAmbulanceTelemetry(gc);
            return;
        }

        drawStaticAmbulances(gc);
    }

    private void drawAnimatedAmbulanceTelemetry(GraphicsContext gc) {
        for (AmbulanceMovementTelemetry ambTel : ambulanceTelemetry) {
            MapCoordinateTransform.ScreenPoint pt = transform.toScreen(ambTel.currentCoordinates());

            boolean isSelected = (selectedAmbulanceId != null && selectedAmbulanceId.equals(ambTel.ambulanceId()));
            Color stateColor = resolveVehicleVisualColor(ambTel.visualState(), ambTel.isBlocked());

            // 1. Halo / Siren Beacon Pulse Effect
            if (ambTel.isMoving() || ambTel.visualState() == VehicleVisualState.AT_SCENE) {
                gc.setFill(stateColor.deriveColor(0, 1, 1, 0.22));
                gc.fillOval(pt.x() - 18, pt.y() - 18, 36, 36);
            }

            // 2. Selection Ring
            if (isSelected) {
                gc.setStroke(Color.web("#fbbf24"));
                gc.setLineWidth(3.0);
                gc.strokeOval(pt.x() - 22, pt.y() - 22, 44, 44);
            }

            // 3. Directional Heading Pointer (when in transit)
            if (ambTel.isMoving()) {
                double rad = Math.toRadians(ambTel.headingDegrees() - 90.0);
                double tipDist = 26.0;
                double tipX = pt.x() + tipDist * Math.cos(rad);
                double tipY = pt.y() + tipDist * Math.sin(rad);

                double backDist = 18.0;
                double wAngle = Math.PI / 7.0;
                double w1X = pt.x() + backDist * Math.cos(rad - wAngle);
                double w1Y = pt.y() + backDist * Math.sin(rad - wAngle);
                double w2X = pt.x() + backDist * Math.cos(rad + wAngle);
                double w2Y = pt.y() + backDist * Math.sin(rad + wAngle);

                gc.setFill(stateColor);
                gc.fillPolygon(new double[]{tipX, w1X, w2X}, new double[]{tipY, w1Y, w2Y}, 3);
            }

            // 4. Vehicle Capsule Body
            double badgeW = 54.0;
            double badgeH = 22.0;
            double badgeX = pt.x() - badgeW / 2.0;
            double badgeY = pt.y() - badgeH / 2.0;

            gc.setFill(Color.web("#090f1d"));
            gc.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

            gc.setStroke(stateColor);
            gc.setLineWidth(isSelected ? 2.5 : 1.8);
            gc.strokeRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

            // 5. Vehicle Label
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.setFill(Color.WHITE);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("🚑 " + ambTel.ambulanceId(), pt.x(), pt.y());

            // 6. Mini Route Progress Bar
            if (ambTel.isMoving()) {
                double barW = badgeW;
                double barH = 3.5;
                double barX = badgeX;
                double barY = badgeY + badgeH + 3.0;

                gc.setFill(Color.web("#1e293b"));
                gc.fillRoundRect(barX, barY, barW, barH, 2, 2);

                double fillW = Math.max(2.0, barW * ambTel.routeProgress());
                gc.setFill(stateColor);
                gc.fillRoundRect(barX, barY, fillW, barH, 2, 2);
            }

            // 7. Top Pill: ETA / Status Tag
            String tagText;
            if (ambTel.isBlocked()) {
                tagText = "⛔ BLOCKED";
            } else if (ambTel.isMoving()) {
                tagText = "ETA: " + ambTel.getFormattedEta();
            } else if (ambTel.visualState() == VehicleVisualState.AT_SCENE) {
                tagText = "AT SCENE";
            } else {
                tagText = ambTel.visualState().name();
            }

            double tagW = 62.0;
            double tagH = 14.0;
            double tagX = pt.x() - tagW / 2.0;
            double tagY = badgeY - tagH - 4.0;

            gc.setFill(Color.web("#030712", 0.90));
            gc.fillRoundRect(tagX, tagY, tagW, tagH, 6, 6);

            gc.setStroke(stateColor.deriveColor(0, 1, 1, 0.7));
            gc.setLineWidth(1.0);
            gc.strokeRoundRect(tagX, tagY, tagW, tagH, 6, 6);

            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8.5));
            gc.setFill(stateColor);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(tagText, pt.x(), tagY + tagH / 2.0);
        }
    }

    private Color resolveVehicleVisualColor(VehicleVisualState state, boolean isBlocked) {
        if (isBlocked) return Color.web("#ef4444");
        if (state == null) return Color.web("#38bdf8");
        return switch (state) {
            case AVAILABLE -> Color.web("#10b981");
            case RESPONDING -> Color.web("#00b4d8");
            case AT_SCENE -> Color.web("#a855f7");
            case TRANSPORTING -> Color.web("#f59e0b");
            case RETURNING -> Color.web("#06b6d4");
            case OUT_OF_SERVICE -> Color.web("#64748b");
        };
    }

    private void drawStaticAmbulances(GraphicsContext gc) {
        Map<String, Integer> unitsPerNode = new HashMap<>();

        for (Ambulance amb : ambulances) {
            graph.getNode(amb.getCurrentLocationNodeId()).ifPresent(node -> {
                MapCoordinateTransform.ScreenPoint pt = transform.toScreen(node.getCoordinates());

                int offsetIndex = unitsPerNode.getOrDefault(amb.getCurrentLocationNodeId(), 0);
                unitsPerNode.put(amb.getCurrentLocationNodeId(), offsetIndex + 1);

                double badgeX = pt.x() - 14 + (offsetIndex * 18);
                double badgeY = pt.y() - 34;

                Color ambColor = amb.isAvailable() ? Color.web("#10b981") : (amb.getStatus() == AmbulanceStatus.TRANSPORTING ? Color.web("#f59e0b") : Color.web("#38bdf8"));

                gc.setFill(Color.web("#0f172a"));
                gc.fillRoundRect(badgeX - 3, badgeY - 3, 26, 20, 6, 6);

                gc.setStroke(ambColor);
                gc.setLineWidth(1.5);
                gc.strokeRoundRect(badgeX - 3, badgeY - 3, 26, 20, 6, 6);

                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.TOP);
                gc.fillText("🚑", badgeX, badgeY);
            });
        }
    }
}
