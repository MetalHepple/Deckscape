package uk.darkbyte.deckscape;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class OverdriveVehicleTelemetryProviderTest {
    @Test
    public void acceptsOnlyACompleteJsonSuccessResponse() throws Exception {
        String body = "{\"status\":\"ok\"}";
        String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                + "\r\nConnection: close\r\n\r\n" + body;
        assertEquals(body, OverdriveVehicleTelemetryProvider.parseHttpResponse(
                response.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rejectsRedirectsAndNonJsonBodies() {
        assertRejected("HTTP/1.1 302 Found\r\nContent-Type: application/json\r\n"
                + "Content-Length: 2\r\n\r\n{}");
        assertRejected("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n"
                + "Content-Length: 2\r\n\r\n{}");
    }

    @Test
    public void parsesOnlyTheAllowlistedVehicleValues() throws Exception {
        VehicleTelemetrySnapshot snapshot =
                OverdriveVehicleTelemetryProvider.parseTelemetry(new JSONObject("{"
                + "\"success\":true,\"telemetry\":{"
                + "\"soc\":95,\"soh\":98,\"capacity\":55.8,"
                + "\"volt_12v\":12.6,\"ev_range_km\":401,"
                + "\"is_charging\":0,\"charge_power\":0,"
                + "\"batt_temp\":31,\"ext_temp\":17,\"inside_temp\":24,"
                + "\"tyre_p_fl\":250,\"tyre_p_fr\":247,"
                + "\"tyre_p_rl\":250,\"tyre_p_rr\":247,"
                + "\"tyre_t_fl\":30,\"tyre_t_fr\":30,"
                + "\"tyre_t_rl\":27,\"tyre_t_rr\":26,"
                + "\"lat\":53.1,\"lon\":-1.2,\"vin\":\"ignored\"}}"), 1_000);
        assertTrue(snapshot.vehicleDataReady);
        assertEquals(95, snapshot.socPercent, 0);
        assertEquals(98, snapshot.sohPercent, 0);
        assertEquals(55.8, snapshot.remainingKwh, 0);
        assertEquals(12.6, snapshot.voltage12v, 0);
        assertEquals(401, snapshot.rangeKm, 0);
        assertEquals(31, snapshot.batteryTempC, 0);
        assertEquals(17, snapshot.outdoorTempC, 0);
        assertEquals(24, snapshot.cabinTempC, 0);
        assertEquals(2.47, snapshot.tyreFrontRightBar, 0.0001);
        assertEquals(30, snapshot.tyreFrontRightTempC, 0);
        assertEquals(26, snapshot.tyreRearRightTempC, 0);
        assertFalse(snapshot.charging);
        assertTrue(snapshot.chargingKnown);
    }

    @Test
    public void invalidSentinelsBecomeUnavailableInsteadOfDisplayValues() {
        VehicleTelemetrySnapshot.Builder builder = new VehicleTelemetrySnapshot.Builder();
        builder.fetchedAtMillis = 10_000;
        builder.socPercent = 255;
        builder.sohPercent = 0;
        builder.cabinTempC = 65_535;
        builder.outdoorTempC = -128;
        builder.batteryTempC = 31;
        VehicleTelemetrySnapshot snapshot = builder.build();
        assertTrue(Double.isNaN(snapshot.socPercent));
        assertTrue(Double.isNaN(snapshot.sohPercent));
        assertTrue(Double.isNaN(snapshot.cabinTempC));
        assertTrue(Double.isNaN(snapshot.outdoorTempC));
        assertEquals(31, snapshot.batteryTempC, 0);
        assertTrue(snapshot.isDisplayable(20_000));
        assertFalse(snapshot.isDisplayable(10_000
                + VehicleTelemetrySnapshot.DISPLAY_MAX_AGE_MILLIS + 1));
    }

    private static void assertRejected(String response) {
        try {
            OverdriveVehicleTelemetryProvider.parseHttpResponse(
                    response.getBytes(StandardCharsets.UTF_8));
            fail("Expected the response to be rejected");
        } catch (IOException expected) {
            // Expected.
        }
    }
}
