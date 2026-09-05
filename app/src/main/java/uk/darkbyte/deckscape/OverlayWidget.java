package uk.darkbyte.deckscape;

import java.util.EnumSet;

/** Stable identities for independently enabled and positioned wallpaper cards. */
enum OverlayWidget {
    CLOCK("clock", "CLOCK", new OverlayPlacement(0.16f, 0.36f), false, null),
    WEATHER("weather", "WEATHER", new OverlayPlacement(0.38f, 0.36f), true, null),
    FORECAST("forecast", "FORECAST", new OverlayPlacement(0.67f, 0.36f), true, null),
    VEHICLE_BATTERY("vehicle_battery", "BATTERY",
            new OverlayPlacement(0.15f, 0.52f), false, VehicleTelemetryMetric.BATTERY),
    VEHICLE_TEMPERATURES("vehicle_temperatures", "TEMPERATURES",
            new OverlayPlacement(0.40f, 0.52f), false, VehicleTelemetryMetric.TEMPERATURES),
    VEHICLE_TYRES("vehicle_tyres", "TYRES",
            new OverlayPlacement(0.66f, 0.52f), false, VehicleTelemetryMetric.TYRES);

    final String preferenceSuffix;
    final String editorLabel;
    final OverlayPlacement defaultPlacement;
    final boolean usesWeather;
    final VehicleTelemetryMetric telemetryMetric;

    OverlayWidget(String preferenceSuffix, String editorLabel,
                  OverlayPlacement defaultPlacement,
                  boolean usesWeather,
                  VehicleTelemetryMetric telemetryMetric) {
        this.preferenceSuffix = preferenceSuffix;
        this.editorLabel = editorLabel;
        this.defaultPlacement = defaultPlacement;
        this.usesWeather = usesWeather;
        this.telemetryMetric = telemetryMetric;
    }

    static EnumSet<OverlayWidget> availableWhen(boolean vehicleProviderAvailable) {
        return vehicleProviderAvailable
                ? EnumSet.allOf(OverlayWidget.class)
                : EnumSet.of(CLOCK, WEATHER, FORECAST);
    }
}
