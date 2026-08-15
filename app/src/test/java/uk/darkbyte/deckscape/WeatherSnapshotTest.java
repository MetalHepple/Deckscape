package uk.darkbyte.deckscape;

import org.junit.Test;

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
}
