package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies repository normalization, identity, and raw URL construction. */
public final class RepositorySourceTest {
    @Test
    public void resolvesConfiguredRoot() {
        RepositorySource source = new RepositorySource("Breeze", "KDE", "breeze",
                "master", "wallpapers/Next", false);
        assertEquals("wallpapers/Next/contents/images", source.resolvePath("contents/images"));
        assertEquals("contents/images", source.relativePath("wallpapers/Next/contents/images"));
    }

    @Test
    public void rawUrlIsHttpsAndEncoded() {
        RepositorySource source = new RepositorySource("Wallz", "fr0st-xyz", "wallz",
                "main", "", true);
        assertEquals("https://raw.githubusercontent.com/fr0st-xyz/wallz/main/Animated/06.%20Animated.gif",
                source.rawUrl("Animated/06. Animated.gif"));
        assertTrue(WallpaperRules.isAllowedRawUrl(source.rawUrl("Animated/06. Animated.gif"), source));
    }
}
