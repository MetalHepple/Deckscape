package uk.darkbyte.deckscape;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Produces concise source-health text for the widget workspace only. */
final class WidgetDiagnostics {
    private WidgetDiagnostics() {}

    static String describe(EnumSet<OverlayWidget> enabled, boolean hasSavedArea,
                           WeatherSnapshot weather, boolean overdriveInstalled,
                           VehicleTelemetrySnapshot vehicle, long nowMillis) {
        EnumSet<OverlayWidget> values = enabled == null
                ? EnumSet.noneOf(OverlayWidget.class) : EnumSet.copyOf(enabled);
        List<String> lines = new ArrayList<>();
        boolean usesWeather = false;
        for (OverlayWidget widget : values) usesWeather |= widget.usesWeather;
        if (!usesWeather) {
            lines.add("Weather data: widgets off");
        } else if (!hasSavedArea) {
            lines.add("Weather data: saved area needed");
        } else if (weather == null || !weather.isValid()) {
            lines.add("Weather data: waiting for refresh");
        } else {
            String forecast = values.contains(OverlayWidget.FORECAST)
                    ? weather.forecast.isEmpty() ? " • forecast unavailable"
                    : " • " + weather.forecast.size() + " forecast points" : "";
            lines.add("Weather data: updated " + age(weather.fetchedAtMillis, nowMillis)
                    + forecast);
        }

        boolean usesVehicle = false;
        for (OverlayWidget widget : values) usesVehicle |= widget.telemetryMetric != null;
        if (!usesVehicle) {
            lines.add("Overdrive data: widgets off");
        } else if (!overdriveInstalled) {
            lines.add("Overdrive data: app not installed");
        } else if (vehicle == null || !vehicle.isDisplayable(nowMillis)) {
            lines.add("Overdrive data: waiting for local telemetry");
        } else {
            lines.add("Overdrive data: updated " + age(vehicle.fetchedAtMillis, nowMillis));
            if (values.contains(OverlayWidget.VEHICLE_BATTERY)
                    && !VehicleTelemetrySnapshot.isNumber(vehicle.socPercent)) {
                lines.add("Battery: state of charge unavailable");
            }
            if (values.contains(OverlayWidget.VEHICLE_TEMPERATURES)) {
                List<String> missing = new ArrayList<>();
                if (!VehicleTelemetrySnapshot.isNumber(vehicle.cabinTempC)) missing.add("cabin");
                if (!VehicleTelemetrySnapshot.isNumber(vehicle.outdoorTempC)) missing.add("outside");
                if (!VehicleTelemetrySnapshot.isNumber(vehicle.batteryTempC)) missing.add("battery");
                lines.add(missing.isEmpty() ? "Temperatures: all readings ready"
                        : "Temperatures: " + String.join(", ", missing) + " unavailable");
            }
            if (values.contains(OverlayWidget.VEHICLE_TYRES)) {
                int present = 0;
                if (VehicleTelemetrySnapshot.isNumber(vehicle.tyreFrontLeftBar)) present++;
                if (VehicleTelemetrySnapshot.isNumber(vehicle.tyreFrontRightBar)) present++;
                if (VehicleTelemetrySnapshot.isNumber(vehicle.tyreRearLeftBar)) present++;
                if (VehicleTelemetrySnapshot.isNumber(vehicle.tyreRearRightBar)) present++;
                lines.add(present == 4 ? "Tyres: all readings ready"
                        : String.format(Locale.ROOT, "Tyres: %d of 4 readings ready", present));
            }
        }
        return String.join("\n", lines);
    }

    private static String age(long fetchedAtMillis, long nowMillis) {
        if (fetchedAtMillis <= 0 || nowMillis < fetchedAtMillis) return "time unknown";
        long minutes = (nowMillis - fetchedAtMillis) / 60_000L;
        if (minutes == 0) return "just now";
        if (minutes < 60) return minutes + "m ago";
        return minutes / 60 + "h ago";
    }
}
