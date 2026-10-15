package com.resqroute.model;

import java.util.Objects;

/**
 * Represents a geographical intersection, waypoint, or emergency facility in the road network.
 * Node equality and hash code are strictly derived from the unique identifier.
 */
public final class Node {

    private final String id;
    private final GeoCoordinate coordinates;
    private final String name;

    /**
     * Constructs a Node with full details.
     *
     * @param id          Unique non-blank node identifier.
     * @param coordinates Non-null geographic coordinates.
     * @param name        Descriptive name (e.g. "St. Jude Trauma Center"). If null/blank, defaults to id.
     */
    public Node(String id, GeoCoordinate coordinates, String name) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID must not be null or blank");
        }
        this.id = id.trim();
        this.coordinates = Objects.requireNonNull(coordinates, "Coordinates must not be null");
        this.name = (name == null || name.trim().isEmpty()) ? this.id : name.trim();
    }

    /**
     * Constructs a Node without an explicit name.
     */
    public Node(String id, GeoCoordinate coordinates) {
        this(id, coordinates, null);
    }

    /**
     * Convenience constructor with raw latitude and longitude.
     */
    public Node(String id, double latitude, double longitude) {
        this(id, new GeoCoordinate(latitude, longitude), null);
    }

    /**
     * Convenience constructor with raw latitude, longitude, and descriptive name.
     */
    public Node(String id, double latitude, double longitude, String name) {
        this(id, new GeoCoordinate(latitude, longitude), name);
    }

    public String getId() {
        return id;
    }

    public GeoCoordinate getCoordinates() {
        return coordinates;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Node{id='%s', name='%s', lat=%.5f, lon=%.5f}",
                id, name, coordinates.latitude(), coordinates.longitude());
    }
}
