package uk.darkbyte.deckscape;

/** Pure safety rules shared by the dashboard capture state and decoder. */
final class DashboardCapturePolicy {
    private static final int MAX_AXIS = 8_192;

    private DashboardCapturePolicy() {}

    static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_AXIS && height <= MAX_AXIS;
    }

    static boolean shouldRecover(long nowMillis, long startedAtMillis, long timeoutMillis) {
        return startedAtMillis <= 0 || nowMillis < startedAtMillis
                || nowMillis - startedAtMillis >= timeoutMillis;
    }

    static long recoveryDelayMillis(long nowMillis, long startedAtMillis,
                                    long timeoutMillis) {
        if (shouldRecover(nowMillis, startedAtMillis, timeoutMillis)) return 0;
        return timeoutMillis - (nowMillis - startedAtMillis);
    }
}
