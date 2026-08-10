package uk.darkbyte.deckscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contributor and repository-licence metadata displayed by the About screens. */
final class RepositoryMetadata {
    final List<Contributor> contributors;
    final Map<String, SourceLicense> licenses;
    final boolean stale;

    RepositoryMetadata(List<Contributor> contributors, Map<String, SourceLicense> licenses,
                       boolean stale) {
        this.contributors = Collections.unmodifiableList(new ArrayList<>(contributors));
        this.licenses = Collections.unmodifiableMap(new LinkedHashMap<>(licenses));
        this.stale = stale;
    }

    static String repositoryKey(String owner, String repository) {
        return (owner + "/" + repository).toLowerCase(java.util.Locale.ROOT);
    }

    /** A public GitHub contributor without avatar or private account information. */
    static final class Contributor {
        final String login;
        final String pageUrl;
        final int contributions;

        Contributor(String login, String pageUrl, int contributions) {
            this.login = login;
            this.pageUrl = pageUrl;
            this.contributions = contributions;
        }
    }

    /** Repository-level licence declaration; individual artwork rights may differ. */
    static final class SourceLicense {
        final String repositoryName;
        final String licenseName;
        final String spdxId;
        final String pageUrl;
        final boolean detected;

        SourceLicense(String repositoryName, String licenseName, String spdxId,
                      String pageUrl, boolean detected) {
            this.repositoryName = repositoryName;
            this.licenseName = licenseName;
            this.spdxId = spdxId;
            this.pageUrl = pageUrl;
            this.detected = detected;
        }
    }
}
