package uk.darkbyte.deckscape;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Locale;

/**
 * Optional BYD adapter backed by Overdrive's read-only localhost telemetry preview.
 *
 * <p>The response also contains values Deckscape does not need. Parsing is an exact allowlist:
 * location, identity, door, trip and other values are ignored, never logged and never persisted.
 * The client cannot follow redirects or connect anywhere except the IPv4 loopback address.</p>
 */
final class OverdriveVehicleTelemetryProvider implements VehicleTelemetryProvider {
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int PORT = 8_080;
    private static final int CONNECT_TIMEOUT_MILLIS = 1_500;
    private static final int READ_TIMEOUT_MILLIS = 2_500;
    private static final int MAX_HEADER_BYTES = 8 * 1_024;
    private static final int MAX_BODY_BYTES = 32 * 1_024;
    private static final int MAX_RESPONSE_BYTES = MAX_HEADER_BYTES + MAX_BODY_BYTES + 4;
    private static final String TELEMETRY_PATH = "/api/mqtt/telemetry";

    private final Context context;

    OverdriveVehicleTelemetryProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isAvailable() {
        return OverdriveBrand.isInstalled(context);
    }

    @Override
    public VehicleTelemetrySnapshot fetch(EnumSet<VehicleTelemetryMetric> metrics,
                                           long nowMillis) throws IOException {
        if (!isAvailable()) throw new IOException("Overdrive is not installed");
        try {
            return parseTelemetry(requestJson(TELEMETRY_PATH), nowMillis);
        } catch (JSONException exception) {
            throw new IOException("Overdrive telemetry is invalid", exception);
        }
    }

    private JSONObject requestJson(String path) throws IOException, JSONException {
        byte[] request = ("GET " + path + " HTTP/1.1\r\n"
                + "Host: " + LOOPBACK_HOST + ":" + PORT + "\r\n"
                + "Accept: application/json\r\n"
                + "Accept-Encoding: identity\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] response;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), PORT),
                    CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream output = socket.getOutputStream();
            output.write(request);
            output.flush();
            response = readBounded(socket.getInputStream());
        }
        return new JSONObject(parseHttpResponse(response));
    }

    static String parseHttpResponse(byte[] response) throws IOException {
        if (response == null || response.length == 0) {
            throw new IOException("Empty localhost response");
        }
        int headerEnd = headerEnd(response);
        if (headerEnd < 0 || headerEnd > MAX_HEADER_BYTES) {
            throw new IOException("Invalid localhost response headers");
        }
        String headers = new String(response, 0, headerEnd, StandardCharsets.US_ASCII);
        String[] lines = headers.split("\\r\\n");
        if (lines.length == 0 || !lines[0].startsWith("HTTP/1.")) {
            throw new IOException("Invalid localhost HTTP status");
        }
        String[] statusParts = lines[0].split(" ", 3);
        if (statusParts.length < 2 || !"200".equals(statusParts[1])) {
            throw new IOException("Localhost HTTP request was not successful");
        }
        int contentLength = -1;
        boolean json = false;
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon <= 0) continue;
            String name = lines[index].substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = lines[index].substring(colon + 1).trim();
            if ("content-length".equals(name)) {
                try {
                    contentLength = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid localhost response length", exception);
                }
            } else if ("content-type".equals(name)) {
                json = value.toLowerCase(Locale.ROOT).startsWith("application/json");
            } else if ("transfer-encoding".equals(name)
                    && !value.equalsIgnoreCase("identity")) {
                throw new IOException("Unsupported localhost transfer encoding");
            }
        }
        if (!json) throw new IOException("Localhost response is not JSON");
        int bodyStart = headerEnd + 4;
        int available = response.length - bodyStart;
        if (contentLength < -1 || contentLength > MAX_BODY_BYTES
                || available > MAX_BODY_BYTES) {
            throw new IOException("Localhost response is too large");
        }
        int bodyLength = contentLength >= 0 ? contentLength : available;
        if (bodyLength <= 0 || available != bodyLength) {
            throw new IOException("Incomplete localhost response body");
        }
        return new String(response, bodyStart, bodyLength, StandardCharsets.UTF_8);
    }

    static VehicleTelemetrySnapshot parseTelemetry(JSONObject root, long nowMillis)
            throws JSONException {
        if (!root.optBoolean("success", false)) {
            throw new JSONException("Telemetry response is unsuccessful");
        }
        JSONObject telemetry = root.optJSONObject("telemetry");
        if (telemetry == null) throw new JSONException("Telemetry payload is missing");
        VehicleTelemetrySnapshot.Builder builder = new VehicleTelemetrySnapshot.Builder();
        builder.fetchedAtMillis = nowMillis;
        builder.vehicleDataReady = telemetry.length() > 0;
        builder.socPercent = number(telemetry, "soc");
        builder.sohPercent = number(telemetry, "soh");
        if (!VehicleTelemetrySnapshot.isNumber(builder.sohPercent)) {
            builder.sohPercent = number(telemetry, "soh_oem");
        }
        builder.remainingKwh = number(telemetry, "capacity");
        builder.voltage12v = number(telemetry, "volt_12v");
        builder.rangeKm = number(telemetry, "ev_range_km");
        if (telemetry.has("is_charging") && !telemetry.isNull("is_charging")) {
            Object charging = telemetry.opt("is_charging");
            builder.charging = charging instanceof Boolean
                    ? (Boolean) charging : telemetry.optInt("is_charging", 0) == 1;
            builder.chargingKnown = true;
        }
        builder.chargingKw = number(telemetry, "charge_power");
        builder.cabinTempC = number(telemetry, "inside_temp");
        if (!VehicleTelemetrySnapshot.isNumber(builder.cabinTempC)) {
            builder.cabinTempC = number(telemetry, "cabin_temp");
        }
        builder.outdoorTempC = number(telemetry, "ext_temp");
        builder.batteryTempC = number(telemetry, "batt_temp");
        if (!VehicleTelemetrySnapshot.isNumber(builder.batteryTempC)) {
            builder.batteryTempC = number(telemetry, "cell_t_avg");
        }
        builder.tyreFrontLeftBar = kpaToBar(number(telemetry, "tyre_p_fl"));
        builder.tyreFrontRightBar = kpaToBar(number(telemetry, "tyre_p_fr"));
        builder.tyreRearLeftBar = kpaToBar(number(telemetry, "tyre_p_rl"));
        builder.tyreRearRightBar = kpaToBar(number(telemetry, "tyre_p_rr"));
        builder.tyreFrontLeftTempC = number(telemetry, "tyre_t_fl");
        builder.tyreFrontRightTempC = number(telemetry, "tyre_t_fr");
        builder.tyreRearLeftTempC = number(telemetry, "tyre_t_rl");
        builder.tyreRearRightTempC = number(telemetry, "tyre_t_rr");
        return builder.build();
    }

    private static double number(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return Double.NaN;
        return object.optDouble(key, Double.NaN);
    }

    private static double kpaToBar(double value) {
        return VehicleTelemetrySnapshot.isNumber(value) ? value / 100.0 : Double.NaN;
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4_096);
        byte[] buffer = new byte[4_096];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("Localhost response is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static int headerEnd(byte[] value) {
        for (int index = 0; index <= value.length - 4; index++) {
            if (value[index] == '\r' && value[index + 1] == '\n'
                    && value[index + 2] == '\r' && value[index + 3] == '\n') {
                return index;
            }
        }
        return -1;
    }
}
