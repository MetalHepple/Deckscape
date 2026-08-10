package uk.darkbyte.deckscape;

/** Debounces ambient-light readings so tunnels and shadows do not change wallpaper pools. */
final class AmbientLightTracker {
    static final float NIGHT_THRESHOLD_LUX = 25f;
    static final float DAY_THRESHOLD_LUX = 75f;
    static final long INITIAL_SETTLE_MS = 60_000L;
    static final long CHANGE_SETTLE_MS = 5L * 60_000L;

    private DayPhase phase;
    private DayPhase candidate;
    private long candidateSince;

    DayPhase update(float lux, long elapsedRealtime) {
        DayPhase desired = lux <= NIGHT_THRESHOLD_LUX ? DayPhase.NIGHT
                : lux >= DAY_THRESHOLD_LUX ? DayPhase.DAY : null;
        if (desired == null || desired == phase) {
            candidate = null;
            candidateSince = 0;
            return phase;
        }
        if (candidate != desired || elapsedRealtime < candidateSince) {
            candidate = desired;
            candidateSince = elapsedRealtime;
            return phase;
        }
        long required = phase == null ? INITIAL_SETTLE_MS : CHANGE_SETTLE_MS;
        if (elapsedRealtime - candidateSince >= required) {
            phase = desired;
            candidate = null;
            candidateSince = 0;
        }
        return phase;
    }

    DayPhase phase() {
        return phase;
    }

    void reset() {
        phase = null;
        candidate = null;
        candidateSince = 0;
    }
}
