package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class UpdateRulesTest {
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void acceptsOnlyTheFixedLatestReleaseApi() {
        assertTrue(UpdateRules.isAllowedApiUrl(UpdateRules.API_URL));
        assertFalse(UpdateRules.isAllowedApiUrl(
                "https://api.github.com/repos/another/project/releases/latest"));
        assertFalse(UpdateRules.isAllowedApiUrl(
                "http://api.github.com/repos/MetalHepple/Deckscape/releases/latest"));
        assertFalse(UpdateRules.isAllowedApiUrl(UpdateRules.API_URL + "?unexpected=true"));
    }

    @Test
    public void acceptsRepositoryAssetsAndGitHubAssetRedirects() {
        assertTrue(UpdateRules.isAllowedReleaseUrl(
                "https://github.com/MetalHepple/Deckscape/releases/download/v1.4.0/Deckscape-1.4.0.apk"));
        assertTrue(UpdateRules.isAllowedReleaseUrl(
                "https://release-assets.githubusercontent.com/github-production-release-asset/file.apk?token=x"));
        assertFalse(UpdateRules.isAllowedReleaseUrl(
                "https://github.com/other/Deckscape/releases/download/v1.4.0/app.apk"));
        assertFalse(UpdateRules.isAllowedReleaseUrl("https://example.com/Deckscape.apk"));
        assertFalse(UpdateRules.isAllowedReleaseUrl(
                "https://github.com/MetalHepple/Deckscape/releases/download/v1.4.0/app.apk#fragment"));
    }

    @Test
    public void parsesApiAndChecksumFileDigests() {
        String upper = DIGEST.toUpperCase();
        assertEquals(upper, UpdateRules.sha256("sha256:" + DIGEST));
        assertEquals(upper, UpdateRules.sha256(DIGEST + "  Deckscape-1.4.0.apk\n"));
        assertNull(UpdateRules.sha256("not-a-checksum"));
    }

    @Test
    public void validatesOnlyDeckscapeReleasePages() {
        assertTrue(UpdateRules.isAllowedReleasePage(
                "https://github.com/MetalHepple/Deckscape/releases/tag/v1.4.0"));
        assertTrue(UpdateRules.isAllowedReleasePage(
                "https://github.com/MetalHepple/Deckscape/releases/latest"));
        assertFalse(UpdateRules.isAllowedReleasePage(
                "https://github.com/MetalHepple/Other/releases/tag/v1.4.0"));
        assertFalse(UpdateRules.isAllowedReleasePage(
                "https://github.com/MetalHepple/Deckscape/releases/tag/v1.4.0?redirect=other"));
    }
}
