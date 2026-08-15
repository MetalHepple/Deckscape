package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies manual and timed wallpaper-rotation rules. */
public final class RotationPolicyTest {
    @Test
    public void zeroIntervalKeepsTheSelectedWallpaperFixed() {
        assertFalse(RotationPolicy.isSlideshowEnabled(0));
        assertFalse(RotationPolicy.isSlideshowEnabled(-1));
        assertTrue(RotationPolicy.isSlideshowEnabled(60_000));
    }

    @Test
    public void rotatesOnlyAfterIntervalWithMultipleItems() {
        assertTrue(RotationPolicy.shouldRotate(61_000, 1_000, 60_000, 2));
        assertFalse(RotationPolicy.shouldRotate(60_999, 1_000, 60_000, 2));
        assertFalse(RotationPolicy.shouldRotate(61_000, 1_000, 60_000, 1));
        assertFalse(RotationPolicy.shouldRotate(61_000, 1_000, 0, 2));
    }

    @Test
    public void wrapsAndNormalizesIndex() {
        assertEquals(0, RotationPolicy.nextIndex(2, 3));
        assertEquals(0, RotationPolicy.nextIndex(-1, 3));
        assertEquals(0, RotationPolicy.nextIndex(8, 0));
    }
}
