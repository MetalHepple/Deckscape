package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies construction and strict origin checks for reduced-preview URLs. */
public final class PreviewUrlTest {
    @Test
    public void buildsBoundedJpegPreviewUrl() {
        RepositorySource source = new RepositorySource("Wallz", "fr0st-xyz", "wallz",
                "main", "", true);
        CatalogItem item = new CatalogItem("06. Animated.gif", "Animated/06. Animated.gif",
                "file", 355_000L, "abc123");

        String value = PreviewUrl.forItem(source, item);

        assertTrue(PreviewUrl.isAllowedEndpoint(value));
        assertTrue(value.contains("raw.githubusercontent.com%2Ffr0st-xyz%2Fwallz"));
        assertTrue(value.contains("w=480&h=270&fit=cover&output=jpg&q=82"));
    }

    @Test
    public void rejectsLookalikeAndClearTextHosts() {
        assertFalse(PreviewUrl.isAllowedEndpoint("https://wsrv.nl.example/"));
        assertFalse(PreviewUrl.isAllowedEndpoint("http://wsrv.nl/"));
        assertFalse(PreviewUrl.isAllowedEndpoint("https://wsrv.nl:444/"));
    }
}
