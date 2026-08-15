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
    private static final String KEY_ASSIGNMENT_MODE = "day_night_assignment_mode";
    private static final String KEY_DAY_MINUTE = "day_start_minute";
    private static final String KEY_NIGHT_MINUTE = "night_start_minute";
    private static final String KEY_DEFAULT_SCALE = "default_scale_mode";

    private final Context context;
    private final SharedPreferences preferences;
    private final WallpaperProfileStore profiles;
    private final SavedAreaSettings savedArea;

    DayNightSettings(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(WallpaperEngineService.PREFS,
                Context.MODE_PRIVATE);
        profiles = new WallpaperProfileStore(context);
        savedArea = new SavedAreaSettings(context);
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

    DayNightAssignmentMode assignmentMode() {
        return DayNightAssignmentMode.parse(preferences.getString(KEY_ASSIGNMENT_MODE,
                DayNightAssignmentMode.MANUAL.name()));
    }

    void setAssignmentMode(DayNightAssignmentMode mode) {
        preferences.edit().putString(KEY_ASSIGNMENT_MODE,
                (mode == null ? DayNightAssignmentMode.MANUAL : mode).name()).apply();
    }

    DayNightRole effectiveRole(File file) {
        WallpaperProfile profile = profiles.get(file);
        return assignmentMode() == DayNightAssignmentMode.AUTO
                ? profile.automaticRole : profile.role;
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

    /** Returns today's local solar boundaries from the same inputs used by the engine. */
    DayPhaseResolver.SolarTimes solarTimes(long nowMillis) {
        if (!savedArea.hasLocation()) return null;
        double latitude = savedArea.latitudeTenths() / 10.0;
        double longitude = savedArea.longitudeTenths() / 10.0;
        return DayPhaseResolver.solarTimes(Instant.ofEpochMilli(nowMillis),
                ZoneId.systemDefault(), latitude, longitude);
    }

    DayPhase currentPhase(long nowMillis, DayPhase sensorPhase) {
        Instant now = Instant.ofEpochMilli(nowMillis);
        ZoneId zone = ZoneId.systemDefault();
        if (mode() == ScheduleMode.AUTO) {
            if (sensorPhase != null) return sensorPhase;
            if (savedArea.hasLocation()) {
                double latitude = savedArea.latitudeTenths() / 10.0;
                double longitude = savedArea.longitudeTenths() / 10.0;
                DayPhase solar = DayPhaseResolver.solar(now, zone, latitude, longitude);
                if (solar != null) return solar;
            }
        }
        return DayPhaseResolver.manual(now, zone, dayMinute(), nightMinute());
    }

    int eligibleCount(DayPhase phase) {
        int count = 0;
        for (File file : WallpaperStore.list(context)) {
            if (effectiveRole(file).isEligible(phase)) count++;
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
        files.removeIf(file -> !effectiveRole(file).isEligible(phase));
        return files;
    }
}
