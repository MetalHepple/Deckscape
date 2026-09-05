package uk.darkbyte.deckscape;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertTrue;

public final class WidgetDiagnosticsTest {
    @Test
    public void identifiesTheExactMissingTemperatureReading() {
        VehicleTelemetrySnapshot.Builder builder = new VehicleTelemetrySnapshot.Builder();
        builder.fetchedAtMillis = 100_000;
        builder.outdoorTempC = 12;
        builder.batteryTempC = 24;
        String value = WidgetDiagnostics.describe(
                EnumSet.of(OverlayWidget.VEHICLE_TEMPERATURES), false, null,
                true, builder.build(), 120_000);

        assertTrue(value.contains("Temperatures: cabin unavailable"));
    }

    @Test
    public void distinguishesMissingAreaFromMissingForecast() {
        String missingArea = WidgetDiagnostics.describe(EnumSet.of(OverlayWidget.FORECAST),
                false, null, false, null, 1);
        WeatherSnapshot currentOnly = new WeatherSnapshot(0, 0, 12, 2, 1);
        String missingForecast = WidgetDiagnostics.describe(EnumSet.of(OverlayWidget.FORECAST),
                true, currentOnly, false, null, 1);

        assertTrue(missingArea.contains("saved area needed"));
        assertTrue(missingForecast.contains("forecast unavailable"));
    }
}
