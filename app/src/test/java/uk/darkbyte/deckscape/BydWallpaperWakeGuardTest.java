package uk.darkbyte.deckscape;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BydWallpaperWakeGuardTest {
    @Test
    public void startsOnlyForVisibleActiveWallpaperOnEligibleHardware() {
        assertTrue(BydWallpaperWakeGuard.shouldStart(
                true, true, false, false, false));
        assertFalse(BydWallpaperWakeGuard.shouldStart(
                false, true, false, false, false));
        assertFalse(BydWallpaperWakeGuard.shouldStart(
                true, false, false, false, false));
        assertFalse(BydWallpaperWakeGuard.shouldStart(
                true, true, true, false, false));
    }

    @Test
    public void doesNotOverlapOrRestartAfterClose() {
        assertFalse(BydWallpaperWakeGuard.shouldStart(
                true, true, false, true, false));
        assertFalse(BydWallpaperWakeGuard.shouldStart(
                true, true, false, false, true));
    }

    @Test
    public void nextWakeIsEligibleImmediatelyAfterThePreviousWindowCompletes() {
        assertFalse(BydWallpaperWakeGuard.shouldStart(
                true, true, false, true, false));
        assertTrue(BydWallpaperWakeGuard.shouldStart(
                true, true, false, false, false));
    }
}
