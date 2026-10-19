package com.resqroute.ui.map;

import com.resqroute.graph.RoadGraph;
import com.resqroute.model.Edge;
import com.resqroute.model.GeoCoordinate;
import com.resqroute.model.Node;

import java.util.Collection;
import java.util.Optional;

/**
 * Coordinate projection and viewport transformation engine for the interactive city map.
 *
 * <p>Converts WGS-84 geographic coordinates (latitude, longitude) into 2D screen coordinates (x, y)
 * and vice-versa, managing pan offset, zoom scaling factor, and boundary auto-fitting.</p>
 */
public class MapCoordinateTransform {

    private double minLat = 12.90;
    private double maxLat = 13.00;
    private double minLon = 77.50;
    private double maxLon = 77.70;

    private double canvasWidth = 800.0;
    private double canvasHeight = 600.0;
    private double padding = 60.0;

    private double zoom = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;

    public record ScreenPoint(double x, double y) {}

    public MapCoordinateTransform() {
    }

    public void updateCanvasSize(double width, double height) {
        if (width > 0 && height > 0) {
            this.canvasWidth = width;
            this.canvasHeight = height;
        }
    }

    /**
     * Auto-fits the viewport transform to encompass all provided nodes.
     */
    public void fitToBounds(Collection<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        double minLatVal = Double.MAX_VALUE;
        double maxLatVal = -Double.MAX_VALUE;
        double minLonVal = Double.MAX_VALUE;
        double maxLonVal = -Double.MAX_VALUE;

        for (Node node : nodes) {
            GeoCoordinate c = node.getCoordinates();
            minLatVal = Math.min(minLatVal, c.latitude());
            maxLatVal = Math.max(maxLatVal, c.latitude());
            minLonVal = Math.min(minLonVal, c.longitude());
            maxLonVal = Math.max(maxLonVal, c.longitude());
        }

        // Add 10% safety margin if bounds are too tight
        double latSpan = Math.max(0.005, maxLatVal - minLatVal);
        double lonSpan = Math.max(0.005, maxLonVal - minLonVal);

        this.minLat = minLatVal - latSpan * 0.1;
        this.maxLat = maxLatVal + latSpan * 0.1;
        this.minLon = minLonVal - lonSpan * 0.1;
        this.maxLon = maxLonVal + lonSpan * 0.1;

        this.zoom = 1.0;
        this.panX = 0.0;
        this.panY = 0.0;
    }

    /**
     * Projects geographic coordinate to screen coordinate (x, y).
     * In screen coordinates, y increases downward, so higher latitude appears at smaller y.
     */
    public ScreenPoint toScreen(GeoCoordinate coord) {
        if (coord == null) return new ScreenPoint(0, 0);
        return toScreen(coord.latitude(), coord.longitude());
    }

    public ScreenPoint toScreen(double latitude, double longitude) {
        double lonSpan = Math.max(0.0001, maxLon - minLon);
        double latSpan = Math.max(0.0001, maxLat - minLat);

        double usableWidth = Math.max(10.0, canvasWidth - 2 * padding);
        double usableHeight = Math.max(10.0, canvasHeight - 2 * padding);

        // Normalized fractions [0, 1]
        double normX = (longitude - minLon) / lonSpan;
        double normY = (maxLat - latitude) / latSpan; // Invert latitude for screen Y

        // Base screen position
        double baseX = padding + normX * usableWidth;
        double baseY = padding + normY * usableHeight;

        // Apply zoom and pan relative to screen center
        double centerX = canvasWidth / 2.0;
        double centerY = canvasHeight / 2.0;

        double screenX = centerX + (baseX - centerX) * zoom + panX;
        double screenY = centerY + (baseY - centerY) * zoom + panY;

        return new ScreenPoint(screenX, screenY);
    }

    /**
     * Converts screen coordinate (x, y) back to geographic coordinate (latitude, longitude).
     */
    public GeoCoordinate toGeo(double screenX, double screenY) {
        double lonSpan = Math.max(0.0001, maxLon - minLon);
        double latSpan = Math.max(0.0001, maxLat - minLat);

        double usableWidth = Math.max(10.0, canvasWidth - 2 * padding);
        double usableHeight = Math.max(10.0, canvasHeight - 2 * padding);

        double centerX = canvasWidth / 2.0;
        double centerY = canvasHeight / 2.0;

        double baseX = ((screenX - panX - centerX) / zoom) + centerX;
        double baseY = ((screenY - panY - centerY) / zoom) + centerY;

        double normX = (baseX - padding) / usableWidth;
        double normY = (baseY - padding) / usableHeight;

        double longitude = minLon + normX * lonSpan;
        double latitude = maxLat - normY * latSpan;

        return new GeoCoordinate(
                Math.clamp(latitude, -90.0, 90.0),
                Math.clamp(longitude, -180.0, 180.0)
        );
    }

