package uk.darkbyte.deckscape;

import java.util.EnumSet;

/** Stable identities for independently enabled and positioned wallpaper cards. */
enum OverlayWidget {
    CLOCK("clock", "CLOCK", new OverlayPlacement(0.16f, 0.36f), null),
    WEATHER("weather", "WEATHER", new OverlayPlacement(0.38f, 0.36f), null),
    VEHICLE_BATTERY("vehicle_battery", "BATTERY",
            new OverlayPlacement(0.15f, 0.52f), VehicleTelemetryMetric.BATTERY),
    VEHICLE_TEMPERATURES("vehicle_temperatures", "TEMPERATURES",
            new OverlayPlacement(0.40f, 0.52f), VehicleTelemetryMetric.TEMPERATURES),
    VEHICLE_TYRES("vehicle_tyres", "TYRES",
            new OverlayPlacement(0.66f, 0.52f), VehicleTelemetryMetric.TYRES);

    final String preferenceSuffix;
    final String editorLabel;
    final OverlayPlacement defaultPlacement;
    final VehicleTelemetryMetric telemetryMetric;

    OverlayWidget(String preferenceSuffix, String editorLabel,
                  OverlayPlacement defaultPlacement,
                  VehicleTelemetryMetric telemetryMetric) {
        this.preferenceSuffix = preferenceSuffix;
        this.editorLabel = editorLabel;
        this.defaultPlacement = defaultPlacement;
        this.telemetryMetric = telemetryMetric;
    }

    static EnumSet<OverlayWidget> availableWhen(boolean vehicleProviderAvailable) {
        return vehicleProviderAvailable
                ? EnumSet.allOf(OverlayWidget.class)
                : EnumSet.of(CLOCK, WEATHER);
    }
}
