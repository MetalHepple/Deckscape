package uk.darkbyte.horizondeck;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class RepositorySourceParserTest {
    @Test
    public void parsesShortRepositoryName() {
        RepositorySourceParser.ParsedSource source = RepositorySourceParser.parse("owner/repo");
        assertEquals("owner", source.owner);
        assertEquals("repo", source.repository);
        assertEquals("", source.branch);
        assertEquals("", source.path);
    }

    @Test
    public void parsesTreeUrlAndFolder() {
        RepositorySourceParser.ParsedSource source = RepositorySourceParser.parse(
                "https://github.com/KDE/breeze/tree/master/wallpapers/Next/contents");
        assertEquals("KDE", source.owner);
        assertEquals("breeze", source.repository);
        assertEquals("master", source.branch);
        assertEquals("wallpapers/Next/contents", source.path);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonGitHubHost() {
        RepositorySourceParser.parse("https://example.com/owner/repo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTraversal() {
        RepositorySource.normalizePath("images/../private");
    }
}
