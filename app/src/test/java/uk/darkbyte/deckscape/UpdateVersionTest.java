package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public final class UpdateVersionTest {
    @Test
    public void normalizesReleaseTags() {
        assertEquals("1.4.0", UpdateVersion.normalize("v1.4.0"));
        assertEquals("12.0.31", UpdateVersion.normalize(" 12.0.31 "));
    }

    @Test
    public void comparesEachNumericComponent() {
        assertTrue(UpdateVersion.compare("1.10.0", "1.9.9") > 0);
        assertTrue(UpdateVersion.compare("2.0.0", "1.99.99") > 0);
        assertEquals(0, UpdateVersion.compare("v1.4.0", "1.4.0"));
    }

    @Test
    public void rejectsPrereleaseOrIncompleteVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateVersion.normalize("1.4"));
        assertThrows(IllegalArgumentException.class,
                () -> UpdateVersion.normalize("1.4.0-beta"));
        assertThrows(IllegalArgumentException.class,
                () -> UpdateVersion.normalize("latest"));
    }
}
