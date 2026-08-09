package uk.darkbyte.deckscape;

import java.net.URI;
import java.util.Locale;

/** Centralizes the fixed repository, transport, size, and checksum update policy. */
final class UpdateRules {
    static final String OWNER = "MetalHepple";
    static final String REPOSITORY = "Deckscape";
    static final String API_URL = "https://api.github.com/repos/MetalHepple/Deckscape/releases/latest";
    static final long MAX_RELEASE_JSON_BYTES = 1024L * 1024L;
    static final long MAX_CHECKSUM_BYTES = 8L * 1024L;
    static final long MAX_APK_BYTES = 25L * 1024L * 1024L;

    private static final String RELEASE_PATH_PREFIX =
            "/" + OWNER + "/" + REPOSITORY + "/releases/download/";

    private UpdateRules() {}

    static boolean isAllowedApiUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "api.github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && uri.getPort() == -1
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && ("/repos/" + OWNER + "/" + REPOSITORY + "/releases/latest")
                    .equals(uri.getPath());
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean isAllowedReleaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1) return false;
            if (uri.getRawFragment() != null) return false;
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ("github.com".equals(host)) return uri.getPath().startsWith(RELEASE_PATH_PREFIX);
            return "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host)
                    || "github-releases.githubusercontent.com".equals(host);
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean isAllowedReleasePage(String value) {
        try {
            URI uri = URI.create(value);
            String prefix = "/" + OWNER + "/" + REPOSITORY + "/releases/";
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && uri.getPort() == -1
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getPath().startsWith(prefix);
        } catch (Exception exception) {
            return false;
        }
    }

    /** Normalizes GitHub's {@code sha256:<hex>} digest or a checksum-file line. */
    static String sha256(String value) {
        if (value == null) return null;
        String candidate = value.trim();
        if (candidate.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
            candidate = candidate.substring(7).trim();
        } else {
            int whitespace = firstWhitespace(candidate);
            if (whitespace >= 0) candidate = candidate.substring(0, whitespace);
        }
        if (candidate.length() != 64) return null;
        for (int index = 0; index < candidate.length(); index++) {
            char character = Character.toLowerCase(candidate.charAt(index));
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) return null;
        }
        return candidate.toUpperCase(Locale.ROOT);
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) return index;
        }
        return -1;
    }
}
