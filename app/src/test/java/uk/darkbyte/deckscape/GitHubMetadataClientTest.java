package uk.darkbyte.deckscape;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises bounded About metadata parsing without making network requests. */
public class GitHubMetadataClientTest {
    @Test
    public void contributorsExcludeBots() throws Exception {
        List<RepositoryMetadata.Contributor> values = GitHubMetadataClient.parseContributors(
                "[{\"login\":\"MetalHepple\",\"html_url\":\"https://github.com/MetalHepple\",\"contributions\":9},"
                        + "{\"login\":\"dependabot[bot]\",\"type\":\"Bot\",\"contributions\":2}]");
        assertEquals(1, values.size());
        assertEquals("MetalHepple", values.get(0).login);
        assertEquals(9, values.get(0).contributions);
    }

    @Test
    public void repositoryLicenseParsesSpdxAndSafeLink() throws Exception {
        RepositoryMetadata.SourceLicense value = GitHubMetadataClient.parseLicense(
                "{\"license\":{\"key\":\"mit\",\"name\":\"MIT License\","
                        + "\"spdx_id\":\"MIT\",\"html_url\":\"https://github.com/x/y/blob/main/LICENSE\"}}",
                "x", "y", "https://github.com/x/y");
        assertTrue(value.detected);
        assertEquals("MIT", value.spdxId);
        assertEquals("https://github.com/x/y/blob/main/LICENSE", value.pageUrl);
    }

    @Test
    public void absentRepositoryLicenseIsExplicit() throws Exception {
        RepositoryMetadata.SourceLicense value = GitHubMetadataClient.parseLicense(
                "{\"license\":null}", "x", "y", "https://github.com/x/y");
        assertFalse(value.detected);
        assertEquals("No repository licence detected", value.licenseName);
    }
}
