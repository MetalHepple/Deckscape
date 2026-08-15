package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

/** Owns the one rounded area shared by Weather and automatic solar scheduling. */
final class SavedAreaSettings {
    private static final String KEY_LATITUDE_TENTHS = "coarse_latitude_tenths";
    private static final String KEY_LONGITUDE_TENTHS = "coarse_longitude_tenths";
    private static final String KEY_LOCATION_TIME = "coarse_location_time";
    private static final String KEY_HAS_LOCATION = "has_coarse_location";
    private static final String KEY_DAILY_REFRESH = "shared_area_daily_refresh";
    private static final String KEY_LAST_REFRESH_ATTEMPT = "shared_area_last_refresh_attempt";

    private final SharedPreferences preferences;

    SavedAreaSettings(Context context) {
        preferences = context.getSharedPreferences(WallpaperEngineService.PREFS,
                Context.MODE_PRIVATE);
    }

    void setLocation(double latitude, double longitude, long acquiredAt) {
        int roundedLatitude = (int) Math.round(Math.max(-90, Math.min(90, latitude)) * 10);
        int roundedLongitude = (int) Math.round(Math.max(-180, Math.min(180, longitude)) * 10);
        preferences.edit()
                .putBoolean(KEY_HAS_LOCATION, true)
                .putInt(KEY_LATITUDE_TENTHS, roundedLatitude)
                .putInt(KEY_LONGITUDE_TENTHS, roundedLongitude)
                .putLong(KEY_LOCATION_TIME, acquiredAt)
                .apply();
    }

    boolean hasLocation() {
        return preferences.getBoolean(KEY_HAS_LOCATION, false);
    }

    long locationTime() {
        return preferences.getLong(KEY_LOCATION_TIME, 0);
    }

    int latitudeTenths() {
        return preferences.getInt(KEY_LATITUDE_TENTHS, 0);
    }

    int longitudeTenths() {
        return preferences.getInt(KEY_LONGITUDE_TENTHS, 0);
    }

    boolean isDailyRefreshEnabled() {
        return preferences.getBoolean(KEY_DAILY_REFRESH, true);
    }

    void setDailyRefreshEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DAILY_REFRESH, enabled).apply();
    }

    long lastRefreshAttempt() {
        return preferences.getLong(KEY_LAST_REFRESH_ATTEMPT, 0);
    }

    void recordRefreshAttempt(long attemptedAt) {
        preferences.edit().putLong(KEY_LAST_REFRESH_ATTEMPT, attemptedAt).apply();
    }
}
