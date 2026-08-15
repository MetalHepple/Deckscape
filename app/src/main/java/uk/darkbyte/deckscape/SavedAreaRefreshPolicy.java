package uk.darkbyte.deckscape;

/** Pure cadence policy for optional foreground refreshes of the shared saved area. */
final class SavedAreaRefreshPolicy {
    static final long DAILY_REFRESH_MILLIS = 24L * 60 * 60 * 1000;

    private SavedAreaRefreshPolicy() {}

    static boolean shouldRefresh(boolean enabled, boolean areaInUse,
                                 long nowMillis, long lastAttemptMillis) {
        if (!enabled || !areaInUse || nowMillis <= 0) return false;
        return lastAttemptMillis <= 0 || nowMillis < lastAttemptMillis
                || nowMillis - lastAttemptMillis >= DAILY_REFRESH_MILLIS;
    }
}
