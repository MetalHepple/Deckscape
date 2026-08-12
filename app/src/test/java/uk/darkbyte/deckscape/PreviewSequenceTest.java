package uk.darkbyte.deckscape;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies stable, bounded navigation through visible wallpaper results. */
public final class PreviewSequenceTest {
    private static CatalogItem file(String path) {
        return new CatalogItem(path, path, "file", 100, path);
    }

    @Test
    public void directoriesAreSkippedAndSelectedWallpaperIsRetained() {
        CatalogItem first = file("one.jpg");
        CatalogItem selected = file("two.png");
        PreviewSequence sequence = new PreviewSequence(Arrays.asList(
                new CatalogItem("Folder", "Folder", "dir", 0, "folder"),
                first, selected), selected);

        assertEquals(2, sequence.size());
        assertEquals(2, sequence.position());
        assertEquals("two.png", sequence.current().path);
        assertTrue(sequence.hasPrevious());
        assertFalse(sequence.hasNext());
    }

    @Test
    public void navigationStopsAtBothEnds() {
        CatalogItem first = file("one.jpg");
        CatalogItem second = file("two.jpg");
        PreviewSequence sequence = new PreviewSequence(Arrays.asList(first, second), first);

        assertEquals("one.jpg", sequence.previous().path);
        assertEquals("two.jpg", sequence.next().path);
        assertEquals("two.jpg", sequence.next().path);
        assertEquals(2, sequence.position());
    }

    @Test
    public void clickedWallpaperRemainsPreviewableDuringAListRace() {
        CatalogItem selected = file("selected.gif");
        PreviewSequence sequence = new PreviewSequence(Collections.emptyList(), selected);

        assertFalse(sequence.isEmpty());
        assertEquals(1, sequence.size());
        assertEquals("selected.gif", sequence.current().path);
    }
}
