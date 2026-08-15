package uk.darkbyte.deckscape;

/** Calculates wall-clock-aligned redraw delays for static wallpaper frames. */
final class WallpaperRedrawScheduler {
    static final long DEFAULT_REDRAW_MILLIS = 15_000L;
    private static final long MINUTE_MILLIS = 60_000L;

    private WallpaperRedrawScheduler() {}

    static long staticDelayMillis(long nowMillis, boolean clockVisible) {
        if (!clockVisible) return DEFAULT_REDRAW_MILLIS;
        long intoMinute = Math.floorMod(nowMillis, MINUTE_MILLIS);
        return MINUTE_MILLIS - intoMinute;
    }
}
