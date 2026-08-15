package uk.darkbyte.deckscape;

import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class WeatherClientTest {
    @Test
    public void endpointUsesOnlyLockedHttpsHostAndRoundedCoordinates() throws Exception {
        URL url = WeatherClient.endpoint(515, -1);

        assertEquals("https", url.getProtocol());
        assertEquals("api.open-meteo.com", url.getHost());
        assertEquals(-1, url.getPort());
        assertEquals("/v1/forecast", url.getPath());
        assertTrue(url.getQuery().contains("latitude=51.5"));
        assertTrue(url.getQuery().contains("longitude=-0.1"));
        assertTrue(url.getQuery().contains("current=temperature_2m,weather_code"));
    }

    @Test
    public void endpointRejectsCoordinatesOutsideRoundedWorldBounds() {
        assertThrows(IOException.class, () -> WeatherClient.endpoint(901, 0));
        assertThrows(IOException.class, () -> WeatherClient.endpoint(0, -1801));
    }

    @Test
    public void parsesBoundedCurrentConditions() throws Exception {
        WeatherSnapshot snapshot = WeatherClient.parse(
                "{\"current\":{\"temperature_2m\":18.6,\"weather_code\":2}}",
                515, -1, 123_456L);

        assertEquals(515, snapshot.latitudeTenths);
        assertEquals(-1, snapshot.longitudeTenths);
        assertEquals(18.6, snapshot.temperatureCelsius, 0.001);
        assertEquals(2, snapshot.weatherCode);
        assertEquals(123_456L, snapshot.fetchedAtMillis);
    }

    @Test
    public void rejectsMissingMalformedAndOutOfRangeConditions() {
        assertThrows(IOException.class,
                () -> WeatherClient.parse("{}", 0, 0, 1));
        assertThrows(IOException.class,
                () -> WeatherClient.parse("not json", 0, 0, 1));
        assertThrows(IOException.class,
                () -> WeatherClient.parse(
                        "{\"current\":{\"temperature_2m\":101,\"weather_code\":0}}",
                        0, 0, 1));
        assertThrows(IOException.class,
                () -> WeatherClient.parse(
                        "{\"current\":{\"temperature_2m\":12,\"weather_code\":2.5}}",
                        0, 0, 1));
        assertThrows(IOException.class,
                () -> WeatherClient.parse("{\"error\":true}", 0, 0, 1));
    }
}
