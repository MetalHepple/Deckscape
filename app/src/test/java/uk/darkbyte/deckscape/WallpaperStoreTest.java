package uk.darkbyte.deckscape;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;

/** Unit tests for user-facing downloaded-wallpaper names. */
public final class WallpaperStoreTest {
    @Test
    public void displayNameRemovesRepositoryAndRevisionPrefix() {
        File file = new File("elementary_wallpapers-4ee0fba74d79-A_Trail_In_The_Sand.jpg");
        assertEquals("A Trail In The Sand", WallpaperStore.displayName(file));
    }

    @Test
    public void displayNameFallsBackToPlainFilename() {
        assertEquals("local wallpaper", WallpaperStore.displayName(new File("local_wallpaper.png")));
    }

    @Test
    public void displayNameRemovesCatalogPathAndFinalExtension() {
        assertEquals("Blue.Hour.v2", WallpaperStore.displayName("landscapes/Blue.Hour.v2.webp"));
    }

    @Test
    public void displayNamePreservesANameWithoutExtension() {
        assertEquals("Northern Lights", WallpaperStore.displayName("Northern_Lights"));
    }
}
