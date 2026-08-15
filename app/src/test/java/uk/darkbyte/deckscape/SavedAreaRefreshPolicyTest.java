package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SavedAreaRefreshPolicyTest {
    private static final long NOW = 2_000_000_000L;

    @Test
    public void refreshesUsedAreaAfterOneDay() {
        assertFalse(SavedAreaRefreshPolicy.shouldRefresh(true, true, NOW,
                NOW - SavedAreaRefreshPolicy.DAILY_REFRESH_MILLIS + 1));
        assertTrue(SavedAreaRefreshPolicy.shouldRefresh(true, true, NOW,
                NOW - SavedAreaRefreshPolicy.DAILY_REFRESH_MILLIS));
    }

    @Test
    public void disabledOrUnusedAreaDoesNotRefresh() {
        assertFalse(SavedAreaRefreshPolicy.shouldRefresh(false, true, NOW, 0));
        assertFalse(SavedAreaRefreshPolicy.shouldRefresh(true, false, NOW, 0));
    }

    @Test
    public void firstAttemptAndClockCorrectionRefresh() {
        assertTrue(SavedAreaRefreshPolicy.shouldRefresh(true, true, NOW, 0));
        assertTrue(SavedAreaRefreshPolicy.shouldRefresh(true, true, NOW, NOW + 1));
        assertFalse(SavedAreaRefreshPolicy.shouldRefresh(true, true, 0, 0));
    }
}
