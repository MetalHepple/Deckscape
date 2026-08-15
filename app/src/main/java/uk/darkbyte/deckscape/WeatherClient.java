package uk.darkbyte.deckscape;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Strict, bounded client for the single Open-Meteo endpoint used by wallpaper weather. */
final class WeatherClient {
    static final String HOST = "api.open-meteo.com";
    private static final int MAXIMUM_RESPONSE_BYTES = 64 * 1024;
    private static final int TIMEOUT_MILLIS = 8_000;

    private final AtomicReference<HttpURLConnection> activeConnection =
            new AtomicReference<>();

    WeatherSnapshot fetch(int latitudeTenths, int longitudeTenths, long fetchedAtMillis)
            throws IOException {
        URL endpoint = endpoint(latitudeTenths, longitudeTenths);
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Deckscape/" + BuildConfig.VERSION_NAME);
        activeConnection.set(connection);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Weather service returned HTTP " + status);
            }
            String contentType = connection.getContentType();
            if (contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
                throw new IOException("Weather service returned an unexpected response type");
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAXIMUM_RESPONSE_BYTES) {
                throw new IOException("Weather response is too large");
            }
            try (InputStream input = connection.getInputStream()) {
                return parse(readBounded(input), latitudeTenths, longitudeTenths,
                        fetchedAtMillis);
            }
        } finally {
            activeConnection.compareAndSet(connection, null);
            connection.disconnect();
        }
    }

    void cancel() {
        HttpURLConnection connection = activeConnection.getAndSet(null);
        if (connection != null) connection.disconnect();
    }

    static URL endpoint(int latitudeTenths, int longitudeTenths) throws IOException {
        if (latitudeTenths < -900 || latitudeTenths > 900
                || longitudeTenths < -1800 || longitudeTenths > 1800) {
            throw new IOException("Rounded weather coordinate is out of range");
        }
        String value = String.format(Locale.ROOT,
                "https://%s/v1/forecast?latitude=%.1f&longitude=%.1f"
                        + "&current=temperature_2m,weather_code&forecast_days=1",
                HOST, latitudeTenths / 10.0, longitudeTenths / 10.0);
        URL url = new URL(value);
        if (!"https".equals(url.getProtocol()) || !HOST.equals(url.getHost())
                || url.getPort() != -1 || !"/v1/forecast".equals(url.getPath())) {
            throw new IOException("Invalid weather endpoint");
        }
        return url;
    }

    static WeatherSnapshot parse(String json, int latitudeTenths, int longitudeTenths,
                                 long fetchedAtMillis) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("error", false)) {
                throw new IOException("Weather service rejected the request");
            }
            JSONObject current = root.optJSONObject("current");
            if (current == null) throw new IOException("Weather response has no current data");
            double temperature = current.optDouble("temperature_2m", Double.NaN);
            double codeValue = current.optDouble("weather_code", Double.NaN);
            if (!Double.isFinite(codeValue) || codeValue != Math.rint(codeValue)) {
                throw new IOException("Weather response has an invalid condition code");
            }
            WeatherSnapshot snapshot = new WeatherSnapshot(latitudeTenths, longitudeTenths,
                    temperature, (int) codeValue, fetchedAtMillis);
            if (!snapshot.isValid()) throw new IOException("Weather response is out of range");
            return snapshot;
        } catch (JSONException exception) {
            throw new IOException("Weather response is not valid JSON", exception);
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4_096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAXIMUM_RESPONSE_BYTES) {
                throw new IOException("Weather response is too large");
            }
            output.write(buffer, 0, read);
        }
        if (total == 0) throw new IOException("Weather service returned an empty response");
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
