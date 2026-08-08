package uk.darkbyte.deckscape;

import java.net.URI;

/** Parses supported GitHub repository shorthand and HTTPS tree URLs. */
final class RepositorySourceParser {
    private RepositorySourceParser() {}

    /** Parses user input without performing a network request. */
    static ParsedSource parse(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a GitHub repository");

        if (!value.contains("://")) value = "https://github.com/" + value;
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("That is not a valid GitHub repository URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("Only public github.com repositories are supported");
        }

        String[] raw = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
        if (raw.length < 3 || raw[1].isEmpty() || raw[2].isEmpty()) {
            throw new IllegalArgumentException("Use owner/repository or a GitHub repository URL");
        }
        String owner = raw[1];
        String repository = raw[2];
        String branch = "";
        String path = "";
        if (raw.length >= 5 && "tree".equals(raw[3])) {
            branch = raw[4];
            StringBuilder builder = new StringBuilder();
            for (int i = 5; i < raw.length; i++) {
                if (raw[i].isEmpty()) continue;
                if (builder.length() > 0) builder.append('/');
                builder.append(raw[i]);
            }
            path = builder.toString();
        } else if (raw.length > 3) {
            throw new IllegalArgumentException("Use the repository root or a /tree/branch/folder URL");
        }

        // RepositorySource performs the strict segment validation.
        RepositorySource probe = new RepositorySource("GitHub source", owner, repository,
                branch.isEmpty() ? "main" : branch, path, false);
        return new ParsedSource(probe.owner, probe.repository, branch, probe.rootPath);
    }

    /** Parsed source components awaiting default-branch and directory validation. */
    static final class ParsedSource {
        final String owner;
        final String repository;
        final String branch;
        final String path;

        ParsedSource(String owner, String repository, String branch, String path) {
            this.owner = owner;
            this.repository = repository;
            this.branch = branch;
            this.path = path;
        }
    }
}
