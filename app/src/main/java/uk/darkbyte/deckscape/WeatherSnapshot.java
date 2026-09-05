package uk.darkbyte.deckscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable current and short-forecast result tied to the rounded request coordinate. */
final class WeatherSnapshot {
    static final int MAX_FORECAST_POINTS = 4;
    private static final long FORECAST_PAST_TOLERANCE_MILLIS = 2 * 60 * 60_000L;
    private static final long FORECAST_FUTURE_LIMIT_MILLIS = 12 * 60 * 60_000L;

    static final class ForecastPoint {
        final long timeMillis;
        final double temperatureCelsius;
        final int weatherCode;
        final int precipitationProbability;

        ForecastPoint(long timeMillis, double temperatureCelsius, int weatherCode,
                      int precipitationProbability) {
            this.timeMillis = timeMillis;
            this.temperatureCelsius = temperatureCelsius;
            this.weatherCode = weatherCode;
            this.precipitationProbability = precipitationProbability;
        }

        boolean isValid() {
            return timeMillis > 0
                    && Double.isFinite(temperatureCelsius)
                    && temperatureCelsius >= -100 && temperatureCelsius <= 100
                    && weatherCode >= 0 && weatherCode <= 99
                    && precipitationProbability >= 0 && precipitationProbability <= 100;
        }
    }

    final int latitudeTenths;
    final int longitudeTenths;
    final double temperatureCelsius;
    final int weatherCode;
    final long fetchedAtMillis;
    final List<ForecastPoint> forecast;

    WeatherSnapshot(int latitudeTenths, int longitudeTenths, double temperatureCelsius,
                    int weatherCode, long fetchedAtMillis) {
        this(latitudeTenths, longitudeTenths, temperatureCelsius, weatherCode,
                fetchedAtMillis, Collections.emptyList());
    }

    WeatherSnapshot(int latitudeTenths, int longitudeTenths, double temperatureCelsius,
                    int weatherCode, long fetchedAtMillis, List<ForecastPoint> forecast) {
        this.latitudeTenths = latitudeTenths;
        this.longitudeTenths = longitudeTenths;
        this.temperatureCelsius = temperatureCelsius;
        this.weatherCode = weatherCode;
        this.fetchedAtMillis = fetchedAtMillis;
        this.forecast = Collections.unmodifiableList(forecast == null
                ? Collections.emptyList() : new ArrayList<>(forecast));
    }

    boolean matches(int latitude, int longitude) {
        return latitudeTenths == latitude && longitudeTenths == longitude;
    }

    boolean isValid() {
        return latitudeTenths >= -900 && latitudeTenths <= 900
                && longitudeTenths >= -1800 && longitudeTenths <= 1800
                && Double.isFinite(temperatureCelsius)
                && temperatureCelsius >= -100 && temperatureCelsius <= 100
                && weatherCode >= 0 && weatherCode <= 99
                && fetchedAtMillis > 0
                && isForecastValid();
    }

    private boolean isForecastValid() {
        if (forecast.size() > MAX_FORECAST_POINTS) return false;
        long previousTime = 0;
        for (ForecastPoint point : forecast) {
            if (point == null || !point.isValid() || point.timeMillis <= previousTime
                    || point.timeMillis < fetchedAtMillis - FORECAST_PAST_TOLERANCE_MILLIS
                    || point.timeMillis > fetchedAtMillis + FORECAST_FUTURE_LIMIT_MILLIS) {
                return false;
            }
            previousTime = point.timeMillis;
        }
        return true;
    }
}
