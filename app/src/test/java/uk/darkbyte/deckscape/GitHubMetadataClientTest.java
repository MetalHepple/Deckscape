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
                "[{\"login\":\"alice\",\"html_url\":\"https://github.com/alice\","
                        + "\"avatar_url\":\"https://avatars.githubusercontent.com/u/123?v=4\","
                        + "\"contributions\":9},"
                        + "{\"login\":\"dependabot[bot]\",\"type\":\"Bot\",\"contributions\":2}]");
        assertEquals(1, values.size());
        assertEquals("alice", values.get(0).displayName);
        assertEquals("alice", values.get(0).login);
        assertEquals("alice", values.get(0).displayLabel());
        assertEquals("https://avatars.githubusercontent.com/u/123?v=4",
                values.get(0).avatarUrl);
    }

    @Test
    public void creatorAccountAndAnonymousCommitsBecomeOneFriendlyProfile() throws Exception {
        List<RepositoryMetadata.Contributor> values = GitHubMetadataClient.parseContributors(
                "[{\"name\":\"Paul Hepple\",\"contributions\":3},"
                        + "{\"login\":\"MetalHepple\","
                        + "\"html_url\":\"https://github.com/MetalHepple\","
                        + "\"avatar_url\":\"https://avatars.githubusercontent.com/u/68790254?v=4\","
                        + "\"contributions\":1}]");

        assertEquals(1, values.size());
        RepositoryMetadata.Contributor creator = values.get(0);
        assertEquals("Paul Hepple", creator.displayName);
        assertEquals("MetalHepple", creator.login);
        assertEquals("Paul Hepple (@MetalHepple)", creator.displayLabel());
        assertEquals("https://github.com/MetalHepple", creator.pageUrl);
        assertEquals("https://avatars.githubusercontent.com/u/68790254?v=4",
                creator.avatarUrl);
    }

    @Test
    public void anonymousCreatorUsesKnownPublicProfileAndAvatar() throws Exception {
        List<RepositoryMetadata.Contributor> values = GitHubMetadataClient.parseContributors(
                "[{\"name\":\"Paul Hepple\",\"type\":\"Anonymous\","
                        + "\"contributions\":3}]");

        assertEquals(1, values.size());
        RepositoryMetadata.Contributor creator = values.get(0);
        assertEquals("Paul Hepple (@MetalHepple)", creator.displayLabel());
        assertEquals(AppMetadata.CREATOR_URL, creator.pageUrl);
        assertEquals(AppMetadata.CREATOR_AVATAR_URL, creator.avatarUrl);
    }

    @Test
    public void contributorAvatarUrlsUseOnlyGitHubsNumericAvatarEndpoint() {
        assertTrue(GitHubMetadataClient.isAllowedAvatarUrl(
                "https://avatars.githubusercontent.com/u/68790254?v=4"));
        assertFalse(GitHubMetadataClient.isAllowedAvatarUrl(
                "https://avatars.githubusercontent.com/example.png"));
        assertFalse(GitHubMetadataClient.isAllowedAvatarUrl(
                "https://avatars.githubusercontent.com.evil.test/u/68790254"));
        assertFalse(GitHubMetadataClient.isAllowedAvatarUrl(
                "http://avatars.githubusercontent.com/u/68790254"));
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
