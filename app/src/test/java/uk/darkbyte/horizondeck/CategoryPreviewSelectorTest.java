package uk.darkbyte.horizondeck;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class CategoryPreviewSelectorTest {
    @Test
    public void attachesImageFromInsideEachCategory() {
        CatalogItem anime = new CatalogItem("Anime", "Anime", "dir", 0, "dir-a");
        CatalogItem nature = new CatalogItem("Nature", "Nature", "dir", 0, "dir-n");
        CatalogItem rootImage = image("cover.jpg", "cover.jpg", 100_000);
        CatalogItem animeImage = image("sky.jpg", "Anime/sky.jpg", 200_000);
        CatalogItem natureImage = image("lake.png", "Nature/landscape/lake.png", 300_000);

        List<CatalogItem> result = CategoryPreviewSelector.attach(
                Arrays.asList(anime, nature, rootImage),
                Arrays.asList(rootImage, animeImage, natureImage));

        assertEquals("Anime/sky.jpg", result.get(0).preview.path);
        assertEquals("Nature/landscape/lake.png", result.get(1).preview.path);
        assertNull(result.get(2).preview);
    }

    @Test
    public void prefersDirectStaticImageOverNestedOrGif() {
        CatalogItem category = new CatalogItem("Live", "Live", "dir", 0, "dir");
        CatalogItem nested = image("a.jpg", "Live/nested/a.jpg", 100_000);
        CatalogItem gif = image("a.gif", "Live/a.gif", 100_000);
        CatalogItem direct = image("z.jpg", "Live/z.jpg", 100_000);

        CatalogItem result = CategoryPreviewSelector.attach(
                Arrays.asList(category), Arrays.asList(nested, gif, direct)).get(0);

        assertEquals("Live/z.jpg", result.preview.path);
    }

    private static CatalogItem image(String name, String path, long size) {
        return new CatalogItem(name, path, "file", size, path + "-sha");
    }
}
