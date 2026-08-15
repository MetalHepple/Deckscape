package uk.darkbyte.deckscape;

/** Immutable current-weather result tied to the rounded coordinate used for its request. */
final class WeatherSnapshot {
    final int latitudeTenths;
    final int longitudeTenths;
    final double temperatureCelsius;
    final int weatherCode;
    final long fetchedAtMillis;

    WeatherSnapshot(int latitudeTenths, int longitudeTenths, double temperatureCelsius,
                    int weatherCode, long fetchedAtMillis) {
        this.latitudeTenths = latitudeTenths;
        this.longitudeTenths = longitudeTenths;
        this.temperatureCelsius = temperatureCelsius;
        this.weatherCode = weatherCode;
        this.fetchedAtMillis = fetchedAtMillis;
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
                && fetchedAtMillis > 0;
    }
}
