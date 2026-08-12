package uk.darkbyte.deckscape;

/**
 * Centralizes the product identity exposed by the UI, logs, and HTTP clients.
 *
 */
final class AppMetadata {
    static final String DISPLAY_NAME = "Deckscape";
    static final String CREATOR_NAME = "Paul Hepple";
    static final String CREATOR_LOGIN = "MetalHepple";
    static final String CREATOR_URL = "https://github.com/MetalHepple";
    static final String CREATOR_AVATAR_URL =
            "https://avatars.githubusercontent.com/u/68790254?v=4";
    static final String REPOSITORY_URL = "https://github.com/MetalHepple/Deckscape";

    private AppMetadata() {}

    /** Returns a versioned identifier suitable for outbound HTTP {@code User-Agent} headers. */
    static String userAgent() {
        return DISPLAY_NAME + "/" + BuildConfig.VERSION_NAME + " (Android)";
    }

    /** Returns the product and build version for user-visible diagnostic surfaces. */
    static String versionLabel() {
        return DISPLAY_NAME + " " + BuildConfig.VERSION_NAME;
    }
}
