package uk.darkbyte.deckscape;

/** Process-memory-only cache. Vehicle telemetry is never persisted by Deckscape. */
final class VehicleTelemetryStore {
    private static volatile VehicleTelemetrySnapshot latest;

    private VehicleTelemetryStore() {}

    static VehicleTelemetrySnapshot latest() {
        return latest;
    }

    static void update(VehicleTelemetrySnapshot snapshot) {
        if (snapshot != null) latest = snapshot;
    }
}
