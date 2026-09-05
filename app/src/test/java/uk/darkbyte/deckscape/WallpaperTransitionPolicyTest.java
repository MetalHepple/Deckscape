package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WallpaperTransitionPolicyTest {
    @Test
    public void onlyCrossfadesBoundedVisibleStaticWallpapers() {
        assertTrue(WallpaperTransitionPolicy.shouldAnimate(
                true, false, true, true, true, 1920, 1080));
        assertFalse(WallpaperTransitionPolicy.shouldAnimate(
                true, true, true, true, true, 1920, 1080));
        assertFalse(WallpaperTransitionPolicy.shouldAnimate(
                true, false, true, true, false, 1920, 1080));
        assertFalse(WallpaperTransitionPolicy.shouldAnimate(
                true, false, true, true, true, 4000, 2000));
    }

    @Test
    public void outgoingFrameFadesSmoothlyAndEndsExactly() {
        assertEquals(255, WallpaperTransitionPolicy.outgoingAlpha(0));
        int middle = WallpaperTransitionPolicy.outgoingAlpha(
                WallpaperTransitionPolicy.DURATION_MILLIS / 2);
        assertTrue(middle > 100 && middle < 155);
        assertEquals(0, WallpaperTransitionPolicy.outgoingAlpha(
                WallpaperTransitionPolicy.DURATION_MILLIS));
    }
}
