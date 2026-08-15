package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DayNightAssignmentModeTest {
    @Test
    public void missingOrUnknownValuesStayManual() {
        assertEquals(DayNightAssignmentMode.MANUAL, DayNightAssignmentMode.parse(null));
        assertEquals(DayNightAssignmentMode.MANUAL, DayNightAssignmentMode.parse("UNKNOWN"));
    }

    @Test
    public void autoValueRoundTrips() {
        assertEquals(DayNightAssignmentMode.AUTO,
                DayNightAssignmentMode.parse(DayNightAssignmentMode.AUTO.name()));
    }
}
