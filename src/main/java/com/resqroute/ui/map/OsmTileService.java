package com.resqroute.ui.map;

import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * High-performance, non-blocking tile caching and asynchronous fetching service
 * for OpenStreetMap cartography.
 *
 * <p>Features:
 * <ul>
 *   <li>Complies with OpenStreetMap Foundation Tile Usage Policies (custom User-Agent, concurrency caps).</li>
 *   <li>Thread-safe two-tier in-memory LRU caching with bounded capacity.</li>
 *   <li>Automatic circuit breaker that detects network offline state and triggers fallback mode without crashing.</li>
 *   <li>Pluggable tile fetcher interface enabling 100% offline unit and integration testing.</li>
 * </ul>
 * </p>
 */
public class OsmTileService {

    public static final String DEFAULT_USER_AGENT =
            "ResQRoute-EMS/1.0 (Emergency Ambulance Dispatch; https://github.com/Shyaman014/ResQRoute)";

    public enum Status {
        ONLINE("🟢 Live Tiles (Online)"),
        LOADING("🔄 Loading Map Tiles..."),
        OFFLINE_FALLBACK("⚠️ Offline Fallback (Vector Network Active)"),
        DISABLED("📐 Local Vector Mode");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @FunctionalInterface
    public interface TileFetcher {
        byte[] fetchTileBytes(String url) throws Exception;
    }

    private final int maxCacheSize;
    private final TileFetcher tileFetcher;
    private final ExecutorService executorService;
    private final boolean ownsExecutor;

    private final Map<String, Image> memoryCache;
    private final Set<String> inFlightRequests = ConcurrentHashMap.newKeySet();
    private final Set<String> failedTileKeys = ConcurrentHashMap.newKeySet();

    private volatile Status status = Status.ONLINE;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 3;

    private Runnable onTileLoadedListener;
    private Consumer<Status> onStatusChangedListener;

    /**
     * Constructs a tile service using the default HTTP client fetcher.
     */
    public OsmTileService() {
        this(256, createDefaultHttpFetcher(), null);
    }

    /**
     * Constructs a tile service with a custom fetcher (useful for unit testing).
     */
    public OsmTileService(int maxCacheSize, TileFetcher tileFetcher, ExecutorService customExecutor) {
        this.maxCacheSize = Math.max(16, maxCacheSize);
        this.tileFetcher = Objects.requireNonNull(tileFetcher, "tileFetcher must not be null");

        if (customExecutor != null) {
            this.executorService = customExecutor;
            this.ownsExecutor = false;
        } else {
            this.executorService = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "OsmTileFetcher");
                t.setDaemon(true);
                return t;
            });
            this.ownsExecutor = true;
        }

        this.memoryCache = Collections.synchronizedMap(new LinkedHashMap<String, Image>(maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > OsmTileService.this.maxCacheSize;
            }
        });
    }

    /**
     * Retrieves an OpenStreetMap tile from cache if present; otherwise enqueues asynchronous retrieval.
     *
     * @param tileX    Tile X coordinate [0..2^zoom-1]
     * @param tileY    Tile Y coordinate [0..2^zoom-1]
     * @param zoom     Zoom level [0..19]
     * @param provider Map provider configuration
     * @return Optional containing the cached JavaFX Image if immediately available, or empty if pending/failed
     */
    public Optional<Image> getTile(int tileX, int tileY, int zoom, MapProvider provider) {
        if (provider == null || !provider.isOnline()) {
            return Optional.empty();
        }

        // Validate coordinate bounds
        int maxTiles = 1 << Math.clamp(zoom, 0, 30);
        if (zoom < 0 || zoom > 19 || tileX < 0 || tileX >= maxTiles || tileY < 0 || tileY >= maxTiles) {
            return Optional.empty();
        }

        String key = buildCacheKey(provider, tileX, tileY, zoom);

        // 1. Check in-memory LRU cache
        Image cached = memoryCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        // 2. If already marked as failed or in offline fallback mode with failures, return empty
        if (failedTileKeys.contains(key)) {
            return Optional.empty();
        }

        // 3. Trigger asynchronous background fetch if not already in-flight
        if (inFlightRequests.add(key)) {
            requestTileAsync(provider, tileX, tileY, zoom, key);
        }

        return Optional.empty();
    }

    private void requestTileAsync(MapProvider provider, int tileX, int tileY, int zoom, String key) {
        String url = provider.buildTileUrl(tileX, tileY, zoom);
        if (url == null) {
            inFlightRequests.remove(key);
            return;
        }

        if (status == Status.ONLINE && consecutiveFailures.get() == 0) {
            updateStatus(Status.LOADING);
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return tileFetcher.fetchTileBytes(url);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService).whenComplete((bytes, error) -> {
            inFlightRequests.remove(key);

            if (error != null || bytes == null || bytes.length == 0) {
                failedTileKeys.add(key);
                int fails = consecutiveFailures.incrementAndGet();
                if (fails >= FAILURE_THRESHOLD && status != Status.OFFLINE_FALLBACK) {
                    updateStatus(Status.OFFLINE_FALLBACK);
                }
                return;
            }

            try (InputStream in = new ByteArrayInputStream(bytes)) {
                Image image = new Image(in);
                if (!image.isError() && image.getWidth() > 0) {
                    memoryCache.put(key, image);
                    consecutiveFailures.set(0);
                    if (status != Status.ONLINE) {
                        updateStatus(Status.ONLINE);
                    }
                    notifyTileLoaded();
                } else {
                    failedTileKeys.add(key);
                }
            } catch (Exception ex) {
                failedTileKeys.add(key);
            }
        });
    }

    private void updateStatus(Status newStatus) {
        this.status = newStatus;
        Consumer<Status> listener = onStatusChangedListener;
        if (listener != null) {
            safeRunOnFxThread(() -> listener.accept(newStatus));
        }
    }

    private void notifyTileLoaded() {
        Runnable listener = onTileLoadedListener;
        if (listener != null) {
            safeRunOnFxThread(listener);
        }
    }

    private void safeRunOnFxThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // JavaFX toolkit not initialized (e.g., in headless unit test)
            action.run();
        }
    }

    public void retryConnection() {
        consecutiveFailures.set(0);
        failedTileKeys.clear();
        updateStatus(Status.ONLINE);
        notifyTileLoaded();
    }

    public void clearCache() {
        memoryCache.clear();
        failedTileKeys.clear();
        inFlightRequests.clear();
        notifyTileLoaded();
    }

    public int getCacheSize() {
        return memoryCache.size();
    }

    public Status getStatus() {
        return status;
    }

    public void setOnTileLoadedListener(Runnable onTileLoadedListener) {
        this.onTileLoadedListener = onTileLoadedListener;
    }

    public void setOnStatusChangedListener(Consumer<Status> onStatusChangedListener) {
        this.onStatusChangedListener = onStatusChangedListener;
    }

    public void shutdown() {
        if (ownsExecutor) {
            executorService.shutdownNow();
        }
    }

    public static String buildCacheKey(MapProvider provider, int tileX, int tileY, int zoom) {
        return provider.name() + "/" + zoom + "/" + tileX + "/" + tileY;
    }

    private static TileFetcher createDefaultHttpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        return url -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "image/png,image/*;q=0.8")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new RuntimeException("HTTP " + response.statusCode() + " fetching tile: " + url);
        };
    }
}
