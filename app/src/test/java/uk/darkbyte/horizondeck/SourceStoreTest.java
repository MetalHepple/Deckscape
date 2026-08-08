package uk.darkbyte.horizondeck;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SourceStoreTest {
    @Test
    public void includesAllCuratedDefaultsWithVerifiedBranchesAndRoots() {
        List<RepositorySource> sources = SourceStore.defaultSources();

        assertEquals(7, sources.size());
        assertSource(sources, "vyrx-dev", "Wallpapers", "master", "");
        assertSource(sources, "D3Ext", "aesthetic-wallpapers", "main", "images");
        assertSource(sources, "JoshuaThadi", "Wall-E-Desk", "main", "");
        assertSource(sources, "ItsTerm1n4l", "Wallpapers", "main", "images");
    }

    private static void assertSource(List<RepositorySource> sources, String owner,
                                     String repository, String branch, String rootPath) {
        for (RepositorySource source : sources) {
            if (owner.equals(source.owner) && repository.equals(source.repository)) {
                assertEquals(branch, source.branch);
                assertEquals(rootPath, source.rootPath);
                assertTrue(source.builtIn);
                return;
            }
        }
        throw new AssertionError("Missing default source " + owner + "/" + repository);
    }
}
