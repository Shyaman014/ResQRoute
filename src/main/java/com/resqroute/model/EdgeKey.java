package com.resqroute.model;

import java.util.Objects;

/**
 * Immutable key identifying a directed road segment between an origin and destination node.
 * Useful for graph index lookups and path exclusion sets in Yen's K-shortest paths algorithm.
 */
public record EdgeKey(String sourceId, String destinationId) {

    public EdgeKey {
        Objects.requireNonNull(sourceId, "Source ID must not be null");
        Objects.requireNonNull(destinationId, "Destination ID must not be null");
    }

    public static EdgeKey of(String sourceId, String destinationId) {
        return new EdgeKey(sourceId.trim(), destinationId.trim());
    }
}
