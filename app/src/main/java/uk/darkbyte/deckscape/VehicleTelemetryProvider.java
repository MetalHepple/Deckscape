package uk.darkbyte.deckscape;

import java.io.IOException;
import java.util.EnumSet;

/** A read-only source of normalized vehicle values for passive wallpaper cards. */
interface VehicleTelemetryProvider {
    boolean isAvailable();

    VehicleTelemetrySnapshot fetch(EnumSet<VehicleTelemetryMetric> metrics, long nowMillis)
            throws IOException;
}