    public void pan(double deltaX, double deltaY) {
        this.panX += deltaX;
        this.panY += deltaY;
    }

    public void zoomAt(double factor, double pivotX, double pivotY) {
        double oldZoom = this.zoom;
        double newZoom = Math.clamp(this.zoom * factor, 0.25, 8.0);
        if (Math.abs(newZoom - oldZoom) < 0.0001) {
            return;
        }

        double centerX = canvasWidth / 2.0;
        double centerY = canvasHeight / 2.0;
        double scaleChange = newZoom / oldZoom;

        // Invariant: screen point at (pivotX, pivotY) remains invariant after zoom
        this.panX = (pivotX - centerX) * (1.0 - scaleChange) + this.panX * scaleChange;
        this.panY = (pivotY - centerY) * (1.0 - scaleChange) + this.panY * scaleChange;
        this.zoom = newZoom;
    }

    public void resetView() {
        this.zoom = 1.0;
        this.panX = 0.0;
        this.panY = 0.0;
    }

    /**
     * Centers the viewport at the specified geographic coordinate.
     */
    public void centerAt(GeoCoordinate coord) {
        if (coord == null) return;
        centerAt(coord.latitude(), coord.longitude());
    }

    /**
     * Centers the viewport at the specified latitude and longitude.
     */
    public void centerAt(double latitude, double longitude) {
        ScreenPoint current = toScreen(latitude, longitude);
        double targetX = canvasWidth / 2.0;
        double targetY = canvasHeight / 2.0;
        this.panX += (targetX - current.x());
        this.panY += (targetY - current.y());
    }

    /**
     * Finds the nearest node within a screen pixel radius.
     */
    public Optional<Node> findNearestNode(Collection<Node> nodes, double screenX, double screenY, double hitRadiusPx) {
        if (nodes == null) return Optional.empty();

        Node closestNode = null;
        double closestDistSq = hitRadiusPx * hitRadiusPx;

        for (Node n : nodes) {
            ScreenPoint pt = toScreen(n.getCoordinates());
            double dx = pt.x() - screenX;
            double dy = pt.y() - screenY;
            double distSq = dx * dx + dy * dy;
            if (distSq <= closestDistSq) {
                closestDistSq = distSq;
                closestNode = n;
            }
        }

        return Optional.ofNullable(closestNode);
    }

    /**
     * Finds the nearest road edge within a screen pixel threshold.
     */
    public Optional<EdgeMatch> findNearestEdge(RoadGraph graph, double screenX, double screenY, double hitDistancePx) {
        if (graph == null) return Optional.empty();

        EdgeMatch closest = null;
        double closestDist = hitDistancePx;

        for (Node source : graph.getAllNodes()) {
            ScreenPoint ptA = toScreen(source.getCoordinates());
            for (Edge edge : graph.getOutgoingEdges(source.getId())) {
                Node dest = edge.getDestination();
                ScreenPoint ptB = toScreen(dest.getCoordinates());

                double dist = distanceToSegment(screenX, screenY, ptA.x(), ptA.y(), ptB.x(), ptB.y());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = new EdgeMatch(source.getId(), edge);
                }
            }
        }

