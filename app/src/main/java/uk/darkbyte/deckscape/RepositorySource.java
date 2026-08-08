package uk.darkbyte.deckscape;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** Immutable, validated location within a public GitHub repository. */
final class RepositorySource {
    final String displayName;
    final String owner;
    final String repository;
    final String branch;
    final String rootPath;
    final boolean builtIn;

    RepositorySource(String displayName, String owner, String repository, String branch,
                     String rootPath, boolean builtIn) {
        this.displayName = requireText(displayName, "Source name");
        this.owner = requireSegment(owner, "Owner");
        this.repository = requireSegment(stripDotGit(repository), "Repository");
        this.branch = requireBranch(branch);
        this.rootPath = normalizePath(rootPath);
        this.builtIn = builtIn;
    }

    String id() {
        return (owner + "/" + repository + "@" + branch + ":" + rootPath)
                .toLowerCase(Locale.ROOT);
    }

    String repositoryUrl() {
        return "https://github.com/" + owner + "/" + repository;
    }

    String resolvePath(String relativePath) {
        String relative = normalizePath(relativePath);
        if (rootPath.isEmpty()) return relative;
        if (relative.isEmpty()) return rootPath;
        return rootPath + "/" + relative;
    }

    String relativePath(String absolutePath) {
        String absolute = normalizePath(absolutePath);
        if (rootPath.isEmpty()) return absolute;
        if (absolute.equals(rootPath)) return "";
        String prefix = rootPath + "/";
        return absolute.startsWith(prefix) ? absolute.substring(prefix.length()) : absolute;
    }

    String rawUrl(String absolutePath) {
        String encodedPath = encodePath(normalizePath(absolutePath));
        return "https://raw.githubusercontent.com/"
                + encodePath(owner) + "/" + encodePath(repository) + "/"
                + encodePath(branch) + "/" + encodedPath;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("name", displayName)
                .put("owner", owner)
                .put("repository", repository)
                .put("branch", branch)
                .put("rootPath", rootPath);
    }

    static RepositorySource fromJson(JSONObject object) {
        return new RepositorySource(
                object.optString("name", "GitHub source"),
                object.optString("owner", ""),
                object.optString("repository", ""),
                object.optString("branch", "main"),
                object.optString("rootPath", ""),
                false
        );
    }

    /** Normalizes a repository-relative path while rejecting traversal and control characters. */
    static String normalizePath(String value) {
        if (value == null) return "";
        String path = value.trim().replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty()) return "";
        if (path.length() > 512) throw new IllegalArgumentException("Repository path is too long");
        StringBuilder clean = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) throw new IllegalArgumentException("Repository path cannot contain ..");
            if (containsControl(segment)) throw new IllegalArgumentException("Repository path contains control characters");
            if (clean.length() > 0) clean.append('/');
            clean.append(segment);
        }
        return clean.toString();
    }

    private static String requireSegment(String value, String label) {
        String segment = requireText(value, label);
        if (!segment.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(label + " contains unsupported characters");
        }
        return segment;
    }

    private static String requireBranch(String value) {
        String branch = requireText(value, "Branch");
        if (!branch.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Branches containing slashes are not supported yet");
        }
        return branch;
    }

    private static String requireText(String value, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw new IllegalArgumentException(label + " is required");
        if (containsControl(text)) throw new IllegalArgumentException(label + " contains control characters");
        return text;
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }

    private static String encodePath(String value) {
        try {
            String raw = new URI(null, null, "/" + value, null).getRawPath();
            return raw.substring(1);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Unable to encode repository path", exception);
        }
    }

    private static String stripDotGit(String value) {
        String text = value == null ? "" : value.trim();
        return text.endsWith(".git") ? text.substring(0, text.length() - 4) : text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RepositorySource && id().equals(((RepositorySource) other).id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id());
    }

    @Override
    public String toString() {
        return displayName;
    }
}
