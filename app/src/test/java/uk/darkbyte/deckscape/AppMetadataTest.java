package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Tests product identity derived from the generated Gradle build metadata. */
public final class AppMetadataTest {
    @Test
    public void applicationIdUsesDeckscapeNamespace() {
        assertEquals("uk.darkbyte.deckscape", BuildConfig.APPLICATION_ID);
    }

    @Test
    public void versionLabelIncludesConfiguredVersion() {
        assertEquals("Deckscape " + BuildConfig.VERSION_NAME, AppMetadata.versionLabel());
    }

    @Test
    public void userAgentIsVersionedAndPlatformSpecific() {
        String userAgent = AppMetadata.userAgent();
        assertTrue(userAgent.startsWith("Deckscape/" + BuildConfig.VERSION_NAME));
        assertTrue(userAgent.endsWith("(Android)"));
    }
}