        return Optional.ofNullable(closest);
    }

    public record EdgeMatch(String sourceNodeId, Edge edge) {}

    private double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1e-6) {
            return Math.hypot(px - x1, py - y1);
        }

        double t = Math.clamp(((px - x1) * dx + (py - y1) * dy) / lengthSq, 0.0, 1.0);
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;

        return Math.hypot(px - projX, py - projY);
    }

    public double getZoom() {
        return zoom;
    }

    public double getPanX() {
        return panX;
    }

    public double getPanY() {
        return panY;
    }

    public double getCanvasWidth() {
        return canvasWidth;
    }

    public double getCanvasHeight() {
        return canvasHeight;
    }

    // ==========================================
    // Web Mercator Slippy Map Tile Calculations
    // ==========================================

    public record ScreenRect(double x, double y, double width, double height) {}

    public record TileBounds(int minX, int maxX, int minY, int maxY, int zoom) {}

    /**
     * Calculates the optimal Web Mercator tile zoom level [1..18] for the current viewport scale.
     */
    public int calculateOptimalTileZoom() {
        GeoCoordinate left = toGeo(0, canvasHeight / 2.0);
        GeoCoordinate right = toGeo(canvasWidth, canvasHeight / 2.0);
        double lonSpan = Math.abs(right.longitude() - left.longitude());
        if (lonSpan <= 1e-6) {
            lonSpan = 0.005;
        }

        double pixelsPerDegree = Math.max(1.0, canvasWidth / lonSpan);
        double zoomDouble = Math.log((pixelsPerDegree * 360.0) / 256.0) / Math.log(2.0);
        int z = (int) Math.round(zoomDouble);
        return Math.clamp(z, 2, 18);
    }

    /**
     * Calculates the bounding range of visible Slippy Map tiles intersecting the viewport.
     */
    public TileBounds getVisibleTileBounds(int zoom) {
        int clampedZoom = Math.clamp(zoom, 1, 19);
        int maxTiles = 1 << clampedZoom;

        // Sample corners and midpoints for safety against skewed aspect ratios
        GeoCoordinate nw = toGeo(0, 0);
        GeoCoordinate se = toGeo(canvasWidth, canvasHeight);
        GeoCoordinate ne = toGeo(canvasWidth, 0);
        GeoCoordinate sw = toGeo(0, canvasHeight);

        double minLonVal = Math.min(Math.min(nw.longitude(), se.longitude()), Math.min(ne.longitude(), sw.longitude()));
        double maxLonVal = Math.max(Math.max(nw.longitude(), se.longitude()), Math.max(ne.longitude(), sw.longitude()));
        double minLatVal = Math.min(Math.min(nw.latitude(), se.latitude()), Math.min(ne.latitude(), sw.latitude()));
        double maxLatVal = Math.max(Math.max(nw.latitude(), se.latitude()), Math.max(ne.latitude(), sw.latitude()));

        int minX = Math.clamp(lonToTileX(minLonVal, clampedZoom), 0, maxTiles - 1);
        int maxX = Math.clamp(lonToTileX(maxLonVal, clampedZoom), 0, maxTiles - 1);
        int minY = Math.clamp(latToTileY(maxLatVal, clampedZoom), 0, maxTiles - 1);
        int maxY = Math.clamp(latToTileY(minLatVal, clampedZoom), 0, maxTiles - 1);

        return new TileBounds(minX, maxX, minY, maxY, clampedZoom);
    }

    /**
     * Calculates the screen rectangle where a specific OpenStreetMap tile should be rendered.
     */
    public ScreenRect getTileScreenRect(int tileX, int tileY, int zoom) {
        double latNW = tileYToLat(tileY, zoom);
        double lonNW = tileXToLon(tileX, zoom);
        double latSE = tileYToLat(tileY + 1, zoom);
        double lonSE = tileXToLon(tileX + 1, zoom);

        ScreenPoint ptNW = toScreen(latNW, lonNW);
        ScreenPoint ptSE = toScreen(latSE, lonSE);

        double x = ptNW.x();
        double y = ptNW.y();
        double width = Math.max(1.0, ptSE.x() - ptNW.x());
        double height = Math.max(1.0, ptSE.y() - ptNW.y());

        return new ScreenRect(x, y, width, height);
    }

    public static int lonToTileX(double lon, int zoom) {
        int maxTiles = 1 << Math.clamp(zoom, 0, 30);
        double clampedLon = Math.clamp(lon, -180.0, 180.0);
        int x = (int) Math.floor((clampedLon + 180.0) / 360.0 * maxTiles);
        return Math.clamp(x, 0, maxTiles - 1);
    }

    public static int latToTileY(double lat, int zoom) {
        int maxTiles = 1 << Math.clamp(zoom, 0, 30);
        double clampedLat = Math.clamp(lat, -85.0511, 85.0511);
        double rad = Math.toRadians(clampedLat);
        double n = (1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0;
        int y = (int) Math.floor(n * maxTiles);
        return Math.clamp(y, 0, maxTiles - 1);
    }

    public static double tileXToLon(int tileX, int zoom) {
        int maxTiles = 1 << Math.clamp(zoom, 0, 30);
        return ((double) tileX / maxTiles) * 360.0 - 180.0;
    }

    public static double tileYToLat(int tileY, int zoom) {
        int maxTiles = 1 << Math.clamp(zoom, 0, 30);
        double n = Math.PI * (1.0 - 2.0 * ((double) tileY / maxTiles));
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}

