package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Covers Library grouping, especially the intentional overlap of Both-role images. */
public final class LibraryGroupTest {
    @Test
    public void allContainsEveryRole() {
        assertTrue(LibraryGroup.ALL.includes(DayNightRole.BOTH));
        assertTrue(LibraryGroup.ALL.includes(DayNightRole.DAY));
        assertTrue(LibraryGroup.ALL.includes(DayNightRole.NIGHT));
    }

    @Test
    public void dayContainsDayAndBothOnly() {
        assertTrue(LibraryGroup.DAY.includes(DayNightRole.DAY));
        assertTrue(LibraryGroup.DAY.includes(DayNightRole.BOTH));
        assertFalse(LibraryGroup.DAY.includes(DayNightRole.NIGHT));
    }

    @Test
    public void nightContainsNightAndBothOnly() {
        assertTrue(LibraryGroup.NIGHT.includes(DayNightRole.NIGHT));
        assertTrue(LibraryGroup.NIGHT.includes(DayNightRole.BOTH));
        assertFalse(LibraryGroup.NIGHT.includes(DayNightRole.DAY));
    }
}
