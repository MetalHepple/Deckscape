package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Owns opt-in wallpaper information settings and weather disclosure state. */
final class OverlaySettings {
    private static final String KEY_CLOCK_ENABLED = "overlay_clock_enabled";
    private static final String KEY_WEATHER_ENABLED = "overlay_weather_enabled";
    private static final String KEY_WEATHER_DISCLOSED = "overlay_weather_disclosed";
    private static final String KEY_POSITION = "overlay_position";
    private static final String KEY_CLOCK_X = "overlay_clock_x";
    private static final String KEY_CLOCK_Y = "overlay_clock_y";
    private static final String KEY_WEATHER_X = "overlay_weather_x";
    private static final String KEY_WEATHER_Y = "overlay_weather_y";
    private static final String KEY_VEHICLE_BATTERY_ENABLED =
            "overlay_vehicle_battery_enabled";
    private static final String KEY_VEHICLE_TEMPERATURES_ENABLED =
            "overlay_vehicle_temperatures_enabled";
    private static final String KEY_VEHICLE_TYRES_ENABLED = "overlay_vehicle_tyres_enabled";
    private static final String KEY_VEHICLE_BATTERY_X = "overlay_vehicle_battery_x";
    private static final String KEY_VEHICLE_BATTERY_Y = "overlay_vehicle_battery_y";
    private static final String KEY_VEHICLE_TEMPERATURES_X =
            "overlay_vehicle_temperatures_x";
    private static final String KEY_VEHICLE_TEMPERATURES_Y =
            "overlay_vehicle_temperatures_y";
    private static final String KEY_VEHICLE_TYRES_X = "overlay_vehicle_tyres_x";
    private static final String KEY_VEHICLE_TYRES_Y = "overlay_vehicle_tyres_y";

    private final SharedPreferences preferences;

    OverlaySettings(Context context) {
        preferences = context.getSharedPreferences(WallpaperEngineService.PREFS,
                Context.MODE_PRIVATE);
    }

    boolean isClockEnabled() {
        return isEnabled(OverlayWidget.CLOCK);
    }

    void setClockEnabled(boolean enabled) {
        setEnabled(OverlayWidget.CLOCK, enabled);
    }

    boolean isWeatherEnabled() {
        return isEnabled(OverlayWidget.WEATHER);
    }

    void setWeatherEnabled(boolean enabled) {
        setEnabled(OverlayWidget.WEATHER, enabled);
    }

    boolean isEnabled(OverlayWidget widget) {
        return preferences.getBoolean(enabledKey(widget), false);
    }

    void setEnabled(OverlayWidget widget, boolean enabled) {
        preferences.edit().putBoolean(enabledKey(widget), enabled).apply();
    }

    EnumSet<OverlayWidget> enabledWidgets() {
        EnumSet<OverlayWidget> result = EnumSet.noneOf(OverlayWidget.class);
        for (OverlayWidget widget : OverlayWidget.values()) {
            if (isEnabled(widget)) result.add(widget);
        }
        return result;
    }

    EnumSet<VehicleTelemetryMetric> requestedVehicleMetrics() {
        EnumSet<VehicleTelemetryMetric> result =
                EnumSet.noneOf(VehicleTelemetryMetric.class);
        for (OverlayWidget widget : enabledWidgets()) {
            if (widget.telemetryMetric != null) result.add(widget.telemetryMetric);
        }
        return result;
    }

    void setEnabledWidgets(EnumSet<OverlayWidget> enabledWidgets) {
        EnumSet<OverlayWidget> values = enabledWidgets == null
                ? EnumSet.noneOf(OverlayWidget.class) : EnumSet.copyOf(enabledWidgets);
        SharedPreferences.Editor editor = preferences.edit();
        for (OverlayWidget widget : OverlayWidget.values()) {
            editor.putBoolean(enabledKey(widget), values.contains(widget));
        }
        editor.apply();
    }

    void setEnabled(boolean clockEnabled, boolean weatherEnabled) {
        EnumSet<OverlayWidget> enabled = enabledWidgets();
        setMembership(enabled, OverlayWidget.CLOCK, clockEnabled);
        setMembership(enabled, OverlayWidget.WEATHER, weatherEnabled);
        setEnabledWidgets(enabled);
    }

    boolean hasWeatherDisclosure() {
        return preferences.getBoolean(KEY_WEATHER_DISCLOSED, false);
    }

