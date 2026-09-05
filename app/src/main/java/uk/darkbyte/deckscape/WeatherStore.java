package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Small private cache that permits an offline weather fallback without background scheduling. */
final class WeatherStore {
    private static final Object REFRESH_LOCK = new Object();
    private static final String PREFERENCES = "weather_cache";
    private static final String KEY_HAS_VALUE = "has_value";
    private static final String KEY_LATITUDE = "latitude_tenths";
    private static final String KEY_LONGITUDE = "longitude_tenths";
    private static final String KEY_TEMPERATURE = "temperature_bits";
    private static final String KEY_CODE = "weather_code";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final String KEY_FORECAST_COUNT = "forecast_count";
    private static final String KEY_ATTEMPT_LATITUDE = "attempt_latitude_tenths";
    private static final String KEY_ATTEMPT_LONGITUDE = "attempt_longitude_tenths";
    private static final String KEY_ATTEMPT_TIME = "attempt_time";

    private final SharedPreferences preferences;

    WeatherStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    WeatherSnapshot read() {
        if (!preferences.getBoolean(KEY_HAS_VALUE, false)) return null;
        int count = preferences.getInt(KEY_FORECAST_COUNT, 0);
        if (count < 0 || count > WeatherSnapshot.MAX_FORECAST_POINTS) return null;
        List<WeatherSnapshot.ForecastPoint> forecast = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            forecast.add(new WeatherSnapshot.ForecastPoint(
                    preferences.getLong(forecastKey("time", index), 0),
                    Double.longBitsToDouble(preferences.getLong(
                            forecastKey("temperature", index), 0)),
                    preferences.getInt(forecastKey("code", index), -1),
                    preferences.getInt(forecastKey("rain", index), -1)));
        }
        WeatherSnapshot snapshot = new WeatherSnapshot(
                preferences.getInt(KEY_LATITUDE, 0),
                preferences.getInt(KEY_LONGITUDE, 0),
                Double.longBitsToDouble(preferences.getLong(KEY_TEMPERATURE, 0)),
                preferences.getInt(KEY_CODE, -1),
                preferences.getLong(KEY_FETCHED_AT, 0), forecast);
        return snapshot.isValid() ? snapshot : null;
    }

    void save(WeatherSnapshot snapshot) {
        if (snapshot == null || !snapshot.isValid()) return;
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_HAS_VALUE, true)
                .putInt(KEY_LATITUDE, snapshot.latitudeTenths)
                .putInt(KEY_LONGITUDE, snapshot.longitudeTenths)
                .putLong(KEY_TEMPERATURE,
                        Double.doubleToRawLongBits(snapshot.temperatureCelsius))
                .putInt(KEY_CODE, snapshot.weatherCode)
                .putLong(KEY_FETCHED_AT, snapshot.fetchedAtMillis)
                .putInt(KEY_FORECAST_COUNT, snapshot.forecast.size());
        for (int index = 0; index < snapshot.forecast.size(); index++) {
            WeatherSnapshot.ForecastPoint point = snapshot.forecast.get(index);
            editor.putLong(forecastKey("time", index), point.timeMillis)
                    .putLong(forecastKey("temperature", index),
                            Double.doubleToRawLongBits(point.temperatureCelsius))
                    .putInt(forecastKey("code", index), point.weatherCode)
                    .putInt(forecastKey("rain", index), point.precipitationProbability);
        }
        editor.apply();
    }

    /** Atomically throttles all active wallpaper engines for the same rounded area. */
    boolean beginRefresh(int latitudeTenths, int longitudeTenths, long nowMillis,
                         long minimumIntervalMillis) {
        synchronized (REFRESH_LOCK) {
            int previousLatitude = preferences.getInt(KEY_ATTEMPT_LATITUDE,
                    Integer.MIN_VALUE);
            int previousLongitude = preferences.getInt(KEY_ATTEMPT_LONGITUDE,
                    Integer.MIN_VALUE);
            long previousTime = preferences.getLong(KEY_ATTEMPT_TIME, 0);
            boolean sameArea = latitudeTenths == previousLatitude
                    && longitudeTenths == previousLongitude;
            if (sameArea && previousTime > 0 && nowMillis >= previousTime
                    && nowMillis - previousTime < minimumIntervalMillis) return false;
            preferences.edit()
                    .putInt(KEY_ATTEMPT_LATITUDE, latitudeTenths)
                    .putInt(KEY_ATTEMPT_LONGITUDE, longitudeTenths)
                    .putLong(KEY_ATTEMPT_TIME, nowMillis)
                    .apply();
            return true;
        }
    }

    private static String forecastKey(String value, int index) {
        return "forecast_" + index + "_" + value;
    }
}
