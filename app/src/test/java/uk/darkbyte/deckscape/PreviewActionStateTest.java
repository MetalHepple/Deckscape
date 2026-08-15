package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PreviewActionStateTest {
    @Test
    public void downloadStateOffersGetOnlyWhenAllowed() {
        assertEquals(PreviewActionState.GET,
                PreviewActionState.resolve(false, false, false, true));
        assertEquals(PreviewActionState.GET_UNAVAILABLE,
                PreviewActionState.resolve(false, false, false, false));
    }

    @Test
    public void installedWallpaperOffersSetUntilSelected() {
        assertEquals(PreviewActionState.SET,
                PreviewActionState.resolve(true, false, false, true));
        assertEquals(PreviewActionState.SELECTED,
                PreviewActionState.resolve(true, true, false, true));
    }

    @Test
    public void activeSelectionIsNowShowing() {
        assertEquals(PreviewActionState.NOW_SHOWING,
                PreviewActionState.resolve(true, true, true, true));
    }
}
