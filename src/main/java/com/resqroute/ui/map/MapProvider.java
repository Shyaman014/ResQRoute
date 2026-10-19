package com.resqroute.ui.map;

/**
 * Supported map tile providers for OpenStreetMap-based cartography rendering.
 *
 * <p>Complies with OpenStreetMap Foundation Tile Usage Policies by requiring valid
 * User-Agent headers and rendering obligatory attribution notices.</p>
 */
public enum MapProvider {

    CARTO_DARK(
            "OpenStreetMap (Dark Carto)",
            "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors, © CARTO"
    ),

    OPEN_STREET_MAP(
            "OpenStreetMap (Standard)",
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors"
    ),

    CARTO_LIGHT(
            "OpenStreetMap (Light Positron)",
            "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors, © CARTO"
    ),

    OFFLINE_VECTOR(
            "Offline Blueprint (Vector Grid)",
            null,
            "ResQRoute Local Vector Topology (Offline)"
    );

    private final String displayName;
    private final String urlTemplate;
    private final String attribution;

    MapProvider(String displayName, String urlTemplate, String attribution) {
        this.displayName = displayName;
        this.urlTemplate = urlTemplate;
        this.attribution = attribution;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public String getAttribution() {
        return attribution;
    }

    public boolean isOnline() {
        return urlTemplate != null;
    }

    /**
     * Builds the HTTP URL for a specific tile.
     *
     * @param tileX Tile column index [0..2^z-1]
     * @param tileY Tile row index [0..2^z-1]
     * @param zoom  Zoom level [0..19]
     * @return Formatted URL or null if offline provider
     */
    public String buildTileUrl(int tileX, int tileY, int zoom) {
        if (urlTemplate == null) {
            return null;
        }
        return urlTemplate
                .replace("{z}", String.valueOf(zoom))
                .replace("{x}", String.valueOf(tileX))
                .replace("{y}", String.valueOf(tileY));
    }

    @Override
    public String toString() {
        return displayName;
    }
}
