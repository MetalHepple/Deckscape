package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Owns global display defaults and privacy-preserving day/night schedule preferences. */
final class DayNightSettings {
    static final int DEFAULT_DAY_MINUTE = 7 * 60;
    static final int DEFAULT_NIGHT_MINUTE = 19 * 60;

    private static final String KEY_ENABLED = "day_night_enabled";
    private static final String KEY_MODE = "day_night_mode";
    private static final String KEY_DAY_MINUTE = "day_start_minute";
    private static final String KEY_NIGHT_MINUTE = "night_start_minute";
    private static final String KEY_LATITUDE_TENTHS = "coarse_latitude_tenths";
    private static final String KEY_LONGITUDE_TENTHS = "coarse_longitude_tenths";
    private static final String KEY_LOCATION_TIME = "coarse_location_time";
    private static final String KEY_HAS_LOCATION = "has_coarse_location";
    private static final String KEY_DEFAULT_SCALE = "default_scale_mode";

    private final Context context;
    private final SharedPreferences preferences;
    private final WallpaperProfileStore profiles;

    DayNightSettings(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(WallpaperEngineService.PREFS,
                Context.MODE_PRIVATE);
        profiles = new WallpaperProfileStore(context);
    }

    boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    boolean setEnabled(boolean enabled) {
        if (enabled && !hasCompletePools()) return false;
        SharedPreferences.Editor editor = preferences.edit().putBoolean(KEY_ENABLED, enabled);
        if (enabled) editor.remove(WallpaperEngineService.PREF_LAST_PHASE);
        editor.apply();
        return true;
    }

    ScheduleMode mode() {
        return ScheduleMode.parse(preferences.getString(KEY_MODE, ScheduleMode.AUTO.name()));
    }

    void setMode(ScheduleMode mode) {
        preferences.edit().putString(KEY_MODE,
                (mode == null ? ScheduleMode.AUTO : mode).name()).apply();
    }

    int dayMinute() {
        return preferences.getInt(KEY_DAY_MINUTE, DEFAULT_DAY_MINUTE);
    }

    int nightMinute() {
        return preferences.getInt(KEY_NIGHT_MINUTE, DEFAULT_NIGHT_MINUTE);
    }

    void setManualTimes(int dayMinute, int nightMinute) {
        preferences.edit()
                .putInt(KEY_DAY_MINUTE, Math.floorMod(dayMinute, 24 * 60))
                .putInt(KEY_NIGHT_MINUTE, Math.floorMod(nightMinute, 24 * 60))
                .apply();
    }

    ScaleMode defaultScaleMode() {
        return ScaleMode.parse(preferences.getString(KEY_DEFAULT_SCALE, ScaleMode.FILL.name()),
                ScaleMode.FILL);
    }

    void setDefaultScaleMode(ScaleMode mode) {
        ScaleMode value = mode == null || mode == ScaleMode.DEFAULT || mode == ScaleMode.CUSTOM
                ? ScaleMode.FILL : mode;
        preferences.edit().putString(KEY_DEFAULT_SCALE, value.name()).apply();
    }

    void setCoarseLocation(double latitude, double longitude, long acquiredAt) {
        int roundedLatitude = (int) Math.round(Math.max(-90, Math.min(90, latitude)) * 10);
        int roundedLongitude = (int) Math.round(Math.max(-180, Math.min(180, longitude)) * 10);
        preferences.edit()
                .putBoolean(KEY_HAS_LOCATION, true)
                .putInt(KEY_LATITUDE_TENTHS, roundedLatitude)
                .putInt(KEY_LONGITUDE_TENTHS, roundedLongitude)
                .putLong(KEY_LOCATION_TIME, acquiredAt)
                .apply();
    }

    boolean hasCoarseLocation() {
        return preferences.getBoolean(KEY_HAS_LOCATION, false);
    }

    long locationTime() {
        return preferences.getLong(KEY_LOCATION_TIME, 0);
    }

    /** Returns today's local solar boundaries from the same inputs used by the engine. */
    DayPhaseResolver.SolarTimes solarTimes(long nowMillis) {
        if (!hasCoarseLocation()) return null;
        double latitude = preferences.getInt(KEY_LATITUDE_TENTHS, 0) / 10.0;
        double longitude = preferences.getInt(KEY_LONGITUDE_TENTHS, 0) / 10.0;
        return DayPhaseResolver.solarTimes(Instant.ofEpochMilli(nowMillis),
                ZoneId.systemDefault(), latitude, longitude);
    }

    DayPhase currentPhase(long nowMillis, DayPhase sensorPhase) {
        Instant now = Instant.ofEpochMilli(nowMillis);
        ZoneId zone = ZoneId.systemDefault();
        if (mode() == ScheduleMode.AUTO) {
            if (sensorPhase != null) return sensorPhase;
            if (hasCoarseLocation()) {
                double latitude = preferences.getInt(KEY_LATITUDE_TENTHS, 0) / 10.0;
                double longitude = preferences.getInt(KEY_LONGITUDE_TENTHS, 0) / 10.0;
                DayPhase solar = DayPhaseResolver.solar(now, zone, latitude, longitude);
                if (solar != null) return solar;
            }
        }
        return DayPhaseResolver.manual(now, zone, dayMinute(), nightMinute());
    }

    int eligibleCount(DayPhase phase) {
        int count = 0;
        for (File file : WallpaperStore.list(context)) {
            if (profiles.get(file).role.isEligible(phase)) count++;
        }
        return count;
    }

    boolean hasCompletePools() {
        return eligibleCount(DayPhase.DAY) > 0 && eligibleCount(DayPhase.NIGHT) > 0;
    }

    /** Disables scheduling after a library edit makes either pool empty. */
    boolean disableIfIncomplete() {
        if (!isEnabled() || hasCompletePools()) return false;
        preferences.edit().putBoolean(KEY_ENABLED, false)
                .remove(WallpaperEngineService.PREF_LAST_PHASE).apply();
        return true;
    }

    List<File> eligibleFiles(DayPhase phase) {
        List<File> files = WallpaperStore.list(context);
        files.removeIf(file -> !profiles.get(file).role.isEligible(phase));
        return files;
    }
}
