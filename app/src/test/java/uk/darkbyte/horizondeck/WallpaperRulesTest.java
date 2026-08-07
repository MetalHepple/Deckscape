package uk.darkbyte.horizondeck;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WallpaperRulesTest {
    private final RepositorySource wallz = new RepositorySource("Wallz", "fr0st-xyz",
            "wallz", "main", "", true);

    @Test
    public void supportsStaticAndGifFormats() {
        assertTrue(WallpaperRules.isSupportedName("road.JPG"));
        assertTrue(WallpaperRules.isSupportedName("road.webp"));
        assertTrue(WallpaperRules.isSupportedName("road.gif"));
        assertFalse(WallpaperRules.isSupportedName("movie.mp4"));
    }

    @Test
    public void rawUrlMustMatchSelectedRepository() {
        assertTrue(WallpaperRules.isAllowedRawUrl(
                "https://raw.githubusercontent.com/fr0st-xyz/wallz/main/A/a.jpg", wallz));
        assertFalse(WallpaperRules.isAllowedRawUrl(
                "https://raw.githubusercontent.com/other/wallz/main/A/a.jpg", wallz));
        assertFalse(WallpaperRules.isAllowedRawUrl("http://raw.githubusercontent.com/x/y/z", wallz));
    }

    @Test
    public void appliesIndependentDownloadCaps() {
        assertEquals(12L * 1024 * 1024, WallpaperRules.maxBytesFor("loop.gif"));
        assertEquals(40L * 1024 * 1024, WallpaperRules.maxBytesFor("still.png"));
    }

    @Test
    public void sanitizesStoredFileNames() {
        assertEquals("06._Animated.gif", WallpaperRules.safeFileName("06. Animated.gif"));
    }
}
