package com.resqroute.ui.map;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OsmTileService and Web Mercator Slippy Map Tile Tests")
class OsmTileServiceTest {

    // 1x1 transparent PNG image bytes for headless mock testing
    private static final byte[] TINY_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08,
            0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00, 0x00,
            0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    @DisplayName("Tile coordinate conversions match Web Mercator EPSG:3857 standards")
    void tileCoordinateConversions() {
        // Bengaluru Cubbon Park: 12.975° N, 77.592° E at zoom 13
        int tileX = MapCoordinateTransform.lonToTileX(77.592, 13);
        int tileY = MapCoordinateTransform.latToTileY(12.975, 13);

        assertEquals(5861, tileX, "Tile X for 77.592° at zoom 13 must match expected Mercator column");
        assertEquals(3798, tileY, "Tile Y for 12.975° at zoom 13 must match expected Mercator row");

        // Verify inverse conversions reconstruct geographic coordinates within tile bounds
        double lonReconstructed = MapCoordinateTransform.tileXToLon(tileX, 13);
        double latReconstructed = MapCoordinateTransform.tileYToLat(tileY, 13);

        assertTrue(lonReconstructed <= 77.592 && 77.592 <= MapCoordinateTransform.tileXToLon(tileX + 1, 13),
                "Coordinate longitude must fall strictly within the tile's horizontal span");
        assertTrue(latReconstructed >= 12.975 && 12.975 >= MapCoordinateTransform.tileYToLat(tileY + 1, 13),
                "Coordinate latitude must fall strictly within the tile's vertical span");
    }

    @Test
    @DisplayName("Extreme coordinate edge cases clamp safely without throwing")
    void extremeCoordinateEdgeCases() {
        // North/South pole limits
        int yNorth = MapCoordinateTransform.latToTileY(89.0, 10);
        int ySouth = MapCoordinateTransform.latToTileY(-89.0, 10);
        assertTrue(yNorth >= 0, "North pole must clamp to valid row index >= 0");
        assertTrue(ySouth < (1 << 10), "South pole must clamp to valid row index < 2^10");

        // Antimeridian limits (-180, +180)
        int xWest = MapCoordinateTransform.lonToTileX(-180.0, 10);
        int xEast = MapCoordinateTransform.lonToTileX(180.0, 10);
        assertEquals(0, xWest, "Longitude -180 must map to tile 0");
        assertTrue(xEast < (1 << 10), "Longitude 180 must map to last tile in column");
    }

    @Test
    @DisplayName("MapProvider builds authentic URLs and satisfies attribution requirements")
    void mapProviderUrlAndAttribution() {
        String osmUrl = MapProvider.OPEN_STREET_MAP.buildTileUrl(5861, 3798, 13);
        assertEquals("https://tile.openstreetmap.org/13/5861/3798.png", osmUrl);
        assertNotNull(MapProvider.OPEN_STREET_MAP.getAttribution());
        assertTrue(MapProvider.OPEN_STREET_MAP.getAttribution().contains("OpenStreetMap"));

        String cartoUrl = MapProvider.CARTO_DARK.buildTileUrl(5861, 3798, 13);
        assertEquals("https://a.basemaps.cartocdn.com/dark_all/13/5861/3798.png", cartoUrl);
        assertTrue(MapProvider.CARTO_DARK.getAttribution().contains("CARTO"));

        assertNull(MapProvider.OFFLINE_VECTOR.buildTileUrl(5861, 3798, 13));
        assertFalse(MapProvider.OFFLINE_VECTOR.isOnline());
    }

