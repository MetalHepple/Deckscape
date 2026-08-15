package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WallpaperRedrawSchedulerTest {
    @Test
    public void alignsVisibleClockToNextDeviceMinute() {
        assertEquals(50_000,
                WallpaperRedrawScheduler.staticDelayMillis(10_000, true));
        assertEquals(60_000,
                WallpaperRedrawScheduler.staticDelayMillis(60_000, true));
    }

    @Test
    public void retainsNormalCadenceWithoutClock() {
        assertEquals(15_000,
                WallpaperRedrawScheduler.staticDelayMillis(12_345, false));
    }
}
