package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Ensures sensor scheduling ignores short shadows and uses hysteresis. */
public class AmbientLightTrackerTest {
    @Test
    public void initialReadingMustRemainStable() {
        AmbientLightTracker tracker = new AmbientLightTracker();
        assertNull(tracker.update(5, 1_000));
        assertNull(tracker.update(5, 1_000 + AmbientLightTracker.INITIAL_SETTLE_MS - 1));
        assertEquals(DayPhase.NIGHT,
                tracker.update(5, 1_000 + AmbientLightTracker.INITIAL_SETTLE_MS));
    }

    @Test
    public void ambiguousReadingsKeepCurrentPhase() {
        AmbientLightTracker tracker = new AmbientLightTracker();
        tracker.update(100, 0);
        tracker.update(100, AmbientLightTracker.INITIAL_SETTLE_MS);
        assertEquals(DayPhase.DAY, tracker.update(50, 999_999));
    }

    @Test
    public void briefTunnelDoesNotChangeDayToNight() {
        AmbientLightTracker tracker = new AmbientLightTracker();
        tracker.update(100, 0);
        tracker.update(100, AmbientLightTracker.INITIAL_SETTLE_MS);
        assertEquals(DayPhase.DAY, tracker.update(5, 70_000));
        assertEquals(DayPhase.DAY, tracker.update(100, 80_000));
    }
}
