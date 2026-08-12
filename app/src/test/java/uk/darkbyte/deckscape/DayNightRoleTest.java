package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Covers the deterministic role order used by the Library's quick control. */
public final class DayNightRoleTest {
    @Test
    public void nextCyclesThroughBothDayAndNight() {
        assertEquals(DayNightRole.DAY, DayNightRole.BOTH.next());
        assertEquals(DayNightRole.NIGHT, DayNightRole.DAY.next());
        assertEquals(DayNightRole.BOTH, DayNightRole.NIGHT.next());
    }
}
