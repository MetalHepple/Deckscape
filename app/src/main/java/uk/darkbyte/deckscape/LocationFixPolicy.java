package uk.darkbyte.deckscape;

/** Timing and cache-age policy for a one-shot foreground location request. */
final class LocationFixPolicy {
    static final long REQUEST_TIMEOUT_MS = 60_000L;
    static final long IMMEDIATE_CACHE_AGE_MS = 24L * 60 * 60 * 1000;
    static final long FALLBACK_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long CLOCK_SKEW_TOLERANCE_MS = 5L * 60 * 1000;

    private LocationFixPolicy() {}

    /** Returns whether a timestamp is recent enough, tolerating small clock corrections. */
    static boolean isRecent(long nowMillis, long fixMillis, long maximumAgeMillis) {
        if (fixMillis <= 0 || maximumAgeMillis < 0) return false;
        long age = nowMillis - fixMillis;
        return age >= -CLOCK_SKEW_TOLERANCE_MS && age <= maximumAgeMillis;
    }
}