    @Test
    @DisplayName("Tile bounds calculation accurately encompasses bounding box")
    void tileBoundsCalculation() {
        MapCoordinateTransform transform = new MapCoordinateTransform();
        transform.updateCanvasSize(800, 600);
        transform.fitToBounds(java.util.List.of(
                new com.resqroute.model.Node("A", 12.98, 77.60),
                new com.resqroute.model.Node("B", 12.94, 77.58)
        ));

        int zoom = transform.calculateOptimalTileZoom();
        assertTrue(zoom >= 10 && zoom <= 18, "Optimal zoom level for metropolis sector should be in [10..18]");

        MapCoordinateTransform.TileBounds bounds = transform.getVisibleTileBounds(zoom);
        assertTrue(bounds.minX() <= bounds.maxX(), "MinX must be <= MaxX");
        assertTrue(bounds.minY() <= bounds.maxY(), "MinY must be <= MaxY");

        MapCoordinateTransform.ScreenRect rect = transform.getTileScreenRect(bounds.minX(), bounds.minY(), zoom);
        assertTrue(rect.width() > 0 && rect.height() > 0, "Tile screen rectangle must have positive dimensions");
    }

    @Test
    @DisplayName("In-memory LRU cache stores and retrieves tiles with bounded size")
    void lruCacheStorageAndEviction() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        OsmTileService service = new OsmTileService(4, url -> TINY_PNG, null);
        service.setOnTileLoadedListener(latch::countDown);

        // Request tile (initially not in cache, triggers async fetch)
        Optional<Image> initial = service.getTile(10, 20, 8, MapProvider.CARTO_DARK);
        assertTrue(initial.isEmpty(), "Initial request before load completion must return empty");

        boolean loaded = latch.await(2, TimeUnit.SECONDS);
        assertTrue(loaded, "Tile fetcher must notify onTileLoadedListener within 2 seconds");

        // Subsequent request must be served immediately from cache
        Optional<Image> cached = service.getTile(10, 20, 8, MapProvider.CARTO_DARK);
        assertTrue(cached.isPresent(), "Cached image must be returned on subsequent call");
        assertFalse(cached.get().isError(), "Image must not be in error state");

        service.shutdown();
    }

    @Test
    @DisplayName("Offline network failures trigger circuit breaker fallback gracefully without crashing")
    void offlineNetworkFailuresTriggerCircuitBreaker() throws Exception {
        OsmTileService service = new OsmTileService(16, url -> {
            throw new java.net.ConnectException("Network is unreachable (simulated offline mode)");
        }, null);

        // Request 3 distinct tiles that will fail
        service.getTile(1, 1, 5, MapProvider.CARTO_DARK);
        service.getTile(2, 1, 5, MapProvider.CARTO_DARK);
        service.getTile(3, 1, 5, MapProvider.CARTO_DARK);

        // Allow async thread pool to process failures
        Thread.sleep(400);

        assertEquals(OsmTileService.Status.OFFLINE_FALLBACK, service.getStatus(),
                "Consecutive network failures must trip status to OFFLINE_FALLBACK");

        // Verify retryConnection restores ONLINE status
        service.retryConnection();
        assertEquals(OsmTileService.Status.ONLINE, service.getStatus(),
                "retryConnection must reset circuit breaker to ONLINE");

        service.shutdown();
    }

    @Test
    @DisplayName("Invalid tile bounds parameters return empty safely")
    void invalidParametersHandledSafely() {
        OsmTileService service = new OsmTileService();
        assertTrue(service.getTile(-1, 0, 10, MapProvider.CARTO_DARK).isEmpty());
        assertTrue(service.getTile(0, -1, 10, MapProvider.CARTO_DARK).isEmpty());
        assertTrue(service.getTile(0, 0, -1, MapProvider.CARTO_DARK).isEmpty());
        assertTrue(service.getTile(1000, 1000, 2, MapProvider.CARTO_DARK).isEmpty()); // Tile index > 2^2
        assertTrue(service.getTile(0, 0, 10, MapProvider.OFFLINE_VECTOR).isEmpty()); // Offline provider
        assertTrue(service.getTile(0, 0, 10, null).isEmpty()); // Null provider
        service.shutdown();
    }
}
