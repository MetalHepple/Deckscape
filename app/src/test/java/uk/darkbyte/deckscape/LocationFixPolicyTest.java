package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies that cached location fixes cannot silently remain current indefinitely. */
public final class LocationFixPolicyTest {
    private static final long NOW = 2_000_000_000L;

    @Test
    public void recentFixIsAcceptedImmediately() {
        assertTrue(LocationFixPolicy.isRecent(NOW, NOW - 60_000,
                LocationFixPolicy.IMMEDIATE_CACHE_AGE_MS));
    }

    @Test
    public void weekOldFixCannotBeUsedAsFallback() {
        assertFalse(LocationFixPolicy.isRecent(NOW,
                NOW - LocationFixPolicy.FALLBACK_CACHE_AGE_MS - 1,
                LocationFixPolicy.FALLBACK_CACHE_AGE_MS));
    }

    @Test
    public void smallClockCorrectionIsToleratedButInvalidTimesAreRejected() {
        assertTrue(LocationFixPolicy.isRecent(NOW, NOW + 60_000,
                LocationFixPolicy.IMMEDIATE_CACHE_AGE_MS));
        assertFalse(LocationFixPolicy.isRecent(NOW, 0,
                LocationFixPolicy.IMMEDIATE_CACHE_AGE_MS));
        assertFalse(LocationFixPolicy.isRecent(NOW, NOW + 10 * 60_000,
                LocationFixPolicy.IMMEDIATE_CACHE_AGE_MS));
    }
}
