package uk.darkbyte.deckscape;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WeatherSnapshotTest {
    @Test
    public void validSnapshotMatchesOnlyItsRoundedCoordinate() {
        WeatherSnapshot snapshot = new WeatherSnapshot(515, -1, 18.5, 3, 100L);

        assertTrue(snapshot.isValid());
        assertTrue(snapshot.matches(515, -1));
        assertFalse(snapshot.matches(516, -1));
    }

    @Test
    public void rejectsUnsafeValues() {
        assertFalse(new WeatherSnapshot(901, 0, 10, 0, 1).isValid());
        assertFalse(new WeatherSnapshot(0, 0, Double.NaN, 0, 1).isValid());
        assertFalse(new WeatherSnapshot(0, 0, 10, 100, 1).isValid());
        assertFalse(new WeatherSnapshot(0, 0, 10, 0, 0).isValid());
    }

    @Test
    public void validatesOrderedForecastPoints() {
        WeatherSnapshot valid = new WeatherSnapshot(0, 0, 10, 0, 1_000,
                Arrays.asList(
                        new WeatherSnapshot.ForecastPoint(1_000, 10, 1, 20),
                        new WeatherSnapshot.ForecastPoint(2_000, 11, 2, 30)));
        WeatherSnapshot unordered = new WeatherSnapshot(0, 0, 10, 0, 1_000,
                Arrays.asList(
                        new WeatherSnapshot.ForecastPoint(2_000, 10, 1, 20),
                        new WeatherSnapshot.ForecastPoint(1_000, 11, 2, 30)));
        WeatherSnapshot badRain = new WeatherSnapshot(0, 0, 10, 0, 1_000,
                Arrays.asList(new WeatherSnapshot.ForecastPoint(1_000, 10, 1, 101)));

        assertTrue(valid.isValid());
        assertFalse(unordered.isValid());
        assertFalse(badRain.isValid());
    }
}