    void acknowledgeWeatherDisclosure() {
        preferences.edit().putBoolean(KEY_WEATHER_DISCLOSED, true).apply();
    }

    OverlayPlacement clockPlacement() {
        return placement(OverlayWidget.CLOCK);
    }

    OverlayPlacement weatherPlacement() {
        return placement(OverlayWidget.WEATHER);
    }

    OverlayPlacement placement(OverlayWidget widget) {
        String xKey = xKey(widget);
        String yKey = yKey(widget);
        if (preferences.contains(xKey) && preferences.contains(yKey)) {
            return new OverlayPlacement(preferences.getFloat(xKey, 0.5f),
                    preferences.getFloat(yKey, 0.5f));
        }
        if (preferences.contains(KEY_POSITION)) {
            if (widget == OverlayWidget.CLOCK) return OverlayPlacement.legacyClock(legacyPosition());
            if (widget == OverlayWidget.WEATHER) {
                return OverlayPlacement.legacyWeather(legacyPosition());
            }
        }
        return widget.defaultPlacement;
    }

    EnumMap<OverlayWidget, OverlayPlacement> placements() {
        EnumMap<OverlayWidget, OverlayPlacement> result =
                new EnumMap<>(OverlayWidget.class);
        for (OverlayWidget widget : OverlayWidget.values()) {
            result.put(widget, placement(widget));
        }
        return result;
    }

    void setPlacements(OverlayPlacement clock, OverlayPlacement weather) {
        EnumMap<OverlayWidget, OverlayPlacement> values = placements();
        values.put(OverlayWidget.CLOCK, clock);
        values.put(OverlayWidget.WEATHER, weather);
        setPlacements(values);
    }

    void setPlacements(Map<OverlayWidget, OverlayPlacement> placements) {
        SharedPreferences.Editor editor = preferences.edit();
        for (OverlayWidget widget : OverlayWidget.values()) {
            OverlayPlacement placement = placements == null ? null : placements.get(widget);
            OverlayPlacement value = placement == null ? widget.defaultPlacement : placement;
            editor.putFloat(xKey(widget), value.x);
            editor.putFloat(yKey(widget), value.y);
        }
        editor.remove(KEY_POSITION).apply();
    }

    private OverlayPosition legacyPosition() {
        return OverlayPosition.parse(preferences.getString(
                KEY_POSITION, OverlayPosition.TOP_LEFT.name()));
    }

    private static void setMembership(EnumSet<OverlayWidget> values, OverlayWidget widget,
                                      boolean enabled) {
        if (enabled) values.add(widget);
        else values.remove(widget);
    }

    private static String enabledKey(OverlayWidget widget) {
        switch (widget) {
            case CLOCK:
                return KEY_CLOCK_ENABLED;
            case WEATHER:
                return KEY_WEATHER_ENABLED;
            case VEHICLE_BATTERY:
                return KEY_VEHICLE_BATTERY_ENABLED;
            case VEHICLE_TEMPERATURES:
                return KEY_VEHICLE_TEMPERATURES_ENABLED;
            case VEHICLE_TYRES:
                return KEY_VEHICLE_TYRES_ENABLED;
            default:
                throw new IllegalArgumentException("Unknown overlay widget");
        }
    }

    private static String xKey(OverlayWidget widget) {
        switch (widget) {
            case CLOCK:
                return KEY_CLOCK_X;
            case WEATHER:
                return KEY_WEATHER_X;
            case VEHICLE_BATTERY:
                return KEY_VEHICLE_BATTERY_X;
            case VEHICLE_TEMPERATURES:
                return KEY_VEHICLE_TEMPERATURES_X;
            case VEHICLE_TYRES:
                return KEY_VEHICLE_TYRES_X;
            default:
                throw new IllegalArgumentException("Unknown overlay widget");
        }
    }

    private static String yKey(OverlayWidget widget) {
        switch (widget) {
            case CLOCK:
                return KEY_CLOCK_Y;
            case WEATHER:
                return KEY_WEATHER_Y;
            case VEHICLE_BATTERY:
                return KEY_VEHICLE_BATTERY_Y;
            case VEHICLE_TEMPERATURES:
                return KEY_VEHICLE_TEMPERATURES_Y;
            case VEHICLE_TYRES:
                return KEY_VEHICLE_TYRES_Y;
            default:
                throw new IllegalArgumentException("Unknown overlay widget");
        }
    }
}
