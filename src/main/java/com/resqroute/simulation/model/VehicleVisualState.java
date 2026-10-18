package com.resqroute.simulation.model;

/**
 * Visual presentation state of an emergency response vehicle on the map.
 *
 * <p>Provides distinct visual styles and badge indicators according to operational mission phase:
 * <ul>
 *   <li>{@link #AVAILABLE} - Stationary at station/hospital, ready for dispatch.</li>
 *   <li>{@link #RESPONDING} - Dispatched and en route to the emergency pickup location.</li>
 *   <li>{@link #AT_SCENE} - Present at scene stabilizing or extracting patient.</li>
 *   <li>{@link #TRANSPORTING} - En route to hospital facility conveying patient under care.</li>
 *   <li>{@link #RETURNING} - Returning to assigned base station or fleet staging hub.</li>
 *   <li>{@link #OUT_OF_SERVICE} - Offline or unavailable.</li>
 * </ul>
 * </p>
 */
public enum VehicleVisualState {

    /**
     * Stationary or standby, immediately ready for emergency assignment.
     */
    AVAILABLE,

    /**
     * In transit responding toward the emergency incident scene.
     */
    RESPONDING,

    /**
     * Arrived on scene providing emergency stabilization/treatment.
     */
    AT_SCENE,

    /**
     * In transit transporting patient to designated receiving hospital.
     */
    TRANSPORTING,

    /**
     * In transit returning to base station or dispatch hub.
     */
    RETURNING,

    /**
     * Unavailable due to shift change, maintenance, or offline status.
     */
    OUT_OF_SERVICE;

    /**
     * Returns true if vehicle is actively moving along a road route.
     */
    public boolean isMoving() {
        return this == RESPONDING || this == TRANSPORTING || this == RETURNING;
    }
}
