package uk.darkbyte.deckscape;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/** Exercises release selection without making a network request. */
public final class UpdateReleaseTest {
    private static final String DIGEST =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void selectsTheExactlyNamedStableApkAndDigest() throws Exception {
        UpdateRelease release = UpdateRelease.parse(releaseJson(
                "Deckscape-1.4.0.apk", DIGEST, false, false));

        assertEquals("1.4.0", release.versionName);
        assertEquals("Deckscape-1.4.0.apk", release.apk.name);
        assertEquals(4_000_000L, release.apk.size);
        assertNotNull(release.sha256);
    }

    @Test
    public void rejectsUnexpectedApkNamesAndPrereleases() {
        assertThrows(IOException.class, () -> UpdateRelease.parse(releaseJson(
                "app-release.apk", DIGEST, false, false)));
        assertThrows(IOException.class, () -> UpdateRelease.parse(releaseJson(
                "Deckscape-1.4.0.apk", DIGEST, false, true)));
    }

    @Test
    public void acceptsTheMatchingChecksumSidecarWhenDigestIsAbsent() throws Exception {
        UpdateRelease release = UpdateRelease.parse(releaseJson(
                "Deckscape-1.4.0.apk", null, true, false));

        assertNotNull(release.checksum);
        assertEquals("Deckscape-1.4.0.apk.sha256", release.checksum.name);
    }

    private static String releaseJson(String apkName, String digest,
                                      boolean checksum, boolean prerelease) {
        String digestProperty = digest == null ? "" : ",\"digest\":\"" + digest + "\"";
        String checksumAsset = checksum
                ? ", {\"name\":\"Deckscape-1.4.0.apk.sha256\","
                + "\"browser_download_url\":\"https://github.com/MetalHepple/Deckscape/"
                + "releases/download/v1.4.0/Deckscape-1.4.0.apk.sha256\",\"size\":96}"
                : "";
        return "{\"tag_name\":\"v1.4.0\",\"name\":\"Deckscape 1.4.0\","
                + "\"html_url\":\"https://github.com/MetalHepple/Deckscape/releases/tag/v1.4.0\","
                + "\"draft\":false,\"prerelease\":" + prerelease + ",\"assets\":[{"
                + "\"name\":\"" + apkName + "\",\"browser_download_url\":"
                + "\"https://github.com/MetalHepple/Deckscape/releases/download/v1.4.0/"
                + apkName + "\",\"size\":4000000" + digestProperty + "}"
                + checksumAsset + "]}";
    }
}
